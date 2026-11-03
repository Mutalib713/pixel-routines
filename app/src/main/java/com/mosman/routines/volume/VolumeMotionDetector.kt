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
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f

    // ---- Back-tap detection state ----
    private var lastFilteredZ = Float.NaN
    private var firstTapTime = -1L
    private var lastBackTapTrigger = -100_000L

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
            lastAccelX = x
            lastAccelY = y
            lastAccelZ = z
            return
        }

        val totalG = sqrt(x * x + y * y + z * z)

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

            // Find dominant movement axis
            val netZ = z - 9.81f
            val axis = when {
                abs(x) >= abs(y) && abs(x) >= abs(netZ) -> 0
                abs(y) >= abs(x) && abs(y) >= abs(netZ) -> 1
                else -> 2
            }
            val primaryVal = when (axis) {
                0 -> x
                1 -> y
                else -> netZ
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
                        0 -> x
                        1 -> y
                        else -> netZ
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

            if (!isViolentShake && zSpike > tapThresh && (now - lastBackTapTrigger > cooldownMs)) {
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

        lastAccelX = x
        lastAccelY = y
        lastAccelZ = z
    }

    fun reset() {
        shakeMoveCount = 0
        firstShakeTime = 0L
        firstTapTime = -1L
        lastFilteredZ = Float.NaN
    }
}
