package com.mosman.routines.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mosman.routines.Permissions
import com.mosman.routines.ShizukuBridge
import com.mosman.routines.Store
import com.mosman.routines.ai.AssistantMode
import com.mosman.routines.ai.AssistantPrefs
import com.mosman.routines.ui.theme.AppThemeMode
import com.mosman.routines.ui.theme.ThemePreferences
import kotlinx.coroutines.launch

/**
 * Settings, layout S1 (picked by Mutalib 2026-11-20): one list like Pixel's own Settings app,
 * icon + one-line summary per group, each opening its own page. Activity lives here now.
 */
private object Sp {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 32.dp
}
private object Shp {
    val group = RoundedCornerShape(24.dp)
    val row = RoundedCornerShape(16.dp)
}

private enum class Page(val title: String) {
    MAIN("Settings"), APPEARANCE("Appearance"), PERMISSIONS("Permissions"), AI("Routine AI"),
    BACKUP("Backup"), ADVANCED("Advanced"), ABOUT("About"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenRedesigned(
    tick: Int,
    onBack: () -> Unit,
    onNavigateToVolumeTriggers: () -> Unit = {},
    onNavigateToActivity: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.MAIN) }

    // A sub-page catches Back first and returns to the list.
    BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }

    val perms = remember(tick) { permissionRows(ctx) }
    val missing = perms.count { !it.granted }
    var paused by remember(tick) { mutableStateOf(Store.isPaused(ctx)) }
    val themeMode = ThemePreferences.getThemeMode(ctx)
    val dynamic = ThemePreferences.isDynamicColor(ctx)
    var aiMode by remember(tick) { mutableStateOf(AssistantPrefs.mode(ctx)) }
    var aiConsent by remember(tick) { mutableStateOf(AssistantPrefs.hasConsent(ctx)) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson(ctx).toByteArray()) } != null
            }.getOrDefault(false)
            scope.launch { snackbar.showSnackbar(if (ok) "Routines saved to the file" else "Couldn't write the file") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val added = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { Store.importJson(ctx, it.readBytes().decodeToString()) } ?: 0
            }.getOrDefault(0)
            scope.launch {
                snackbar.showSnackbar(if (added > 0) "Added $added routine${if (added == 1) "" else "s"}" else "No routines found in that file")
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = {
                    IconButton(onClick = { if (page == Page.MAIN) onBack() else page = Page.MAIN }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = Sp.l, end = Sp.l, top = Sp.s, bottom = Sp.xxl),
            verticalArrangement = Arrangement.spacedBy(Sp.m),
        ) {
            when (page) {
                Page.MAIN -> mainPage(
                    paused = paused,
                    onPause = { Store.setPaused(ctx, it); paused = it },
                    themeSummary = "${themeMode.short()} · ${if (dynamic) "wallpaper colours" else "app colours"}",
                    missing = missing,
                    aiSummary = "${aiMode.label()} · ${if (aiConsent) "Gemini allowed" else "Gemini not allowed yet"}",
                    shizukuSummary = ShizukuBridge.status(ctx),
                    version = version,
                    open = { page = it },
                    onActivity = onNavigateToActivity,
                    onControls = onNavigateToVolumeTriggers,
                )

                Page.APPEARANCE -> {
                    item { Label("Theme") }
                    item {
                        Group {
                            AppThemeMode.entries.forEach { m ->
                                RadioRow(m.short(), selected = themeMode == m) { ThemePreferences.setThemeMode(ctx, m) }
                            }
                        }
                    }
                    item { Label("Colours") }
                    item {
                        Group {
                            SwitchRow(
                                Icons.Outlined.Wallpaper, "Wallpaper colours",
                                "Match the app to your wallpaper, like the rest of your Pixel", dynamic,
                            ) { ThemePreferences.setDynamicColor(ctx, it) }
                        }
                    }
                }

                Page.PERMISSIONS -> {
                    item {
                        Note("Each one unlocks something specific. Routines that need a missing one say so in the editor.")
                    }
                    item {
                        Group {
                            perms.forEach { p ->
                                PermRow(p) { ctx.startActivity(p.open(ctx)) }
                            }
                        }
                    }
                }

                Page.AI -> {
                    item { Label("Who answers") }
                    item {
                        Group {
                            AssistantMode.entries.forEach { m ->
                                RadioRow(m.label(), m.detail(), selected = aiMode == m) {
                                    AssistantPrefs.setMode(ctx, m); aiMode = m
                                }
                            }
                        }
                    }
                    item { Label("Gemini") }
                    item {
                        Group {
                            SwitchRow(
                                Icons.Outlined.Cloud, "Typed messages may go to Gemini",
                                "Only when the phone can't answer. Google may use them on the free plan.", aiConsent,
                            ) { AssistantPrefs.setConsent(ctx, it); aiConsent = it }
                        }
                    }
                    item { Note("Pictures and files are asked about every time, whatever this switch says.") }
                }

                Page.BACKUP -> {
                    item { Note("Save your routines to a file to keep a copy or move them to another phone.") }
                    item {
                        Group {
                            NavRow(Icons.Outlined.Upload, "Save to a file", "pixel-routines.json") {
                                exporter.launch("pixel-routines.json")
                            }
                            NavRow(Icons.Outlined.Download, "Add from a file", "Routines in it are added, nothing is replaced") {
                                importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                        }
                    }
                }

                Page.ADVANCED -> {
                    item {
                        Note("Wi-Fi, Bluetooth and airplane-mode actions, pressing keys for WhatsApp statuses and battery saver need extra access that only a computer or Shizuku can give.")
                    }
                    item {
                        Group {
                            NavRow(Icons.Outlined.Terminal, "Shizuku", ShizukuBridge.status(ctx)) {
                                if (ShizukuBridge.running && !ShizukuBridge.granted) ShizukuBridge.requestPermission()
                                else ShizukuBridge.openShizuku(ctx)
                            }
                            val secure = Permissions.hasSecureSettings(ctx)
                            NavRow(
                                Icons.Outlined.AdminPanelSettings, "Secure settings",
                                if (secure) "Allowed" else "Tap to copy the one-time ADB command",
                            ) {
                                val cmd = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("adb", cmd))
                                scope.launch { snackbar.showSnackbar("ADB command copied") }
                            }
                        }
                    }
                }

                Page.ABOUT -> {
                    item {
                        Group {
                            NavRow(Icons.Outlined.Info, "Pixel Routines", "Version $version") {}
                            NavRow(Icons.Outlined.Lock, "Privacy", "Routines stay on your phone. Only Routine AI's online answers use the internet.") {}
                        }
                    }
                }
            }
        }
    }
}

