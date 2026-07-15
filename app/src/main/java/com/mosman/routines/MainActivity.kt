@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mosman.routines

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import java.time.format.TextStyle as JTextStyle

/**
 * Pixel-style Material You theme: colors come from the wallpaper (dynamic color,
 * available since Android 12 = our minSdk) and follow the system light/dark setting.
 */
@Composable
fun RoutinesTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

class MainActivity : ComponentActivity() {
    private val permTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Scheduler.rescheduleAll(this)
        setContent {
            RoutinesTheme {
                RoutinesApp(tick = permTick.intValue, refresh = { permTick.intValue++ })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permTick.intValue++ // re-check special accesses when coming back from Settings
    }
}

private fun timeLabel(ctx: Context, hour: Int, minute: Int): String =
    if (DateFormat.is24HourFormat(ctx)) String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    else LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

@Composable
fun RoutinesApp(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val routines = remember { mutableStateListOf<Routine>().apply { addAll(Store.load(ctx)) } }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = ZonedDateTime.now() } }

    fun persist() {
        routines.sortBy { it.hour * 60 + it.minute }
        Store.save(ctx, routines.toList())
    }

    if (showEditor) {
        BackHandler { showEditor = false }
        EditorScreen(
            initial = editing,
            onBack = { showEditor = false },
            onSave = { r ->
                editing?.let { old -> Scheduler.cancel(ctx, old); routines.removeAll { it.id == old.id } }
                routines.add(r)
                persist()
                if (r.enabled) Scheduler.schedule(ctx, r)
                showEditor = false
                scope.launch { snackbar.showSnackbar("Saved “${r.name}”") }
            },
            onDelete = { r ->
                Scheduler.cancel(ctx, r)
                routines.removeAll { it.id == r.id }
                persist()
                showEditor = false
            },
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add routine")
                }
            },
        ) { pad ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                        Text(
                            "Routines",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            if (routines.isEmpty()) "Your phone, on your schedule"
                            else routines.count { it.enabled }.toString() + " of " + routines.size + " active",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item { PermissionCards(tick = tick, routines = routines.toList(), refresh = refresh) }

                if (routines.isEmpty()) {
                    item { EmptyState() }
                }

                items(routines.toList(), key = { it.id }) { r ->
                    RoutineCard(
                        r = r, now = now,
                        onClick = { editing = r; showEditor = true },
                        onToggle = { on ->
                            val idx = routines.indexOfFirst { it.id == r.id }
                            if (idx >= 0) {
                                val upd = r.copy(enabled = on)
                                routines[idx] = upd
                                persist()
                                if (on) Scheduler.schedule(ctx, upd) else Scheduler.cancel(ctx, upd)
                            }
                        },
                        onRunNow = {
                            val results = Actions.apply(ctx, r)
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (results.isEmpty()) "No actions set" else results.joinToString(" · ")
                                )
                            }
                        },
                    )
                }

                item { Spacer(Modifier.height(80.dp)) } // room above FAB
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No routines yet", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                "Tap + to make your first one. Ideas:\n" +
                    "• “Lectures” — Mon–Fri 8:00 → Vibrate\n" +
                    "• “Night” — every day 22:00 → Silent + DND on\n" +
                    "• “Morning” — every day 6:30 → Sound, media 60%",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Permission / special-access cards ----

@Composable
private fun PermissionCards(tick: Int, routines: List<Routine>, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val alarmMgr = ctx.getSystemService(AlarmManager::class.java)
    val notifMgr = ctx.getSystemService(NotificationManager::class.java)

    val needExact = remember(tick) { !alarmMgr.canScheduleExactAlarms() }
    val needDnd = remember(tick) { !notifMgr.isNotificationPolicyAccessGranted }
    val usesDisplay = routines.any { it.brightness != null || it.autoRotate != Toggle.NO_CHANGE }
    val needWrite = remember(tick) { !Settings.System.canWrite(ctx) } && usesDisplay
    val needNotif = remember(tick) {
        Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (needDnd) PermCard(
            "Allow Do Not Disturb access",
            "Needed to switch Sound / Vibrate / Silent and DND. Pick Routines in the list, then allow.",
        ) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }

        if (needExact) PermCard(
            "Allow exact alarms",
            "Needed so routines fire at the exact minute you set.",
        ) {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
            )
        }

        if (needWrite) PermCard(
            "Allow modifying system settings",
            "Needed for brightness and auto-rotate actions.",
        ) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
            )
        }

        if (needNotif) PermCard(
            "Allow notifications",
            "Optional: get a small confirmation each time a routine runs.",
        ) { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
}

@Composable
private fun PermCard(title: String, desc: String, onGrant: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    desc, fontSize = 12.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(onClick = onGrant) { Text("Grant", fontWeight = FontWeight.Bold) }
        }
    }
}

// ---- Routine card ----

