@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mosman.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.DayOfWeek
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun BackIcon() = Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")

// Kinds drive the picker menus.
private enum class TriggerKind(val emoji: String, val label: String, val sub: String) {
    TIME("⏰", "Time of day", "At a set time on chosen days"),
    BATTERY("🔋", "Battery level", "Drops below / rises above a %"),
    POWER("🔌", "Charging", "Charger connected or unplugged"),
    HEADSET("🎧", "Headphones", "Wired headset plugged/unplugged"),
    BLUETOOTH("🔵", "Bluetooth device", "A device connects/disconnects"),
    WIFI("📶", "Wi-Fi network", "Join or leave a network"),
    LOCATION("📍", "Location", "Arrive at or leave a place"),
    SCREEN("📱", "Screen", "Screen turns on or off"),
    AIRPLANE("✈️", "Airplane mode", "Airplane mode on or off"),
}

private enum class ActionKind(val emoji: String, val label: String, val access: Access) {
    RINGER("🔕", "Ringer mode", Access.DND),
    DND("🌙", "Do Not Disturb", Access.DND),
    VOLUME("🔊", "Set a volume", Access.NONE),
    BRIGHTNESS("💡", "Brightness", Access.WRITE_SETTINGS),
    ROTATE("🔄", "Auto-rotate", Access.WRITE_SETTINGS),
    DARK("🌑", "Dark theme", Access.SECURE_SETTINGS),
    SAVER("🪫", "Battery Saver", Access.SECURE_SETTINGS),
    WIFI("📶", "Wi-Fi on/off", Access.SHIZUKU),
    BLUETOOTH("🔵", "Bluetooth on/off", Access.SHIZUKU),
    AIRPLANE("✈️", "Airplane on/off", Access.SHIZUKU),
    APP("📲", "Open an app", Access.NONE),
    FLASH("🔦", "Flashlight", Access.NONE),
    NOTIFY("🔔", "Show a reminder", Access.NONE),
}

private enum class ConditionKind(val label: String) {
    DAYS("Only on certain days"),
    WINDOW("Only between times"),
    BATTERY("Only under battery %"),
    CHARGING("Only while charging"),
}

