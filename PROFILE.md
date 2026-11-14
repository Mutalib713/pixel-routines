# Pixel Routines: Profile

The canonical record of decisions. If a session wants to change a Sacred Rule, it stops and asks Mutalib first.

## 1. What it is

Samsung-style "Modes & Routines" for a Pixel 6 Pro (Android 17). A routine reads as one line: IF something happens, THEN do these things, UNTIL something else, then restore. Package `com.mosman.routines`. Sideloaded; not on the Play Store.

## 2. Who it's for

Mutalib, on his own phone. He may make the app (and repo) public later, so nothing secret goes in the APK or in git.

## 3. Sacred Rules

1. **Every routine fits on one IF → THEN → UNTIL card.** No Tasker-style variables, scenes or scripting.
2. **Saved data must keep loading.** Enum constant names are never renamed. R8 keeps them (`proguard-rules.pro`). New JSON fields get defaults.
3. **The AI never saves, edits, pauses or deletes a routine without the user approving a proposal first.** A shown proposal isn't a saved routine.
4. **Never report success before the app has confirmed it.** That covers "Done!" messages, History lines and test buttons.
5. **Cloud AI goes through Firebase AI Logic with App Check.** No raw Gemini API key is ever put in the APK or committed. (Decided 2026-11-14, because the app may go public.)
6. **Gemini free models first.** Free-tier content may be used by Google and read by human reviewers, so the app sends only what a request needs: no contact phone numbers, no raw message text.
7. **Shizuku features degrade gracefully.** With Shizuku missing, stopped or not allowed, the feature says so in plain words. It never crashes and never pretends.
8. **Volume and button features never change the ringer behind the user's back.** The "preserve Silent & Vibrate" behaviour stays on by default.

## 4. Stack (agreed)

- Kotlin + Jetpack Compose, Material 3. AGP 9 with built-in Kotlin. minSdk 31, target/compile 36.
- Routines in SharedPreferences JSON (`Store.kt`). Room only for new structured data such as chat history.
- Cloud AI: Firebase AI Logic → Gemini Developer API (free Spark plan), App Check debug provider for development. A production attestation provider is needed before the app is shared.
- ADB-level actions (Wi-Fi, Bluetooth, airplane mode, pressing keys in other apps): Shizuku (`dev.rikka.shizuku` 13.1.5).

## 5. Features decided but not built yet

- **Unmute WhatsApp statuses** (branch `feature/whatsapp-status-unmute`). When a WhatsApp or WhatsApp Business status opens while the ringer is on Vibrate or Silent, Pixel Routines presses volume-up through Shizuku. WhatsApp treats a real key press as its own "unmute"; a plain volume change does not work. Both facts were tested on the Pixel on 2026-11-14.
- **Routine AI**: see the stages in the Antigravity plan PDF, rebuilt on Sacred Rules 3–6.

## 6. Not in v1

- No Gemini Nano. The Pixel 6 Pro can't run it.
- No accessibility service that reads other apps' screens.
- No automatic sending of WhatsApp messages. WhatsApp has no send API, so the app pre-fills and the user taps send.

## 7. Verification

- `gradlew testDebugUnitTest assembleDebug`: unit tests plus a build.
- `gradlew lintDebug`: lint already reports 7 errors in older files that predate the 2026-11 work. New code must not add any.
- Hardware checks happen on the Pixel 6 Pro when it's connected, otherwise on the Android Studio emulator (`wird_pixel6pro` AVD). That AVD is shared with the Wird project, so check which app is in front before tapping and put things back afterwards.
