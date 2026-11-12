package dev.realscale.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** DESIGN.md tokens — Warm Minimalism: cream canvas, coral primary, editorial serif display. */
object Warm {
    val Primary = Color(0xFFCC785C); val PrimaryActive = Color(0xFFA9583E); val PrimaryDisabled = Color(0xFFE6DFD8)
    val Ink = Color(0xFF141413); val Body = Color(0xFF3D3D3A); val BodyStrong = Color(0xFF252523)
    val Muted = Color(0xFF6C6A64); val MutedSoft = Color(0xFF8E8B82)
    val Hairline = Color(0xFFE6DFD8); val HairlineSoft = Color(0xFFEBE6DF)
    val Canvas = Color(0xFFFAF9F5); val SurfaceSoft = Color(0xFFF5F0E8)
    val SurfaceCard = Color(0xFFEFE9DE); val SurfaceCreamStrong = Color(0xFFE8E0D2)
    val Dark = Color(0xFF181715); val DarkElevated = Color(0xFF252320); val DarkSoft = Color(0xFF1F1E1B)
    val OnPrimary = Color(0xFFFFFFFF); val OnDark = Color(0xFFFAF9F5); val OnDarkSoft = Color(0xFFA09D96)
    val Teal = Color(0xFF5DB8A6); val Amber = Color(0xFFE8A55A)
    val Success = Color(0xFF5DB872); val Warning = Color(0xFFD4A017); val Error = Color(0xFFC64545)
}

private fun serif(size: Int, line: Int, spacing: Double) =
    TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)
private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val WarmTypography = Typography(
    displayLarge = serif(48, 52, -1.0), displayMedium = serif(36, 41, -0.5), displaySmall = serif(28, 34, -0.3),
    titleLarge = sans(22, 29, FontWeight.Medium), titleMedium = sans(18, 25, FontWeight.Medium), titleSmall = sans(16, 22, FontWeight.Medium),
    bodyLarge = sans(16, 25), bodySmall = sans(14, 22),
    labelMedium = sans(13, 18, FontWeight.Medium), labelLarge = sans(14, 14, FontWeight.Medium),
)

val WarmShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
)

@Composable fun RealScaleTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Warm.Primary, onPrimary = Warm.OnPrimary, background = Warm.Dark, onBackground = Warm.OnDark,
        surface = Warm.DarkSoft, onSurface = Warm.OnDark, surfaceVariant = Warm.DarkElevated, onSurfaceVariant = Warm.OnDarkSoft,
        outline = Warm.DarkElevated, error = Warm.Error,
    ) else lightColorScheme(
        primary = Warm.Primary, onPrimary = Warm.OnPrimary, background = Warm.Canvas, onBackground = Warm.Ink,
        surface = Warm.Canvas, onSurface = Warm.Ink, surfaceVariant = Warm.SurfaceCard, onSurfaceVariant = Warm.Body,
        outline = Warm.Hairline, error = Warm.Error,
    )
    MaterialTheme(colorScheme = colors, typography = WarmTypography, shapes = WarmShapes, content = content)
}
