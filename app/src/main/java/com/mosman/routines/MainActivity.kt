@file:OptIn(ExperimentalMaterial3Api::class)

package com.mosman.routines

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
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
    data object Home : Screen
    data object Discover : Screen
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
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var routines by remember { mutableStateOf(Store.load(ctx)) }
    fun reload() { routines = Store.load(ctx) }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            routines = routines,
            tick = tick,
            onRefreshPerms = refresh,
            onOpenDiscover = { screen = Screen.Discover },
            onNew = { screen = Screen.Edit(Routine.new()) },
            onOpen = { screen = Screen.Edit(it) },
            onToggle = { r, on -> Store.setEnabled(ctx, r.id, on); reload() },
            onRunNow = { r -> Engine.runNow(ctx, r) },
        )
        is Screen.Discover -> DiscoverScreen(
            onBack = { screen = Screen.Home },
            onPick = { built -> Store.upsert(ctx, built); reload(); screen = Screen.Edit(built) },
            onScratch = { screen = Screen.Edit(Routine.new()) },
        )
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
    onNew: () -> Unit,
    onOpen: (Routine) -> Unit,
    onToggle: (Routine, Boolean) -> Unit,
    onRunNow: (Routine) -> List<String>,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Pixel Routines", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onOpenDiscover) {
                        Text("💡", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp)); Text("Ideas")
                    }
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionArea(routines, tick, onRefreshPerms) }
            if (routines.isEmpty()) item { EmptyState(onOpenDiscover) }
            items(routines, key = { it.id }) { r ->
                RoutineCard(r,
                    onClick = { onOpen(r) },
                    onToggle = { onToggle(r, it) },
                    onRunNow = {
                        val res = onRunNow(r)
                        scope.launch {
                            snackbar.showSnackbar(if (res.isEmpty()) "No actions" else res.joinToString(" · "))
                        }
                    })
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun RoutineCard(r: Routine, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onRunNow: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(r.emoji, fontSize = 22.sp) }
                Spacer(Modifier.width(12.dp))
                Text(r.name.ifBlank { "Untitled" }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Switch(checked = r.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(12.dp))
            SummaryLine("IF", r.ifSummary(), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            SummaryLine("THEN", r.thenSummary(), MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(4.dp))
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
            Modifier.clip(RoundedCornerShape(6.dp)).background(tagColor.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tagColor) }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyState(onOpenDiscover: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✨", fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text("Let your phone run itself", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Create a routine that changes settings on a schedule, or when something happens.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onOpenDiscover) {
                Text("💡", fontSize = 16.sp)
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
    val needNotif = remember(tick) {
        Build.VERSION.SDK_INT >= 33 && !Permissions.hasNotifications(ctx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (needDnd) PermCard("Allow Do Not Disturb access",
            "Unlocks Silent / Vibrate / Sound and DND. Choose Pixel Routines, then allow.") {
            ctx.startActivity(Permissions.dndSettings())
        }
        if (needExact) PermCard("Allow exact alarms",
            "So timed routines fire on the exact minute.") {
            ctx.startActivity(Permissions.exactAlarmSettings(ctx))
        }
        if (needWrite) PermCard("Allow modifying system settings",
            "Needed for brightness and auto-rotate.") {
            ctx.startActivity(Permissions.writeSettings(ctx))
        }
        if (needNotif) PermCard("Allow notifications",
            "Get a confirmation when a routine runs.") {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun PermCard(title: String, desc: String, onGrant: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
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
        TopAppBar(
            title = { Text("Ideas") },
            navigationIcon = { IconButton(onClick = onBack) { BackIcon() } },
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedCard(onClick = onScratch, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✏️", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
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
                            contentAlignment = Alignment.Center) { Text(r.emoji, fontSize = 22.sp) }
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
