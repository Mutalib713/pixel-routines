package com.mosman.routines

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

// ============================================================================
//  Enums
// ============================================================================

enum class RingerMode { SILENT, VIBRATE, SOUND;
    fun label() = when (this) { SILENT -> "Silent"; VIBRATE -> "Vibrate"; SOUND -> "Sound" }
}

enum class StreamType { MEDIA, RING, NOTIFICATION, ALARM, CALL;
    fun label() = when (this) {
        MEDIA -> "Media"; RING -> "Ring"; NOTIFICATION -> "Notification"; ALARM -> "Alarm"; CALL -> "Call"
    }
}

/** How multiple triggers combine. */
enum class Match { ANY, ALL;
    fun label() = if (this == ANY) "Any of these" else "All of these"
}

/** What happens when a routine's end condition is met (Samsung-style). */
enum class EndMode { NOTHING, REVERT, CUSTOM;
    fun label() = when (this) {
        NOTHING -> "Leave as is"
        REVERT -> "Undo the changes"
        CUSTOM -> "Run other actions"
    }
    fun describe() = when (this) {
        NOTHING -> "keep the settings"
        REVERT -> "put the settings back"
        CUSTOM -> "run the end actions"
    }
}

// ============================================================================
//  Triggers  (the "IF")
// ============================================================================

sealed class Trigger {
    abstract fun describe(): String
    abstract fun icon(): String
    abstract fun toJson(): JSONObject

    data class TimeOfDay(val hour: Int, val minute: Int, val days: Set<Int>) : Trigger() {
        override fun icon() = "schedule"
        override fun describe() = "At ${LocalTime.of(hour, minute)} on ${daysLabel(days)}"
        override fun toJson() = JSONObject().put("t", "time").put("h", hour).put("m", minute)
            .put("days", JSONArray(days.toList()))
    }

    data class Battery(val below: Boolean, val level: Int) : Trigger() {
        override fun icon() = "battery"
        override fun describe() = "Battery ${if (below) "drops below" else "rises above"} $level%"
        override fun toJson() = JSONObject().put("t", "battery").put("below", below).put("level", level)
    }

    data class Power(val connected: Boolean) : Trigger() {
        override fun icon() = "power"
        override fun describe() = if (connected) "Charger connected" else "Charger disconnected"
        override fun toJson() = JSONObject().put("t", "power").put("on", connected)
    }

    data class Headset(val connected: Boolean) : Trigger() {
        override fun icon() = "headphones"
        override fun describe() = if (connected) "Headphones plugged in" else "Headphones unplugged"
        override fun toJson() = JSONObject().put("t", "headset").put("on", connected)
    }

    data class Bluetooth(val connected: Boolean, val deviceName: String?) : Trigger() {
        override fun icon() = "bluetooth"
        override fun describe() =
            "Bluetooth ${deviceName ?: "any device"} ${if (connected) "connects" else "disconnects"}"
        override fun toJson() = JSONObject().put("t", "bt").put("on", connected)
            .put("name", deviceName ?: JSONObject.NULL)
    }

    data class Wifi(val connected: Boolean, val ssid: String?) : Trigger() {
        override fun icon() = "wifi"
        override fun describe() =
            "Wi-Fi ${ssid ?: "any network"} ${if (connected) "connects" else "disconnects"}"
        override fun toJson() = JSONObject().put("t", "wifi").put("on", connected)
            .put("ssid", ssid ?: JSONObject.NULL)
    }

    data class Location(val enter: Boolean, val lat: Double, val lng: Double,
                        val radius: Float, val place: String) : Trigger() {
        override fun icon() = "location"
        override fun describe() = "${if (enter) "Arrive at" else "Leave"} $place"
        override fun toJson() = JSONObject().put("t", "loc").put("enter", enter)
            .put("lat", lat).put("lng", lng).put("radius", radius).put("place", place)
    }

    data class Screen(val on: Boolean) : Trigger() {
        override fun icon() = "screen"
        override fun describe() = if (on) "Screen turns on" else "Screen turns off"
        override fun toJson() = JSONObject().put("t", "screen").put("on", on)
    }

