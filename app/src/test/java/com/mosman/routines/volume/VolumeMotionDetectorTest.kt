package com.mosman.routines.volume

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VolumeMotionDetectorTest {

    private var shakeCount = 0
    private var backTapCount = 0
    private var flipDownCount = 0
    private var flipUpCount = 0

    private lateinit var detector: VolumeMotionDetector

    @Before
    fun setUp() {
        shakeCount = 0
        backTapCount = 0
        flipDownCount = 0
        flipUpCount = 0

        detector = VolumeMotionDetector(
            onShakeDetected = { shakeCount++ },
            onBackTapDetected = { backTapCount++ },
            onFlipDetected = { isDown -> if (isDown) flipDownCount++ else flipUpCount++ },
        )
    }

    @Test
    fun stationaryPhone_doesNotTriggerShakeOrTap() {
        // Normal stationary phone resting flat (gravity = 9.81 on Z)
        for (i in 0..20) {
            detector.onSensorChanged(0f, 0f, 9.81f, now = i * 50L)
        }
        assertEquals(0, shakeCount)
        assertEquals(0, backTapCount)
    }

    @Test
    fun singleShakeMovement_doesNotTrigger() {
        // Move once strongly, then stay still
        detector.onSensorChanged(0f, 0f, 9.81f, now = 0L)
        detector.onSensorChanged(26f, 0f, 9.81f, now = 100L) // first shake
        detector.onSensorChanged(0f, 0f, 9.81f, now = 200L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 1200L) // after window expired

        assertEquals(0, shakeCount)
    }

    @Test
    fun twoDeliberateMovements_triggersShake() {
        detector.onSensorChanged(0f, 0f, 9.81f, now = 0L)
        // First shake movement
        detector.onSensorChanged(26f, 0f, 9.81f, now = 100L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 150L)
        // Second deliberate shake movement 150ms after the first
        detector.onSensorChanged(-26f, 0f, 9.81f, now = 250L)

        assertEquals(1, shakeCount)
    }

    /**
     * Feeds [seconds] of readings every [stepMs] ms with gravity [g] (x, y, z), plus a
     * side-to-side shake on X of [amp] m/s² at 4 Hz between 1 s and 3 s. Returns shakes seen.
     */
    private fun shakeIn(
        g: Triple<Float, Float, Float>,
        sensitivity: Sensitivity,
        amp: Float = 18f,
        shaking: Boolean = true,
        stepMs: Long = 20L,
        seconds: Int = 4,
    ): Int {
        var count = 0
        val d = VolumeMotionDetector(onShakeDetected = { count++ }, onBackTapDetected = {})
        var t = 0L
        while (t < seconds * 1000L) {
            val shake = if (shaking && t in 1000L until 3000L)
                amp * kotlin.math.sin(2 * Math.PI * 4 * t / 1000.0).toFloat() else 0f
            d.onSensorChanged(g.first + shake, g.second, g.third, now = t,
                shakeSensitivity = sensitivity, enableBackTap = false, enableFlip = false)
            t += stepMs
        }
        return count
    }

    private val flatUp = Triple(0f, 0f, 9.81f)
    private val upright = Triple(0f, 9.81f, 0f)
    private val faceDown = Triple(0f, 0f, -9.81f)

    @Test
    fun shake_isDetected_inEveryOrientation() {
        for (g in listOf(flatUp, upright, faceDown)) {
            for (s in listOf(Sensitivity.MEDIUM, Sensitivity.HIGH)) {
                assert(shakeIn(g, s) >= 1) { "no shake detected for gravity=$g sensitivity=$s" }
            }
        }
    }

    @Test
    fun shake_isDetected_atTheSlowSensorRateToo() {
        // SENSOR_DELAY_UI on a Pixel delivers a reading roughly every 66 ms.
        assert(shakeIn(upright, Sensitivity.MEDIUM, stepMs = 66L) >= 1)
    }

    @Test
    fun phoneHeldStill_neverShakes_evenOnHighSensitivity() {
        // The old detector treated gravity on Y (phone upright) as "already moving" on High.
        for (g in listOf(flatUp, upright, faceDown)) {
            assertEquals(0, shakeIn(g, Sensitivity.HIGH, shaking = false))
        }
    }

    @Test
    fun turningThePhoneOver_isNotAShake() {
        var count = 0
        val d = VolumeMotionDetector(onShakeDetected = { count++ }, onBackTapDetected = {})
        var t = 0L
        // Upright for a second, rotate to flat over 300 ms, then rest.
        while (t <= 3000L) {
            val p = ((t - 1000L).coerceIn(0L, 300L)) / 300.0
            val y = (9.81 * kotlin.math.cos(p * Math.PI / 2)).toFloat()
            val z = (9.81 * kotlin.math.sin(p * Math.PI / 2)).toFloat()
            d.onSensorChanged(0f, y, z, now = t, enableBackTap = false, enableFlip = false)
            t += 20L
        }
        assertEquals(0, count)
    }

    @Test
    fun shakingTheHeldPhone_isNotABackTap() {
        // Phone upright, shaken side to side, with the front-to-back wobble a real wrist adds.
        var taps = 0
        val d = VolumeMotionDetector(onShakeDetected = {}, onBackTapDetected = { taps++ })
        var t = 0L
        while (t < 4000L) {
            val on = t in 1000L until 3000L
            val sx = if (on) 18f * kotlin.math.sin(2 * Math.PI * 4 * t / 1000.0).toFloat() else 0f
            val wobble = if (on) 6f * kotlin.math.sin(2 * Math.PI * 9 * t / 1000.0).toFloat() else 0f
            d.onSensorChanged(sx, 9.81f, wobble, now = t, enableFlip = false)
            t += 20L
        }
        assertEquals(0, taps)
    }

    @Test
    fun doubleTapBack_triggersWhenTwoSpikesOccurInWindow() {
        // Baseline
        for (i in 0..5) {
            detector.onSensorChanged(0f, 0f, 9.81f, now = i * 20L)
        }

        // Tap 1: Sharp impulse on Z-axis
        detector.onSensorChanged(0f, 0f, 15.5f, now = 200L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 240L)
        assertEquals(0, backTapCount)

        // Tap 2: Second sharp impulse 240ms later (well within 100ms..600ms window)
        detector.onSensorChanged(0f, 0f, 16.0f, now = 440L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 480L)

        assertEquals(1, backTapCount)
    }

    @Test
    fun doubleTapBack_enforcesCooldown() {
        // Baseline
        for (i in 0..5) {
            detector.onSensorChanged(0f, 0f, 9.81f, now = i * 20L)
        }

        // First double tap
        detector.onSensorChanged(0f, 0f, 15.5f, now = 200L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 250L)
        detector.onSensorChanged(0f, 0f, 16.0f, now = 450L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 500L)
        assertEquals(1, backTapCount)

        // Another double tap within 1500ms cooldown (at 700L and 950L) should be ignored
        detector.onSensorChanged(0f, 0f, 16.0f, now = 700L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 750L)
        detector.onSensorChanged(0f, 0f, 16.0f, now = 950L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 1000L)
        assertEquals(1, backTapCount)

        // After cooldown expires (now = 2500L, > 1500ms since last trigger at 450L)
        detector.onSensorChanged(0f, 0f, 17.0f, now = 2500L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 2550L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 2600L)
        detector.onSensorChanged(0f, 0f, 17.0f, now = 2750L)
        detector.onSensorChanged(0f, 0f, 9.81f, now = 2800L)
        assertEquals(2, backTapCount)
    }
}
