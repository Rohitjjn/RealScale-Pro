package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.core.imaging.DecodedPreviewImage
import com.tumuyan.ncnn.realsr.core.imaging.DecodedViewportTile
import com.tumuyan.ncnn.realsr.core.imaging.OomSafeImageDecoders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Full-screen image that stays sharp while it is zoomed — the way the system gallery does it.
 *
 * Coil (and every "just scale the composable" viewer) decodes one bitmap at the view's size and then
 * stretches it with the GPU, so a 4× upscale looked soft the moment you pinched in. Here the photo is
 * painted in two layers:
 *
 *  1. a downsampled preview (<= 2048 px) that is always drawn, and
 *  2. once the gesture settles and the image is zoomed in, a native-resolution region decoded with
 *     [android.graphics.BitmapRegionDecoder] for exactly the visible part of the photo.
 *
 * The preview keeps panning instant; the region lands a frame later and replaces the blurry area at
 * 1:1 pixel density. Everything is drawn by hand ([Canvas] + [drawIntoCanvas]) so both layers share
 * one transform and never disagree about where a pixel goes.
 */
@Composable
fun ZoomableImageFile(
    file: File,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onZoomChange: (Boolean) -> Unit = {},
) {
    var preview by remember(file.path) { mutableStateOf<DecodedPreviewImage?>(null) }
    var tile by remember(file.path) { mutableStateOf<DecodedViewportTile?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember(file.path) { mutableStateOf(true) }

    var zoom by remember(file.path) { mutableFloatStateOf(1f) }
    var panX by remember(file.path) { mutableFloatStateOf(0f) }
    var panY by remember(file.path) { mutableFloatStateOf(0f) }

    // 1. Downsampled preview: instant, cheap, the thing you see while pinching.
    LaunchedEffect(file.path) {
        loading = true
        preview = withContext(Dispatchers.IO) {
            OomSafeImageDecoders.decodeDownsampledPreview(file, maxEdgePx = 2048)
        }
        loading = false
    }

    val intrinsicW = preview?.intrinsicWidth ?: 0
    val intrinsicH = preview?.intrinsicHeight ?: 0
    val scale = if (intrinsicW > 0 && viewport.width > 0) {
        (viewport.width.toFloat() / intrinsicW).coerceAtMost(viewport.height.toFloat() / intrinsicH) * zoom
    } else {
        0f
    }

    // 2. Native-resolution region for the visible area, once the gesture has settled.
    LaunchedEffect(file.path, zoom, panX, panY, viewport, intrinsicW, intrinsicH) {
        if (zoom <= 1.05f || viewport.width == 0 || intrinsicW == 0 || scale <= 0f) {
            tile = null
            return@LaunchedEffect
        }
        delay(120)
        val w = viewport.width.toFloat()
        val h = viewport.height.toFloat()
        val originX = w / 2f + panX
        val originY = h / 2f + panY
        val left = ((0f - originX) / scale + intrinsicW / 2f).coerceIn(0f, intrinsicW.toFloat())
        val right = ((w - originX) / scale + intrinsicW / 2f).coerceIn(0f, intrinsicW.toFloat())
        val top = ((0f - originY) / scale + intrinsicH / 2f).coerceIn(0f, intrinsicH.toFloat())
        val bottom = ((h - originY) / scale + intrinsicH / 2f).coerceIn(0f, intrinsicH.toFloat())
        if (right - left < 2f || bottom - top < 2f) {
            tile = null
            return@LaunchedEffect
        }
        // A little margin so a slow pan does not reveal an unsharp edge.
        val padX = (right - left) * 0.15f
        val padY = (bottom - top) * 0.15f
        val rect = Rect(
            (left - padX).coerceAtLeast(0f),
            (top - padY).coerceAtLeast(0f),
            (right + padX).coerceAtMost(intrinsicW.toFloat()),
            (bottom + padY).coerceAtMost(intrinsicH.toFloat()),
        )
        tile = withContext(Dispatchers.IO) {
            OomSafeImageDecoders.decodeHighResRegionTile(
                file = file,
                visibleRect = android.graphics.Rect(
                    rect.left.roundToInt(),
                    rect.top.roundToInt(),
                    rect.right.roundToInt(),
                    rect.bottom.roundToInt(),
                ),
                maxTileEdgePx = 2048,
            )
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size != viewport) {
                    viewport = size
                    panX = 0f
                    panY = 0f
                }
            }
            .pointerInput(file.path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        val next = if (zoom > 1.05f) 1f else 2.5f
                        zoom = next
                        if (next == 1f) {
                            panX = 0f
                            panY = 0f
                        }
                        onZoomChange(next > 1.05f)
                    },
                )
            }
            .pointerInput(file.path, viewport) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val next = (zoom * gestureZoom).coerceIn(1f, 6f)
                    val nextScale = if (intrinsicW > 0 && viewport.width > 0) {
                        (viewport.width.toFloat() / intrinsicW)
                            .coerceAtMost(viewport.height.toFloat() / intrinsicH) * next
                    } else {
                        0f
                    }
                    val maxPanX = max(0f, (intrinsicW * nextScale - viewport.width) / 2f)
                    val maxPanY = max(0f, (intrinsicH * nextScale - viewport.height) / 2f)
                    zoom = next
                    panX = if (next <= 1.001f) 0f else (panX + pan.x).coerceIn(-maxPanX, maxPanX)
                    panY = if (next <= 1.001f) 0f else (panY + pan.y).coerceIn(-maxPanY, maxPanY)
                    onZoomChange(next > 1.05f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val base = preview
        if (base != null && viewport.width > 0 && intrinsicW > 0 && scale > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val originX = size.width / 2f + panX
                val originY = size.height / 2f + panY
                fun screenX(imageX: Float) = originX + (imageX - intrinsicW / 2f) * scale
                fun screenY(imageY: Float) = originY + (imageY - intrinsicH / 2f) * scale

                drawImage(
                    image = base.bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(base.bitmap.width, base.bitmap.height),
                    dstOffset = IntOffset(screenX(0f).roundToInt(), screenY(0f).roundToInt()),
                    dstSize = IntSize(
                        (intrinsicW * scale).roundToInt().coerceAtLeast(1),
                        (intrinsicH * scale).roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Medium,
                )

                tile?.let { sharp ->
                    val src = sharp.sourceRect
                    if (src.width() > 0 && src.height() > 0) {
                        drawImage(
                            image = sharp.bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(sharp.bitmap.width, sharp.bitmap.height),
                            dstOffset = IntOffset(screenX(src.left.toFloat()).roundToInt(), screenY(src.top.toFloat()).roundToInt()),
                            dstSize = IntSize(
                                (src.width() * scale).roundToInt().coerceAtLeast(1),
                                (src.height() * scale).roundToInt().coerceAtLeast(1),
                            ),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
        }

        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Kept for callers that only have a path string. */
@Composable
fun ZoomableImageFile(
    path: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onZoomChange: (Boolean) -> Unit = {},
) = ZoomableImageFile(File(path), modifier, onTap, onZoomChange)
