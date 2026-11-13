package com.tumuyan.ncnn.realsr.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Input downscale for over-limit sources in Upscale Mode (design: "Large photo optimized for
 * your device"). Super Resolution Mode never calls this — the original file is used untouched.
 * Decode → scale → re-encode PNG in place at [cappedFile]; returns decoded dims of the result.
 */
object InputOptimizer {

    data class Outcome(val width: Int, val height: Int, val optimized: Boolean)

    /**
     * If the image at [file] exceeds [limitMP], writes a downscaled PNG to [cappedFile] and
     * returns its dims with optimized=true. Otherwise returns original dims, optimized=false.
     */
    fun capIfNeeded(file: File, cappedFile: File, limitMP: Double): Outcome {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return Outcome(w.coerceAtLeast(1), h.coerceAtLeast(1), optimized = false)
        val mp = (w.toLong() * h.toLong()) / 1e6
        if (mp <= limitMP) return Outcome(w, h, optimized = false)

        val k = sqrt(limitMP / mp)
        val tw = maxOf(1, (w * k).roundToInt())
        val th = maxOf(1, (h * k).roundToInt())
        // Sample-decoding to something near target, then exact scale.
        var sample = 1
        while (w / (sample * 2) >= tw && h / (sample * 2) >= th) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ) ?: return Outcome(w, h, optimized = false)

        val final = if (decoded.width == tw && decoded.height == th) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, tw, th, true).also { if (it !== decoded) decoded.recycle() }
        }
        cappedFile.outputStream().use { out -> final.compress(Bitmap.CompressFormat.PNG, 100, out) }
        final.recycle()
        return Outcome(tw, th, optimized = true)
    }
}
