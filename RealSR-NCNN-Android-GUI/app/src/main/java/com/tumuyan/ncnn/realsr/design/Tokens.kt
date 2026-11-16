package com.tumuyan.ncnn.realsr.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour token from the prototype's `src/index.css` (`:root` and `[data-theme="dark"]`).
 *
 * The prototype exposes them as CSS variables on two levels: theme-dependent surfaces/text/line
 * colours, and a fixed accent palette. Both are modelled here so a Composable never hard-codes a
 * hex value.
 */
@Immutable
data class RsColors(
    // surfaces
    val canvas: Color,
    val soft: Color,
    val card: Color,
    val strong: Color,
    // text
    val ink: Color,
    val bodyStrong: Color,
    val body: Color,
    val muted: Color,
    val mutedSoft: Color,
    // lines
    val hairline: Color,
    val hairlineSoft: Color,
    // dark product surfaces
    val dark: Color,
    val darkElevated: Color,
    val darkSoft: Color,
    // "featured" flip: dark on cream
    val inverse: Color,
    val onInverse: Color,
    val onInverseSoft: Color,
    // status
    val link: Color,
    val error: Color,
    val disabled: Color,
    val onDisabled: Color,
    val backdrop: Color,
    val stage: Color,
    val scrim: Color,
    // fixed accents (identical in both themes)
    val primary: Color = Color(0xFFCC785C),
    val primaryActive: Color = Color(0xFFA9583E),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val teal: Color = Color(0xFF5DB8A6),
    val amber: Color = Color(0xFFE8A55A),
    val onAccent: Color = Color(0xFF141413),
    val success: Color = Color(0xFF5DB872),
    val warning: Color = Color(0xFFD4A017),
    val onDark: Color = Color(0xFFFAF9F5),
    val onDarkSoft: Color = Color(0xFFA09D96),
    // toasts: a warm cream bubble in both themes, never black
    val toast: Color = Color(0xFFF6E9D2),
    val onToast: Color = Color(0xFF44331F),
    val toastLine: Color = Color(0xFFE7D3B4),
) {
    /** `bg-stage/75` and friends: the prototype uses alpha on a handful of stage colours. */
    fun stageScrim(alpha: Float): Color = stage.copy(alpha = alpha)

    fun onDarkFaint(alpha: Float): Color = onDark.copy(alpha = alpha)
}

val LightRsColors = RsColors(
    canvas = Color(0xFFFAF9F5),
    soft = Color(0xFFF5F0E8),
    card = Color(0xFFEFE9DE),
    strong = Color(0xFFE8E0D2),
    ink = Color(0xFF141413),
    bodyStrong = Color(0xFF252523),
    body = Color(0xFF3D3D3A),
    muted = Color(0xFF6C6A64),
    mutedSoft = Color(0xFF8E8B82),
    hairline = Color(0xFFE6DFD8),
    hairlineSoft = Color(0xFFEBE6DF),
    dark = Color(0xFF181715),
    darkElevated = Color(0xFF252320),
    darkSoft = Color(0xFF1F1E1B),
    inverse = Color(0xFF181715),
    onInverse = Color(0xFFFAF9F5),
    onInverseSoft = Color(0xFFA09D96),
    link = Color(0xFF9C4F36),
    error = Color(0xFFC64545),
    disabled = Color(0xFFE6DFD8),
    onDisabled = Color(0xFF6C6A64),
    backdrop = Color(0xFFE8E0D2),
    stage = Color(0xFF181715),
    scrim = Color(0x70141413),
)

val DarkRsColors = RsColors(
    canvas = Color(0xFF181715),
    soft = Color(0xFF1F1E1B),
    card = Color(0xFF252320),
    strong = Color(0xFF322F2B),
    ink = Color(0xFFFAF9F5),
    bodyStrong = Color(0xFFECE8E1),
    body = Color(0xFFD3CFC7),
    muted = Color(0xFFA09D96),
    mutedSoft = Color(0xFF7F7C75),
    hairline = Color(0xFF35322D),
    hairlineSoft = Color(0xFF2B2926),
    dark = Color(0xFF252320),
    darkElevated = Color(0xFF322F2B),
    darkSoft = Color(0xFF2B2926),
    inverse = Color(0xFFF5F0E8),
    onInverse = Color(0xFF141413),
    onInverseSoft = Color(0xFF6C6A64),
    link = Color(0xFFDD8F74),
    error = Color(0xFFE58585),
    disabled = Color(0xFF322F2B),
    onDisabled = Color(0xFF8E8B82),
    backdrop = Color(0xFF2B2926),
    stage = Color(0xFF0F0E0D),
    scrim = Color(0x99000000),
    toast = Color(0xFFF3E6CE),
    onToast = Color(0xFF3B2C1B),
    toastLine = Color(0xFFD8C3A2),
)

val LocalRsColors = staticCompositionLocalOf { LightRsColors }

/** The prototype's radius scale (`--radius-*`), in dp. */
object RsRadius {
    const val xs = 4f
    const val sm = 6f
    const val md = 8f
    const val lg = 12f
    const val xl = 16f
    const val xxl = 20f

    /** Source colour tokens are used as `size-*`/`min-h-*` in Tailwind (1 unit = 4 dp). */
    const val full = 999f
}

/** Tailwind spacing units used by the prototype, resolved to dp (n × 4). */
object RsSpace {
    val hair = 0.5f
    val xxs = 2f
    val xs = 4f
    val s = 6f
    val sm = 8f
    val m = 12f
    val md = 14f
    val lg = 16f
    val xl = 20f
    val xxl = 24f
    val xxxl = 28f
    val huge = 40f
}
