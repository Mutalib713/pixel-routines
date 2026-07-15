package com.mosman.routines

/**
 * Placeholder for the Shizuku-powered toggles (Wi-Fi / Bluetooth / Airplane / mobile data).
 *
 * Google removed these APIs from normal apps, so the only no-root path is Shizuku
 * (https://shizuku.rikka.app) — it runs shell commands like `svc wifi enable` with
 * ADB-level rights. The action types exist and are pickable now so the capability is
 * visible in the UI; wiring the Shizuku binder + AIDL executor is the next milestone.
 *
 * Until then each call returns a clear status string instead of silently doing nothing.
 */
object Shizuku {
    private const val NEEDS = "needs Shizuku (tap Set up in Settings)"

    val available: Boolean get() = false   // becomes a real binder check once wired

    fun svc(service: String, on: Boolean): String =
        if (!available) "Wi-Fi toggle $NEEDS" else "Wi-Fi ${if (on) "on" else "off"}"

    fun bluetooth(on: Boolean): String =
        if (!available) "Bluetooth toggle $NEEDS" else "Bluetooth ${if (on) "on" else "off"}"

    fun airplane(on: Boolean): String =
        if (!available) "Airplane toggle $NEEDS" else "Airplane ${if (on) "on" else "off"}"
}
