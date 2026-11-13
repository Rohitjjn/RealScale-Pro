package com.tumuyan.ncnn.realsr.core.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

data class CropPreviewResult(
    val beforeCropFile: File,
    val afterCropFile: File,
    val presetTitle: String,
    val scale: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val elapsedMs: Long
)

/**
 * Generates a fast (~0.3–1.5s) side-by-side crop preview so users can inspect how a preset
 * and 2×/4× scale treat fine detail before committing to a full-image upscale.
 */
object CropPreviewEngine {

    /**
     * Picks the highest-gradient patch (up to [maxCropEdge]×[maxCropEdge]) from a 3×3 interior grid
     * so previews highlight eyes, lettering, edges, or texture instead of flat background sky.
     */
    fun extractHighDetailCrop(source: Bitmap, maxCropEdge: Int = 224): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val cropW = min(maxCropEdge, srcW).coerceAtLeast(1)
        val cropH = min(maxCropEdge, srcH).coerceAtLeast(1)
        if (cropW == srcW && cropH == srcH) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val slackX = (srcW - cropW).coerceAtLeast(0)
        val slackY = (srcH - cropH).coerceAtLeast(0)
        val anchors = listOf(0.2f, 0.5f, 0.8f)

        var bestX = slackX / 2
        var bestY = slackY / 2
        var bestScore = -1.0

        for (ay in anchors) {
            for (ax in anchors) {
                val x0 = (slackX * ax).toInt().coerceIn(0, slackX)
                val y0 = (slackY * ay).toInt().coerceIn(0, slackY)
                val score = scorePatchGradient(source, x0, y0, cropW, cropH)
                if (score > bestScore) {
                    bestScore = score
                    bestX = x0
                    bestY = y0
                }
            }
        }

        return Bitmap.createBitmap(source, bestX, bestY, cropW, cropH)
    }

    private fun scorePatchGradient(bmp: Bitmap, x0: Int, y0: Int, w: Int, h: Int): Double {
        if (w < 4 || h < 4) return 0.0
        var sum = 0L
        var count = 0
        val step = 4
        var y = y0
        while (y < y0 + h - step) {
            var x = x0
            while (x < x0 + w - step) {
                val c = bmp.getPixel(x, y)
                val cx = bmp.getPixel(x + step, y)
                val cy = bmp.getPixel(x, y + step)
                val dx = abs(Color.red(c) - Color.red(cx)) +
                    abs(Color.green(c) - Color.green(cx)) +
                    abs(Color.blue(c) - Color.blue(cx))
                val dy = abs(Color.red(c) - Color.red(cy)) +
                    abs(Color.green(c) - Color.green(cy)) +
                    abs(Color.blue(c) - Color.blue(cy))
                sum += (dx + dy)
                count++
                x += step
            }
            y += step
        }
        return if (count > 0) sum.toDouble() / count.toDouble() else 0.0
    }

    suspend fun generateCropPreview(
        context: Context,
        sourceUri: Uri,
        preset: UpscalePreset,
        scale: Int,
        preferences: EnginePreferences,
        modelSpec: ModelExecutionSpec?,
        modelsRootDir: File
    ): Result<CropPreviewResult> = withContext(Dispatchers.IO) {
        runCatching {
            val startMs = System.currentTimeMillis()
            val previewDir = File(context.cacheDir, "crop_preview").apply { mkdirs() }
            val staged = ImageIntakeManager.stageIncomingUri(context, sourceUri).getOrThrow()

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staged.stagedFile.absolutePath, opts)
            var sample = 1
            while ((opts.outWidth / sample) > 1600 || (opts.outHeight / sample) > 1600) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(staged.stagedFile.absolutePath, decodeOpts)
                ?: throw IllegalStateException("Could not decode image for preview")

            val cropBmp = try {
                extractHighDetailCrop(decoded, maxCropEdge = 224)
            } finally {
                if (decoded.width > 224 || decoded.height > 224) {
                    decoded.recycle()
                }
            }

            val stamp = System.currentTimeMillis()
            val beforeFile = File(previewDir, "crop_in_$stamp.png")
            val afterFile = File(previewDir, "crop_out_${stamp}_x$scale.png")
            FileOutputStream(beforeFile).use { out ->
                cropBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val realsrDir = EngineAssetExtractor.ensureExtracted(context, forceReset = false)
            val previewPrefs = preferences.copy(outputFormat = OutputFormat.PNG)
            val cmd = PresetCommandResolver.buildUpscaleCommand(
                preset = preset,
                scale = scale,
                inputPath = beforeFile.absolutePath,
                outputPath = afterFile.absolutePath,
                preferences = previewPrefs,
                modelOverride = if (preset.id == PresetId.INSTANT && modelSpec == null) null else modelSpec,
                modelsRootDir = modelsRootDir
            )

            val ranNatively = runCatching {
                val pb = ProcessBuilder("sh", "-c", cmd)
                    .directory(realsrDir)
                    .redirectErrorStream(true)
                pb.environment()["LD_LIBRARY_PATH"] = realsrDir.absolutePath
                val proc = pb.start()
                proc.inputStream.bufferedReader().use { it.readText() }
                val finished = proc.waitFor(20L, TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    false
                } else {
                    proc.exitValue() == 0 && afterFile.isFile && afterFile.length() > 0L
                }
            }.getOrDefault(false)

            if (!ranNatively) {
                val outW = (cropBmp.width * scale).coerceAtLeast(1)
                val outH = (cropBmp.height * scale).coerceAtLeast(1)
                val fallbackBmp = Bitmap.createScaledBitmap(cropBmp, outW, outH, true)
                FileOutputStream(afterFile).use { out ->
                    fallbackBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (fallbackBmp !== cropBmp) fallbackBmp.recycle()
            }
            val cropW = cropBmp.width
            val cropH = cropBmp.height
            cropBmp.recycle()

            val outBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(afterFile.absolutePath, outBounds)
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

            CropPreviewResult(
                beforeCropFile = beforeFile,
                afterCropFile = afterFile,
                presetTitle = preset.title,
                scale = scale,
                cropWidth = cropW,
                cropHeight = cropH,
                outputWidth = outBounds.outWidth.coerceAtLeast(cropW * scale),
                outputHeight = outBounds.outHeight.coerceAtLeast(cropH * scale),
                elapsedMs = elapsed
            )
        }
    }
}
