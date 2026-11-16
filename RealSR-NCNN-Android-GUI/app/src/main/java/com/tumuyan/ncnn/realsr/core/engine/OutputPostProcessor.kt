package com.tumuyan.ncnn.realsr.core.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File
import java.util.Locale

/**
 * The two post-processing promises the Config screen makes (Round 4, issues 10 and 11):
 *
 *  - **Image compression** — the model reads a re-encoded temporary copy instead of the original,
 *    so a heavy phone-sized JPEG never has to be decoded twice.
 *  - **Keep photo metadata** — the saved file keeps the camera, date and location tags.
 *
 * Both work on real pixels and real files; nothing here is cosmetic. Every step is best-effort: if a
 * decode fails (huge image, odd colour space) the original file is handed on untouched and the job
 * still finishes.
 */
object OutputPostProcessor {

    /** The planner's own threshold: files above this are "Auto" compressed. */
    private const val AUTO_COMPRESS_BYTES = 2L * 1024 * 1024

    /**
     * A full ARGB decode is needed to re-encode. 24 MP × 4 B is ~96 MB, which is the point where a
     * mid-range phone starts to feel it, so anything larger is left alone.
     */
    private const val MAX_DECODE_PIXELS = 24_000_000L

    /** JPEG from the native tools (OpenCV default) is already ~95, so lower is the only real change. */
    private const val NATIVE_JPEG_QUALITY = 95

    data class InputPolicy(val enabled: Boolean, val quality: Int)

    fun inputPolicy(preference: String, quality: Int, sizeBytes: Long): InputPolicy = when (preference) {
        "off" -> InputPolicy(false, quality)
        "custom" -> InputPolicy(true, quality.coerceIn(60, 100))
        else -> InputPolicy(sizeBytes > AUTO_COMPRESS_BYTES, 90)
    }

    /**
     * Returns the file the engine should read: either [source] unchanged, or a JPEG copy at
     * [InputPolicy.quality]. The copy is only kept when it is actually smaller and the pixels fit.
     */
    fun applyInputCompression(source: File, dir: File, policy: InputPolicy): File {
        if (!policy.enabled || !source.isFile || source.length() == 0L) return source
        // A PNG is lossless by intent: re-encoding it as JPEG would drop alpha on stickers.
        if (source.extension.lowercase(Locale.US) == "png") return source
        val bitmap = decode(source) ?: return source
        val target = File(dir, "compressed_${System.currentTimeMillis()}_${policy.quality}_${source.nameWithoutExtension}.jpg")
        val written = runCatching {
            target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, policy.quality, out) }
        }.getOrDefault(false)
        bitmap.recycle()
        if (!written || !target.isFile || target.length() == 0L || target.length() >= source.length()) {
            target.delete()
            return source
        }
        return target
    }

    /**
     * Applies the Advanced "quality" slider to the saved file. PNG is lossless and the native JPEG /
     * WebP output is already near-100, so this only does work when the user asked for less.
     */
    fun reencodeOutput(file: File, format: OutputFormat, quality: Int): File {
        if (format == OutputFormat.PNG || quality >= NATIVE_JPEG_QUALITY) return file
        if (!file.isFile || file.length() == 0L) return file
        val bitmap = decode(file) ?: return file
        val compressFormat = if (format == OutputFormat.WEBP) Bitmap.CompressFormat.WEBP else Bitmap.CompressFormat.JPEG
        val temp = File(file.parentFile ?: file, "tmp_q${quality}_${file.name}")
        val written = runCatching {
            temp.outputStream().use { out -> bitmap.compress(compressFormat, quality.coerceIn(60, 100), out) }
        }.getOrDefault(false)
        bitmap.recycle()
        if (!written || !temp.isFile || temp.length() == 0L) {
            temp.delete()
            return file
        }
        /*
         * Copy the bytes back over the same path instead of delete+rename: the saved file keeps its
         * name (the gallery row points at it) and a failed rename can never leave the job with no
         * output at all.
         */
        runCatching {
            temp.inputStream().use { input -> file.outputStream().use { out -> input.copyTo(out) } }
        }
        temp.delete()
        return file
    }

    /** Camera / date / location tags copied from the photo the user picked onto the result. */
    private val COPIED_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
    )

    /**
     * `android.media.ExifInterface` writes JPEG (and passes through PNG read-only), so a PNG result
     * simply keeps what the engine produced. Returns true when at least one tag was carried over.
     */
    fun copyMetadata(from: File, to: File): Boolean {
        if (!from.isFile || !to.isFile) return false
        if (to.extension.lowercase(Locale.US) !in setOf("jpg", "jpeg")) return false
        val copied = runCatching {
            val source = ExifInterface(from.absolutePath)
            val target = ExifInterface(to.absolutePath)
            var any = false
            COPIED_TAGS.forEach { tag ->
                val value = source.getAttribute(tag)
                if (!value.isNullOrBlank()) {
                    target.setAttribute(tag, value)
                    any = true
                }
            }
            if (!any) return@runCatching false
            target.saveAttributes()
            true
        }.getOrDefault(false)
        return copied
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (pixels <= 0L || pixels > MAX_DECODE_PIXELS) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }
}
