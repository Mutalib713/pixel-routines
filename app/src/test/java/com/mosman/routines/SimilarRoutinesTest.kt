package com.mosman.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarRoutinesTest {
    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val vibrate = Action.Ringer(RingerMode.VIBRATE)

    // His real ones: "Lectures" 08:00-17:00 weekdays, and Lecture Mode's Tuesday 07:55-09:55 block.
    private val lectures = Routine(
        id = 1, name = "Lectures", actions = listOf(vibrate),
        triggers = listOf(Trigger.TimeOfDay(8, 0, weekdays)),
        endTriggers = listOf(Trigger.TimeOfDay(17, 0, weekdays)),
    )
    private val lectureMode = Routine(
        id = 2, name = "Lecture Mode", actions = listOf(vibrate),
        triggers = listOf(Trigger.TimeOfDay(7, 55, setOf(2))),
        endTriggers = listOf(Trigger.TimeOfDay(9, 55, setOf(2))),
    )

    @Test fun overlappingTimesThatBothSetTheRingerAreFlagged() {
        val hits = SimilarRoutines.find(lectureMode, listOf(lectures))
        assertEquals("Lectures", hits.single().routine.name)
        assertTrue(hits.single().reason.contains("overlapping"))
        assertTrue(hits.single().reason.contains("ringer"))
    }

    @Test fun sameTimeButDifferentSettingIsFine() {
        val brightness = lectureMode.copy(id = 3, name = "Dim", actions = listOf(Action.Brightness(20)))
        assertTrue(SimilarRoutines.find(brightness, listOf(lectures)).isEmpty())
    }

    @Test fun differentDaysDoNotOverlap() {
        val weekend = lectureMode.copy(id = 4, name = "Weekend",
            triggers = listOf(Trigger.TimeOfDay(9, 0, setOf(6, 7))),
            endTriggers = listOf(Trigger.TimeOfDay(10, 0, setOf(6, 7))))
        assertTrue(SimilarRoutines.find(weekend, listOf(lectures)).isEmpty())
    }

    @Test fun sameGestureAlwaysFlagged() {
        val a = Routine(id = 5, name = "Torch", triggers = listOf(Trigger.Gesture(GestureType.SHAKE)),
            actions = listOf(Action.Flashlight(true)))
        val b = Routine(id = 6, name = "Quiet", triggers = listOf(Trigger.Gesture(GestureType.SHAKE)),
            actions = listOf(vibrate))
        assertTrue(SimilarRoutines.find(b, listOf(a)).single().reason.contains("both would run"))
    }

    @Test fun sameNameFlaggedAndItselfIgnored() {
        assertTrue(SimilarRoutines.find(lectures, listOf(lectures)).isEmpty())
        val again = lectures.copy(id = 9, name = " lectures ")
        assertTrue(SimilarRoutines.find(again, listOf(lectures)).single().reason.contains("same name"))
    }

    @Test fun twoCopiesBecomeOneLine() {
        val copy = lectureMode.copy(id = 7)
        val hits = SimilarRoutines.find(lectures.copy(id = 8, name = "Class mode"), listOf(lectureMode, copy))
        assertEquals(2, hits.size)
        val lines = SimilarRoutines.lines(hits)
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("you have 2 of these"))
    }
}
