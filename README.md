# Pixel Routines

My own version of Samsung's **Modes & Routines**, built for the Google Pixel — the automation
app Pixels never shipped. Tell your phone *"IF this happens, THEN do that"* and it just
happens: go silent at bedtime, vibrate on campus, Battery Saver when you're low, dark theme
after dark.

**Kotlin · Jetpack Compose · Material You** (the UI themes itself from your wallpaper and
follows system light/dark, like Google's own apps). No root required.

---

## What it does

### IF — triggers
| | |
|---|---|
| ⏱ **Time of day** | at a set time, on the days you choose |
| 🔋 **Battery level** | drops below / rises above a % |
| 🔌 **Charging** | charger connected or unplugged |
| 🎧 **Headphones** | wired headset in or out |
| 🔵 **Bluetooth** | a device (or any device) connects/disconnects |
| 📶 **Wi-Fi** | joining or leaving a network |
| 📍 **Location** | arriving at or leaving a place — pick it on the built-in map |
| 📱 **Screen** / ✈️ **Airplane mode** | turns on or off |

Combine several with **Any** or **All**, and narrow them with **conditions** (only on
weekdays, only between times, only under a battery %, only while charging).

### THEN — actions
Ringer (silent/vibrate/sound) · Do Not Disturb · any volume (media, ring, notification,
alarm, call) · brightness · auto-rotate · dark theme · Battery Saver · open an app ·
flashlight · reminders · Wi-Fi / Bluetooth / airplane toggles.

### UNTIL — end conditions
Give a routine an end condition and choose what happens when it stops:

- **Undo the changes** *(default)* — the app snapshots your settings before the routine runs
  and puts them back exactly as they were
- **Leave as is**
- **Run other actions**

### Also
- **Home-screen widget** — every routine as a tap-to-toggle row (On / Off / Running)
- **Ideas gallery** — ready-made routines, one tap to add
- **Run now** — test any routine instantly
- Survives reboots, app updates, clock and timezone changes

---

## What Android allows (and the ways around it)

| Capability | How it's unlocked |
|---|---|
| Ringer, DND, volumes | **Do Not Disturb access** — one toggle, the app walks you through it |
| Exact timing | `USE_EXACT_ALARM`, auto-granted |
| Brightness, auto-rotate | **Modify system settings** — one toggle |
| **Dark theme, Battery Saver** | one command from a computer, once (Settings → *Copy command*):<br>`adb shell pm grant com.mosman.routines android.permission.WRITE_SECURE_SETTINGS`<br>It sticks forever, even across reboots. This is how Tasker does it. |
| **Wi-Fi / Bluetooth / airplane toggles** | Google removed these APIs from normal apps (Wi-Fi in Android 10, Bluetooth in 13). The only no-root route is **[Shizuku](https://shizuku.rikka.app)** — Settings shows live status and guides setup. |
| Location triggers | Location permission set to **Allow all the time** (background) |

Anything not listed needs root, and is out of scope.

---

## How it works

`AlarmManager.setExactAndAllowWhileIdle` drives time triggers; a light foreground service
listens for live events (battery, charging, Bluetooth, Wi-Fi, headphones, screen); geofences
come from `GeofencingClient`. A boot receiver re-arms everything after a restart. Routines are
JSON in `SharedPreferences` — no database.

Snapshots are the neat bit: the "before" state is captured as the very same `Action` objects,
so undoing a routine is just replaying them.

```
Model.kt      sealed Trigger / Condition / Action + Routine
Engine.kt     arms triggers, evaluates conditions, fires and ends routines
Snapshot.kt   captures/restores state for "undo the changes"
Actions.kt    executes each action
EventService  foreground service for live event triggers
Geofences.kt  location triggers
PlacePicker   in-app map + place search (OpenStreetMap, no API key)
ShizukuBridge ADB-level shell for the restricted toggles
```

---

## Building (no Android Studio needed)

Needs the Android SDK (platform 36 + build-tools) and a JDK 17+. Builds from the command
line only — handy on a low-RAM machine.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
gradle -p . :app:assembleRelease --no-daemon
```

APK lands in `app/build/outputs/apk/release/` (~3.4 MB, R8-minified).

**Signing** — create your own keystore once (it's git-ignored):

```powershell
keytool -genkeypair -keystore routines.keystore -alias routines -keyalg RSA `
  -keysize 2048 -validity 10950 -storepass routines2026 -keypass routines2026 -dname "CN=Routines"
```

**Avast users:** its Web Shield re-signs HTTPS, which the build JVM won't trust, and Gradle
misreports it as *"plugin not found"*. `gradle.properties` points at a local truststore —
rebuild it with `keytool` if Avast is reinstalled.

**Gotchas worth knowing:** AGP 9 has built-in Kotlin, so the `org.jetbrains.kotlin.android`
plugin must *not* be applied. Enum constant names are pinned in `proguard-rules.pro` because
routines are stored by enum name — letting R8 rename them would silently wipe saved data on
upgrade.

## Install

Copy the APK to the phone (Drive or USB) → tap it → allow installs from that app → Play
Protect will warn about any self-built app, choose **More details → Install anyway**.
