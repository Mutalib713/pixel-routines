package com.mosman.routines

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(val at: Long, val name: String, val icon: String,
                    val kind: String, val results: List<String>)

/** Ring buffer of the last 100 routine runs — the History screen (Samsung parity). */
object RunLog {
    private const val PREF = "pixel_routines_log"
    private const val KEY = "entries"
    private const val MAX = 100

    fun add(ctx: Context, r: Routine, kind: String, results: List<String>) {
        val all = load(ctx).toMutableList()
        all.add(0, LogEntry(System.currentTimeMillis(), r.name.ifBlank { "Untitled" }, r.icon, kind, results))
        while (all.size > MAX) all.removeAt(all.size - 1)
        val arr = JSONArray()
        all.forEach { e ->
            arr.put(JSONObject().put("at", e.at).put("name", e.name).put("icon", e.icon)
                .put("kind", e.kind).put("results", JSONArray(e.results)))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun load(ctx: Context): List<LogEntry> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val res = o.optJSONArray("results") ?: JSONArray()
                LogEntry(o.getLong("at"), o.optString("name"), o.optString("icon", "star"),
                    o.optString("kind", "ran"),
                    (0 until res.length()).map { res.getString(it) })
            }
        }.getOrDefault(emptyList())
    }

    fun clear(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
