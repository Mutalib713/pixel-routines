package com.mosman.routines

import android.content.Context
import org.json.JSONArray

/** Single source of truth: routines persisted as JSON in SharedPreferences. */
object Store {
    private const val PREF = "pixel_routines"
    private const val KEY = "routines"
    private const val PREF_ACTIVE = "pixel_routines_active"
    private const val KEY_ACTIVE = "active"
    private const val PREF_APP = "pixel_routines_app"
    private const val KEY_ONBOARDED = "onboarded"

    fun load(ctx: Context): List<Routine> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Routine.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, list: List<Routine>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun get(ctx: Context, id: Long): Routine? = load(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(ctx: Context, r: Routine) {
        val list = load(ctx).filter { it.id != r.id } + r
        save(ctx, list.sortedBy { it.name.lowercase() })
        Engine.rearmAll(ctx)
        RoutinesWidget.refresh(ctx)
    }

    fun delete(ctx: Context, id: Long) {
        get(ctx, id)?.let {
            // Deleting a routine mid-run used to leave its changes in place for good.
            if (isActive(ctx, id)) Engine.endRoutine(ctx, it)
            Engine.disarm(ctx, it)
        }
        save(ctx, load(ctx).filter { it.id != id })
        Snapshot.clear(ctx, id)
        setActive(ctx, id, false)
        Engine.rearmAll(ctx)
        RoutinesWidget.refresh(ctx)
    }

    fun setEnabled(ctx: Context, id: Long, enabled: Boolean) {
        get(ctx, id)?.let {
            // Switching off a running routine (Lecture Mode mid-class) ends it and puts the
            // settings back. Before, it stayed on Vibrate and its end alarm was ignored.
            if (!enabled && isActive(ctx, id)) Engine.endRoutine(ctx, it)
            upsert(ctx, it.copy(enabled = enabled))
        }
    }

    // ---- Which routines are currently "running" (fired, waiting for their end) ----

    fun activeIds(ctx: Context): Set<Long> =
        activePrefs(ctx).getStringSet(KEY_ACTIVE, emptySet())!!
            .mapNotNull { it.toLongOrNull() }.toSet()

    fun isActive(ctx: Context, id: Long) = activeIds(ctx).contains(id)

    @Synchronized
    fun setActive(ctx: Context, id: Long, active: Boolean) {
        val now = activeIds(ctx).toMutableSet()
        if (active) now.add(id) else now.remove(id)
        activePrefs(ctx).edit()
            .putStringSet(KEY_ACTIVE, now.map { it.toString() }.toSet()).apply()
    }

    // ---- First-run onboarding ----

    fun onboarded(ctx: Context) = appPrefs(ctx).getBoolean(KEY_ONBOARDED, false)
    fun setOnboarded(ctx: Context) = appPrefs(ctx).edit().putBoolean(KEY_ONBOARDED, true).apply()

    // ---- Pause everything (Quick Settings tile / home banner) ----

    fun isPaused(ctx: Context) = appPrefs(ctx).getBoolean("paused", false)
    fun setPaused(ctx: Context, paused: Boolean) {
        appPrefs(ctx).edit().putBoolean("paused", paused).apply()
        // Pausing used to only stop routines STARTING, so one already running (Lecture Mode
        // holding the phone on Vibrate) carried on and "Pause all" looked broken. Now pausing
        // ends whatever is running and puts its settings back.
        if (paused) {
            val all = load(ctx).associateBy { it.id }
            activeIds(ctx).forEach { id ->
                val r = all[id]
                if (r != null) Engine.endRoutine(ctx, r) else setActive(ctx, id, false)
            }
        }
        RoutinesWidget.refresh(ctx)
        // Keep the Quick Settings tile in step when pausing from inside the app.
        runCatching {
            android.service.quicksettings.TileService.requestListeningState(
                ctx, android.content.ComponentName(ctx, RoutinesTile::class.java))
        }
    }

    // ---- Export / import (share your routines with friends) ----

    fun exportJson(ctx: Context): String {
        val arr = JSONArray()
        load(ctx).forEach { arr.put(it.toJson()) }
        return arr.toString(2)
    }

    /** Merges imported routines under fresh ids. Returns how many were added. */
    fun importJson(ctx: Context, json: String): Int {
        val incoming = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { Routine.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        if (incoming.isEmpty()) return 0
        var nextId = System.currentTimeMillis()
        val fresh = incoming.map { it.copy(id = nextId++) }
        save(ctx, load(ctx) + fresh)
        Engine.rearmAll(ctx)
        RoutinesWidget.refresh(ctx)
        return fresh.size
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun activePrefs(ctx: Context) = ctx.getSharedPreferences(PREF_ACTIVE, Context.MODE_PRIVATE)
    private fun appPrefs(ctx: Context) = ctx.getSharedPreferences(PREF_APP, Context.MODE_PRIVATE)
}
