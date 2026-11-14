package com.mosman.routines.ai

import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.StreamType
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.drafts.RoutineDraftValidator
import com.mosman.routines.ui.assistant.ParsedAIResponse
import org.json.JSONObject

/**
 * Turns Gemini's JSON answer into a proposal, on the phone. This is the safety boundary:
 * Gemini only ever *suggests*. Here every value is checked against the kinds the app supports,
 * routine names must match one you actually saved, and the result goes through the same
 * RoutineDraftValidator as the manual editor. Anything else is dropped and said out loud.
 * Pure Kotlin + org.json, so it is unit-tested on the JVM (GeminiProposalMapperTest).
 */
object GeminiProposalMapper {

    /** The answer format Gemini is held to (mirrored as a Schema in GeminiAssistant). */
    val OPERATIONS = listOf("CREATE", "ENABLE", "DISABLE", "DELETE", "NONE")
    val TRIGGER_KINDS = listOf(
        "TIME", "BATTERY_BELOW", "CHARGER_CONNECTED", "CHARGER_DISCONNECTED",
        "HEADPHONES_CONNECTED", "HEADPHONES_DISCONNECTED",
    )
    val ACTION_KINDS = listOf(
        "RINGER", "DND", "BRIGHTNESS", "DARK_THEME", "BATTERY_SAVER", "MEDIA_VOLUME",
        "REMIND", "NOTIFY", "FLASHLIGHT", "AUTO_ROTATE",
    )
    val RINGER_MODES = listOf("SOUND", "VIBRATE", "SILENT")

    private const val MAX_REPLY = 400

    fun map(json: String?, saved: List<Routine>): ParsedAIResponse {
        val o = runCatching { JSONObject(json ?: "") }.getOrNull()
            ?: return ParsedAIResponse("The online assistant sent an answer I couldn't read, so I didn't use it.", null)
        val reply = o.optString("reply").trim().take(MAX_REPLY)
        val questions = o.optJSONArray("questions")?.let { a ->
            (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf(String::isNotBlank)?.take(160) }
        }.orEmpty().take(3)

        return when (val op = o.optString("operation").uppercase()) {
            "ENABLE", "DISABLE", "DELETE" -> manage(op, o.optString("target"), saved, reply)
            "CREATE" -> create(o.optJSONObject("routine"), reply, questions)
            else -> ParsedAIResponse(
                (reply.ifBlank { "I don't have an answer for that." } + questions.joinToString("") { " $it" }).trim(),
                null,
            )
        }
    }

    private fun manage(op: String, target: String, saved: List<Routine>, reply: String): ParsedAIResponse {
        val r = saved.firstOrNull { it.name.equals(target.trim(), ignoreCase = true) }
            ?: return ParsedAIResponse("I couldn't find a routine called “${target.take(60)}”.", null)
        val operation = DraftOperation.valueOf(op)
        val already = (operation == DraftOperation.ENABLE && r.enabled) || (operation == DraftOperation.DISABLE && !r.enabled)
        if (already) return ParsedAIResponse("“${r.name}” is already ${if (r.enabled) "on" else "paused"}.", null)
        // The card's wording is ours, not the model's: it states exactly what Save will do.
        val explanation = when (operation) {
            DraftOperation.ENABLE -> "Switch “${r.name}” back on?"
            DraftOperation.DISABLE -> "Pause “${r.name}”? It won't run until you switch it back on."
            else -> "Delete “${r.name}” for good? This can't be undone."
        }
        // Our wording, not the model's: on the emulator Gemini said "I have created a routine"
        // before anything was saved, despite being told not to (PROFILE.md Sacred Rule 4).
        return ParsedAIResponse(
            PROPOSAL_INTRO,
            RoutineDraft(
                draftId = "gemini-${System.currentTimeMillis()}",
                operation = operation,
                targetRoutineId = r.id,
                explanation = explanation,
                requiresApproval = true,
            ),
        )
    }

    private fun intOrNull(o: JSONObject, key: String): Int? =
        if (o.has(key) && !o.isNull(key)) o.optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

