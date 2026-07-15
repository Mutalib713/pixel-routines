package com.mosman.routines

/** Ready-made routines shown in the "Discover" gallery — one tap to add and tweak. */
object Presets {

    data class Preset(val subtitle: String, val build: () -> Routine)

    val all: List<Preset> = listOf(
        Preset("Silence + dark theme every night at 10 PM") {
            base("Bedtime", "🌙",
                triggers = listOf(Trigger.TimeOfDay(22, 0, (1..7).toSet())),
                actions = listOf(Action.Ringer(RingerMode.SILENT), Action.Dnd(true),
                    Action.DarkTheme(true), Action.Brightness(20)))
        },
        Preset("Sound back on at 6:30 AM on weekdays") {
            base("Wake up", "🌅",
                triggers = listOf(Trigger.TimeOfDay(6, 30, setOf(1, 2, 3, 4, 5))),
                actions = listOf(Action.Ringer(RingerMode.SOUND), Action.Dnd(false),
                    Action.DarkTheme(false), Action.Brightness(80)))
        },
        Preset("Vibrate only during lectures, Mon–Fri 8 AM") {
            base("Lectures", "📚",
                triggers = listOf(Trigger.TimeOfDay(8, 0, setOf(1, 2, 3, 4, 5))),
                actions = listOf(Action.Ringer(RingerMode.VIBRATE)))
        },
        Preset("Battery Saver kicks in below 20%") {
            base("Low battery", "🪫",
                triggers = listOf(Trigger.Battery(below = true, level = 20)),
                actions = listOf(Action.BatterySaver(true)))
        },
        Preset("Do Not Disturb when your car connects") {
            base("Driving", "🚗",
                triggers = listOf(Trigger.Bluetooth(connected = true, deviceName = null)),
                actions = listOf(Action.Dnd(true), Action.Volume(StreamType.MEDIA, 70)))
        },
        Preset("Full ring volume on your home Wi-Fi") {
            base("Home", "🏠",
                triggers = listOf(Trigger.Wifi(connected = true, ssid = null)),
                actions = listOf(Action.Volume(StreamType.RING, 100), Action.Ringer(RingerMode.SOUND)))
        },
        Preset("Headphones in → louder media") {
            base("Music time", "🎧",
                triggers = listOf(Trigger.Headset(connected = true)),
                actions = listOf(Action.Volume(StreamType.MEDIA, 80)))
        },
        Preset("Arrive on campus → vibrate + Wi-Fi") {
            base("At KNUST", "🎓",
                triggers = listOf(Trigger.Location(true, 6.6745, -1.5716, 300f, "KNUST")),
                actions = listOf(Action.Ringer(RingerMode.VIBRATE)))
        },
    )

    private fun base(name: String, emoji: String, triggers: List<Trigger>, actions: List<Action>) =
        Routine(id = System.currentTimeMillis(), name = name, emoji = emoji,
            triggers = triggers, actions = actions)
}
