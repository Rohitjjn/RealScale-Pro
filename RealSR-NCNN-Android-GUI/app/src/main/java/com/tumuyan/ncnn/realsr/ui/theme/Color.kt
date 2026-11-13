package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * RealScale-Pro design tokens — ported 1:1 from the Claude-style TSX design (src/index.css).
 * Every colour in `:root` (light) and `[data-theme="dark"]` is represented here, including
 * the dark product surfaces (stage / dark / dark-elevated) and the "featured flip" (inverse).
 * Accent colours (primary, teal, amber, success, warning) are identical in light and dark.
 */
data class RealScaleColors(
    // Surfaces
    val canvas: Color, val soft: Color, val card: Color, val strong: Color,
    // Text
    val ink: Color, val bodyStrong: Color, val body: Color, val muted: Color, val mutedSoft: Color,
    // Lines
    val hairline: Color, val hairlineSoft: Color,
    // Dark product surfaces
    val dark: Color, val darkElevated: Color, val darkSoft: Color,
    // "Featured" flip
    val inverse: Color, val onInverse: Color, val onInverseSoft: Color,
    // Semantics
    val link: Color, val error: Color, val disabled: Color, val onDisabled: Color,
    val backdrop: Color, val stage: Color, val scrim: Color,
    // Accents (shared across themes)
    val primary: Color, val primaryActive: Color, val onPrimary: Color,
    val teal: Color, val amber: Color, val onAccent: Color,
    val success: Color, val warning: Color,
    // Text drawn on dark surfaces
    val onDark: Color, val onDarkSoft: Color,
    val isDark: Boolean,
) {
    companion object {
        val Light = RealScaleColors(
            canvas = Color(0xFFFAF9F5), soft = Color(0xFFF5F0E8), card = Color(0xFFEFE9DE), strong = Color(0xFFE8E0D2),
            ink = Color(0xFF141413), bodyStrong = Color(0xFF252523), body = Color(0xFF3D3D3A), muted = Color(0xFF6C6A64), mutedSoft = Color(0xFF8E8B82),
            hairline = Color(0xFFE6DFD8), hairlineSoft = Color(0xFFEBE6DF),
            dark = Color(0xFF181715), darkElevated = Color(0xFF252320), darkSoft = Color(0xFF1F1E1B),
            inverse = Color(0xFF181715), onInverse = Color(0xFFFAF9F5), onInverseSoft = Color(0xFFA09D96),
            link = Color(0xFF9C4F36), error = Color(0xFFC64545), disabled = Color(0xFFE6DFD8), onDisabled = Color(0xFF6C6A64),
            backdrop = Color(0xFFE8E0D2), stage = Color(0xFF181715), scrim = Color(0x70141413),
            primary = Color(0xFFCC785C), primaryActive = Color(0xFFA9583E), onPrimary = Color(0xFFFFFFFF),
            teal = Color(0xFF5DB8A6), amber = Color(0xFFE8A55A), onAccent = Color(0xFF141413),
            success = Color(0xFF5DB872), warning = Color(0xFFD4A017),
            onDark = Color(0xFFFAF9F5), onDarkSoft = Color(0xFFA09D96),
            isDark = false,
        )

        val Dark = RealScaleColors(
            canvas = Color(0xFF181715), soft = Color(0xFF1F1E1B), card = Color(0xFF252320), strong = Color(0xFF322F2B),
            ink = Color(0xFFFAF9F5), bodyStrong = Color(0xFFECE8E1), body = Color(0xFFD3CFC7), muted = Color(0xFFA09D96), mutedSoft = Color(0xFF7F7C75),
            hairline = Color(0xFF35322D), hairlineSoft = Color(0xFF2B2926),
            dark = Color(0xFF252320), darkElevated = Color(0xFF322F2B), darkSoft = Color(0xFF2B2926),
            inverse = Color(0xFFF5F0E8), onInverse = Color(0xFF141413), onInverseSoft = Color(0xFF6C6A64),
            link = Color(0xFFDD8F74), error = Color(0xFFE58585), disabled = Color(0xFF322F2B), onDisabled = Color(0xFF8E8B82),
            backdrop = Color(0xFF2B2926), stage = Color(0xFF0F0E0D), scrim = Color(0x99000000),
            primary = Color(0xFFCC785C), primaryActive = Color(0xFFA9583E), onPrimary = Color(0xFFFFFFFF),
            teal = Color(0xFF5DB8A6), amber = Color(0xFFE8A55A), onAccent = Color(0xFF141413),
            success = Color(0xFF5DB872), warning = Color(0xFFD4A017),
            onDark = Color(0xFFFAF9F5), onDarkSoft = Color(0xFFA09D96),
            isDark = true,
        )
    }
}

val LocalRealScaleColors = compositionLocalOf { RealScaleColors.Light }
