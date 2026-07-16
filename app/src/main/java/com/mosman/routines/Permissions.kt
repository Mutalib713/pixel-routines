package com.mosman.routines

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/** One-stop shop for the special accesses the app needs, plus intents to request them. */
object Permissions {

    fun hasDnd(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun hasExactAlarm(ctx: Context) =
        ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun hasWriteSettings(ctx: Context) = Settings.System.canWrite(ctx)

    fun hasNotifications(ctx: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun hasFineLocation(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(ctx: Context) =
        Build.VERSION.SDK_INT < 29 ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasSecureSettings(ctx: Context) = Secure.canWriteSecure(ctx)

    fun dndSettings() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun exactAlarmSettings(ctx: Context) =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))

    fun writeSettings(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Background location can only be granted from the app's own settings page. */
    fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    @Suppress("DEPRECATION")
    fun currentSsid(ctx: Context): String? {
        if (!hasFineLocation(ctx)) return null
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val ssid = wm?.connectionInfo?.ssid ?: return null
        val clean = ssid.trim('"')
        return if (clean.isBlank() || clean == "<unknown ssid>") null else clean
    }

    /** Which accesses a set of routines actually needs, and whether each is granted. */
    fun needed(ctx: Context, routines: List<Routine>): List<Access> {
        val used = buildSet {
            add(Access.DND) // ringer/dnd extremely common; always surface if missing
            routines.forEach { r -> r.actions.forEach { add(it.access()) } }
        }
        return used.filter { acc ->
            when (acc) {
                Access.NONE -> false
                Access.DND -> !hasDnd(ctx)
                Access.WRITE_SETTINGS -> !hasWriteSettings(ctx)
                Access.SECURE_SETTINGS -> !hasSecureSettings(ctx)
                Access.SHIZUKU -> !ShizukuBridge.ready
            }
        }
    }
}
