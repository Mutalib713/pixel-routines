package com.mosman.routines

/**
 * Short wording for a routine's triggers, shared by the editor's summary line and the chat.
 * One or two triggers are listed in full; past that they're counted, because a routine like
 * Lecture Mode with four start times turned the full list into a paragraph nobody reads.
 */
object RoutineText {
    fun triggers(ts: List<Trigger>): String = when {
        ts.isEmpty() -> "no trigger yet"
        ts.size <= 2 -> ts.joinToString(" or ") { it.describe().replaceFirstChar { c -> c.lowercase() } }
        ts.all { it is Trigger.TimeOfDay } -> "at ${ts.size} set times"
        else -> "on any of ${ts.size} triggers"
    }

    /** "At 08:00 on weekdays → Set ringer to silent", with long trigger lists counted. */
    fun oneLine(r: Routine): String =
        "${triggers(r.triggers).replaceFirstChar { it.uppercase() }} → ${r.thenSummary()}"
}
