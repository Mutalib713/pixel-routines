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

/** Action triggered by secondary gestures on the floating volume button. */
enum class FloatingAction {
    VOLUME_PANEL,
    VOLUME_UP,
    VOLUME_DOWN,
    NONE;

    fun label(): String = when (this) {
        VOLUME_PANEL -> "Open Volume Panel"
        VOLUME_UP -> "Volume Up"
        VOLUME_DOWN -> "Volume Down"
        NONE -> "Disabled"
    }
}

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

    // General Settings
    val cooldownDurationMs: Long = 1500L,
    val hapticFeedbackEnabled: Boolean = true,
    val screenOnOnly: Boolean = true,

    // Bug Fix: Preserve user's manual Silent & Vibrate mode choices
    val preserveSilentVibrate: Boolean = true,
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
        put("cooldownDurationMs", cooldownDurationMs)
        put("hapticFeedbackEnabled", hapticFeedbackEnabled)
        put("screenOnOnly", screenOnOnly)
        put("preserveSilentVibrate", preserveSilentVibrate)
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
        )
    }
}
