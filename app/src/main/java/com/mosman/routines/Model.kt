package com.mosman.routines

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

enum class RingerAction { NO_CHANGE, SOUND, VIBRATE, SILENT }
enum class Toggle { NO_CHANGE, ON, OFF }

fun RingerAction.label(): String = when (this) {
    RingerAction.NO_CHANGE -> "Keep"
    RingerAction.SOUND -> "Sound"
    RingerAction.VIBRATE -> "Vibrate"
    RingerAction.SILENT -> "Silent"
}

data class Routine(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>, // ISO: 1 = Monday .. 7 = Sunday
    val ringer: RingerAction = RingerAction.NO_CHANGE,
    val dnd: Toggle = Toggle.NO_CHANGE,
    val mediaVol: Int? = null,   // 0..100 or null = don't touch
    val ringVol: Int? = null,
    val alarmVol: Int? = null,
    val brightness: Int? = null, // 0..100 or null
    val autoRotate: Toggle = Toggle.NO_CHANGE,
) {
    fun hasAnyAction(): Boolean =
        ringer != RingerAction.NO_CHANGE || dnd != Toggle.NO_CHANGE ||
            mediaVol != null || ringVol != null || alarmVol != null ||
            brightness != null || autoRotate != Toggle.NO_CHANGE

    fun nextTrigger(from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (days.isEmpty()) return null
        for (d in 0..7L) {
            val date = from.toLocalDate().plusDays(d)
            val cand = date.atTime(hour, minute).atZone(from.zone)
            if (cand.isAfter(from) && days.contains(date.dayOfWeek.value)) return cand
        }
        return null
    }

    fun summary(): String {
        val parts = mutableListOf<String>()
        if (ringer != RingerAction.NO_CHANGE) parts += "Ringer " + ringer.label().lowercase()
        when (dnd) { Toggle.ON -> parts += "DND on"; Toggle.OFF -> parts += "DND off"; else -> {} }
        mediaVol?.let { parts += "Media $it%" }
        ringVol?.let { parts += "Ring $it%" }
        alarmVol?.let { parts += "Alarm $it%" }
        brightness?.let { parts += "Brightness $it%" }
        when (autoRotate) { Toggle.ON -> parts += "Rotate on"; Toggle.OFF -> parts += "Rotate off"; else -> {} }
        return if (parts.isEmpty()) "No actions" else parts.joinToString(" · ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("enabled", enabled)
        put("hour", hour); put("minute", minute)
        put("days", JSONArray(days.toList()))
        put("ringer", ringer.name); put("dnd", dnd.name)
        put("mediaVol", mediaVol ?: -1); put("ringVol", ringVol ?: -1); put("alarmVol", alarmVol ?: -1)
        put("brightness", brightness ?: -1)
        put("autoRotate", autoRotate.name)
    }

    companion object {
        fun fromJson(o: JSONObject): Routine {
            val daysArr = o.optJSONArray("days") ?: JSONArray()
            val days = buildSet { for (i in 0 until daysArr.length()) add(daysArr.getInt(i)) }
            fun opt(k: String): Int? = o.optInt(k, -1).let { if (it < 0) null else it }
            return Routine(
                id = o.getLong("id"),
                name = o.optString("name", "Routine"),
                enabled = o.optBoolean("enabled", true),
                hour = o.optInt("hour", 8),
                minute = o.optInt("minute", 0),
                days = days,
                ringer = runCatching { RingerAction.valueOf(o.optString("ringer")) }.getOrDefault(RingerAction.NO_CHANGE),
                dnd = runCatching { Toggle.valueOf(o.optString("dnd")) }.getOrDefault(Toggle.NO_CHANGE),
                mediaVol = opt("mediaVol"), ringVol = opt("ringVol"), alarmVol = opt("alarmVol"),
                brightness = opt("brightness"),
                autoRotate = runCatching { Toggle.valueOf(o.optString("autoRotate")) }.getOrDefault(Toggle.NO_CHANGE),
            )
        }
    }
}

object Store {
    private const val PREF = "routines"
    private const val KEY = "list"

    fun load(ctx: Context): List<Routine> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Routine.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(ctx: Context, list: List<Routine>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
