package com.mosman.routines.ai

import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.StreamType
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gemini only suggests; these pin down what the phone accepts from it. */
class GeminiProposalMapperTest {

    private val night = Routine(id = 11, name = "Night Routine",
        triggers = listOf(Trigger.TimeOfDay(22, 0, (1..7).toSet())), actions = listOf(Action.Dnd(true)))

    @Test fun createsAValidRoutine() {
        val json = """{"reply":"Here you go.","operation":"CREATE","routine":{"name":"Charging night",
            "triggerKind":"CHARGER_CONNECTED","actions":[{"kind":"DARK_THEME","on":true},
            {"kind":"MEDIA_VOLUME","percent":20}]}}"""
        val r = GeminiProposalMapper.map(json, emptyList())
        val routine = assertNotNull(r.draft).let { r.draft!!.proposedRoutine!! }
        assertEquals(DraftOperation.CREATE, r.draft!!.operation)
        assertEquals(listOf<Trigger>(Trigger.Power(true)), routine.triggers)
        assertEquals(listOf(Action.DarkTheme(true), Action.Volume(StreamType.MEDIA, 20)), routine.actions)
    }

    @Test fun geminisOwnClaimsNeverReachTheCard() {
        // Seen on the emulator: Gemini said "I have created a routine" before anything was saved.
        val json = """{"reply":"I have created a routine for you.","operation":"CREATE","routine":{"name":"x",
            "triggerKind":"CHARGER_CONNECTED","actions":[{"kind":"DARK_THEME","on":true}]}}"""
        val r = GeminiProposalMapper.map(json, emptyList())
        assertFalse(r.explanation.contains("created", ignoreCase = true))
        assertTrue("no days note for a charger trigger", r.draft!!.questions.none { it.contains("days") })
    }

    @Test fun timeRoutineWithEnd() {
        val json = """{"reply":"ok","operation":"CREATE","routine":{"name":"Lecture","triggerKind":"TIME",
            "hour":10,"minute":0,"days":[1,3],"endHour":12,"actions":[{"kind":"RINGER","ringerMode":"VIBRATE"}]}}"""
        val routine = GeminiProposalMapper.map(json, emptyList()).draft!!.proposedRoutine!!
        assertEquals(Trigger.TimeOfDay(10, 0, setOf(1, 3)), routine.triggers.single())
        assertEquals(Trigger.TimeOfDay(12, 0, setOf(1, 3)), routine.endTriggers.single())
        assertEquals(listOf<Action>(Action.Ringer(RingerMode.VIBRATE)), routine.actions)
    }

    @Test fun disable_matchesASavedNameExactly() {
        val d = GeminiProposalMapper.map("""{"reply":"Pausing.","operation":"DISABLE","target":"night routine"}""",
            listOf(night)).draft!!
        assertEquals(DraftOperation.DISABLE, d.operation)
        assertEquals(11L, d.targetRoutineId)
    }

    @Test fun delete_ofAnInventedName_isRefused() {
        val r = GeminiProposalMapper.map("""{"reply":"Deleting.","operation":"DELETE","target":"Gym"}""", listOf(night))
        assertNull(r.draft)
        assertTrue(r.explanation.contains("couldn't find"))
    }

    @Test fun unknownActionsAreDroppedAndSaidOutLoud() {
        val json = """{"reply":"ok","operation":"CREATE","routine":{"name":"x","triggerKind":"TIME","hour":7,
            "actions":[{"kind":"SEND_MONEY"},{"kind":"DND","on":true}]}}"""
        val d = GeminiProposalMapper.map(json, emptyList()).draft!!
        assertEquals(listOf<Action>(Action.Dnd(true)), d.proposedRoutine!!.actions)
        assertTrue(d.questions.any { it.contains("send money") })
    }

    @Test fun impossibleValuesFailTheAppsChecks() {
        val json = """{"reply":"ok","operation":"CREATE","routine":{"name":"x","triggerKind":"TIME","hour":31,
            "actions":[{"kind":"DND","on":true}]}}"""
        val r = GeminiProposalMapper.map(json, emptyList())
        assertNull(r.draft)
        assertTrue(r.explanation.contains("checks"))
    }

    @Test fun noUsableActions_noProposal() {
        val json = """{"reply":"ok","operation":"CREATE","routine":{"name":"x","triggerKind":"TIME","hour":7,"actions":[]}}"""
        assertNull(GeminiProposalMapper.map(json, emptyList()).draft)
    }

    @Test fun plainAnswer_hasNoProposal() {
        val r = GeminiProposalMapper.map("""{"reply":"Pixel Routines can't send money.","operation":"NONE"}""", emptyList())
        assertNull(r.draft)
        assertEquals("Pixel Routines can't send money.", r.explanation)
    }

    @Test fun brokenJson_isNotUsed() {
        assertNull(GeminiProposalMapper.map("not json {", emptyList()).draft)
        assertNull(GeminiProposalMapper.map(null, emptyList()).draft)
    }

    @Test fun offlineOnly_neverGoesOnline() {
        assertFalse(AssistantPrefs.wantsOnline(AssistantMode.OFFLINE_ONLY, offlineHandled = false, hasPhoto = false))
    }

    @Test fun automatic_goesOnlineOnlyWhenOfflineCouldNotHandleIt() {
        assertFalse(AssistantPrefs.wantsOnline(AssistantMode.AUTOMATIC, offlineHandled = true, hasPhoto = false))
        assertTrue(AssistantPrefs.wantsOnline(AssistantMode.AUTOMATIC, offlineHandled = false, hasPhoto = false))
    }

    @Test fun photosStayOnThePhone() {
        assertFalse(AssistantPrefs.wantsOnline(AssistantMode.ONLINE, offlineHandled = false, hasPhoto = true))
    }
}
