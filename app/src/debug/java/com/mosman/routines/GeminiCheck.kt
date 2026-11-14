package com.mosman.routines

import android.app.Application
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.mosman.routines.ai.GeminiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG builds only: proves the whole chain works (Firebase project → App Check → AI Logic →
 * Gemini) with one tiny request, and logs the outcome under tag "PixelRoutinesGemini".
 * Runs until it succeeds once per install, then stops, so it doesn't spend free-tier quota
 * on every launch. Sends no personal data.
 */
object GeminiCheck {
    private const val TAG = "PixelRoutinesGemini"
    private const val PREFS = "pixel_gemini_check"
    private const val KEY_OK = "ok"

    fun runOnceAfterInstall(app: Application) {
        val prefs = app.getSharedPreferences(PREFS, Application.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OK, false)) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel(GeminiConfig.MODEL)
                    .generateContent("Reply with the single word OK.")
                    .text
            }.onSuccess { reply ->
                Log.i(TAG, "Gemini reachable via Firebase AI Logic (${GeminiConfig.MODEL}): \"${reply?.trim()}\"")
                prefs.edit { putBoolean(KEY_OK, true) }
            }.onFailure {
                Log.w(TAG, "Gemini check failed (${GeminiConfig.MODEL}): ${it::class.simpleName}: ${it.message}")
            }
        }
    }
}
