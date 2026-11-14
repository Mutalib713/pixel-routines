package com.mosman.routines.volume

import android.content.Context
import com.mosman.routines.Engine
import com.mosman.routines.Permissions
import com.mosman.routines.Store
import com.mosman.routines.Trigger
import org.json.JSONObject

/**
 * Persistence layer for Volume Panel Triggers settings.
 * Stores settings safely as JSON in private SharedPreferences.
 */
object VolumePanelStore {
    private const val PREF_NAME = "pixel_volume_panel"
    private const val KEY_SETTINGS = "settings"

    fun load(ctx: Context): VolumePanelSettings {
        val raw = prefs(ctx).getString(KEY_SETTINGS, null) ?: return VolumePanelSettings()
        return runCatching {
            VolumePanelSettings.fromJson(JSONObject(raw))
        }.getOrDefault(VolumePanelSettings())
    }

    fun save(ctx: Context, settings: VolumePanelSettings) {
        prefs(ctx).edit()
            .putString(KEY_SETTINGS, settings.toJson().toString())
            .apply()
        syncServices(ctx)
    }

    fun update(ctx: Context, transform: (VolumePanelSettings) -> VolumePanelSettings): VolumePanelSettings {
        val current = load(ctx)
        val updated = transform(current)
        save(ctx, updated)
        return updated
    }

    /**
     * Re-arms everything after a settings change. The sensor triggers (shake, back-tap) live in
     * EventService and the overlays are synced at the end of rearmAll, so one call covers both.
     */
    fun syncServices(ctx: Context) {
        Engine.rearmAll(ctx.applicationContext)
    }

    /**
     * Starts or stops the two overlay services to match settings, routines and the
     * "Display over other apps" permission. Cheap enough to run on every app resume, which is
     * what makes the button appear right after the user grants that permission in Settings
     * (before, it only appeared after the app was fully restarted).
     */
    fun syncOverlays(ctx: Context) {
        val app = ctx.applicationContext
        val settings = load(app)
        val hasOverlay = Permissions.hasOverlay(app)

        if (settings.floatingButtonEnabled && hasOverlay) {
            FloatingVolumeService.start(app)
        } else {
            FloatingVolumeService.stop(app)
        }

        if (swipeEdges(app, settings).isNotEmpty() && hasOverlay) {
            SwipeTriggerService.start(app)
        } else {
            SwipeTriggerService.stop(app)
        }
    }

    /**
     * Every screen edge that needs a swipe zone: the volume-panel edge (if that's switched on)
     * plus any edge an enabled routine uses as a "Swipe from edge" trigger. Routines no longer
     * depend on the volume-panel switch being on with the same edge.
     */
    fun swipeEdges(ctx: Context, settings: VolumePanelSettings = load(ctx)): Set<SwipeDirection> {
        val edges = mutableSetOf<SwipeDirection>()
        if (settings.swipeEnabled) edges += settings.swipeDirection
        Store.load(ctx).filter { it.enabled }
            .flatMap { it.triggers + it.endTriggers }
            .filterIsInstance<Trigger.Swipe>()
            .forEach { edges += it.direction }
        return edges
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
