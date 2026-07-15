package com.mosman.routines

import android.content.Context
import android.content.pm.PackageManager

/**
 * Helpers for the WRITE_SECURE_SETTINGS permission, which unlocks dark theme and
 * Battery Saver. It can't be granted from a normal app — the user runs, once, over USB:
 *
 *   adb shell pm grant com.mosman.routines android.permission.WRITE_SECURE_SETTINGS
 *
 * After that it persists across reboots forever (this is the Tasker approach).
 */
object Secure {
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    fun canWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}
