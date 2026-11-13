package com.tumuyan.ncnn.realsr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Editorial typography pairing with Devanagari-safe font padding:
 * - Display & Headlines: Book Serif ALWAYS at weight 400 (Normal) with calm negative tracking.
 * - Body & UI Controls: Clean humanist sans-serif at weights 400 / 500 with generous 1.45–1.55x line height.
 * - PlatformTextStyle(includeFontPadding = true) prevents tall Devanagari vowel signs (ि, ी, े, ै, ो, ौ)
 *   and subscript matras (ु, ू, ृ) from clipping in Hindi (values-hi) and multi-script labels.
 */
val EditorialSerifFamily = FontFamily.Serif
val HumanistSansFamily = FontFamily.SansSerif
val ConsoleMonoFamily = FontFamily.Monospace

private val DevanagariSafePlatformStyle = PlatformTextStyle(includeFontPadding = true)

val RealScaleTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = EditorialSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.6).sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = EditorialSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = EditorialSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.35).sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = EditorialSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    titleLarge = TextStyle(
        fontFamily = EditorialSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    titleMedium = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    titleSmall = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    bodySmall = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    labelLarge = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    labelMedium = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
        platformStyle = DevanagariSafePlatformStyle
    ),
    labelSmall = TextStyle(
        fontFamily = HumanistSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        platformStyle = DevanagariSafePlatformStyle
    )
)
