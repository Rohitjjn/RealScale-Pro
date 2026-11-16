package com.tumuyan.ncnn.realsr.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * Turns the picked photo into something an image model can accept, and turns the model's answer back
 * into the exact file the app saves.
 *
 * Two hard limits are enforced here rather than in the UI:
 *  - what gets uploaded is capped at [MAX_UPLOAD_PIXELS] (and at the model's own `maxInputMP`),
 *  - what gets decoded again is capped at [MAX_DECODE_PIXELS], so a huge 4× answer falls back to a
 *    byte-for-byte save instead of an out-of-memory crash.
 */
object AiImagePrep {

    const val MAX_DECODE_PIXELS = 24_000_000L
    private const val MAX_UPLOAD_PIXELS = 24_000_000L

    data class Payload(
        val bytes: ByteArray,
        val mime: String,
        val width: Int,
        val height: Int,
        val downscaled: Boolean,
        val bitmap: Bitmap?,
    )

    /** Reads [uri], downsizes to fit [maxInputMP] and re-encodes as JPEG. */
    fun prepare(context: Context, uri: Uri, maxInputMP: Int): Payload {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val srcW = bounds.outWidth.coerceAtLeast(1)
        val srcH = bounds.outHeight.coerceAtLeast(1)

        var sample = 1
        while ((srcW / sample).toLong() * (srcH / sample) > MAX_UPLOAD_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            runCatching { BitmapFactory.decodeStream(it, null, options) }.getOrNull()
        } ?: throw AiClientException("That image could not be read.")

        val limit = maxInputMP.coerceAtLeast(1) * 1_000_000L
        val pixels = decoded.width.toLong() * decoded.height
        val scaled = if (pixels > limit) {
            val k = kotlin.math.sqrt(limit.toDouble() / pixels.toDouble())
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * k).toInt().coerceAtLeast(64),
                (decoded.height * k).toInt().coerceAtLeast(64),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 95, out)
        val bytes = out.toByteArray()
        return Payload(
            bytes = bytes,
            mime = "image/jpeg",
            width = scaled.width,
            height = scaled.height,
            downscaled = sample > 1 || scaled !== decoded || pixels > limit,
            bitmap = scaled,
        )
    }

    fun saveBeforeCopy(payload: Payload, dir: File, name: String): File {
        val file = File(dir, name)
        file.outputStream().use { it.write(payload.bytes) }
        return file
    }

    /** Null when the answer is too large to decode safely; the raw bytes are still saved then. */
    fun decodeResult(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (pixels <= 0L || pixels > MAX_DECODE_PIXELS) return null
        return runCatching {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
            )
        }.getOrNull()
    }

    /** Dimensions of an encoded image without decoding its pixels. */
    fun boundsOf(bytes: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth.coerceAtLeast(0) to bounds.outHeight.coerceAtLeast(0)
    }

    fun boundsOf(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth.coerceAtLeast(0) to bounds.outHeight.coerceAtLeast(0)
    }

    /** Scales to the size the user asked for, never beyond [MAX_DECODE_PIXELS]. */
    fun fit(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (targetW <= 0 || targetH <= 0) return bitmap
        if (bitmap.width == targetW && bitmap.height == targetH) return bitmap
        val pixels = targetW.toLong() * targetH.toLong()
        if (pixels > MAX_DECODE_PIXELS) return bitmap
        val scaled = runCatching {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        }.getOrNull() ?: return bitmap
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun encode(bitmap: Bitmap, format: String): ByteArray {
        val compress = when (format.lowercase(Locale.US)) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.PNG
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(compress, 95, out)
        return out.toByteArray()
    }

    /** Cheap magic-byte sniffing: providers do not always send the content type they promise. */
    fun sniffMime(bytes: ByteArray): String {
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return "image/png"
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()) return "image/webp"
        return "image/png"
    }

    fun extensionFor(mime: String): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }
}