    data class Airplane(val on: Boolean) : Trigger() {
        override fun icon() = "flight"
        override fun describe() = "Airplane mode turns ${if (on) "on" else "off"}"
        override fun toJson() = JSONObject().put("t", "air").put("on", on)
    }

    /** A notification arrives from an app, optionally containing some text. */
    data class NotificationFrom(val pkg: String, val label: String, val contains: String) : Trigger() {
        override fun icon() = "notify"
        override fun describe(): String {
            val what = if (contains.isBlank()) "" else " saying “$contains”"
            return "$label notification$what"
        }
        override fun toJson() = JSONObject().put("t", "notif").put("pkg", pkg)
            .put("label", label).put("contains", contains)
    }

    /** A calendar event starts or ends. */
    data class CalendarEvent(val titleContains: String, val atStart: Boolean) : Trigger() {
        override fun icon() = "calendar"
        override fun describe(): String {
            val which = if (titleContains.isBlank()) "any event" else "“$titleContains”"
            return "$which ${if (atStart) "starts" else "ends"} on my calendar"
        }
        override fun toJson() = JSONObject().put("t", "cal")
            .put("title", titleContains).put("start", atStart)
    }

    /** You start (or stop) driving, walking, cycling, running. */
    data class Motion(val type: MotionType, val entering: Boolean) : Trigger() {
        override fun icon() = type.icon()
        override fun describe() =
            "${if (entering) "You start" else "You stop"} ${type.verb()}"
        override fun toJson() = JSONObject().put("t", "motion")
            .put("type", type.name).put("in", entering)
    }

    /** A physical gesture: phone placed face-down, or shaken. */
    data class Gesture(val type: GestureType) : Trigger() {
        override fun icon() = "screen"
        override fun describe() = type.label()
        override fun toJson() = JSONObject().put("t", "gesture").put("type", type.name)
    }

    /** An app is brought to the foreground. */
    data class AppOpened(val pkg: String, val label: String) : Trigger() {
        override fun icon() = "app"
        override fun describe() = "You open $label"
        override fun toJson() = JSONObject().put("t", "appopen").put("pkg", pkg).put("label", label)
    }

    /** An NFC tag is tapped. */
    data class NfcTag(val id: String, val label: String) : Trigger() {
        override fun icon() = "nfc"
        override fun describe() = "You tap the “$label” NFC tag"
        override fun toJson() = JSONObject().put("t", "nfc").put("id", id).put("label", label)
    }

    /** Sunrise or sunset at a chosen place, with an optional offset in minutes. */
    data class Sun(val sunrise: Boolean, val offsetMin: Int,
                   val lat: Double, val lng: Double, val place: String) : Trigger() {
        override fun icon() = if (sunrise) "sunny" else "bedtime"
        override fun describe(): String {
            val event = if (sunrise) "sunrise" else "sunset"
            val off = when {
                offsetMin == 0 -> ""
                offsetMin > 0 -> " +${offsetMin}m"
                else -> " ${offsetMin}m"
            }
            return "At $event$off ($place)"
        }
        override fun toJson() = JSONObject().put("t", "sun").put("rise", sunrise)
            .put("off", offsetMin).put("lat", lat).put("lng", lng).put("place", place)
    }

    companion object {
        fun fromJson(o: JSONObject): Trigger = when (o.getString("t")) {
            "time" -> TimeOfDay(o.getInt("h"), o.getInt("m"), o.getJSONArray("days").toIntSet())
            "battery" -> Battery(o.getBoolean("below"), o.getInt("level"))
            "power" -> Power(o.getBoolean("on"))
            "headset" -> Headset(o.getBoolean("on"))
            "bt" -> Bluetooth(o.getBoolean("on"), o.optNullString("name"))
            "wifi" -> Wifi(o.getBoolean("on"), o.optNullString("ssid"))
            "loc" -> Location(o.getBoolean("enter"), o.getDouble("lat"), o.getDouble("lng"),
                o.getDouble("radius").toFloat(), o.getString("place"))
            "screen" -> Screen(o.getBoolean("on"))
            "air" -> Airplane(o.getBoolean("on"))
            "sun" -> Sun(o.getBoolean("rise"), o.optInt("off", 0),
                o.getDouble("lat"), o.getDouble("lng"), o.optString("place", "here"))
            "notif" -> NotificationFrom(o.optString("pkg"), o.optString("label"), o.optString("contains"))
            "cal" -> CalendarEvent(o.optString("title"), o.optBoolean("start", true))
            "motion" -> Motion(
                runCatching { MotionType.valueOf(o.getString("type")) }.getOrDefault(MotionType.VEHICLE),
                o.optBoolean("in", true))
            "gesture" -> Gesture(
                runCatching { GestureType.valueOf(o.getString("type")) }.getOrDefault(GestureType.FLIP_DOWN))
            "appopen" -> AppOpened(o.optString("pkg"), o.optString("label"))
            "nfc" -> NfcTag(o.optString("id"), o.optString("label"))
            else -> Screen(true)
        }
    }
}

