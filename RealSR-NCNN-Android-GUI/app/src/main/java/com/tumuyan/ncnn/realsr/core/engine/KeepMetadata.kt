package com.tumuyan.ncnn.realsr.core.engine

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Keep-metadata: copies camera EXIF (date/time, make/model, GPS, exposure basics) from the
 * source image into the finished output file. Best-effort by design — any failure is logged
 * and swallowed so a metadata hiccup never blocks a completed upscale.
 */
object KeepMetadata {

    private val COPIED_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
    )

    fun copyToOutput(context: Context, sourceUri: Uri, outputFile: java.io.File): Boolean = runCatching {
        val src: ExifInterface = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            ExifInterface(stream)
        } ?: return false
        val attrs = COPIED_TAGS.associateWith { src.getAttribute(it) }.filterValues { !it.isNullOrBlank() }
        if (attrs.isEmpty()) return true
        val dst = ExifInterface(outputFile.absolutePath)
        attrs.forEach { (tag, value) -> runCatching { dst.setAttribute(tag, value) } }
        dst.saveAttributes()
        true
    }.getOrElse { false }
}