@Composable
private fun RoutineCard(
    r: Routine,
    now: ZonedDateTime,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
) {
    val ctx = LocalContext.current
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        r.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        timeLabel(ctx, r.hour, r.minute),
                        fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = if (r.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = r.enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (d in 1..7) {
                    val active = r.days.contains(d)
                    Text(
                        DayOfWeek.of(d).getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                r.summary(), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (r.enabled) {
                r.nextTrigger(now)?.let { next ->
                    val d = Duration.between(now, next)
                    val days = d.toDays()
                    val h = d.toHours() % 24
                    val m = (d.toMinutes() % 60)
                    val rel = when {
                        days > 0 -> "${days}d ${h}h"
                        h > 0 -> "${h}h ${m}m"
                        else -> "${maxOf(m, 1)}m"
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Next: " + next.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()) +
                            " · in " + rel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRunNow) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run now", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---- Editor ----

@Composable
fun EditorScreen(
    initial: Routine?,
    onBack: () -> Unit,
    onSave: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    val ctx = LocalContext.current
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    val timeState = rememberTimePickerState(
        initialHour = initial?.hour ?: 22,
        initialMinute = initial?.minute ?: 0,
        is24Hour = DateFormat.is24HourFormat(ctx),
    )
    var days by remember(initial) { mutableStateOf(initial?.days ?: (1..7).toSet()) }
    var ringer by remember(initial) { mutableStateOf(initial?.ringer ?: RingerAction.NO_CHANGE) }
    var dnd by remember(initial) { mutableStateOf(initial?.dnd ?: Toggle.NO_CHANGE) }
    var mediaVol by remember(initial) { mutableStateOf(initial?.mediaVol) }
    var ringVol by remember(initial) { mutableStateOf(initial?.ringVol) }
    var alarmVol by remember(initial) { mutableStateOf(initial?.alarmVol) }
    var brightness by remember(initial) { mutableStateOf(initial?.brightness) }
    var autoRotate by remember(initial) { mutableStateOf(initial?.autoRotate ?: Toggle.NO_CHANGE) }
    var confirmDelete by remember { mutableStateOf(false) }

    val candidate = Routine(
        id = initial?.id ?: System.currentTimeMillis(),
        name = name.ifBlank { "Routine" },
        enabled = initial?.enabled ?: true,
        hour = timeState.hour, minute = timeState.minute,
        days = days, ringer = ringer, dnd = dnd,
        mediaVol = mediaVol, ringVol = ringVol, alarmVol = alarmVol,
        brightness = brightness, autoRotate = autoRotate,
    )
    val valid = days.isNotEmpty() && candidate.hasAnyAction()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (initial == null) "New routine" else "Edit routine",
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (initial != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Name") },
            placeholder = { Text("e.g. Night mode") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionCard("When") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 1..7) {
                    val sel = days.contains(d)
                    FilterChip(
                        selected = sel,
                        onClick = { days = if (sel) days - d else days + d },
                        label = { Text(DayOfWeek.of(d).getDisplayName(JTextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { days = (1..7).toSet() }) { Text("Every day") }
                TextButton(onClick = { days = (1..5).toSet() }) { Text("Weekdays") }
                TextButton(onClick = { days = setOf(6, 7) }) { Text("Weekend") }
            }
        }

        SectionCard("Sound") {
            FieldLabel("Sound mode")
            Segmented(
                options = RingerAction.entries.map { it to it.label() },
                selected = ringer, onSelect = { ringer = it },
            )
            FieldLabel("Do Not Disturb")
            Segmented(
                options = listOf(Toggle.NO_CHANGE to "Keep", Toggle.ON to "On", Toggle.OFF to "Off"),
                selected = dnd, onSelect = { dnd = it },
            )
            OptionalSlider("Media volume", mediaVol) { mediaVol = it }
            OptionalSlider("Ring volume", ringVol) { ringVol = it }
            OptionalSlider("Alarm volume", alarmVol) { alarmVol = it }
        }

        SectionCard("Display") {
            OptionalSlider("Brightness", brightness) { brightness = it }
            FieldLabel("Auto-rotate")
            Segmented(
                options = listOf(Toggle.NO_CHANGE to "Keep", Toggle.ON to "On", Toggle.OFF to "Off"),
                selected = autoRotate, onSelect = { autoRotate = it },
            )
        }

        Button(
            onClick = { onSave(candidate) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Save routine", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

        if (!valid) {
            Text(
                "Pick at least one day and one action (“Keep” means don’t change).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “" + initial.name + "”?") },
            text = { Text("This routine and its schedule will be removed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(initial) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                icon = {},
            ) { Text(label, maxLines = 1, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun OptionalSlider(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .clickable { onChange(if (value == null) 60 else null) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = value != null, onCheckedChange = { onChange(if (it) 60 else null) })
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (value != null) {
                Text("$value%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        if (value != null) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = 0f..100f,
            )
        }
    }
}
