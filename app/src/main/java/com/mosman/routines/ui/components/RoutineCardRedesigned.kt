package com.mosman.routines.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.Ic
import com.mosman.routines.Routine
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RoutineCardRedesigned(
    routine: Routine,
    isRunning: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Column(Modifier.padding(18.dp)) {
            // Header Row: Icon, Title, Running/Status Tag, Overflow Menu & Switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (routine.enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Ic.of(routine.icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (routine.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = routine.name.ifBlank { "Untitled Routine" },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedVisibility(
                        visible = isRunning,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                        exit = scaleOut(),
                    ) {
                        Text(
                            text = "● Running now",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                        )
                    }
                    if (!isRunning) {
                        Text(
                            text = if (routine.enabled) "Active" else "Paused",
                            fontSize = 12.sp,
                            color = if (routine.enabled) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Three-dot overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { showMenu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { showMenu = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }

                Switch(
                    checked = routine.enabled,
                    onCheckedChange = onToggle,
                )
            }

            Spacer(Modifier.height(14.dp))

            // IF & THEN Summary Rows
            SummaryLineBadge("IF", routine.ifSummary(), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            SummaryLineBadge("THEN", routine.thenSummary(), MaterialTheme.colorScheme.tertiary)
            routine.endSummary()?.let { end ->
                Spacer(Modifier.height(6.dp))
                SummaryLineBadge("UNTIL", end, MaterialTheme.colorScheme.secondary)
            }

            // Next Run Indicator
            if (routine.enabled) {
                routine.nextRun()?.let { next ->
                    val now = ZonedDateTime.now()
                    val d = Duration.between(now, next)
                    val rel = when {
                        d.toDays() > 0 -> "${d.toDays()}d ${d.toHours() % 24}h"
                        d.toHours() > 0 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
                        else -> "${maxOf(d.toMinutes(), 1)}m"
                    }
                    val dayName = next.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val timeStr = "%02d:%02d".format(next.hour, next.minute)

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Next: $dayName $timeStr · in $rel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Bottom Actions Row: Run now
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onRunNow,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SummaryLineBadge(tag: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
