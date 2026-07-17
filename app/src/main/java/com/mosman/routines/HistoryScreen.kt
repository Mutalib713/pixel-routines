@file:OptIn(ExperimentalMaterial3Api::class)

package com.mosman.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

/** "What ran when" — the trust screen. Samsung has one; so do we. */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(RunLog.load(ctx)) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("History") },
            navigationIcon = { IconButton(onClick = onBack) { BackIcon() } },
            actions = {
                if (entries.isNotEmpty()) IconButton(onClick = {
                    RunLog.clear(ctx); entries = emptyList()
                }) { Icon(Icons.Filled.Delete, "Clear history") }
            },
        )
    }) { pad ->
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.History, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Nothing yet", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("Every routine run shows up here.", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { e ->
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center) {
                            Icon(Ic.of(e.icon), null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                                    modifier = Modifier.weight(1f))
                                Text(
                                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.at)),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${e.kind} · " + e.results.joinToString(" · ").ifBlank { "no actions" },
                                fontSize = 12.sp, lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.at)),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
