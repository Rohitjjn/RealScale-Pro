package com.tumuyan.ncnn.realsr.core.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.tumuyan.ncnn.realsr.ImageProcessor
import com.tumuyan.ncnn.realsr.ProcessingService
import com.tumuyan.ncnn.realsr.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume

class UnsupportedImageFormatException(userMessage: String, cause: Throwable? = null) :
    IllegalArgumentException(userMessage, cause)

data class StagedInputPhoto(
    val stagedUri: Uri,
    val stagedFile: File,
    val displayName: String,
    val width: Int,
    val height: Int,
    val formatLabel: String,
    val sizeBytes: Long
)

/**
 * Safely copies incoming content/file URIs into app-private cache while transient
 * URI read grants are active, without ever modifying the caller's original file,
 * and normalizes non-native containers (HEIC, AVIF, GIF, TIFF, etc.) to lossless PNG.
 */
object ImageIntakeManager {

    enum class DetectedContainer(val ext: String, val nativeEngineSupported: Boolean) {
        PNG("png", true),
        JPEG("jpg", true),
        WEBP("webp", true),
        BMP("bmp", true),
        GIF("gif", false),
        HEIF("heic", false),
        AVIF("avif", false),
        OTHER("bin", false)
    }

    fun detectContainer(header: ByteArray, length: Int): DetectedContainer {
        if (length >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte()
        ) {
            return DetectedContainer.PNG
        }
        if (length >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return DetectedContainer.JPEG
        }
        if (length >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        ) {
            return DetectedContainer.WEBP
        }
        if (length >= 2 &&
            header[0] == 'B'.code.toByte() &&
            header[1] == 'M'.code.toByte()
        ) {
            return DetectedContainer.BMP
        }
        if (length >= 6 &&
            header[0] == 'G'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == '8'.code.toByte()
        ) {
            return DetectedContainer.GIF
        }
        if (length >= 12 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            val brand = String(header, 8, 4, Charsets.US_ASCII).lowercase(Locale.US)
            return when {
                brand.startsWith("avi") -> DetectedContainer.AVIF
                brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("msf") -> DetectedContainer.HEIF
                else -> DetectedContainer.OTHER
            }
        }
        return DetectedContainer.OTHER
    }

    fun resolveDisplayName(context: Context, uri: Uri): String? = runCatching {
        if (uri.scheme == "file") {
            return File(requireNotNull(uri.path)).name
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    /**
     * Copies an incoming external URI into cache/intake immediately so background processing
     * remains unaffected even after the Quick Sheet activity finishes and Android revokes
     * transient activity URI grants.
     */
    suspend fun stageIncomingUri(context: Context, uri: Uri): Result<StagedInputPhoto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val intakeDir = File(context.cacheDir, "intake").apply { mkdirs() }
                pruneOldIntakeFiles(intakeDir)

                val displayName = resolveDisplayName(context, uri) ?: "Photo.png"
                val rawTemp = File(intakeDir, "intake_${System.currentTimeMillis()}_${uri.hashCode().toUInt()}.tmp")

                copyUriStreamToFile(context, uri, rawTemp)
                if (!rawTemp.isFile || rawTemp.length() < 4L) {
                    rawTemp.delete()
                    throw UnsupportedImageFormatException("The selected photo appears to be empty or unreadable.")
                }

                val header = ByteArray(16)
                val readLen = FileInputStream(rawTemp).use { it.read(header) }
                val container = detectContainer(header, readLen)
                val ext = if (container.nativeEngineSupported) container.ext else "bin"
                val finalStaged = File(intakeDir, "staged_${System.currentTimeMillis()}_${uri.hashCode().toUInt()}.$ext")
                rawTemp.renameTo(finalStaged)

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(finalStaged.absolutePath, bounds)

                StagedInputPhoto(
                    stagedUri = Uri.fromFile(finalStaged),
                    stagedFile = finalStaged,
                    displayName = displayName,
                    width = bounds.outWidth.coerceAtLeast(0),
                    height = bounds.outHeight.coerceAtLeast(0),
                    formatLabel = container.name,
                    sizeBytes = finalStaged.length()
                )
            }
        }

