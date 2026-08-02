package com.mosman.routines

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.content.res.Configuration
import org.json.JSONArray
import kotlin.math.roundToInt

/**
 * Remembers what the phone looked like before a routine changed it, so the routine can
 * put everything back when it ends (Samsung's "undo the changes" behaviour).
 *
 * Neat trick: the "before" state is stored as the very same Action objects — restoring is
 * just running them.
 */
object Snapshot {
    private const val PREF = "pixel_routines_snapshots"

    /** Reads the current value of every setting this routine is about to change. */
    fun capture(ctx: Context, r: Routine): List<Action> = r.actions.mapNotNull { current(ctx, it) }

    private fun current(ctx: Context, a: Action): Action? {
        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return runCatching {
            when (a) {
                is Action.Ringer -> Action.Ringer(when (am.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> RingerMode.SOUND
                    AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                    else -> RingerMode.SILENT
                })
                is Action.Dnd -> Action.Dnd(
                    nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
                is Action.Volume -> {
                    val s = streamOf(a.stream)
                    val max = am.getStreamMaxVolume(s).coerceAtLeast(1)
                    Action.Volume(a.stream, (am.getStreamVolume(s) * 100f / max).roundToInt())
                }
                is Action.Brightness -> {
                    val v = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                    Action.Brightness((v * 100f / 255f).roundToInt())
                }
                is Action.AutoRotate -> Action.AutoRotate(
                    Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1)
                is Action.DarkTheme -> Action.DarkTheme(
                    (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES)
                is Action.BatterySaver -> Action.BatterySaver(
                    Settings.Global.getInt(ctx.contentResolver, "low_power", 0) == 1)
                is Action.WifiToggle -> Action.WifiToggle(
                    ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled)
                is Action.BluetoothToggle -> Action.BluetoothToggle(
                    ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true)
                is Action.AirplaneToggle -> Action.AirplaneToggle(State.airplane(ctx))
                // Nothing sensible to undo for these:
                is Action.LaunchApp, is Action.Flashlight, is Action.Notify,
                is Action.Media, is Action.OpenUrl, is Action.Wait,
                is Action.Message, is Action.Call, is Action.SendSms,
                is Action.Speak, is Action.ReplyNotification,
                is Action.Remind -> null
            }
        }.getOrNull()
    }

    private fun streamOf(s: StreamType) = when (s) {
        StreamType.MEDIA -> AudioManager.STREAM_MUSIC
        StreamType.RING -> AudioManager.STREAM_RING
        StreamType.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        StreamType.ALARM -> AudioManager.STREAM_ALARM
        StreamType.CALL -> AudioManager.STREAM_VOICE_CALL
    }

    fun save(ctx: Context, id: Long, actions: List<Action>) {
        val arr = JSONArray()
        actions.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(id.toString(), arr.toString()).apply()
    }

    fun load(ctx: Context, id: Long): List<Action> {
        val raw = prefs(ctx).getString(id.toString(), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Action.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun clear(ctx: Context, id: Long) = prefs(ctx).edit().remove(id.toString()).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
