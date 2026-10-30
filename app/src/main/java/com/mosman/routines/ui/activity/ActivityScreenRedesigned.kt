package com.mosman.routines.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
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
import com.mosman.routines.Ic
import com.mosman.routines.LogEntry
import com.mosman.routines.RunLog
import java.text.DateFormat
import java.util.Date

enum class ActivityTab {
    HISTORY,
    LOGS,
    ERRORS;

    fun label(): String = when (this) {
        HISTORY -> "History"
        LOGS -> "Logs"
        ERRORS -> "Errors"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreenRedesigned(
    tick: Int,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var entries by remember(tick) { mutableStateOf(RunLog.load(ctx)) }
    var selectedTab by remember { mutableStateOf(ActivityTab.HISTORY) }
    var showClearDialog by remember { mutableStateOf(false) }

    val errorEntries = remember(entries) {
        entries.filter { e ->
            e.results.any { res ->
                res.contains("fail", ignoreCase = true) ||
                    res.contains("error", ignoreCase = true) ||
                    res.contains("denied", ignoreCase = true) ||
                    res.contains("missing", ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity", fontWeight = FontWeight.Bold) },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear activity")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            // Segmented Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ActivityTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            val count = when (tab) {
                                ActivityTab.HISTORY -> entries.size
                                ActivityTab.LOGS -> entries.size
                                ActivityTab.ERRORS -> errorEntries.size
                            }
                            Text("${tab.label()} ($count)")
                        },
                    )
                }
            }

            val currentList = when (selectedTab) {
                ActivityTab.HISTORY,
                ActivityTab.LOGS -> entries
                ActivityTab.ERRORS -> errorEntries
            }

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Icon(
                            if (selectedTab == ActivityTab.ERRORS) Icons.Filled.CheckCircle
                            else Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (selectedTab == ActivityTab.ERRORS) Color(0xFF10B981)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == ActivityTab.ERRORS) "No errors detected"
                            else "No activity yet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (selectedTab == ActivityTab.ERRORS) "All routine executions completed without issue."
                            else "Routine executions and logs will appear here.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(currentList) { entry ->
                        if (selectedTab == ActivityTab.LOGS) {
                            DiagnosticLogRow(entry)
                        } else {
                            ExecutionHistoryCard(entry)
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear activity logs?") },
            text = { Text("This will delete all routine execution history.") },
            confirmButton = {
                TextButton(onClick = {
                    RunLog.clear(ctx)
                    entries = emptyList()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ExecutionHistoryCard(entry: LogEntry) {
    val isError = entry.results.any { res ->
        res.contains("fail", ignoreCase = true) ||
            res.contains("error", ignoreCase = true) ||
            res.contains("denied", ignoreCase = true)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isError) MaterialTheme.colorScheme.errorContainer
                        else Color(0xFF10B981).copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isError) Icons.Filled.Error else Ic.of(entry.icon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF10B981),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.at)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${entry.kind.replaceFirstChar { it.uppercase() }} · " +
                        entry.results.joinToString(" · ").ifBlank { "no actions executed" },
                    fontSize = 12.sp,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp,
                )
                Text(
                    text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.at)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticLogRow(entry: LogEntry) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(entry.at))}]",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${entry.name} (${entry.kind})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            entry.results.forEach { res ->
                Text(
                    text = "  → $res",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
