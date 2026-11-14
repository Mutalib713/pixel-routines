package com.mosman.routines.ai

import android.content.Context
import androidx.core.content.edit

/** Which assistant answers. */
enum class AssistantMode {
    /** Offline first; Gemini only for messages the offline assistant couldn't handle. */
    AUTOMATIC,
    /** Never sends anything off the phone. */
    OFFLINE_ONLY,
    /** Gemini for every typed message (photos still stay on the phone). */
    ONLINE;

    fun label() = when (this) {
        AUTOMATIC -> "Automatic"
        OFFLINE_ONLY -> "Offline only"
        ONLINE -> "Online (Gemini)"
    }

    fun detail() = when (this) {
        AUTOMATIC -> "On this phone first. Gemini only when needed"
        OFFLINE_ONLY -> "Nothing leaves your phone"
        ONLINE -> "Every message goes to Gemini"
    }
}

/**
 * Assistant settings. Cloud consent is asked the first time a message would go to Gemini and
 * is stored here; "Offline only" always wins, consent or not.
 */
object AssistantPrefs {
    private const val PREFS = "pixel_assistant"
    private const val KEY_MODE = "mode"
    private const val KEY_CONSENT = "cloud_consent"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(ctx: Context): AssistantMode =
        runCatching { AssistantMode.valueOf(prefs(ctx).getString(KEY_MODE, null)!!) }.getOrDefault(AssistantMode.AUTOMATIC)

    fun setMode(ctx: Context, mode: AssistantMode) = prefs(ctx).edit { putString(KEY_MODE, mode.name) }

    fun hasConsent(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CONSENT, false)

    fun setConsent(ctx: Context, given: Boolean) = prefs(ctx).edit { putBoolean(KEY_CONSENT, given) }

    /** Should this message go to Gemini (consent aside)? Photos never do yet. */
    fun wantsOnline(mode: AssistantMode, offlineHandled: Boolean, hasPhoto: Boolean): Boolean = when {
        hasPhoto -> false
        mode == AssistantMode.OFFLINE_ONLY -> false
        mode == AssistantMode.ONLINE -> true
        else -> !offlineHandled
    }
}
