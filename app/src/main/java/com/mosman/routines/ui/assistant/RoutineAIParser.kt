package com.mosman.routines.ui.assistant

import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineDraft

data class ParsedAIResponse(
    val explanation: String,
    val draft: RoutineDraft?,
    /** False when the offline assistant couldn't really deal with the message, so the
     *  online assistant may try (Automatic mode, with consent). */
    val handled: Boolean = true,
)

/**
 * The offline assistant: a rule-based reader for plain requests. It is NOT a language model.
 * The online assistant (Gemini via Firebase AI Logic) takes over the hard cases later.
 *
 * Contract (see RoutineAIParserTest): every message gets either a correct proposal or an
 * honest "I can't / I need more" answer. It never throws, and when it isn't sure it says
 * what it assumed instead of quietly guessing. Nothing is saved here; a proposal still has
 * to be approved (PROFILE.md Sacred Rule 3).
 *
 * Order matters: managing a saved routine ("pause …") and questions ("what does …") are
 * recognised before anything is treated as a request to create a new routine.
 */
object RoutineAIParser {

    private const val EXAMPLES =
        "Try “Vibrate and Do Not Disturb at 8am on weekdays”, “Remind me to call Mum at 6pm” or “Pause Night Routine”."

    fun parse(
        userText: String,
        extractedOcrText: String? = null,
        saved: List<Routine> = emptyList(),
    ): ParsedAIResponse = runCatching { route(userText.trim(), extractedOcrText, saved) }
        .getOrElse { ParsedAIResponse("Sorry, I couldn't make sense of that one. $EXAMPLES", null, handled = false) }

    private fun route(text: String, ocr: String?, saved: List<Routine>): ParsedAIResponse {
        if (!ocr.isNullOrBlank()) {
            val slots = findRanges(ocr).distinct()
            if (slots.size >= 2) return ParsedAIResponse(
                "I read ${slots.size} different times in that picture (${slots.joinToString { it.label() }}). " +
                    "One routine can only have one start time, and turning a whole timetable into a routine " +
                    "per class isn't built yet. For now, add them one at a time, like “Vibrate on Monday from 8am to 10am”.",
                null,
            )
        }
        val lower = text.lowercase().trim().trimEnd('.', '!')
        manage(lower, saved)?.let { return it }
        question(lower, saved)?.let { return it }
        edit(lower, saved)?.let { return it }
        return create(text, ocr)
    }

    // ---------------------------------------------------------------- saved routines

    private val FILLER = setOf("my", "the", "a", "an", "routine", "routines", "mode", "old", "please", "for", "me", "it", "of")

    private fun words(s: String): Set<String> =
        Regex("[a-z0-9]+").findAll(s.lowercase()).map { it.value }.filter { it !in FILLER }.toSet()

