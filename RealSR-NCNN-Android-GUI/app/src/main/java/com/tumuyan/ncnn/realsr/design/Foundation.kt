package com.tumuyan.ncnn.realsr.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons

/* ============================================================================================ */
/* Brand                                                                                        */
/* ============================================================================================ */

/**
 * The RealScale-Pro eagle mark — the prototype's `logo.jpg` with the 24 % rounded corners it
 * applies in `ui.tsx`. `animate` plays the `pop` keyframe (scale 0.6 → 1.06 → 1) on first show.
 */
@Composable
fun Mark(size: Dp, modifier: Modifier = Modifier, animate: Boolean = false) {
    val scale = remember { Animatable(if (animate) 0.6f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
        }
    }
    Image(
        painter = painterResource(R.drawable.brand_mark),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(size * 0.24f)),
    )
}

/* ============================================================================================ */
/* Buttons                                                                                      */
/* ============================================================================================ */

enum class RsButtonVariant { Primary, Secondary, Tonal, Ghost, Dark, Danger }

enum class RsButtonSize { Lg, Md, Sm }

private fun RsButtonSize.minHeight(): Dp = when (this) {
    RsButtonSize.Lg -> 52.dp
    RsButtonSize.Md -> 44.dp
    RsButtonSize.Sm -> 36.dp
}

private fun RsButtonSize.horizontalPadding(): Dp = when (this) {
    RsButtonSize.Lg -> 20.dp
    RsButtonSize.Md -> 16.dp
    RsButtonSize.Sm -> 12.dp
}

fun RsButtonSize.textStyle() = when (this) {
    RsButtonSize.Lg -> RsType.body(16f, FontWeight.SemiBold, 1.2f)
    RsButtonSize.Md -> RsType.body(15f, FontWeight.Medium, 1.2f)
    RsButtonSize.Sm -> RsType.body(14f, FontWeight.Medium, 1.2f)
}

private fun RsButtonVariant.container(pressed: Boolean, enabled: Boolean, c: RsColors): Color = when {
    !enabled -> when (this) {
        RsButtonVariant.Primary -> c.disabled
        RsButtonVariant.Dark -> c.darkElevated
        else -> Color.Transparent
    }
    else -> when (this) {
        RsButtonVariant.Primary -> if (pressed) c.primaryActive else c.primary
        RsButtonVariant.Secondary -> if (pressed) c.soft else c.canvas
        RsButtonVariant.Tonal -> if (pressed) c.strong else c.card
        RsButtonVariant.Ghost -> if (pressed) c.card else Color.Transparent
        RsButtonVariant.Dark -> if (pressed) c.darkSoft else c.darkElevated
        RsButtonVariant.Danger -> if (pressed) c.soft else c.canvas
    }
}

private fun RsButtonVariant.contentColor(enabled: Boolean, c: RsColors): Color = when {
    !enabled -> when (this) {
        RsButtonVariant.Primary -> c.onDisabled
        RsButtonVariant.Dark -> c.onDarkSoft
        else -> c.mutedSoft
    }
    else -> when (this) {
        RsButtonVariant.Primary -> c.onPrimary
        RsButtonVariant.Dark -> c.onDark
        RsButtonVariant.Danger -> c.error
        else -> c.ink
    }
}

/**
 * The prototype's `Button`: 8 dp radius, 150 ms colour change, no ripple, and the pressed colour
 * taken from the variant table. `label` renders the standard label style; `content` is available
 * for the two-line call to action ("Upscale" + output size).
 */
