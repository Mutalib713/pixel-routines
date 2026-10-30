package com.mosman.routines

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import com.mosman.routines.ui.theme.ThemePreferences
import com.mosman.routines.ui.theme.PixelRoutinesTheme as ThemedRoot

/**
 * Pixel look: Supports dynamic colors as well as user-selected Light, Dark, or System mode.
 */
@Composable
fun PixelRoutinesTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val mode = ThemePreferences.getThemeMode(ctx)
    ThemedRoot(mode = mode, content = content)
}
