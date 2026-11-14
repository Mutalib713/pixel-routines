package com.mosman.routines

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * DEBUG builds only. Uses App Check's debug provider: on first use it prints a "debug secret"
 * to logcat, which has to be added once in the Firebase console
 * (Security → App Check → Apps → ⋮ → Manage debug tokens). Each phone or emulator install
 * gets its own secret. Keep it private, and never ship this provider in a release.
 */
object AppCheckSetup {
    fun install(app: Application) {
        Firebase.initialize(app)
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        // Ask for a token once at start-up so the debug secret shows up in logcat straight away,
        // instead of only when the assistant first goes online.
        Firebase.appCheck.getAppCheckToken(false)
            .addOnSuccessListener { Log.i(TAG, "App Check token OK (debug secret is registered)") }
            .addOnFailureListener { Log.w(TAG, "App Check token failed: ${it.message}") }
        GeminiCheck.runOnceAfterInstall(app)
    }

    private const val TAG = "PixelRoutinesAppCheck"
}
