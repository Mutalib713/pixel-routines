package com.mosman.routines.ui.lens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineDraft
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object RoutineLensProcessor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processImageUri(context: Context, uri: Uri): Result<RoutineDraft> {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val extractedText = recognizeText(image)
            if (extractedText.isBlank()) {
                Result.failure(Exception("No readable text found in the image. Please take a clearer photo."))
            } else {
                val draft = parseScheduleText(extractedText)
                Result.success(draft)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun processBitmap(bitmap: Bitmap): Result<RoutineDraft> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val extractedText = recognizeText(image)
            if (extractedText.isBlank()) {
                Result.failure(Exception("No readable text found in the image. Please take a clearer photo."))
            } else {
                val draft = parseScheduleText(extractedText)
                Result.success(draft)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { exception ->
                    cont.resumeWithException(exception)
                }
        }

    fun parseScheduleText(rawText: String): RoutineDraft {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lowerText = rawText.lowercase()

        // 1. Detect Days of Week (1=Mon ... 7=Sun)
        val days = mutableSetOf<Int>()
        if (lowerText.contains("mon") || lowerText.contains("monday")) days.add(1)
        if (lowerText.contains("tue") || lowerText.contains("tuesday")) days.add(2)
        if (lowerText.contains("wed") || lowerText.contains("wednesday")) days.add(3)
        if (lowerText.contains("thu") || lowerText.contains("thursday")) days.add(4)
        if (lowerText.contains("fri") || lowerText.contains("friday")) days.add(5)
        if (lowerText.contains("sat") || lowerText.contains("saturday")) days.add(6)
        if (lowerText.contains("sun") || lowerText.contains("sunday")) days.add(7)

        val activeDays = if (days.isEmpty()) {
            if (lowerText.contains("weekend")) setOf(6, 7)
            else setOf(1, 2, 3, 4, 5) // Default to weekdays
        } else days

        // 2. Detect Times
        var startHour = 8
        var startMinute = 0
        var endHour = 17
        var endMinute = 0
        var hasEndTime = false

        // Pattern for time range: e.g. "08:00 - 10:00" or "8am - 2pm" or "10:30 to 12:30"
        val rangePattern = Pattern.compile(
            "(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?\\s*(?:-|to|–|—)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?",
            Pattern.CASE_INSENSITIVE
        )
        val rangeMatcher = rangePattern.matcher(rawText)

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
            hasEndTime = true
        } else {
            // Single time pattern
            val singlePattern = Pattern.compile("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE)
            val singleMatcher = singlePattern.matcher(rawText)
            if (singleMatcher.find()) {
                var sH = singleMatcher.group(1)?.toIntOrNull() ?: 8
                val sM = singleMatcher.group(2)?.toIntOrNull() ?: 0
                val sAmPm = singleMatcher.group(3)?.lowercase()
                if (sAmPm == "pm" && sH < 12) sH += 12
                if (sAmPm == "am" && sH == 12) sH = 0
                startHour = sH.coerceIn(0, 23)
                startMinute = sM.coerceIn(0, 59)
                endHour = (startHour + 2) % 24
                hasEndTime = true
            }
        }

        // 3. Detect Context & Naming
        val routineName: String
        val icon: String
        val actions: List<Action>

        when {
            lowerText.contains("knust") || lowerText.contains("lecture") || lowerText.contains("class") || lowerText.contains("exam") || lowerText.contains("timetable") -> {
                val courseCode = lines.firstOrNull { it.matches(Regex(".*[A-Z]{2,4}\\s*\\d{3}.*")) }
                routineName = if (courseCode != null) "$courseCode Lecture Mode" else "Class Lecture Mode"
                icon = "school"
                actions = listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true))
            }

            lowerText.contains("shift") || lowerText.contains("work") || lowerText.contains("office") || lowerText.contains("meeting") -> {
                routineName = "Work Shift Mode"
                icon = "work"
                actions = listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true))
            }

            lowerText.contains("night") || lowerText.contains("sleep") || lowerText.contains("bedtime") -> {
                routineName = "Night Schedule"
                icon = "bedtime"
                actions = listOf(Action.DarkTheme(true), Action.Dnd(true), Action.Brightness(15))
            }

            lowerText.contains("study") || lowerText.contains("library") -> {
                routineName = "Library Study Focus"
                icon = "school"
                actions = listOf(Action.Ringer(RingerMode.SILENT), Action.Dnd(true))
            }

            else -> {
                val firstLine = lines.firstOrNull { it.length in 4..30 } ?: "Scanned Timetable"
                routineName = "$firstLine Mode"
                icon = "event"
                actions = listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true))
            }
        }

        val triggers = listOf(Trigger.TimeOfDay(hour = startHour, minute = startMinute, days = activeDays))
        val endTriggers = if (hasEndTime) {
            listOf(Trigger.TimeOfDay(hour = endHour, minute = endMinute, days = activeDays))
        } else emptyList()

        val proposedRoutine = Routine(
            id = System.currentTimeMillis(),
            name = routineName,
            icon = icon,
            triggers = triggers,
            actions = actions,
            endTriggers = endTriggers,
        )

        val dayNames = if (activeDays.size == 5 && !activeDays.contains(6) && !activeDays.contains(7)) "weekdays"
        else if (activeDays.size == 7) "every day"
        else if (activeDays == setOf(6, 7)) "weekends"
        else "${activeDays.size} days a week"

        val timeString = String.format("%02d:%02d", startHour, startMinute)
        val endTimeString = if (hasEndTime) String.format(" to %02d:%02d", endHour, endMinute) else ""

        val explanation = "Extracted from scanned image: detected schedule on $dayNames ($timeString$endTimeString). Automatically silences phone during this window."

        return RoutineDraft(
            draftId = "lens-${System.currentTimeMillis()}",
            operation = DraftOperation.CREATE,
            proposedRoutine = proposedRoutine,
            explanation = explanation,
            requiresApproval = true,
        )
    }
}
