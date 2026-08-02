@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mosman.routines

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

@Composable
fun BackIcon() = Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")

private enum class TriggerKind(val icon: String, val label: String, val sub: String) {
    TIME("schedule", "Time of day", "At a set time on chosen days"),
    SUN("sunny", "Sunrise / sunset", "Follows the sun, with an offset"),
    NOTIFICATION("notify", "A notification arrives", "From an app, optionally matching text"),
    CALENDAR("calendar", "Calendar event", "When an event starts or ends"),
    MOTION("car", "Driving / walking", "When you start or stop moving"),
    GESTURE("screen", "Flip or shake", "Face-down to silence, shake to run"),
    APP_OPEN("app", "You open an app", "When an app comes to the front"),
    NFC("nfc", "NFC tag", "Tap a sticker to run this"),
    BATTERY("battery", "Battery level", "Drops below / rises above a %"),
    POWER("power", "Charging", "Charger connected or unplugged"),
    HEADSET("headphones", "Headphones", "Wired headset plugged/unplugged"),
    BLUETOOTH("bluetooth", "Bluetooth device", "A device connects/disconnects"),
    WIFI("wifi", "Wi-Fi network", "Join or leave a network"),
    LOCATION("location", "Location", "Arrive at or leave a place"),
    SCREEN("screen", "Screen", "Screen turns on or off"),
    AIRPLANE("flight", "Airplane mode", "Airplane mode on or off"),
}

private enum class ActionKind(val icon: String, val label: String, val access: Access) {
    RINGER("ringer", "Ringer mode", Access.DND),
    DND("dnd", "Do Not Disturb", Access.DND),
    VOLUME("volume", "Set a volume", Access.NONE),
    BRIGHTNESS("brightness", "Brightness", Access.WRITE_SETTINGS),
    ROTATE("rotate", "Auto-rotate", Access.WRITE_SETTINGS),
    DARK("dark", "Dark theme", Access.SECURE_SETTINGS),
    SAVER("saver", "Battery Saver", Access.SECURE_SETTINGS),
    WIFI("wifi", "Wi-Fi on/off", Access.SHIZUKU),
    BLUETOOTH("bluetooth", "Bluetooth on/off", Access.SHIZUKU),
    AIRPLANE("flight", "Airplane on/off", Access.SHIZUKU),
    APP("app", "Open an app", Access.NONE),
    WEBSITE("app", "Open a website", Access.NONE),
    MESSAGE("chat", "Message someone", Access.NONE),
    CALL("call", "Call someone", Access.CALL),
    SMS("chat", "Send a text", Access.SMS),
    REPLY("reply", "Reply to a message", Access.NOTIF_ACCESS),
    SPEAK("speak", "Say something out loud", Access.NONE),
    MEDIA("music", "Media control", Access.NONE),
    FLASH("flash", "Flashlight", Access.NONE),
    NOTIFY("notify", "Show a reminder", Access.NONE),
    WAIT("schedule", "Wait between actions", Access.NONE),
}

private enum class ConditionKind(val label: String) {
    DAYS("Only on certain days"),
    WINDOW("Only between times"),
    BATTERY("Only under battery %"),
    CHARGING("Only while charging"),
}

private data class TrigEdit(val kind: TriggerKind, val index: Int?, val isEnd: Boolean)
private data class ActEdit(val kind: ActionKind, val index: Int?, val isEnd: Boolean)

