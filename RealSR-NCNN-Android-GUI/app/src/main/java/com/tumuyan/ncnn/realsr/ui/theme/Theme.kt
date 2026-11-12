package com.tumuyan.ncnn.realsr.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val LightMaterialScheme = lightColorScheme(
    primary = ClaudePalette.Terracotta,
    onPrimary = ClaudePalette.OnTerracotta,
    primaryContainer = ClaudePalette.TerracottaSoftTint,
    onPrimaryContainer = ClaudePalette.InkPrimary,
    secondary = ClaudePalette.InkStrong,
    onSecondary = ClaudePalette.PampasCanvas,
    secondaryContainer = ClaudePalette.WarmSand,
    onSecondaryContainer = ClaudePalette.InkPrimary,
    background = ClaudePalette.PampasCanvas,
    onBackground = ClaudePalette.InkPrimary,
    surface = ClaudePalette.PampasCanvas,
    onSurface = ClaudePalette.InkPrimary,
    surfaceVariant = ClaudePalette.EditorialCream,
    onSurfaceVariant = ClaudePalette.StoneMuted,
    outline = ClaudePalette.WarmHairline,
    outlineVariant = ClaudePalette.SoftHairline,
    error = ClaudePalette.CrimsonError
)

private val DarkMaterialScheme = darkColorScheme(
    primary = ClaudePalette.TerracottaOnDark,
    onPrimary = ClaudePalette.OnTerracotta,
    primaryContainer = ClaudePalette.TerracottaDarkTint,
    onPrimaryContainer = ClaudePalette.ParchmentOnDark,
    secondary = ClaudePalette.ParchmentOnDark,
    onSecondary = ClaudePalette.CharcoalNight,
    secondaryContainer = ClaudePalette.ObsidianSoft,
    onSecondaryContainer = ClaudePalette.ParchmentOnDark,
    background = ClaudePalette.CharcoalNight,
    onBackground = ClaudePalette.ParchmentOnDark,
    surface = ClaudePalette.CharcoalNight,
    onSurface = ClaudePalette.ParchmentOnDark,
    surfaceVariant = ClaudePalette.WalnutElevated,
    onSurfaceVariant = ClaudePalette.PebbleMutedOnDark,
    outline = ClaudePalette.DarkHairline,
    outlineVariant = ClaudePalette.DarkSoftHairline,
    error = ClaudePalette.CrimsonErrorDark
)

@Composable
fun RealScaleTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDark) DarkMaterialScheme else LightMaterialScheme
    val extendedColors = if (useDark) DarkExtendedColors else LightExtendedColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = extendedColors.canvas.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = extendedColors.canvas.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDark
            controller.isAppearanceLightNavigationBars = !useDark
        }
    }

    CompositionLocalProvider(LocalRealScaleColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RealScaleTypography,
            shapes = RealScaleShapes,
            content = content
        )
    }
}

object RealScaleThemeTokens {
    val colors: RealScaleExtendedColors
        @Composable
        get() = LocalRealScaleColors.current
}