// ============================================================================
//  Conditions  (extra "AND ... is currently true" gates)
// ============================================================================

sealed class Condition {
    abstract fun describe(): String
    abstract fun toJson(): JSONObject

    data class OnDays(val days: Set<Int>) : Condition() {
        override fun describe() = "only on ${daysLabel(days)}"
        override fun toJson() = JSONObject().put("c", "days").put("days", JSONArray(days.toList()))
    }
    data class BetweenHours(val startH: Int, val startM: Int, val endH: Int, val endM: Int) : Condition() {
        override fun describe() = "between ${LocalTime.of(startH, startM)} and ${LocalTime.of(endH, endM)}"
        override fun toJson() = JSONObject().put("c", "window")
            .put("sh", startH).put("sm", startM).put("eh", endH).put("em", endM)
    }
    data class BatteryUnder(val level: Int) : Condition() {
        override fun describe() = "battery under $level%"
        override fun toJson() = JSONObject().put("c", "batt").put("level", level)
    }
    data class WhileCharging(val charging: Boolean) : Condition() {
        override fun describe() = if (charging) "while charging" else "while on battery"
        override fun toJson() = JSONObject().put("c", "charging").put("on", charging)
    }

    companion object {
        fun fromJson(o: JSONObject): Condition = when (o.getString("c")) {
            "days" -> OnDays(o.getJSONArray("days").toIntSet())
            "window" -> BetweenHours(o.getInt("sh"), o.getInt("sm"), o.getInt("eh"), o.getInt("em"))
            "batt" -> BatteryUnder(o.getInt("level"))
            "charging" -> WhileCharging(o.getBoolean("on"))
            else -> OnDays((1..7).toSet())
        }
    }
}

// ============================================================================
//  Actions  (the "THEN")
// ============================================================================

enum class Access { NONE, DND, WRITE_SETTINGS, SECURE_SETTINGS, SHIZUKU, CALL, SMS, NOTIF_ACCESS }

sealed class Action {
    abstract fun describe(): String
    abstract fun icon(): String
    abstract fun access(): Access
    abstract fun toJson(): JSONObject

