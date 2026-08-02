package com.mosman.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Arms and cancels exact alarms for TimeOfDay triggers. */
object Scheduler {
    const val EXTRA_ID = "rid"
    const val EXTRA_INDEX = "tindex"
    const val ACTION_FIRE = "com.mosman.routines.TIME_FIRE"

    private fun code(id: Long, index: Int): Int = ((id and 0xFFFFFF) * 16 + index).toInt()

    private fun pi(ctx: Context, id: Long, index: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("routine://$id/$index")
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_INDEX, index)
        }
        return PendingIntent.getBroadcast(ctx, code(id, index), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleTime(ctx: Context, r: Routine, index: Int, t: Trigger.TimeOfDay) {
        nextTimeTrigger(t)?.let { setAlarm(ctx, r, index, it.toInstant().toEpochMilli()) }
    }

    fun scheduleSun(ctx: Context, r: Routine, index: Int, t: Trigger.Sun) {
        SunCalc.next(t)?.let { setAlarm(ctx, r, index, it.toInstant().toEpochMilli()) }
    }

    /** Arms an alarm for the next matching calendar event's start (or end). */
    fun scheduleCalendar(ctx: Context, r: Routine, index: Int, t: Trigger.CalendarEvent) {
        val event = Calendars.nextMatching(ctx, t.titleContains, t.atStart) ?: return
        setAlarm(ctx, r, index, if (t.atStart) event.begin else event.end)
    }

    private fun setAlarm(ctx: Context, r: Routine, index: Int, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, r.id, index)
        if (!am.canScheduleExactAlarms()) {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, p)
            return
        }
        // A routine that reminds you is an alarm in every sense that matters, so it gets
        // setAlarmClock — the one type Doze never defers, at the cost of the alarm icon in
        // the status bar. Everything else stays quiet with setExactAndAllowWhileIdle.
        val reminds = (r.actions + r.endActions).any { it is Action.Remind }
        if (reminds) {
            val show = PendingIntent.getActivity(ctx, code(r.id, index) + 1,
                Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), p) }
                .onFailure { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    /** Cancels every possible slot (start 0..7, end 8..15) so nothing is left armed. */
    fun cancel(ctx: Context, r: Routine) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        for (i in 0 until 16) am.cancel(pi(ctx, r.id, i))
    }
}
