package com.mosman.routines

import org.junit.Assert.assertEquals
import org.junit.Test

class RevertFilterTest {
    private val before = listOf(Action.Volume(StreamType.RING, 60), Action.Ringer(RingerMode.SOUND))
    private val lectureMode = listOf(Action.Ringer(RingerMode.VIBRATE))

    @Test fun routineThatSetVibrateCanPutSoundBack() {
        val out = RevertFilter.filter(before, lectureMode, RingerMode.VIBRATE, preserveSilentVibrate = true)
        assertEquals(listOf(Action.Ringer(RingerMode.SOUND), Action.Volume(StreamType.RING, 60)), out)
    }

    @Test fun userMutedSomeOtherWayStaysMuted() {
        // The routine set Vibrate, but the user switched to Silent during class: leave it.
        val out = RevertFilter.filter(before, lectureMode, RingerMode.SILENT, preserveSilentVibrate = true)
        assertEquals(emptyList<Action>(), out)
    }

    @Test fun routineThatNeverTouchedRingerDoesNotUnmute() {
        val out = RevertFilter.filter(before, listOf(Action.Volume(StreamType.RING, 0)), RingerMode.VIBRATE, true)
        assertEquals(emptyList<Action>(), out)
    }

    @Test fun settingOffRestoresEverything() {
        val out = RevertFilter.filter(before, emptyList(), RingerMode.SILENT, preserveSilentVibrate = false)
        assertEquals(2, out.size)
        assertEquals(Action.Ringer(RingerMode.SOUND), out.first())
    }
}
