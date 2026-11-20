package com.mosman.routines.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mosman.routines.GestureType
import com.mosman.routines.Ic
import com.mosman.routines.Permissions
import com.mosman.routines.Routine
import com.mosman.routines.Store
import com.mosman.routines.Trigger
import com.mosman.routines.volume.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Controls: one place for every gesture and on-screen button, each doing the job you pick.
 *
 * Layout picked 2026-11-20 ("A + B combined"): a Pixel-Settings-style list (A) where a gesture
 * that's switched on opens its job chips underneath (B), so what each gesture does is visible
 * without opening anything. Tapping a chip opens a sheet of jobs. Before this, the screen was
 * volume-panel only ("Physical Button Alternative"), so users didn't know gestures could do more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumePanelSettingsScreen(
    tick: Int,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var settings by remember { mutableStateOf(VolumePanelStore.load(ctx)) }
    var hasOverlay by remember { mutableStateOf(Permissions.hasOverlay(ctx)) }
    LaunchedEffect(tick) { hasOverlay = Permissions.hasOverlay(ctx) }
    val routines = remember(tick) { Store.load(ctx) }

    fun update(transform: (VolumePanelSettings) -> VolumePanelSettings) {
        settings = transform(settings)
        VolumePanelStore.save(ctx, settings)
    }

    /** Switching on something that draws over other apps sends you to grant that first. */
    fun needsOverlay(on: Boolean) {
        hasOverlay = Permissions.hasOverlay(ctx)
        if (on && !hasOverlay) ctx.startActivity(Permissions.overlaySettings(ctx))
    }

    var picking by remember { mutableStateOf<JobPick?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (onBack != null) TopAppBar(
                title = { Text("Controls") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        LazyColumn(
            modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
        ) {
            if (onBack == null) item {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                    Text("Controls", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Gestures and buttons that work anywhere on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val overlayUsed = settings.swipeEnabled || settings.floatingButtonEnabled
            if (overlayUsed && !hasOverlay) item {
                Notice(
                    "Swipe and the floating button need “Display over other apps”.",
                    "Allow",
                ) { ctx.startActivity(Permissions.overlaySettings(ctx)) }
            }

            // ---------------- Gestures
            item { SectionLabel("Gestures") }
            item {
                Group {
                    GestureRow(
                        Icons.Outlined.Vibration, "Shake", sensitivityText(settings.shakeSensitivity),
                        settings.shakeEnabled, { on -> update { it.copy(shakeEnabled = on) } },
                    ) {
                        JobLine("Does", settings.shakeJob, routines) {
                            picking = JobPick("Shake", settings.shakeJob) { j -> update { it.copy(shakeJob = j) } }
                        }
                        SensitivityChips(settings.shakeSensitivity) { v -> update { it.copy(shakeSensitivity = v) } }
                        AlsoUsedBy(routines, "Shake") { it is Trigger.Gesture && it.type == GestureType.SHAKE }
                    }
                    GestureRow(
                        Icons.Outlined.TouchApp, "Double-tap the back",
                        "${sensitivityText(settings.backTapSensitivity)} · may miss light taps",
                        settings.backTapEnabled, { on -> update { it.copy(backTapEnabled = on) } },
                    ) {
                        JobLine("Does", settings.backTapJob, routines) {
                            picking = JobPick("Double-tap the back", settings.backTapJob) { j -> update { it.copy(backTapJob = j) } }
                        }
                        SensitivityChips(settings.backTapSensitivity) { v -> update { it.copy(backTapSensitivity = v) } }
                        AlsoUsedBy(routines, "double-tap") { it is Trigger.Gesture && it.type == GestureType.BACK_TAP }
                    }
                    GestureRow(
                        Icons.Outlined.Flip, "Flip face down", "When the phone is placed screen-down",
                        settings.flipEnabled, { on -> update { it.copy(flipEnabled = on) } },
                    ) {
                        JobLine("Does", settings.flipJob, routines) {
                            picking = JobPick("Flip face down", settings.flipJob) { j -> update { it.copy(flipJob = j) } }
                        }
                        AlsoUsedBy(routines, "face-down") { it is Trigger.Gesture && it.type == GestureType.FLIP_DOWN }
                    }
                    GestureRow(
                        Icons.Outlined.Swipe, "Swipe in from the edge", settings.swipeDirection.shortLabel(),
                        settings.swipeEnabled, { on -> needsOverlay(on); update { it.copy(swipeEnabled = on) } },
                    ) {
                        JobLine("Does", settings.swipeJob, routines) {
                            picking = JobPick("Swipe in", settings.swipeJob) { j -> update { it.copy(swipeJob = j) } }
                        }
                        ChipRow("Edge") {
                            SwipeDirection.entries.forEach { d ->
                                FilterChip(selected = settings.swipeDirection == d,
                                    onClick = { update { it.copy(swipeDirection = d) } },
                                    label = { Text(d.shortLabel().removeSuffix(" edge")) })
                            }
                        }
                        AlsoUsedBy(routines, "this edge") { it is Trigger.Swipe && it.direction == settings.swipeDirection }
                    }
                }
            }

            // ---------------- On screen
            item { SectionLabel("On screen") }
            item {
                Group {
                    GestureRow(
                        Icons.Outlined.RadioButtonChecked, "Floating button",
                        "${settings.floatingButtonSize.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                            "${(settings.floatingButtonOpacity * 100).roundToInt()}% visible",
                        settings.floatingButtonEnabled,
                        { on -> needsOverlay(on); update { it.copy(floatingButtonEnabled = on) } },
                    ) {
                        JobLine("Tap", settings.floatingTapJob, routines) {
                            picking = JobPick("Tapping the button", settings.floatingTapJob) { j -> update { it.copy(floatingTapJob = j) } }
                        }
                        val dbl = ControlJob(settings.floatingDoubleTapAction, settings.floatingDoubleTapRoutineId)
                        JobLine("Double-tap", dbl, routines) {
                            picking = JobPick("Double-tapping the button", dbl) { j ->
                                update { it.copy(floatingDoubleTapAction = j.action, floatingDoubleTapRoutineId = j.routineId) }
                            }
                        }
                        val hold = ControlJob(settings.floatingLongPressAction, settings.floatingLongPressRoutineId)
                        JobLine("Hold", hold, routines) {
                            picking = JobPick("Holding the button", hold) { j ->
                                update { it.copy(floatingLongPressAction = j.action, floatingLongPressRoutineId = j.routineId) }
                            }
                        }
                        ChipRow("Size") {
                            ButtonSize.entries.forEach { s ->
                                FilterChip(selected = settings.floatingButtonSize == s,
                                    onClick = { update { it.copy(floatingButtonSize = s) } },
                                    label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Text("How visible: ${(settings.floatingButtonOpacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = settings.floatingButtonOpacity,
                            onValueChange = { v -> update { it.copy(floatingButtonOpacity = v) } },
                            valueRange = 0.3f..1f)
                        TextButton(onClick = { update { it.copy(floatingButtonX = -1, floatingButtonY = -1) } },
                            contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("Move it back to the side")
                        }
                    }
                }
            }

            // ---------------- Apps
            item { SectionLabel("Apps") }
            item {
                val hasUsage = remember(tick) { Permissions.hasUsageAccess(ctx) }
                val shizukuReady = remember(tick) { com.mosman.routines.ShizukuBridge.ready }
                val shizukuStatus = remember(tick) { com.mosman.routines.ShizukuBridge.status(ctx) }
                val last = remember(tick, settings) { StatusUnmute.lastResult(ctx) }
                Group {
                    GestureRow(
                        Icons.AutoMirrored.Outlined.VolumeUp, "Unmute WhatsApp statuses",
                        if (shizukuReady) "Presses volume-up when a muted status opens" else "Needs Shizuku · $shizukuStatus",
                        settings.statusUnmuteEnabled,
                        { on ->
                            if (on && !Permissions.hasUsageAccess(ctx)) ctx.startActivity(Permissions.usageAccessSettings())
                            update { it.copy(statusUnmuteEnabled = on) }
                        },
                    ) {
                        Requirement("Usage access", if (hasUsage) "Allowed" else "Needed to see when a status opens",
                            hasUsage, if (hasUsage) null else "Allow") { ctx.startActivity(Permissions.usageAccessSettings()) }
                        Requirement("Shizuku", shizukuStatus, shizukuReady, if (shizukuReady) null else "Open") {
                            val sb = com.mosman.routines.ShizukuBridge
                            if (sb.running && !sb.granted) sb.requestPermission() else sb.openShizuku(ctx)
                        }
                        SwitchLine("Keep the volume level", "Also presses volume-down straight after. Not yet tried on WhatsApp.",
                            settings.statusUnmuteKeepLevel) { on -> update { it.copy(statusUnmuteKeepLevel = on) } }
                        if (last != null) Text("Last: $last", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            scope.launch {
                                val failure = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.mosman.routines.ShizukuBridge.pressKeys(ctx, android.view.KeyEvent.KEYCODE_VOLUME_UP)
                                }
                                snackbar.showSnackbar(failure ?: "Shizuku pressed volume-up. The volume panel should appear.")
                            }
                        }) { Text("Test it") }
                    }
                }
            }

            // ---------------- General
            item { SectionLabel("General") }
            item {
                Group {
                    PlainRow {
                        SwitchLine("Only when the screen is on", "Stops gestures firing in a pocket",
                            settings.screenOnOnly) { on -> update { it.copy(screenOnOnly = on) } }
                    }
                    PlainRow {
                        SwitchLine("Vibrate when used", "A short click so you know it worked",
                            settings.hapticFeedbackEnabled) { on -> update { it.copy(hapticFeedbackEnabled = on) } }
                    }
                    PlainRow {
                        SwitchLine("Keep Silent and Vibrate", "Routines that end won't switch the sound back on if you muted the phone yourself",
                            settings.preserveSilentVibrate) { on -> update { it.copy(preserveSilentVibrate = on) } }
                    }
                    PlainRow {
                        Column {
                            Text("Wait between uses", style = MaterialTheme.typography.bodyLarge)
                            Text("So one shake doesn't fire twice", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1000L to "1 s", 1500L to "1.5 s", 2000L to "2 s", 3000L to "3 s").forEach { (ms, label) ->
                                    FilterChip(selected = settings.cooldownDurationMs == ms,
                                        onClick = { update { it.copy(cooldownDurationMs = ms) } },
                                        label = { Text(label) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    picking?.let { p ->
        JobSheet(p, routines, onDismiss = { picking = null }) { job -> p.onPick(job); picking = null }
    }
}

// ============================================================================
//  Pieces
// ============================================================================

private class JobPick(val what: String, val current: ControlJob, val onPick: (ControlJob) -> Unit)

private fun sensitivityText(s: Sensitivity) = "${s.label()} sensitivity"

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
}

/** Rounded group of rows with thin gaps, like Pixel Settings. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
private fun PlainRow(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
}

/** One gesture: icon, name, a line under it, a switch. On = its details open underneath. */
@Composable
private fun GestureRow(
    icon: ImageVector, title: String, subtitle: String,
    on: Boolean, onToggle: (Boolean) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle(!on) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(if (on) subtitle else "Off", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = on, onCheckedChange = onToggle)
        }
        AnimatedVisibility(on) {
            Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = details)
        }
    }
}

/** "Does  [Volume panel ▾]": the job chip that opens the picker. */
@Composable
private fun JobLine(label: String, job: ControlJob, routines: List<Routine>, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        AssistChip(
            onClick = onClick,
            label = { Text(jobText(job, routines), maxLines = 1) },
            leadingIcon = { Icon(jobIcon(job.action), null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = null,
        )
    }
}

private fun jobText(job: ControlJob, routines: List<Routine>): String =
    if (job.action == FloatingAction.RUN_ROUTINE)
        routines.firstOrNull { it.id == job.routineId }?.let { "Run “${it.name.trim()}”" } ?: "Pick a routine"
    else job.action.label()

private fun jobIcon(a: FloatingAction): ImageVector = when (a) {
    FloatingAction.VOLUME_PANEL -> Icons.AutoMirrored.Outlined.VolumeUp
    FloatingAction.VOLUME_UP -> Icons.Outlined.Add
    FloatingAction.VOLUME_DOWN -> Icons.Outlined.Remove
    FloatingAction.NONE -> Icons.Outlined.Block
    FloatingAction.FLASHLIGHT -> Icons.Outlined.FlashlightOn
    FloatingAction.RINGER_TOGGLE -> Icons.Outlined.Vibration
    FloatingAction.DND_TOGGLE -> Icons.Outlined.DoNotDisturbOn
    FloatingAction.RUN_ROUTINE -> Icons.Outlined.PlayCircle
}

@Composable
private fun SensitivityChips(value: Sensitivity, onChange: (Sensitivity) -> Unit) {
    ChipRow("Sensitivity") {
        Sensitivity.entries.forEach { s ->
            FilterChip(selected = value == s, onClick = { onChange(s) }, label = { Text(s.label()) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(label: String, chips: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

/** "Your routine “Torch” also uses Shake": both will run, so say so. */
@Composable
private fun AlsoUsedBy(routines: List<Routine>, what: String, match: (Trigger) -> Boolean) {
    val users = routines.filter { r -> r.enabled && r.triggers.any(match) }
    if (users.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            (if (users.size == 1) "Your routine “${users[0].name.trim()}” also uses $what, so both will run."
            else "${users.size} routines also use $what, so they'll run too."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchLine(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun Requirement(label: String, status: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = if (ok) "Ready" else "Needs attention",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Notice(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** "What should Shake do?" Picking "Run a routine" swaps the list for your routines. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobSheet(p: JobPick, routines: List<Routine>, onDismiss: () -> Unit, onPick: (ControlJob) -> Unit) {
    var choosingRoutine by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(if (choosingRoutine) "Which routine?" else "What should ${p.what.replaceFirstChar { it.lowercase() }} do?",
            style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            if (choosingRoutine) "It runs straight away, the same as tapping Run now."
            else "Works on any screen.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (!choosingRoutine) {
                items(FloatingAction.entries.filter { it != FloatingAction.NONE } + FloatingAction.NONE) { a ->
                    val selected = p.current.action == a
                    SheetOption(jobIcon(a), a.pickerLabel(), selected) {
                        if (a == FloatingAction.RUN_ROUTINE) choosingRoutine = true else onPick(ControlJob(a))
                    }
                }
            } else {
                if (routines.isEmpty()) item {
                    Text("You don't have any routines yet.", modifier = Modifier.padding(24.dp))
                }
                items(routines) { r ->
                    SheetOption(Ic.of(r.icon), r.name.trim(),
                        p.current.action == FloatingAction.RUN_ROUTINE && p.current.routineId == r.id) {
                        onPick(ControlJob(FloatingAction.RUN_ROUTINE, r.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(18.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}
