package com.mosman.routines

/** Ready-made routines shown in the "Ideas" gallery — one tap to add, then tweak. */
object Presets {

    data class Preset(val subtitle: String, val build: () -> Routine)

    val all: List<Preset> = listOf(
        Preset("Silence and dim at 10 PM, back to normal at 6:30 AM") {
            base("Bedtime", "bedtime",
                triggers = listOf(Trigger.TimeOfDay(22, 0, (1..7).toSet())),
                actions = listOf(Action.Ringer(RingerMode.SILENT), Action.Dnd(true),
                    Action.DarkTheme(true), Action.Brightness(20)),
                endTriggers = listOf(Trigger.TimeOfDay(6, 30, (1..7).toSet())))
        },
        Preset("Sound and brightness back up on weekday mornings") {
            base("Wake up", "sunny",
                triggers = listOf(Trigger.TimeOfDay(6, 30, setOf(1, 2, 3, 4, 5))),
                actions = listOf(Action.Ringer(RingerMode.SOUND), Action.Dnd(false),
                    Action.DarkTheme(false), Action.Brightness(80)),
                endMode = EndMode.NOTHING)
        },
        Preset("Vibrate through lectures, Mon–Fri 8 AM to 5 PM") {
            base("Lectures", "school",
                triggers = listOf(Trigger.TimeOfDay(8, 0, setOf(1, 2, 3, 4, 5))),
                actions = listOf(Action.Ringer(RingerMode.VIBRATE)),
                endTriggers = listOf(Trigger.TimeOfDay(17, 0, setOf(1, 2, 3, 4, 5))))
        },
        Preset("Battery Saver under 20%, off again above 40%") {
            base("Low battery", "battery",
                triggers = listOf(Trigger.Battery(below = true, level = 20)),
                actions = listOf(Action.BatterySaver(true)),
                endTriggers = listOf(Trigger.Battery(below = false, level = 40)))
        },
        Preset("Do Not Disturb while your car is connected") {
            base("Driving", "car",
                triggers = listOf(Trigger.Bluetooth(connected = true, deviceName = null)),
                actions = listOf(Action.Dnd(true), Action.Volume(StreamType.MEDIA, 70)),
                endTriggers = listOf(Trigger.Bluetooth(connected = false, deviceName = null)))
        },
        Preset("Loud ring at home, back to normal when you leave") {
            base("Home", "home",
                triggers = listOf(Trigger.Wifi(connected = true, ssid = null)),
                actions = listOf(Action.Volume(StreamType.RING, 100), Action.Ringer(RingerMode.SOUND)),
                endTriggers = listOf(Trigger.Wifi(connected = false, ssid = null)))
        },
        Preset("Headphones in → louder media, back down when out") {
            base("Music time", "headphones",
                triggers = listOf(Trigger.Headset(connected = true)),
                actions = listOf(Action.Volume(StreamType.MEDIA, 80)),
                endTriggers = listOf(Trigger.Headset(connected = false)))
        },
        Preset("Vibrate on campus, sound again when you leave") {
            base("At campus", "location",
                triggers = listOf(Trigger.Location(true, 6.6745, -1.5716, 300f, "KNUST")),
                actions = listOf(Action.Ringer(RingerMode.VIBRATE)),
                endTriggers = listOf(Trigger.Location(false, 6.6745, -1.5716, 300f, "KNUST")))
        },
    )

    private fun base(
        name: String, icon: String,
        triggers: List<Trigger>, actions: List<Action>,
        endTriggers: List<Trigger> = emptyList(),
        endMode: EndMode = EndMode.REVERT,
    ) = Routine(id = System.currentTimeMillis(), name = name, icon = icon,
        triggers = triggers, actions = actions, endTriggers = endTriggers, endMode = endMode)
}
