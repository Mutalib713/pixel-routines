# Pixel Routines

My own version of Samsung's **Modes & Routines** for the Google Pixel 6 Pro — schedule your phone to change sound mode, Do Not Disturb, volumes, brightness and auto-rotate at exact times, automatically.

Built with **Kotlin + Jetpack Compose + Material You** (dynamic color — the app themes itself from your wallpaper and follows system light/dark, just like Google's own apps). No root required.

## Features (v1)

- ⏰ Unlimited routines, each with a name, exact time and days of the week
- 🔕 Sound mode: **Sound / Vibrate / Silent**
- 🌙 **Do Not Disturb** on/off
- 🔊 Media / Ring / Alarm volume levels
- 💡 Brightness level and auto-rotate on/off
- ▶️ **Run now** button to test any routine instantly
- ⏭️ "Next run" countdown on every routine
- 🔔 Optional notification confirming each run
- ♻️ Survives reboots, app updates, time and timezone changes
- 🎯 Exact-minute triggers that work through Doze (deep sleep)

## How it works

`AlarmManager.setExactAndAllowWhileIdle()` fires a `BroadcastReceiver` at the routine's
time; the receiver applies the actions and re-arms the next occurrence. A boot receiver
re-arms everything after restarts. Routines are stored as JSON in `SharedPreferences` —
no database, no background service, no battery drain.

| Capability | Requires |
|---|---|
| Exact alarms | `USE_EXACT_ALARM` (auto-granted) |
| Ringer + DND | Do Not Disturb access (one-time toggle in Settings, the app guides you) |
| Brightness / rotate | "Modify system settings" (one-time toggle) |
| Run confirmations | Notification permission (optional) |

## Building (no Android Studio needed)

Requirements: Android SDK (platform 36 + build tools) and a JDK 17+.
This repo builds from the command line only — handy on low-RAM machines.

```powershell
# Windows (PowerShell) — adjust paths to your JDK / Gradle
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
gradle -p . :app:assembleRelease --no-daemon
```

APK lands in `app/build/outputs/apk/release/`.

**Signing:** create your own keystore once (the file is git-ignored):

```powershell
keytool -genkeypair -keystore routines.keystore -alias routines -keyalg RSA `
  -keysize 2048 -validity 10950 -storepass routines2026 -keypass routines2026 `
  -dname "CN=Routines"
```

**Note (Avast users):** if dependency downloads fail with "not found", Avast's HTTPS
scanning is re-signing TLS. `gradle.properties` in this repo already contains the fix
(`systemProp.javax.net.ssl.trustStoreType=Windows-ROOT`).

## Install on the phone

1. Copy the APK over (USB, Google Drive, or `adb install`).
2. Tap it → allow installs from that app → **Install anyway** if Play Protect warns
   (expected for self-built apps).
3. Open Routines and grant the accesses it asks for on the home screen.

## Roadmap

- **v1.1** — Dark theme + Battery Saver actions (via one-time
  `adb shell pm grant … WRITE_SECURE_SETTINGS`, the Tasker approach), launch-app action, reminders
- **v2** — Trigger engine: Bluetooth device connects, Wi-Fi network, battery level,
  charging state, with IF/AND conditions
- **v2.5** — Optional [Shizuku](https://shizuku.rikka.app) module: Wi-Fi / Bluetooth /
  mobile data / airplane-mode toggles
- **v3** — Location (geofence) routines, e.g. arrive at campus → vibrate