    data class Ringer(val mode: RingerMode) : Action() {
        override fun icon() = "ringer"
        override fun describe() = "Set ringer to ${mode.label().lowercase()}"
        override fun access() = Access.DND
        override fun toJson() = JSONObject().put("a", "ringer").put("mode", mode.name)
    }
    data class Dnd(val on: Boolean) : Action() {
        override fun icon() = "dnd"
        override fun describe() = "Turn Do Not Disturb ${onOff(on)}"
        override fun access() = Access.DND
        override fun toJson() = JSONObject().put("a", "dnd").put("on", on)
    }
    data class Volume(val stream: StreamType, val percent: Int) : Action() {
        override fun icon() = "volume"
        override fun describe() = "${stream.label()} volume to $percent%"
        override fun access() = if (stream == StreamType.RING || stream == StreamType.NOTIFICATION)
            Access.DND else Access.NONE
        override fun toJson() = JSONObject().put("a", "vol").put("stream", stream.name).put("pct", percent)
    }
    data class Brightness(val percent: Int) : Action() {
        override fun icon() = "brightness"
        override fun describe() = "Brightness to $percent%"
        override fun access() = Access.WRITE_SETTINGS
        override fun toJson() = JSONObject().put("a", "bright").put("pct", percent)
    }
    data class AutoRotate(val on: Boolean) : Action() {
        override fun icon() = "rotate"
        override fun describe() = "Auto-rotate ${onOff(on)}"
        override fun access() = Access.WRITE_SETTINGS
        override fun toJson() = JSONObject().put("a", "rotate").put("on", on)
    }
    data class DarkTheme(val on: Boolean) : Action() {
        override fun icon() = "dark"
        override fun describe() = "Dark theme ${onOff(on)}"
        override fun access() = Access.SECURE_SETTINGS
        override fun toJson() = JSONObject().put("a", "dark").put("on", on)
    }
    data class BatterySaver(val on: Boolean) : Action() {
        override fun icon() = "saver"
        override fun describe() = "Battery Saver ${onOff(on)}"
        override fun access() = Access.SECURE_SETTINGS
        override fun toJson() = JSONObject().put("a", "saver").put("on", on)
    }
    data class WifiToggle(val on: Boolean) : Action() {
        override fun icon() = "wifi"
        override fun describe() = "Turn Wi-Fi ${onOff(on)}"
        override fun access() = Access.SHIZUKU
        override fun toJson() = JSONObject().put("a", "wifi").put("on", on)
    }
    data class BluetoothToggle(val on: Boolean) : Action() {
        override fun icon() = "bluetooth"
        override fun describe() = "Turn Bluetooth ${onOff(on)}"
        override fun access() = Access.SHIZUKU
        override fun toJson() = JSONObject().put("a", "bt").put("on", on)
    }
    data class AirplaneToggle(val on: Boolean) : Action() {
        override fun icon() = "flight"
        override fun describe() = "Turn Airplane mode ${onOff(on)}"
        override fun access() = Access.SHIZUKU
        override fun toJson() = JSONObject().put("a", "air").put("on", on)
    }
    data class LaunchApp(val pkg: String, val label: String) : Action() {
        override fun icon() = "app"
        override fun describe() = "Open $label"
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "app").put("pkg", pkg).put("label", label)
    }
    data class Flashlight(val on: Boolean) : Action() {
        override fun icon() = "flash"
        override fun describe() = "Flashlight ${onOff(on)}"
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "flash").put("on", on)
    }
    data class Notify(val title: String, val text: String) : Action() {
        override fun icon() = "notify"
        override fun describe() = "Notify: $title"
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "notify").put("title", title).put("text", text)
    }
    data class Media(val key: MediaKey) : Action() {
        override fun icon() = "music"
        override fun describe() = key.label()
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "media").put("key", key.name)
    }
    data class OpenUrl(val url: String) : Action() {
        override fun icon() = "app"
        override fun describe() = "Open $url"
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "url").put("url", url)
    }
    data class Wait(val seconds: Int) : Action() {
        override fun icon() = "schedule"
        override fun describe() = "Wait $seconds s"
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "wait").put("s", seconds)
    }
    /** Opens a chat with the message already typed. The send tap stays with you. */
    data class Message(val app: MessageApp, val number: String, val who: String, val text: String) : Action() {
        override fun icon() = "chat"
        override fun describe() = "Open ${app.label()} chat with ${who.ifBlank { number }}"
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "msg").put("app", app.name)
            .put("num", number).put("who", who).put("text", text)
    }
    /** Places the call for real — no dialer, no tap. */
    data class Call(val number: String, val who: String) : Action() {
        override fun icon() = "call"
        override fun describe() = "Call ${who.ifBlank { number }}"
        override fun access() = Access.CALL
        override fun toJson() = JSONObject().put("a", "call").put("num", number).put("who", who)
    }
    /** Sends an SMS silently. */
    data class SendSms(val number: String, val who: String, val text: String) : Action() {
        override fun icon() = "chat"
        override fun describe() = "Text ${who.ifBlank { number }}"
        override fun access() = Access.SMS
        override fun toJson() = JSONObject().put("a", "sms").put("num", number)
            .put("who", who).put("text", text)
    }
    /** Says something out loud. */
    data class Speak(val text: String) : Action() {
        override fun icon() = "speak"
        override fun describe() = "Say “$text”"
        override fun access() = Access.NONE
        override fun toJson() = JSONObject().put("a", "speak").put("text", text)
    }
    /**
     * Replies to the newest repliable notification — the trick that makes hands-free
     * WhatsApp replies possible. Pair it with a NotificationFrom trigger.
     */
    data class ReplyNotification(val text: String, val pkg: String, val label: String) : Action() {
        override fun icon() = "reply"
        override fun describe() =
            "Reply to ${label.ifBlank { "the last message" }}: “$text”"
        override fun access() = Access.NOTIF_ACCESS
        override fun toJson() = JSONObject().put("a", "reply").put("text", text)
            .put("pkg", pkg).put("label", label)
    }

    companion object {
        fun fromJson(o: JSONObject): Action = when (o.getString("a")) {
            "ringer" -> Ringer(RingerMode.valueOf(o.getString("mode")))
            "dnd" -> Dnd(o.getBoolean("on"))
            "vol" -> Volume(StreamType.valueOf(o.getString("stream")), o.getInt("pct"))
            "bright" -> Brightness(o.getInt("pct"))
            "rotate" -> AutoRotate(o.getBoolean("on"))
            "dark" -> DarkTheme(o.getBoolean("on"))
            "saver" -> BatterySaver(o.getBoolean("on"))
            "wifi" -> WifiToggle(o.getBoolean("on"))
            "bt" -> BluetoothToggle(o.getBoolean("on"))
            "air" -> AirplaneToggle(o.getBoolean("on"))
            "app" -> LaunchApp(o.getString("pkg"), o.getString("label"))
            "flash" -> Flashlight(o.getBoolean("on"))
            "notify" -> Notify(o.getString("title"), o.optString("text"))
            "media" -> Media(runCatching { MediaKey.valueOf(o.getString("key")) }
                .getOrDefault(MediaKey.PLAY_PAUSE))
            "url" -> OpenUrl(o.getString("url"))
            "wait" -> Wait(o.optInt("s", 3).coerceIn(1, 30))
            "msg" -> Message(
                runCatching { MessageApp.valueOf(o.getString("app")) }.getOrDefault(MessageApp.WHATSAPP),
                o.optString("num"), o.optString("who"), o.optString("text"))
            "call" -> Call(o.optString("num"), o.optString("who"))
            "sms" -> SendSms(o.optString("num"), o.optString("who"), o.optString("text"))
            "speak" -> Speak(o.optString("text"))
            "reply" -> ReplyNotification(o.optString("text"), o.optString("pkg"), o.optString("label"))
            else -> Notify("Routine", "")
        }
    }
}