@Composable
fun RsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RsButtonVariant = RsButtonVariant.Secondary,
    size: RsButtonSize = RsButtonSize.Md,
    enabled: Boolean = true,
    full: Boolean = false,
    icon: ImageVector? = null,
    iconSize: Dpf = Dpf(18f),
    label: String? = null,
    contentDescription: String? = null,
    content: (@Composable RowScope.(Color) -> Unit)? = null,
) {
    val c = LocalRsColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val container = variant.container(pressed, enabled, c)
    val fg = variant.contentColor(enabled, c)
    val shape = RoundedCornerShape(RsRadius.md.dp)
    Row(
        modifier = modifier
            .then(if (full) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = size.minHeight())
            .clip(shape)
            .then(
                if (variant == RsButtonVariant.Secondary || variant == RsButtonVariant.Danger) {
                    Modifier.border(1.dp, c.hairline, shape)
                } else {
                    Modifier
                },
            )
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = size.horizontalPadding(), vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize.value.dp))
            Spacer(Modifier.width(8.dp))
        }
        when {
            content != null -> content(fg)
            label != null -> Text(label, style = size.textStyle(), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small value holder so call sites read `iconSize = Dpf(22f)`. */
@JvmInline
value class Dpf(val value: Float)

enum class RsIconButtonVariant { Outline, Plain, Dark }

/** 40 dp circular icon button (`IconButton` in the prototype), 48 dp through its halo. */
@Composable
fun RsIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: RsIconButtonVariant = RsIconButtonVariant.Outline,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
) {
    val c = LocalRsColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when (variant) {
        RsIconButtonVariant.Outline -> if (pressed) c.soft else c.canvas
        RsIconButtonVariant.Plain -> if (pressed) c.card else Color.Transparent
        RsIconButtonVariant.Dark -> if (pressed) c.darkSoft else c.darkElevated.copy(alpha = 0.9f)
    }
    val tint = if (variant == RsIconButtonVariant.Dark) c.onDark else c.ink
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (variant == RsIconButtonVariant.Outline) Modifier.border(1.dp, c.hairline, CircleShape) else Modifier)
                .background(background)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/* ============================================================================================ */
/* Screen scaffolding                                                                           */
/* ============================================================================================ */

@Composable
fun Screen(
    modifier: Modifier = Modifier,
    background: Color = LocalRsColors.current.canvas,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(background), content = content)
}

/** Header of every pushed screen; `scrolled` fades the bottom hairline in (200 ms). */
@Composable
fun RsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    close: Boolean = false,
    scrolled: Boolean = false,
    right: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalRsColors.current
    val hairlineAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(200, easing = RsMotion.Ease),
        label = "topbar-hairline",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(c.canvas),
    ) {
        Row(
            modifier = Modifier.height(56.dp).fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onBack != null) {
                RsIconButton(
                    icon = if (close) LucideIcons.X else LucideIcons.ChevronLeft,
                    label = if (close) "Close" else "Back",
                    variant = RsIconButtonVariant.Plain,
                    iconSize = if (close) 22.dp else 24.dp,
                    onClick = onBack,
                )
            }
            Text(
                text = title,
                style = RsType.display(24f, FontWeight.SemiBold, 32.4f),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (onBack == null) 10.dp else 0.dp),
            )
            right()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline.copy(alpha = hairlineAlpha)))
    }
}

@Composable
fun RsBottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalRsColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.canvas)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        content = content,
    )
}

/* ============================================================================================ */
/* Grouped rows                                                                                 */
/* ============================================================================================ */

@Composable
fun RsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalRsColors.current
    val shape = RoundedCornerShape(RsRadius.lg.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.hairline, shape)
            .background(c.canvas),
        content = content,
    )
}

@Composable
fun GroupTopDivider() {
    val c = LocalRsColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
}

/**
 * One row of a [RsGroup]: icon, title, optional sub-line, value, trailing slot and chevron.
 * `topDivider` reproduces Tailwind's `divide-y`; the first row of a group passes `false`.
 */
