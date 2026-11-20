package com.mosman.routines.volume

import org.json.JSONObject

/**
 * Sensitivity thresholds for motion-based triggers (Shake and Back-Tap).
 * - LOW: Requires more intense movement / higher force to trigger. Resists false positives.
 * - MEDIUM: Balanced for everyday usage.
 * - HIGH: Requires less force, more responsive to lighter movements.
 */
enum class Sensitivity {
    LOW, MEDIUM, HIGH;

    fun label(): String = when (this) {
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
    }

    /** Accelerometer threshold in m/s² for shake detection (delta above gravity). */
    fun shakeThreshold(): Float = when (this) {
        LOW -> 18.0f     // Stricter, deliberate shake
        MEDIUM -> 13.0f  // Balanced
        HIGH -> 9.0f     // Sensitive
    }

    /** Accelerometer Z-axis impulse threshold in m/s² for back-tap detection. */
    fun backTapZThreshold(): Float = when (this) {
        LOW -> 5.5f
        MEDIUM -> 3.8f
        HIGH -> 2.6f
    }
}

/** Direction from which an edge swipe gesture originates. */
enum class SwipeDirection {
    LEFT, RIGHT, TOP, BOTTOM;

    fun label(): String = when (this) {
        LEFT -> "Swipe from Left edge"
        RIGHT -> "Swipe from Right edge"
        TOP -> "Swipe from Top edge"
        BOTTOM -> "Swipe from Bottom edge"
    }

    fun shortLabel(): String = when (this) {
        LEFT -> "Left edge"
        RIGHT -> "Right edge"
        TOP -> "Top edge"
        BOTTOM -> "Bottom edge"
    }
}

/** On-screen size options for the floating volume button. */
enum class ButtonSize(val dpSize: Int) {
    SMALL(42),
    MEDIUM(50),
    LARGE(58);

    fun label(): String = when (this) {
        SMALL -> "Small (42dp)"
        MEDIUM -> "Medium (50dp)"
        LARGE -> "Large (58dp)"
    }
}

/**
 * A job a gesture or button can do. It started as the floating button's list (the first four);
 * the rest were added 2026-11-20 when Controls became general. Names are saved: append only,
 * never rename (Sacred Rule 2).
 */
enum class FloatingAction {
    VOLUME_PANEL,
    VOLUME_UP,
    VOLUME_DOWN,
    NONE,
    FLASHLIGHT,
    RINGER_TOGGLE,
    DND_TOGGLE,
    RUN_ROUTINE;

    fun label(): String = when (this) {
        VOLUME_PANEL -> "Volume panel"
        VOLUME_UP -> "Volume up"
        VOLUME_DOWN -> "Volume down"
        NONE -> "Nothing"
        FLASHLIGHT -> "Flashlight"
        RINGER_TOGGLE -> "Sound / Vibrate"
        DND_TOGGLE -> "Do Not Disturb"
        RUN_ROUTINE -> "Run a routine"
    }

    /** Longer wording for the picker sheet. */
    fun pickerLabel(): String = when (this) {
        VOLUME_PANEL -> "Show the volume panel"
        VOLUME_UP -> "Volume up"
        VOLUME_DOWN -> "Volume down"
        NONE -> "Nothing"
        FLASHLIGHT -> "Flashlight on or off"
        RINGER_TOGGLE -> "Switch Sound / Vibrate"
        DND_TOGGLE -> "Do Not Disturb on or off"
        RUN_ROUTINE -> "Run a routine…"
    }
}

/** What one gesture does: a job, plus which routine when the job is RUN_ROUTINE. */
data class ControlJob(val action: FloatingAction = FloatingAction.VOLUME_PANEL, val routineId: Long = 0L)

/**
 * Data model for Volume Panel Triggers configuration.
 * All triggers are independent and disabled by default with safe defaults.
 */
