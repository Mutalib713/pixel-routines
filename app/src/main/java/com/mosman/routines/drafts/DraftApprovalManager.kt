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

        return when (draft.operation) {
            DraftOperation.CREATE -> {
                val routine = RoutineDraftMapper.toRoutineToSave(draft)
                Store.upsert(context, routine)
                ApprovalResult.Success(routine, "Routine “${routine.name}” created successfully.")
            }

            DraftOperation.UPDATE -> {
                val routine = RoutineDraftMapper.toRoutineToSave(draft, draft.targetRoutineId)
                Store.upsert(context, routine)
                ApprovalResult.Success(routine, "Routine “${routine.name}” updated successfully.")
            }

            DraftOperation.ENABLE -> {
                val id = draft.targetRoutineId ?: return ApprovalResult.Error(listOf("Missing target ID."))
                Store.setEnabled(context, id, true)
                val updated = Store.get(context, id)
                ApprovalResult.Success(updated, "Routine enabled.")
            }

            DraftOperation.DISABLE -> {
                val id = draft.targetRoutineId ?: return ApprovalResult.Error(listOf("Missing target ID."))
                Store.setEnabled(context, id, false)
                val updated = Store.get(context, id)
                ApprovalResult.Success(updated, "Routine disabled.")
            }

            DraftOperation.DELETE -> {
                val id = draft.targetRoutineId ?: return ApprovalResult.Error(listOf("Missing target ID."))
                val name = Store.get(context, id)?.name ?: "Routine"
                Store.delete(context, id)
                ApprovalResult.Success(null, "Routine “$name” deleted.")
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
