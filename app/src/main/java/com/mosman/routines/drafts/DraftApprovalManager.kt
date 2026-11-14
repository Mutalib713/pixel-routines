package com.mosman.routines.drafts

import android.content.Context
import com.mosman.routines.Routine
import com.mosman.routines.Store

sealed interface ApprovalResult {
    data class Success(val routine: Routine?, val message: String) : ApprovalResult
    data class Rejected(val reason: String) : ApprovalResult
    data class Error(val errors: List<String>) : ApprovalResult
}

/**
 * Manages the approval gate for routine mutations.
 * Enforces that no routine change (from AI, Lens, or templates) can mutate
 * persistent storage without explicit validation and user approval.
 */
object DraftApprovalManager {

    /**
     * Approves and commits a validated RoutineDraft into persistent Store.
     * Returns an ApprovalResult indicating whether the operation succeeded or was rejected.
     */
    fun approve(context: Context, draft: RoutineDraft): ApprovalResult {
        val validation = RoutineDraftValidator.validate(draft)
        if (!validation.isValid) {
            return ApprovalResult.Error(validation.errors)
        }

        // Every branch reads the store back before reporting success, so the message describes
        // what was actually saved, not what was meant to be (PROFILE.md Sacred Rule 4).
        return when (draft.operation) {
            DraftOperation.CREATE, DraftOperation.UPDATE -> {
                val routine = if (draft.operation == DraftOperation.CREATE) RoutineDraftMapper.toRoutineToSave(draft)
                    else RoutineDraftMapper.toRoutineToSave(draft, draft.targetRoutineId)
                Store.upsert(context, routine)
                val stored = Store.get(context, routine.id)
                    ?: return ApprovalResult.Error(listOf("“${routine.name}” didn't save. Please try again."))
                val verb = if (draft.operation == DraftOperation.CREATE) "Saved" else "Updated"
                val state = if (stored.enabled) "It's on and will run: ${stored.ifSummary()}." else "It's saved but paused."
                ApprovalResult.Success(stored, "$verb “${stored.name}”. $state")
            }

            DraftOperation.ENABLE, DraftOperation.DISABLE -> {
                val id = draft.targetRoutineId ?: return ApprovalResult.Error(listOf("Missing target ID."))
                val on = draft.operation == DraftOperation.ENABLE
                Store.setEnabled(context, id, on)
                val updated = Store.get(context, id)
                    ?: return ApprovalResult.Error(listOf("That routine no longer exists."))
                if (updated.enabled != on) return ApprovalResult.Error(listOf("“${updated.name}” didn't change."))
                ApprovalResult.Success(updated, if (on) "“${updated.name}” is back on." else "“${updated.name}” is paused.")
            }

            DraftOperation.DELETE -> {
                val id = draft.targetRoutineId ?: return ApprovalResult.Error(listOf("Missing target ID."))
                val name = Store.get(context, id)?.name ?: "Routine"
                Store.delete(context, id)
                if (Store.get(context, id) != null) return ApprovalResult.Error(listOf("“$name” couldn't be deleted."))
                ApprovalResult.Success(null, "Deleted “$name”.")
            }
        }
    }

    /**
     * Explicitly rejects a proposed draft with a user-supplied or default reason.
     */
    fun reject(draft: RoutineDraft, reason: String = "Discarded by user"): ApprovalResult {
        return ApprovalResult.Rejected(reason)
    }
}
