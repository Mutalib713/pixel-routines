package com.mosman.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/** Fires a routine's time trigger and re-arms the next occurrence. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        val index = intent.getIntExtra(Scheduler.EXTRA_INDEX, 0)
        if (id > 0) Engine.onTimeFired(context, id, index)
    }
}

/** Re-arms everything after reboot, app update, or clock changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Engine.rearmAll(context)
    }
}

/** Handles geofence enter/exit transitions (v3 location triggers). */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val enter = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        event.triggeringGeofences?.forEach { g ->
            val parts = g.requestId.split(":")
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val index = parts.getOrNull(1)?.toIntOrNull() ?: 0
            Engine.onGeofence(context, id, index, enter)
        }
    }
}
