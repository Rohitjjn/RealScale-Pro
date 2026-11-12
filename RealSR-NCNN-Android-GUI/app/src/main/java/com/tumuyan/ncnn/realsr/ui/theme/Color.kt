package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Claude warm editorial palette derived from VoltAgent/awesome-design-md (`claude/DESIGN.md`).
 * Warm parchment & terracotta in light mode; warm charcoal & muted coral in dark mode.
 * Never introduces cool blue-grays or pure #000000.
 */
object ClaudePalette {
    // Light surfaces
    val PampasCanvas = Color(0xFFFAF9F5)
    val WarmSand = Color(0xFFF5F0E8)
    val EditorialCream = Color(0xFFEFE9DE)
    val CreamStrong = Color(0xFFE8E0D2)

    // Light ink & borders
    val InkPrimary = Color(0xFF141413)
    val InkStrong = Color(0xFF252523)
    val CharcoalBody = Color(0xFF3D3D3A)
    val StoneMuted = Color(0xFF6C6A64)
    val AshSubtle = Color(0xFF8E8B82)
    val WarmHairline = Color(0xFFE6DFD8)
    val SoftHairline = Color(0xFFEBE6DF)

    // Brand accent (reserved strictly for primary CTAs & focal callouts)
    val Terracotta = Color(0xFFCC785C)
    val TerracottaActive = Color(0xFFA9583E)
    val TerracottaSoftTint = Color(0xFFF5E6DF)
    val OnTerracotta = Color(0xFFFFFFFF)

    // Dark surfaces
    val CharcoalNight = Color(0xFF181715)
    val ObsidianSoft = Color(0xFF1F1E1B)
    val WalnutElevated = Color(0xFF252320)
    val WalnutStrong = Color(0xFF2F2C28)

    // Dark ink & borders
    val ParchmentOnDark = Color(0xFFFAF9F5)
    val WarmSilverBody = Color(0xFFD7D3CA)
    val PebbleMutedOnDark = Color(0xFFA09D96)
    val SubtleOnDark = Color(0xFF7B7870)
    val DarkHairline = Color(0xFF33302B)
    val DarkSoftHairline = Color(0xFF292723)
    val TerracottaOnDark = Color(0xFFD28267)
    val TerracottaDarkTint = Color(0xFF34241E)

    // Semantic status
    val SageSuccess = Color(0xFF4A9B60)
    val SageSuccessDark = Color(0xFF67BE7C)
    val AmberWarning = Color(0xFFC48F14)
    val CrimsonError = Color(0xFFC64545)
    val CrimsonErrorDark = Color(0xFFE06C6C)
}

@Immutable
data class RealScaleExtendedColors(
    val canvas: Color,
    val surfaceSoft: Color,
    val surfaceCard: Color,
    val surfaceStrong: Color,
    val ink: Color,
    val body: Color,
    val muted: Color,
    val subtle: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val terracotta: Color,
    val terracottaPressed: Color,
    val terracottaTint: Color,
    val onTerracotta: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean
)

val LightExtendedColors = RealScaleExtendedColors(
    canvas = ClaudePalette.PampasCanvas,
    surfaceSoft = ClaudePalette.WarmSand,
    surfaceCard = ClaudePalette.EditorialCream,
    surfaceStrong = ClaudePalette.CreamStrong,
    ink = ClaudePalette.InkPrimary,
    body = ClaudePalette.CharcoalBody,
    muted = ClaudePalette.StoneMuted,
    subtle = ClaudePalette.AshSubtle,
    hairline = ClaudePalette.WarmHairline,
    hairlineSoft = ClaudePalette.SoftHairline,
    terracotta = ClaudePalette.Terracotta,
    terracottaPressed = ClaudePalette.TerracottaActive,
    terracottaTint = ClaudePalette.TerracottaSoftTint,
    onTerracotta = ClaudePalette.OnTerracotta,
    success = ClaudePalette.SageSuccess,
    warning = ClaudePalette.AmberWarning,
    error = ClaudePalette.CrimsonError,
    isDark = false
)

val DarkExtendedColors = RealScaleExtendedColors(
    canvas = ClaudePalette.CharcoalNight,
    surfaceSoft = ClaudePalette.ObsidianSoft,
    surfaceCard = ClaudePalette.WalnutElevated,
    surfaceStrong = ClaudePalette.WalnutStrong,
    ink = ClaudePalette.ParchmentOnDark,
    body = ClaudePalette.WarmSilverBody,
    muted = ClaudePalette.PebbleMutedOnDark,
    subtle = ClaudePalette.SubtleOnDark,
    hairline = ClaudePalette.DarkHairline,
    hairlineSoft = ClaudePalette.DarkSoftHairline,
    terracotta = ClaudePalette.TerracottaOnDark,
    terracottaPressed = ClaudePalette.Terracotta,
    terracottaTint = ClaudePalette.TerracottaDarkTint,
    onTerracotta = ClaudePalette.OnTerracotta,
    success = ClaudePalette.SageSuccessDark,
    warning = ClaudePalette.AmberWarning,
    error = ClaudePalette.CrimsonErrorDark,
    isDark = true
)

val LocalRealScaleColors = staticCompositionLocalOf { LightExtendedColors }