enum class MediaKey { PLAY_PAUSE, NEXT, PREVIOUS;
    fun label() = when (this) {
        PLAY_PAUSE -> "Play / pause media"; NEXT -> "Next track"; PREVIOUS -> "Previous track"
    }
}

enum class MessageApp { WHATSAPP, SMS;
    fun label() = when (this) { WHATSAPP -> "WhatsApp"; SMS -> "Messages" }
}

enum class MotionType { VEHICLE, WALKING, RUNNING, BICYCLE, STILL;
    fun verb() = when (this) {
        VEHICLE -> "driving"; WALKING -> "walking"; RUNNING -> "running"
        BICYCLE -> "cycling"; STILL -> "sitting still"
    }
    fun label() = verb().replaceFirstChar { it.uppercase() }
    fun icon() = when (this) {
        VEHICLE -> "car"; WALKING -> "walk"; RUNNING -> "fitness"
        BICYCLE -> "bike"; STILL -> "meditation"
    }
}

enum class GestureType { FLIP_DOWN, FLIP_UP, SHAKE;
    fun label() = when (this) {
        FLIP_DOWN -> "Phone placed face-down"
        FLIP_UP -> "Phone turned face-up"
        SHAKE -> "Phone shaken"
    }
}

// ============================================================================
//  Routine
// ============================================================================

