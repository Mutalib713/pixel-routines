package com.mosman.routines.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.Ic
import com.mosman.routines.drafts.DraftOperation
import com.mosman.routines.drafts.RoutineDraft

@Composable
fun RoutineProposalCard(
    draft: RoutineDraft,
    onApprove: (RoutineDraft) -> Unit,
    onReject: (RoutineDraft) -> Unit,
    onEdit: (RoutineDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val routine = draft.proposedRoutine
    val opColor = when (draft.operation) {
        DraftOperation.CREATE -> MaterialTheme.colorScheme.primary
        DraftOperation.UPDATE -> MaterialTheme.colorScheme.tertiary
        DraftOperation.ENABLE -> Color(0xFF10B981)
        DraftOperation.DISABLE -> MaterialTheme.colorScheme.outline
        DraftOperation.DELETE -> MaterialTheme.colorScheme.error
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Column(Modifier.padding(18.dp)) {
            // Header: Icon + Name + Operation Tag
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(opColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Ic.of(routine?.icon ?: "star"),
                        contentDescription = null,
                        tint = opColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = routine?.name?.ifBlank { "Proposed Routine" } ?: "Routine Action",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = draft.operation.label(),
                        fontSize = 12.sp,
                        color = opColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (draft.explanation.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = draft.explanation,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Routine configuration details if present
            if (routine != null) {
                Spacer(Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProposalDetailRow(
                            label = "WHEN",
                            value = routine.ifSummary(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ProposalDetailRow(
                            label = "THEN",
                            value = routine.thenSummary(),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        routine.endSummary()?.let { end ->
                            ProposalDetailRow(
                                label = "UNTIL",
                                value = end,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            // Warnings section (e.g. Conflict or Permission)
            if (draft.warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    draft.warnings.forEach { warning ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = warning,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // Questions for user if any
            if (draft.questions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                draft.questions.forEach { q ->
                    Text(
                        text = "• $q",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Action Buttons
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onReject(draft) },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject", maxLines = 1, fontSize = 13.sp)
                }

                if (routine != null) {
                    OutlinedButton(
                        onClick = { onEdit(draft) },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", maxLines = 1, fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = { onApprove(draft) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = opColor),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1.2f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (draft.operation == DraftOperation.DELETE) "Confirm" else "Save",
                        maxLines = 1,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposalDetailRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