@Composable
fun EditorScreen(
    initial: Routine,
    onSave: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var match by remember { mutableStateOf(initial.match) }
    val triggers = remember { mutableStateListOf<Trigger>().apply { addAll(initial.triggers) } }
    val conditions = remember { mutableStateListOf<Condition>().apply { addAll(initial.conditions) } }
    val actions = remember { mutableStateListOf<Action>().apply { addAll(initial.actions) } }
    val ctx = LocalContext.current
    val existing = remember { Store.get(ctx, initial.id) != null }

    var showTriggerSheet by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showConditionSheet by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var editTrigger by remember { mutableStateOf<Pair<TriggerKind, Int?>?>(null) }
    var editAction by remember { mutableStateOf<Pair<ActionKind, Int?>?>(null) }
    var editCondition by remember { mutableStateOf<Pair<ConditionKind, Int?>?>(null) }

    fun result() = initial.copy(
        name = name.ifBlank { "Routine" }, emoji = emoji, match = match,
        triggers = triggers.toList(), conditions = conditions.toList(), actions = actions.toList(),
    )
    val valid = triggers.isNotEmpty() && actions.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing) "Edit routine" else "New routine") },
                navigationIcon = { IconButton(onClick = onBack) { BackIcon() } },
                actions = {
                    if (existing) IconButton(onClick = { onDelete(initial) }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(onClick = { onSave(result()) }, enabled = valid,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        Text("Save routine", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Name + emoji
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { showEmoji = true },
                    contentAlignment = Alignment.Center) { Text(emoji, fontSize = 26.sp) }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name") }, placeholder = { Text("e.g. Bedtime") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }

            // IF
            SectionHeader("IF", "this happens", MaterialTheme.colorScheme.primary)
            if (triggers.size > 1) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Match.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = match == m, onClick = { match = m },
                            shape = SegmentedButtonDefaults.itemShape(i, Match.entries.size)) {
                            Text(m.label(), fontSize = 13.sp)
                        }
                    }
                }
            }
            triggers.forEachIndexed { i, t ->
                ItemRow(t.icon(), t.describe(),
                    onClick = { editTrigger = kindOf(t) to i },
                    onRemove = { triggers.removeAt(i) })
            }
            AddButton("Add trigger") { showTriggerSheet = true }

            // WHEN (optional conditions)
            if (conditions.isNotEmpty() || triggers.isNotEmpty()) {
                SectionHeader("ONLY IF", "these also hold (optional)", MaterialTheme.colorScheme.secondary)
                conditions.forEachIndexed { i, c ->
                    ItemRow("✔️", c.describe().replaceFirstChar { it.uppercase() },
                        onClick = { editCondition = kindOfCond(c) to i },
                        onRemove = { conditions.removeAt(i) })
                }
                AddButton("Add condition") { showConditionSheet = true }
            }

            // THEN
            SectionHeader("THEN", "do this", MaterialTheme.colorScheme.tertiary)
            actions.forEachIndexed { i, a ->
                ItemRow(a.icon(), a.describe(),
                    onClick = { editAction = kindOf(a) to i },
                    onRemove = { actions.removeAt(i) },
                    badge = accessBadge(a.access()))
            }
            AddButton("Add action") { showActionSheet = true }

            if (!valid) Text("Add at least one trigger and one action to save.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- Picker sheets ----
    if (showTriggerSheet) PickSheet("Choose a trigger", TriggerKind.entries,
        { it.emoji }, { it.label }, { it.sub }, onDismiss = { showTriggerSheet = false }) {
        showTriggerSheet = false; editTrigger = it to null
    }
    if (showActionSheet) PickSheet("Choose an action", ActionKind.entries,
        { it.emoji }, { it.label }, { accessNote(it.access) }, onDismiss = { showActionSheet = false }) {
        showActionSheet = false; editAction = it to null
    }
    if (showConditionSheet) PickSheet("Add a condition", ConditionKind.entries,
        { "✔️" }, { it.label }, { "" }, onDismiss = { showConditionSheet = false }) {
        showConditionSheet = false; editCondition = it to null
    }

    // ---- Config dialogs ----
    editTrigger?.let { (kind, idx) ->
        TriggerConfig(kind, idx?.let { triggers[it] },
            onDismiss = { editTrigger = null }) { built ->
            if (idx == null) triggers.add(built) else triggers[idx] = built
            editTrigger = null
        }
    }
    editAction?.let { (kind, idx) ->
        ActionConfig(kind, idx?.let { actions[it] },
            onDismiss = { editAction = null }) { built ->
            if (idx == null) actions.add(built) else actions[idx] = built
            editAction = null
        }
    }
    editCondition?.let { (kind, idx) ->
        ConditionConfig(kind, idx?.let { conditions[it] },
            onDismiss = { editCondition = null }) { built ->
            if (idx == null) conditions.add(built) else conditions[idx] = built
            editCondition = null
        }
    }
    if (showEmoji) EmojiDialog(onPick = { emoji = it; showEmoji = false }, onDismiss = { showEmoji = false })
}

// ============================================================================
//  Reusable pieces
// ============================================================================

@Composable
private fun SectionHeader(tag: String, sub: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(tag, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ItemRow(emoji: String, text: String, onClick: () -> Unit, onRemove: () -> Unit, badge: String? = null) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text, fontSize = 15.sp)
                if (badge != null) Text(badge, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text)
    }
}

@Composable
private fun <T> PickSheet(
    title: String, options: List<T>,
    emoji: (T) -> String, label: (T) -> String, sub: (T) -> String,
    onDismiss: () -> Unit, onPick: (T) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(options) { opt ->
                Row(Modifier.fillMaxWidth().clickable { onPick(opt) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji(opt), fontSize = 22.sp); Spacer(Modifier.width(16.dp))
                    Column {
                        Text(label(opt), fontSize = 16.sp)
                        if (sub(opt).isNotBlank()) Text(sub(opt), fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ConfigDialog(title: String, canSave: Boolean = true, onDismiss: () -> Unit,
                         onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onSave, enabled = canSave) { Text("Done") }
                }
            }
        }
    }
}

