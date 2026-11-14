package com.tumuyan.ncnn.realsr.core.imaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DecodedPreviewImage(
    val bitmap: Bitmap,
    val intrinsicWidth: Int,
    val intrinsicHeight: Int
)

data class DecodedViewportTile(
    val bitmap: Bitmap,
    val sourceRect: Rect
)

/**
 * Memory-safe image decoding & coordinate math for viewing high-megapixel (e.g. 12000x9000)
 * upscaled images without ever allocating a full-resolution RGBA Bitmap in RAM.
 */
object OomSafeImageDecoders {

    /**
     * Computes the smallest power-of-two `inSampleSize` such that both decoded dimensions
     * stay at or below [maxEdgePx].
     */
    fun computePowerOfTwoSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        maxEdgePx: Int = 2048
    ): Int {
        val safeMax = maxEdgePx.coerceAtLeast(128)
        var sample = 1
        var w = srcWidth
        var h = srcHeight
        while (w > safeMax || h > safeMax) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }

    /**
     * Computes the aspect-fitted base display rectangle (at zoom = 1, pan = 0) of an image
     * of [imageWidth] × [imageHeight] inside a container of [viewportWidth] × [viewportHeight].
     */
    fun computeFittedBounds(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float
    ): Pair< Offset, Size> {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0f || viewportHeight <= 0f) {
            return Offset.Zero to Size.Zero
        }
        val scale = min(viewportWidth / imageWidth.toFloat(), viewportHeight / imageHeight.toFloat())
        val drawW = imageWidth * scale
        val drawH = imageHeight * scale
        val left = (viewportWidth - drawW) * 0.5f
        val top = (viewportHeight - drawH) * 0.5f
        return Offset(left, top) to Size(drawW, drawH)
    }

    /**
     * Maps the currently visible viewport back into native pixel coordinates [Rect] of an image
     * given synchronized [zoom], [panX], and [panY] around the viewport center.
     */
    fun computeVisibleSourceRect(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
        zoom: Float,
        panX: Float,
        panY: Float
    ): Rect? {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 1f || viewportHeight <= 1f) {
            return null
        }
        val (fitTopLeft, fitSize) = computeFittedBounds(imageWidth, imageHeight, viewportWidth, viewportHeight)
        if (fitSize.width <= 1f || fitSize.height <= 1f) return null

        val cx = viewportWidth * 0.5f
        val cy = viewportHeight * 0.5f
        val safeZoom = zoom.coerceAtLeast(1f)

        // Screen point (sx, sy) maps to unzoomed canvas point:
        // ux = cx + (sx - cx - panX) / zoom
        val unzoomedLeft = cx + (0f - cx - panX) / safeZoom
        val unzoomedRight = cx + (viewportWidth - cx - panX) / safeZoom
        val unzoomedTop = cy + (0f - cy - panY) / safeZoom
        val unzoomedBottom = cy + (viewportHeight - cy - panY) / safeZoom

        val normLeft = ((unzoomedLeft - fitTopLeft.x) / fitSize.width).coerceIn(0f, 1f)
        val normRight = ((unzoomedRight - fitTopLeft.x) / fitSize.width).coerceIn(0f, 1f)
        val normTop = ((unzoomedTop - fitTopLeft.y) / fitSize.height).coerceIn(0f, 1f)
        val normBottom = ((unzoomedBottom - fitTopLeft.y) / fitSize.height).coerceIn(0f, 1f)

        val srcLeft = floor(normLeft * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val srcTop = floor(normTop * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val srcRight = max(srcLeft + 1, (normRight * imageWidth).roundToInt().coerceIn(1, imageWidth))
        val srcBottom = max(srcTop + 1, (normBottom * imageHeight).roundToInt().coerceIn(1, imageHeight))

        return if (srcRight > srcLeft && srcBottom > srcTop) {
            Rect(srcLeft, srcTop, srcRight, srcBottom)
        } else {
            null
        }
    }

    /**
     * Computes the zoom factor that displays 1 image pixel as 1 screen pixel ("100%").
     */
    fun computeOneToOneZoom(
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float
    ): Float {
        val (_, fitSize) = computeFittedBounds(imageWidth, imageHeight, viewportWidth, viewportHeight)
        if (fitSize.width <= 1f || imageWidth <= 0) return 2.5f
        val nativeZoom = imageWidth.toFloat() / fitSize.width
        return if (nativeZoom > 1.15f) nativeZoom.coerceIn(1.5f, 12f) else 2.5f
    }

    fun decodeDownsampledPreview(file: File, maxEdgePx: Int = 2048): DecodedPreviewImage? {
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = computePowerOfTwoSampleSize(srcW, srcH, maxEdgePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        }.getOrNull() ?: return null

        return DecodedPreviewImage(
            bitmap = bmp,
            intrinsicWidth = srcW,
            intrinsicHeight = srcH
        )
    }

    fun decodeHighResRegionTile(
        file: File,
        visibleRect: Rect,
        maxTileEdgePx: Int = 2048
    ): DecodedViewportTile? {
        if (!file.isFile || visibleRect.width() <= 0 || visibleRect.height() <= 0) return null
        return runCatching {
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return null
            try {
                val clamped = Rect(
                    visibleRect.left.coerceIn(0, decoder.width - 1),
                    visibleRect.top.coerceIn(0, decoder.height - 1),
                    visibleRect.right.coerceIn(1, decoder.width),
                    visibleRect.bottom.coerceIn(1, decoder.height)
                )
                if (clamped.width() <= 0 || clamped.height() <= 0) return null
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computePowerOfTwoSampleSize(
                        clamped.width(),
                        clamped.height(),
                        maxTileEdgePx
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val regionBmp = decoder.decodeRegion(clamped, opts) ?: return null
                DecodedViewportTile(bitmap = regionBmp, sourceRect = clamped)
            } finally {
                decoder.recycle()
            }
        }.getOrNull()
    }
}
