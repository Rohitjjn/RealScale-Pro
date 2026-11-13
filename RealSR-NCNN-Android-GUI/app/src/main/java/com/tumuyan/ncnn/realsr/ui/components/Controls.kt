package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import com.tumuyan.ncnn.realsr.ui.theme.SpringEasing

/** Switch — design: 52×32 track, 2dp border, 20dp thumb, 6dp travel, 300ms spring. */
@Composable
fun RealScaleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalRealScaleColors.current
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) c.inverse else Color.Transparent,
        animationSpec = tween(200), label = "track",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) c.inverse else c.mutedSoft,
        animationSpec = tween(200), label = "border",
    )
    val thumbTravel by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(300, easing = SpringEasing), label = "thumb",
    )
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(percent = 50))
            .border(2.dp, borderColor, RoundedCornerShape(percent = 50))
            .background(trackColor)
            .clickable(
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = thumbTravel, end = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) c.onInverse else c.mutedSoft),
        )
    }
}

/** Segmented — design: bg-card container p-1, 40dp segments, sliding canvas indicator with soft shadow. */
@Composable
fun RealScaleSegmented(
    options: List<Pair<String, String>>, // value to label
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Segmented control",
) {
    val c = LocalRealScaleColors.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraintsCompat(modifier) { width ->
        val n = options.size
        val inner = width - 8.dp // container padding 4dp each side
        val seg = inner / n
        val indicatorOffset by animateDpAsState(
            targetValue = seg * index,
            animationSpec = tween(300, easing = SpringEasing), label = "indicator",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RealScaleShape.Md)
                .background(c.card)
                .padding(4.dp),
        ) {
            // indicator
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(seg)
                    .height(40.dp)
                    .clip(RealScaleShape.Sm)
                    .background(c.canvas),
            )
            Row(Modifier.fillMaxWidth()) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Box(
                        Modifier
                            .width(seg)
                            .height(40.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(value) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = RealScaleType.Body14Medium,
                            color = if (isSelected) c.ink else c.body,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsCompat(
    modifier: Modifier,
    content: @Composable (width: androidx.compose.ui.unit.Dp) -> Unit,
) = androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
    content(maxWidth)
}

/** Chip — design: min-h-11, 8dp radius, selected = inverse flip. */
@Composable
fun RealScaleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
) {
    val c = LocalRealScaleColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        !enabled -> Color.Transparent
        selected -> c.inverse
        pressed -> c.soft
        else -> c.canvas
    }
    val borderColor = when {
        !enabled -> c.hairlineSoft
        selected -> c.inverse
        else -> c.hairline
    }
    val fg = when {
        !enabled -> c.mutedSoft
        selected -> c.onInverse
        else -> c.ink
    }
    Column(
        modifier = modifier
            .clip(RealScaleShape.Md)
            .border(1.dp, borderColor, RealScaleShape.Md)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .heightIn(min = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = RealScaleType.Body14Medium, color = fg, maxLines = 1)
        if (sub != null) {
            Text(
                sub,
                style = RealScaleType.Caption12,
                color = if (selected) c.onInverseSoft else if (!enabled) c.mutedSoft else c.muted,
                maxLines = 1,
            )
        }
    }
}

/** Range slider — design: 4dp rounded track, ink fill, 24dp canvas thumb with 2dp ink ring, mono value readout. */
@Composable
fun RealScaleRange(
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
    suffix: String = "",
    step: Int = 1,
) {
    val c = LocalRealScaleColors.current
    val fraction = ((value - min).toFloat() / (max - min).coerceAtLeast(1)).coerceIn(0f, 1f)
    var trackWidthPx by remember { androidx.compose.runtime.mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
                .clearAndSetSemantics {
                    role = Role.Slider
                    stateDescription = "$value$suffix"
                }
                .pointerInput(min, max, step) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun valueFromX(x: Float): Int {
                            val w = size.width.coerceAtLeast(1)
                            val raw = min + ((x / w) * (max - min))
                            val stepped = (kotlin.math.round(raw / step) * step).toInt()
                            return stepped.coerceIn(min, max)
                        }
                        onValueChange(valueFromX(down.position.x))
                        drag(down.id) { change ->
                            change.consume()
                            onValueChange(valueFromX(change.position.x))
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // track
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(c.mutedSoft.copy(alpha = 0.45f)),
            )
            // fill
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(c.ink),
            )
            // thumb (24dp canvas circle, 2dp ink ring) positioned by measured width
            val thumbPx = with(density) { 24.dp.toPx() }
            val maxOffset = (trackWidthPx - thumbPx).coerceAtLeast(0f)
            Box(
                Modifier
                    .offset(x = with(density) { (maxOffset * fraction).toDp() })
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(c.canvas)
                    .border(2.dp, c.ink, CircleShape),
            )
        }
        Text(
            text = "$value$suffix",
            style = RealScaleType.tnum(RealScaleType.Mono13),
            color = c.ink,
            modifier = Modifier.padding(start = 12.dp).widthIn(min = 44.dp),
        )
    }
}

/** Stepper — design: bordered group, 44dp square buttons, mono value. */
@Composable
fun RealScaleStepper(
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
    format: ((Int) -> String)? = null,
) {
    val c = LocalRealScaleColors.current
    Row(
        modifier = modifier
            .clip(RealScaleShape.Md)
            .border(1.dp, c.hairline, RealScaleShape.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun StepButton(increase: Boolean) {
            val enabled = if (increase) value < max else value > min
            val iconRes = if (increase) R.drawable.ic_lucide_plus else R.drawable.ic_lucide_minus
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RealScaleShape.Md)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = { onValueChange((value + if (increase) step else -step).coerceIn(min, max)) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = if (increase) "Increase $contentDescription" else "Decrease $contentDescription",
                    tint = if (enabled) c.ink else c.mutedSoft,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        StepButton(increase = false)
        Text(
            text = format?.invoke(value) ?: value.toString(),
            style = RealScaleType.tnum(RealScaleType.Mono13),
            color = c.ink,
            modifier = Modifier.widthIn(min = 64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton(increase = true)
    }
}

/** Field — design: 15px medium label, content, 13px muted hint. */
@Composable
fun RealScaleField(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    right: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalRealScaleColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = RealScaleType.Body15Medium, color = c.ink)
            if (right != null) right()
        }
        content()
        if (hint != null) {
            Text(hint, style = RealScaleType.Body13, color = c.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