@Composable
fun EditorScreen(
    initial: Routine,
    onSave: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.icon) }
    var match by remember { mutableStateOf(initial.match) }
    var endMode by remember { mutableStateOf(initial.endMode) }
    var notifyOnRun by remember { mutableStateOf(initial.notifyOnRun) }
    val triggers = remember { mutableStateListOf<Trigger>().apply { addAll(initial.triggers) } }
    val conditions = remember { mutableStateListOf<Condition>().apply { addAll(initial.conditions) } }
    val actions = remember { mutableStateListOf<Action>().apply { addAll(initial.actions) } }
    val endTriggers = remember { mutableStateListOf<Trigger>().apply { addAll(initial.endTriggers) } }
    val endActions = remember { mutableStateListOf<Action>().apply { addAll(initial.endActions) } }
    val ctx = LocalContext.current
    val existing = remember { Store.get(ctx, initial.id) != null }

    var triggerSheetFor by remember { mutableStateOf<Boolean?>(null) }  // false=start, true=end
    var actionSheetFor by remember { mutableStateOf<Boolean?>(null) }
    var showConditionSheet by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var editTrigger by remember { mutableStateOf<TrigEdit?>(null) }
    var editAction by remember { mutableStateOf<ActEdit?>(null) }
    var editCondition by remember { mutableStateOf<Pair<ConditionKind, Int?>?>(null) }

    fun result() = initial.copy(
        name = name.ifBlank { "Routine" }, icon = icon, match = match, notifyOnRun = notifyOnRun,
        triggers = triggers.toList(), conditions = conditions.toList(), actions = actions.toList(),
        endTriggers = endTriggers.toList(), endMode = endMode, endActions = endActions.toList(),
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
                        shape = RoundedCornerShape(18.dp)) {
                        Text("Save routine", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Name + icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { showIconPicker = true },
                    contentAlignment = Alignment.Center) {
                    Icon(Ic.of(icon), "Choose icon", Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
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
                    onClick = { editTrigger = TrigEdit(kindOf(t), i, false) },
                    onRemove = { triggers.removeAt(i) })
            }
            AddButton("Add trigger") { triggerSheetFor = false }

            // ONLY IF
            SectionHeader("ONLY IF", "these also hold (optional)", MaterialTheme.colorScheme.secondary)
            conditions.forEachIndexed { i, c ->
                ItemRow("check", c.describe().replaceFirstChar { it.uppercase() },
                    onClick = { editCondition = kindOfCond(c) to i },
                    onRemove = { conditions.removeAt(i) })
            }
            AddButton("Add condition") { showConditionSheet = true }

            // THEN
            SectionHeader("THEN", "do this", MaterialTheme.colorScheme.tertiary)
            actions.forEachIndexed { i, a ->
                ItemRow(a.icon(), a.describe(),
                    onClick = { editAction = ActEdit(kindOf(a), i, false) },
                    onRemove = { actions.removeAt(i) },
                    badge = accessBadge(ctx, a.access()))
            }
            AddButton("Add action") { actionSheetFor = false }

            // UNTIL (end condition)
            SectionHeader("UNTIL", "the routine should stop (optional)",
                MaterialTheme.colorScheme.secondary)
            endTriggers.forEachIndexed { i, t ->
                ItemRow(t.icon(), t.describe(),
                    onClick = { editTrigger = TrigEdit(kindOf(t), i, true) },
                    onRemove = { endTriggers.removeAt(i) })
            }
            AddButton("Add end condition") { triggerSheetFor = true }

            if (endTriggers.isNotEmpty()) {
                Text("When it stops", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    EndMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = endMode == m, onClick = { endMode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, EndMode.entries.size)) {
                            Text(m.label(), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                if (endMode == EndMode.REVERT) Text(
                    "Your settings go back exactly how they were before the routine ran.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (endMode == EndMode.CUSTOM) {
                    endActions.forEachIndexed { i, a ->
                        ItemRow(a.icon(), a.describe(),
                            onClick = { editAction = ActEdit(kindOf(a), i, true) },
                            onRemove = { endActions.removeAt(i) },
                            badge = accessBadge(ctx, a.access()))
                    }
                    AddButton("Add end action") { actionSheetFor = true }
                }
            }

            // One-tap shortcut on the home screen
            if (existing) OutlinedButton(
                onClick = {
                    if (RunActivity.pinShortcut(ctx, result()))
                        android.widget.Toast.makeText(ctx, "Check your home screen",
                            android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.AddToHomeScreen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add a shortcut to the home screen")
            }

            // Notify toggle
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notify when it runs", fontSize = 15.sp)
                        Text("Turn off for a routine that should work quietly",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = notifyOnRun, onCheckedChange = { notifyOnRun = it })
                }
            }

            if (!valid) Text("Add at least one trigger and one action to save.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- Picker sheets ----
    triggerSheetFor?.let { isEnd ->
        PickSheet(if (isEnd) "What ends this routine?" else "Choose a trigger",
            TriggerKind.entries, { it.icon }, { it.label }, { it.sub },
            onDismiss = { triggerSheetFor = null }) {
            triggerSheetFor = null; editTrigger = TrigEdit(it, null, isEnd)
        }
    }
    actionSheetFor?.let { isEnd ->
        PickSheet("Choose an action", ActionKind.entries,
            { it.icon }, { it.label }, { accessNote(ctx, it.access) },
            onDismiss = { actionSheetFor = null }) {
            actionSheetFor = null; editAction = ActEdit(it, null, isEnd)
        }
    }
    if (showConditionSheet) PickSheet("Add a condition", ConditionKind.entries,
        { "check" }, { it.label }, { "" }, onDismiss = { showConditionSheet = false }) {
        showConditionSheet = false; editCondition = it to null
    }

    // ---- Config dialogs ----
    editTrigger?.let { e ->
        val list = if (e.isEnd) endTriggers else triggers
        TriggerConfig(e.kind, e.index?.let { list[it] }, onDismiss = { editTrigger = null }) { built ->
            if (e.index == null) list.add(built) else list[e.index] = built
            editTrigger = null
        }
    }
    editAction?.let { e ->
        val list = if (e.isEnd) endActions else actions
        ActionConfig(e.kind, e.index?.let { list[it] }, onDismiss = { editAction = null }) { built ->
            if (e.index == null) list.add(built) else list[e.index] = built
            editAction = null
        }
    }
    editCondition?.let { (kind, idx) ->
        ConditionConfig(kind, idx?.let { conditions[it] }, onDismiss = { editCondition = null }) { built ->
            if (idx == null) conditions.add(built) else conditions[idx] = built
            editCondition = null
        }
    }
    if (showIconPicker) IconPickerDialog(
        onPick = { icon = it; showIconPicker = false }, onDismiss = { showIconPicker = false })
}

// ============================================================================
//  Reusable pieces
// ============================================================================

@Composable
private fun SectionHeader(tag: String, sub: String, color: Color) {
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
private fun ItemRow(iconKey: String, text: String, onClick: () -> Unit, onRemove: () -> Unit,
                    badge: String? = null) {
    Card(shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.of(iconKey), null, Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
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
        shape = RoundedCornerShape(18.dp)) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text)
    }
}

@Composable
private fun <T> PickSheet(
    title: String, options: List<T>,
    icon: (T) -> String, label: (T) -> String, sub: (T) -> String,
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
                    Icon(Ic.of(icon(opt)), null, Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(18.dp))
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
        Card(shape = RoundedCornerShape(28.dp)) {
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

@Composable
private fun OnOff(label: String, on: Boolean, onChange: (Boolean) -> Unit,
                  onText: String = "On", offText: String = "Off") {
    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = on, onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(onText, maxLines = 1) }
            SegmentedButton(selected = !on, onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(offText, maxLines = 1) }
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

/** Pick a person from the system contact picker, or just type a number. */
@Composable
private fun ContactField(who: String, number: String,
                         onWho: (String) -> Unit, onNumber: (String) -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            Contacts.read(ctx, uri)?.let { c -> onWho(c.name); onNumber(c.number) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { picker.launch(Contacts.pickIntent()) },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Person, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (who.isBlank()) "Choose a contact" else "Change contact")
        }
        if (who.isNotBlank()) Text(who, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        InputField("Phone number", number, number = true) { onNumber(it) }
    }
}

@Composable
private fun InputField(label: String, value: String, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth())
}

// ============================================================================
//  Trigger config
// ============================================================================

@Composable
private fun TriggerConfig(kind: TriggerKind, existing: Trigger?, onDismiss: () -> Unit,
                          onSave: (Trigger) -> Unit) {
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
        TriggerKind.POWER -> OnOffTrigger("Charging", existing,
            { (it as? Trigger.Power)?.connected ?: true }, { Trigger.Power(it) },
            onDismiss, onSave, "Connected", "Disconnected")
        TriggerKind.HEADSET -> OnOffTrigger("Headphones", existing,
            { (it as? Trigger.Headset)?.connected ?: true }, { Trigger.Headset(it) },
            onDismiss, onSave, "Plugged in", "Unplugged")
        TriggerKind.SCREEN -> OnOffTrigger("Screen", existing,
            { (it as? Trigger.Screen)?.on ?: true }, { Trigger.Screen(it) },
            onDismiss, onSave, "Turns on", "Turns off")
        TriggerKind.AIRPLANE -> OnOffTrigger("Airplane mode", existing,
            { (it as? Trigger.Airplane)?.on ?: true }, { Trigger.Airplane(it) },
            onDismiss, onSave, "Turns on", "Turns off")
        TriggerKind.BLUETOOTH -> {
            val t = existing as? Trigger.Bluetooth
            var on by remember { mutableStateOf(t?.connected ?: true) }
            var dev by remember { mutableStateOf(t?.deviceName ?: "") }
            ConfigDialog("Bluetooth device", onDismiss = onDismiss,
                onSave = { onSave(Trigger.Bluetooth(on, dev.ifBlank { null })) }) {
                OnOff("When a device", on, { on = it }, "Connects", "Disconnects")
                InputField("Device name (blank = any)", dev) { dev = it }
            }
        }
        TriggerKind.WIFI -> {
            val t = existing as? Trigger.Wifi
            var on by remember { mutableStateOf(t?.connected ?: true) }
            var ssid by remember { mutableStateOf(t?.ssid ?: "") }
            ConfigDialog("Wi-Fi network", onDismiss = onDismiss,
                onSave = { onSave(Trigger.Wifi(on, ssid.ifBlank { null })) }) {
                OnOff("When Wi-Fi", on, { on = it }, "Connects", "Disconnects")
                InputField("Network name / SSID (blank = any)", ssid) { ssid = it }
            }
        }
        TriggerKind.LOCATION -> LocationConfig(existing as? Trigger.Location, onDismiss, onSave)
        TriggerKind.SUN -> SunConfig(existing as? Trigger.Sun, onDismiss, onSave)
        TriggerKind.NOTIFICATION -> {
            val ctx = LocalContext.current
            val t = existing as? Trigger.NotificationFrom
            val apps = remember { Apps.installed(ctx) }
            var pkg by remember { mutableStateOf(t?.pkg ?: "") }
            var label by remember { mutableStateOf(t?.label ?: "") }
            var contains by remember { mutableStateOf(t?.contains ?: "") }
            var query by remember { mutableStateOf("") }
            ConfigDialog("Notification arrives", canSave = pkg.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Trigger.NotificationFrom(pkg, label, contains.trim())) }) {
                if (pkg.isNotBlank()) Text("From: $label", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                InputField("Search apps", query) { query = it }
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        Row(Modifier.fillMaxWidth()
                            .clickable { pkg = app.pkg; label = app.label }
                            .padding(vertical = 10.dp)) {
                            Text(app.label, fontSize = 15.sp,
                                color = if (app.pkg == pkg) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                InputField("Only if it contains (optional)", contains) { contains = it }
                Text("Needs notification access — the app will ask.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TriggerKind.CALENDAR -> {
            val ctx = LocalContext.current
            val t = existing as? Trigger.CalendarEvent
            var title by remember { mutableStateOf(t?.titleContains ?: "") }
            var atStart by remember { mutableStateOf(t?.atStart ?: true) }
            val titles = remember { Calendars.upcomingTitles(ctx) }
            ConfigDialog("Calendar event", onDismiss = onDismiss,
                onSave = { onSave(Trigger.CalendarEvent(title.trim(), atStart)) }) {
                OnOff("Trigger when the event", atStart, { atStart = it }, "Starts", "Ends")
                InputField("Event name contains (blank = any)", title) { title = it }
                if (titles.isNotEmpty()) {
                    Text("From your calendar:", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        titles.take(6).forEach { s ->
                            SuggestionChip(onClick = { title = s },
                                label = { Text(s, maxLines = 1, fontSize = 12.sp) })
                        }
                    }
                }
            }
        }
        TriggerKind.MOTION -> {
            val t = existing as? Trigger.Motion
            var type by remember { mutableStateOf(t?.type ?: MotionType.VEHICLE) }
            var entering by remember { mutableStateOf(t?.entering ?: true) }
            ConfigDialog("Driving / walking", onDismiss = onDismiss,
                onSave = { onSave(Trigger.Motion(type, entering)) }) {
                OnOff("Trigger when you", entering, { entering = it }, "Start", "Stop")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MotionType.entries.forEach { m ->
                        FilterChip(selected = type == m, onClick = { type = m },
                            label = { Text(m.label()) })
                    }
                }
                Text("Your phone works this out from movement — no extra hardware needed.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TriggerKind.GESTURE -> {
            var type by remember { mutableStateOf((existing as? Trigger.Gesture)?.type ?: GestureType.FLIP_DOWN) }
            ConfigDialog("Flip or shake", onDismiss = onDismiss,
                onSave = { onSave(Trigger.Gesture(type)) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GestureType.entries.forEach { g ->
                        FilterChip(selected = type == g, onClick = { type = g },
                            label = { Text(g.label()) }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Text("Keeps the motion sensor awake while enabled — costs a little battery.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TriggerKind.APP_OPEN -> {
            val ctx = LocalContext.current
            val t = existing as? Trigger.AppOpened
            val apps = remember { Apps.installed(ctx) }
            var pkg by remember { mutableStateOf(t?.pkg ?: "") }
            var label by remember { mutableStateOf(t?.label ?: "") }
            var query by remember { mutableStateOf("") }
            ConfigDialog("You open an app", canSave = pkg.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Trigger.AppOpened(pkg, label)) }) {
                if (pkg.isNotBlank()) Text("App: $label", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                InputField("Search apps", query) { query = it }
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        Row(Modifier.fillMaxWidth()
                            .clickable { pkg = app.pkg; label = app.label }
                            .padding(vertical = 10.dp)) {
                            Text(app.label, fontSize = 15.sp,
                                color = if (app.pkg == pkg) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Text("Needs Usage access — the app will ask.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TriggerKind.NFC -> NfcConfig(existing as? Trigger.NfcTag, onDismiss, onSave)
    }
}

/** Reads a real tag so you never type an id by hand. */
@Composable
private fun NfcConfig(existing: Trigger.NfcTag?, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? android.app.Activity
    var id by remember { mutableStateOf(existing?.id ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var scanning by remember { mutableStateOf(false) }

    // Reader mode captures the tag while this dialog is open.
    DisposableEffect(scanning) {
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(ctx)
        if (scanning && activity != null && adapter != null) {
            adapter.enableReaderMode(activity, { tag ->
                id = tag.idHex(); scanning = false
            }, android.nfc.NfcAdapter.FLAG_READER_NFC_A or
                android.nfc.NfcAdapter.FLAG_READER_NFC_B or
                android.nfc.NfcAdapter.FLAG_READER_NFC_F or
                android.nfc.NfcAdapter.FLAG_READER_NFC_V or
                android.nfc.NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        }
        onDispose { if (activity != null) runCatching { adapter?.disableReaderMode(activity) } }
    }

    ConfigDialog("NFC tag", canSave = id.isNotBlank() && label.isNotBlank(), onDismiss = onDismiss,
        onSave = { onSave(Trigger.NfcTag(id, label.trim())) }) {
        if (!NfcActivity.available(ctx)) {
            Text("NFC is off or unavailable. Turn it on in Settings first.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
        FilledTonalButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Ic.of("nfc"), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (scanning) "Hold your tag to the phone…" else if (id.isBlank()) "Scan a tag" else "Scan a different tag")
        }
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (id.isNotBlank()) Text("Tag: $id", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        InputField("Name this tag", label) { label = it }
    }
}

/** Sunrise/sunset trigger — place picked on the map, plus a ± minutes offset. */
@Composable
private fun SunConfig(existing: Trigger.Sun?, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
    var sunrise by remember { mutableStateOf(existing?.sunrise ?: false) }
    var offset by remember { mutableIntStateOf(existing?.offsetMin ?: 0) }
    var place by remember { mutableStateOf(existing?.place ?: "") }
    var lat by remember { mutableDoubleStateOf(existing?.lat ?: 0.0) }
    var lng by remember { mutableDoubleStateOf(existing?.lng ?: 0.0) }
    var showMap by remember { mutableStateOf(false) }

    if (showMap) {
        Dialog(onDismissRequest = { showMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                PlacePicker(
                    initial = if (place.isNotBlank())
                        Trigger.Location(true, lat, lng, 200f, place) else null,
                    onBack = { showMap = false },
                    onDone = { p, la, ln, _ -> place = p; lat = la; lng = ln; showMap = false },
                )
            }
        }
        return
    }

    ConfigDialog("Sunrise / sunset", canSave = place.isNotBlank(), onDismiss = onDismiss,
        onSave = { onSave(Trigger.Sun(sunrise, offset, lat, lng, place)) }) {
        OnOff("Follow the", sunrise, { sunrise = it }, "Sunrise", "Sunset")
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Offset", fontSize = 14.sp)
                Text(
                    when {
                        offset == 0 -> "exactly"
                        offset > 0 -> "$offset min after"
                        else -> "${-offset} min before"
                    },
                    fontWeight = FontWeight.SemiBold)
            }
            Slider(value = offset.toFloat(), onValueChange = { offset = it.toInt() },
                valueRange = -60f..60f, steps = 23)
        }
        FilledTonalButton(onClick = { showMap = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (place.isBlank()) "Pick where you are on the map" else "Change place")
        }
        if (place.isNotBlank()) Text(place, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Sun times are computed offline from the location — no internet needed.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Location trigger — opens the full-screen map picker instead of asking for coordinates. */
@Composable
private fun LocationConfig(existing: Trigger.Location?, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
    var enter by remember { mutableStateOf(existing?.enter ?: true) }
    var place by remember { mutableStateOf(existing?.place ?: "") }
    var lat by remember { mutableDoubleStateOf(existing?.lat ?: 0.0) }
    var lng by remember { mutableDoubleStateOf(existing?.lng ?: 0.0) }
    var radius by remember { mutableFloatStateOf(existing?.radius ?: 200f) }
    var showMap by remember { mutableStateOf(false) }

    if (showMap) {
        Dialog(onDismissRequest = { showMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                PlacePicker(
                    initial = if (place.isNotBlank()) Trigger.Location(enter, lat, lng, radius, place) else null,
                    onBack = { showMap = false },
                    onDone = { p, la, ln, r ->
                        place = p; lat = la; lng = ln; radius = r; showMap = false
                    },
                )
            }
        }
        return
    }

    ConfigDialog("Location", canSave = place.isNotBlank(), onDismiss = onDismiss,
        onSave = { onSave(Trigger.Location(enter, lat, lng, radius, place)) }) {
        OnOff("When you", enter, { enter = it }, "Arrive", "Leave")
        FilledTonalButton(onClick = { showMap = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (place.isBlank()) "Pick a place on the map" else "Change place")
        }
        if (place.isNotBlank()) Text("$place · ${radius.toInt()} m radius",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================================
//  Action config
// ============================================================================

@Composable
private fun ActionConfig(kind: ActionKind, existing: Action?, onDismiss: () -> Unit,
                         onSave: (Action) -> Unit) {
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
        ActionKind.DND -> OnOffAction("Do Not Disturb", existing,
            { (it as? Action.Dnd)?.on ?: true }, { Action.Dnd(it) }, onDismiss, onSave)
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
        ActionKind.ROTATE -> OnOffAction("Auto-rotate", existing,
            { (it as? Action.AutoRotate)?.on ?: true }, { Action.AutoRotate(it) }, onDismiss, onSave)
        ActionKind.DARK -> OnOffAction("Dark theme", existing,
            { (it as? Action.DarkTheme)?.on ?: true }, { Action.DarkTheme(it) }, onDismiss, onSave,
            note = "Needs the one-time ADB grant — see Settings.")
        ActionKind.SAVER -> OnOffAction("Battery Saver", existing,
            { (it as? Action.BatterySaver)?.on ?: true }, { Action.BatterySaver(it) }, onDismiss, onSave,
            note = "Needs the one-time ADB grant — see Settings.")
        ActionKind.WIFI -> OnOffAction("Wi-Fi", existing,
            { (it as? Action.WifiToggle)?.on ?: true }, { Action.WifiToggle(it) }, onDismiss, onSave,
            note = "Needs Shizuku — set it up in Settings.")
        ActionKind.BLUETOOTH -> OnOffAction("Bluetooth", existing,
            { (it as? Action.BluetoothToggle)?.on ?: true }, { Action.BluetoothToggle(it) },
            onDismiss, onSave, note = "Needs Shizuku — set it up in Settings.")
        ActionKind.AIRPLANE -> OnOffAction("Airplane mode", existing,
            { (it as? Action.AirplaneToggle)?.on ?: true }, { Action.AirplaneToggle(it) },
            onDismiss, onSave, note = "Needs Shizuku — set it up in Settings.")
        ActionKind.FLASH -> OnOffAction("Flashlight", existing,
            { (it as? Action.Flashlight)?.on ?: true }, { Action.Flashlight(it) }, onDismiss, onSave)
        ActionKind.APP -> {
            val ctx = LocalContext.current
            val apps = remember { Apps.installed(ctx) }
            var query by remember { mutableStateOf("") }
            ConfigDialog("Open an app", canSave = false, onDismiss = onDismiss, onSave = {}) {
                InputField("Search", query) { query = it }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        Row(Modifier.fillMaxWidth()
                            .clickable { onSave(Action.LaunchApp(app.pkg, app.label)) }
                            .padding(vertical = 12.dp)) { Text(app.label, fontSize = 15.sp) }
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
                InputField("Title", title) { title = it }
                InputField("Message", text) { text = it }
            }
        }
        ActionKind.WEBSITE -> {
            var url by remember { mutableStateOf((existing as? Action.OpenUrl)?.url ?: "") }
            ConfigDialog("Open a website", canSave = url.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Action.OpenUrl(url.trim())) }) {
                InputField("Address", url) { url = it }
                Text("e.g. wikipedia.org — https:// is added for you",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ActionKind.MEDIA -> {
            var key by remember { mutableStateOf((existing as? Action.Media)?.key ?: MediaKey.PLAY_PAUSE) }
            ConfigDialog("Media control", onDismiss = onDismiss, onSave = { onSave(Action.Media(key)) }) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MediaKey.entries.forEachIndexed { i, k ->
                        SegmentedButton(selected = key == k, onClick = { key = k },
                            shape = SegmentedButtonDefaults.itemShape(i, MediaKey.entries.size)) {
                            Text(when (k) {
                                MediaKey.PLAY_PAUSE -> "Play/Pause"
                                MediaKey.NEXT -> "Next"
                                MediaKey.PREVIOUS -> "Previous"
                            }, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        ActionKind.MESSAGE -> {
            val a = existing as? Action.Message
            var app by remember { mutableStateOf(a?.app ?: MessageApp.WHATSAPP) }
            var who by remember { mutableStateOf(a?.who ?: "") }
            var num by remember { mutableStateOf(a?.number ?: "") }
            var text by remember { mutableStateOf(a?.text ?: "") }
            ConfigDialog("Message someone", canSave = num.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Action.Message(app, num.trim(), who, text)) }) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MessageApp.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = app == m, onClick = { app = m },
                            shape = SegmentedButtonDefaults.itemShape(i, MessageApp.entries.size)) {
                            Text(m.label(), fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
                ContactField(who, num, { who = it }, { num = it })
                InputField("Message", text) { text = it }
                Text(
                    if (app == MessageApp.WHATSAPP)
                        "Opens the chat with your message already typed — you tap send. " +
                            "WhatsApp gives no app a way to send for you."
                    else "Opens Messages with the text ready to send.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ActionKind.CALL -> {
            val a = existing as? Action.Call
            var who by remember { mutableStateOf(a?.who ?: "") }
            var num by remember { mutableStateOf(a?.number ?: "") }
            ConfigDialog("Call someone", canSave = num.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Action.Call(num.trim(), who)) }) {
                ContactField(who, num, { who = it }, { num = it })
                Text("This dials for real, on its own. Give it a time you're sure about.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        ActionKind.SMS -> {
            val a = existing as? Action.SendSms
            var who by remember { mutableStateOf(a?.who ?: "") }
            var num by remember { mutableStateOf(a?.number ?: "") }
            var text by remember { mutableStateOf(a?.text ?: "") }
            ConfigDialog("Send a text", canSave = num.isNotBlank() && text.isNotBlank(),
                onDismiss = onDismiss,
                onSave = { onSave(Action.SendSms(num.trim(), who, text)) }) {
                ContactField(who, num, { who = it }, { num = it })
                InputField("Message", text) { text = it }
                Text("Sends by itself, no tap needed. Carrier rates apply.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        ActionKind.REPLY -> {
            val ctx = LocalContext.current
            val a = existing as? Action.ReplyNotification
            val apps = remember { Apps.installed(ctx) }
            var pkg by remember { mutableStateOf(a?.pkg ?: "") }
            var label by remember { mutableStateOf(a?.label ?: "") }
            var text by remember { mutableStateOf(a?.text ?: "") }
            var query by remember { mutableStateOf("") }
            ConfigDialog("Reply to a message", canSave = text.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Action.ReplyNotification(text, pkg, label)) }) {
                InputField("Reply with", text) { text = it }
                Text("Which app (blank = whatever messaged you last)", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pkg.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { pkg = ""; label = "" }) { Text("Any app") }
                }
                InputField("Search apps", query) { query = it }
                LazyColumn(Modifier.heightIn(max = 180.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        Row(Modifier.fillMaxWidth()
                            .clickable { pkg = app.pkg; label = app.label }
                            .padding(vertical = 10.dp)) { Text(app.label, fontSize = 15.sp) }
                    }
                }
                Text("Replies through the notification itself — the one way to answer " +
                    "WhatsApp hands-free. Pair it with a notification trigger.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ActionKind.SPEAK -> {
            var text by remember { mutableStateOf((existing as? Action.Speak)?.text ?: "") }
            ConfigDialog("Say something out loud", canSave = text.isNotBlank(), onDismiss = onDismiss,
                onSave = { onSave(Action.Speak(text)) }) {
                InputField("Words", text) { text = it }
                Text("Spoken through your phone's speaker.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ActionKind.WAIT -> {
            var secs by remember { mutableIntStateOf((existing as? Action.Wait)?.seconds ?: 3) }
            ConfigDialog("Wait between actions", onDismiss = onDismiss,
                onSave = { onSave(Action.Wait(secs)) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pause for", fontSize = 14.sp)
                    Text("$secs s", fontWeight = FontWeight.SemiBold)
                }
                Slider(value = secs.toFloat(), onValueChange = { secs = it.toInt().coerceIn(1, 30) },
                    valueRange = 1f..30f, steps = 28)
                Text("Actions after this one run once the pause finishes.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun ConditionConfig(kind: ConditionKind, existing: Condition?, onDismiss: () -> Unit,
                            onSave: (Condition) -> Unit) {
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
                onSave = { onSave(Condition.BatteryUnder(level)) }) {
                PercentSlider("Level", level) { level = it }
            }
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

// ---- Icon picker ----

@Composable
private fun IconPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Choose an icon", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ic.routinePicker.forEach { key ->
                        Box(Modifier.size(50.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPick(key) }, contentAlignment = Alignment.Center) {
                            Icon(Ic.of(key), key, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    is Trigger.Sun -> TriggerKind.SUN
    is Trigger.NotificationFrom -> TriggerKind.NOTIFICATION
    is Trigger.CalendarEvent -> TriggerKind.CALENDAR
    is Trigger.Motion -> TriggerKind.MOTION
    is Trigger.Gesture -> TriggerKind.GESTURE
    is Trigger.AppOpened -> TriggerKind.APP_OPEN
    is Trigger.NfcTag -> TriggerKind.NFC
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
    is Action.OpenUrl -> ActionKind.WEBSITE
    is Action.Message -> ActionKind.MESSAGE
    is Action.Call -> ActionKind.CALL
    is Action.SendSms -> ActionKind.SMS
    is Action.ReplyNotification -> ActionKind.REPLY
    is Action.Speak -> ActionKind.SPEAK
    is Action.Media -> ActionKind.MEDIA
    is Action.Flashlight -> ActionKind.FLASH
    is Action.Notify -> ActionKind.NOTIFY
    is Action.Wait -> ActionKind.WAIT
}

private fun kindOfCond(c: Condition): ConditionKind = when (c) {
    is Condition.OnDays -> ConditionKind.DAYS
    is Condition.BetweenHours -> ConditionKind.WINDOW
    is Condition.BatteryUnder -> ConditionKind.BATTERY
    is Condition.WhileCharging -> ConditionKind.CHARGING
}

/**
 * Warning chip on an action that cannot run yet. These read as "this is broken", so they
 * have to reflect what is actually missing — an action whose access is already granted
 * gets no chip at all.
 */
private fun accessBadge(ctx: Context, a: Access): String? {
    if (Permissions.has(ctx, a)) return null
    return when (a) {
        Access.SHIZUKU -> "needs Shizuku"
        Access.SECURE_SETTINGS -> "needs ADB grant"
        else -> null
    }
}

/** Same rule for the action picker: only mention an access you still have to give. */
private fun accessNote(ctx: Context, a: Access): String {
    if (Permissions.has(ctx, a)) return ""
    return when (a) {
        Access.DND -> "Needs DND access"
        Access.WRITE_SETTINGS -> "Needs system-settings access"
        Access.SECURE_SETTINGS -> "Needs one-time ADB grant"
        Access.SHIZUKU -> "Restricted — needs Shizuku"
        Access.CALL -> "Dials for real — needs phone permission"
        Access.SMS -> "Sends silently — needs SMS permission"
        Access.NOTIF_ACCESS -> "Needs notification access"
        Access.NONE -> ""
    }
}
