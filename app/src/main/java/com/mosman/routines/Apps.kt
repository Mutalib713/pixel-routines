package com.mosman.routines

import android.content.Context
import android.content.Intent

data class AppInfo(val label: String, val pkg: String)

/** Lists launchable apps for the "Open an app" action. */
object Apps {
    private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")   // consumer, business

    fun installed(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .mapNotNull {
                val pkg = it.activityInfo?.packageName ?: return@mapNotNull null
                AppInfo(it.loadLabel(pm).toString(), pkg)
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    /** Whichever WhatsApp flavour is installed, or null. */
    fun whatsappPackage(ctx: Context): String? = WHATSAPP.firstOrNull { pkg ->
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }
}
