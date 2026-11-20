package com.mosman.routines.volume

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumePanelSettingsTest {

    @Test
    fun defaultSettings_haveSafeDefaults() {
        val s = VolumePanelSettings()
        assertEquals(FloatingAction.VOLUME_DOWN, s.floatingDoubleTapAction)
        assertEquals(FloatingAction.VOLUME_UP, s.floatingLongPressAction)
        assertTrue(s.preserveSilentVibrate)
        assertEquals(1500L, s.cooldownDurationMs)
    }

    @Test
    fun jsonSerialization_roundTripsCorrectly() {
        val original = VolumePanelSettings(
            shakeEnabled = true,
            shakeSensitivity = Sensitivity.HIGH,
            floatingButtonEnabled = true,
            floatingDoubleTapAction = FloatingAction.VOLUME_DOWN,
            floatingLongPressAction = FloatingAction.VOLUME_UP,
            preserveSilentVibrate = true,
            statusUnmuteEnabled = true,
            statusUnmuteKeepLevel = true,
        )

        val json = original.toJson()
        val parsed = VolumePanelSettings.fromJson(json)

        assertEquals(true, parsed.statusUnmuteEnabled)
        assertEquals(true, parsed.statusUnmuteKeepLevel)

        assertEquals(original.shakeEnabled, parsed.shakeEnabled)
        assertEquals(original.shakeSensitivity, parsed.shakeSensitivity)
        assertEquals(original.floatingButtonEnabled, parsed.floatingButtonEnabled)
        assertEquals(original.floatingDoubleTapAction, parsed.floatingDoubleTapAction)
        assertEquals(original.floatingLongPressAction, parsed.floatingLongPressAction)
        assertEquals(original.preserveSilentVibrate, parsed.preserveSilentVibrate)
    }

    @Test
    fun jsonParsing_handlesMissingFieldsGracefully() {
        // Simulating legacy JSON from an older installation
        val legacyJson = JSONObject().apply {
            put("shakeEnabled", true)
            put("floatingButtonEnabled", true)
        }

        val parsed = VolumePanelSettings.fromJson(legacyJson)

        assertEquals(true, parsed.shakeEnabled)
        assertEquals(true, parsed.floatingButtonEnabled)
        // New fields take their safe defaults
        assertEquals(FloatingAction.VOLUME_DOWN, parsed.floatingDoubleTapAction)
        assertEquals(FloatingAction.VOLUME_UP, parsed.floatingLongPressAction)
        assertTrue(parsed.preserveSilentVibrate)
        // Settings saved before the WhatsApp feature existed leave it off.
        assertEquals(false, parsed.statusUnmuteEnabled)
        assertEquals(false, parsed.statusUnmuteKeepLevel)
    }

    @Test fun gestureJobs_roundTrip() {
        val s = VolumePanelSettings(
            shakeJob = ControlJob(FloatingAction.FLASHLIGHT),
            flipEnabled = true,
            flipJob = ControlJob(FloatingAction.RUN_ROUTINE, 42L),
            floatingLongPressAction = FloatingAction.DND_TOGGLE,
            floatingLongPressRoutineId = 7L,
        )
        val back = VolumePanelSettings.fromJson(JSONObject(s.toJson().toString()))
        assertEquals(s, back)
    }

    @Test fun oldSettings_keepOpeningTheVolumePanel() {
        // Saved before gestures had jobs: every gesture still does what it did (the volume panel).
        val parsed = VolumePanelSettings.fromJson(JSONObject("""{"shakeEnabled":true}"""))
        assertEquals(FloatingAction.VOLUME_PANEL, parsed.shakeJob.action)
        assertEquals(FloatingAction.VOLUME_PANEL, parsed.backTapJob.action)
        assertEquals(FloatingAction.VOLUME_PANEL, parsed.swipeJob.action)
        assertEquals(FloatingAction.VOLUME_PANEL, parsed.floatingTapJob.action)
        assertEquals(false, parsed.flipEnabled)
    }
}
