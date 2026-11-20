package com.mosman.routines.ui.assistant

import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline assistant's contract: for every message, either the correct proposal or an
 * honest "I can't / I need more" answer. Never a crash, never a confidently wrong routine.
 * Every case below was a real failure of the Antigravity parser (probed 2026-11-14).
 */
class RoutineAIParserTest {

    @Test fun listQuestion_groupsRoutinesIntoSections() {
        val on = Routine(id = 1, name = "Class mode", triggers = listOf(Trigger.TimeOfDay(8, 0, setOf(1, 2, 3, 4, 5))),
            actions = listOf(Action.Ringer(RingerMode.SILENT)))
        val off = on.copy(id = 2, name = "Wake up", enabled = false)
        val r = RoutineAIParser.parse("what routines do I have?", null, listOf(on, off), activeIds = emptySet())
        assertTrue(r.explanation, r.explanation.contains("## On") && r.explanation.contains("## Paused"))
        assertTrue(r.explanation, r.explanation.contains("- **Class mode**"))
        val running = RoutineAIParser.parse("what's running", null, listOf(on, off), activeIds = setOf(1L))
        assertTrue(running.explanation, running.explanation.contains("## Running now"))
    }

    private val nightRoutine = Routine(id = 11, name = "Night Routine",
        triggers = listOf(Trigger.TimeOfDay(22, 0, (1..7).toSet())),
        actions = listOf(Action.Ringer(RingerMode.SILENT)))
    private val batterySaver = Routine(id = 12, name = "Battery Saver",
        triggers = listOf(Trigger.Battery(below = true, level = 20)),
        actions = listOf(Action.BatterySaver(true), Action.Brightness(25)))
    private val studyRoutine = Routine(id = 13, name = "Old Study Routine",
        triggers = listOf(Trigger.TimeOfDay(14, 0, (1..5).toSet())),
        actions = listOf(Action.Dnd(true)))
    private val lectureMode = Routine(id = 14, name = "Lecture Mode",
        triggers = listOf(Trigger.TimeOfDay(8, 0, (1..5).toSet())),
        actions = listOf(Action.Ringer(RingerMode.VIBRATE)))
    private val saved = listOf(nightRoutine, batterySaver, studyRoutine, lectureMode)

    private fun created(text: String, routines: List<Routine> = emptyList()): Routine {
        val r = RoutineAIParser.parse(text, saved = routines)
        val d = assertNotNull("expected a proposal for: $text", r.draft).let { r.draft!! }
        assertEquals(DraftOperation.CREATE, d.operation)
        return d.proposedRoutine!!
    }

    private fun time(r: Routine) = r.triggers.filterIsInstance<Trigger.TimeOfDay>().single()

    // ---- Managing saved routines ----

    @Test fun pause_targetsTheSavedRoutine() {
        val d = RoutineAIParser.parse("Pause Night Routine", saved = saved).draft!!
        assertEquals(DraftOperation.DISABLE, d.operation)
        assertEquals(11L, d.targetRoutineId)
    }

    @Test fun pause_unknownRoutine_saysSoInsteadOfCreatingOne() {
        val r = RoutineAIParser.parse("Pause Night Routine", saved = emptyList())
        assertNull(r.draft)
        assertTrue(r.explanation.contains("couldn't find", ignoreCase = true))
    }

    @Test fun delete_targetsTheSavedRoutine() {
        val d = RoutineAIParser.parse("Delete my old study routine", saved = saved).draft!!
        assertEquals(DraftOperation.DELETE, d.operation)
        assertEquals(13L, d.targetRoutineId)
    }

    @Test fun resume_enablesTheSavedRoutine() {
        val paused = listOf(nightRoutine.copy(enabled = false))
        val d = RoutineAIParser.parse("Turn on night routine", saved = paused).draft!!
        assertEquals(DraftOperation.ENABLE, d.operation)
        assertEquals(11L, d.targetRoutineId)
    }

    @Test fun question_explainsTheRealSavedConfiguration() {
        val r = RoutineAIParser.parse("What does Battery Saver do?", saved = saved)
        assertNull(r.draft)
        assertTrue(r.explanation, r.explanation.contains("20%"))
        assertTrue(r.explanation, r.explanation.contains("aver", ignoreCase = true))
    }

    @Test fun whenSentence_isARequest_notAQuestionAboutARoutine() {
        // His exact message on the Pixel; he has a saved routine literally called "volume".
        val volume = Routine(id = 15, name = "volume",
            triggers = listOf(Trigger.Gesture(com.mosman.routines.GestureType.SHAKE)),
            actions = listOf(Action.Volume(com.mosman.routines.StreamType.RING, 60)))
        val r = RoutineAIParser.parse(
            "when I plug in my charger at night turn in dark mode and set media volume to 20.percent",
            saved = listOf(volume),
        )
        assertFalse("must not describe the 'volume' routine: ${r.explanation}", r.explanation.contains("is paused") || r.explanation.contains("switched on"))
        // The offline assistant has no charger rule, so it must hand this to Gemini in Automatic mode.
        assertFalse(r.handled)
    }

    @Test fun whenQuestionWithAQuestionMark_stillExplains() {
        val r = RoutineAIParser.parse("When does Night Routine run?", saved = saved)
        assertNull(r.draft)
        assertTrue(r.explanation, r.explanation.contains("22:00"))
    }

