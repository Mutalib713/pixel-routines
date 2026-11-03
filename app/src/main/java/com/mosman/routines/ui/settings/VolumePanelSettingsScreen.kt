package com.mosman.routines.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mosman.routines.Permissions
import com.mosman.routines.volume.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumePanelSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var settings by remember { mutableStateOf(VolumePanelStore.load(ctx)) }
    var hasOverlay by remember { mutableStateOf(Permissions.hasOverlay(ctx)) }

    fun updateSettings(transform: (VolumePanelSettings) -> VolumePanelSettings) {
        val updated = transform(settings)
        settings = updated
        VolumePanelStore.save(ctx, updated)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Volume Panel Triggers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero Intro Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Physical Button Alternative",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Open volume controls via gestures or on-screen shortcuts.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        FilledTonalButton(
                            onClick = {
                                val ok = VolumePanelController.show(ctx, ignoreCooldown = true)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (ok) "System volume panel opened"
                                        else "Could not open volume panel"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test Volume Panel Now")
                        }
                    }
                }
            }

            // Section Header: Activation Methods
            item {
                SettingsSectionHeader("Activation Methods")
            }

            // Trigger 1: Shake Phone Card
            item {
                TriggerCard(
                    icon = Icons.Filled.Vibration,
                    title = "Shake Phone",
                    subtitle = "Deliberately shake your phone twice to open the volume controls.",
                    enabled = settings.shakeEnabled,
                    onEnabledChange = { on ->
                        updateSettings { it.copy(shakeEnabled = on) }
                    },
                    onTest = {
                        val ok = VolumePanelController.show(ctx, ignoreCooldown = true)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Shake trigger tested successfully"
                                else "Test could not show volume panel"
                            )
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sensitivity", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            Sensitivity.entries.forEachIndexed { i, s ->
                                SegmentedButton(
                                    selected = settings.shakeSensitivity == s,
                                    onClick = { updateSettings { it.copy(shakeSensitivity = s) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                                ) {
                                    Text(s.label(), fontSize = 12.sp)
                                }
                            }
                        }
                        Text(
                            text = when (settings.shakeSensitivity) {
                                Sensitivity.LOW -> "Requires firm, deliberate shaking. Best against false triggers."
                                Sensitivity.MEDIUM -> "Balanced for daily usage."
                                Sensitivity.HIGH -> "Responds to lighter shaking."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Trigger 2: Double-Tap Back Card
            item {
                TriggerCard(
                    icon = Icons.Filled.TouchApp,
                    title = "Double-Tap Back",
                    subtitle = "Quickly tap the back of your phone twice to show volume controls.",
                    badge = "Experimental",
                    enabled = settings.backTapEnabled,
                    onEnabledChange = { on ->
                        updateSettings { it.copy(backTapEnabled = on) }
                    },
                    onTest = {
                        val ok = VolumePanelController.show(ctx, ignoreCooldown = true)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Double-tap back trigger tested successfully"
                                else "Test could not show volume panel"
                            )
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sensitivity", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            Sensitivity.entries.forEachIndexed { i, s ->
                                SegmentedButton(
                                    selected = settings.backTapSensitivity == s,
                                    onClick = { updateSettings { it.copy(backTapSensitivity = s) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                                ) {
                                    Text(s.label(), fontSize = 12.sp)
                                }
                            }
                        }

                        // Informative limitation notice
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp).padding(top = 1.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Custom motion sensor detector. Designed for Pixel 6 Pro. Thick cases or heavy phone movement may impact detection consistency.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Trigger 3: Swipe Gesture Card
            item {
                TriggerCard(
                    icon = Icons.Filled.Swipe,
                    title = "Swipe Gesture",
                    subtitle = "Swipe inward from your chosen screen edge to open volume controls.",
                    enabled = settings.swipeEnabled,
                    onEnabledChange = { on ->
                        hasOverlay = Permissions.hasOverlay(ctx)
                        if (on && !hasOverlay) {
                            ctx.startActivity(Permissions.overlaySettings(ctx))
                        }
                        updateSettings { it.copy(swipeEnabled = on) }
                    },
                    onTest = {
                        val ok = VolumePanelController.show(ctx, ignoreCooldown = true)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Swipe gesture tested successfully"
                                else "Test could not show volume panel"
                            )
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!hasOverlay) {
                            OverlayPermissionWarning {
                                ctx.startActivity(Permissions.overlaySettings(ctx))
                            }
                        }

                        Text("Swipe Direction", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SwipeDirection.entries.forEach { dir ->
                                FilterChip(
                                    selected = settings.swipeDirection == dir,
                                    onClick = { updateSettings { it.copy(swipeDirection = dir) } },
                                    label = { Text(dir.label(), fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (dir) {
                                                SwipeDirection.LEFT -> Icons.AutoMirrored.Filled.ArrowForward
                                                SwipeDirection.RIGHT -> Icons.AutoMirrored.Filled.ArrowBack
                                                SwipeDirection.TOP -> Icons.Filled.ArrowDownward
                                                SwipeDirection.BOTTOM -> Icons.Filled.ArrowUpward
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Text(
                            "A subtle edge zone listens for inward swipes without interfering with center screen touch or standard back gestures.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Trigger 4: Floating Volume Button Card
            item {
                TriggerCard(
                    icon = Icons.Filled.ControlPoint,
                    title = "Floating Button",
                    subtitle = "A movable floating shortcut button that stays over applications.",
                    enabled = settings.floatingButtonEnabled,
                    onEnabledChange = { on ->
                        hasOverlay = Permissions.hasOverlay(ctx)
                        if (on && !hasOverlay) {
                            ctx.startActivity(Permissions.overlaySettings(ctx))
                        }
                        updateSettings { it.copy(floatingButtonEnabled = on) }
                    },
                    onTest = {
                        val ok = VolumePanelController.show(ctx, ignoreCooldown = true)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (ok) "Floating button tested successfully"
                                else "Test could not show volume panel"
                            )
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (!hasOverlay) {
                            OverlayPermissionWarning {
                                ctx.startActivity(Permissions.overlaySettings(ctx))
                            }
                        }

                        // Size Selector
                        Text("Button Size", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ButtonSize.entries.forEachIndexed { i, s ->
                                SegmentedButton(
                                    selected = settings.floatingButtonSize == s,
                                    onClick = { updateSettings { it.copy(floatingButtonSize = s) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, ButtonSize.entries.size),
                                ) {
                                    Text(s.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp)
                                }
                            }
                        }

                        // Opacity Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Opacity",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(settings.floatingButtonOpacity * 100).roundToInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = settings.floatingButtonOpacity,
                            onValueChange = { updateSettings { s -> s.copy(floatingButtonOpacity = it) } },
                            valueRange = 0.20f..1.0f,
                            steps = 7,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Position Reset Action
                        OutlinedButton(
                            onClick = {
                                updateSettings { it.copy(floatingButtonX = -1, floatingButtonY = -1) }
                                scope.launch { snackbar.showSnackbar("Floating button position reset to edge") }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset Button Position to Default")
                        }
                    }
                }
            }

            // Section Header: General Configuration
            item {
                SettingsSectionHeader("General Preferences")
            }

            // General Settings Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Cooldown Setting
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Trigger Cooldown", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Minimum delay between volume panel activations",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf(1000L to "1.0s", 1500L to "1.5s", 2000L to "2.0s", 3000L to "3.0s")
                                    .forEachIndexed { i, (duration, label) ->
                                        SegmentedButton(
                                            selected = settings.cooldownDurationMs == duration,
                                            onClick = { updateSettings { it.copy(cooldownDurationMs = duration) } },
                                            shape = SegmentedButtonDefaults.itemShape(i, 4),
                                        ) {
                                            Text(label, fontSize = 12.sp)
                                        }
                                    }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        // Screen-On Only Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Only When Screen is On", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Prevents gestures from firing inside pockets or while device sleeps",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.screenOnOnly,
                                onCheckedChange = { updateSettings { s -> s.copy(screenOnOnly = it) } },
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        // Haptic Feedback Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Haptic Feedback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Provide a subtle click vibration when volume controls open",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.hapticFeedbackEnabled,
                                onCheckedChange = { updateSettings { s -> s.copy(hapticFeedbackEnabled = it) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun TriggerCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Row: Icon, Title, Badge, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (badge != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEAB308).copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCA8A04),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }

                Spacer(Modifier.width(8.dp))
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            // Expanded Controls when enabled
            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onTest) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test Trigger", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayPermissionWarning(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Display over other apps permission required",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                "Android requires this permission to display overlay shortcuts and monitor edge swipes outside this app.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = 15.sp,
            )
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Grant Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
