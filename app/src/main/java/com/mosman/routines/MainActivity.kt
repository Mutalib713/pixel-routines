@file:OptIn(ExperimentalMaterial3Api::class)

package com.mosman.routines

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Discover : Screen
    data object Settings : Screen
    data object History : Screen
    data class Edit(val routine: Routine) : Screen
}

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.rearmAll(this)
        setContent { PixelRoutinesTheme { AppRoot(tick.intValue) { tick.intValue++ } } }
    }

    override fun onResume() { super.onResume(); tick.intValue++ }
}

@Composable
private fun AppRoot(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    var screen by remember {
        mutableStateOf<Screen>(if (Store.onboarded(ctx)) Screen.Home else Screen.Onboarding)
    }
    var routines by remember { mutableStateOf(Store.load(ctx)) }
    fun reload() { routines = Store.load(ctx) }

    when (val s = screen) {
        is Screen.Onboarding -> Onboarding(tick) { screen = Screen.Home }
        is Screen.Home -> HomeScreen(
            routines = routines,
            tick = tick,
            onRefreshPerms = refresh,
            onOpenDiscover = { screen = Screen.Discover },
            onOpenSettings = { screen = Screen.Settings },
            onOpenHistory = { screen = Screen.History },
            onNew = { screen = Screen.Edit(Routine.new()) },
            onOpen = { screen = Screen.Edit(it) },
            onToggle = { r, on -> Store.setEnabled(ctx, r.id, on); reload() },
        )
        is Screen.Discover -> DiscoverScreen(
            onBack = { screen = Screen.Home },
            onPick = { built -> Store.upsert(ctx, built); reload(); screen = Screen.Edit(built) },
            onScratch = { screen = Screen.Edit(Routine.new()) },
        )
        is Screen.Settings -> SettingsScreen(tick, onBack = { screen = Screen.Home })
        is Screen.History -> HistoryScreen(onBack = { screen = Screen.Home })
        is Screen.Edit -> EditorScreen(
            initial = s.routine,
            onSave = { r -> Store.upsert(ctx, r); reload(); screen = Screen.Home },
            onDelete = { r -> Store.delete(ctx, r.id); reload(); screen = Screen.Home },
            onBack = { screen = Screen.Home },
        )
    }
}

