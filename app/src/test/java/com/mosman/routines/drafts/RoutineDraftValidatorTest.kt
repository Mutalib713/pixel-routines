package com.mosman.routines.drafts

import com.mosman.routines.Access
import com.mosman.routines.Action
import com.mosman.routines.Condition
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftValidatorTest {

    @Test
    fun validRoutineDraftPasses() {
        val routine = Routine(
            id = 1L,
            name = "Lecture Mode",
            triggers = listOf(Trigger.TimeOfDay(hour = 10, minute = 0, days = setOf(1, 2, 3, 4, 5))),
            actions = listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(on = true)),
        )
        val draft = RoutineDraft(
            draftId = "test-1",
            operation = DraftOperation.CREATE,
            proposedRoutine = routine,
            explanation = "Silence phone during morning lecture",
        )

        val result = RoutineDraftValidator.validate(draft)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.requiredAccess.contains(Access.DND))
    }

    @Test
    fun blankNameFails() {
        val routine = Routine(
            id = 1L,
            name = "   ",
            triggers = listOf(Trigger.TimeOfDay(10, 0, setOf(1))),
            actions = listOf(Action.Ringer(RingerMode.VIBRATE)),
        )
        val draft = RoutineDraft("test-2", DraftOperation.CREATE, proposedRoutine = routine)
        val result = RoutineDraftValidator.validate(draft)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("name cannot be blank", ignoreCase = true) })
    }

    @Test
    fun missingTriggersFails() {
        val routine = Routine(
            id = 1L,
            name = "Study",
            triggers = emptyList(),
            actions = listOf(Action.Dnd(on = true)),
        )
        val draft = RoutineDraft("test-3", DraftOperation.CREATE, proposedRoutine = routine)
        val result = RoutineDraftValidator.validate(draft)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("trigger is required", ignoreCase = true) })
    }

    @Test
    fun missingActionsFails() {
        val routine = Routine(
            id = 1L,
            name = "Study",
            triggers = listOf(Trigger.Screen(true)),
            actions = emptyList(),
        )
        val draft = RoutineDraft("test-4", DraftOperation.CREATE, proposedRoutine = routine)
        val result = RoutineDraftValidator.validate(draft)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("action is required", ignoreCase = true) })
    }

    @Test
    fun invalidTimeOfDayParametersFail() {
        val routine = Routine(
            id = 1L,
            name = "Invalid Time",
            triggers = listOf(Trigger.TimeOfDay(hour = 25, minute = 65, days = emptySet())),
            actions = listOf(Action.Dnd(true)),
        )
        val draft = RoutineDraft("test-5", DraftOperation.CREATE, proposedRoutine = routine)
        val result = RoutineDraftValidator.validate(draft)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("hour must be between 0 and 23") })
        assertTrue(result.errors.any { it.contains("minute must be between 0 and 59") })
        assertTrue(result.errors.any { it.contains("at least one day must be selected") })
    }

    @Test
    fun invalidActionParametersFail() {
        val routine = Routine(
            id = 1L,
            name = "Invalid Actions",
            triggers = listOf(Trigger.Power(true)),
            actions = listOf(
                Action.Brightness(percent = 150),
                Action.Wait(seconds = 60),
                Action.Remind(text = "", snoozeMin = 200),
            ),
        )
        val draft = RoutineDraft("test-6", DraftOperation.CREATE, proposedRoutine = routine)
        val result = RoutineDraftValidator.validate(draft)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("brightness percent must be between 0 and 100") })
        assertTrue(result.errors.any { it.contains("wait duration must be between 1 and 30") })
        assertTrue(result.errors.any { it.contains("reminder text cannot be blank") })
        assertTrue(result.errors.any { it.contains("snooze must be between 1 and 120") })
    }

    @Test
    fun updateRequiresTargetRoutineId() {
        val routine = Routine(
            id = 1L,
            name = "Update Routine",
            triggers = listOf(Trigger.Power(true)),
            actions = listOf(Action.Dnd(true)),
        )
        val draftWithoutTarget = RoutineDraft("test-7", DraftOperation.UPDATE, targetRoutineId = null, proposedRoutine = routine)
        val result = RoutineDraftValidator.validate(draftWithoutTarget)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Target routine ID is required") })
    }

    @Test
    fun deleteRequiresTargetRoutineId() {
        val draftWithoutTarget = RoutineDraft("test-8", DraftOperation.DELETE, targetRoutineId = null)
        val result = RoutineDraftValidator.validate(draftWithoutTarget)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Target routine ID is required") })

        val validDelete = RoutineDraft("test-9", DraftOperation.DELETE, targetRoutineId = 42L)
        val validResult = RoutineDraftValidator.validate(validDelete)
        assertTrue(validResult.isValid)
    }

    @Test
    fun collectsAllRequiredPermissions() {
        val routine = Routine(
            id = 1L,
            name = "All Permissions Routine",
            triggers = listOf(Trigger.Power(true)),
            actions = listOf(
                Action.Brightness(50),
                Action.DarkTheme(true),
                Action.WifiToggle(true),
                Action.Call("123", "Alice"),
                Action.Remind("Wake up"),
            ),
        )
        val draft = RoutineDraft("test-10", DraftOperation.CREATE, proposedRoutine = routine)
        val result = RoutineDraftValidator.validate(draft)

        assertTrue(result.isValid)
        assertEquals(
            setOf(
                Access.WRITE_SETTINGS,
                Access.SECURE_SETTINGS,
                Access.SHIZUKU,
                Access.CALL,
                Access.FULL_SCREEN,
            ),
            result.requiredAccess,
        )
    }
}
