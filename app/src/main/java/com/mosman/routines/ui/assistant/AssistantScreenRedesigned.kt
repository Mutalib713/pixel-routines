package com.mosman.routines.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Store
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineConflictAnalyzer
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.drafts.RoutineDraftValidator
import com.mosman.routines.ui.components.RoutineProposalCard
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val draft: RoutineDraft? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreenRedesigned(
    drafts: List<RoutineDraft>,
    onApproveDraft: (RoutineDraft) -> Unit,
    onRejectDraft: (RoutineDraft) -> Unit,
    onEditDraft: (RoutineDraft) -> Unit,
    onOpenLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Hi Mutalib! I'm Routine AI. Describe what you'd like your phone to do, or try one of the suggestions below.",
            )
        )
    }

    val suggestedPrompts = listOf(
        "Create a study routine",
        "Automate my timetable",
        "Save battery automatically",
        "Create a bedtime routine",
        "Silence phone at KNUST",
    )

    fun handleUserPrompt(promptText: String) {
        if (promptText.isBlank()) return
        messages.add(ChatMessage(isUser = true, text = promptText))

        val lower = promptText.lowercase()
        val proposedRoutine: Routine
        val explanation: String

        when {
            lower.contains("study") -> {
                proposedRoutine = Routine(
                    id = System.currentTimeMillis(),
                    name = "Study Focus",
                    icon = "school",
                    triggers = listOf(Trigger.TimeOfDay(hour = 14, minute = 0, days = setOf(1, 2, 3, 4, 5))),
                    actions = listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true)),
                )
                explanation = "Activates at 2:00 PM on weekdays. Switches ringer to Vibrate and turns on Do Not Disturb."
            }

            lower.contains("bedtime") || lower.contains("night") -> {
                proposedRoutine = Routine(
                    id = System.currentTimeMillis(),
                    name = "Night Routine",
                    icon = "bedtime",
                    triggers = listOf(Trigger.TimeOfDay(hour = 22, minute = 30, days = setOf(1, 2, 3, 4, 5, 6, 7))),
                    actions = listOf(Action.DarkTheme(true), Action.Dnd(true), Action.Brightness(15)),
                )
                explanation = "Activates at 10:30 PM every night. Enables dark theme, Do Not Disturb, and reduces brightness to 15%."
            }

            lower.contains("battery") -> {
                proposedRoutine = Routine(
                    id = System.currentTimeMillis(),
                    name = "Battery Saver Auto",
                    icon = "saver",
                    triggers = listOf(Trigger.Battery(below = true, level = 20)),
                    actions = listOf(Action.BatterySaver(true), Action.Brightness(25)),
                )
                explanation = "Activates when battery drops below 20%. Automatically turns on Battery Saver and dims brightness."
            }

            lower.contains("timetable") || lower.contains("knust") || lower.contains("lecture") || lower.contains("class") -> {
                proposedRoutine = Routine(
                    id = System.currentTimeMillis(),
                    name = "Lecture Mode",
                    icon = "school",
                    triggers = listOf(Trigger.TimeOfDay(hour = 10, minute = 0, days = setOf(1, 2, 3, 4, 5))),
                    actions = listOf(Action.Ringer(RingerMode.VIBRATE), Action.Dnd(true)),
                )
                explanation = "Silences the phone during morning classes (Mon–Fri 10:00 AM). Switches ringer to Vibrate and turns on DND."
            }

            else -> {
                proposedRoutine = Routine(
                    id = System.currentTimeMillis(),
                    name = promptText.trim().take(30),
                    icon = "star",
                    triggers = listOf(Trigger.Screen(true)),
                    actions = listOf(Action.Notify("Pixel Routines", "Routine activated")),
                )
                explanation = "Custom routine proposed from your description."
            }
        }

        // Run validation & conflict analysis against existing routines
        val savedRoutines = Store.load(ctx)
        val conflicts = RoutineConflictAnalyzer.analyze(proposedRoutine, savedRoutines)
        val warnings = conflicts.map { it.message }

        val draft = RoutineDraft(
            draftId = "draft-${System.currentTimeMillis()}",
            operation = DraftOperation.CREATE,
            proposedRoutine = proposedRoutine,
            explanation = explanation,
            warnings = warnings,
            requiresApproval = true,
        )

        messages.add(
            ChatMessage(
                isUser = false,
                text = "Here is your routine proposal. Please review it and confirm before saving:",
                draft = draft,
            )
        )

        scope.launch {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
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
                        Column {
                            Text("Routine AI", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Local Engine · Offline Ready",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp), // Room for bottom nav bar
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenLens) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Routine Lens",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Ask or describe a routine…") },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = {
                            val text = input
                            input = ""
                            handleUserPrompt(text)
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Suggested Prompts
            item {
                Text(
                    text = "Suggested automations:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestedPrompts.forEach { prompt ->
                        SuggestionChip(
                            onClick = { handleUserPrompt(prompt) },
                            label = { Text(prompt, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            // Message Thread
            items(messages, key = { it.id }) { msg ->
                if (msg.isUser) {
                    UserMessageBubble(msg.text)
                } else {
                    AssistantMessageBubble(
                        text = msg.text,
                        draft = msg.draft,
                        onApprove = { draft ->
                            onApproveDraft(draft)
                            messages.add(
                                ChatMessage(
                                    isUser = false,
                                    text = "Great! Routine “${draft.proposedRoutine?.name}” has been saved and scheduled.",
                                )
                            )
                        },
                        onReject = { draft ->
                            onRejectDraft(draft)
                            messages.add(
                                ChatMessage(
                                    isUser = false,
                                    text = "Proposal discarded. Let me know if you want to try something else.",
                                )
                            )
                        },
                        onEdit = onEditDraft,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    text: String,
    draft: RoutineDraft?,
    onApprove: (RoutineDraft) -> Unit,
    onReject: (RoutineDraft) -> Unit,
    onEdit: (RoutineDraft) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }

        if (draft != null) {
            Spacer(Modifier.height(10.dp))
            RoutineProposalCard(
                draft = draft,
                onApprove = onApprove,
                onReject = onReject,
                onEdit = onEdit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
