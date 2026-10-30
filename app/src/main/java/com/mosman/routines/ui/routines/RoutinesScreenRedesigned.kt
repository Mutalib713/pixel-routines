package com.mosman.routines.ui.routines

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.Engine
import com.mosman.routines.Routine
import com.mosman.routines.Store
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.ui.components.RoutineCardRedesigned
import com.mosman.routines.ui.components.RoutineProposalCard
import kotlinx.coroutines.launch

enum class RoutineFilter {
    ALL,
    ACTIVE,
    PAUSED,
    RUNNING,
    DRAFTS;

    fun label(): String = when (this) {
        ALL -> "All"
        ACTIVE -> "Active"
        PAUSED -> "Paused"
        RUNNING -> "Running"
        DRAFTS -> "Drafts"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreenRedesigned(
    routines: List<Routine>,
    drafts: List<RoutineDraft>,
    tick: Int,
    onOpenRoutine: (Routine) -> Unit,
    onToggleRoutine: (Routine, Boolean) -> Unit,
    onDuplicateRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
    onOpenCreateOptions: () -> Unit,
    onApproveDraft: (RoutineDraft) -> Unit,
    onRejectDraft: (RoutineDraft) -> Unit,
    onEditDraft: (RoutineDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(RoutineFilter.ALL) }
    val activeIds = remember(tick, routines) { Store.activeIds(ctx) }

    // Filter routines based on filter chip and search query
    val filteredRoutines = remember(routines, searchQuery, selectedFilter, activeIds) {
        routines.filter { r ->
            val matchesFilter = when (selectedFilter) {
                RoutineFilter.ALL -> true
                RoutineFilter.ACTIVE -> r.enabled
                RoutineFilter.PAUSED -> !r.enabled
                RoutineFilter.RUNNING -> activeIds.contains(r.id)
                RoutineFilter.DRAFTS -> false
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                r.name.contains(searchQuery, ignoreCase = true) ||
                    r.ifSummary().contains(searchQuery, ignoreCase = true) ||
                    r.thenSummary().contains(searchQuery, ignoreCase = true)
            }

            matchesFilter && matchesSearch
        }
    }

    val filteredDrafts = remember(drafts, searchQuery, selectedFilter) {
        if (selectedFilter != RoutineFilter.DRAFTS && selectedFilter != RoutineFilter.ALL) {
            emptyList()
        } else {
            drafts.filter { d ->
                if (searchQuery.isBlank()) true else {
                    d.proposedRoutine?.name?.contains(searchQuery, ignoreCase = true) == true ||
                        d.explanation.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Routines", fontWeight = FontWeight.Bold) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenCreateOptions,
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create routine")
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Search Input Field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search routines…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Filter Chips Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(RoutineFilter.entries.toTypedArray()) { filter ->
                        val count = when (filter) {
                            RoutineFilter.ALL -> routines.size
                            RoutineFilter.ACTIVE -> routines.count { it.enabled }
                            RoutineFilter.PAUSED -> routines.count { !it.enabled }
                            RoutineFilter.RUNNING -> activeIds.size
                            RoutineFilter.DRAFTS -> drafts.size
                        }
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text("${filter.label()} ($count)") },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            // Drafts Section if viewing All or Drafts
            if (filteredDrafts.isNotEmpty() && (selectedFilter == RoutineFilter.DRAFTS || selectedFilter == RoutineFilter.ALL)) {
                item {
                    Text(
                        text = "Unsaved Drafts (${filteredDrafts.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(filteredDrafts, key = { it.draftId }) { draft ->
                    RoutineProposalCard(
                        draft = draft,
                        onApprove = onApproveDraft,
                        onReject = onRejectDraft,
                        onEdit = onEditDraft,
                    )
                }
            }

            // Empty State
            if (filteredRoutines.isEmpty() && (selectedFilter != RoutineFilter.DRAFTS || filteredDrafts.isEmpty())) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching routines found"
                            else "No ${selectedFilter.label().lowercase()} routines",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try a different search query"
                            else "Create one with the button below",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Routines List
            if (selectedFilter != RoutineFilter.DRAFTS) {
                items(filteredRoutines, key = { it.id }) { r ->
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
        }
    }
}
