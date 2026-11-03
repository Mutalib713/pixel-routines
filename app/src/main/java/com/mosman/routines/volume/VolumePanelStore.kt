package com.mosman.routines.volume

import android.content.Context
import com.mosman.routines.Engine
import com.mosman.routines.Permissions
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
     * Synchronizes all volume trigger services based on current configuration and permissions.
     */
    fun syncServices(ctx: Context) {
        val app = ctx.applicationContext
        val settings = load(app)
        val hasOverlay = Permissions.hasOverlay(app)

        // 1. Floating Button overlay service
        if (settings.floatingButtonEnabled && hasOverlay) {
            FloatingVolumeService.start(app)
        } else {
            FloatingVolumeService.stop(app)
        }

        // 2. Edge Swipe Gesture overlay service
        if (settings.swipeEnabled && hasOverlay) {
            SwipeTriggerService.start(app)
        } else {
            SwipeTriggerService.stop(app)
        }

        // 3. Sensor motion triggers (Shake & Back-tap managed via EventService in Engine.rearmAll)
        Engine.rearmAll(app)
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
