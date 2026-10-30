package com.mosman.routines.drafts

import com.mosman.routines.Routine

/**
 * Kind of modification proposed by the user, assistant, or image recognition pipeline.
 */
enum class DraftOperation {
    CREATE,
    UPDATE,
    ENABLE,
    DISABLE,
    DELETE;

    fun label(): String = when (this) {
        CREATE -> "Create routine"
        UPDATE -> "Update routine"
        ENABLE -> "Enable routine"
        DISABLE -> "Disable routine"
        DELETE -> "Delete routine"
    }
}

/**
 * Immutable draft proposal representing an intended routine creation or mutation.
 * Every routine change proposed by AI or Lens MUST pass through a RoutineDraft and
 * be reviewed and approved by the user before touching the live automation Store.
 */
data class RoutineDraft(
    val draftId: String,
    val operation: DraftOperation,
    val targetRoutineId: Long? = null,
    val proposedRoutine: Routine? = null,
    val explanation: String = "",
    val questions: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val unsupportedActions: List<String> = emptyList(),
    val requiresApproval: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isExecutable: Boolean
        get() = when (operation) {
            DraftOperation.CREATE -> proposedRoutine != null && proposedRoutine.isValid
            DraftOperation.UPDATE -> targetRoutineId != null && proposedRoutine != null && proposedRoutine.isValid
            DraftOperation.ENABLE,
            DraftOperation.DISABLE,
            DraftOperation.DELETE -> targetRoutineId != null
        }
}
