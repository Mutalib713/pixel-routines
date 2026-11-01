package com.mosman.routines.ui.assistant

import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineDraft
import java.util.regex.Pattern

data class ParsedAIResponse(
    val explanation: String,
    val draft: RoutineDraft?,
)

object RoutineAIParser {

    /**
     * Parses free-form user text into a structured RoutineDraft.
     * Supports Ghanaian/student contexts (KNUST, campus, lectures),
     * time schedules, battery levels, ringer modes, DND, and dark theme.
     */
    fun parse(userText: String, extractedOcrText: String? = null): ParsedAIResponse {
        val combined = if (extractedOcrText.isNullOrBlank()) userText else "$userText\n$extractedOcrText"
        val lower = combined.lowercase()

        // 1. Detect Days of Week (1=Mon ... 7=Sun)
        val days = mutableSetOf<Int>()
        if (lower.contains("mon") || lower.contains("monday")) days.add(1)
        if (lower.contains("tue") || lower.contains("tuesday")) days.add(2)
        if (lower.contains("wed") || lower.contains("wednesday")) days.add(3)
        if (lower.contains("thu") || lower.contains("thursday")) days.add(4)
        if (lower.contains("fri") || lower.contains("friday")) days.add(5)
        if (lower.contains("sat") || lower.contains("saturday")) days.add(6)
        if (lower.contains("sun") || lower.contains("sunday")) days.add(7)

        val activeDays = if (days.isEmpty()) {
            if (lower.contains("weekend")) setOf(6, 7)
            else if (lower.contains("every day") || lower.contains("daily") || lower.contains("night") || lower.contains("bedtime")) setOf(1, 2, 3, 4, 5, 6, 7)
            else setOf(1, 2, 3, 4, 5) // Default to weekdays
        } else days

        // 2. Detect Times
        var startHour: Int? = null
        var startMinute: Int = 0
        var endHour: Int? = null
        var endMinute: Int = 0

        // Time range: e.g. "8am to 5pm", "08:00 - 17:00", "from 2pm to 4pm"
        val rangePattern = Pattern.compile(
            "(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?\\s*(?:-|to|–|—)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE
        )
        val rangeMatcher = rangePattern.matcher(combined)

        if (rangeMatcher.find()) {
            var sH = rangeMatcher.group(1)?.toIntOrNull() ?: 8
            val sM = rangeMatcher.group(2)?.toIntOrNull() ?: 0
            val sAmPm = rangeMatcher.group(3)?.lowercase()

            var eH = rangeMatcher.group(4)?.toIntOrNull() ?: 17
            val eM = rangeMatcher.group(5)?.toIntOrNull() ?: 0
            val eAmPm = rangeMatcher.group(6)?.lowercase()

            if (sAmPm == "pm" && sH < 12) sH += 12
            if (sAmPm == "am" && sH == 12) sH = 0
            if (eAmPm == "pm" && eH < 12) eH += 12
            if (eAmPm == "am" && eH == 12) eH = 0

            startHour = sH.coerceIn(0, 23)
            startMinute = sM.coerceIn(0, 59)
            endHour = eH.coerceIn(0, 23)
            endMinute = eM.coerceIn(0, 59)
        } else {
            // Single time: e.g. "at 10pm", "at 22:30", "at 7:00 am"
            val singlePattern = Pattern.compile("(?:at|@)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
            val singleMatcher = singlePattern.matcher(combined)
            if (singleMatcher.find()) {
                var sH = singleMatcher.group(1)?.toIntOrNull() ?: 8
                val sM = singleMatcher.group(2)?.toIntOrNull() ?: 0
                val sAmPm = singleMatcher.group(3)?.lowercase()
                if (sAmPm == "pm" && sH < 12) sH += 12
                if (sAmPm == "am" && sH == 12) sH = 0
                startHour = sH.coerceIn(0, 23)
                startMinute = sM.coerceIn(0, 59)
            }
        }

        // 3. Triggers
        val triggers = mutableListOf<Trigger>()
        val endTriggers = mutableListOf<Trigger>()

        // Battery Trigger
        val batteryMatch = Pattern.compile("(?:below|under|drops? to|<)?\\s*(\\d{1,2})%\\s*(?:battery)?", Pattern.CASE_INSENSITIVE).matcher(combined)
        if (batteryMatch.find() || lower.contains("battery")) {
            val level = batteryMatch.group(1)?.toIntOrNull() ?: 20
            triggers.add(Trigger.Battery(below = true, level = level))
        }

        // Time Trigger if time was detected or implied
        if (triggers.isEmpty()) {
            val h = startHour ?: when {
                lower.contains("bedtime") || lower.contains("sleep") || lower.contains("night") -> 22
                lower.contains("morning") || lower.contains("wake") -> 7
                lower.contains("study") || lower.contains("afternoon") -> 14
                lower.contains("knust") || lower.contains("lecture") || lower.contains("class") -> 8
                else -> 8
            }
            val m = startMinute
            triggers.add(Trigger.TimeOfDay(hour = h, minute = m, days = activeDays))

            if (endHour != null) {
                endTriggers.add(Trigger.TimeOfDay(hour = endHour, minute = endMinute, days = activeDays))
            } else if (lower.contains("lecture") || lower.contains("class") || lower.contains("study")) {
                endTriggers.add(Trigger.TimeOfDay(hour = (h + 2) % 24, minute = m, days = activeDays))
            }
        }

        // 4. Actions
        val actions = mutableListOf<Action>()

        // Ringer mode
        if (lower.contains("silent") || lower.contains("mute")) {
            actions.add(Action.Ringer(RingerMode.SILENT))
        } else if (lower.contains("vibrate")) {
            actions.add(Action.Ringer(RingerMode.VIBRATE))
        } else if (lower.contains("sound") || lower.contains("loud") || lower.contains("ring")) {
            actions.add(Action.Ringer(RingerMode.SOUND))
        }

        // DND
        if (lower.contains("dnd") || lower.contains("do not disturb") || lower.contains("quiet") || lower.contains("silence") || lower.contains("sleep") || lower.contains("bedtime") || lower.contains("lecture") || lower.contains("study")) {
            if (actions.none { it is Action.Dnd }) {
                actions.add(Action.Dnd(true))
            }
        }

        // Dark Theme
        if (lower.contains("dark") || lower.contains("night") || lower.contains("bedtime")) {
            actions.add(Action.DarkTheme(true))
        }

        // Brightness
        val brightMatch = Pattern.compile("brightness\\s*(?:to|=)?\\s*(\\d{1,3})%?", Pattern.CASE_INSENSITIVE).matcher(combined)
        if (brightMatch.find()) {
            val b = brightMatch.group(1)?.toIntOrNull()?.coerceIn(0, 100) ?: 20
            actions.add(Action.Brightness(b))
        } else if (lower.contains("night") || lower.contains("bedtime") || lower.contains("sleep")) {
            actions.add(Action.Brightness(15))
        }

        // Battery saver
        if (lower.contains("battery") || lower.contains("saver") || lower.contains("power")) {
            actions.add(Action.BatterySaver(true))
            if (actions.none { it is Action.Brightness }) {
                actions.add(Action.Brightness(25))
            }
        }

        // Fallback default action
        if (actions.isEmpty()) {
            actions.add(Action.Ringer(RingerMode.VIBRATE))
            actions.add(Action.Dnd(true))
        }

        // 5. Title & Icon
        val title: String
        val icon: String
        when {
            lower.contains("bedtime") || lower.contains("sleep") || lower.contains("night") -> {
                title = "Bedtime Routine"
                icon = "bedtime"
            }
            lower.contains("knust") || lower.contains("lecture") || lower.contains("class") -> {
                title = if (lower.contains("knust")) "KNUST Lecture Mode" else "Class Lecture Mode"
                icon = "school"
            }
            lower.contains("study") || lower.contains("focus") -> {
                title = "Study Focus"
                icon = "school"
            }
            lower.contains("battery") -> {
                title = "Battery Saver Auto"
                icon = "saver"
            }
            lower.contains("work") || lower.contains("shift") -> {
                title = "Work Mode"
                icon = "work"
            }
            else -> {
                val cleaned = userText.trim().replace(Regex("[^a-zA-Z0-9 ]"), "").take(26)
                title = if (cleaned.isNotBlank()) cleaned.replaceFirstChar { it.uppercase() } else "Custom Routine"
                icon = "star"
            }
        }

        val routine = Routine(
            id = System.currentTimeMillis(),
            name = title,
            icon = icon,
            triggers = triggers,
            actions = actions,
            endTriggers = endTriggers,
        )

        val explanation = buildString {
            append("I created a proposal for “$title”. ")
            when (val tr = triggers.firstOrNull()) {
                is Trigger.TimeOfDay -> {
                    val timeStr = String.format("%02d:%02d", tr.hour, tr.minute)
                    append("It starts at $timeStr. ")
                }
                is Trigger.Battery -> append("It triggers when battery drops below ${tr.level}%. ")
                else -> {}
            }
            if (endTriggers.isNotEmpty()) {
                val end = endTriggers.first() as? Trigger.TimeOfDay
                if (end != null) {
                    val endTime = String.format("%02d:%02d", end.hour, end.minute)
                    append("Settings will restore automatically at $endTime.")
                }
            }
        }

        val draft = RoutineDraft(
            draftId = "ai-${System.currentTimeMillis()}",
            operation = DraftOperation.CREATE,
            proposedRoutine = routine,
            explanation = explanation,
            requiresApproval = true,
        )

        return ParsedAIResponse(
            explanation = "Here is what I put together based on your request. Review the settings below and confirm to save:",
            draft = draft,
        )
    }
}
