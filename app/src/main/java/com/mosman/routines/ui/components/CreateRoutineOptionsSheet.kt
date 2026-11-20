package com.mosman.routines.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * "How do you want to start?" sheet shown before a new routine.
 *
 * Same grouped-list look as Settings and the editor's bands: one rounded group, rows split by
 * thin gaps, icons on the theme's primary container. It used to have four hard-coded gradient
 * circles (blue, green, blue, amber) that ignored the wallpaper colours the rest of the app uses.
 */
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Text("New routine", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 8.dp))
            Text("How do you want to start?", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp))

            Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Option(Icons.Outlined.AutoAwesome, "Ask Routines",
                    "Describe it in your own words") { onDismiss(); onChatWithAi() }
                Option(Icons.Outlined.DocumentScanner, "Scan a file or image",
                    "A photo, screenshot, PDF or text file") { onDismiss(); onFromImage() }
                Option(Icons.Outlined.Add, "Start from scratch",
                    "Pick the WHEN and THEN yourself") { onDismiss(); onManualSetup() }
                Option(Icons.Outlined.Lightbulb, "Pick an idea",
                    "Ready-made routines you can change") { onDismiss(); onUseTemplate() }
            }
        }
    }
}

@Composable
private fun Option(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
