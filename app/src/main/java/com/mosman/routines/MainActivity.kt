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
import com.mosman.routines.ui.lens.RoutineLensScreen
import com.mosman.routines.ui.routines.RoutinesScreenRedesigned
import com.mosman.routines.ui.settings.SettingsScreenRedesigned

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Routines : Screen
    data object Activity : Screen
    data object Assistant : Screen
    data object Discover : Screen
    data object Settings : Screen
    data object Lens : Screen
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

    override fun onResume() {
        super.onResume()
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

    fun reload() {
        routines = Store.load(ctx)
    }

    val isTopLevel = screen is Screen.Home || screen is Screen.Routines ||
        screen is Screen.Activity || screen is Screen.Assistant

    // Back button handling: returns smoothly to Home or previous screen
    BackHandler(enabled = screen !is Screen.Home && screen !is Screen.Onboarding) {
        screen = when (screen) {
            is Screen.Edit -> Screen.Routines
            is Screen.Discover,
            is Screen.Settings,
            is Screen.Lens,
            is Screen.Routines,
            is Screen.Activity,
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
                        selected = screen is Screen.Activity,
                        onClick = { screen = Screen.Activity },
                        icon = { Icon(Icons.Filled.History, contentDescription = "Activity") },
                        label = { Text("Activity") },
                    )
                    NavigationBarItem(
                        selected = screen is Screen.Assistant,
                        onClick = { screen = Screen.Assistant },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "AI") },
                        label = { Text("AI") },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (val s = screen) {
                is Screen.Onboarding -> Onboarding(tick) { screen = Screen.Home }

                is Screen.Home -> HomeScreenRedesigned(
                    routines = routines,
                    tick = tick,
                    onNavigateToRoutines = { screen = Screen.Routines },
                    onNavigateToAi = { screen = Screen.Assistant },
                    onNavigateToLens = { screen = Screen.Lens },
                    onNavigateToDiscover = { screen = Screen.Discover },
                    onNavigateToSettings = { screen = Screen.Settings },
                    onNavigateToActivity = { screen = Screen.Activity },
                    onOpenCreateOptions = { showCreateOptions = true },
                    onOpenRoutine = { screen = Screen.Edit(it) },
                    onToggleRoutine = { r, on -> Store.setEnabled(ctx, r.id, on); reload() },
                    onDuplicateRoutine = { r ->
                        val copy = r.copy(id = System.currentTimeMillis(), name = "${r.name} (Copy)")
                        Store.upsert(ctx, copy)
                        reload()
                    },
                    onDeleteRoutine = { r -> Store.delete(ctx, r.id); reload() },
                )

                is Screen.Routines -> RoutinesScreenRedesigned(
                    routines = routines,
                    drafts = drafts,
                    tick = tick,
                    onOpenRoutine = { screen = Screen.Edit(it) },
                    onToggleRoutine = { r, on -> Store.setEnabled(ctx, r.id, on); reload() },
                    onDuplicateRoutine = { r ->
                        val copy = r.copy(id = System.currentTimeMillis(), name = "${r.name} (Copy)")
                        Store.upsert(ctx, copy)
                        reload()
                    },
                    onDeleteRoutine = { r -> Store.delete(ctx, r.id); reload() },
                    onOpenCreateOptions = { showCreateOptions = true },
                    onApproveDraft = { d ->
                        DraftApprovalManager.approve(ctx, d)
                        drafts.remove(d)
                        reload()
                    },
                    onRejectDraft = { d ->
                        DraftApprovalManager.reject(d)
                        drafts.remove(d)
                    },
                    onEditDraft = { d ->
                        d.proposedRoutine?.let { screen = Screen.Edit(it) }
                    },
                )

                is Screen.Activity -> ActivityScreenRedesigned(tick = tick)

                is Screen.Assistant -> AssistantScreenRedesigned(
                    drafts = drafts,
                    onApproveDraft = { d ->
                        DraftApprovalManager.approve(ctx, d)
                        drafts.remove(d)
                        reload()
                    },
                    onRejectDraft = { d ->
                        DraftApprovalManager.reject(d)
                        drafts.remove(d)
                    },
                    onEditDraft = { d ->
                        d.proposedRoutine?.let { screen = Screen.Edit(it) }
                    },
                    onOpenLens = { screen = Screen.Lens },
                )

                is Screen.Discover -> DiscoverScreen(
                    onBack = { screen = Screen.Home },
                    onPick = { built -> Store.upsert(ctx, built); reload(); screen = Screen.Edit(built) },
                    onScratch = { screen = Screen.Edit(Routine.new()) },
                )

                is Screen.Settings -> SettingsScreenRedesigned(
                    tick = tick,
                    onBack = { screen = Screen.Home },
                )

                is Screen.Lens -> RoutineLensScreen(
                    onBack = { screen = Screen.Home },
                    onDraftCreated = { d ->
                        drafts.add(d)
                        screen = Screen.Routines
                    },
                )

                is Screen.Edit -> EditorScreen(
                    initial = s.routine,
                    onSave = { r -> Store.upsert(ctx, r); reload(); screen = Screen.Routines },
                    onDelete = { r -> Store.delete(ctx, r.id); reload(); screen = Screen.Routines },
                    onBack = { screen = Screen.Routines },
                )
            }
        }
    }

    if (showCreateOptions) {
        CreateRoutineOptionsSheet(
            onDismiss = { showCreateOptions = false },
            onChatWithAi = { screen = Screen.Assistant },
            onFromImage = { screen = Screen.Lens },
            onManualSetup = { screen = Screen.Edit(Routine.new()) },
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