// ---- Main list ---------------------------------------------------------------------------

private fun LazyListScope.mainPage(
    paused: Boolean,
    onPause: (Boolean) -> Unit,
    themeSummary: String,
    missing: Int,
    aiSummary: String,
    shizukuSummary: String,
    version: String,
    open: (Page) -> Unit,
    onActivity: () -> Unit,
    onControls: () -> Unit,
) {
    item {
        Row(
            Modifier.fillMaxWidth().clip(Shp.group).background(MaterialTheme.colorScheme.surfaceContainer).padding(Sp.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("M", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(Sp.l))
            Column {
                Text("Mutalib", style = MaterialTheme.typography.titleMedium)
                Text("${Build.MODEL} · Android ${Build.VERSION.RELEASE}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item {
        Group {
            SwitchRow(Icons.Outlined.PauseCircle, "Pause all routines", "Running routines stop and put things back. Nothing starts until you switch this off.", paused, onPause)
        }
    }
    item {
        Group {
            NavRow(Icons.Outlined.History, "Activity", "What ran, when, and anything that failed", onClick = onActivity)
            NavRow(Icons.Outlined.Tune, "Controls", "Floating volume button, swipes, shake, WhatsApp", onClick = onControls)
            NavRow(Icons.Outlined.Palette, "Appearance", themeSummary) { open(Page.APPEARANCE) }
            NavRow(
                Icons.Outlined.VerifiedUser, "Permissions",
                if (missing == 0) "Everything the app uses is allowed" else "What the app is allowed to do",
                badge = if (missing > 0) "$missing needed" else null,
            ) { open(Page.PERMISSIONS) }
            NavRow(Icons.Filled.AutoAwesome, "Routine AI", aiSummary) { open(Page.AI) }
        }
    }
    item {
        Group {
            NavRow(Icons.Outlined.Save, "Backup", "Save or add routines from a file") { open(Page.BACKUP) }
            NavRow(Icons.Outlined.Terminal, "Advanced", "Shizuku: $shizukuSummary") { open(Page.ADVANCED) }
            NavRow(Icons.Outlined.Info, "About", "Version $version") { open(Page.ABOUT) }
        }
    }
}

private fun AppThemeMode.short() = when (this) {
    AppThemeMode.SYSTEM -> "Same as phone"
    AppThemeMode.LIGHT -> "Light"
    AppThemeMode.DARK -> "Dark"
}

// ---- Permissions --------------------------------------------------------------------------

private class Perm(val icon: ImageVector, val name: String, val why: String, val granted: Boolean, val open: (Context) -> android.content.Intent)

private fun permissionRows(ctx: Context): List<Perm> = listOf(
    Perm(Icons.Outlined.DoNotDisturbOn, "Do Not Disturb", "Switch Do Not Disturb and the ringer", Permissions.hasDnd(ctx)) { Permissions.dndSettings() },
    Perm(Icons.Outlined.Alarm, "Alarms on the minute", "Run time routines exactly on time", Permissions.hasExactAlarm(ctx)) { Permissions.exactAlarmSettings(it) },
    Perm(Icons.Outlined.Settings, "Change system settings", "Brightness, rotation, ringtone", Permissions.hasWriteSettings(ctx)) { Permissions.writeSettings(it) },
    Perm(Icons.Outlined.Notifications, "Notifications", "Tell you when a routine runs, and reminders", Permissions.hasNotifications(ctx)) { Permissions.appDetails(it) },
    Perm(Icons.Outlined.QueryStats, "Usage access", "Notice which app is open (app triggers, WhatsApp unmute)", Permissions.hasUsageAccess(ctx)) { Permissions.usageAccessSettings() },
    Perm(Icons.Outlined.Layers, "Display over other apps", "Floating volume button and edge swipes", Permissions.hasOverlay(ctx)) { Permissions.overlaySettings(it) },
)

// ---- Pieces -------------------------------------------------------------------------------

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Shp.group).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = Sp.xs),
        content = content,
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Sp.l, top = Sp.s))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Sp.s))
}

@Composable
private fun NavRow(icon: ImageVector, title: String, summary: String, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shp.row).clickable(onClick = onClick).padding(horizontal = Sp.l, vertical = Sp.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Sp.xl))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        badge?.let {
            Spacer(Modifier.width(Sp.s))
            Text(
                it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.clip(Shp.row).background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Sp.s, vertical = Sp.xs),
            )
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shp.row).clickable { onChange(!checked) }.padding(horizontal = Sp.l, vertical = Sp.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Sp.xl))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Sp.s))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(title: String, summary: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shp.row).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Sp.l, vertical = Sp.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Sp.l))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PermRow(p: Perm, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shp.row).clickable(onClick = onClick).padding(horizontal = Sp.l, vertical = Sp.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(p.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Sp.xl))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            Text(p.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Sp.s))
        Text(
            if (p.granted) "Allowed" else "Allow",
            style = MaterialTheme.typography.labelLarge,
            color = if (p.granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}
