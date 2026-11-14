package com.tumuyan.ncnn.realsr.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tumuyan.ncnn.realsr.R

/**
 * Typography, ported from the prototype's font stack and the type scale observed in the captures
 * (`docs/ui-reference/TOKENS.md`).
 *
 * - display → Cormorant Garamond 500/600 (the documented open substitute for Copernicus/Tiempos)
 * - body    → Inter 400/500/600/700 (substitute for StyreneB)
 * - mono    → JetBrains Mono 400/500 (file names, numbers, console)
 * - Devanagari fallback bundled for Hindi; the platform also falls back per glyph.
 */
object RsFonts {
    val Display = FontFamily(
        Font(R.font.cormorant_garamond_500, FontWeight.Medium),
        Font(R.font.cormorant_garamond_600, FontWeight.SemiBold),
    )

    val Body = FontFamily(
        Font(R.font.inter_400, FontWeight.Normal),
        Font(R.font.inter_500, FontWeight.Medium),
        Font(R.font.inter_600, FontWeight.SemiBold),
        Font(R.font.inter_700, FontWeight.Bold),
    )

    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_400, FontWeight.Normal),
        Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    )

    /** Bundled so Hindi renders identically everywhere; registered as a system-wide fallback too. */
    val Devanagari = FontFamily(
        Font(R.font.noto_sans_devanagari_400, FontWeight.Normal),
        Font(R.font.noto_sans_devanagari_500, FontWeight.Medium),
    )

    val all: List<FontFamily> = listOf(Display, Body, Mono, Devanagari)
}

/**
 * The prototype's three text utilities plus the sizes it actually uses.
 *
 * All sizes are sp values equal to the CSS px values (1rem = 16dp, so `text-[0.9375rem]` = 15sp).
 */
object RsType {
    private val devanagariSafe = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    /** `.display`: serif, 500, -0.02em tracking, 1.08 line height, lining numerals. */
    fun display(size: Float, weight: FontWeight = FontWeight.Medium, lineHeight: Float = size * 1.08f) = TextStyle(
        fontFamily = RsFonts.Display,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = (-0.02f * size).sp,
        fontFeatureSettings = "lnum",
        lineHeightStyle = devanagariSafe,
    )

    /** `.eyebrow`: 12sp, 500, 0.125em, uppercase, 1.4 line height. */
    val eyebrow = TextStyle(
        fontFamily = RsFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.8.sp,
        letterSpacing = (0.125f * 12f).sp,
        lineHeightStyle = devanagariSafe,
    )

    /** Body copy; `lineHeightRatio` mirrors Tailwind's `leading-snug` (1.375) / `relaxed` (1.625). */
    fun body(
        size: Float,
        weight: FontWeight = FontWeight.Normal,
        lineHeightRatio: Float = 1.375f,
        trackingEm: Float = 0f,
    ) = TextStyle(
        fontFamily = RsFonts.Body,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeightRatio).sp,
        letterSpacing = (trackingEm * size).sp,
        lineHeightStyle = devanagariSafe,
    )

    /** `.tnum`: tabular, lining numerals, used for every number that can change. */
    fun mono(size: Float, weight: FontWeight = FontWeight.Normal, lineHeightRatio: Float = 1.375f) = TextStyle(
        fontFamily = RsFonts.Mono,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeightRatio).sp,
        fontFeatureSettings = "tnum, lnum".replace(", ", ","),
        lineHeightStyle = devanagariSafe,
    )

    /** Tabular numerals on the body family (`tnum` applied to Inter text). */
    fun bodyTnum(size: Float, weight: FontWeight = FontWeight.Normal, lineHeightRatio: Float = 1.375f) =
        body(size, weight, lineHeightRatio).copy(fontFeatureSettings = "tnum,lnum")

    val tnumFeature: TextUnit get() = 0.sp
}
