package com.mosman.routines

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The automation core. Arms each enabled routine's triggers (alarms, geofences, live
 * events) and, when one fires, checks the conditions and runs the actions.
 */
object Engine {

    fun rearmAll(ctx: Context) {
        val routines = Store.load(ctx)
        routines.forEach { disarm(ctx, it) }
        routines.filter { it.enabled }.forEach { arm(ctx, it) }
        // Keep the live-event service running only if something needs it.
        val needsService = routines.any { it.enabled && it.triggers.any { t -> isEventTrigger(t) } }
        if (needsService) EventService.start(ctx) else EventService.stop(ctx)
        val needsGeo = routines.any { it.enabled && it.triggers.any { t -> t is Trigger.Location } }
        if (needsGeo) Geofences.syncAll(ctx)
    }

    fun arm(ctx: Context, r: Routine) {
        r.triggers.forEachIndexed { i, t ->
            when (t) {
                is Trigger.TimeOfDay -> Scheduler.scheduleTime(ctx, r, i, t)
                is Trigger.Location -> Geofences.add(ctx, r, i, t)
                else -> Unit // event triggers handled by EventService
            }
        }
    }

    fun disarm(ctx: Context, r: Routine) {
        Scheduler.cancel(ctx, r)
        Geofences.remove(ctx, r)
    }

    // ---- Firing paths --------------------------------------------------------

    /** Called by AlarmReceiver when a time trigger elapses. */
    fun onTimeFired(ctx: Context, id: Long, index: Int) {
        val r = Store.get(ctx, id) ?: return
        if (r.enabled) fireRoutine(ctx, r)
        // Re-arm this recurring time trigger for its next occurrence.
        (r.triggers.getOrNull(index) as? Trigger.TimeOfDay)?.let { Scheduler.scheduleTime(ctx, r, index, it) }
    }

    /** Called by GeofenceReceiver. */
    fun onGeofence(ctx: Context, id: Long, enter: Boolean) {
        val r = Store.get(ctx, id) ?: return
        val matches = r.enabled && r.triggers.any { it is Trigger.Location && it.enter == enter }
        if (matches) fireRoutine(ctx, r)
    }

    /** Called by EventService for live device events. */
    fun handleEvent(ctx: Context, matcher: (Trigger) -> Boolean) {
        Store.load(ctx).filter { it.enabled && it.triggers.any(matcher) }
            .forEach { fireRoutine(ctx, it) }
    }

    /** Runs a routine if its conditions and match rule pass right now. */
    fun fireRoutine(ctx: Context, r: Routine) {
        if (!conditionsMet(ctx, r)) return
        if (r.match == Match.ALL && !allStateTriggersTrue(ctx, r)) return
        val results = Actions.runAll(ctx, r)
        Actions.notifyRan(ctx, r, results)
    }

    /** Manual "Run now" — skips trigger/condition checks. */
    fun runNow(ctx: Context, r: Routine): List<String> = Actions.runAll(ctx, r)

    // ---- Condition + state evaluation ---------------------------------------

    private fun conditionsMet(ctx: Context, r: Routine): Boolean {
        val now = ZonedDateTime.now()
        return r.conditions.all { c ->
            when (c) {
                is Condition.OnDays -> c.days.contains(now.dayOfWeek.value)
                is Condition.BetweenHours -> inWindow(
                    LocalTime.now(),
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

    /** True/false for stateful triggers, null for event-only (time, location, bluetooth). */
    fun isTrue(ctx: Context, t: Trigger): Boolean? = when (t) {
        is Trigger.Battery -> if (t.below) batteryLevel(ctx) < t.level else batteryLevel(ctx) > t.level
        is Trigger.Power -> charging(ctx) == t.connected
        is Trigger.Headset -> headsetOn(ctx) == t.connected
        is Trigger.Airplane -> airplane(ctx) == t.on
        is Trigger.Screen -> screenOn(ctx) == t.on
        else -> null
    }
}
