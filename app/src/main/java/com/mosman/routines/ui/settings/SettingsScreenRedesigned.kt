package com.mosman.routines.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.mosman.routines.ShizukuBridge
import com.mosman.routines.Store
import com.mosman.routines.ui.theme.AppThemeMode
import com.mosman.routines.ui.theme.ThemePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenRedesigned(
    tick: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var themeMode by remember { mutableStateOf(ThemePreferences.getThemeMode(ctx)) }
    var dynamicColor by remember { mutableStateOf(ThemePreferences.isDynamicColor(ctx)) }
    var paused by remember(tick) { mutableStateOf(Store.isPaused(ctx)) }

    val shizukuReady = remember(tick) { ShizukuBridge.ready }
    val shizukuStatus = remember(tick) { ShizukuBridge.status(ctx) }
    val secureGranted = remember(tick) { Permissions.hasSecureSettings(ctx) }
    val dndGranted = remember(tick) { Permissions.hasDnd(ctx) }
    val alarmsGranted = remember(tick) { Permissions.hasExactAlarm(ctx) }
    val writeGranted = remember(tick) { Permissions.hasWriteSettings(ctx) }
    val notifGranted = remember(tick) {
        if (Build.VERSION.SDK_INT >= 33) Permissions.hasNotifications(ctx) else true
    }

    val adbCmd = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"

    // Document export & import contracts
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(Store.exportJson(ctx).toByteArray())
                }
            }
            scope.launch { snackbar.showSnackbar("Routines exported successfully") }
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val added = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use {
                    Store.importJson(ctx, it.readBytes().decodeToString())
                } ?: 0
            }.getOrDefault(0)
            scope.launch {
                snackbar.showSnackbar(
                    if (added > 0) "Imported $added routine${if (added == 1) "" else "s"}"
                    else "No valid routines found in file"
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile Card (Matching Screen 9)
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "M",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mutalib", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                "Pixel 6 Pro · Android 14",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Global Pause Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp),
                        ),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Pause all routines", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Temporarily pause all automations",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = paused,
                            onCheckedChange = {
                                paused = it
                                Store.setPaused(ctx, it)
                            },
                        )
                    }
                }
            }

            // Section: Appearance
            item {
                SettingsSectionHeader("Appearance")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Theme mode", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            AppThemeMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = {
                                        themeMode = mode
                                        ThemePreferences.setThemeMode(ctx, mode)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(i, AppThemeMode.entries.size),
                                ) {
                                    Text(mode.label(), fontSize = 12.sp)
                                }
                            }
                        }

                        if (Build.VERSION.SDK_INT >= 31) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Dynamic color", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "Use colors extracted from your wallpaper",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = {
                                        dynamicColor = it
                                        ThemePreferences.setDynamicColor(ctx, it)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Section: Permissions
            item {
                SettingsSectionHeader("Permissions & Access")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionStatusRow("Do Not Disturb", dndGranted) {
                            ctx.startActivity(Permissions.dndSettings())
                        }
                        PermissionStatusRow("Exact Alarms", alarmsGranted) {
                            ctx.startActivity(Permissions.exactAlarmSettings(ctx))
                        }
                        PermissionStatusRow("Modify System Settings", writeGranted) {
                            ctx.startActivity(Permissions.writeSettings(ctx))
                        }
                        PermissionStatusRow("Notifications", notifGranted) {
                            ctx.startActivity(Permissions.appDetails(ctx))
                        }
                        PermissionStatusRow("Shizuku ADB Access", shizukuReady) {
                            if (ShizukuBridge.running && !ShizukuBridge.granted) ShizukuBridge.requestPermission()
                            else ShizukuBridge.openShizuku(ctx)
                        }
                        PermissionStatusRow("Secure Settings", secureGranted) {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("adb", adbCmd))
                            scope.launch { snackbar.showSnackbar("ADB command copied to clipboard") }
                        }
                    }
                }
            }

            // Section: Backup & Export
            item {
                SettingsSectionHeader("Backup & Export")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Export your routines to a JSON file to back them up or transfer to another device.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(
                                onClick = { exporter.launch("pixel-routines.json") },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export")
                            }
                            FilledTonalButton(
                                onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import")
                            }
                        }
                    }
                }
            }

            // Section: General & About
            item {
                SettingsSectionHeader("General")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pixel Routines", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("v1.2", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "Smart, privacy-focused automation for Android.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
private fun PermissionStatusRow(name: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (granted) Color(0xFF10B981).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.errorContainer
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = if (granted) "Granted" else "Needed",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (granted) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) {
            Text(if (granted) "Manage" else "Grant", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
