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

    fun upsert(ctx: Context, r: Routine) {
        val list = load(ctx).filter { it.id != r.id } + r
        save(ctx, list.sortedBy { it.name.lowercase() })
        Engine.rearmAll(ctx)
        RoutinesWidget.refresh(ctx)
    }

    fun delete(ctx: Context, id: Long) {
        get(ctx, id)?.let { Engine.disarm(ctx, it) }
        save(ctx, load(ctx).filter { it.id != id })
        Snapshot.clear(ctx, id)
        setActive(ctx, id, false)
        Engine.rearmAll(ctx)
        RoutinesWidget.refresh(ctx)
    }

    fun setEnabled(ctx: Context, id: Long, enabled: Boolean) {
        get(ctx, id)?.let { upsert(ctx, it.copy(enabled = enabled)) }
    }

    // ---- Which routines are currently "running" (fired, waiting for their end) ----

    fun activeIds(ctx: Context): Set<Long> =
        activePrefs(ctx).getStringSet(KEY_ACTIVE, emptySet())!!
            .mapNotNull { it.toLongOrNull() }.toSet()

    fun isActive(ctx: Context, id: Long) = activeIds(ctx).contains(id)

    fun setActive(ctx: Context, id: Long, active: Boolean) {
        val now = activeIds(ctx).toMutableSet()
        if (active) now.add(id) else now.remove(id)
        activePrefs(ctx).edit()
            .putStringSet(KEY_ACTIVE, now.map { it.toString() }.toSet()).apply()
    }

    // ---- First-run onboarding ----

    fun onboarded(ctx: Context) = appPrefs(ctx).getBoolean(KEY_ONBOARDED, false)
    fun setOnboarded(ctx: Context) = appPrefs(ctx).edit().putBoolean(KEY_ONBOARDED, true).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun activePrefs(ctx: Context) = ctx.getSharedPreferences(PREF_ACTIVE, Context.MODE_PRIVATE)
    private fun appPrefs(ctx: Context) = ctx.getSharedPreferences(PREF_APP, Context.MODE_PRIVATE)
}
