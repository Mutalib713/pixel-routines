@file:OptIn(ExperimentalMaterial3Api::class)

package com.mosman.routines

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.drafts.DraftApprovalManager
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.ui.activity.ActivityScreenRedesigned
import com.mosman.routines.ui.assistant.AssistantScreenRedesigned
import com.mosman.routines.ui.components.CreateRoutineOptionsSheet
import com.mosman.routines.ui.home.HomeScreenRedesigned
import com.mosman.routines.ui.routines.RoutinesScreenRedesigned
import com.mosman.routines.ui.settings.SettingsScreenRedesigned
import com.mosman.routines.ui.settings.VolumePanelSettingsScreen
import com.mosman.routines.volume.VolumePanelStore

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Routines : Screen
    data object Activity : Screen
    data object Assistant : Screen
    data object Discover : Screen
    data object Settings : Screen
    data object VolumePanelTriggers : Screen
    data class Edit(val routine: Routine, val back: Screen = Routines) : Screen
}

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.rearmAll(this)   // also syncs the floating button / swipe overlays
        setContent { PixelRoutinesTheme { AppRoot(tick.intValue) { tick.intValue++ } } }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the "Display over other apps" page: start (or stop) the overlays now
        // instead of waiting for the next cold start.
        VolumePanelStore.syncOverlays(this)
        tick.intValue++
    }
}