    /**
     * Copies [uri] to a clean engine-ready file inside [creationsDir]. If the source container
     * is not directly supported by NCNN/stb_image (e.g. HEIC, AVIF, GIF), converts it to lossless PNG
     * via Android's BitmapFactory first, falling back to `./magick` if needed.
     */
    suspend fun prepareEngineInputFile(
        context: Context,
        uri: Uri,
        creationsDir: File,
        filePrefix: String,
        realsrDir: File,
        service: ProcessingService?
    ): File = withContext(Dispatchers.IO) {
        val rawCopy = File(creationsDir, "${filePrefix}_raw.tmp")
        try {
            copyUriStreamToFile(context, uri, rawCopy)
        } catch (t: Throwable) {
            rawCopy.delete()
            throw UnsupportedImageFormatException(
                "Could not read the selected photo. Permission to access the file may have expired.",
                t
            )
        }

        if (!rawCopy.isFile || rawCopy.length() < 8L) {
            rawCopy.delete()
            throw UnsupportedImageFormatException("This image file is empty or damaged.")
        }

        val header = ByteArray(16)
        val n = FileInputStream(rawCopy).use { it.read(header) }
        val container = detectContainer(header, n)

        if (container.nativeEngineSupported) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(rawCopy.absolutePath, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val readyFile = File(creationsDir, "${filePrefix}.${container.ext}")
                if (readyFile.exists()) readyFile.delete()
                if (!rawCopy.renameTo(readyFile)) {
                    rawCopy.copyTo(readyFile, overwrite = true)
                    rawCopy.delete()
                }
                return@withContext readyFile
            }
        }

        // Non-native container (HEIC, AVIF, GIF, TIFF, etc.): convert first to lossless PNG
        val convertedPng = File(creationsDir, "${filePrefix}.png")
        val decoded: Bitmap? = runCatching {
            BitmapFactory.decodeFile(rawCopy.absolutePath)
        }.getOrNull()

        if (decoded != null) {
            try {
                FileOutputStream(convertedPng).use { out ->
                    if (!decoded.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IllegalStateException("PNG encode returned false")
                    }
                }
            } finally {
                decoded.recycle()
                rawCopy.delete()
            }
            if (convertedPng.isFile && convertedPng.length() > 0L) {
                return@withContext convertedPng
            }
        }

        // Fallback to bundled ImageMagick CLI (`./magick <raw>[0] <convertedPng>`) if available
        val magickBin = File(realsrDir, "magick")
        if (service != null && magickBin.isFile) {
            val srcFrameZero = ShellUtils.escapeShellArgument("${rawCopy.absolutePath}[0]")
            val dstEscaped = ShellUtils.escapeShellArgument(convertedPng.absolutePath)
            val ok = suspendCancellableCoroutine<Boolean> { cont ->
                service.startTask(
                    "./magick $srcFrameZero $dstEscaped",
                    realsrDir.absolutePath,
                    0,
                    object : ImageProcessor.ProcessCallback {
                        override fun onProgress(line: String?) = Unit
                        override fun onCompleted(result: String?, success: Boolean) {
                            if (cont.isActive) cont.resume(success)
                        }
                        override fun onError(error: String?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                )
            }
            rawCopy.delete()
            if (ok && convertedPng.isFile && convertedPng.length() > 0L) {
                return@withContext convertedPng
            }
        } else {
            rawCopy.delete()
        }

        convertedPng.delete()
        throw UnsupportedImageFormatException(
            "This image format couldn't be opened on your device. Try saving or sharing it as PNG or JPEG first."
        )
    }

    private fun copyUriStreamToFile(context: Context, uri: Uri, target: File) {
        if (uri.scheme == "file") {
            val src = File(requireNotNull(uri.path))
            if (src.absolutePath == target.absolutePath) return
            src.copyTo(target, overwrite = true)
            return
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw UnsupportedImageFormatException("Unable to open selected image.")
        input.use { inStream ->
            FileOutputStream(target).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun pruneOldIntakeFiles(intakeDir: File) {
        val cutoff = System.currentTimeMillis() - 6L * 3600_000L // 6 hours
        intakeDir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) {
                f.delete()
            }
        }
    }
}
