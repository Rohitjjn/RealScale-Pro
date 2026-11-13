package com.tumuyan.ncnn.realsr.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.MotionDuration
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import com.tumuyan.ncnn.realsr.ui.theme.SpringEasing
import androidx.compose.material3.Text

/**
 * Bottom sheet — port of the design's <Sheet/>: scrim + panel sliding from the bottom,
 * a drag handle (36×4 pill) with drag-to-dismiss (>90dp or flick), Escape/back = close,
 * display-style title + circular close button, optional footer with hairline top border.
 * Animation: sheet-in 360ms spring / sheet-out 220ms ease-in / scrim fade 260/200ms.
 */
@Composable
fun RealScaleSheet(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    tall: Boolean = false,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val rendered = remember { mutableStateOf(open) }
    val scrimAlpha = remember { Animatable(0f) }
    val panelOffset = remember { mutableStateOf(1f) } // fraction of panel height (1 = offscreen)
    val density = LocalDensity.current
    val panelHeightPx = remember { mutableStateOf(1) }

    LaunchedEffect(open) {
        if (open) {
            rendered.value = true
            scrimAlpha.snapTo(0f)
            panelOffset.value = 1f
            scrimAlpha.animateTo(1f, tween(MotionDuration.Fade))
            panelOffset.value = 0f
        } else if (rendered.value) {
            scrimAlpha.animateTo(0f, tween(MotionDuration.FadeOut))
            panelOffset.value = 1f
            rendered.value = false
        }
    }
    if (!rendered.value) return

    BackHandler(enabled = open) { onClose() }

    val c = LocalRealScaleColors.current
    Box(modifier = modifier.fillMaxSize()) {
        // Scrim
        Box(
            Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value)
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        // Panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (tall) Modifier.fillMaxSize(0.9f) else Modifier)
                .onGloballyPositioned { panelHeightPx.value = it.size.height.coerceAtLeast(1) }
                .graphicsLayer { translationY = panelOffset.value * panelHeightPx.value }
                .navigationBarsPadding()
                .clip(RealScaleShape.SheetTop)
                .background(c.canvas),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (totalDrag > with(density) { 90.dp.toPx() }) onClose()
                            },
                            onDragCancel = {
                                if (totalDrag > with(density) { 90.dp.toPx() }) onClose()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount.y.coerceAtLeast(0f)
                                val current = panelOffset.value
                                // follow the finger downward only
                                val deltaFraction = (dragAmount.y / panelHeightPx.value).coerceAtLeast(0f)
                                if (dragAmount.y > 0f) {
                                    panelOffset.value = (current + deltaFraction).coerceIn(0f, 1f)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(c.strong),
                )
            }
            if (title != null) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = RealScaleType.Display28,
                        color = c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    RealScaleIconButton(
                        onClick = onClose,
                        variant = IconButtonVariant.Outline,
                        contentDescription = "Close",
                        icon = androidx.compose.ui.res.painterResource(com.tumuyan.ncnn.realsr.R.drawable.ic_lucide_x),
                    )
                }
            }
            Column(
                modifier = (if (tall) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 20.dp),
            ) {
                content()
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            }
            if (footer != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp, bottom = 0.dp),
                ) {
                    androidx.compose.material3.HorizontalDivider(color = LocalRealScaleColors.current.hairlineSoft, thickness = 1.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                    footer()
                }
            }
        }
    }
}

/** Confirmation sheet with primary/danger action + ghost cancel. */
@Composable
fun RealScaleConfirmSheet(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    cancelLabel: String = "Cancel",
    danger: Boolean = false,
) {
    RealScaleSheet(
        open = open,
        onClose = onClose,
        title = title,
        footer = {
            Column {
                RealScaleButton(
                    onClick = onConfirm, variant = if (danger) ButtonVariant.Danger else ButtonVariant.Primary,
                    size = ButtonSize.Lg, full = true, text = confirmLabel,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                RealScaleButton(onClick = onClose, variant = ButtonVariant.Ghost, full = true, text = cancelLabel)
                androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            }
        },
    ) {
        Text(body, style = RealScaleType.Body15, color = LocalRealScaleColors.current.body,
            modifier = Modifier.padding(bottom = 16.dp))
    }
}
