package com.mosman.routines.volume

import android.media.AudioManager
import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusUnmuteTest {

    // Measured on the Pixel 6 Pro, 2026-11-14 (WhatsApp Business 2.26.44.90).
    private val statusCls = "com.whatsapp.status.playback.StatusPlaybackActivity"
    private val chatCls = "com.whatsapp.Conversation"

    private fun press(
        pkg: String? = "com.whatsapp",
        cls: String? = statusCls,
        resumedAt: Long = 10_000L,
        lastHandled: Long = 0L,
        ringer: Int = AudioManager.RINGER_MODE_VIBRATE,
        now: Long = 10_500L,
        lastPress: Long = 0L,
    ) = StatusUnmute.shouldPress(pkg, cls, resumedAt, lastHandled, ringer, now, lastPress)

    @Test fun statusOpening_onVibrate_presses() = assertTrue(press())

    @Test fun statusOpening_onSilent_presses() =
        assertTrue(press(ringer = AudioManager.RINGER_MODE_SILENT))

    @Test fun whatsAppBusiness_counts() = assertTrue(press(pkg = "com.whatsapp.w4b"))

    @Test fun ringerOnSound_doesNotPress() =
        assertFalse(press(ringer = AudioManager.RINGER_MODE_NORMAL))

    @Test fun chatScreen_doesNotPress() = assertFalse(press(cls = chatCls))

    @Test fun otherAppWithStatusLikeName_doesNotPress() =
        assertFalse(press(pkg = "com.instagram.android", cls = statusCls))

    @Test fun unknownClass_doesNotPress() = assertFalse(press(cls = null))

    @Test fun sameStatusScreen_isPressedOnlyOnce() =
        // The poller keeps seeing the same resume while the user taps through statuses.
        assertFalse(press(resumedAt = 10_000L, lastHandled = 10_000L))

    @Test fun reopeningStatuses_pressesAgain() =
        assertTrue(press(resumedAt = 20_000L, lastHandled = 10_000L, now = 20_400L, lastPress = 10_400L))

    @Test fun neverTwiceWithinTheGap() =
        assertFalse(press(resumedAt = 12_000L, lastHandled = 10_000L, now = 12_300L, lastPress = 10_400L))

    @Test fun keys_upOnly_byDefault() =
        assertArrayEquals(intArrayOf(KeyEvent.KEYCODE_VOLUME_UP), StatusUnmute.keysFor(keepLevel = false))

    @Test fun keys_upThenDown_whenKeepingLevel() =
        assertArrayEquals(
            intArrayOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN),
            StatusUnmute.keysFor(keepLevel = true),
        )
}
