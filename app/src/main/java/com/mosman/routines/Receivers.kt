package com.mosman.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by AlarmManager at the routine's time (also used by "Run now"). */
class RoutineFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        val r = Store.load(context).firstOrNull { it.id == id } ?: return
        if (r.enabled) {
            val results = Actions.apply(context, r)
            Actions.notifyRan(context, r, results)
        }
        // Self-healing: re-arm everything (idempotent, PendingIntents just get replaced)
        Scheduler.rescheduleAll(context)
    }
}

/** Re-arms all alarms after reboot, app update, or time/timezone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context)
    }
}
