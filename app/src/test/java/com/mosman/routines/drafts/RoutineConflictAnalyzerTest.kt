package com.mosman.routines.drafts

import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineConflictAnalyzerTest {

    @Test
    fun detectsDirectOpposingRingerConflict() {
        val existing = Routine(
            id = 101L,
            name = "Work Focus",
            enabled = true,
            triggers = listOf(Trigger.TimeOfDay(hour = 9, minute = 0, days = setOf(1, 2, 3, 4, 5))),
            actions = listOf(Action.Ringer(RingerMode.SOUND)),
        )

        val proposed = Routine(
            id = 0L,
            name = "Lecture Mode",
            triggers = listOf(Trigger.TimeOfDay(hour = 9, minute = 30, days = setOf(1, 3, 5))),
            actions = listOf(Action.Ringer(RingerMode.VIBRATE)),
        )

        val conflicts = RoutineConflictAnalyzer.analyze(proposed, listOf(existing))

        assertEquals(1, conflicts.size)
        val c = conflicts.first()
        assertEquals(ConflictSeverity.CONFLICT, c.severity)
        assertEquals("Ringer mode", c.settingName)
        assertEquals(101L, c.existingRoutineId)
        assertTrue(c.message.contains("Lecture Mode"))
        assertTrue(c.message.contains("Work Focus"))
    }

    @Test
    fun detectsDndOpposingConflict() {
        val existing = Routine(
            id = 202L,
            name = "Night Calm",
            enabled = true,
            triggers = listOf(Trigger.TimeOfDay(hour = 22, minute = 0, days = setOf(1, 2, 3, 4, 5, 6, 7))),
            actions = listOf(Action.Dnd(on = true)),
        )

        val proposed = Routine(
            id = 0L,
            name = "Emergency Night",
            triggers = listOf(Trigger.TimeOfDay(hour = 22, minute = 15, days = setOf(5, 6))),
            actions = listOf(Action.Dnd(on = false)),
        )

        val conflicts = RoutineConflictAnalyzer.analyze(proposed, listOf(existing))
        assertEquals(1, conflicts.size)
        assertEquals(ConflictSeverity.CONFLICT, conflicts[0].severity)
        assertEquals("Do Not Disturb", conflicts[0].settingName)
    }

    @Test
    fun noConflictWhenSchedulesDoNotOverlap() {
        val existing = Routine(
            id = 303L,
            name = "Morning Wakeup",
            enabled = true,
            triggers = listOf(Trigger.TimeOfDay(hour = 7, minute = 0, days = setOf(1, 2, 3))),
            actions = listOf(Action.Ringer(RingerMode.SOUND)),
        )

        val proposed = Routine(
            id = 0L,
            name = "Evening Study",
            triggers = listOf(Trigger.TimeOfDay(hour = 19, minute = 0, days = setOf(1, 2, 3))),
            actions = listOf(Action.Ringer(RingerMode.SILENT)),
        )

        val conflicts = RoutineConflictAnalyzer.analyze(proposed, listOf(existing))
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun ignoresDisabledRoutines() {
        val disabled = Routine(
            id = 404L,
            name = "Old Study",
            enabled = false,
            triggers = listOf(Trigger.TimeOfDay(hour = 10, minute = 0, days = setOf(1, 2, 3, 4, 5))),
            actions = listOf(Action.Ringer(RingerMode.SOUND)),
        )

        val proposed = Routine(
            id = 0L,
            name = "New Study",
            triggers = listOf(Trigger.TimeOfDay(hour = 10, minute = 0, days = setOf(1, 2, 3, 4, 5))),
            actions = listOf(Action.Ringer(RingerMode.VIBRATE)),
        )

        val conflicts = RoutineConflictAnalyzer.analyze(proposed, listOf(disabled))
        assertTrue(conflicts.isEmpty())
    }
}
