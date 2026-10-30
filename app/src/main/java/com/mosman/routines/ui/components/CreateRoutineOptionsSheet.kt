package com.mosman.routines.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoutineOptionsSheet(
    onDismiss: () -> Unit,
    onChatWithAi: () -> Unit,
    onFromImage: () -> Unit,
    onManualSetup: () -> Unit,
    onUseTemplate: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = "Create a Routine",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "How would you like to create your routine?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 1. Chat with Routine AI
            CreationOptionCard(
                title = "Chat with Routine AI",
                subtitle = "Describe what you want in natural language.",
                icon = Icons.Filled.AutoAwesome,
                iconTint = Color.White,
                iconBackground = Brush.linearGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                ),
                onClick = { onDismiss(); onChatWithAi() },
            )

            // 2. From Image (Routine Lens)
            CreationOptionCard(
                title = "From Image",
                subtitle = "Scan a timetable, schedule or any image.",
                icon = Icons.Filled.CameraAlt,
                iconTint = Color.White,
                iconBackground = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                ),
                onClick = { onDismiss(); onFromImage() },
            )

            // 3. Manual Setup
            CreationOptionCard(
                title = "Manual Setup",
                subtitle = "Build your routine step by step.",
                icon = Icons.Filled.Add,
                iconTint = Color.White,
                iconBackground = Brush.linearGradient(
                    colors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                ),
                onClick = { onDismiss(); onManualSetup() },
            )

            // 4. Use a Template
            CreationOptionCard(
                title = "Use a Template",
                subtitle = "Start with a ready-made idea.",
                icon = Icons.Filled.Lightbulb,
                iconTint = Color.White,
                iconBackground = Brush.linearGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                ),
                onClick = { onDismiss(); onUseTemplate() },
            )
        }
    }
}

@Composable
private fun CreationOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Brush,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
