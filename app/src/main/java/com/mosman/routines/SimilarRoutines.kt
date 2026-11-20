package com.mosman.routines

/**
 * Finds saved routines that would clash with, or repeat, one you're about to save.
 * Used by the editor (manual) and the chat proposal card, so both warn the same way.
 *
 * It only flags things that actually matter:
 *  - the same name;
 *  - the same gesture or swipe (both routines would fire at once);
 *  - the same trigger AND changing the same setting;
 *  - overlapping times AND changing the same setting (e.g. two routines both setting the ringer).
 * Two routines that merely run at the same time but touch different things are fine.
 */
object SimilarRoutines {

    data class Similar(val routine: Routine, val reason: String) {
        fun message() = "Similar to “${routine.name.trim()}”: $reason."
    }

    /**
     * One line per distinct warning. Two copies of the same routine (same name, same reason)
     * become one line that says how many there are, instead of repeating the line.
     */
    fun lines(hits: List<Similar>): List<String> =
        hits.groupBy { it.routine.name.trim().lowercase() to it.reason }.values.map { group ->
            val first = group.first()
            if (group.size == 1) first.message()
            else "Similar to “${first.routine.name.trim()}” (you have ${group.size} of these): ${first.reason}."
        }

    fun find(candidate: Routine, existing: List<Routine>): List<Similar> =
        existing.filter { it.id != candidate.id }.mapNotNull { other ->
            reason(candidate, other)?.let { Similar(other, it) }
        }

    private fun reason(a: Routine, b: Routine): String? {
        if (a.name.isNotBlank() && a.name.trim().equals(b.name.trim(), ignoreCase = true))
            return "it has the same name"

        val shared = sharedSetting(a, b)

        a.triggers.firstOrNull { t ->
            (t is Trigger.Gesture || t is Trigger.Swipe) && b.triggers.contains(t)
        }?.let { return "it also starts with “${it.describe()}”, so both would run" }

        if (shared != null && a.triggers.any { b.triggers.contains(it) })
            return "it starts the same way and also changes $shared"

        if (shared != null && windowsOverlap(windows(a), windows(b)))
            return "it runs at overlapping times and also changes $shared"

        return null
    }

    /** The first setting both routines change, in plain words, or null. */
    private fun sharedSetting(a: Routine, b: Routine): String? {
        val bKeys = b.actions.map { key(it) }.toSet()
        return a.actions.firstOrNull { key(it) in bKeys }?.let { label(it) }
    }

    private fun key(a: Action): String =
        if (a is Action.Volume) "volume-${a.stream}" else a::class.simpleName ?: a.toString()

    private fun label(a: Action): String = when (a) {
        is Action.Ringer -> "the ringer"
        is Action.Dnd -> "Do Not Disturb"
        is Action.Volume -> "${a.stream.label().lowercase()} volume"
        is Action.Brightness -> "brightness"
        is Action.DarkTheme -> "dark theme"
        is Action.BatterySaver -> "Battery Saver"
        is Action.AutoRotate -> "auto-rotate"
        is Action.Flashlight -> "the flashlight"
        is Action.WifiToggle -> "Wi-Fi"
        is Action.BluetoothToggle -> "Bluetooth"
        is Action.AirplaneToggle -> "airplane mode"
        else -> "“${a.describe()}”"
    }

    /** A stretch of one day, in minutes from midnight, on some weekdays (1 = Mon … 7 = Sun). */
    internal data class Window(val days: Set<Int>, val start: Int, val end: Int)

    /**
     * Turns time triggers into stretches of time. A start with a matching time-based end runs
     * until that end; a start with no end is treated as a one-minute moment.
     */
    internal fun windows(r: Routine): List<Window> {
        val ends = r.endTriggers.filterIsInstance<Trigger.TimeOfDay>()
        return r.triggers.filterIsInstance<Trigger.TimeOfDay>().map { s ->
            val start = s.hour * 60 + s.minute
            val end = ends.filter { e -> e.days.any { it in s.days } }
                .map { it.hour * 60 + it.minute }
                .filter { it > start }
                .minOrNull() ?: (start + 1)
            Window(s.days, start, end)
        }
    }

    internal fun windowsOverlap(a: List<Window>, b: List<Window>): Boolean =
        a.any { x -> b.any { y -> x.days.any { it in y.days } && x.start < y.end && y.start < x.end } }
}
