package com.tumuyan.ncnn.realsr.core.engine

import android.content.Context
import com.tumuyan.ncnn.realsr.AssetsCopyer
import com.tumuyan.ncnn.realsr.BuildConfig
import com.tumuyan.ncnn.realsr.ShellUtils
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Extracts and prepares the native CLI runtime in `<cacheDir>/realsr` using upstream's
 * [AssetsCopyer], plus transparent unpacking of `binaries.tar.xz` when solid-compressed in APK assets.
 */
object EngineAssetExtractor {

    const val SOLID_ARCHIVE_NAME = "binaries.tar.xz"

    private const val CMD_CP_LIB_OPENCL =
        "cp /vendor/lib64/libOpenCL.so ./ 2>/dev/null; " +
            "cp /system/vendor/lib64/libOpenCL.so ./ 2>/dev/null; " +
            "cp /system/lib64/libOpenCL.so ./ 2>/dev/null"

    fun ensureExtracted(context: Context, forceReset: Boolean = false): File {
        val cacheRoot = context.cacheDir
        val realsrDir = File(cacheRoot, "realsr")
        val stampFile = File(realsrDir, ".engine_ready_v${BuildConfig.VERSION_CODE}")
        val resizeBin = File(realsrDir, "resize-ncnn")

        if (forceReset && realsrDir.exists()) {
            realsrDir.deleteRecursively()
        }

        val alreadyReady = !forceReset &&
            stampFile.exists() &&
            resizeBin.exists() &&
            resizeBin.length() > 0L

        if (!alreadyReady) {
            realsrDir.mkdirs()
            AssetsCopyer.releaseAssets(
                context,
                "realsr",
                cacheRoot.absolutePath,
                !forceReset
            )

            val extractedArchive = File(realsrDir, SOLID_ARCHIVE_NAME)
            if (extractedArchive.exists() && extractedArchive.length() > 0L) {
                FileInputStream(extractedArchive).use { fis ->
                    unpackTarXz(BufferedInputStream(fis), realsrDir)
                }
                extractedArchive.delete()
            }

            // Ensure executable bit on all CLI tools both via Java File API and upstream shell setup
            realsrDir.listFiles()?.forEach { file ->
                if (file.isFile && !file.name.endsWith(".xml") && !file.name.endsWith(".png")) {
                    file.setExecutable(true, false)
                }
            }
            runCatching {
                val escapedDir = ShellUtils.escapeShellArgument(realsrDir.absolutePath)
                ProcessBuilder(
                    "sh",
                    "-c",
                    "cd $escapedDir; $CMD_CP_LIB_OPENCL; chmod +x *"
                ).redirectErrorStream(true).start().waitFor()
            }

            stampFile.writeText(BuildConfig.VERSION_NAME)
        }

        return realsrDir
    }

    /**
     * Streams a `.tar.xz` (POSIX ustar / GNU tar 512-byte blocks) archive into [destDir]
     * with path-traversal protection.
     */
    internal fun unpackTarXz(tarXzStream: InputStream, destDir: File) {
        val canonicalDest = destDir.canonicalFile
        XZInputStream(tarXzStream).use { xzIn ->
            val header = ByteArray(512)
            val copyBuffer = ByteArray(32 * 1024)
            var pendingLongName: String? = null

            while (true) {
                val readHeader = readFullyOrEof(xzIn, header)
                if (readHeader == 0 || header.all { it == 0.toByte() }) {
                    break
                }

                val nameField = extractTarString(header, 0, 100)
                val prefixField = extractTarString(header, 345, 155)
                val rawEntryName = pendingLongName ?: if (prefixField.isNotEmpty()) {
                    "$prefixField/$nameField"
                } else {
                    nameField
                }
                pendingLongName = null

                val size = parseOctalLong(header, 124, 12)
                val typeFlag = header[156].toInt().toChar()

                if (typeFlag == 'L') {
                    // GNU tar long filename block
                    val nameBytes = ByteArray(size.toInt())
                    readExact(xzIn, nameBytes, size.toInt())
                    pendingLongName = extractTarString(nameBytes, 0, nameBytes.size)
                    skipTarPadding(xzIn, size)
                    continue
                }

                val cleanRelPath = rawEntryName.trimStart('.', '/').trim()
                if (cleanRelPath.isEmpty()) {
                    skipBytesExact(xzIn, size)
                    skipTarPadding(xzIn, size)
                    continue
                }

                val outFile = File(canonicalDest, cleanRelPath).canonicalFile
                require(
                    outFile.path == canonicalDest.path ||
                        outFile.path.startsWith(canonicalDest.path + File.separator)
                ) {
                    "Refusing tar entry outside target directory: $rawEntryName"
                }

                when (typeFlag) {
                    '5' -> {
                        outFile.mkdirs()
                        skipBytesExact(xzIn, size)
                    }
                    '0', '\u0000' -> {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var remaining = size
                            while (remaining > 0L) {
                                val toRead = minOf(remaining, copyBuffer.size.toLong()).toInt()
                                val n = xzIn.read(copyBuffer, 0, toRead)
                                if (n <= 0) throw IllegalStateException("Unexpected EOF in tar entry $cleanRelPath")
                                fos.write(copyBuffer, 0, n)
                                remaining -= n
                            }
                        }
                    }
                    else -> {
                        skipBytesExact(xzIn, size)
                    }
                }
                skipTarPadding(xzIn, size)
            }
        }
    }

    private fun extractTarString(buf: ByteArray, offset: Int, maxLen: Int): String {
        var end = offset
        val limit = minOf(buf.size, offset + maxLen)
        while (end < limit && buf[end] != 0.toByte()) {
            end++
        }
        return String(buf, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun parseOctalLong(buf: ByteArray, offset: Int, len: Int): Long {
        val text = extractTarString(buf, offset, len).trim()
        if (text.isEmpty()) return 0L
        return text.toLong(8)
    }

    private fun skipTarPadding(input: InputStream, size: Long) {
        val rem = (size % 512L).toInt()
        if (rem != 0) {
            skipBytesExact(input, (512 - rem).toLong())
        }
    }

    private fun skipBytesExact(input: InputStream, bytes: Long) {
        var remaining = bytes
        val scratch = ByteArray(4096)
        while (remaining > 0L) {
            val n = input.read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
            if (n <= 0) break
            remaining -= n
        }
    }

    private fun readFullyOrEof(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) return total
            total += n
        }
        return total
    }

    private fun readExact(input: InputStream, buf: ByteArray, count: Int) {
        var total = 0
        while (total < count) {
            val n = input.read(buf, total, count - total)
            if (n <= 0) throw IllegalStateException("Unexpected EOF in tar stream")
            total += n
        }
    }
}
