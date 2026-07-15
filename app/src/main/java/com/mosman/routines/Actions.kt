package com.mosman.routines

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import kotlin.math.roundToInt

/** Executes a routine's actions and reports human-readable results. */
object Actions {

    fun runAll(ctx: Context, r: Routine): List<String> {
        val out = mutableListOf<String>()
        for (a in r.actions) out += runOne(ctx, a)
        return out
    }

    fun runOne(ctx: Context, a: Action): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val am = ctx.getSystemService(AudioManager::class.java)
        val dnd = nm.isNotificationPolicyAccessGranted
        return runCatching {
            when (a) {
                is Action.Ringer -> {
                    if (!dnd) return "Ringer needs DND access"
                    am.ringerMode = when (a.mode) {
                        RingerMode.SOUND -> AudioManager.RINGER_MODE_NORMAL
                        RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                        RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
                    }
                    "Ringer → ${a.mode.label().lowercase()}"
                }
                is Action.Dnd -> {
                    if (!dnd) return "DND needs access"
                    nm.setInterruptionFilter(
                        if (a.on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                    "DND ${if (a.on) "on" else "off"}"
                }
                is Action.Volume -> {
                    val stream = when (a.stream) {
                        StreamType.MEDIA -> AudioManager.STREAM_MUSIC
                        StreamType.RING -> AudioManager.STREAM_RING
                        StreamType.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
                        StreamType.ALARM -> AudioManager.STREAM_ALARM
                        StreamType.CALL -> AudioManager.STREAM_VOICE_CALL
                    }
                    if ((a.stream == StreamType.RING || a.stream == StreamType.NOTIFICATION) && !dnd)
                        return "${a.stream.label()} volume needs DND access"
                    val max = am.getStreamMaxVolume(stream)
                    am.setStreamVolume(stream, (max * a.percent / 100f).roundToInt().coerceIn(0, max), 0)
                    "${a.stream.label()} volume ${a.percent}%"
                }
                is Action.Brightness -> {
                    if (!Settings.System.canWrite(ctx)) return "Brightness needs system-settings access"
                    Settings.System.putInt(ctx.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                        (255 * a.percent / 100f).roundToInt().coerceIn(1, 255))
                    "Brightness ${a.percent}%"
                }
                is Action.AutoRotate -> {
                    if (!Settings.System.canWrite(ctx)) return "Auto-rotate needs system-settings access"
                    Settings.System.putInt(ctx.contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION, if (a.on) 1 else 0)
                    "Auto-rotate ${if (a.on) "on" else "off"}"
                }
                is Action.DarkTheme -> {
                    // Requires: adb shell pm grant com.mosman.routines android.permission.WRITE_SECURE_SETTINGS
                    if (!Secure.canWriteSecure(ctx)) return "Dark theme needs the one-time ADB grant"
                    Settings.Secure.putInt(ctx.contentResolver, "ui_night_mode", if (a.on) 2 else 1)
                    "Dark theme ${if (a.on) "on" else "off"}"
                }
                is Action.BatterySaver -> {
                    if (!Secure.canWriteSecure(ctx)) return "Battery Saver needs the one-time ADB grant"
                    Settings.Global.putInt(ctx.contentResolver, "low_power", if (a.on) 1 else 0)
                    "Battery Saver ${if (a.on) "on" else "off"}"
                }
                is Action.WifiToggle -> Shizuku.svc("wifi", a.on)
                is Action.BluetoothToggle -> Shizuku.bluetooth(a.on)
                is Action.AirplaneToggle -> Shizuku.airplane(a.on)
                is Action.LaunchApp -> {
                    val i = ctx.packageManager.getLaunchIntentForPackage(a.pkg)
                        ?: return "Can't open ${a.label}"
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    "Opened ${a.label}"
                }
                is Action.Flashlight -> {
                    val cm = ctx.getSystemService(CameraManager::class.java)
                    val id = cm.cameraIdList.firstOrNull {
                        cm.getCameraCharacteristics(it)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: return "No flashlight"
                    cm.setTorchMode(id, a.on)
                    "Flashlight ${if (a.on) "on" else "off"}"
                }
                is Action.Notify -> {
                    notify(ctx, a.title, a.text)
                    "Notified"
                }
            }
        }.getOrElse { "${a.describe()} — failed" }
    }

    private fun notify(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, "reminders")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(title.hashCode(), n) }
    }

    /** Notification confirming a routine ran (Samsung-style toast card). */
    fun notifyRan(ctx: Context, r: Routine, results: List<String>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("runs", "Routine activity", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, "runs")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${r.emoji}  ${r.name}")
            .setContentText(results.joinToString(" · "))
            .setStyle(Notification.BigTextStyle().bigText(results.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(r.id.toInt(), n) }
    }
}