// ---- On/Off + days + slider helpers ----

@Composable
private fun OnOff(label: String, on: Boolean, onChange: (Boolean) -> Unit,
                  onText: String = "On", offText: String = "Off") {
    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = on, onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(onText) }
            SegmentedButton(selected = !on, onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(offText) }
        }
    }
}

@Composable
private fun DaysRow(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in 1..7) {
            val sel = days.contains(d)
            FilterChip(selected = sel, onClick = { onChange(if (sel) days - d else days + d) },
                label = { Text(DayOfWeek.of(d).getDisplayName(JTextStyle.SHORT, Locale.getDefault())) })
        }
    }
    Row {
        TextButton(onClick = { onChange((1..7).toSet()) }) { Text("Every day") }
        TextButton(onClick = { onChange((1..5).toSet()) }) { Text("Weekdays") }
        TextButton(onClick = { onChange(setOf(6, 7)) }) { Text("Weekend") }
    }
}

@Composable
private fun PercentSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp); Text("$value%", fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..100f)
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit,
                     number: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth())
}

// ============================================================================
//  Trigger config
// ============================================================================

@Composable
private fun TriggerConfig(kind: TriggerKind, existing: Trigger?, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
    when (kind) {
        TriggerKind.TIME -> {
            val t = existing as? Trigger.TimeOfDay
            val time = rememberTimePickerState(t?.hour ?: 22, t?.minute ?: 0, false)
            var days by remember { mutableStateOf(t?.days ?: (1..7).toSet()) }
            ConfigDialog("Time of day", canSave = days.isNotEmpty(), onDismiss = onDismiss,
                onSave = { onSave(Trigger.TimeOfDay(time.hour, time.minute, days)) }) {
                TimeInput(state = time)
                DaysRow(days) { days = it }
            }
        }
        TriggerKind.BATTERY -> {
            val t = existing as? Trigger.Battery
            var below by remember { mutableStateOf(t?.below ?: true) }
            var level by remember { mutableIntStateOf(t?.level ?: 20) }
            ConfigDialog("Battery level", onDismiss = onDismiss,
                onSave = { onSave(Trigger.Battery(below, level)) }) {
                OnOff("When battery", below, { below = it }, "Drops below", "Rises above")
                PercentSlider("Level", level) { level = it }
            }
        }
        TriggerKind.POWER -> OnOffTrigger("Charging", existing, { (it as? Trigger.Power)?.connected ?: true },
            { Trigger.Power(it) }, onDismiss, onSave, "Connected", "Disconnected")
        TriggerKind.HEADSET -> OnOffTrigger("Headphones", existing, { (it as? Trigger.Headset)?.connected ?: true },
            { Trigger.Headset(it) }, onDismiss, onSave, "Plugged in", "Unplugged")
        TriggerKind.SCREEN -> OnOffTrigger("Screen", existing, { (it as? Trigger.Screen)?.on ?: true },
            { Trigger.Screen(it) }, onDismiss, onSave, "Turns on", "Turns off")
        TriggerKind.AIRPLANE -> OnOffTrigger("Airplane mode", existing, { (it as? Trigger.Airplane)?.on ?: true },
            { Trigger.Airplane(it) }, onDismiss, onSave, "Turns on", "Turns off")
        TriggerKind.BLUETOOTH -> {
            val t = existing as? Trigger.Bluetooth
            var on by remember { mutableStateOf(t?.connected ?: true) }
            var dev by remember { mutableStateOf(t?.deviceName ?: "") }
            ConfigDialog("Bluetooth device", onDismiss = onDismiss,
                onSave = { onSave(Trigger.Bluetooth(on, dev.ifBlank { null })) }) {
                OnOff("When a device", on, { on = it }, "Connects", "Disconnects")
                NumField("Device name (blank = any)", dev) { dev = it }
            }
        }
        TriggerKind.WIFI -> {
            val t = existing as? Trigger.Wifi
            var on by remember { mutableStateOf(t?.connected ?: true) }
            var ssid by remember { mutableStateOf(t?.ssid ?: "") }
            ConfigDialog("Wi-Fi network", onDismiss = onDismiss,
                onSave = { onSave(Trigger.Wifi(on, ssid.ifBlank { null })) }) {
                OnOff("When Wi-Fi", on, { on = it }, "Connects", "Disconnects")
                NumField("Network name / SSID (blank = any)", ssid) { ssid = it }
            }
        }
        TriggerKind.LOCATION -> {
            val t = existing as? Trigger.Location
            var enter by remember { mutableStateOf(t?.enter ?: true) }
            var place by remember { mutableStateOf(t?.place ?: "") }
            var lat by remember { mutableStateOf(t?.lat?.toString() ?: "") }
            var lng by remember { mutableStateOf(t?.lng?.toString() ?: "") }
            var radius by remember { mutableStateOf((t?.radius ?: 200f).toString()) }
            val ok = place.isNotBlank() && lat.toDoubleOrNull() != null && lng.toDoubleOrNull() != null
            ConfigDialog("Location", canSave = ok, onDismiss = onDismiss, onSave = {
                onSave(Trigger.Location(enter, lat.toDouble(), lng.toDouble(),
                    radius.toFloatOrNull() ?: 200f, place))
            }) {
                OnOff("When you", enter, { enter = it }, "Arrive", "Leave")
                NumField("Place name", place) { place = it }
                NumField("Latitude", lat, { lat = it }, number = true)
                NumField("Longitude", lng, { lng = it }, number = true)
                NumField("Radius (metres)", radius, { radius = it }, number = true)
                Text("Tip: long-press a spot in Google Maps to copy its latitude, longitude.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnOffTrigger(title: String, existing: Trigger?, get: (Trigger?) -> Boolean,
                         make: (Boolean) -> Trigger, onDismiss: () -> Unit, onSave: (Trigger) -> Unit,
                         onText: String, offText: String) {
    var on by remember { mutableStateOf(get(existing)) }
    ConfigDialog(title, onDismiss = onDismiss, onSave = { onSave(make(on)) }) {
        OnOff("Trigger when it", on, { on = it }, onText, offText)
    }
}

// ============================================================================
//  Action config
// ============================================================================

@Composable
private fun ActionConfig(kind: ActionKind, existing: Action?, onDismiss: () -> Unit, onSave: (Action) -> Unit) {
    when (kind) {
        ActionKind.RINGER -> {
            var mode by remember { mutableStateOf((existing as? Action.Ringer)?.mode ?: RingerMode.SILENT) }
            ConfigDialog("Ringer mode", onDismiss = onDismiss, onSave = { onSave(Action.Ringer(mode)) }) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RingerMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = mode == m, onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, RingerMode.entries.size)) {
                            Text(m.label(), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        ActionKind.DND -> OnOffAction("Do Not Disturb", existing, { (it as? Action.Dnd)?.on ?: true },
            { Action.Dnd(it) }, onDismiss, onSave)
        ActionKind.VOLUME -> {
            val a = existing as? Action.Volume
            var stream by remember { mutableStateOf(a?.stream ?: StreamType.MEDIA) }
            var pct by remember { mutableIntStateOf(a?.percent ?: 60) }
            ConfigDialog("Set a volume", onDismiss = onDismiss,
                onSave = { onSave(Action.Volume(stream, pct)) }) {
                Text("Which volume", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StreamType.entries.forEach { s ->
                        FilterChip(selected = stream == s, onClick = { stream = s },
                            label = { Text(s.label()) })
                    }
                }
                PercentSlider("Level", pct) { pct = it }
            }
        }
        ActionKind.BRIGHTNESS -> {
            var pct by remember { mutableIntStateOf((existing as? Action.Brightness)?.percent ?: 50) }
            ConfigDialog("Brightness", onDismiss = onDismiss,
                onSave = { onSave(Action.Brightness(pct)) }) { PercentSlider("Level", pct) { pct = it } }
        }
        ActionKind.ROTATE -> OnOffAction("Auto-rotate", existing, { (it as? Action.AutoRotate)?.on ?: true },
            { Action.AutoRotate(it) }, onDismiss, onSave)
        ActionKind.DARK -> OnOffAction("Dark theme", existing, { (it as? Action.DarkTheme)?.on ?: true },
            { Action.DarkTheme(it) }, onDismiss, onSave, note = "Needs the one-time ADB grant (see Settings).")
        ActionKind.SAVER -> OnOffAction("Battery Saver", existing, { (it as? Action.BatterySaver)?.on ?: true },
            { Action.BatterySaver(it) }, onDismiss, onSave, note = "Needs the one-time ADB grant.")
        ActionKind.WIFI -> OnOffAction("Wi-Fi", existing, { (it as? Action.WifiToggle)?.on ?: true },
            { Action.WifiToggle(it) }, onDismiss, onSave, note = "Needs Shizuku (coming soon).")
        ActionKind.BLUETOOTH -> OnOffAction("Bluetooth", existing, { (it as? Action.BluetoothToggle)?.on ?: true },
            { Action.BluetoothToggle(it) }, onDismiss, onSave, note = "Needs Shizuku (coming soon).")
        ActionKind.AIRPLANE -> OnOffAction("Airplane mode", existing, { (it as? Action.AirplaneToggle)?.on ?: true },
            { Action.AirplaneToggle(it) }, onDismiss, onSave, note = "Needs Shizuku (coming soon).")
        ActionKind.FLASH -> OnOffAction("Flashlight", existing, { (it as? Action.Flashlight)?.on ?: true },
            { Action.Flashlight(it) }, onDismiss, onSave)
        ActionKind.APP -> {
            val ctx = LocalContext.current
            val apps = remember { Apps.installed(ctx) }
            var query by remember { mutableStateOf("") }
            ConfigDialog("Open an app", canSave = false, onDismiss = onDismiss, onSave = {}) {
                NumField("Search", query) { query = it }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        Row(Modifier.fillMaxWidth().clickable {
                            onSave(Action.LaunchApp(app.pkg, app.label))
                        }.padding(vertical = 12.dp)) { Text(app.label, fontSize = 15.sp) }
                    }
                }
            }
        }
        ActionKind.NOTIFY -> {
            val a = existing as? Action.Notify
            var title by remember { mutableStateOf(a?.title ?: "") }
            var text by remember { mutableStateOf(a?.text ?: "") }
            ConfigDialog("Show a reminder", canSave = title.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Action.Notify(title, text)) }) {
                NumField("Title", title) { title = it }
                NumField("Message", text) { text = it }
            }
        }
    }
}

@Composable
private fun OnOffAction(title: String, existing: Action?, get: (Action?) -> Boolean,
                        make: (Boolean) -> Action, onDismiss: () -> Unit, onSave: (Action) -> Unit,
                        note: String? = null) {
    var on by remember { mutableStateOf(get(existing)) }
    ConfigDialog(title, onDismiss = onDismiss, onSave = { onSave(make(on)) }) {
        OnOff("Turn it", on, { on = it })
        if (note != null) Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
    }
}

// ============================================================================
//  Condition config
// ============================================================================

@Composable
private fun ConditionConfig(kind: ConditionKind, existing: Condition?, onDismiss: () -> Unit, onSave: (Condition) -> Unit) {
    when (kind) {
        ConditionKind.DAYS -> {
            var days by remember { mutableStateOf((existing as? Condition.OnDays)?.days ?: (1..7).toSet()) }
            ConfigDialog("Only on days", canSave = days.isNotEmpty(), onDismiss = onDismiss,
                onSave = { onSave(Condition.OnDays(days)) }) { DaysRow(days) { days = it } }
        }
        ConditionKind.WINDOW -> {
            val c = existing as? Condition.BetweenHours
            val start = rememberTimePickerState(c?.startH ?: 22, c?.startM ?: 0, false)
            val end = rememberTimePickerState(c?.endH ?: 6, c?.endM ?: 0, false)
            ConfigDialog("Between times", onDismiss = onDismiss, onSave = {
                onSave(Condition.BetweenHours(start.hour, start.minute, end.hour, end.minute))
            }) {
                Text("From", fontSize = 13.sp); TimeInput(state = start)
                Text("Until", fontSize = 13.sp); TimeInput(state = end)
            }
        }
        ConditionKind.BATTERY -> {
            var level by remember { mutableIntStateOf((existing as? Condition.BatteryUnder)?.level ?: 30) }
            ConfigDialog("Battery under", onDismiss = onDismiss,
                onSave = { onSave(Condition.BatteryUnder(level)) }) { PercentSlider("Level", level) { level = it } }
        }
        ConditionKind.CHARGING -> {
            var charging by remember { mutableStateOf((existing as? Condition.WhileCharging)?.charging ?: true) }
            ConfigDialog("Charging state", onDismiss = onDismiss,
                onSave = { onSave(Condition.WhileCharging(charging)) }) {
                OnOff("Only while", charging, { charging = it }, "Charging", "On battery")
            }
        }
    }
}

// ---- Emoji picker ----

@Composable
private fun EmojiDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val emojis = listOf("✨","🌙","🌅","📚","🔋","🚗","🏠","🎧","🎓","💤","🏢","🏋️","🍿","🎮",
        "📵","🔔","☀️","🌑","📶","✈️","🛏️","☕","🎬","🧘")
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Choose an icon", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojis.forEach { e ->
                        Box(Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPick(e) }, contentAlignment = Alignment.Center) {
                            Text(e, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---- helpers ----

private fun kindOf(t: Trigger): TriggerKind = when (t) {
    is Trigger.TimeOfDay -> TriggerKind.TIME
    is Trigger.Battery -> TriggerKind.BATTERY
    is Trigger.Power -> TriggerKind.POWER
    is Trigger.Headset -> TriggerKind.HEADSET
    is Trigger.Bluetooth -> TriggerKind.BLUETOOTH
    is Trigger.Wifi -> TriggerKind.WIFI
    is Trigger.Location -> TriggerKind.LOCATION
    is Trigger.Screen -> TriggerKind.SCREEN
    is Trigger.Airplane -> TriggerKind.AIRPLANE
}

private fun kindOf(a: Action): ActionKind = when (a) {
    is Action.Ringer -> ActionKind.RINGER
    is Action.Dnd -> ActionKind.DND
    is Action.Volume -> ActionKind.VOLUME
    is Action.Brightness -> ActionKind.BRIGHTNESS
    is Action.AutoRotate -> ActionKind.ROTATE
    is Action.DarkTheme -> ActionKind.DARK
    is Action.BatterySaver -> ActionKind.SAVER
    is Action.WifiToggle -> ActionKind.WIFI
    is Action.BluetoothToggle -> ActionKind.BLUETOOTH
    is Action.AirplaneToggle -> ActionKind.AIRPLANE
    is Action.LaunchApp -> ActionKind.APP
    is Action.Flashlight -> ActionKind.FLASH
    is Action.Notify -> ActionKind.NOTIFY
}

private fun kindOfCond(c: Condition): ConditionKind = when (c) {
    is Condition.OnDays -> ConditionKind.DAYS
    is Condition.BetweenHours -> ConditionKind.WINDOW
    is Condition.BatteryUnder -> ConditionKind.BATTERY
    is Condition.WhileCharging -> ConditionKind.CHARGING
}

private fun accessBadge(a: Access): String? = when (a) {
    Access.SHIZUKU -> "needs Shizuku setup"
    Access.SECURE_SETTINGS -> "needs ADB grant"
    else -> null
}

private fun accessNote(a: Access): String = when (a) {
    Access.DND -> "Needs DND access"
    Access.WRITE_SETTINGS -> "Needs system-settings access"
    Access.SECURE_SETTINGS -> "Needs one-time ADB grant"
    Access.SHIZUKU -> "Restricted — needs Shizuku"
    Access.NONE -> ""
}
