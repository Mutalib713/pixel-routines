package com.mosman.routines

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

object Actions {

    /** Applies every action of the routine. Returns human-readable results for the notification/snackbar. */
    fun apply(ctx: Context, r: Routine): List<String> {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val am = ctx.getSystemService(AudioManager::class.java)
        val done = mutableListOf<String>()
        val policy = nm.isNotificationPolicyAccessGranted

        // Lift DND first (if asked) so the other changes apply cleanly
        if (r.dnd == Toggle.OFF) {
            if (policy) runCatching {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                done += "DND off"
            }.onFailure { done += "DND failed" }
            else done += "DND skipped (no access)"
        }

        fun setVol(stream: Int, pct: Int, label: String) {
            runCatching {
                val max = am.getStreamMaxVolume(stream)
                am.setStreamVolume(stream, (max * pct / 100f).roundToInt().coerceIn(0, max), 0)
                done += "$label $pct%"
            }.onFailure { done += "$label blocked" }
        }
        r.mediaVol?.let { setVol(AudioManager.STREAM_MUSIC, it, "Media") }
        r.alarmVol?.let { setVol(AudioManager.STREAM_ALARM, it, "Alarm") }
        r.ringVol?.let {
            if (policy) setVol(AudioManager.STREAM_RING, it, "Ring")
            else done += "Ring vol skipped (no access)"
        }

        if (r.ringer != RingerAction.NO_CHANGE) {
            if (policy) runCatching {
                am.ringerMode = when (r.ringer) {
                    RingerAction.SOUND -> AudioManager.RINGER_MODE_NORMAL
                    RingerAction.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                    else -> AudioManager.RINGER_MODE_SILENT
                }
                done += "Ringer → " + r.ringer.label().lowercase()
            }.onFailure { done += "Ringer failed" }
            else done += "Ringer skipped (no access)"
        }

        if (r.dnd == Toggle.ON) {
            if (policy) runCatching {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                done += "DND on"
            }.onFailure { done += "DND failed" }
            else done += "DND skipped (no access)"
        }

        if (r.brightness != null || r.autoRotate != Toggle.NO_CHANGE) {
            if (Settings.System.canWrite(ctx)) {
                r.brightness?.let { pct ->
                    runCatching {
                        Settings.System.putInt(
                            ctx.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        )
                        Settings.System.putInt(
                            ctx.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            (255 * pct / 100f).roundToInt().coerceIn(1, 255)
                        )
                        done += "Brightness $pct%"
                    }.onFailure { done += "Brightness failed" }
                }
                if (r.autoRotate != Toggle.NO_CHANGE) {
                    runCatching {
                        Settings.System.putInt(
                            ctx.contentResolver,
                            Settings.System.ACCELEROMETER_ROTATION,
                            if (r.autoRotate == Toggle.ON) 1 else 0
                        )
                        done += "Auto-rotate " + (if (r.autoRotate == Toggle.ON) "on" else "off")
                    }.onFailure { done += "Auto-rotate failed" }
                }
            } else done += "Display skipped (no access)"
        }
        return done
    }

    fun notifyRan(ctx: Context, r: Routine, results: List<String>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("runs", "Routine activity", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (results.isEmpty()) "No actions set" else results.joinToString(" · ")
        val n = Notification.Builder(ctx, "runs")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Ran “" + r.name + "”")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(results.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // On 13+ this silently no-ops if the notification permission wasn't granted
        runCatching { nm.notify((r.id and 0x7FFFFFFF).toInt(), n) }
    }
}
