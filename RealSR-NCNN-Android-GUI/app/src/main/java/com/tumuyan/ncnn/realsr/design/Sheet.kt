package com.tumuyan.ncnn.realsr.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons

/**
 * The prototype's bottom sheet: scrim fade (260 ms in / 200 ms out), panel slides up on the spring
 * over 360 ms, 20 dp top corners, 90 % max height, drag-down-to-dismiss past 90 dp.
 *
 * It is hand-built rather than Material3's `ModalBottomSheet` because the prototype's geometry
 * (12 dp handle bar, 28 sp serif title, hairline-separated footer) is part of the design.
 */
@Composable
fun RsSheet(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    tall: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalRsColors.current
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val dismissThreshold = with(density) { 90.dp.toPx() }
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    var dragOffset by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val followOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = if (dragging) tween(0) else tween(220, easing = RsMotion.Ease),
        label = "sheet-drag",
    )

    LaunchedEffect(open) {
        if (!open) {
            dragOffset = 0f
            dragging = false
        }
    }

    AnimatedVisibility(
        visible = open,
        enter = fadeIn(tween(RsMotion.fadeMs, easing = RsMotion.Ease)),
        exit = fadeOut(tween(200, easing = RsMotion.Ease)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
    }

    AnimatedVisibility(
        visible = open,
        enter = slideInVertically(
            animationSpec = if (reduce) tween(1) else tween(RsMotion.sheetInMs, easing = RsMotion.Spring),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = tween(if (reduce) 1 else RsMotion.sheetOutMs, easing = RsMotion.Ease),
            targetOffsetY = { it + dragOffset.toInt() },
        ),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (tall) Modifier.fillMaxHeight(0.9f) else Modifier.heightIn(max = maxPanelHeight))
                    .offset { IntOffset(0, followOffset.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = RsRadius.xxl.dp, topEnd = RsRadius.xxl.dp))
                    .background(c.canvas)
                    .semantics { contentDescription = title ?: "Dialog" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Handle: 24 dp tall hit strip with a 36 x 4 dp bar.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(open) {
                            detectVerticalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    if (dragOffset > dismissThreshold) {
                                        dragOffset = 0f
                                        onClose()
                                    } else {
                                        dragOffset = 0f
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragOffset = 0f
                                },
                            ) { _, dragAmount ->
                                dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c.strong),
                    )
                }
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = title,
                            style = RsType.display(28f),
                            color = c.ink,
                            modifier = Modifier.weight(1f).padding(top = 4.dp),
                        )
                        RsIconButton(icon = LucideIcons.X, label = "Close", onClick = onClose)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = tall)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    content = content,
                )
                if (footer != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        content = footer,
                    )
                } else {
                    Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(12.dp))
                }
            }
        }
    }
}

/** `ConfirmSheet`: a title, body copy, one confirm button and a ghost cancel. */
@Composable
fun RsConfirmSheet(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    cancelLabel: String = "Cancel",
    danger: Boolean = false,
) {
    val c = LocalRsColors.current
    RsSheet(
        open = open,
        onClose = onClose,
        title = title,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RsButton(
                    onClick = onConfirm,
                    variant = if (danger) RsButtonVariant.Danger else RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    label = confirmLabel,
                )
                RsButton(onClick = onClose, variant = RsButtonVariant.Ghost, full = true, label = cancelLabel)
            }
        },
    ) {
        Text(
            text = body,
            style = RsType.body(15f, FontWeight.Normal, 1.6f),
            color = c.body,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

/**
 * Toast: bottom 112 dp, centered, inverse surface, `rise-in` 450 ms. The prototype keeps it for
 * 3 s; the host owns that timing.
 */
@Composable
fun RsToastHost(message: String?, modifier: Modifier = Modifier) {
    val c = LocalRsColors.current
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(RsMotion.fadeMs, easing = RsMotion.Ease)) +
            slideInVertically(tween(RsMotion.riseMs, easing = RsMotion.Spring)) { it / 2 },
        exit = fadeOut(tween(200, easing = RsMotion.Ease)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .padding(bottom = 112.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.inverse)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    style = RsType.body(14f, FontWeight.Normal, 1.4f),
                    color = c.onInverse,
                )
            }
        }
    }
}

@Composable
fun SheetGap(height: Int = 16) {
    Spacer(Modifier.height(height.dp))
}

/** Section divider used inside sheets (`border-t border-hairline-soft`). */
@Composable
fun HairlineSoftDivider(modifier: Modifier = Modifier) {
    val c = LocalRsColors.current
    Box(modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = LocalRsColors.current.hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
