package com.mosman.routines.ai

import com.mosman.routines.Action
import com.mosman.routines.EndMode
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.RoutineDraftValidator
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineDraft
import org.json.JSONObject

/** One class from a timetable. Times are minutes after midnight; day 1 = Monday … 7 = Sunday. */
data class ClassSlot(
    val course: String,
    val title: String,
    val day: Int,
    val start: Int,
    val end: Int,
    val venue: String,
) {
    fun label() = "%s %s–%s %s".format(dayName(day), hhmm(start), hhmm(end), course).trim()
}

internal fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
internal fun dayName(d: Int) = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").getOrElse(d - 1) { "?" }

/**
 * Turns a timetable into "Lecture Mode": the phone goes to Vibrate + Do Not Disturb a few minutes
 * before each class and gets its sound back after, using the existing routine engine.
 *
 * The one rule that matters: classes that run back to back (or overlap) are MERGED into one block,
 * so the sound doesn't come back for the five-minute changeover and then switch off again.
 * Blocks that start (or end) at the same time on different days share one trigger. The engine
 * allows 8 start and 8 end times per routine; a timetable that needs more is split into
 * "Lecture Mode", "Lecture Mode (2)"… Their blocks never overlap, so they can't fight over the
 * sound settings.
 */
object LectureMode {

    data class Block(val day: Int, val start: Int, val end: Int)

    const val MAX_TIMES = 8 // Engine.END_OFFSET: start (and end) alarms per routine

    /** Merge per day: a gap of [mergeGapMin] minutes or less counts as back to back. */
    fun blocks(slots: List<ClassSlot>, mergeGapMin: Int = 10): List<Block> =
        slots.groupBy { it.day }.toSortedMap().flatMap { (day, list) ->
            val sorted = list.sortedBy { it.start }
            val out = mutableListOf<Block>()
            for (s in sorted) {
                val last = out.lastOrNull()
                if (last != null && s.start - last.end <= mergeGapMin) {
                    out[out.size - 1] = last.copy(end = maxOf(last.end, s.end))
                } else out += Block(day, s.start, s.end)
            }
            out
        }

    /** The Lecture Mode routine(s), or an empty list when there are no classes. */
    fun build(slots: List<ClassSlot>, leadMin: Int = 5, mergeGapMin: Int = 10, idBase: Long = System.currentTimeMillis()): List<Routine> {
        val all = blocks(slots, mergeGapMin).map { it.copy(start = (it.start - leadMin).coerceAtLeast(0)) }
        if (all.isEmpty()) return emptyList()

        // Fill routines one block at a time, as long as neither distinct start times nor
        // distinct end times go over the engine's 8.
        val groups = mutableListOf<MutableList<Block>>()
        for (b in all) {
            val g = groups.firstOrNull { g ->
                (g.map { it.start } + b.start).toSet().size <= MAX_TIMES &&
                    (g.map { it.end } + b.end).toSet().size <= MAX_TIMES
            } ?: mutableListOf<Block>().also { groups += it }
            g += b
        }

        return groups.mapIndexed { i, g ->
            Routine(
                id = idBase + i,
                name = if (i == 0) "Lecture Mode" else "Lecture Mode (${i + 1})",
                icon = "school",
                triggers = g.groupBy { it.start }.toSortedMap().map { (t, bs) -> Trigger.TimeOfDay(t / 60, t % 60, bs.map { it.day }.toSet()) },
                actions = listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true)),
                endTriggers = g.groupBy { it.end }.toSortedMap().map { (t, bs) -> Trigger.TimeOfDay(t / 60, t % 60, bs.map { it.day }.toSet()) },
                endMode = EndMode.REVERT,
            )
        }
    }

    /**
     * Reads the classes out of Gemini's JSON and checks every one on the phone: real day,
     * real times, end after start, nothing absurd. Bad rows are dropped and counted.
     */
    fun parseClasses(o: JSONObject): Pair<List<ClassSlot>, Int> {
        val arr = o.optJSONArray("classes") ?: return emptyList<ClassSlot>() to 0
        var dropped = 0
        val out = mutableListOf<ClassSlot>()
        for (i in 0 until minOf(arr.length(), 80)) {
            val c = arr.optJSONObject(i)
            val day = c?.optInt("day", 0) ?: 0
            val start = c?.let { minutes(it.optString("start")) }
            val end = c?.let { minutes(it.optString("end")) }
            if (c == null || day !in 1..7 || start == null || end == null || end <= start || end - start > 6 * 60) {
                dropped++; continue
            }
            out += ClassSlot(
                course = c.optString("course").trim().take(24),
                title = c.optString("title").trim().take(60),
                day = day, start = start, end = end,
                venue = c.optString("venue").trim().take(40),
            )
        }
        return out.distinct() to dropped
    }

    /** "8:00", "08:00", "13:55" → minutes after midnight, or null. */
    fun minutes(s: String): Int? {
        val m = Regex("^\\s*(\\d{1,2})[:.](\\d{2})\\s*$").find(s) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h * 60 + mi else null
    }

    /** Proposal cards for the Lecture Mode routine(s), checked by the same validator as everything else. */
    fun drafts(slots: List<ClassSlot>, dropped: Int): Pair<String, List<RoutineDraft>> {
        val routines = build(slots)
        if (routines.isEmpty()) return "I didn't find any classes in that." to emptyList()
        val days = slots.map { it.day }.distinct().sorted().joinToString { dayName(it) }
        val list = slots.sortedWith(compareBy({ it.day }, { it.start })).joinToString("\n") { "• " + it.label() }
        val notes = buildList {
            add("Starts 5 minutes before class. Back-to-back classes stay in one stretch, so your sound doesn't come back in between.")
            if (dropped > 0) add("$dropped entr${if (dropped == 1) "y" else "ies"} didn't make sense, so I left ${if (dropped == 1) "it" else "them"} out.")
            add("It repeats every week. Pause it when the semester ends.")
        }
        val drafts = routines.mapNotNull { r ->
            val d = RoutineDraft(
                draftId = "lecture-${r.id}",
                operation = DraftOperation.CREATE,
                proposedRoutine = r,
                explanation = "## ${slots.size} classes on $days\n$list",
                questions = notes,
                requiresApproval = true,
            )
            d.takeIf { RoutineDraftValidator.validate(it).isValid }
        }
        return "I found ${slots.size} classes. Check them, then tap Save. Nothing changes until you do." to drafts
    }
}
