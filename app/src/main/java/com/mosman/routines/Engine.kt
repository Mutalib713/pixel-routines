package com.mosman.routines

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The automation core. Arms each enabled routine's start and end triggers (alarms,
 * geofences, live events); when one fires it checks the conditions and runs the actions,
 * or ends the routine and puts the settings back.
 */
object Engine {

    /** Alarm/geofence indices 0..7 are start triggers, 8..15 are end triggers. */
    const val END_OFFSET = 8

    fun rearmAll(ctx: Context) {
        val routines = Store.load(ctx)
        routines.forEach { disarm(ctx, it) }
        routines.filter { it.enabled }.forEach { arm(ctx, it) }

        val needsService = routines.any { r ->
            r.enabled && (r.triggers.any(::isEventTrigger) || r.endTriggers.any(::isEventTrigger))
        }
        if (needsService) EventService.start(ctx) else EventService.stop(ctx)

        if (routines.any { r -> r.enabled && (r.triggers + r.endTriggers).any { it is Trigger.Location } })
            Geofences.syncAll(ctx)
    }

    fun arm(ctx: Context, r: Routine) {
        r.triggers.take(END_OFFSET).forEachIndexed { i, t -> armOne(ctx, r, i, t) }
        r.endTriggers.take(END_OFFSET).forEachIndexed { i, t -> armOne(ctx, r, END_OFFSET + i, t) }
    }

    private fun armOne(ctx: Context, r: Routine, index: Int, t: Trigger) {
        when (t) {
            is Trigger.TimeOfDay -> Scheduler.scheduleTime(ctx, r, index, t)
            is Trigger.Location -> Geofences.add(ctx, r, index, t)
            else -> Unit // event triggers are handled by EventService
        }
    }

    fun disarm(ctx: Context, r: Routine) {
        Scheduler.cancel(ctx, r)
        Geofences.remove(ctx, r)
    }

    // ---- Firing paths --------------------------------------------------------

    fun onTimeFired(ctx: Context, id: Long, index: Int) {
        val r = Store.get(ctx, id) ?: return
        if (!r.enabled) return
        if (index >= END_OFFSET) {
            endRoutine(ctx, r)
            (r.endTriggers.getOrNull(index - END_OFFSET) as? Trigger.TimeOfDay)
                ?.let { Scheduler.scheduleTime(ctx, r, index, it) }
        } else {
            fireRoutine(ctx, r)
            (r.triggers.getOrNull(index) as? Trigger.TimeOfDay)
                ?.let { Scheduler.scheduleTime(ctx, r, index, it) }
        }
    }

    fun onGeofence(ctx: Context, id: Long, index: Int, enter: Boolean) {
        val r = Store.get(ctx, id) ?: return
        if (!r.enabled) return
        val list = if (index >= END_OFFSET) r.endTriggers else r.triggers
        val t = list.getOrNull(if (index >= END_OFFSET) index - END_OFFSET else index) as? Trigger.Location
            ?: return
        if (t.enter != enter) return
        if (index >= END_OFFSET) endRoutine(ctx, r) else fireRoutine(ctx, r)
    }

    /** Live device events from EventService. */
    fun handleEvent(ctx: Context, matcher: (Trigger) -> Boolean) {
        Store.load(ctx).filter { it.enabled }.forEach { r ->
            when {
                r.triggers.any(matcher) -> fireRoutine(ctx, r)
                r.endTriggers.any(matcher) -> endRoutine(ctx, r)
            }
        }
    }

    /** Runs a routine if its conditions and match rule pass right now. */
    fun fireRoutine(ctx: Context, r: Routine) {
        if (!conditionsMet(ctx, r)) return
        if (r.match == Match.ALL && !allStateTriggersTrue(ctx, r)) return

        // Remember the "before" state so the end condition can put it back.
        if (r.hasEnd && r.endMode == EndMode.REVERT)
            Snapshot.save(ctx, r.id, Snapshot.capture(ctx, r))

        val results = Actions.runAll(ctx, r)
        if (r.hasEnd) Store.setActive(ctx, r.id, true)
        Actions.notifyRan(ctx, r, results)
        RoutinesWidget.refresh(ctx)
    }

    /** Ends a running routine: undo, run end actions, or leave things alone. */
    fun endRoutine(ctx: Context, r: Routine) {
        val results = when (r.endMode) {
            EndMode.REVERT -> Snapshot.load(ctx, r.id).map { Actions.runOne(ctx, it) }
            EndMode.CUSTOM -> r.endActions.map { Actions.runOne(ctx, it) }
            EndMode.NOTHING -> emptyList()
        }
        Snapshot.clear(ctx, r.id)
        Store.setActive(ctx, r.id, false)
        if (results.isNotEmpty()) Actions.notifyEnded(ctx, r, results)
        RoutinesWidget.refresh(ctx)
    }

    /** Manual "Run now" — skips trigger/condition checks. */
    fun runNow(ctx: Context, r: Routine): List<String> {
        if (r.hasEnd && r.endMode == EndMode.REVERT)
            Snapshot.save(ctx, r.id, Snapshot.capture(ctx, r))
        val out = Actions.runAll(ctx, r)
        if (r.hasEnd) { Store.setActive(ctx, r.id, true); RoutinesWidget.refresh(ctx) }
        return out
    }

    // ---- Condition + state evaluation ---------------------------------------

    private fun conditionsMet(ctx: Context, r: Routine): Boolean {
        val now = ZonedDateTime.now()
        return r.conditions.all { c ->
            when (c) {
                is Condition.OnDays -> c.days.contains(now.dayOfWeek.value)
                is Condition.BetweenHours -> inWindow(LocalTime.now(),
                    LocalTime.of(c.startH, c.startM), LocalTime.of(c.endH, c.endM))
                is Condition.BatteryUnder -> State.batteryLevel(ctx) < c.level
                is Condition.WhileCharging -> State.charging(ctx) == c.charging
            }
        }
    }

    private fun allStateTriggersTrue(ctx: Context, r: Routine): Boolean =
        r.triggers.all { State.isTrue(ctx, it) != false }

    private fun isEventTrigger(t: Trigger): Boolean = when (t) {
        is Trigger.TimeOfDay, is Trigger.Location -> false
        else -> true
    }

    private fun inWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) now >= start && now <= end else now >= start || now <= end
}

/** Point-in-time device state probes. Returns null when a trigger type isn't stateful. */
object State {
    fun batteryLevel(ctx: Context): Int =
        ctx.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun charging(ctx: Context): Boolean =
        ctx.getSystemService(BatteryManager::class.java).isCharging

    fun airplane(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun screenOn(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isInteractive

    @Suppress("DEPRECATION")
    fun headsetOn(ctx: Context): Boolean =
        ctx.getSystemService(AudioManager::class.java).isWiredHeadsetOn

    fun isTrue(ctx: Context, t: Trigger): Boolean? = when (t) {
        is Trigger.Battery -> if (t.below) batteryLevel(ctx) < t.level else batteryLevel(ctx) > t.level
        is Trigger.Power -> charging(ctx) == t.connected
        is Trigger.Headset -> headsetOn(ctx) == t.connected
        is Trigger.Airplane -> airplane(ctx) == t.on
        is Trigger.Screen -> screenOn(ctx) == t.on
        else -> null
    }
}