@Composable
private fun AppRoot(tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    var screen by remember {
        mutableStateOf<Screen>(if (Store.onboarded(ctx)) Screen.Home else Screen.Onboarding)
    }
    var routines by remember { mutableStateOf(Store.load(ctx)) }
    val drafts = remember { mutableStateListOf<RoutineDraft>() }
    var showCreateOptions by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Routine?>(null) }
    // "Scan / Image" opens the chat with its add sheet already up (layout L1).
    var openAttach by remember { mutableStateOf(false) }
    fun openPicture() { openAttach = true; screen = Screen.Assistant }

    fun reload() {
        routines = Store.load(ctx)
    }

    // Tabs: Home · Routines · Controls · AI. Activity now lives inside Settings.
    val isTopLevel = screen is Screen.Home || screen is Screen.Routines ||
        screen is Screen.VolumePanelTriggers || screen is Screen.Assistant

    // Back button handling: returns smoothly to Home or previous screen
    BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Onboarding) {
        screen = when (screen) {
            is Screen.Edit -> (screen as Screen.Edit).back
            is Screen.Activity -> Screen.Settings
            is Screen.VolumePanelTriggers,
            is Screen.Discover,
            is Screen.Settings,
            is Screen.Routines,
            is Screen.Assistant -> Screen.Home
            else -> Screen.Home
        }
    }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen is Screen.Home,
                        onClick = { screen = Screen.Home },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = screen is Screen.Routines,
                        onClick = { screen = Screen.Routines },
                        icon = { Icon(Icons.Filled.FormatListBulleted, contentDescription = "Routines") },
                        label = { Text("Routines") },
                    )
                    NavigationBarItem(
                        selected = screen is Screen.VolumePanelTriggers,
                        onClick = { screen = Screen.VolumePanelTriggers },
                        icon = { Icon(Icons.Filled.Tune, contentDescription = "Controls") },
                        label = { Text("Controls") },
                    )
                    NavigationBarItem(
                        selected = screen is Screen.Assistant,
                        onClick = { screen = Screen.Assistant },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask") },
                        label = { Text("Ask") },
                    )
                }
            }
        },
    ) { pad ->
        // consumeWindowInsets: screens inside already have the status bar and tabs accounted for,
        // so their own imePadding() lifts the composer only by what the keyboard adds.
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (val s = screen) {
                is Screen.Onboarding -> Onboarding(tick) { screen = Screen.Home }

                is Screen.Home -> HomeScreenRedesigned(
                    routines = routines,
                    tick = tick,
                    onNavigateToRoutines = { screen = Screen.Routines },
                    onNavigateToAi = { screen = Screen.Assistant },
                    onNavigateToLens = { openPicture() },
                    onNavigateToDiscover = { screen = Screen.Discover },
                    onNavigateToSettings = { screen = Screen.Settings },
                    onNavigateToActivity = { screen = Screen.Activity },
                    onOpenCreateOptions = { showCreateOptions = true },
                    onOpenRoutine = { screen = Screen.Edit(it, back = Screen.Home) },
                    onToggleRoutine = { r, on -> Store.setEnabled(ctx, r.id, on); reload() },
                    onDuplicateRoutine = { r ->
                        val copy = r.copy(id = System.currentTimeMillis(), name = "${r.name} (Copy)")
                        Store.upsert(ctx, copy)
                        reload()
                    },
                    onDeleteRoutine = { r -> pendingDelete = r },
                )

                is Screen.Routines -> RoutinesScreenRedesigned(
                    routines = routines,
                    drafts = drafts,
                    tick = tick,
                    onOpenRoutine = { screen = Screen.Edit(it, back = Screen.Routines) },
                    onToggleRoutine = { r, on -> Store.setEnabled(ctx, r.id, on); reload() },
                    onDuplicateRoutine = { r ->
                        val copy = r.copy(id = System.currentTimeMillis(), name = "${r.name} (Copy)")
                        Store.upsert(ctx, copy)
                        reload()
                    },
                    onDeleteRoutine = { r -> pendingDelete = r },
                    onOpenCreateOptions = { showCreateOptions = true },
                    onApproveDraft = { d ->
                        val result = DraftApprovalManager.approve(ctx, d)
                        drafts.remove(d)
                        reload()
                        val msg = when (result) {
                            is com.mosman.routines.drafts.ApprovalResult.Success -> result.message
                            is com.mosman.routines.drafts.ApprovalResult.Error -> "Not saved: " + result.errors.joinToString("; ")
                            is com.mosman.routines.drafts.ApprovalResult.Rejected -> result.reason
                        }
                        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                    },
                    onRejectDraft = { d ->
                        DraftApprovalManager.reject(d)
                        drafts.remove(d)
                    },
                    onEditDraft = { d ->
                        d.proposedRoutine?.let { screen = Screen.Edit(it) }
                    },
                )

                is Screen.Activity -> ActivityScreenRedesigned(tick = tick, onBack = { screen = Screen.Settings })

                is Screen.Assistant -> AssistantScreenRedesigned(
                    drafts = drafts,
                    onApproveDraft = { d ->
                        val result = DraftApprovalManager.approve(ctx, d)
                        drafts.remove(d)
                        reload()
                        result
                    },
                    onRejectDraft = { d ->
                        DraftApprovalManager.reject(d)
                        drafts.remove(d)
                    },
                    onEditDraft = { d ->
                        d.proposedRoutine?.let { screen = Screen.Edit(it, back = Screen.Assistant) }
                    },
                    onOpenLens = { openPicture() },
                    openAttachSheet = openAttach,
                    onAttachSheetOpened = { openAttach = false },
                )

                is Screen.Discover -> DiscoverScreen(
                    onBack = { screen = Screen.Home },
                    // Used to save the idea the moment it was tapped, so backing out still left it saved.
                    onPick = { built -> screen = Screen.Edit(built, back = Screen.Discover) },
                    onScratch = { screen = Screen.Edit(Routine.new(), back = Screen.Discover) },
                )

                is Screen.Settings -> SettingsScreenRedesigned(
                    tick = tick,
                    onBack = { screen = Screen.Home },
                    onNavigateToVolumeTriggers = { screen = Screen.VolumePanelTriggers },
                    onNavigateToActivity = { screen = Screen.Activity },
                )

                // A tab now, so no back arrow.
                is Screen.VolumePanelTriggers -> VolumePanelSettingsScreen(tick = tick, onBack = null)

                is Screen.Edit -> EditorScreen(
                    initial = s.routine,
                    onSave = { r -> Store.upsert(ctx, r); reload(); screen = Screen.Routines },
                    onDelete = { r -> pendingDelete = r },
                    onBack = { screen = s.back },
                )
            }
        }
    }

    // One confirmation for every Delete (routine menus and the editor). Deleting used to happen
    // on a single tap with no way back.
    pendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${r.name.trim()}”?") },
            text = {
                Text(if (Store.isActive(ctx, r.id)) "It's running now. Deleting it ends it first and puts your settings back. This can't be undone."
                    else "This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.delete(ctx, r.id); reload(); pendingDelete = null
                    if (screen is Screen.Edit) screen = Screen.Routines
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep it") } },
        )
    }

    if (showCreateOptions) {
        CreateRoutineOptionsSheet(
            onDismiss = { showCreateOptions = false },
            onChatWithAi = { screen = Screen.Assistant },
            onFromImage = { openPicture() },
            onManualSetup = { val from = screen; screen = Screen.Edit(Routine.new(), back = from) },
            onUseTemplate = { screen = Screen.Discover },
        )
    }
}

// ---- Discover (Ideas & Presets) ----

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
                OutlinedCard(
                    onClick = onScratch,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Start from scratch", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                "Build your own IF → THEN routine",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(Presets.all) { p ->
                val r = remember(p) { p.build() }
                ElevatedCard(
                    onClick = { onPick(p.build()) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Ic.of(r.icon),
                                null,
                                Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                p.subtitle,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.Add, "Add")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