    @Test fun question_aboutUnknownRoutine_doesNotCrashOrCreate() {
        val r = RoutineAIParser.parse("What does Battery Saver do?", saved = emptyList())
        assertNull(r.draft)
    }

    @Test fun editingASavedRoutine_isHonestlyNotSupportedOffline() {
        val r = RoutineAIParser.parse("Make Lecture Mode activate 10 minutes early", saved = saved)
        assertNull(r.draft)
        assertTrue(r.explanation.contains("Lecture Mode"))
    }

    // ---- Creating routines ----

    @Test fun saveBattery_withoutAPercent_doesNotCrash() {
        val r = created("Save battery automatically")
        val t = r.triggers.single() as Trigger.Battery
        assertEquals(20, t.level)
        assertTrue(r.actions.any { it is Action.BatterySaver })
    }

    @Test fun brightnessPercent_isNotMistakenForABatteryLevel() {
        val r = created("Set brightness to 30% at 7pm")
        assertEquals(19, time(r).hour)
        assertTrue(r.triggers.none { it is Trigger.Battery })
        assertEquals(listOf<Action>(Action.Brightness(30)), r.actions)
    }

    @Test fun friend_isNotFriday_andAReminderIsAReminder() {
        val r = created("Remind me to call my friend at 6pm")
        assertEquals(18, time(r).hour)
        assertEquals((1..7).toSet(), time(r).days)
        assertEquals(listOf<Action>(Action.Remind("call my friend")), r.actions)
    }

    @Test fun weekdayReminder_withBrightness() {
        val r = created("Every weekday at 6 AM, reduce brightness and remind me to study")
        assertEquals(6, time(r).hour)
        assertEquals((1..5).toSet(), time(r).days)
        assertTrue(r.actions.contains(Action.Brightness(30)))
        assertTrue(r.actions.contains(Action.Remind("study")))
        assertTrue("the reminder text must not switch on study mode", r.actions.none { it is Action.Dnd })
    }

    @Test fun during_doesNotMeanRing() {
        val r = created("Turn on vibrate during my 8am class")
        assertEquals(8, time(r).hour)
        assertTrue(r.actions.contains(Action.Ringer(RingerMode.VIBRATE)))
        assertTrue(r.actions.none { it == Action.Ringer(RingerMode.SOUND) })
    }

    @Test fun bedtime_at1030pm_everyDay() {
        val r = created("Bedtime mode at 10:30 PM")
        assertEquals(22, time(r).hour)
        assertEquals(30, time(r).minute)
        assertEquals((1..7).toSet(), time(r).days)
    }

    @Test fun timeRange_setsAnEnd() {
        val r = created("Study focus: Mute phone from 2pm to 5pm")
        assertEquals(14, time(r).hour)
        assertEquals(17, (r.endTriggers.single() as Trigger.TimeOfDay).hour)
        assertTrue(r.actions.contains(Action.Ringer(RingerMode.SILENT)))
    }

    @Test fun aDate_isNotReadAsATime() {
        val r = created("Silence my phone on 2026-11-20 at 9am")
        assertEquals(9, time(r).hour)
    }

    @Test fun explicitDays() {
        val r = created("Vibrate on Monday and Wednesday at 8am")
        assertEquals(setOf(1, 3), time(r).days)
    }

    @Test fun nothingToDo_asksInsteadOfGuessing() {
        val r = RoutineAIParser.parse("hello")
        assertNull(r.draft)
        assertFalse(r.explanation.isBlank())
    }

    @Test fun neverThrows_onOddInput() {
        listOf("", "   ", "%%%", "battery", "at", "99999 to 88888", "remind me to", "50% 50% 50%")
            .forEach { RoutineAIParser.parse(it, saved = saved) }
    }

    // ---- Photos ----

    @Test fun randomPhotoText_neverBecomesARoutine() {
        // A random photo still has stray text in it: brand names, numbers, signs.
        listOf(
            "SAMSUNG\nGalaxy\n5G",
            "EXIT\n3",
            "MTN\n024 455 1234",
            "Shoprite\nTotal 45.50\n12/11/2026",
            "2",
        ).forEach { ocr ->
            val r = RoutineAIParser.parse("", extractedOcrText = ocr)
            assertNull("made a routine from: $ocr", r.draft)
        }
    }

    @Test fun lensMessage_showsWhatWasRead() {
        val m = com.mosman.routines.ui.lens.lensNoRoutineMessage("I couldn't tell what you'd like.", "SAMSUNG\nGalaxy\n5G")
        assertTrue(m, m.contains("no time or schedule") && m.contains("SAMSUNG"))
    }

    @Test fun timetableWithSeveralClassTimes_isNotCollapsedIntoOneRoutine() {
        val ocr = """
            KNUST BIT 1 TIMETABLE 2026
            MONDAY 8:00-10:00 BIT 152 Room 330
            TUESDAY 10:30-12:30 MATH 151 SCB-SF7
            THURSDAY 14:00-16:00 COMS 158 FF1
        """.trimIndent()
        val r = RoutineAIParser.parse("", extractedOcrText = ocr)
        assertNull(r.draft)
        assertTrue(r.explanation, r.explanation.contains("3"))
    }
}
