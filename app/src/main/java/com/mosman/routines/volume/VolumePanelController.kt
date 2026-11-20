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
     * Steps the active volume up or down with UI feedback and haptic vibration.
     *
     * @param ctx Android Context
     * @param direction AudioManager.ADJUST_RAISE or AudioManager.ADJUST_LOWER
     */
    fun adjustVolume(ctx: Context, direction: Int): Boolean {
        val appContext = ctx.applicationContext
        val am = appContext.getSystemService(AudioManager::class.java) ?: return false
        // Not adjustSuggestedStreamVolume: when nothing is playing, AudioService swallows the
        // first "suggested" adjustment and only opens the panel (it treats it like the first
        // press of a hardware volume key), so a single double-tap/long-press changed nothing.
        // Adjusting a named stream skips that suppression. Media is what the Pixel volume
        // panel shows by default; during a call, adjust the call instead.
        val stream = when (am.mode) {
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val dispatched = runCatching {
            am.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
            true
        }.getOrDefault(false)

        if (dispatched) {
            val settings = VolumePanelStore.load(appContext)
            if (settings.hapticFeedbackEnabled) {
                performHaptic(appContext)
            }
        }
        return dispatched
    }

    /**
     * Executes the designated floating button action.
     */
    fun executeFloatingAction(ctx: Context, action: FloatingAction, routineId: Long = 0L) {
        perform(ctx, ControlJob(action, routineId), fromButton = true)
    }

    /**
     * Does one gesture's job. [fromButton] = a deliberate tap on the floating button, which skips
     * the cooldown and screen-on checks that protect against accidental shakes and pocket taps.
     * Toggles say what they did in a short toast, using the result the action itself reports
     * (Sacred Rule 4: only what actually happened).
     */
    fun perform(ctx: Context, job: ControlJob, fromButton: Boolean = false) {
        val app = ctx.applicationContext
        if (job.action == FloatingAction.VOLUME_PANEL) { show(app, ignoreCooldown = fromButton); return }
        if (!fromButton && !claimTrigger(app)) return
        val settings = VolumePanelStore.load(app)
        when (job.action) {
            FloatingAction.VOLUME_PANEL, FloatingAction.NONE -> Unit
            FloatingAction.VOLUME_DOWN -> adjustVolume(app, AudioManager.ADJUST_LOWER)
            FloatingAction.VOLUME_UP -> adjustVolume(app, AudioManager.ADJUST_RAISE)
            FloatingAction.FLASHLIGHT -> {
                torchWatch(app)
                toast(app, com.mosman.routines.Actions.runOne(app, com.mosman.routines.Action.Flashlight(!torchOn)))
            }
            FloatingAction.RINGER_TOGGLE -> {
                val am = app.getSystemService(AudioManager::class.java)
                val to = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL)
                    com.mosman.routines.RingerMode.VIBRATE else com.mosman.routines.RingerMode.SOUND
                toast(app, com.mosman.routines.Actions.runOne(app, com.mosman.routines.Action.Ringer(to)))
            }
            FloatingAction.DND_TOGGLE -> {
                val nm = app.getSystemService(android.app.NotificationManager::class.java)
                val on = nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                toast(app, com.mosman.routines.Actions.runOne(app, com.mosman.routines.Action.Dnd(on)))
            }
            FloatingAction.RUN_ROUTINE -> {
                val r = com.mosman.routines.Store.get(app, job.routineId)
                if (r == null) toast(app, "That routine no longer exists. Pick another in Controls.")
                else com.mosman.routines.Engine.runNow(app, r) { toast(app, "Ran “${r.name.trim()}”") }
            }
        }
        if (settings.hapticFeedbackEnabled && job.action != FloatingAction.NONE) performHaptic(app)
    }

    /** Cooldown + screen-on gate for gesture jobs other than the volume panel (which has its own). */
    private fun claimTrigger(ctx: Context): Boolean {
        if (!canTriggerNow(ctx)) return false
        lastTriggerTimestamp.set(System.currentTimeMillis())
        return true
    }

    private fun toast(ctx: Context, text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Android has no "is the torch on?" call, only a callback, so we listen once and remember.
    @Volatile private var torchOn = false
    @Volatile private var torchWatching = false
    fun torchWatch(ctx: Context) {
        if (torchWatching) return
        torchWatching = true
        runCatching {
            ctx.getSystemService(android.hardware.camera2.CameraManager::class.java).registerTorchCallback(
                object : android.hardware.camera2.CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchOn = enabled }
                },
                android.os.Handler(android.os.Looper.getMainLooper()),
            )
        }
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
