package com.mosman.routines

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Runs ADB-level shell commands through Shizuku (https://shizuku.rikka.app), which is the
 * only no-root way to reach the toggles Google closed off to normal apps: Wi-Fi (since
 * Android 10), Bluetooth (since 13), airplane mode and mobile data.
 *
 * Everything degrades gracefully: if Shizuku isn't installed, isn't running, or hasn't been
 * granted, each call returns a plain-English status instead of throwing.
 */
object ShizukuBridge {
    const val REQUEST_CODE = 4242
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun installed(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true }.getOrDefault(false)

    /** Shizuku's service is alive (user started it via wireless debugging or root). */
    val running: Boolean
        get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    val granted: Boolean
        get() = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /** Ready to actually run commands. */
    val ready: Boolean get() = running && granted

    fun requestPermission() {
        runCatching { if (running && !granted) Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun openShizuku(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
            ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    /** Human-readable status for the setup screen. */
    fun status(ctx: Context): String = when {
        !installed(ctx) -> "Not installed"
        !running -> "Installed, but not running — start it in the Shizuku app"
        !granted -> "Running — tap to allow Pixel Routines"
        else -> "Connected"
    }

    // ---- Commands ----

    fun wifi(on: Boolean): String =
        run("svc wifi ${enableOrDisable(on)}", "Wi-Fi", on)

    fun bluetooth(on: Boolean): String =
        run("svc bluetooth ${enableOrDisable(on)}", "Bluetooth", on)

    fun airplane(on: Boolean): String =
        run("cmd connectivity airplane-mode ${enableOrDisable(on)}", "Airplane mode", on)

    private fun enableOrDisable(on: Boolean) = if (on) "enable" else "disable"

    private fun run(cmd: String, label: String, on: Boolean): String {
        if (!running) return "$label needs Shizuku running"
        if (!granted) return "$label needs Shizuku permission"
        return exec(cmd).fold(
            onSuccess = { "$label ${if (on) "on" else "off"}" },
            onFailure = { "$label failed (${it.message ?: "shizuku error"})" },
        )
    }

    /**
     * Shizuku's newProcess is hidden API (and slated for removal in favour of UserService),
     * so it's reached reflectively. If a future Shizuku drops it, callers just see a failure.
     */
    private fun exec(cmd: String): Result<String> = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val err = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code != 0) throw IllegalStateException(err.ifBlank { "exit $code" })
        out
    }
}
