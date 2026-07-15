package com.mosman.routines

import android.content.Context
import org.json.JSONArray

/** Single source of truth: routines persisted as JSON in SharedPreferences. */
object Store {
    private const val PREF = "pixel_routines"
    private const val KEY = "routines"

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

    /** Insert or replace by id, then re-arm and refresh the widget. */
    fun upsert(ctx: Context, r: Routine) {
        val list = load(ctx).filter { it.id != r.id } + r
        save(ctx, list.sortedBy { it.name.lowercase() })
        Engine.rearmAll(ctx)
        RoutinesWidget.refresh(ctx)
    }

    fun delete(ctx: Context, id: Long) {
        get(ctx, id)?.let { Engine.disarm(ctx, it) }
        save(ctx, load(ctx).filter { it.id != id })
        Engine.rearmAll(ctx)
        RoutinesWidget.refresh(ctx)
    }

    fun setEnabled(ctx: Context, id: Long, enabled: Boolean) {
        get(ctx, id)?.let { upsert(ctx, it.copy(enabled = enabled)) }
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
