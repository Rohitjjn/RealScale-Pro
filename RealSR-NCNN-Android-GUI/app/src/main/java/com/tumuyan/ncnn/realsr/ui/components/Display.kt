package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/* ---------- Badge ---------- */
enum class BadgeTone { Neutral, Strong, Amber, Outline, Dark }

@Composable
fun RealScaleBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
    leading: @Composable (() -> Unit)? = null,
) {
    val c = LocalRealScaleColors.current
    val (bg, fg, border) = when (tone) {
        BadgeTone.Neutral -> Triple(c.card, c.ink, null)
        BadgeTone.Strong -> Triple(c.strong, c.ink, null)
        BadgeTone.Amber -> Triple(c.amber, c.onAccent, null)
        BadgeTone.Outline -> Triple(Color.Transparent, c.muted, c.hairline)
        BadgeTone.Dark -> Triple(c.darkElevated, c.onDark, null)
    }
    Row(
        modifier = modifier
            .clip(RealScaleShape.Pill)
            .then(if (border != null) Modifier.border(1.dp, border, RealScaleShape.Pill) else Modifier)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        Text(text, style = RealScaleType.Caption12, color = fg, maxLines = 1)
    }
}

/* ---------- Dot ---------- */
enum class DotTone { Good, Ok, No, On }

@Composable
fun RealScaleDot(tone: DotTone, modifier: Modifier = Modifier) {
    val c = LocalRealScaleColors.current
    val color = when (tone) {
        DotTone.Good -> c.teal
        DotTone.Ok -> c.amber
        DotTone.No -> c.mutedSoft
        DotTone.On -> c.success
    }
    Box(modifier.size(6.dp).clip(CircleShape).background(color))
}

/* ---------- RadioMark ---------- */
@Composable
fun RealScaleRadioMark(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalRealScaleColors.current
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (checked) Modifier.background(c.inverse) else Modifier)
            .border(2.dp, if (checked) c.inverse else c.mutedSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_check),
                contentDescription = null,
                tint = c.onInverse,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/* ---------- Group ---------- */
@Composable
fun RealScaleGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = LocalRealScaleColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RealScaleShape.Lg)
            .border(1.dp, c.hairline, RealScaleShape.Lg)
            .background(c.canvas),
    ) {
        content()
    }
}

@Composable
fun RealScaleDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = LocalRealScaleColors.current.hairlineSoft,
        thickness = 1.dp,
    )
}

/* ---------- Row ---------- */
@Composable
fun RealScaleRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    value: String? = null,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    disabled: Boolean = false,
    titleColor: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalRealScaleColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (pressed && onClick != null) c.soft else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 10.dp)
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    painter = icon, contentDescription = null,
                    tint = if (disabled) c.mutedSoft else c.ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = RealScaleType.Body15Medium,
                    color = titleColor ?: if (disabled) c.mutedSoft else c.ink,
                    maxLines = 2,
                )
                if (sub != null) {
                    Text(
                        sub,
                        style = RealScaleType.Body13,
                        color = c.muted,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2,
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = RealScaleType.Body14,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }
            if (trailing != null) trailing()
            if (chevron) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_chevron_right),
                    contentDescription = null, tint = c.mutedSoft,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    if (onClick != null) {
        Box(
            modifier = modifier.clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !disabled,
                role = Role.Button,
                onClick = onClick,
            ),
        ) { content() }
    } else {
        Box(modifier = modifier) { content() }
    }
}

/** Switch row — icon, title, sub, and a trailing switch; the whole row toggles. */
@Composable
fun RealScaleSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    enabled: Boolean = true,
) {
    val c = LocalRealScaleColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) c.ink else c.mutedSoft, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RealScaleType.Body15Medium, color = if (enabled) c.ink else c.mutedSoft)
            if (sub != null) {
                Text(sub, style = RealScaleType.Body13, color = c.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        RealScaleSwitch(checked = checked, onCheckedChange = onCheckedChange, contentDescription = title, enabled = enabled)
    }
}

/* ---------- ProgressBar ---------- */
@Composable
fun RealScaleProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    contentDescription: String = "Progress",
) {
    val c = LocalRealScaleColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RealScaleShape.Pill)
            .background(c.strong),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RealScaleShape.Pill)
                .background(c.primary),
        )
    }
}

/* ---------- Ring (download progress) ---------- */
@Composable
fun RealScaleRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String = "Progress",
) {
    val c = LocalRealScaleColors.current
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(size),
    ) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(
            color = c.strong,
            radius = (this.size.minDimension - stroke.width) / 2f,
            style = stroke,
        )
        val sweep = 360f * progress.coerceIn(0f, 1f)
        drawArc(
            color = c.primary,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            style = stroke,
        )
    }
}