data class VolumePanelSettings(
    // Trigger 1: Shake
    val shakeEnabled: Boolean = false,
    val shakeSensitivity: Sensitivity = Sensitivity.MEDIUM,

    // Trigger 2: Double-Tap Back (Experimental)
    val backTapEnabled: Boolean = false,
    val backTapSensitivity: Sensitivity = Sensitivity.MEDIUM,

    // Trigger 3: Swipe Gesture
    val swipeEnabled: Boolean = false,
    val swipeDirection: SwipeDirection = SwipeDirection.RIGHT,

    // Trigger 4: Floating Button
    val floatingButtonEnabled: Boolean = false,
    val floatingButtonSize: ButtonSize = ButtonSize.MEDIUM,
    val floatingButtonOpacity: Float = 0.85f,
    val floatingButtonX: Int = -1,
    val floatingButtonY: Int = -1,
    val floatingDoubleTapAction: FloatingAction = FloatingAction.VOLUME_DOWN,
    val floatingLongPressAction: FloatingAction = FloatingAction.VOLUME_UP,
    val floatingDoubleTapRoutineId: Long = 0L,
    val floatingLongPressRoutineId: Long = 0L,

    // Jobs per gesture (2026-11-20). Each used to only open the volume panel; that stays the default.
    val shakeJob: ControlJob = ControlJob(),
    val backTapJob: ControlJob = ControlJob(),
    val swipeJob: ControlJob = ControlJob(),
    val floatingTapJob: ControlJob = ControlJob(),
    val flipEnabled: Boolean = false,
    val flipJob: ControlJob = ControlJob(FloatingAction.RINGER_TOGGLE),

    // General Settings
    val cooldownDurationMs: Long = 1500L,
    val hapticFeedbackEnabled: Boolean = true,
    val screenOnOnly: Boolean = true,

    // Bug Fix: Preserve user's manual Silent & Vibrate mode choices
    val preserveSilentVibrate: Boolean = true,

    // Unmute WhatsApp statuses (presses volume-up through Shizuku when a status opens muted)
    val statusUnmuteEnabled: Boolean = false,
    /** Also press volume-down straight after, so the unmute doesn't nudge the volume up. */
    val statusUnmuteKeepLevel: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shakeEnabled", shakeEnabled)
        put("shakeSensitivity", shakeSensitivity.name)
        put("backTapEnabled", backTapEnabled)
        put("backTapSensitivity", backTapSensitivity.name)
        put("swipeEnabled", swipeEnabled)
        put("swipeDirection", swipeDirection.name)
        put("floatingButtonEnabled", floatingButtonEnabled)
        put("floatingButtonSize", floatingButtonSize.name)
        put("floatingButtonOpacity", floatingButtonOpacity.toDouble())
        put("floatingButtonX", floatingButtonX)
        put("floatingButtonY", floatingButtonY)
        put("floatingDoubleTapAction", floatingDoubleTapAction.name)
        put("floatingLongPressAction", floatingLongPressAction.name)
        put("floatingDoubleTapRoutineId", floatingDoubleTapRoutineId)
        put("floatingLongPressRoutineId", floatingLongPressRoutineId)
        putJob("shakeJob", shakeJob)
        putJob("backTapJob", backTapJob)
        putJob("swipeJob", swipeJob)
        putJob("floatingTapJob", floatingTapJob)
        put("flipEnabled", flipEnabled)
        putJob("flipJob", flipJob)
        put("cooldownDurationMs", cooldownDurationMs)
        put("hapticFeedbackEnabled", hapticFeedbackEnabled)
        put("screenOnOnly", screenOnOnly)
        put("preserveSilentVibrate", preserveSilentVibrate)
        put("statusUnmuteEnabled", statusUnmuteEnabled)
        put("statusUnmuteKeepLevel", statusUnmuteKeepLevel)
    }

    companion object {
        fun fromJson(json: JSONObject): VolumePanelSettings = VolumePanelSettings(
            shakeEnabled = json.optBoolean("shakeEnabled", false),
            shakeSensitivity = runCatching {
                Sensitivity.valueOf(json.optString("shakeSensitivity", Sensitivity.MEDIUM.name))
            }.getOrDefault(Sensitivity.MEDIUM),
            backTapEnabled = json.optBoolean("backTapEnabled", false),
            backTapSensitivity = runCatching {
                Sensitivity.valueOf(json.optString("backTapSensitivity", Sensitivity.MEDIUM.name))
            }.getOrDefault(Sensitivity.MEDIUM),
            swipeEnabled = json.optBoolean("swipeEnabled", false),
            swipeDirection = runCatching {
                SwipeDirection.valueOf(json.optString("swipeDirection", SwipeDirection.RIGHT.name))
            }.getOrDefault(SwipeDirection.RIGHT),
            floatingButtonEnabled = json.optBoolean("floatingButtonEnabled", false),
            floatingButtonSize = runCatching {
                ButtonSize.valueOf(json.optString("floatingButtonSize", ButtonSize.MEDIUM.name))
            }.getOrDefault(ButtonSize.MEDIUM),
            floatingButtonOpacity = json.optDouble("floatingButtonOpacity", 0.85).toFloat()
                .coerceIn(0.2f, 1.0f),
            floatingButtonX = json.optInt("floatingButtonX", -1),
            floatingButtonY = json.optInt("floatingButtonY", -1),
            floatingDoubleTapAction = runCatching {
                FloatingAction.valueOf(json.optString("floatingDoubleTapAction", FloatingAction.VOLUME_DOWN.name))
            }.getOrDefault(FloatingAction.VOLUME_DOWN),
            floatingLongPressAction = runCatching {
                FloatingAction.valueOf(json.optString("floatingLongPressAction", FloatingAction.VOLUME_UP.name))
            }.getOrDefault(FloatingAction.VOLUME_UP),
            cooldownDurationMs = json.optLong("cooldownDurationMs", 1500L).coerceIn(500L, 5000L),
            hapticFeedbackEnabled = json.optBoolean("hapticFeedbackEnabled", true),
            screenOnOnly = json.optBoolean("screenOnOnly", true),
            preserveSilentVibrate = json.optBoolean("preserveSilentVibrate", true),
            statusUnmuteEnabled = json.optBoolean("statusUnmuteEnabled", false),
            statusUnmuteKeepLevel = json.optBoolean("statusUnmuteKeepLevel", false),
            floatingDoubleTapRoutineId = json.optLong("floatingDoubleTapRoutineId", 0L),
            floatingLongPressRoutineId = json.optLong("floatingLongPressRoutineId", 0L),
            shakeJob = json.job("shakeJob", ControlJob()),
            backTapJob = json.job("backTapJob", ControlJob()),
            swipeJob = json.job("swipeJob", ControlJob()),
            floatingTapJob = json.job("floatingTapJob", ControlJob()),
            flipEnabled = json.optBoolean("flipEnabled", false),
            flipJob = json.job("flipJob", ControlJob(FloatingAction.RINGER_TOGGLE)),
        )

        private fun JSONObject.job(key: String, default: ControlJob): ControlJob = ControlJob(
            runCatching { FloatingAction.valueOf(optString(key, default.action.name)) }.getOrDefault(default.action),
            optLong("${key}Routine", default.routineId),
        )
    }
}

private fun JSONObject.putJob(key: String, j: ControlJob) {
    put(key, j.action.name)
    put("${key}Routine", j.routineId)
}