@Composable
fun RsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    sub: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    topDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalRsColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(modifier.fillMaxWidth()) {
        if (topDivider) GroupTopDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .background(if (pressed && enabled) c.soft else Color.Transparent)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = RsType.body(15f, FontWeight.Medium, 1.3f),
                    color = if (enabled) c.ink else c.muted,
                )
                if (sub != null) {
                    Text(sub, style = RsType.body(13f, FontWeight.Normal, 1.3f), color = c.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = RsType.body(14f, FontWeight.Normal, 1.4f),
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
            if (chevron) {
                Icon(LucideIcons.ChevronRight, contentDescription = null, tint = c.mutedSoft, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/* ============================================================================================ */
/* Field, labels, badges                                                                        */
/* ============================================================================================ */

@Composable
fun RsField(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    right: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalRsColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                label,
                style = RsType.body(15f, FontWeight.Medium, 1.3f),
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            right()
        }
        content()
        if (hint != null) {
            Text(
                hint,
                style = RsType.body(13f, FontWeight.Normal, 1.3f),
                color = c.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun EyebrowLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalRsColors.current.muted) {
    Text(text = text.uppercase(), style = RsType.eyebrow, color = color, modifier = modifier)
}

enum class RsBadgeTone { Neutral, Strong, Amber, Outline, Dark }

@Composable
fun RsBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: RsBadgeTone = RsBadgeTone.Neutral,
    icon: ImageVector? = null,
    dot: RsDotTone? = null,
) {
    val c = LocalRsColors.current
    val shape = RoundedCornerShape(RsRadius.full.dp)
    val background = when (tone) {
        RsBadgeTone.Neutral -> c.card
        RsBadgeTone.Strong -> c.strong
        RsBadgeTone.Amber -> c.amber
        RsBadgeTone.Outline -> Color.Transparent
        RsBadgeTone.Dark -> c.darkElevated
    }
    val foreground = when (tone) {
        RsBadgeTone.Amber -> c.onAccent
        RsBadgeTone.Outline -> c.muted
        RsBadgeTone.Dark -> c.onDark
        else -> c.ink
    }
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (tone == RsBadgeTone.Outline) Modifier.border(1.dp, c.hairline, shape) else Modifier)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Dot(dot)
        if (icon != null) Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
        Text(text, style = RsType.body(12f, FontWeight.Medium, 1.6f), color = foreground, maxLines = 1)
    }
}

enum class RsDotTone { Good, Ok, No, On }

@Composable
fun Dot(tone: RsDotTone) {
    val c = LocalRsColors.current
    val color = when (tone) {
        RsDotTone.Good -> c.teal
        RsDotTone.Ok -> c.amber
        RsDotTone.No -> c.mutedSoft
        RsDotTone.On -> c.success
    }
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

@Composable
fun RadioMark(
    checked: Boolean,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    fillColor: Color? = null,
    tint: Color? = null,
) {
    val c = LocalRsColors.current
    val fill = fillColor ?: c.inverse
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) fill else Color.Transparent)
            .border(2.dp, if (checked) fill else (borderColor ?: c.mutedSoft), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(LucideIcons.Check, contentDescription = null, tint = tint ?: c.onInverse, modifier = Modifier.size(14.dp))
        }
    }
}

/* ============================================================================================ */
/* Progress                                                                                     */
/* ============================================================================================ */

@Composable
fun RsProgressBar(value: Float, label: String, modifier: Modifier = Modifier) {
    val c = LocalRsColors.current
    val shape = RoundedCornerShape(RsRadius.full.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(c.strong)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(shape)
                .background(c.primary),
        )
    }
}

/** 28 dp progress ring used by model downloads. */
@Composable
fun RsRing(value: Float, label: String, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val c = LocalRsColors.current
    Canvas(modifier.size(size).semantics { contentDescription = label }) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(c.strong, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            c.primary,
            -90f,
            360f * value.coerceIn(0f, 1f),
            false,
            Offset(inset, inset),
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/* ============================================================================================ */
/* Shimmer, empty states                                                                        */
/* ============================================================================================ */

/** `.skeleton`: a shimmering card→soft→card sweep at 1.5 s, exactly like the prototype. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(RsRadius.sm.dp)) {
    val c = LocalRsColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 200f,
        targetValue = -200f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-phase",
    )
    Box(
        modifier
            .clip(shape)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(c.card, c.soft, c.card),
                        start = Offset(size.width * (phase / 100f) / 2f, 0f),
                        end = Offset(size.width * (1f + (phase / 100f) / 2f), size.height),
                    ),
                )
            }
            .background(c.card, shape),
    )
}

@Composable
fun SquareIcon(icon: ImageVector, size: Dp = 64.dp, background: Color? = null, tint: Color? = null) {
    val c = LocalRsColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(RsRadius.xl.dp))
            .background(background ?: c.card),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: c.ink, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    art: @Composable () -> Unit = {},
    action: @Composable () -> Unit = {},
) {
    val c = LocalRsColors.current
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        art()
        Text(
            title,
            style = RsType.display(32f),
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            body,
            style = RsType.body(15f, FontWeight.Normal, 1.5f),
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).width(256.dp),
        )
        Spacer(Modifier.height(24.dp))
        action()
    }
}

@Composable
fun SpacerH(height: Dp) {
    Spacer(Modifier.height(height))
}

@Composable
fun SpacerW(width: Dp) {
    Spacer(Modifier.width(width))
}
