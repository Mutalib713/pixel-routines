package com.mosman.routines

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Real Material Symbols, keyed by stable strings so the model stays free of UI types.
 * Replaces the old emoji icons everywhere (cards, pickers, editor).
 */
object Ic {

    /** Icons a user can pick as a routine's identity. */
    val routinePicker: List<String> = listOf(
        "bedtime", "sunny", "school", "work", "home", "car", "headphones", "battery",
        "fitness", "movie", "games", "coffee", "meditation", "flight", "wifi", "moon",
        "alarm", "bolt", "star", "music", "silent", "location",
    )

    private val map: Map<String, ImageVector> = mapOf(
        // routine identities
        "bedtime" to Icons.Filled.Bedtime,
        "sunny" to Icons.Filled.WbSunny,
        "school" to Icons.Filled.School,
        "work" to Icons.Filled.Work,
        "home" to Icons.Filled.Home,
        "car" to Icons.Filled.DirectionsCar,
        "headphones" to Icons.Filled.Headphones,
        "battery" to Icons.Filled.BatteryAlert,
        "fitness" to Icons.Filled.FitnessCenter,
        "movie" to Icons.Filled.Movie,
        "games" to Icons.Filled.SportsEsports,
        "coffee" to Icons.Filled.LocalCafe,
        "meditation" to Icons.Filled.SelfImprovement,
        "flight" to Icons.Filled.Flight,
        "moon" to Icons.Filled.DarkMode,
        "alarm" to Icons.Filled.Alarm,
        "bolt" to Icons.Filled.Bolt,
        "star" to Icons.Filled.AutoAwesome,
        "music" to Icons.Filled.MusicNote,
        "silent" to Icons.Filled.NotificationsOff,
        // triggers
        "schedule" to Icons.Filled.Schedule,
        "power" to Icons.Filled.PowerSettingsNew,
        "bluetooth" to Icons.Filled.Bluetooth,
        "wifi" to Icons.Filled.Wifi,
        "location" to Icons.Filled.LocationOn,
        "screen" to Icons.Filled.PhoneAndroid,
        // actions
        "ringer" to Icons.Filled.NotificationsActive,
        "dnd" to Icons.Filled.DoNotDisturbOn,
        "volume" to Icons.Filled.VolumeUp,
        "brightness" to Icons.Filled.BrightnessMedium,
        "rotate" to Icons.Filled.ScreenRotation,
        "dark" to Icons.Filled.DarkMode,
        "saver" to Icons.Filled.BatterySaver,
        "app" to Icons.Filled.Apps,
        "flash" to Icons.Filled.FlashlightOn,
        "notify" to Icons.Filled.NotificationsNone,
        "check" to Icons.Filled.CheckCircle,
    )

    fun of(key: String): ImageVector = map[key] ?: Icons.Filled.AutoAwesome
}
