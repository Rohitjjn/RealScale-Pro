package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontFeature
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tumuyan.ncnn.realsr.R

/**
 * Type scale — ported from the design's utilities (index.css + @utility directives):
 *   .display  Cormorant Garamond 500, -0.02em tracking, 1.08 line-height, lining numerals
 *   .eyebrow  12px / 500 / 0.125em / uppercase
 *   .tnum     tabular lining numerals
 * Sizes in dp == design px (1rem = 16dp).
 */
object RealScaleFonts {
    val Cormorant = FontFamily(
        Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
        Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold),
    )
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
    val JetBrainsMono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    )
    /** Latin body/display fonts fall back to Noto Sans Devanagari for Devanagari codepoints. */
    val Devanagari = FontFamily(
        Font(R.font.noto_sans_devanagari_regular, FontWeight.Normal),
        Font(R.font.noto_sans_devanagari_medium, FontWeight.Medium),
    )
    val DevanagariBold = FontFamily(
        Font(R.font.noto_sans_devanagari_medium, FontWeight.SemiBold),
        Font(R.font.noto_sans_devanagari_medium, FontWeight.Bold),
    )
}

/** Letter-spacing: CSS `em` -> sp relative to the style's font size. */
private fun em(fontSizeSp: Float, em: Float) = (fontSizeSp * em).sp

@OptIn(ExperimentalTextApi::class)
object RealScaleType {
    private val flat = PlatformTextStyle(includeFontPadding = false)

    // Sizes lifted from the design's Tailwind classes (1px = 1dp unit, rounded).
    val Display44 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 44.sp, lineHeight = 47.52.sp, letterSpacing = em(44f, -0.02f), platformStyle = flat)
    val Display40 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 40.sp, lineHeight = 43.2.sp, letterSpacing = em(40f, -0.02f), platformStyle = flat)
    val Display38 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 38.sp, lineHeight = 41.04.sp, letterSpacing = em(38f, -0.02f), platformStyle = flat)
    val Display34 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 34.sp, lineHeight = 36.72.sp, letterSpacing = em(34f, -0.02f), platformStyle = flat)
    val Display28 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "lnum", fontSize = 28.sp, lineHeight = 38.sp, letterSpacing = em(28f, -0.02f), platformStyle = flat)
    val Display26 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 26.sp, lineHeight = 28.1.sp, letterSpacing = em(26f, -0.02f), platformStyle = flat)
    val Display24 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 24.sp, lineHeight = 25.9.sp, letterSpacing = em(24f, -0.02f), platformStyle = flat)
    val Display21 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 21.sp, lineHeight = 22.7.sp, letterSpacing = em(21f, -0.02f), platformStyle = flat)
    val Display18 = TextStyle(fontFamily = RealScaleFonts.Cormorant, fontWeight = FontWeight.Medium, fontFeatureSettings = "lnum", fontSize = 18.sp, lineHeight = 19.4.sp, letterSpacing = em(18f, -0.02f), platformStyle = flat)

    /** Body — Inter. Sizes mirror the design (text-[0.9375rem] = 15sp, text-sm = 14sp, text-xs = 12sp). */
    val Body15 = TextStyle(fontFamily = RealScaleFonts.Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.5.sp, platformStyle = flat)
    val Body15Medium = Body15.copy(fontWeight = FontWeight.Medium)
    val Body15Strong = Body15.copy(fontWeight = FontWeight.SemiBold)
    val Body14 = TextStyle(fontFamily = RealScaleFonts.Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, platformStyle = flat)
    val Body14Medium = Body14.copy(fontWeight = FontWeight.Medium)
    val Body13 = TextStyle(fontFamily = RealScaleFonts.Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp, platformStyle = flat)
    val Body13Strong = Body13.copy(fontWeight = FontWeight.Medium)
    val Body16 = TextStyle(fontFamily = RealScaleFonts.Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.6.sp, platformStyle = flat)
    val Body16Medium = Body16.copy(fontWeight = FontWeight.Medium)
    val Caption12 = TextStyle(fontFamily = RealScaleFonts.Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.8.sp, platformStyle = flat)

    /** .eyebrow — 12px, 500, 0.125em tracking, uppercase. */
    val Eyebrow = TextStyle(
        fontFamily = RealScaleFonts.Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.8.sp,
        letterSpacing = em(12f, 0.125f),
        platformStyle = flat,
    )

    val Mono13 = TextStyle(fontFamily = RealScaleFonts.JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.2.sp, platformStyle = flat)
    val Mono12 = TextStyle(fontFamily = RealScaleFonts.JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.8.sp, platformStyle = flat)
    val Mono12Medium = Mono12.copy(fontWeight = FontWeight.Medium)

    /** .tnum — tabular lining figures for any style. */
    fun tnum(base: TextStyle) = base.copy(fontFeatureSettings = "tnum,lnum")

    /** Devanagari-aware body fallback wrapper. */
    fun devanagari(size: Float, lineHeight: Float, weight: FontWeight = FontWeight.Normal) =
        TextStyle(
            fontFamily = if (weight >= FontWeight.Medium) RealScaleFonts.DevanagariBold else RealScaleFonts.Devanagari,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = lineHeight.sp,
            platformStyle = flat,
        )
}

/** Numeric feature flags helpers for ad-hoc styles. */
val TnumFeatures = listOf(FontFeature("tnum"), FontFeature("lnum"))
