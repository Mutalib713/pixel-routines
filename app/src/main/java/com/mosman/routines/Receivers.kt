package com.mosman.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Fires a routine's time/sun trigger. goAsync() keeps the process alive while the actions
 * run on a background thread (needed for Wait actions and slow settings writes).
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        val index = intent.getIntExtra(Scheduler.EXTRA_INDEX, 0)
        if (id <= 0) return
        val pending = goAsync()
        Engine.onTimeFired(context, id, index) { pending.finish() }
    }
}

/** Re-arms all alarms after reboot, app update, or clock changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Engine.rearmAll(context)
    }
}

/** Handles geofence enter/exit transitions (location triggers). */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val enter = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        val fences = event.triggeringGeofences.orEmpty()
        if (fences.isEmpty()) return
        val pending = goAsync()
        var remaining = fences.size
        fences.forEach { g ->
            val parts = g.requestId.split(":")
            val id = parts.getOrNull(0)?.toLongOrNull()
            val index = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val done = { synchronized(pending) { if (--remaining == 0) pending.finish() } }
            if (id == null) done() else Engine.onGeofence(context, id, index, enter, done)
        }
    }
}
