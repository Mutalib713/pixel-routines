package com.mosman.routines

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Driving / walking / cycling detection via Google's activity-recognition API — this is
 * how "Do Not Disturb while driving" works without needing car Bluetooth.
 */
object Motion {

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, MotionReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(ctx, 515151, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    const val ACTION = "com.mosman.routines.MOTION"

    fun activityOf(t: MotionType): Int = when (t) {
        MotionType.VEHICLE -> DetectedActivity.IN_VEHICLE
        MotionType.WALKING -> DetectedActivity.WALKING
        MotionType.RUNNING -> DetectedActivity.RUNNING
        MotionType.BICYCLE -> DetectedActivity.ON_BICYCLE
        MotionType.STILL -> DetectedActivity.STILL
    }

    private fun typeOf(activity: Int): MotionType? = when (activity) {
        DetectedActivity.IN_VEHICLE -> MotionType.VEHICLE
        DetectedActivity.WALKING -> MotionType.WALKING
        DetectedActivity.RUNNING -> MotionType.RUNNING
        DetectedActivity.ON_BICYCLE -> MotionType.BICYCLE
        DetectedActivity.STILL -> MotionType.STILL
        else -> null
    }

    /** Subscribes to exactly the transitions the user's routines care about. */
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context) {
        val wanted = Store.load(ctx).filter { it.enabled }
            .flatMap { it.triggers + it.endTriggers }
            .filterIsInstance<Trigger.Motion>()
            .map { it.type }.distinct()
        if (wanted.isEmpty() || !Permissions.hasActivityRecognition(ctx)) { stop(ctx); return }

        val transitions = wanted.flatMap { t ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(activityOf(t))
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
                ActivityTransition.Builder()
                    .setActivityType(activityOf(t))
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
            )
        }
        runCatching {
            ActivityRecognition.getClient(ctx).requestActivityTransitionUpdates(
                ActivityTransitionRequest(transitions), pending(ctx))
        }
    }

    fun stop(ctx: Context) {
        runCatching {
            ActivityRecognition.getClient(ctx).removeActivityTransitionUpdates(pending(ctx))
        }
    }

    fun onResult(ctx: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { e ->
            val type = typeOf(e.activityType) ?: return@forEach
            val entering = e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            Engine.handleEvent(ctx) { t ->
                t is Trigger.Motion && t.type == type && t.entering == entering
            }
        }
    }
}

class MotionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        runCatching { Motion.onResult(context.applicationContext, intent) }
        pending.finish()
    }
}
