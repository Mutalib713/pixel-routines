package com.mosman.routines

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Reminders are the one thing in this app that must interrupt you. Everything else changes
 * a setting quietly; a reminder has failed if you didn't see it.
 *
 * The full-screen activity is launched by a notification's full-screen intent rather than
 * by startActivity, because a background app is not allowed to start an activity itself.
 * When the screen is locked or off the system opens the activity; when you are actively
 * using the phone it shows as a heads-up instead, which is the behaviour you want.
 */
object Reminders {

    private const val CHANNEL = "reminder_alarm"
    const val ACTION_SNOOZE = "com.mosman.routines.SNOOZE_FIRE"
    const val EXTRA_TEXT = "text"
    const val EXTRA_ROUTINE = "routine"
    const val EXTRA_SNOOZE = "snooze"

    /** Stable per-routine id so a re-fire replaces its own alert instead of stacking. */
    fun notificationId(routineId: Long): Int = (routineId and 0xFFFFFF).toInt() or 0x400000

    /** Raises the reminder. Returns the line that goes into the run history. */
    fun raise(ctx: Context, routineId: Long, text: String, snoozeMin: Int): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders that take over the screen"
                setBypassDnd(true)
                enableVibration(true)
            }
        )

        val full = PendingIntent.getActivity(
            ctx, notificationId(routineId),
            ReminderActivity.intent(ctx, routineId, text, snoozeMin),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .build()

        runCatching { nm.notify(notificationId(routineId), n) }

        // Say plainly when the alert had to fall back to an ordinary notification, rather
        // than logging success and leaving a missed reminder to be discovered later.
        val allowed = runCatching { nm.canUseFullScreenIntent() }.getOrDefault(true)
        return if (allowed) "Reminded: $text"
        else "Reminded (notification only — full-screen not allowed): $text"
    }

    /** Re-arms the same reminder [minutes] from now. */
    fun snooze(ctx: Context, routineId: Long, text: String, minutes: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + minutes * 60_000L
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            data = Uri.parse("reminder://$routineId")
            putExtra(EXTRA_ROUTINE, routineId)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_SNOOZE, minutes)
        }
        val p = PendingIntent.getBroadcast(
            ctx, notificationId(routineId), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // setAlarmClock is the only alarm type Doze never defers, which is exactly the
        // guarantee a snoozed reminder needs.
        runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(ctx, routineId)), p)
        }.onFailure {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    private fun showIntent(ctx: Context, routineId: Long): PendingIntent =
        PendingIntent.getActivity(
            ctx, notificationId(routineId) + 1,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
