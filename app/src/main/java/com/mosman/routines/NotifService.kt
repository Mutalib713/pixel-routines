package com.mosman.routines

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** A notification we can still answer, remembered per app. */
data class Repliable(val pkg: String, val action: Notification.Action, val at: Long)

/**
 * Holds the newest repliable notification per app. This is what makes hands-free WhatsApp
 * replies work: WhatsApp exposes no send API, but every messaging notification carries a
 * RemoteInput reply action that any notification listener may fire.
 */
object NotifRegistry {
    private val byPackage = mutableMapOf<String, Repliable>()

    fun remember(pkg: String, action: Notification.Action) {
        synchronized(byPackage) { byPackage[pkg] = Repliable(pkg, action, System.currentTimeMillis()) }
    }

    fun forget(pkg: String) = synchronized(byPackage) { byPackage.remove(pkg) }

    /** Newest repliable notification, optionally restricted to one app. */
    fun latest(pkg: String?): Repliable? = synchronized(byPackage) {
        if (!pkg.isNullOrBlank()) byPackage[pkg]
        else byPackage.values.maxByOrNull { it.at }
    }

    /** Sends [text] back through the notification's own reply action. */
    fun reply(ctx: Context, pkg: String?, text: String): String {
        val target = latest(pkg) ?: return "No message to reply to"
        val inputs = target.action.remoteInputs
        if (inputs.isNullOrEmpty()) return "That notification can't be replied to"
        return runCatching {
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, results)
            target.action.actionIntent.send(ctx, 0, intent)
            "Replied: “$text”"
        }.getOrElse { "Reply failed" }
    }
}

/**
 * Listens for notifications so routines can trigger on them (and reply to them).
 * Requires the user to switch on notification access in Settings.
 */
class NotifService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val pkg = sbn.packageName ?: return

        // Remember any reply action so a ReplyNotification action can use it.
        n.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
            ?.let { NotifRegistry.remember(pkg, it) }

        // Ignore our own notifications, or a routine could trigger itself.
        if (pkg == packageName) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return   // persistent/foreground noise

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val haystack = "$title $body"

        Engine.handleEvent(applicationContext) { t ->
            t is Trigger.NotificationFrom && t.pkg == pkg &&
                (t.contains.isBlank() || haystack.contains(t.contains, ignoreCase = true))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep the registry from replying into a conversation that's already gone.
        if (activeNotifications?.none { it.packageName == sbn.packageName } == true)
            NotifRegistry.forget(sbn.packageName)
    }
}
