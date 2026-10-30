package com.mosman.routines.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.Engine
import com.mosman.routines.Ic
import com.mosman.routines.LogEntry
import com.mosman.routines.Permissions
import com.mosman.routines.Routine
import com.mosman.routines.RunLog
import com.mosman.routines.Store
import com.mosman.routines.ui.components.RoutineCardRedesigned
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenRedesigned(
    routines: List<Routine>,
    tick: Int,
    onNavigateToRoutines: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToLens: () -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onOpenCreateOptions: () -> Unit,
    onOpenRoutine: (Routine) -> Unit,
    onToggleRoutine: (Routine, Boolean) -> Unit,
    onDuplicateRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val activeIds = remember(tick, routines) { Store.activeIds(ctx) }
    var paused by remember(tick) { mutableStateOf(Store.isPaused(ctx)) }
    val recentRuns = remember(tick) { RunLog.load(ctx).take(3) }

    // Dynamic greeting based on time of day
    val greeting = remember {
        val hour = LocalTime.now().hour
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Active routines count & Next scheduled routine calculation
    val enabledRoutines = remember(routines) { routines.filter { it.enabled } }
    val nextUpcoming = remember(enabledRoutines, tick) {
        enabledRoutines
            .mapNotNull { r -> r.nextRun()?.let { next -> r to next } }
            .minByOrNull { it.second }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Pixel Routines",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // User Greeting Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "$greeting, Mutalib",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (paused) "All automations are currently paused"
                            else "Your routines are keeping you on track.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onNavigateToSettings() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "M",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // Paused Alert Banner
            if (paused) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.PauseCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Automations paused",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Button(
                                onClick = {
                                    Store.setPaused(ctx, false)
                                    paused = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Active Summary Banner (Matching Screen 1)
            item {
                ActiveSummaryBanner(
                    activeCount = enabledRoutines.size,
                    runningCount = activeIds.size,
                    nextRoutine = nextUpcoming?.first?.name,
                    nextTime = nextUpcoming?.second,
                    onClick = onNavigateToRoutines,
                )
            }

            // Quick Actions 4-Grid
            item {
                Text(
                    text = "Quick Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickActionTile(
                        title = "Create\nRoutine",
                        icon = Icons.Filled.Add,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCreateOptions,
                    )
                    QuickActionTile(
                        title = "Routine\nAI",
                        icon = Icons.Filled.AutoAwesome,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAi,
                    )
                    QuickActionTile(
                        title = "Scan /\nImage",
                        icon = Icons.Filled.CameraAlt,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToLens,
                    )
                    QuickActionTile(
                        title = "Browse\nIdeas",
                        icon = Icons.Filled.Lightbulb,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToDiscover,
                    )
                }
            }

            // Upcoming Routines Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Upcoming",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onNavigateToRoutines) {
                        Text("See all", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (routines.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No routines yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create your first routine or choose a template to automate your day.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            FilledTonalButton(onClick = onOpenCreateOptions) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create routine")
                            }
                        }
                    }
                }
            } else {
                // Show up to 3 upcoming routines
                val topUpcoming = routines.take(3)
                items(topUpcoming.size) { idx ->
                    val r = topUpcoming[idx]
                    RoutineCardRedesigned(
                        routine = r,
                        isRunning = activeIds.contains(r.id),
                        onClick = { onOpenRoutine(r) },
                        onToggle = { onToggleRoutine(r, it) },
                        onRunNow = {
                            scope.launch { snackbar.showSnackbar("Running “${r.name}”…") }
                            Engine.runNow(ctx, r) { res ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (res.isEmpty()) "Executed successfully"
                                        else res.joinToString(" · "),
                                    )
                                }
                            }
                        },
                        onEdit = { onOpenRoutine(r) },
                        onDuplicate = { onDuplicateRoutine(r) },
                        onDelete = { onDeleteRoutine(r) },
                    )
                }
            }

            // Recent Activity Section
            if (recentRuns.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent Activity",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onNavigateToActivity) {
                            Text("History", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                items(recentRuns.size) { idx ->
                    val entry = recentRuns[idx]
                    RecentActivityRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ActiveSummaryBanner(
    activeCount: Int,
    runningCount: Int,
    nextRoutine: String?,
    nextTime: ZonedDateTime?,
    onClick: () -> Unit,
) {
    val emerald = Color(0xFF10B981)
    val emeraldContainer = Color(0xFFD1FAE5)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = emeraldContainer.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = emerald.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(emerald.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = emerald,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "$activeCount routines active" + (if (runningCount > 0) " · $runningCount running" else ""),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                if (nextRoutine != null && nextTime != null) {
                    val d = Duration.between(ZonedDateTime.now(), nextTime)
                    val rel = when {
                        d.toDays() > 0 -> "${d.toDays()}d ${d.toHours() % 24}h"
                        d.toHours() > 0 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
                        else -> "${maxOf(d.toMinutes(), 1)} min"
                    }
                    Text(
                        text = "Next: $nextRoutine in $rel",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "No upcoming scheduled events",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 10.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentActivityRow(entry: LogEntry) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Ic.of(entry.icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.results.joinToString(" · ").ifBlank { "Executed successfully" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
