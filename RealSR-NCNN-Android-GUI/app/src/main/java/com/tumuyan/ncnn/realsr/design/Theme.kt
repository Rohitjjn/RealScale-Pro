package com.tumuyan.ncnn.realsr.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Wraps the design tokens, the reduce-motion flag and a Material 3 shell so any Material component
 * that slips through still resolves to sensible colours. Everything visible is built from
 * [LocalRsColors], never from `MaterialTheme.colorScheme`.
 */
@Composable
fun RsTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkRsColors else LightRsColors
    val reduce = rememberReduceMotion()
    val scheme = remember(darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = DarkRsColors.primary,
                background = DarkRsColors.canvas,
                surface = DarkRsColors.canvas,
                onBackground = DarkRsColors.ink,
                onSurface = DarkRsColors.ink,
                error = DarkRsColors.error,
            )
        } else {
            lightColorScheme(
                primary = LightRsColors.primary,
                background = LightRsColors.canvas,
                surface = LightRsColors.canvas,
                onBackground = LightRsColors.ink,
                onSurface = LightRsColors.ink,
                error = LightRsColors.error,
            )
        }
    }
    CompositionLocalProvider(
        LocalRsColors provides colors,
        LocalReduceMotion provides reduce,
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
