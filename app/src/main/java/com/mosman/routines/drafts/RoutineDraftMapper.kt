package com.mosman.routines.drafts

import com.mosman.routines.Routine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Serializes and parses RoutineDraft objects to/from JSON, and converts
 * drafts to ready-to-persist domain models safely.
 */
object RoutineDraftMapper {

    fun toJson(draft: RoutineDraft): JSONObject = JSONObject().apply {
        put("draftId", draft.draftId)
        put("operation", draft.operation.name)
        if (draft.targetRoutineId != null) {
            put("targetRoutineId", draft.targetRoutineId)
        }
        if (draft.proposedRoutine != null) {
            put("proposedRoutine", draft.proposedRoutine.toJson())
        }
        put("explanation", draft.explanation)
        put("questions", JSONArray(draft.questions))
        put("warnings", JSONArray(draft.warnings))
        put("unsupportedActions", JSONArray(draft.unsupportedActions))
        put("requiresApproval", draft.requiresApproval)
        put("createdAt", draft.createdAt)
    }

    fun fromJson(json: JSONObject): RoutineDraft {
        val op = runCatching {
            DraftOperation.valueOf(json.optString("operation", DraftOperation.CREATE.name))
        }.getOrDefault(DraftOperation.CREATE)

        val targetId = if (json.has("targetRoutineId") && !json.isNull("targetRoutineId")) {
            json.getLong("targetRoutineId")
        } else {
            null
        }

        val proposed = if (json.has("proposedRoutine") && !json.isNull("proposedRoutine")) {
            runCatching { Routine.fromJson(json.getJSONObject("proposedRoutine")) }.getOrNull()
        } else {
            null
        }

        return RoutineDraft(
            draftId = json.optString("draftId", UUID.randomUUID().toString()),
            operation = op,
            targetRoutineId = targetId,
            proposedRoutine = proposed,
            explanation = json.optString("explanation", ""),
            questions = json.optJSONArray("questions")?.toStringList() ?: emptyList(),
            warnings = json.optJSONArray("warnings")?.toStringList() ?: emptyList(),
            unsupportedActions = json.optJSONArray("unsupportedActions")?.toStringList() ?: emptyList(),
            requiresApproval = json.optBoolean("requiresApproval", true),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    /**
     * Converts a create draft into a clean, newly identified Routine ready for persistence.
     */
    fun toRoutineToSave(draft: RoutineDraft, customId: Long? = null): Routine {
        val base = draft.proposedRoutine
            ?: throw IllegalStateException("Draft does not contain a proposed routine")

        val targetId = customId ?: draft.targetRoutineId ?: System.currentTimeMillis()
        return base.copy(id = targetId)
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