    /**
     * The saved routine [phrase] names. [strict] = the phrase must be mostly the name (used when
     * the phrase is "everything after the verb"); otherwise every word of the name just has to
     * appear somewhere. Null when nothing matches or two routines match equally well.
     */
    private fun findRoutine(phrase: String, saved: List<Routine>, strict: Boolean): Routine? {
        val said = words(phrase)
        if (said.isEmpty()) return null
        val scored = saved.mapNotNull { r ->
            val name = words(r.name)
            if (name.isEmpty()) return@mapNotNull null
            val hit = name.count { it in said }
            val ok = if (strict) hit * 2 >= name.size && hit * 2 >= said.size else hit == name.size
            if (ok) r to hit.toDouble() / name.size else null
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (scored.count { it.second == best.second } > 1) null else best.first
    }

    private fun routineList(saved: List<Routine>): String =
        if (saved.isEmpty()) "You don't have any routines yet."
        else "Your routines: " + saved.joinToString { "“${it.name}”" } + "."

    private fun describe(r: Routine): String = buildString {
        append("“${r.name}” is ${if (r.enabled) "switched on" else "paused"}. ")
        append("When: ${r.ifSummary()}. ")
        append("Then: ${r.thenSummary()}.")
        r.endSummary()?.let { append(" Until: $it.") }
    }

    private val MANAGE = listOf(
        Regex("^(?:please\\s+)?(pause|disable|stop|turn off|switch off|deactivate)\\s+(.+)$") to DraftOperation.DISABLE,
        Regex("^(?:please\\s+)?(resume|enable|unpause|turn on|switch on|activate|start)\\s+(.+)$") to DraftOperation.ENABLE,
        Regex("^(?:please\\s+)?(delete|remove|get rid of)\\s+(.+)$") to DraftOperation.DELETE,
    )

    /** Verbs that also start ordinary requests ("turn on vibrate at 8am"): only routine names count. */
    private val SOFT_VERBS = setOf("turn off", "switch off", "turn on", "switch on", "stop", "start", "activate", "deactivate")

    private fun manage(lower: String, saved: List<Routine>): ParsedAIResponse? {
        for ((re, op) in MANAGE) {
            val m = re.find(lower) ?: continue
            val verb = m.groupValues[1]
            val phrase = m.groupValues[2]
            val r = findRoutine(phrase, saved, strict = true)
            if (r == null) {
                if (verb in SOFT_VERBS) return null
                return ParsedAIResponse("I couldn't find a routine called “$phrase”. ${routineList(saved)}", null)
            }
            val (explanation, already) = when (op) {
                DraftOperation.DISABLE -> "Pause “${r.name}”? It won't run until you switch it back on." to !r.enabled
                DraftOperation.ENABLE -> "Switch “${r.name}” back on?" to r.enabled
                else -> "Delete “${r.name}” for good? This can't be undone." to false
            }
            if (already) return ParsedAIResponse(
                "“${r.name}” is already ${if (r.enabled) "on" else "paused"}.", null)
            return ParsedAIResponse(
                "Nothing happens until you confirm.",
                RoutineDraft(
                    draftId = "ai-${System.currentTimeMillis()}",
                    operation = op,
                    targetRoutineId = r.id,
                    explanation = explanation,
                    requiresApproval = true,
                ),
            )
        }
        return null
    }

    /** Information questions. ("Can you mute my phone at 10pm?" is a request, not one of these.) */
    private val QUESTION = Regex("^(what|what's|whats|why|how|which|when|explain|describe|tell me about|is|are|does|did)\\b")

    private fun question(lower: String, saved: List<Routine>): ParsedAIResponse? {
        if (!QUESTION.containsMatchIn(lower)) return null
        val r = findRoutine(lower, saved, strict = false)
            ?: return ParsedAIResponse(
                "Offline, I can only explain the routines you've saved, by name. ${routineList(saved)} " +
                    "Harder questions need the online assistant.",
                null,
                handled = false,
            )
        val extra = if (lower.startsWith("why")) " I can't read the run history from here yet. Check the Activity tab for what actually ran." else ""
        return ParsedAIResponse(describe(r) + extra, null)
    }

    private val EDIT = Regex("^(?:please\\s+)?(make|change|edit|modify|rename|move|update|set)\\b")

    private fun edit(lower: String, saved: List<Routine>): ParsedAIResponse? {
        if (!EDIT.containsMatchIn(lower)) return null
        val r = findRoutine(lower, saved, strict = false) ?: return null
        return ParsedAIResponse(
            "Changing a saved routine isn't something I can do from chat yet. " +
                "For now, open Routines and tap it to edit it yourself. Right now: ${describe(r)}",
            null,
        )
    }

    // ---------------------------------------------------------------- times

    private data class Clock(val h: Int, val m: Int)

    private data class Slot(val start: Clock, val end: Clock?) {
        fun label() = "%02d:%02d".format(start.h, start.m) + (end?.let { "–%02d:%02d".format(it.h, it.m) } ?: "")
    }

    private fun to24(h: Int, ampm: String?): Int? {
        val p = ampm?.replace(".", "")
        return when {
            p == null -> h.takeIf { it in 0..23 }
            h !in 1..12 -> null
            p == "am" -> if (h == 12) 0 else h
            else -> if (h == 12) 12 else h + 12
        }
    }

    private fun clock(h: String, m: String?, ampm: String?): Clock? {
        val hour = to24(h.toIntOrNull() ?: return null, ampm?.ifBlank { null }) ?: return null
        val min = m?.ifBlank { null }?.toIntOrNull() ?: 0
        return if (min in 0..59) Clock(hour, min) else null
    }

    // A number glued to "-", "/" or "." on the left is part of a date, so it's never a time.
    private const val NOT_AFTER = "(?<![\\d/.:-])"
    private const val AMPM = "(am|pm|a\\.m\\.|p\\.m\\.)"

    private val RANGE = Regex(
        "$NOT_AFTER(from\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*$AMPM?\\s*(?:-|–|—|to|until|till)\\s*" +
            "(\\d{1,2})(?:[:.](\\d{2}))?\\s*$AMPM?(?![\\d/%])",
    )

    private fun findRanges(text: String): List<Slot> =
        RANGE.findAll(text.lowercase()).mapNotNull { m ->
            val g = m.groupValues
            val from = g[1].isNotBlank()
            var sAm = g[4].ifBlank { null }
            val eAm = g[7].ifBlank { null }
            val colon = g[3].isNotBlank() || g[6].isNotBlank()
            if (!from && !colon && sAm == null && eAm == null) return@mapNotNull null
            // "2 to 5pm": the end's am/pm applies to the start too.
            if (sAm == null && eAm != null && (g[2].toIntOrNull() ?: 99) <= (g[5].toIntOrNull() ?: 0)) sAm = eAm
            val s = clock(g[2], g[3], sAm) ?: return@mapNotNull null
            val e = clock(g[5], g[6], eAm) ?: return@mapNotNull null
            Slot(s, e)
        }.toList()

    private val SINGLES = listOf(
        Regex("\\b(?:at|@|by|from)\\s+(\\d{1,2})(?:[:.](\\d{2}))?\\s*$AMPM?(?![\\d/%-])"),
        Regex("$NOT_AFTER(\\d{1,2}):(\\d{2})\\s*$AMPM?(?![\\d/%])"),
        Regex("$NOT_AFTER(\\d{1,2})()\\s*$AMPM(?![a-z])"),
    )

    /** First time mentioned, plus whether its am/pm was left unsaid. */
    private fun findSingle(text: String): Pair<Clock, Boolean>? {
        val low = text.lowercase()
        return SINGLES.mapNotNull { re ->
            re.find(low)?.let { m ->
                val c = clock(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                c?.let { m.range.first to (it to (m.groupValues[3].isBlank() && m.groupValues[2].isBlank() && it.h in 1..11)) }
            }
        }.minByOrNull { it.first }?.second
    }

    // ---------------------------------------------------------------- creating

    private fun has(low: String, pattern: String) = Regex(pattern).containsMatchIn(low)

    private val DAY_PATTERNS = listOf(
        1 to "\\bmon(day)?s?\\b", 2 to "\\btue(s|sday)?s?\\b", 3 to "\\bwed(nesday)?s?\\b",
        4 to "\\bthu(r|rs|rsday)?s?\\b", 5 to "\\bfri(day)?s?\\b", 6 to "\\bsat(urday)?s?\\b",
        7 to "\\bsun(day)?s?\\b",
    )

    private fun create(original: String, ocr: String?): ParsedAIResponse {
        val questions = mutableListOf<String>()

        // 1. Pull out "remind me to …" first, so the reminder's words ("study", "night") don't
        //    get read as settings to change.
        var analysis = original
        var reminder: String? = null
        Regex(
            "remind me (?:to |about |that )?(.+?)(?=\\s+(?:at|on|every|each|daily|from|by|tomorrow|tonight)\\b|[,.;!?]|\\s+and\\s|$)",
            RegexOption.IGNORE_CASE,
        ).find(original)?.let { m ->
            val t = m.groupValues[1].trim()
            if (t.isNotBlank() && t.lowercase() !in setOf("to", "about", "that")) reminder = t
            else questions += "What should I remind you about?"
            analysis = original.removeRange(m.range)
        }
        val low = analysis.lowercase() + if (ocr.isNullOrBlank()) "" else "\n" + ocr.lowercase()

        val ctxClass = has(low, "\\b(lectures?|class(es)?|knust|exams?|school|church|mosque|library)\\b")
        val ctxMeeting = has(low, "\\bmeetings?\\b")
        val ctxBed = has(low, "\\b(bedtime|bed|sleep|night mode)\\b")
        val ctxStudy = has(low, "\\b(study|studying|focus)\\b")
        val ctxWork = has(low, "\\b(work|office|shift)\\b")

        // 2. Explicit actions
        val actions = mutableListOf<Action>()
        when {
            has(low, "\\b(silent|silence|silenced|mute|muted)\\b") -> actions += Action.Ringer(RingerMode.SILENT)
            has(low, "\\bvibrat\\w*") -> actions += Action.Ringer(RingerMode.VIBRATE)
            has(low, "\\b(unmute|ringer on|sound on|loud)\\b|\\bring(ing|er)?\\s+(on|back)\\b") ->
                actions += Action.Ringer(RingerMode.SOUND)
        }
        if (has(low, "\\b(turn|switch)\\s+off\\s+(dnd|do not disturb)\\b")) actions += Action.Dnd(false)
        else if (has(low, "\\b(dnd|do not disturb|don'?t disturb)\\b")) actions += Action.Dnd(true)
        if (has(low, "\\bdark (theme|mode)\\b")) actions += Action.DarkTheme(true)
        else if (has(low, "\\blight (theme|mode)\\b")) actions += Action.DarkTheme(false)
        Regex("brightness[^\\d%]{0,15}(\\d{1,3})\\s*%?").find(low)?.let {
            actions += Action.Brightness(it.groupValues[1].toInt().coerceIn(0, 100))
        } ?: run {
            if (has(low, "\\b(reduce|lower|dim|decrease)\\b[^.,]{0,15}\\b(brightness|screen)\\b|\\bdim the screen\\b"))
                actions += Action.Brightness(30)
            else if (has(low, "\\b(max|maximum|full|increase|raise)\\b[^.,]{0,15}\\bbrightness\\b"))
                actions += Action.Brightness(100)
        }
        val saverWords = has(low, "\\b(battery saver|power sav\\w*|save battery|saving battery|battery saving)\\b")
        if (saverWords) actions += Action.BatterySaver(!has(low, "\\b(turn|switch)\\s+off\\s+(battery saver|power sav\\w*)"))
        reminder?.let { actions += Action.Remind(it) }

        // 3. Sensible defaults only when nothing was asked for explicitly
        if (actions.isEmpty()) when {
            ctxClass || ctxMeeting -> actions += listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true))
            ctxBed -> actions += listOf(Action.Dnd(true), Action.DarkTheme(true), Action.Brightness(15))
            ctxStudy -> actions += Action.Dnd(true)
        }
        if (actions.isEmpty()) return ParsedAIResponse(
            "I couldn't tell what you'd like the phone to do. $EXAMPLES", null, handled = false)

        // 4. When
        val range = findRanges(analysis).firstOrNull()
        val single = if (range == null) findSingle(analysis) else null
        val start: Clock? = range?.start ?: single?.first
        if (single?.second == true) questions += "I read that as %02d:%02d. Add “pm” if you meant the evening.".format(single.first.h, single.first.m)
        if (has(low, "\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b|\\b\\d{1,2}/\\d{1,2}(/\\d{2,4})?\\b"))
            questions += "One-off dates aren't supported yet, so this repeats on the days shown."

        val triggers = mutableListOf<Trigger>()
        val endTriggers = mutableListOf<Trigger>()

        val days = DAY_PATTERNS.filter { has(low, it.second) }.map { it.first }.toMutableSet()
        if (has(low, "\\bweekdays?\\b")) days += 1..5
        if (has(low, "\\bweekends?\\b")) days += 6..7
        if (has(low, "\\b(every ?day|daily|each day|every night|nightly)\\b")) days += 1..7
        if (days.isEmpty()) {
            if (ctxClass || ctxWork) { days += 1..5; questions += "I set it for weekdays. Tell me if you want other days." }
            else days += 1..7
        }

        if (start != null) {
            triggers += Trigger.TimeOfDay(start.h, start.m, days)
            val end = range?.end
            if (end != null) endTriggers += Trigger.TimeOfDay(end.h, end.m, days)
            else if (reminder == null && (ctxClass || ctxMeeting)) {
                endTriggers += Trigger.TimeOfDay((start.h + 2) % 24, start.m, days)
                questions += "I assumed it lasts 2 hours. Give an end time to change that."
            }
        } else {
            val pct = Regex("battery[^\\d%]{0,25}(\\d{1,3})\\s*%|(\\d{1,3})\\s*%\\s*(?:battery|charge)").find(low)
                ?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
                ?: if (has(low, "\\bbattery\\b")) Regex("(?:below|under|drops?\\s+(?:to|below)|less than|<)\\s*(\\d{1,3})\\s*%")
                    .find(low)?.groupValues?.get(1)?.toIntOrNull() else null
            when {
                pct != null && pct in 1..99 -> triggers += Trigger.Battery(below = true, level = pct)
                saverWords || has(low, "\\blow battery\\b") -> {
                    triggers += Trigger.Battery(below = true, level = 20)
                    questions += "I used 20% battery. Tell me another level if you prefer."
                }
                ctxBed -> {
                    triggers += Trigger.TimeOfDay(22, 0, days)
                    questions += "I picked 22:00. Tell me your bedtime if it's different."
                }
                else -> return ParsedAIResponse(
                    "When should this happen? Give me a time like “at 7pm”, or a battery level like “below 20%”.", null,
                    handled = false)
            }
        }

        // 5. Name
        val name = when {
            reminder != null && actions.size == 1 -> "Reminder: ${reminder!!.take(28)}"
            ctxBed -> "Bedtime"
            ctxClass -> if (has(low, "\\bknust\\b")) "KNUST lecture mode" else "Class mode"
            ctxMeeting -> "Meeting mode"
            ctxStudy -> "Study focus"
            ctxWork -> "Work mode"
            triggers.firstOrNull() is Trigger.Battery -> "Battery saver"
            else -> original.replace(Regex("[^A-Za-z0-9 ]"), "").trim().take(28)
                .replaceFirstChar { it.uppercase() }.ifBlank { "New routine" }
        }
        val icon = when {
            reminder != null && actions.size == 1 -> "alarm"
            ctxBed -> "bedtime"
            ctxClass || ctxStudy -> "school"
            ctxWork || ctxMeeting -> "work"
            triggers.firstOrNull() is Trigger.Battery -> "saver"
            else -> "star"
        }

        val routine = Routine(
            id = System.currentTimeMillis(),
            name = name,
            icon = icon,
            triggers = triggers,
            actions = actions.distinct(),
            endTriggers = endTriggers,
        )
        return ParsedAIResponse(
            "Check this, then tap Save. Nothing changes until you do.",
            RoutineDraft(
                draftId = "ai-${System.currentTimeMillis()}",
                operation = DraftOperation.CREATE,
                proposedRoutine = routine,
                explanation = "New routine “$name”.",
                questions = questions,
                requiresApproval = true,
            ),
        )
    }
}
