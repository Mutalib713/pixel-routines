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

    /**
     * The end trigger for a start at [sh]:[sm] on [days] that ends at [eh]:[em]. When the end is
     * at or before the start, it's overnight, so it ends on the NEXT day. Before this, "10pm to
     * 6am on weekdays" ended at 6am on weekdays, so Friday night ran until Monday morning.
     */
    fun endAt(sh: Int, sm: Int, eh: Int, em: Int, days: Set<Int>): Trigger.TimeOfDay {
        val overnight = eh * 60 + em <= sh * 60 + sm
        return Trigger.TimeOfDay(eh, em, if (overnight) days.map { it % 7 + 1 }.toSet() else days)
    }

    /** "At 08:00 on weekdays → Set ringer to silent", with long trigger lists counted. */
    fun oneLine(r: Routine): String =
        "${triggers(r.triggers).replaceFirstChar { it.uppercase() }} → ${r.thenSummary()}"
}
