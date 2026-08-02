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
        for (a in r.actions) out += runOne(ctx, a, r.id)
        return out
    }

    /**
     * [remindId] only matters to Action.Remind, which keys its alert off the routine so a
     * re-fire replaces its own reminder instead of stacking a second one.
     */
    fun runOne(ctx: Context, a: Action, remindId: Long = 0L): String {
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
                is Action.WifiToggle -> ShizukuBridge.wifi(a.on)
                is Action.BluetoothToggle -> ShizukuBridge.bluetooth(a.on)
                is Action.AirplaneToggle -> ShizukuBridge.airplane(a.on)
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
                is Action.Remind -> Reminders.raise(ctx, remindId, a.text, a.snoozeMin)
                is Action.Media -> {
                    val code = when (a.key) {
                        MediaKey.PLAY_PAUSE -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        MediaKey.NEXT -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                        MediaKey.PREVIOUS -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    }
                    am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                    am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
                    a.key.label()
                }
                is Action.OpenUrl -> {
                    val url = if (a.url.startsWith("http")) a.url else "https://" + a.url
                    val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    "Opened $url"
                }
                is Action.Wait -> {
                    Thread.sleep(a.seconds.coerceIn(1, 30) * 1000L)
                    "Waited ${a.seconds}s"
                }
                is Action.Speak -> speak(ctx, a.text)
                is Action.ReplyNotification -> {
                    if (!Permissions.hasNotificationAccess(ctx)) return "Reply needs notification access"
                    NotifRegistry.reply(ctx, a.pkg.ifBlank { null }, a.text)
                }
                is Action.Message -> openChat(ctx, a)
                is Action.Call -> {
                    if (!Permissions.hasCallPhone(ctx)) return "Call needs phone permission"
                    val i = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:" + a.number))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    "Calling ${a.who.ifBlank { a.number }}"
                }
                is Action.SendSms -> {
                    if (!Permissions.hasSendSms(ctx)) return "Text needs SMS permission"
                    val sms = ctx.getSystemService(android.telephony.SmsManager::class.java)
                    val parts = sms.divideMessage(a.text)
                    if (parts.size > 1) sms.sendMultipartTextMessage(a.number, null, parts, null, null)
                    else sms.sendTextMessage(a.number, null, a.text, null, null)
                    "Texted ${a.who.ifBlank { a.number }}"
                }
            }
        }.getOrElse { "${a.describe()} — failed" }
    }

    /**
     * Speaks text aloud. TextToSpeech needs an init round-trip, so this waits briefly for
     * the engine rather than firing into a void — we're already on a background thread.
     */
    private fun speak(ctx: Context, text: String): String {
        val latch = java.util.concurrent.CountDownLatch(1)
        var tts: android.speech.tts.TextToSpeech? = null
        var ok = false
        tts = android.speech.tts.TextToSpeech(ctx.applicationContext) { status ->
            ok = status == android.speech.tts.TextToSpeech.SUCCESS
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        if (!ok) { runCatching { tts?.shutdown() }; return "Couldn't start text-to-speech" }
        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "routine")
        // Let it finish before tearing the engine down.
        Thread {
            Thread.sleep((1500 + text.length * 90).toLong().coerceAtMost(20_000))
            runCatching { tts.shutdown() }
        }.start()
        return "Said “$text”"
    }

    /**
     * Opens a chat with the text pre-filled. WhatsApp exposes no send API, so the send tap
     * is the user's — by design, not an oversight.
     */
    private fun openChat(ctx: Context, a: Action.Message): String {
        val digits = a.number.filter { it.isDigit() || it == '+' }.removePrefix("+")
        val i = when (a.app) {
            MessageApp.WHATSAPP -> Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                "https://wa.me/$digits?text=" + android.net.Uri.encode(a.text)))
                .apply { if (Apps.whatsappPackage(ctx) != null) setPackage(Apps.whatsappPackage(ctx)) }
            MessageApp.SMS -> Intent(Intent.ACTION_SENDTO,
                android.net.Uri.parse("smsto:" + a.number))
                .putExtra("sms_body", a.text)
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            ctx.startActivity(i)
            "Opened ${a.app.label()} · ${a.who.ifBlank { a.number }}"
        }.getOrElse { "${a.app.label()} isn't installed" }
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

    /** Notification confirming a routine ended and what it put back. */
    fun notifyEnded(ctx: Context, r: Routine, results: List<String>) =
        post(ctx, r, "${r.name} ended", results)

    /** Notification confirming a routine ran (Samsung-style toast card). */
    fun notifyRan(ctx: Context, r: Routine, results: List<String>) = post(ctx, r, r.name, results)

    private fun post(ctx: Context, r: Routine, title: String, results: List<String>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("runs", "Routine activity", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, "runs")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(results.joinToString(" · "))
            .setStyle(Notification.BigTextStyle().bigText(results.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(r.id.toInt(), n) }
    }
}
