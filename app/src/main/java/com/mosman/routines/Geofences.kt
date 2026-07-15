package com.mosman.routines

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/** Registers circular geofences for Location triggers (v3). */
object Geofences {

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, GeofenceReceiver::class.java).setAction("com.mosman.routines.GEOFENCE")
        return PendingIntent.getBroadcast(ctx, 424242, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    fun requestId(id: Long, index: Int) = "$id:$index"

    @SuppressLint("MissingPermission")
    fun add(ctx: Context, r: Routine, index: Int, t: Trigger.Location) {
        if (!Permissions.hasBackgroundLocation(ctx)) return
        val transitions =
            (if (t.enter) Geofence.GEOFENCE_TRANSITION_ENTER else 0) or
                (if (!t.enter) Geofence.GEOFENCE_TRANSITION_EXIT else 0)
        val fence = Geofence.Builder()
            .setRequestId(requestId(r.id, index))
            .setCircularRegion(t.lat, t.lng, t.radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                if (transitions == 0) Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                else transitions)
            .build()
        val req = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(fence)
            .build()
        runCatching { LocationServices.getGeofencingClient(ctx).addGeofences(req, pending(ctx)) }
    }

    fun remove(ctx: Context, r: Routine) {
        val ids = r.triggers.mapIndexedNotNull { i, t ->
            if (t is Trigger.Location) requestId(r.id, i) else null
        }
        if (ids.isNotEmpty())
            runCatching { LocationServices.getGeofencingClient(ctx).removeGeofences(ids) }
    }

    fun syncAll(ctx: Context) {
        Store.load(ctx).filter { it.enabled }.forEach { r ->
            r.triggers.forEachIndexed { i, t -> if (t is Trigger.Location) add(ctx, r, i, t) }
        }
    }
}
