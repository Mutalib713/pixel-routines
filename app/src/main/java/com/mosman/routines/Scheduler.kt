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
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = nextTimeTrigger(t) ?: return
        val at = next.toInstant().toEpochMilli()
        val p = pi(ctx, r.id, index)
        if (am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, p)
    }

    fun cancel(ctx: Context, r: Routine) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        r.triggers.forEachIndexed { i, _ -> am.cancel(pi(ctx, r.id, i)) }
    }
}
