package com.tumuyan.ncnn.realsr.ui.components

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.util.Locale

@Composable
fun BrandLogoMedallion(
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val colors = RealScaleThemeTokens.colors
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Image(
            painter = painterResource(id = R.drawable.brand_logo_medallion),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

@Composable
fun EditorialOverline(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = RealScaleThemeTokens.colors
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.2.sp
        ),
        color = colors.muted,
        modifier = modifier
    )
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val animScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        animScale == 0f || !ValueAnimator.areAnimatorsEnabled()
    }
}

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    containerColor: Color = RealScaleThemeTokens.colors.surfaceCard,
    borderColor: Color = RealScaleThemeTokens.colors.hairline,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = if (onClick != null) {
            modifier
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
        } else {
            modifier
        },
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun TerracottaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val colors = RealScaleThemeTokens.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = Spacing.minTouchTarget, minHeight = 52.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.terracotta,
            contentColor = colors.onTerracotta,
            disabledContainerColor = colors.hairline,
            disabledContentColor = colors.subtle
        ),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 14.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = Spacing.xs)
                    .size(18.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun QuietOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    tint: Color = RealScaleThemeTokens.colors.ink
) {
    val colors = RealScaleThemeTokens.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = Spacing.minTouchTarget, minHeight = Spacing.minTouchTarget),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceSoft,
            contentColor = tint,
            disabledContentColor = colors.subtle
        ),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 12.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = Spacing.xs)
                    .size(18.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun AccessibleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeDot: Boolean = false
) {
    val colors = RealScaleThemeTokens.colors
    Box(
        modifier = modifier
            .size(Spacing.minTouchTarget)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.ink,
            modifier = Modifier.size(22.dp)
        )
        if (badgeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.terracotta)
            )
        }
    }
}

@Composable
fun EditorialTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = RealScaleThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccessibleIconButton(
                    icon = com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons.ArrowBack,
                    contentDescription = "Navigate back",
                    onClick = onBack
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.hairline)
    }
}

@Composable
fun HairlineProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val colors = RealScaleThemeTokens.colors
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.hairline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.terracotta)
        )
    }
}

@Composable
fun IndeterminateHairlineBar(
    modifier: Modifier = Modifier
) {
    val colors = RealScaleThemeTokens.colors
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion) {
        HairlineProgressBar(fraction = 0.38f, modifier = modifier)
        return
    }
    val transition = rememberInfiniteTransition(label = "indeterminate_bar")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar_phase"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.hairline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (phase.value * 140f).dp.coerceAtMost(140.dp),
                    end = ((1f - phase.value) * 140f).dp.coerceAtMost(140.dp)
                )
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.terracotta)
        )
    }
}

fun formatBytesHuman(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 10.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

fun formatDurationHuman(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(1L)
    return if (totalSec < 60L) {
        "${totalSec}s"
    } else {
        val m = totalSec / 60L
        val s = totalSec % 60L
        "${m}m ${s}s"
    }
}
