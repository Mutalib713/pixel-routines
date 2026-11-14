package com.mosman.routines

import android.app.Application

/**
 * Runs once when the app process starts, before any screen or service.
 * Firebase App Check must be installed before the first Gemini request, or Firebase AI Logic
 * (which has App Check enforced) rejects it. Debug and release builds each have their own
 * AppCheckSetup (src/debug, src/release), so the debug provider can never ship in a release.
 */
class RoutinesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckSetup.install(this)
    }
}
