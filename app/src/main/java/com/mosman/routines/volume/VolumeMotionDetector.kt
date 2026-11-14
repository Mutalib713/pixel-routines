package com.mosman.routines.volume

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-performance, lifecycle-aware motion detector for:
 * 1. Deliberate Shake (requires 2 directional movements: strong impulse + reversal within 80-850ms)
 * 2. Double-Tap Back (detects two sharp Z-axis impulses spaced 100ms-600ms apart)
 * 3. Face-Down / Face-Up flip gestures
 */
class VolumeMotionDetector(
    private val onShakeDetected: () -> Unit,
    private val onBackTapDetected: () -> Unit,
    private val onFlipDetected: ((Boolean) -> Unit)? = null,
) {
    // ---- Shake detection state ----
    private var shakeMoveCount = 0
    private var firstShakeTime = 0L
    private var firstShakeAxis = 0 // 0 = X, 1 = Y, 2 = Z
    private var firstShakeSign = 0 // +1 or -1
    private var lastShakeTrigger = -100_000L

    // ---- Gravity estimate (slow low-pass filter of the raw reading) ----
    // The accelerometer always includes gravity, on whichever axis points down. Shake used to
    // assume the phone lay flat face-up (gravity on +Z only), so held upright or face-down it
    // either never fired or locked onto gravity itself. Subtracting this estimate leaves only
    // the hand's movement, whatever the orientation.
    private var gravX = 0f
    private var gravY = 0f
    private var gravZ = 0f
    private var lastSampleTime = 0L

    // ---- Back-tap detection state ----
    private var lastFilteredZ = Float.NaN
    private var firstTapTime = -1L
    private var lastBackTapTrigger = -100_000L
    private var lastLateralMotion = -100_000L

    // ---- Flip detection state ----
    private var faceDown = false
    private var lastFlipTime = -100_000L

    fun onSensorChanged(
        x: Float,
        y: Float,
        z: Float,
        now: Long = System.currentTimeMillis(),
        shakeSensitivity: Sensitivity = Sensitivity.MEDIUM,
        backTapSensitivity: Sensitivity = Sensitivity.MEDIUM,
        cooldownMs: Long = 1500L,
        enableShake: Boolean = true,
        enableBackTap: Boolean = true,
        enableFlip: Boolean = true,
    ) {
        // Initialize filtered Z on first sample
        if (lastFilteredZ.isNaN()) {
            lastFilteredZ = z
            gravX = x
            gravY = y
            gravZ = z
            lastSampleTime = now
            return
        }

        val totalG = sqrt(x * x + y * y + z * z)

        // Time-based low-pass, so it behaves the same at 15 or 50 samples per second.
        val dt = (now - lastSampleTime).coerceIn(1L, 200L).toFloat()
        lastSampleTime = now
        val alpha = GRAVITY_TAU_MS / (GRAVITY_TAU_MS + dt)
        gravX = alpha * gravX + (1 - alpha) * x
        gravY = alpha * gravY + (1 - alpha) * y
        gravZ = alpha * gravZ + (1 - alpha) * z
        val linX = x - gravX
        val linY = y - gravY
        val linZ = z - gravZ

        // 1. Flip Detection (settled near 1g gravity)
        if (enableFlip && onFlipDetected != null) {
            val settled = abs(totalG - 9.81f) < 1.5f
            if (settled && (now - lastFlipTime > 700L)) {
                val nowDown = z < -8.5f
                val nowUp = z > 8.5f
                if (nowDown && !faceDown) {
                    faceDown = true
                    lastFlipTime = now
                    onFlipDetected.invoke(true)
                } else if (nowUp && faceDown) {
                    faceDown = false
                    lastFlipTime = now
                    onFlipDetected.invoke(false)
                }
            }
        }

        // 2. Shake Detection (Two deliberate movements: an impulse and an opposing reversal)
        if (enableShake) {
            val shakeThresh = shakeSensitivity.shakeThreshold()

            // Find dominant movement axis (gravity removed)
            val axis = when {
                abs(linX) >= abs(linY) && abs(linX) >= abs(linZ) -> 0
                abs(linY) >= abs(linX) && abs(linY) >= abs(linZ) -> 1
                else -> 2
            }
            val primaryVal = when (axis) {
                0 -> linX
                1 -> linY
                else -> linZ
            }

            if (now - lastShakeTrigger > cooldownMs) {
                if (shakeMoveCount == 0) {
                    if (abs(primaryVal) > shakeThresh) {
                        shakeMoveCount = 1
                        firstShakeTime = now
                        firstShakeAxis = axis
                        firstShakeSign = if (primaryVal > 0) 1 else -1
                    }
                } else if (shakeMoveCount == 1) {
                    val gap = now - firstShakeTime
                    val currentValOnFirstAxis = when (firstShakeAxis) {
                        0 -> linX
                        1 -> linY
                        else -> linZ
                    }
                    // Deliberate reversal: moving opposite to the first direction
                    val reversed = (currentValOnFirstAxis * firstShakeSign) < -(shakeThresh * 0.7f)

                    if (reversed && gap in 80L..850L) {
                        shakeMoveCount = 0
                        firstShakeTime = 0L
                        lastShakeTrigger = now
                        onShakeDetected()
                    } else if (gap > 850L) {
                        // Window expired
                        if (abs(primaryVal) > shakeThresh) {
                            shakeMoveCount = 1
                            firstShakeTime = now
                            firstShakeAxis = axis
                            firstShakeSign = if (primaryVal > 0) 1 else -1
                        } else {
                            shakeMoveCount = 0
                        }
                    }
                }
            } else if (shakeMoveCount > 0 && (now - firstShakeTime > 900L)) {
                shakeMoveCount = 0
            }
        }

        // 3. Double-Tap Back Detection (Sharp Z-axis impulse spikes)
        if (enableBackTap) {
            val zSpike = abs(z - lastFilteredZ)
            lastFilteredZ = 0.7f * lastFilteredZ + 0.3f * z

            val tapThresh = backTapSensitivity.backTapZThreshold()
            // Guard: Don't treat a violent whole-phone shake as a gentle back-tap
            val isViolentShake = abs(totalG - 9.81f) > 12.0f

            // A knock on the back pushes the phone front-to-back (Z). Shaking or handling it
            // moves it sideways too, and the wobble that comes with that produced Z spikes:
            // on a Pixel 6 Pro, 4 "back-taps" fired during a 3-shake test. So ignore spikes
            // while, and shortly after, the phone is moving sideways.
            val lateral = maxOf(abs(linX), abs(linY))
            if (lateral > LATERAL_MOTION_MS2) lastLateralMotion = now
            val handling = now - lastLateralMotion < LATERAL_QUIET_MS
            val zDominant = abs(linZ) > lateral * 1.5f

            if (!isViolentShake && !handling && zDominant && zSpike > tapThresh &&
                (now - lastBackTapTrigger > cooldownMs)) {
                if (firstTapTime == -1L) {
                    firstTapTime = now
                } else {
                    val tapGap = now - firstTapTime
                    if (tapGap in 100L..600L) {
                        firstTapTime = -1L
                        lastBackTapTrigger = now
                        onBackTapDetected()
                    } else if (tapGap > 600L) {
                        firstTapTime = now
                    }
                }
            } else if (firstTapTime != -1L && (now - firstTapTime > 650L)) {
                firstTapTime = -1L
            }
        }

    }

    fun reset() {
        shakeMoveCount = 0
        firstShakeTime = 0L
        firstTapTime = -1L
        lastFilteredZ = Float.NaN   // also makes the next sample re-seed the gravity estimate
    }

    private companion object {
        /** Gravity filter time constant: slow enough to ignore a 2–6 Hz shake, fast enough to
         *  follow the phone being turned over within about a second. */
        const val GRAVITY_TAU_MS = 400f

        /** Sideways movement (m/s², gravity removed) that counts as "being handled". */
        const val LATERAL_MOTION_MS2 = 4f

        /** How long after sideways movement a Z spike is still not trusted as a knock. */
        const val LATERAL_QUIET_MS = 600L
    }
}
