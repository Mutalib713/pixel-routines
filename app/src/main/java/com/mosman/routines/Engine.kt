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

        Motion.sync(ctx)   // subscribes/unsubscribes driving & walking detection
    }

    fun arm(ctx: Context, r: Routine) {
        r.triggers.take(END_OFFSET).forEachIndexed { i, t -> armOne(ctx, r, i, t) }
        r.endTriggers.take(END_OFFSET).forEachIndexed { i, t -> armOne(ctx, r, END_OFFSET + i, t) }
    }

    private fun armOne(ctx: Context, r: Routine, index: Int, t: Trigger) {
        when (t) {
            is Trigger.TimeOfDay -> Scheduler.scheduleTime(ctx, r, index, t)
            is Trigger.Sun -> Scheduler.scheduleSun(ctx, r, index, t)
            is Trigger.CalendarEvent -> Scheduler.scheduleCalendar(ctx, r, index, t)
            is Trigger.Location -> Geofences.add(ctx, r, index, t)
            else -> Unit // event triggers are handled by EventService / listeners
        }
    }

    fun disarm(ctx: Context, r: Routine) {
        Scheduler.cancel(ctx, r)
        Geofences.remove(ctx, r)
    }

    // ---- Firing paths --------------------------------------------------------

    fun onTimeFired(ctx: Context, id: Long, index: Int, onComplete: (() -> Unit)? = null) {
        val r = Store.get(ctx, id)
        if (r == null || !r.enabled) { onComplete?.invoke(); return }
        val isEnd = index >= END_OFFSET
        if (isEnd) endRoutine(ctx, r, onComplete) else fireRoutine(ctx, r, onComplete)
        // Re-arm this recurring trigger for its next occurrence.
        val list = if (isEnd) r.endTriggers else r.triggers
        when (val t = list.getOrNull(if (isEnd) index - END_OFFSET else index)) {
            is Trigger.TimeOfDay -> Scheduler.scheduleTime(ctx, r, index, t)
            is Trigger.Sun -> Scheduler.scheduleSun(ctx, r, index, t)
            is Trigger.CalendarEvent -> Scheduler.scheduleCalendar(ctx, r, index, t)
            else -> Unit
        }
    }

    fun onGeofence(ctx: Context, id: Long, index: Int, enter: Boolean, onComplete: (() -> Unit)? = null) {
        val r = Store.get(ctx, id)
        val list = if (index >= END_OFFSET) r?.endTriggers else r?.triggers
        val t = list?.getOrNull(if (index >= END_OFFSET) index - END_OFFSET else index) as? Trigger.Location
        if (r == null || !r.enabled || t == null || t.enter != enter) { onComplete?.invoke(); return }
        if (index >= END_OFFSET) endRoutine(ctx, r, onComplete) else fireRoutine(ctx, r, onComplete)
    }

    /** Live device events from EventService. */
    fun handleEvent(ctx: Context, matcher: (Trigger) -> Boolean) {
        Store.load(ctx).filter { it.enabled }.forEach { r ->
            val running = r.hasEnd && Store.isActive(ctx, r.id)
            when {
                // Firing again while it is already running would re-snapshot the settings
                // this routine itself just changed, so the UNTIL side would "restore" them
                // to the values it was meant to undo. Ending one that never started would
                // replay a stale snapshot for the same reason.
                r.triggers.any(matcher) -> if (!running) fireRoutine(ctx, r)
                r.endTriggers.any(matcher) -> if (running) endRoutine(ctx, r)
            }
        }
    }

    /** Runs a routine if its conditions and match rule pass right now (async: Wait actions sleep). */
    fun fireRoutine(ctx: Context, r: Routine, onComplete: (() -> Unit)? = null) {
        if (Store.isPaused(ctx) || !conditionsMet(ctx, r) ||
            (r.match == Match.ALL && !allStateTriggersTrue(ctx, r))) {
            onComplete?.invoke(); return
        }
        val app = ctx.applicationContext
        // Mark it running before the work starts, not after: actions can take seconds (Wait
        // sleeps for up to 30), and an end trigger that lands mid-run still has to find the
        // routine active or it will be ignored.
        if (r.hasEnd) Store.setActive(app, r.id, true)
        Thread {
            // Remember the "before" state so the end condition can put it back.
            if (r.hasEnd && r.endMode == EndMode.REVERT)
                Snapshot.save(app, r.id, Snapshot.capture(app, r))
            val results = Actions.runAll(app, r)
            RunLog.add(app, r, "ran", results)
            if (r.notifyOnRun) Actions.notifyRan(app, r, results)
            RoutinesWidget.refresh(app)
            onComplete?.invoke()
        }.start()
    }

    /** Ends a running routine: undo, run end actions, or leave things alone. */
    fun endRoutine(ctx: Context, r: Routine, onComplete: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        Thread {
            val results = when (r.endMode) {
                EndMode.REVERT -> Snapshot.load(app, r.id).map { Actions.runOne(app, it) }
                EndMode.CUSTOM -> r.endActions.map { Actions.runOne(app, it) }
                EndMode.NOTHING -> emptyList()
            }
            Snapshot.clear(app, r.id)
            Store.setActive(app, r.id, false)
            RunLog.add(app, r, "ended", results.ifEmpty { listOf("left as is") })
            if (results.isNotEmpty() && r.notifyOnRun) Actions.notifyEnded(app, r, results)
            RoutinesWidget.refresh(app)
            onComplete?.invoke()
        }.start()
    }

    /** Manual "Run now" — skips trigger/condition/pause checks; callback lands on the caller's thread pool. */
    fun runNow(ctx: Context, r: Routine, onDone: (List<String>) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            if (r.hasEnd && r.endMode == EndMode.REVERT)
                Snapshot.save(app, r.id, Snapshot.capture(app, r))
            val out = Actions.runAll(app, r)
            if (r.hasEnd) Store.setActive(app, r.id, true)
            RunLog.add(app, r, "ran (manual)", out)
            RoutinesWidget.refresh(app)
            onDone(out)
        }.start()
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

    /** True for triggers the EventService must stay alive to notice. */
    private fun isEventTrigger(t: Trigger): Boolean = when (t) {
        // Handled by alarms, geofences, or their own listener services instead:
        is Trigger.TimeOfDay, is Trigger.Sun, is Trigger.CalendarEvent, is Trigger.Location,
        is Trigger.NotificationFrom, is Trigger.Motion, is Trigger.NfcTag -> false
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
