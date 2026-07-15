package com.mosman.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

object Scheduler {
    const val EXTRA_ID = "routine_id"

    private fun pending(ctx: Context, r: Routine): PendingIntent {
        val i = Intent(ctx, RoutineFireReceiver::class.java).apply {
            action = "com.mosman.routines.FIRE"
            data = Uri.parse("routine://" + r.id)
            putExtra(EXTRA_ID, r.id)
        }
        return PendingIntent.getBroadcast(
            ctx,
            (r.id and 0x7FFFFFFF).toInt(),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(ctx: Context, r: Routine) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = r.nextTrigger() ?: return
        val at = next.toInstant().toEpochMilli()
        val pi = pending(ctx, r)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Fallback if exact alarms ever get revoked: fire within a 10-minute window
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
        }
    }

    fun cancel(ctx: Context, r: Routine) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, r))
    }

    fun rescheduleAll(ctx: Context) {
        Store.load(ctx).forEach { if (it.enabled) schedule(ctx, it) else cancel(ctx, it) }
    }
}
