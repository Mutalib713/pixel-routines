package com.mosman.routines

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Pixel look: Material You dynamic color pulled from the wallpaper, following the system
 * light/dark setting. minSdk 31 guarantees dynamic color is available.
 */
@Composable
fun PixelRoutinesTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}