    private fun create(r: JSONObject?, reply: String, questions: List<String>): ParsedAIResponse {
        if (r == null) return ParsedAIResponse("The online assistant didn't include a routine, so there's nothing to save.", null)
        val notes = questions.toMutableList()
        val dropped = mutableListOf<String>()

        val kind = r.optString("triggerKind").uppercase()
        // Days only mean something for a time trigger (a charger trigger has no days).
        val days = r.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.toSet() }
            ?.takeIf { it.isNotEmpty() }
            ?: (1..7).toSet().also { if (kind == "TIME") notes += "No days were given, so I set every day." }

        val trigger: Trigger? = when (kind) {
            "TIME" -> {
                val h = intOrNull(r, "hour"); val m = intOrNull(r, "minute") ?: 0
                if (h != null) Trigger.TimeOfDay(h, m, days) else null
            }
            "BATTERY_BELOW" -> intOrNull(r, "batteryPercent")?.let { Trigger.Battery(below = true, level = it) }
            "CHARGER_CONNECTED" -> Trigger.Power(true)
            "CHARGER_DISCONNECTED" -> Trigger.Power(false)
            "HEADPHONES_CONNECTED" -> Trigger.Headset(true)
            "HEADPHONES_DISCONNECTED" -> Trigger.Headset(false)
            else -> null
        }

        val actions = mutableListOf<Action>()
        val arr = r.optJSONArray("actions")
        for (i in 0 until (arr?.length() ?: 0)) {
            val a = arr!!.optJSONObject(i) ?: continue
            val on = a.optBoolean("on", true)
            val pct = intOrNull(a, "percent")
            val text = a.optString("text").trim().take(120)
            val kind = a.optString("kind").uppercase()
            val action: Action? = when (kind) {
                "RINGER" -> a.optString("ringerMode").uppercase().takeIf { it in RINGER_MODES }?.let {
                    Action.Ringer(when (it) { "SILENT" -> RingerMode.SILENT; "VIBRATE" -> RingerMode.VIBRATE; else -> RingerMode.SOUND })
                }
                "DND" -> Action.Dnd(on)
                "BRIGHTNESS" -> pct?.let { Action.Brightness(it) }
                "DARK_THEME" -> Action.DarkTheme(on)
                "BATTERY_SAVER" -> Action.BatterySaver(on)
                "MEDIA_VOLUME" -> pct?.let { Action.Volume(StreamType.MEDIA, it) }
                "REMIND" -> text.takeIf { it.isNotBlank() }?.let { Action.Remind(it) }
                "NOTIFY" -> text.takeIf { it.isNotBlank() }?.let { Action.Notify(it.take(40), it) }
                "FLASHLIGHT" -> Action.Flashlight(on)
                "AUTO_ROTATE" -> Action.AutoRotate(on)
                else -> null
            }
            if (action == null) dropped += kind.ifBlank { "an unnamed step" }.lowercase().replace('_', ' ')
            else actions += action
        }
        if (dropped.isNotEmpty()) notes += "I left out what I couldn't use: ${dropped.joinToString()}."

        val endTriggers = if (trigger is Trigger.TimeOfDay) {
            val eh = intOrNull(r, "endHour")
            if (eh != null) listOf(Trigger.TimeOfDay(eh, intOrNull(r, "endMinute") ?: 0, days)) else emptyList()
        } else emptyList()

        val name = r.optString("name").trim().take(40).ifBlank { "New routine" }
        val routine = Routine(
            id = System.currentTimeMillis(),
            name = name,
            triggers = listOfNotNull(trigger),
            actions = actions.distinct(),
            endTriggers = endTriggers,
        )
        val draft = RoutineDraft(
            draftId = "gemini-${System.currentTimeMillis()}",
            operation = DraftOperation.CREATE,
            proposedRoutine = routine,
            explanation = "New routine “$name”.",
            questions = notes,
            unsupportedActions = dropped,
            requiresApproval = true,
        )
        // Same checks as the manual editor and the offline assistant.
        val check = RoutineDraftValidator.validate(draft)
        if (!check.isValid) return ParsedAIResponse(
            "The online assistant's suggestion didn't pass the app's checks, so I didn't use it: " +
                check.errors.joinToString("; ") + ".",
            null,
        )
        return ParsedAIResponse(PROPOSAL_INTRO, draft)
    }

    /** Shown above every Gemini proposal instead of Gemini's own sentence. */
    const val PROPOSAL_INTRO = "Check this, then tap Save. Nothing changes until you do."
}