@Composable
private fun HomeScreen(
    routines: List<Routine>,
    tick: Int,
    onRefreshPerms: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onNew: () -> Unit,
    onOpen: (Routine) -> Unit,
    onToggle: (Routine, Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activeIds = remember(tick, routines) { Store.activeIds(ctx) }
    var paused by remember(tick) { mutableStateOf(Store.isPaused(ctx)) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Pixel Routines", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, "History") }
                    IconButton(onClick = onOpenDiscover) { Icon(Icons.Filled.Lightbulb, "Ideas") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New routine") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            // Extra room at the bottom so the last routine can scroll clear of the
            // floating "New routine" button instead of sitting under it.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (paused) item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("All routines are paused", fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { Store.setPaused(ctx, false); paused = false }) {
                            Text("Resume", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { PermissionArea(routines, tick, onRefreshPerms) }
            if (routines.isEmpty()) item { EmptyState(onOpenDiscover) }
            items(routines, key = { it.id }) { r ->
                RoutineCard(r,
                    running = activeIds.contains(r.id),
                    onClick = { onOpen(r) },
                    onToggle = { onToggle(r, it) },
                    onRunNow = {
                        scope.launch { snackbar.showSnackbar("Running “${r.name}”…") }
                        Engine.runNow(ctx, r) { res ->
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (res.isEmpty()) "No actions" else res.joinToString(" · "))
                            }
                        }
                    })
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun RoutineCard(
    r: Routine, running: Boolean,
    onClick: () -> Unit, onToggle: (Boolean) -> Unit, onRunNow: () -> Unit,
) {
    ElevatedCard(onClick = onClick, shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.of(r.icon), null, Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name.ifBlank { "Untitled" }, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedVisibility(
                        visible = running,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                        exit = scaleOut(),
                    ) {
                        Text("Running now", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Switch(checked = r.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(12.dp))
            SummaryLine("IF", r.ifSummary(), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            SummaryLine("THEN", r.thenSummary(), MaterialTheme.colorScheme.tertiary)
            r.endSummary()?.let {
                Spacer(Modifier.height(6.dp))
                SummaryLine("UNTIL", it, MaterialTheme.colorScheme.secondary)
            }
            if (r.enabled) {
                r.nextRun()?.let { next ->
                    val d = java.time.Duration.between(java.time.ZonedDateTime.now(), next)
                    val rel = when {
                        d.toDays() > 0 -> "${d.toDays()}d ${d.toHours() % 24}h"
                        d.toHours() > 0 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
                        else -> "${maxOf(d.toMinutes(), 1)}m"
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Next: " + next.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) +
                            " " + "%02d:%02d".format(next.hour, next.minute) + " · in " + rel,
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRunNow) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("Run now")
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(tag: String, text: String, tagColor: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(tagColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tagColor) }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyState(onOpenDiscover: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Let your phone run itself", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Create a routine that changes settings on a schedule, or when something happens.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onOpenDiscover) {
                Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Browse ideas")
            }
        }
    }
}

// ---- Permission cards ----

@Composable
private fun PermissionArea(routines: List<Routine>, tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh() }

    val needDnd = remember(tick, routines) { !Permissions.hasDnd(ctx) }
    val needExact = remember(tick) { !Permissions.hasExactAlarm(ctx) }
    val needWrite = remember(tick, routines) {
        routines.any { r -> r.actions.any { it.access() == Access.WRITE_SETTINGS } } &&
            !Permissions.hasWriteSettings(ctx)
    }
    val needNotif = remember(tick) { Build.VERSION.SDK_INT >= 33 && !Permissions.hasNotifications(ctx) }
    val needCall = remember(tick, routines) {
        routines.any { r -> r.actions.any { it.access() == Access.CALL } } && !Permissions.hasCallPhone(ctx)
    }
    val needSms = remember(tick, routines) {
        routines.any { r -> r.actions.any { it.access() == Access.SMS } } && !Permissions.hasSendSms(ctx)
    }
    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh() }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh() }
    val calLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh() }
    val actLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh() }

    val allTriggers = routines.filter { it.enabled }.flatMap { it.triggers + it.endTriggers }
    val allActions = routines.filter { it.enabled }.flatMap { it.actions + it.endActions }

    val needNotifAccess = remember(tick, routines) {
        (allTriggers.any { it is Trigger.NotificationFrom } ||
            allActions.any { it.access() == Access.NOTIF_ACCESS }) &&
            !Permissions.hasNotificationAccess(ctx)
    }
    val needCalendar = remember(tick, routines) {
        allTriggers.any { it is Trigger.CalendarEvent } && !Permissions.hasCalendar(ctx)
    }
    val needActivity = remember(tick, routines) {
        allTriggers.any { it is Trigger.Motion } && !Permissions.hasActivityRecognition(ctx)
    }
    val needUsage = remember(tick, routines) {
        allTriggers.any { it is Trigger.AppOpened } && !Permissions.hasUsageAccess(ctx)
    }
    val needLoc = remember(tick, routines) {
        routines.any { r -> (r.triggers + r.endTriggers).any { it is Trigger.Location } } &&
            !Permissions.hasBackgroundLocation(ctx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (needDnd) PermCard("Allow Do Not Disturb access",
            "Unlocks Silent / Vibrate / Sound and DND.") { ctx.startActivity(Permissions.dndSettings()) }
        if (needExact) PermCard("Allow exact alarms",
            "So timed routines fire on the exact minute.") {
            ctx.startActivity(Permissions.exactAlarmSettings(ctx))
        }
        if (needWrite) PermCard("Allow modifying system settings",
            "Needed for brightness and auto-rotate.") { ctx.startActivity(Permissions.writeSettings(ctx)) }
        if (needLoc) PermCard("Allow location all the time",
            "Location routines need background access to fire when the app is closed.") {
            ctx.startActivity(Permissions.appDetails(ctx))
        }
        if (needNotif) PermCard("Allow notifications",
            "Get a confirmation when a routine runs.") {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needCall) PermCard("Allow phone calls",
            "One of your routines places a call on its own.") {
            callLauncher.launch(Manifest.permission.CALL_PHONE)
        }
        if (needSms) PermCard("Allow sending SMS",
            "One of your routines sends a text on its own.") {
            smsLauncher.launch(Manifest.permission.SEND_SMS)
        }
        if (needNotifAccess) PermCard("Allow notification access",
            "Needed to trigger on notifications and to reply to them. Find Pixel Routines in the list.") {
            ctx.startActivity(Permissions.notificationAccessSettings())
        }
        if (needCalendar) PermCard("Allow reading your calendar",
            "So routines can follow your timetable.") {
            calLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
        if (needActivity) PermCard("Allow activity detection",
            "So your phone knows when you start driving or walking.") {
            actLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (needUsage) PermCard("Allow usage access",
            "So routines can notice which app you opened. Find Pixel Routines in the list.") {
            ctx.startActivity(Permissions.usageAccessSettings())
        }
    }
}

@Composable
private fun PermCard(title: String, desc: String, onGrant: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(desc, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f))
            }
            TextButton(onClick = onGrant) { Text("Grant", fontWeight = FontWeight.Bold) }
        }
    }
}

// ---- Discover ----

@Composable
private fun DiscoverScreen(onBack: () -> Unit, onPick: (Routine) -> Unit, onScratch: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Ideas") },
            navigationIcon = { IconButton(onClick = onBack) { BackIcon() } })
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedCard(onClick = onScratch, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Start from scratch", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Build your own IF → THEN", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(Presets.all) { p ->
                val r = remember(p) { p.build() }
                ElevatedCard(onClick = { onPick(p.build()) }, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center) {
                            Icon(Ic.of(r.icon), null, Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(p.subtitle, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Add, "Add")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---- Settings ----

@Composable
private fun SettingsScreen(tick: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shizukuStatus = remember(tick) { ShizukuBridge.status(ctx) }
    val shizukuReady = remember(tick) { ShizukuBridge.ready }
    val secureGranted = remember(tick) { Permissions.hasSecureSettings(ctx) }
    val adbCmd = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
    var paused by remember(tick) { mutableStateOf(Store.isPaused(ctx)) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(Store.exportJson(ctx).toByteArray())
                }
            }
            scope.launch { snackbar.showSnackbar("Routines exported") }
        }
    }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val added = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use {
                    Store.importJson(ctx, it.readBytes().decodeToString())
                } ?: 0
            }.getOrDefault(0)
            scope.launch {
                snackbar.showSnackbar(
                    if (added > 0) "Imported $added routine${if (added == 1) "" else "s"}"
                    else "Nothing to import in that file")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { BackIcon() } })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pause all routines", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Nothing fires until you resume. Also available as a Quick Settings tile.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = paused, onCheckedChange = {
                            paused = it; Store.setPaused(ctx, it)
                        })
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Share routines", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Export your routines to a file and send it to friends — they import it here.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                exporter.launch("pixel-routines.json")
                            }) { Text("Export") }
                            FilledTonalButton(onClick = {
                                importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }) { Text("Import") }
                        }
                    }
                }
            }

            item {
                SettingCard(
                    title = "Shizuku",
                    body = "Unlocks the Wi-Fi, Bluetooth and airplane-mode toggles that Android " +
                        "blocks for normal apps. Install Shizuku, start it with wireless debugging, " +
                        "then allow Pixel Routines.",
                    status = shizukuStatus,
                    ok = shizukuReady,
                    buttonText = if (ShizukuBridge.running && !ShizukuBridge.granted) "Allow" else "Open Shizuku",
                ) {
                    if (ShizukuBridge.running && !ShizukuBridge.granted) ShizukuBridge.requestPermission()
                    else ShizukuBridge.openShizuku(ctx)
                }
            }

            item {
                SettingCard(
                    title = "Dark theme & Battery Saver",
                    body = "These need one command from a computer, once. Plug in over USB with " +
                        "USB debugging on, then run it — it sticks forever, even after reboots.",
                    status = if (secureGranted) "Granted" else "Not granted",
                    ok = secureGranted,
                    buttonText = "Copy command",
                ) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("adb", adbCmd))
                    scope.launch { snackbar.showSnackbar("Command copied") }
                }
            }

            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("The command", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(adbCmd, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String, body: String, status: String, ok: Boolean,
    buttonText: String, onClick: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(8.dp))
                    .background(
                        if (ok) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(body, fontSize = 13.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onClick) { Text(buttonText) }
        }
    }
}
