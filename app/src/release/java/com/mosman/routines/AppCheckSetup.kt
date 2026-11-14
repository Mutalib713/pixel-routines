package com.mosman.routines

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize

/**
 * RELEASE builds. Play Integrity proves requests come from the genuine app. Note: it checks
 * the app came from Google Play, so a sideloaded release APK may be refused by Firebase
 * AI Logic. [NEEDS: test before the app is shared publicly]
 */
object AppCheckSetup {
    fun install(app: Application) {
        Firebase.initialize(app)
        Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
