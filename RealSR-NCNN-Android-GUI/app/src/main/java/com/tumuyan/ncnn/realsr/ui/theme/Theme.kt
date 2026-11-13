package com.tumuyan.ncnn.realsr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/** Theme preference exactly as persisted: system / light / dark. */
enum class AppTheme { SYSTEM, LIGHT, DARK }

private fun materialScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = RealScaleColors.Dark.primary, onPrimary = RealScaleColors.Dark.onPrimary,
        error = RealScaleColors.Dark.error, onError = RealScaleColors.Dark.onPrimary,
        surface = RealScaleColors.Dark.canvas, onSurface = RealScaleColors.Dark.ink,
        surfaceVariant = RealScaleColors.Dark.card, onSurfaceVariant = RealScaleColors.Dark.body,
        outline = RealScaleColors.Dark.mutedSoft,
    )
} else {
    lightColorScheme(
        primary = RealScaleColors.Light.primary, onPrimary = RealScaleColors.Light.onPrimary,
        error = RealScaleColors.Light.error, onError = RealScaleColors.Light.onPrimary,
        surface = RealScaleColors.Light.canvas, onSurface = RealScaleColors.Light.ink,
        surfaceVariant = RealScaleColors.Light.card, onSurfaceVariant = RealScaleColors.Light.body,
        outline = RealScaleColors.Light.mutedSoft,
    )
}

/**
 * Wraps the app in the RealScale design tokens. When [dynamicColor] is enabled on Android 12+,
 * wallpaper-derived dynamic colours (Material You) replace the tonal surface family and the
 * primary accent while text/hairline tokens stay fixed; otherwise the exact design palette is
 * used in both themes.
 */
@Composable
fun RealScaleTheme(
    themeMode: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val base = if (dark) RealScaleColors.Dark else RealScaleColors.Light

    val tokens = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        base.copy(
            primary = dynamic.primary,
            primaryActive = dynamic.primaryContainer.let { dynamic.primary.copy(alpha = 0.82f) },
            card = dynamic.surfaceVariant,
            soft = dynamic.surfaceContainerLow,
            strong = dynamic.surfaceContainerHighest,
            hairline = dynamic.outlineVariant,
            backdrop = dynamic.surfaceContainerHighest,
        )
    } else {
        base
    }

    MaterialTheme(colorScheme = materialScheme(dark)) {
        CompositionLocalProvider(LocalRealScaleColors provides tokens) {
            content()
        }
    }
}
