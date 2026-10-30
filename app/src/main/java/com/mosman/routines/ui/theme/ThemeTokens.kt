package com.mosman.routines.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun label(): String = when (this) {
        SYSTEM -> "Follow system"
        LIGHT -> "Light theme"
        DARK -> "Dark theme"
    }
}

// Brand color palette matching the Pixel Routines concept board
val BrandBlue = Color(0xFF386BF6)
val BrandBlueDark = Color(0xFF6B93FF)
val ActiveEmerald = Color(0xFF10B981)
val ActiveEmeraldContainer = Color(0xFFD1FAE5)
val ActiveEmeraldText = Color(0xFF065F46)
val ActiveEmeraldContainerDark = Color(0xFF064E3B)
val ActiveEmeraldTextDark = Color(0xFF6EE7B7)

val DarkSurface = Color(0xFF111827)
val DarkSurfaceElevated = Color(0xFF1F2937)
val DarkBackground = Color(0xFF0B0F19)

val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF1F5F9)
val LightBackground = Color(0xFFF8FAFC)

private val LightColors = lightColorScheme(
    primary = Color(0xFF386BF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6B93FF),
    onPrimary = Color(0xFF00227B),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF371E7A),
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = DarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
)

object ThemePreferences {
    private const val PREFS = "pixel_routines_theme"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_DYNAMIC = "use_dynamic_color"

    fun getThemeMode(context: Context): AppThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(raw!!) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun isDynamicColor(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC, true)
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DYNAMIC, enabled)
            .apply()
    }
}

@Composable
fun PixelRoutinesTheme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val dynamic = ThemePreferences.isDynamicColor(ctx)
    val scheme: ColorScheme = if (dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