data class Routine(
    val id: Long,
    val name: String,
    val icon: String = "star",
    val enabled: Boolean = true,
    val notifyOnRun: Boolean = true,
    val match: Match = Match.ANY,
    val triggers: List<Trigger> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    // --- Ending (Samsung-style) ---
    val endTriggers: List<Trigger> = emptyList(),
    val endMode: EndMode = EndMode.REVERT,
    val endActions: List<Action> = emptyList(),
) {
    val isValid get() = triggers.isNotEmpty() && actions.isNotEmpty()
    val hasEnd get() = endTriggers.isNotEmpty()

    fun ifSummary(): String =
        if (triggers.isEmpty()) "No trigger yet"
        else triggers.joinToString(if (match == Match.ALL) "  and  " else "  or  ") { it.describe() }

    fun thenSummary(): String =
        if (actions.isEmpty()) "No actions yet" else actions.joinToString(", ") { it.describe() }

    fun endSummary(): String? {
        if (endTriggers.isEmpty()) return null
        val when_ = endTriggers.joinToString(" or ") { it.describe() }
        return "$when_ → ${endMode.describe()}"
    }

    /** Soonest upcoming run among this routine's schedulable triggers, if any. */
    fun nextRun(from: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): java.time.ZonedDateTime? =
        triggers.mapNotNull { t ->
            when (t) {
                is Trigger.TimeOfDay -> nextTimeTrigger(t, from)
                is Trigger.Sun -> SunCalc.next(t, from)
                else -> null
            }
        }.minOrNull()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("icon", icon); put("enabled", enabled)
        put("notify", notifyOnRun)
        put("match", match.name)
        put("triggers", JSONArray().apply { triggers.forEach { put(it.toJson()) } })
        put("conditions", JSONArray().apply { conditions.forEach { put(it.toJson()) } })
        put("actions", JSONArray().apply { actions.forEach { put(it.toJson()) } })
        put("endTriggers", JSONArray().apply { endTriggers.forEach { put(it.toJson()) } })
        put("endMode", endMode.name)
        put("endActions", JSONArray().apply { endActions.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject) = Routine(
            id = o.getLong("id"),
            name = o.optString("name", "Routine"),
            icon = o.optString("icon", "star"),
            enabled = o.optBoolean("enabled", true),
            notifyOnRun = o.optBoolean("notify", true),
            match = runCatching { Match.valueOf(o.optString("match")) }.getOrDefault(Match.ANY),
            triggers = o.optJSONArray("triggers").items { Trigger.fromJson(it) },
            conditions = o.optJSONArray("conditions").items { Condition.fromJson(it) },
            actions = o.optJSONArray("actions").items { Action.fromJson(it) },
            endTriggers = o.optJSONArray("endTriggers").items { Trigger.fromJson(it) },
            endMode = runCatching { EndMode.valueOf(o.optString("endMode")) }.getOrDefault(EndMode.REVERT),
            endActions = o.optJSONArray("endActions").items { Action.fromJson(it) },
        )

        fun new() = Routine(id = System.currentTimeMillis(), name = "", icon = "star")
    }
}

// ============================================================================
//  Helpers
// ============================================================================

fun daysLabel(days: Set<Int>): String = when {
    days.isEmpty() -> "no days"
    days.size == 7 -> "every day"
    days == setOf(1, 2, 3, 4, 5) -> "weekdays"
    days == setOf(6, 7) -> "weekends"
    else -> days.sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    }
}

private fun onOff(on: Boolean) = if (on) "on" else "off"

private fun JSONArray.toIntSet(): Set<Int> = buildSet { for (i in 0 until length()) add(getInt(i)) }

private inline fun <T> JSONArray?.items(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { f(getJSONObject(it)) }
}
private fun JSONObject.optNullString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).ifBlank { null }

fun nextTimeTrigger(t: Trigger.TimeOfDay, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
    if (t.days.isEmpty()) return null
    for (d in 0..7L) {
        val date = from.toLocalDate().plusDays(d)
        val cand = date.atTime(t.hour, t.minute).atZone(from.zone)
        if (cand.isAfter(from) && t.days.contains(date.dayOfWeek.value)) return cand
    }
    return null
}
