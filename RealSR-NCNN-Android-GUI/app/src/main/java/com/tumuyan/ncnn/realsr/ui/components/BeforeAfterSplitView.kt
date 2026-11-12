package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Interactive Before / After draggable split comparison view with pinch-to-zoom & pan.
 */
@Composable
fun BeforeAfterSplitView(
    beforeFile: File,
    afterFile: File,
    modifier: Modifier = Modifier
) {
    val colors = RealScaleThemeTokens.colors
    var splitFraction by remember(afterFile.absolutePath) { mutableFloatStateOf(0.5f) }
    var zoom by remember(afterFile.absolutePath) { mutableFloatStateOf(1f) }
    var panX by remember(afterFile.absolutePath) { mutableFloatStateOf(0f) }
    var panY by remember(afterFile.absolutePath) { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceSoft,
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 0.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.extraLarge)
                .pointerInput(afterFile.absolutePath) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                        if (newZoom <= 1.01f) {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        } else {
                            val maxX = (size.width * (newZoom - 1f)) / 2f
                            val maxY = (size.height * (newZoom - 1f)) / 2f
                            zoom = newZoom
                            panX = (panX + pan.x).coerceIn(-maxX, maxX)
                            panY = (panY + pan.y).coerceIn(-maxY, maxY)
                        }
                    }
                }
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val splitX = (widthPx * splitFraction).roundToInt()
            val handleRadiusPx = with(density) { 24.dp.toPx() }.roundToInt()

            // Full-bleed AFTER (Upscaled) layer underneath
            AsyncImage(
                model = afterFile,
                contentDescription = "Upscaled image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX
                        translationY = panY
                    }
            )

            // Clipped BEFORE (Original) layer on left side [0 .. splitFraction]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(
                            left = 0f,
                            top = 0f,
                            right = size.width * splitFraction,
                            bottom = size.height
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                AsyncImage(
                    model = beforeFile,
                    contentDescription = "Original image before upscaling",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX
                            translationY = panY
                        }
                )
            }

            // Hairline vertical split line
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = splitX - 1, y = 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(colors.canvas.copy(alpha = 0.92f))
            )

            // Accessible 48dp draggable handle on the split line
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x = splitX - handleRadiusPx, y = 0) }
                    .size(Spacing.minTouchTarget)
                    .semantics {
                        contentDescription = "Drag left or right to compare original and upscaled image"
                    }
                    .pointerInput(widthPx) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            splitFraction = (splitFraction + dragAmount / widthPx).coerceIn(0.03f, 0.97f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.terracotta,
                    border = BorderStroke(1.5.dp, colors.onTerracotta),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = RealScaleIcons.SplitHandle,
                            contentDescription = null,
                            tint = colors.onTerracotta,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quiet Original / Upscaled corner pills
            CornerLabelPill(
                label = "Original",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.sm)
            )
            CornerLabelPill(
                label = "Upscaled",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
            )

            // Reset zoom pill when zoomed in
            if (zoom > 1.05f) {
                Surface(
                    shape = CircleShape,
                    color = colors.canvas.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, colors.hairline),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.sm)
                        .clip(CircleShape)
                        .clickable {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f× · Tap to reset zoom", zoom),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CornerLabelPill(
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = RealScaleThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.canvas.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
