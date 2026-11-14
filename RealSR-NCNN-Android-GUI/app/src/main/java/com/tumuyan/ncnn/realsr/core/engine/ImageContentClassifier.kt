package com.tumuyan.ncnn.realsr.core.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.tumuyan.ncnn.realsr.core.imaging.OomSafeImageDecoders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class SelectionInspectionSummary(
    val count: Int,
    val primaryWidth: Int,
    val primaryHeight: Int,
    val totalPixels: Long,
    val suggestedPreset: PresetId?
)

/**
 * Lightweight, local, deterministic image heuristic that inspects basic visual characteristics
 * (flat-color uniformity, sharp inked-edge density, quantized palette compactness) on a tiny
 * `<= 96 px` thumbnail to quietly suggest `Anime & Art` vs `Photo`.
 */
object ImageContentClassifier {

    fun classifyBitmap(bitmap: Bitmap): PresetId {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return PresetId.PHOTO

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var totalPairs = 0
        var flatPairs = 0
        var nonFlatPairs = 0
        var sharpStepPairs = 0

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val c = pixels[rowOffset + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)

                if (x + 1 < w) {
                    val right = pixels[rowOffset + x + 1]
                    val dr = abs(r - Color.red(right))
                    val dg = abs(g - Color.green(right))
                    val db = abs(b - Color.blue(right))
                    val maxDiff = max(dr, max(dg, db))
                    totalPairs++
                    if (maxDiff <= 2) {
                        flatPairs++
                    } else {
                        nonFlatPairs++
                        val lumaDiff = abs((r * 299 + g * 587 + b * 114) -
                            (Color.red(right) * 299 + Color.green(right) * 587 + Color.blue(right) * 114)) / 1000
                        if (lumaDiff >= 36) {
                            sharpStepPairs++
                        }
                    }
                }

                if (y + 1 < h) {
                    val down = pixels[rowOffset + w + x]
                    val dr = abs(r - Color.red(down))
                    val dg = abs(g - Color.green(down))
                    val db = abs(b - Color.blue(down))
                    val maxDiff = max(dr, max(dg, db))
                    totalPairs++
                    if (maxDiff <= 2) {
                        flatPairs++
                    } else {
                        nonFlatPairs++
                        val lumaDiff = abs((r * 299 + g * 587 + b * 114) -
                            (Color.red(down) * 299 + Color.green(down) * 587 + Color.blue(down) * 114)) / 1000
                        if (lumaDiff >= 36) {
                            sharpStepPairs++
                        }
                    }
                }
            }
        }

        if (totalPairs == 0) return PresetId.PHOTO
        val flatRatio = flatPairs.toFloat() / totalPairs.toFloat()
        val sharpRatio = if (nonFlatPairs > 0) sharpStepPairs.toFloat() / nonFlatPairs.toFloat() else 0f

        // Flat cel-shaded art, screenshots, icons, and line illustrations exhibit either
        // substantial flat regions (>= 42%) or moderate flat fills paired with crisp inked contours.
        val isAnimeOrArt = flatRatio >= 0.42f || (flatRatio >= 0.24f && sharpRatio >= 0.15f)

        return if (isAnimeOrArt) PresetId.ANIME else PresetId.PHOTO
    }

    suspend fun inspectSelection(
        context: Context,
        uris: List<Uri>
    ): SelectionInspectionSummary? = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext null

        var primaryW = 0
        var primaryH = 0
        var sumPixels = 0L
        var suggested: PresetId? = null

        uris.forEachIndexed { index, uri ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
            }
            val w = bounds.outWidth.coerceAtLeast(0)
            val h = bounds.outHeight.coerceAtLeast(0)
            if (index == 0) {
                primaryW = w
                primaryH = h
                if (w > 0 && h > 0) {
                    val sampleOpts = BitmapFactory.Options().apply {
                        inSampleSize = OomSafeImageDecoders.computePowerOfTwoSampleSize(w, h, maxEdgePx = 96)
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val thumb = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, sampleOpts)
                        }
                    }.getOrNull()
                    if (thumb != null) {
                        try {
                            suggested = classifyBitmap(thumb)
                        } finally {
                            thumb.recycle()
                        }
                    }
                }
            }
            if (w > 0 && h > 0) {
                sumPixels += w.toLong() * h.toLong()
            }
        }

        SelectionInspectionSummary(
            count = uris.size,
            primaryWidth = primaryW,
            primaryHeight = primaryH,
            totalPixels = sumPixels,
            suggestedPreset = suggested
        )
    }

    /**
     * Builds the live sticky-bar tabular summary, e.g.:
     * `1080×1440 → 4320×5760 · ~4s · ~6.2 MB`
     */
    fun buildLiveEnhanceSummary(
        inspection: SelectionInspectionSummary?,
        scale: Int,
        preset: UpscalePreset,
        format: OutputFormat,
        backend: ExecutionBackend
    ): String {
        if (inspection == null || inspection.count <= 0) {
            return "Select a photo to preview output resolution, time & size"
        }

        val totalOutPixels = if (inspection.totalPixels > 0L) {
            inspection.totalPixels * scale.toLong() * scale.toLong()
        } else {
            (1280L * 960L * scale * scale * inspection.count)
        }

        // Approximate throughput (output megapixels per second) for honest ballpark preview
        val outMegapixels = totalOutPixels.toDouble() / 1_000_000.0
        val mpPerSec = when (preset.id) {
            PresetId.INSTANT -> 14.0
            PresetId.PHOTO -> if (backend == ExecutionBackend.CPU) 0.55 else 2.4
            PresetId.ANIME -> if (backend == ExecutionBackend.CPU) 0.65 else 2.8
            PresetId.MAXIMUM -> if (backend == ExecutionBackend.CPU) 0.25 else 1.1
        }
        val estSec = (outMegapixels / mpPerSec).roundToInt().coerceAtLeast(1)
        val timeLabel = if (estSec < 60) "~${estSec}s" else "~${estSec / 60}m ${estSec % 60}s"

        val bytesPerPixel = when (format) {
            OutputFormat.PNG -> 1.65
            OutputFormat.WEBP -> 0.55
            OutputFormat.JPG -> 0.65
        }
        val estMb = (totalOutPixels * bytesPerPixel) / (1024.0 * 1024.0)
        val sizeLabel = if (estMb < 0.1) "<0.1 MB" else String.format(Locale.US, "~%.1f MB", estMb)

        val dimPart = if (inspection.count == 1 && inspection.primaryWidth > 0 && inspection.primaryHeight > 0) {
            val outW = inspection.primaryWidth.toLong() * scale.toLong()
            val outH = inspection.primaryHeight.toLong() * scale.toLong()
            "${inspection.primaryWidth}×${inspection.primaryHeight} → ${outW}×${outH}"
        } else {
            "Batch of ${inspection.count} · ${scale}×"
        }

        return "$dimPart · $timeLabel · $sizeLabel"
    }
}
