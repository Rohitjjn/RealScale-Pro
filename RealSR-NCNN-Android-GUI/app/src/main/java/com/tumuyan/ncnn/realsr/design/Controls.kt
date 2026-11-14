package com.tumuyan.ncnn.realsr.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import kotlin.math.roundToInt

/* ============================================================================================ */
/* Switch                                                                                       */
/* ============================================================================================ */

/**
 * The prototype's switch: a 52 x 32 dp pill with a 2 dp border, and a 20 dp knob that travels
 * 20 dp on a 300 ms spring.
 */
@Composable
fun RsSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalRsColors.current
    val offset by animateFloatAsState(
        targetValue = if (checked) 24f else 4f,
        animationSpec = tween(300, easing = RsMotion.Spring),
        label = "switch-knob",
    )
    val trackColor by animateFloatAsState(if (checked) 1f else 0f, tween(200, easing = RsMotion.Ease), label = "switch-track")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onChange(!checked) },
            )
            .clip(CircleShape)
            .background(lerpColor(Color.Transparent, c.inverse, trackColor))
            .border(2.dp, if (checked) c.inverse else c.mutedSoft, CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = offset.dp)
                .align(Alignment.CenterStart)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) c.onInverse else c.mutedSoft),
        )
    }
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color = androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))

@Composable
fun RsSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    sub: String? = null,
    enabled: Boolean = true,
    topDivider: Boolean = true,
) {
    val c = LocalRsColors.current
    Column(modifier.fillMaxWidth()) {
        if (topDivider) GroupTopDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = { onChange(!checked) },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = RsType.body(15f, FontWeight.Medium, 1.3f), color = c.ink)
                if (sub != null) {
                    Text(sub, style = RsType.body(13f, FontWeight.Normal, 1.3f), color = c.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            RsSwitch(checked = checked, onChange = onChange, label = title, enabled = enabled)
        }
    }
}

/* ============================================================================================ */
/* Segmented control                                                                            */
/* ============================================================================================ */

@Composable
fun <T> RsSegmented(
    label: String,
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRsColors.current
    val count = options.size
    val index = options.indexOfFirst { it.first == value }.coerceAtLeast(0).coerceAtMost(count - 1)
    var containerWidth by remember { mutableIntStateOf(0) }
    val thumbWidthPx = ((containerWidth - 8f) / count).coerceAtLeast(0f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.card)
            .padding(4.dp)
            .onSizeChanged { containerWidth = it.width }
            .semantics { contentDescription = label },
    ) {
        // The thumb slides with the 300 ms spring, exactly like the prototype's transform.
        Box(
            Modifier
                .offset { IntOffset((index * thumbWidthPx).roundToInt(), 0) }
                .width(with(LocalDensity.current) { (thumbWidthPx / density).dp })
                .height(40.dp)
                .clip(RoundedCornerShape(RsRadius.sm.dp))
                .background(c.canvas),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (option, text) ->
                val selectedOption = option == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(RoundedCornerShape(RsRadius.sm.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { if (!selectedOption) onChange(option) },
                        )
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .semantics { stateDescription = if (selectedOption) "Selected" else "Not selected" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = RsType.body(14f, FontWeight.Medium, 1.15f),
                        color = if (selectedOption) c.ink else c.body,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* ============================================================================================ */
/* Chip                                                                                         */
/* ============================================================================================ */

@Composable
fun RsChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
) {
    val c = LocalRsColors.current
    val shape = RoundedCornerShape(RsRadius.md.dp)
    val container = when {
        !enabled -> Color.Transparent
        selected -> c.inverse
        else -> c.canvas
    }
    val border = when {
        !enabled -> c.hairlineSoft
        selected -> c.inverse
        else -> c.hairline
    }
    val textColor = when {
        !enabled -> c.mutedSoft
        selected -> c.onInverse
        else -> c.ink
    }
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .border(1.dp, border, shape)
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = RsType.body(14f, FontWeight.Medium, 1.15f), color = textColor, maxLines = 1)
        if (sub != null) {
            Text(
                sub,
                style = RsType.body(12f, FontWeight.Normal, 1.2f),
                color = if (selected) c.onInverseSoft else if (enabled) c.muted else c.mutedSoft,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/* ============================================================================================ */
/* Range slider                                                                                 */
/* ============================================================================================ */

/**
 * The prototype uses a native `<input type="range">` styled with CSS: a 4 dp track (ink up to the
 * value, then muted-soft at 45 % alpha) and a 24 dp thumb with a 2 dp ink ring on the canvas
 * colour. Reimplemented with a Canvas + drag handling so it matches on every API level.
 */
@Composable
fun RsSlider(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    suffix: String = "",
) {
    val c = LocalRsColors.current
    var widthPx by remember { mutableIntStateOf(0) }
    fun valueAt(x: Float): Int {
        if (widthPx <= 0) return value
        val fraction = (x / widthPx.toFloat()).coerceIn(0f, 1f)
        val raw = min.toFloat() + fraction * (max - min).toFloat()
        val stepped = (Math.round(raw.toDouble() / step) * step).toInt()
        return stepped.coerceIn(min, max)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .onSizeChanged { widthPx = it.width }
                .semantics {
                    contentDescription = label
                    setProgress { target ->
                        onChange(target.roundToInt().coerceIn(min, max))
                        true
                    }
                }
                .pointerInput(min, max, step, widthPx) {
                    detectTapGestures { offset -> onChange(valueAt(offset.x)) }
                }
                .pointerInput(min, max, step, widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> onChange(valueAt(offset.x)) },
                    ) { change, _ -> onChange(valueAt(change.position.x)) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val trackHeight = 4.dp.toPx()
                val top = (size.height - trackHeight) / 2f
                val fraction = ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
                val radius = trackHeight / 2f
                drawRoundRect(
                    color = c.mutedSoft.copy(alpha = 0.45f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = c.ink,
                    topLeft = Offset(0f, top),
                    size = Size(size.width * fraction, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                )
                val thumbRadius = 12.dp.toPx()
                val cx = (size.width * fraction).coerceIn(thumbRadius, size.width - thumbRadius)
                drawCircle(color = c.canvas, radius = thumbRadius, center = Offset(cx, size.height / 2f))
                drawCircle(
                    color = c.ink,
                    radius = thumbRadius,
                    center = Offset(cx, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Text(
            text = "$value$suffix",
            style = RsType.mono(13f),
            color = c.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}

/* ============================================================================================ */
/* Stepper                                                                                      */
/* ============================================================================================ */

/** `Stepper`: 44 dp square minus/plus buttons around a mono value, 8 dp radius, hairline border. */
@Composable
fun RsStepper(
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    format: ((Int) -> String)? = null,
) {
    val c = LocalRsColors.current
    val shape = RoundedCornerShape(RsRadius.md.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, c.hairline, shape)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = value > min,
                    role = Role.Button,
                    onClick = { onChange((value - step).coerceAtLeast(min)) },
                )
                .semantics { contentDescription = "Decrease $label" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LucideIcons.Minus, contentDescription = null, tint = if (value > min) c.ink else c.mutedSoft, modifier = Modifier.size(16.dp))
        }
        Text(
            text = format?.invoke(value) ?: value.toString(),
            style = RsType.mono(14f),
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        Box(
            Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = value < max,
                    role = Role.Button,
                    onClick = { onChange((value + step).coerceAtMost(max)) },
                )
                .semantics { contentDescription = "Increase $label" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LucideIcons.Plus, contentDescription = null, tint = if (value < max) c.ink else c.mutedSoft, modifier = Modifier.size(16.dp))
        }
    }
}
