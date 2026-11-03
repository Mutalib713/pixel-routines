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
