package com.mosman.routines.volume

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mosman.routines.State
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized Volume Panel Controller.
 * Responsible for triggering the native Android system volume panel without
 * altering current volume levels, enforcing cooldowns, and providing haptic feedback.
 */
object VolumePanelController {

    private val lastTriggerTimestamp = AtomicLong(0L)

    /**
     * Attempts to show the system volume panel.
     *
     * @param ctx Android Context
     * @param ignoreCooldown If true, bypasses cooldown checks (e.g. for user "Test" buttons)
     * @param customHaptic If specified, overrides the settings haptic preference
     * @return true if the command was successfully dispatched to AudioManager, false if dropped by cooldown or screen state
     */
    fun show(
        ctx: Context,
        ignoreCooldown: Boolean = false,
        customHaptic: Boolean? = null,
    ): Boolean {
        val appContext = ctx.applicationContext
        val settings = VolumePanelStore.load(appContext)

        // Screen-on check: avoid triggering when the phone screen is turned off in pocket
        if (!ignoreCooldown && settings.screenOnOnly && !State.screenOn(appContext)) {
            return false
        }

        // Cooldown check: prevent accidental multi-triggers
        val now = System.currentTimeMillis()
        val last = lastTriggerTimestamp.get()
        if (!ignoreCooldown && (now - last < settings.cooldownDurationMs)) {
            return false
        }

        val am = appContext.getSystemService(AudioManager::class.java) ?: return false

        val dispatched = runCatching {
            // First attempt: Primary target stream (STREAM_MUSIC with ADJUST_SAME and FLAG_SHOW_UI)
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI,
            )
            true
        }.recoverCatching {
            // Fallback attempt: suggested stream
            am.adjustSuggestedStreamVolume(
                AudioManager.ADJUST_SAME,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI,
            )
            true
        }.getOrDefault(false)

        if (dispatched) {
            lastTriggerTimestamp.set(now)

            val shouldVibrate = customHaptic ?: settings.hapticFeedbackEnabled
            if (shouldVibrate) {
                performHaptic(appContext)
            }
        }

        return dispatched
    }

    /**
     * Checks whether an activation can proceed right now without violating cooldown or screen-on rules.
     */
    fun canTriggerNow(ctx: Context): Boolean {
        val appContext = ctx.applicationContext
        val settings = VolumePanelStore.load(appContext)
        if (settings.screenOnOnly && !State.screenOn(appContext)) return false
        val now = System.currentTimeMillis()
        return (now - lastTriggerTimestamp.get() >= settings.cooldownDurationMs)
    }

    /**
     * Performs a subtle haptic feedback vibration.
     */
    fun performHaptic(ctx: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = ctx.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
                )
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Vibrator::class.java)
                v?.vibrate(
                    VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            }
        }
    }
}
