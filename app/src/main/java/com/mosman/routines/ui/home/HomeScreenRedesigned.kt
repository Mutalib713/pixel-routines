package com.mosman.routines.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mosman.routines.Ic
import com.mosman.routines.Routine
import com.mosman.routines.Store
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Home, layout A (picked by Mutalib 2026-11-20) plus the quick actions he asked to keep:
 * the next run is the biggest thing on screen, asking the assistant is one bar, and routines
 * are plain rows with a switch. Colours come only from the Material You scheme.
 */
private object Sp {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 28.dp
}
private object Shp {
    val pill = RoundedCornerShape(28.dp)
    val hero = RoundedCornerShape(28.dp)
    val tile = RoundedCornerShape(20.dp)
    val icon = RoundedCornerShape(12.dp)
}

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
    val running = remember(tick, routines) { Store.activeIds(ctx) }
    var paused by remember(tick) { mutableStateOf(Store.isPaused(ctx)) }

    val greeting = remember(tick) {
        val h = LocalTime.now().hour
        when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
    }
    val next = remember(routines, tick) {
        routines.filter { it.enabled }.mapNotNull { r -> r.nextRun()?.let { r to it } }.minByOrNull { it.second }
    }
    // On first, then by when they next run, then by name.
    val sorted = remember(routines, tick) {
        routines.sortedWith(compareByDescending<Routine> { it.enabled }
            .thenBy { it.nextRun()?.toEpochSecond() ?: Long.MAX_VALUE }
            .thenBy { it.name.lowercase() })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Sp.l, end = Sp.l, top = Sp.s, bottom = Sp.xxl),
        verticalArrangement = Arrangement.spacedBy(Sp.m),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$greeting, Mutalib",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }

        item {
            Surface(onClick = onNavigateToAi, shape = Shp.pill, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(start = Sp.l, end = Sp.xs).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Sp.m))
                    Text("Ask Routine AI", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = onNavigateToLens) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = "Make a routine from a picture")
                    }
                }
            }
        }

        item {
            NextUpCard(
                paused = paused,
                next = next,
                anyOn = routines.any { it.enabled },
                hasRoutines = routines.isNotEmpty(),
                onResume = { Store.setPaused(ctx, false); paused = false },
                onOpen = { next?.first?.let(onOpenRoutine) },
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                QuickAction(Icons.Outlined.Add, "New routine", Modifier.weight(1f), onOpenCreateOptions)
                QuickAction(Icons.Outlined.PhotoCamera, "From a picture", Modifier.weight(1f), onNavigateToLens)
                QuickAction(Icons.Outlined.Lightbulb, "Ideas", Modifier.weight(1f), onNavigateToDiscover)
                QuickAction(
                    if (paused) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle,
                    if (paused) "Resume all" else "Pause all",
                    Modifier.weight(1f),
                ) {
                    Store.setPaused(ctx, !paused); paused = !paused
                }
            }
        }

        item {
            Row(Modifier.padding(top = Sp.s), verticalAlignment = Alignment.CenterVertically) {
                Text("Your routines", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
                if (routines.size > 5) TextButton(onClick = onNavigateToRoutines) { Text("See all ${routines.size}") }
            }
        }

        if (routines.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Make one yourself, start from an idea, or just tell Routine AI what you want.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(sorted.take(5), key = { it.id }) { r ->
                RoutineRow(r, isRunning = r.id in running, onOpen = { onOpenRoutine(r) }, onToggle = { onToggleRoutine(r, it) })
            }
        }
    }
}

@Composable
private fun NextUpCard(
    paused: Boolean,
    next: Pair<Routine, ZonedDateTime>?,
    anyOn: Boolean,
    hasRoutines: Boolean,
    onResume: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        enabled = next != null && !paused,
        shape = Shp.hero,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(Sp.xl)) {
            Text(
                if (paused) "PAUSED" else "NEXT UP",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Sp.xs))
            when {
                paused -> {
                    Text("All routines are paused", style = MaterialTheme.typography.headlineSmall)
                    Text("Nothing runs until you resume.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Sp.m))
                    Button(onClick = onResume) { Text("Resume") }
                }
                next != null -> {
                    val (r, at) = next
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%02d:%02d".format(at.hour, at.minute), style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.width(Sp.s))
                        Text(dayLabel(at), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Sp.s))
                    }
                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        r.actions.joinToString(", ") { it.describe() } +
                            (r.endTriggers.firstOrNull()?.let { " · until ${it.describe().removePrefix("At ")}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(inHowLong(at), style = MaterialTheme.typography.bodySmall)
                }
                anyOn -> {
                    Text("Waiting for a trigger", style = MaterialTheme.typography.headlineSmall)
                    Text("Your routines that are on run when their moment comes, like plugging in the charger.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                hasRoutines -> {
                    Text("Nothing is switched on", style = MaterialTheme.typography.headlineSmall)
                    Text("Turn a routine on below and its next run shows here.", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    Text("No routines yet", style = MaterialTheme.typography.headlineSmall)
                    Text("Your next run shows here once you've made one.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun dayLabel(at: ZonedDateTime): String {
    val today = ZonedDateTime.now().toLocalDate()
    return when (at.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }
}

private fun inHowLong(at: ZonedDateTime): String {
    val d = Duration.between(ZonedDateTime.now(), at)
    val h = d.toHours(); val m = d.toMinutes() % 60
    return when {
        d.isNegative -> "now"
        h >= 24 -> "in ${h / 24} day${if (h / 24 == 1L) "" else "s"}"
        h > 0 -> "in $h h ${m} min"
        else -> "in $m min"
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = Shp.tile, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(
            Modifier.padding(vertical = Sp.m, horizontal = Sp.xs).heightIn(min = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Sp.xs))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoutineRow(r: Routine, isRunning: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shp.tile).clickable(onClick = onOpen).padding(vertical = Sp.s, horizontal = Sp.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(Shp.icon).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.of(r.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.width(Sp.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (isRunning) {
                    Spacer(Modifier.width(Sp.s))
                    Text("Running", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                (r.triggers.firstOrNull()?.describe() ?: "No trigger") +
                    (if (r.triggers.size > 1) " (+${r.triggers.size - 1})" else "") +
                    " → " + r.actions.joinToString(", ") { it.describe() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Sp.s))
        Switch(checked = r.enabled, onCheckedChange = onToggle)
    }
}
