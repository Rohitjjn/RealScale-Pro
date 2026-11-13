package com.tumuyan.ncnn.realsr.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Post-resize step for plans whose requested scale is not natively supported
 * (e.g. 5× = 4× native + 1.25× lanczos-ish resample, or 3× on a 2×-only model = 1.5× resample).
 * Runs AFTER the untouched native CLI finishes; the CLI invocation itself is never modified.
 *
 * Memory guard: refuses to allocate more than [MAX_BITMAP_BYTES] for the result and returns
 * [Result.Oversized] so the caller can fail with a clear, actionable message instead of OOM.
 */
object PostResizeProcessor {

    sealed interface Result {
        data class Ok(val width: Int, val height: Int) : Result
        data object Oversized : Result
        data object Failed : Result
    }

    const val MAX_BITMAP_BYTES = 300L * 1024 * 1024

    fun resizeInPlace(outputFile: java.io.File, targetWidth: Int, targetHeight: Int, format: OutputFormat, jpegQuality: Int = 95): Result {
        if (targetWidth <= 0 || targetHeight <= 0) return Result.Failed
        val targetBytes = targetWidth.toLong() * targetHeight.toLong() * 4L
        if (targetBytes > MAX_BITMAP_BYTES) return Result.Oversized
        return try {
            val src = BitmapFactory.decodeFile(outputFile.absolutePath) ?: return Result.Failed
            if (src.width == targetWidth && src.height == targetHeight) {
                src.recycle()
                return Result.Ok(targetWidth, targetHeight)
            }
            val scaled = Bitmap.createScaledBitmap(src, targetWidth, targetHeight, true)
            if (scaled !== src) src.recycle()
            val tmp = java.io.File(outputFile.parentFile, outputFile.name + ".post.tmp")
            java.io.FileOutputStream(tmp).use { out ->
                val (fmt, quality) = when (format) {
                    OutputFormat.PNG -> Bitmap.CompressFormat.PNG to 100
                    OutputFormat.JPG -> Bitmap.CompressFormat.JPEG to jpegQuality
                    OutputFormat.WEBP -> webpFormat() to 95
                }
                scaled.compress(fmt, quality, out)
            }
            scaled.recycle()
            if (tmp.length() <= 0L) return Result.Failed
            if (!tmp.renameTo(outputFile)) {
                tmp.copyTo(outputFile, overwrite = true)
                tmp.delete()
            }
            Result.Ok(targetWidth, targetHeight)
        } catch (t: Throwable) {
            Result.Failed
        }
    }

    private fun webpFormat(): Bitmap.CompressFormat =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
}
