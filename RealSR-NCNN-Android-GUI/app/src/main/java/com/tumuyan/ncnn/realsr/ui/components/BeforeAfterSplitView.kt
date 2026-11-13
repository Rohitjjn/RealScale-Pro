package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Memory-safe Before / After draggable split viewer:
 * - Decodes bounded (`<= 2048 px`) downsampled base previews on IO so arbitrarily large 4× outputs never OOM.
 * - Streams high-resolution native-pixel viewport crops via [android.graphics.BitmapRegionDecoder] when zoomed in.
 * - Supports synchronized pinch-zoom & pan, double-tap 100% <-> Fit toggle, hold-for-original, and a dual-contrast split line.
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
    var isHoldingOriginal by remember(afterFile.absolutePath) { mutableStateOf(false) }

    var beforePreview by remember(beforeFile.absolutePath) { mutableStateOf<DecodedPreviewImage?>(null) }
    var afterPreview by remember(afterFile.absolutePath) { mutableStateOf<DecodedPreviewImage?>(null) }
    var beforeTile by remember(beforeFile.absolutePath) { mutableStateOf<DecodedViewportTile?>(null) }
    var afterTile by remember(afterFile.absolutePath) { mutableStateOf<DecodedViewportTile?>(null) }

    // Load OOM-safe downsampled base previews
    LaunchedEffect(beforeFile.absolutePath, afterFile.absolutePath) {
        val (b, a) = withContext(Dispatchers.IO) {
            OomSafeImageDecoders.decodeDownsampledPreview(beforeFile, maxEdgePx = 2048) to
                OomSafeImageDecoders.decodeDownsampledPreview(afterFile, maxEdgePx = 2048)
        }
        beforePreview = b
        afterPreview = a
        beforeTile = null
        afterTile = null
    }

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
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val effectiveSplit = if (isHoldingOriginal) 1f else splitFraction
            val splitX = (viewportWidthPx * effectiveSplit).roundToInt()
            val handleRadiusPx = with(density) { 24.dp.toPx() }.roundToInt()

            val afterImgW = afterPreview?.intrinsicWidth ?: 0
            val afterImgH = afterPreview?.intrinsicHeight ?: 0

            // Stream high-res BitmapRegionDecoder tile on IO whenever zoomed past 1.15x
            LaunchedEffect(
                afterFile.absolutePath,
                beforeFile.absolutePath,
                afterImgW,
                afterImgH,
                viewportWidthPx,
                viewportHeightPx,
                zoom,
                panX,
                panY
            ) {
                if (zoom <= 1.15f || afterImgW <= 0 || afterImgH <= 0) {
                    afterTile = null
                    beforeTile = null
                    return@LaunchedEffect
                }
                delay(85L)
                val (newBeforeTile, newAfterTile) = withContext(Dispatchers.IO) {
                    val afterRect = OomSafeImageDecoders.computeVisibleSourceRect(
                        imageWidth = afterImgW,
                        imageHeight = afterImgH,
                        viewportWidth = viewportWidthPx,
                        viewportHeight = viewportHeightPx,
                        zoom = zoom,
                        panX = panX,
                        panY = panY
                    )
                    val bW = beforePreview?.intrinsicWidth ?: 0
                    val bH = beforePreview?.intrinsicHeight ?: 0
                    val beforeRect = if (bW > 0 && bH > 0) {
                        OomSafeImageDecoders.computeVisibleSourceRect(
                            imageWidth = bW,
                            imageHeight = bH,
                            viewportWidth = viewportWidthPx,
                            viewportHeight = viewportHeightPx,
                            zoom = zoom,
                            panX = panX,
                            panY = panY
                        )
                    } else {
                        null
                    }
                    val decodedBefore = beforeRect?.let {
                        OomSafeImageDecoders.decodeHighResRegionTile(beforeFile, it, maxTileEdgePx = 2048)
                    }
                    val decodedAfter = afterRect?.let {
                        OomSafeImageDecoders.decodeHighResRegionTile(afterFile, it, maxTileEdgePx = 2048)
                    }
                    decodedBefore to decodedAfter
                }
                beforeTile = newBeforeTile
                afterTile = newAfterTile
            }

            val beforeImageBitmap = remember(beforePreview) { beforePreview?.bitmap?.asImageBitmap() }
            val afterImageBitmap = remember(afterPreview) { afterPreview?.bitmap?.asImageBitmap() }
            val beforeTileBitmap = remember(beforeTile) { beforeTile?.bitmap?.asImageBitmap() }
            val afterTileBitmap = remember(afterTile) { afterTile?.bitmap?.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "Before and after image comparison"
                    }
                    .pointerInput(afterFile.absolutePath, afterImgW, afterImgH, viewportWidthPx, viewportHeightPx) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (zoom > 1.08f) {
                                    zoom = 1f
                                    panX = 0f
                                    panY = 0f
                                } else {
                                    val targetZoom = OomSafeImageDecoders.computeOneToOneZoom(
                                        imageWidth = afterImgW,
                                        imageHeight = afterImgH,
                                        viewportWidth = viewportWidthPx,
                                        viewportHeight = viewportHeightPx
                                    )
                                    val cx = size.width * 0.5f
                                    val cy = size.height * 0.5f
                                    val maxX = (size.width * (targetZoom - 1f)) * 0.5f
                                    val maxY = (size.height * (targetZoom - 1f)) * 0.5f
                                    zoom = targetZoom
                                    panX = ((cx - tapOffset.x) * (targetZoom - 1f)).coerceIn(-maxX, maxX)
                                    panY = ((cy - tapOffset.y) * (targetZoom - 1f)).coerceIn(-maxY, maxY)
                                }
                            },
                            onPress = {
                                coroutineScope {
                                    val holdJob = launch {
                                        delay(220L)
                                        isHoldingOriginal = true
                                    }
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        holdJob.cancel()
                                        isHoldingOriginal = false
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(afterFile.absolutePath) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            isHoldingOriginal = false
                            val newZoom = (zoom * gestureZoom).coerceIn(1f, 12f)
                            if (newZoom <= 1.01f) {
                                zoom = 1f
                                panX = 0f
                                panY = 0f
                            } else {
                                val maxX = (size.width * (newZoom - 1f)) * 0.5f
                                val maxY = (size.height * (newZoom - 1f)) * 0.5f
                                zoom = newZoom
                                panX = (panX + pan.x).coerceIn(-maxX, maxX)
                                panY = (panY + pan.y).coerceIn(-maxY, maxY)
                            }
                        }
                    }
            ) {
                val center = Offset(size.width * 0.5f, size.height * 0.5f)

                // 1. Draw AFTER (Upscaled) full layer
                if (afterPreview != null && afterImageBitmap != null) {
                    drawPreviewAndOptionalTile(
                        preview = afterPreview!!,
                        previewImg = afterImageBitmap,
                        tile = afterTile,
                        tileImg = afterTileBitmap,
                        zoom = zoom,
                        panX = panX,
                        panY = panY,
                        pivot = center
                    )
                }

                // 2. Draw BEFORE (Original) clipped to [0 .. effectiveSplit]
                if (beforePreview != null && beforeImageBitmap != null) {
                    clipRect(
                        left = 0f,
                        top = 0f,
                        right = size.width * effectiveSplit,
                        bottom = size.height
                    ) {
                        drawPreviewAndOptionalTile(
                            preview = beforePreview!!,
                            previewImg = beforeImageBitmap,
                            tile = beforeTile,
                            tileImg = beforeTileBitmap,
                            zoom = zoom,
                            panX = panX,
                            panY = panY,
                            pivot = center
                        )
                    }
                }
            }

            if (!isHoldingOriginal) {
                // Dual-contrast vertical split line (dark outer shadow + light inner hairline)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = splitX - 2, y = 0) }
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(Color(0x99141413))
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = splitX - 1, y = 0) }
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color(0xEBFAF9F5))
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
                        .pointerInput(viewportWidthPx) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                splitFraction = (splitFraction + dragAmount / viewportWidthPx).coerceIn(0.03f, 0.97f)
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
            }

            // Quiet Original / Upscaled corner pills
            CornerLabelPill(
                label = if (isHoldingOriginal) "Holding · Original" else "Original",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.sm)
            )
            if (!isHoldingOriginal) {
                CornerLabelPill(
                    label = "Upscaled",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.sm)
                )
            }

            // Zoom status & 1-tap reset pill when zoomed in
            if (zoom > 1.05f) {
                val oneToOneZoom = OomSafeImageDecoders.computeOneToOneZoom(
                    imageWidth = afterImgW,
                    imageHeight = afterImgH,
                    viewportWidth = viewportWidthPx,
                    viewportHeight = viewportHeightPx
                )
                val pctOfNative = if (oneToOneZoom > 0.1f) {
                    ((zoom / oneToOneZoom) * 100f).roundToInt()
                } else {
                    (zoom * 100f).roundToInt()
                }
                val zoomLabel = if (pctOfNative in 94..106) {
                    "100% pixel view · Tap to fit"
                } else {
                    String.format(Locale.US, "%.1f× · Tap to fit", zoom)
                }
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
                            text = zoomLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.ink
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawPreviewAndOptionalTile(
    preview: DecodedPreviewImage,
    previewImg: androidx.compose.ui.graphics.ImageBitmap,
    tile: DecodedViewportTile?,
    tileImg: androidx.compose.ui.graphics.ImageBitmap?,
    zoom: Float,
    panX: Float,
    panY: Float,
    pivot: Offset
) {
    val (fitOffset, fitSize) = OomSafeImageDecoders.computeFittedBounds(
        imageWidth = preview.intrinsicWidth,
        imageHeight = preview.intrinsicHeight,
        viewportWidth = size.width,
        viewportHeight = size.height
    )
    if (fitSize.width <= 1f || fitSize.height <= 1f) return

    withTransform({
        translate(left = panX, top = panY)
        scale(scaleX = zoom, scaleY = zoom, pivot = pivot)
    }) {
        drawImage(
            image = previewImg,
            dstOffset = IntOffset(fitOffset.x.roundToInt(), fitOffset.y.roundToInt()),
            dstSize = IntSize(fitSize.width.roundToInt().coerceAtLeast(1), fitSize.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Medium
        )
        if (tile != null && tileImg != null && preview.intrinsicWidth > 0 && preview.intrinsicHeight > 0) {
            val r = tile.sourceRect
            val tileLeft = fitOffset.x + (r.left.toFloat() / preview.intrinsicWidth) * fitSize.width
            val tileTop = fitOffset.y + (r.top.toFloat() / preview.intrinsicHeight) * fitSize.height
            val tileW = (r.width().toFloat() / preview.intrinsicWidth) * fitSize.width
            val tileH = (r.height().toFloat() / preview.intrinsicHeight) * fitSize.height
            drawImage(
                image = tileImg,
                dstOffset = IntOffset(tileLeft.roundToInt(), tileTop.roundToInt()),
                dstSize = IntSize(tileW.roundToInt().coerceAtLeast(1), tileH.roundToInt().coerceAtLeast(1)),
                filterQuality = if (zoom >= 2.5f) FilterQuality.Low else FilterQuality.Medium
            )
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
