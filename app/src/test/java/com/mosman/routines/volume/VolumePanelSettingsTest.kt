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
}
