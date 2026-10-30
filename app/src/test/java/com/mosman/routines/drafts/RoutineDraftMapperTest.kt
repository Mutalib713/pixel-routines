package com.mosman.routines.drafts

import com.mosman.routines.Action
import com.mosman.routines.Condition
import com.mosman.routines.EndMode
import com.mosman.routines.Match
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftMapperTest {

    @Test
    fun roundtripDraftSerialization() {
        val routine = Routine(
            id = 555L,
            name = "Lecture Mode KNUST",
            icon = "school",
            enabled = true,
            notifyOnRun = true,
            match = Match.ALL,
            triggers = listOf(
                Trigger.TimeOfDay(hour = 8, minute = 30, days = setOf(1, 2, 3, 4, 5)),
            ),
            conditions = listOf(
                Condition.WhileCharging(false),
            ),
            actions = listOf(
                Action.Ringer(RingerMode.VIBRATE),
                Action.Dnd(on = true),
                Action.Brightness(30),
            ),
            endTriggers = listOf(
                Trigger.TimeOfDay(hour = 10, minute = 30, days = setOf(1, 2, 3, 4, 5)),
            ),
            endMode = EndMode.REVERT,
        )

        val draft = RoutineDraft(
            draftId = "draft-knust-123",
            operation = DraftOperation.CREATE,
            targetRoutineId = 555L,
            proposedRoutine = routine,
            explanation = "Automatically turns on vibrate and DND during KNUST classes.",
            questions = listOf("Should this apply on weekends?"),
            warnings = listOf("Requires DND permission."),
            unsupportedActions = emptyList(),
            requiresApproval = true,
        )

        val json = RoutineDraftMapper.toJson(draft)
        assertNotNull(json)

        val parsed = RoutineDraftMapper.fromJson(json)
        assertEquals(draft.draftId, parsed.draftId)
        assertEquals(draft.operation, parsed.operation)
        assertEquals(draft.targetRoutineId, parsed.targetRoutineId)
        assertEquals(draft.explanation, parsed.explanation)
        assertEquals(draft.questions, parsed.questions)
        assertEquals(draft.warnings, parsed.warnings)
        assertEquals(draft.requiresApproval, parsed.requiresApproval)

        val parsedRoutine = parsed.proposedRoutine
        assertNotNull(parsedRoutine)
        assertEquals(routine.name, parsedRoutine!!.name)
        assertEquals(routine.icon, parsedRoutine.icon)
        assertEquals(routine.match, parsedRoutine.match)
        assertEquals(routine.triggers.size, parsedRoutine.triggers.size)
        assertEquals(routine.actions.size, parsedRoutine.actions.size)
        assertEquals(routine.conditions.size, parsedRoutine.conditions.size)
        assertEquals(routine.endTriggers.size, parsedRoutine.endTriggers.size)
        assertEquals(routine.endMode, parsedRoutine.endMode)
    }

    @Test
    fun toRoutineToSavePreservesOrOverridesId() {
        val routine = Routine(
            id = 1L,
            name = "Test Routine",
            triggers = listOf(Trigger.Screen(true)),
            actions = listOf(Action.Flashlight(true)),
        )
        val draft = RoutineDraft(
            draftId = "d-1",
            operation = DraftOperation.CREATE,
            proposedRoutine = routine,
        )

        val savedWithCustomId = RoutineDraftMapper.toRoutineToSave(draft, customId = 999L)
        assertEquals(999L, savedWithCustomId.id)
        assertEquals("Test Routine", savedWithCustomId.name)
    }
}
