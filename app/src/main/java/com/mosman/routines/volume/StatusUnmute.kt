package com.mosman.routines.volume

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.core.content.edit
import com.mosman.routines.ShizukuBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unmute WhatsApp statuses.
 *
 * WhatsApp plays statuses muted while the ringer is on Vibrate or Silent, and unmutes when the
 * user presses a hardware volume key on the status screen. A volume change made through
 * AudioManager does NOT count (tested on a Pixel 6 Pro, 2026-11-14), but a key press sent
 * from the ADB shell does. So: notice the status screen opening (EventService polls
 * UsageStats for the activity in front), then press volume-up through Shizuku.
 *
 * The decision logic is pure so it can be unit-tested on the JVM.
 */
object StatusUnmute {

    /** WhatsApp and WhatsApp Business. */
    val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    /** Measured: com.whatsapp.status.playback.StatusPlaybackActivity. A substring survives renames of the class itself. */
    private const val STATUS_SCREEN = ".status.playback."

    /** Never press more than once in this window, whatever the poller sees. */
    const val MIN_GAP_MS = 4_000L

    fun isWhatsApp(pkg: String?): Boolean = pkg in PACKAGES

    fun isStatusScreen(pkg: String?, cls: String?): Boolean =
        isWhatsApp(pkg) && cls != null && cls.contains(STATUS_SCREEN)

    /** WhatsApp only mutes statuses in these two ringer modes. */
    fun ringerMutes(ringerMode: Int): Boolean =
        ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_SILENT

    /**
     * Press now? Only for a status screen that opened after the last one we handled (moving
     * between statuses keeps the same screen, and WhatsApp stays unmuted for those), only
     * when the ringer would make WhatsApp mute it, and never twice within [MIN_GAP_MS].
     */
    fun shouldPress(
        pkg: String?,
        cls: String?,
        resumedAt: Long,
        lastHandledResume: Long,
        ringerMode: Int,
        now: Long,
        lastPressAt: Long,
    ): Boolean =
        isStatusScreen(pkg, cls) &&
            resumedAt > lastHandledResume &&
            ringerMutes(ringerMode) &&
            now - lastPressAt >= MIN_GAP_MS

    /** The keys to send: volume-up unmutes; an optional volume-down puts the level back. */
    fun keysFor(keepLevel: Boolean): IntArray =
        if (keepLevel) intArrayOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
        else intArrayOf(KeyEvent.KEYCODE_VOLUME_UP)

    /** Does the press (blocking: call off the main thread) and records the outcome for the settings card. */
    fun press(ctx: Context, keepLevel: Boolean, what: String): String {
        val failure = ShizukuBridge.pressKeys(ctx, *keysFor(keepLevel))
        // "Pressed", not "Unmuted": the app can confirm the key was sent, not what WhatsApp did with it.
        val result = if (failure == null) "Pressed volume-up on $what" else "Couldn't press volume-up on $what: $failure"
        recordLast(ctx, result)
        return result
    }

    // ---- Last outcome, shown on the settings card so a silent failure is visible ----
    // Kept out of the settings JSON on purpose: saving settings re-arms every service.

    private const val PREFS = "pixel_status_unmute"
    private const val KEY_LAST = "last"

    fun recordLast(ctx: Context, text: String) {
        val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST, "$stamp · $text")
        }
    }

    fun lastResult(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun appLabel(pkg: String?): String =
        if (pkg == "com.whatsapp.w4b") "a WhatsApp Business status" else "a WhatsApp status"
}
