package com.mosman.routines.ui.lens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.Action
import com.mosman.routines.RingerMode
import com.mosman.routines.Routine
import com.mosman.routines.Trigger
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineDraft
import com.mosman.routines.ui.components.RoutineProposalCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineLensScreen(
    onBack: () -> Unit,
    onDraftCreated: (RoutineDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var detectedDraft by remember { mutableStateOf<RoutineDraft?>(null) }

    fun loadSampleTimetable() {
        val routine = Routine(
            id = System.currentTimeMillis(),
            name = "KNUST Lecture Mode",
            icon = "school",
            triggers = listOf(
                Trigger.TimeOfDay(hour = 8, minute = 0, days = setOf(1, 2, 3, 4, 5)),
            ),
            actions = listOf(
                Action.Ringer(RingerMode.VIBRATE),
                Action.Dnd(on = true),
            ),
            endTriggers = listOf(
                Trigger.TimeOfDay(hour = 17, minute = 0, days = setOf(1, 2, 3, 4, 5)),
            ),
        )

        detectedDraft = RoutineDraft(
            draftId = "lens-${System.currentTimeMillis()}",
            operation = DraftOperation.CREATE,
            proposedRoutine = routine,
            explanation = "Extracted from KNUST Class Timetable (12 recurring classes detected across weekdays).",
            requiresApproval = true,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routine Lens", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (detectedDraft != null) {
                Text(
                    text = "Timetable Detected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                RoutineProposalCard(
                    draft = detectedDraft!!,
                    onApprove = { draft ->
                        onDraftCreated(draft)
                        onBack()
                    },
                    onReject = {
                        detectedDraft = null
                    },
                    onEdit = { draft ->
                        onDraftCreated(draft)
                        onBack()
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Scan or Import Timetable",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Capture a lecture schedule, work shift, or exam timetable to create automated routines.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { loadSampleTimetable() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test Sample")
                    }

                    OutlinedButton(
                        onClick = { loadSampleTimetable() },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Image")
                    }
                }
            }
        }
    }
}
