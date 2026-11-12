package com.tumuyan.ncnn.realsr.core.model

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class HttpRangeResponse(
    val statusCode: Int,
    val contentLength: Long,
    val body: InputStream,
    private val onClose: () -> Unit = {}
) : Closeable {
    override fun close() {
        runCatching { body.close() }
        runCatching { onClose() }
    }
}

fun interface HttpRangeClient {
    @Throws(IOException::class)
    fun open(url: String, rangeStartBytes: Long): HttpRangeResponse
}

class HttpUrlConnectionRangeClient(
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 30_000
) : HttpRangeClient {

    override fun open(url: String, rangeStartBytes: Long): HttpRangeResponse {
        var currentUrl = url
        var redirects = 0
        while (redirects < 6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "RealScale-Pro/1.0")
                if (rangeStartBytes > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStartBytes-")
                }
            }
            conn.connect()
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect missing Location header for $currentUrl")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
                continue
            }
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: InputStream.nullInputStream())
            return HttpRangeResponse(
                statusCode = code,
                contentLength = conn.contentLengthLong,
                body = stream,
                onClose = { conn.disconnect() }
            )
        }
        throw IOException("Too many redirects while downloading $url")
    }
}

class TransientDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)
class PermanentDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class DownloadProgressSnapshot(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val verifying: Boolean = false
)

/**
 * Downloads, resumes via HTTP `Range`, verifies SHA-256 checksums, and atomically installs
 * a [ModelCatalogItem] into `<modelsRootDir>/<model.id>/`.
 */
class ResumableModelInstaller(
    private val httpClient: HttpRangeClient = HttpUrlConnectionRangeClient(),
    private val usableSpaceProvider: (File) -> Long = { dir -> dir.usableSpace },
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun downloadAndInstall(
        item: ModelCatalogItem,
        stagingRootDir: File,
        modelsRootDir: File,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit
    ): File {
        stagingRootDir.mkdirs()
        modelsRootDir.mkdirs()

        val modelStagingDir = File(stagingRootDir, item.id).apply { mkdirs() }
        val finalInstallDir = File(modelsRootDir, item.id)

        // Compute already-downloaded bytes across verified/partial staging files
        var completedPriorBytes = 0L
        for (spec in item.files) {
            val verifiedFile = File(modelStagingDir, spec.fileName)
            val partFile = File(modelStagingDir, "${spec.fileName}.part")
            completedPriorBytes += when {
                verifiedFile.exists() && verifiedFile.length() == spec.sizeBytes -> spec.sizeBytes
                partFile.exists() -> partFile.length().coerceAtMost(spec.sizeBytes)
                else -> 0L
            }
        }

        val remainingBytes = (item.totalBytes - completedPriorBytes).coerceAtLeast(0L)
        val requiredFreeBytes = remainingBytes + SAFETY_FREE_SPACE_BYTES
        val availableBytes = usableSpaceProvider(stagingRootDir)
        if (availableBytes < requiredFreeBytes) {
            throw PermanentDownloadException(
                "Not enough storage space. Need ${formatMegabytes(requiredFreeBytes)} free."
            )
        }

        var cumulativeVerifiedBytes = 0L
        var speedWindowStartMs = clockMillis()
        var speedWindowBytes = 0L
        var lastSpeedBps = 0L
        var lastProgressEmitMs = 0L

        for (spec in item.files) {
            currentCoroutineContext().ensureActive()
            val verifiedFile = File(modelStagingDir, spec.fileName)
            if (verifiedFile.exists() && verifiedFile.length() == spec.sizeBytes) {
                if (sha256Hex(verifiedFile).equals(spec.sha256, ignoreCase = true)) {
                    cumulativeVerifiedBytes += spec.sizeBytes
                    onProgress(
                        DownloadProgressSnapshot(
                            downloadedBytes = cumulativeVerifiedBytes,
                            totalBytes = item.totalBytes,
                            speedBytesPerSec = lastSpeedBps
                        )
                    )
                    continue
                } else {
                    verifiedFile.delete()
                }
            }

            val partFile = File(modelStagingDir, "${spec.fileName}.part")
            var existingPartLen = if (partFile.exists()) partFile.length() else 0L
            if (existingPartLen > spec.sizeBytes) {
                partFile.delete()
                existingPartLen = 0L
            }

            if (existingPartLen < spec.sizeBytes) {
                try {
                    httpClient.open(spec.url, existingPartLen).use { response ->
                        val status = response.statusCode
                        when {
                            status == 206 && existingPartLen > 0L -> {
                                // Server honored Range request; append to .part
                            }
                            status == 200 -> {
                                // Full body returned; restart this file from byte 0
                                existingPartLen = 0L
                                if (partFile.exists()) partFile.delete()
                            }
                            status == 416 && existingPartLen == spec.sizeBytes -> {
                                // Already complete
                            }
                            status == 408 || status == 429 || status in 500..599 -> {
                                throw TransientDownloadException("Server temporarily unavailable (HTTP $status)")
                            }
                            else -> {
                                throw PermanentDownloadException("Download failed (HTTP $status)")
                            }
                        }

                        if (existingPartLen < spec.sizeBytes) {
                            RandomAccessFile(partFile, "rw").use { raf ->
                                raf.seek(existingPartLen)
                                val buffer = ByteArray(BUFFER_SIZE)
                                var writtenForFile = existingPartLen
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = response.body.read(buffer)
                                    if (read < 0) break
                                    if (read == 0) continue
                                    raf.write(buffer, 0, read)
                                    writtenForFile += read
                                    speedWindowBytes += read

                                    val now = clockMillis()
                                    val elapsedWindow = now - speedWindowStartMs
                                    if (elapsedWindow >= 400L) {
                                        lastSpeedBps = (speedWindowBytes * 1000L) / elapsedWindow
                                        speedWindowStartMs = now
                                        speedWindowBytes = 0L
                                    }
                                    if (now - lastProgressEmitMs >= 150L) {
                                        lastProgressEmitMs = now
                                        onProgress(
                                            DownloadProgressSnapshot(
                                                downloadedBytes = (cumulativeVerifiedBytes + writtenForFile)
                                                    .coerceAtMost(item.totalBytes),
                                                totalBytes = item.totalBytes,
                                                speedBytesPerSec = lastSpeedBps
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: PermanentDownloadException) {
                    throw e
                } catch (e: TransientDownloadException) {
                    throw e
                } catch (e: IOException) {
                    throw TransientDownloadException("Network connection interrupted: ${e.message}", e)
                }
            }

            currentCoroutineContext().ensureActive()
            onProgress(
                DownloadProgressSnapshot(
                    downloadedBytes = (cumulativeVerifiedBytes + partFile.length()).coerceAtMost(item.totalBytes),
                    totalBytes = item.totalBytes,
                    speedBytesPerSec = 0L,
                    verifying = true
                )
            )

            if (!partFile.exists() || partFile.length() != spec.sizeBytes) {
                throw TransientDownloadException(
                    "Incomplete transfer for ${spec.fileName} (${partFile.length()} / ${spec.sizeBytes} bytes)"
                )
            }

            val actualHash = sha256Hex(partFile)
            if (!actualHash.equals(spec.sha256, ignoreCase = true)) {
                partFile.delete()
                throw PermanentDownloadException(
                    "Checksum verification failed for ${spec.fileName}. Corrupted file discarded."
                )
            }

            if (verifiedFile.exists()) verifiedFile.delete()
            if (!partFile.renameTo(verifiedFile)) {
                partFile.copyTo(verifiedFile, overwrite = true)
                partFile.delete()
            }
            cumulativeVerifiedBytes += spec.sizeBytes
        }

        // All files verified! Atomically promote staging files into models/<id>/
        val tempInstallDir = File(modelsRootDir, "${item.id}.installing")
        if (tempInstallDir.exists()) tempInstallDir.deleteRecursively()
        tempInstallDir.mkdirs()

        for (spec in item.files) {
            val verifiedFile = File(modelStagingDir, spec.fileName)
            val targetFile = File(tempInstallDir, spec.fileName)
            FileInputStream(verifiedFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        if (finalInstallDir.exists()) {
            finalInstallDir.deleteRecursively()
        }
        if (!tempInstallDir.renameTo(finalInstallDir)) {
            tempInstallDir.copyRecursively(finalInstallDir, overwrite = true)
            tempInstallDir.deleteRecursively()
        }
        modelStagingDir.deleteRecursively()

        onProgress(
            DownloadProgressSnapshot(
                downloadedBytes = item.totalBytes,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L
            )
        )
        return finalInstallDir
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024
        const val SAFETY_FREE_SPACE_BYTES = 16L * 1024L * 1024L

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(BUFFER_SIZE)
            FileInputStream(file).use { fis ->
                while (true) {
                    val n = fis.read(buf)
                    if (n < 0) break
                    if (n > 0) digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { b ->
                String.format(Locale.US, "%02x", b)
            }
        }

        private fun formatMegabytes(bytes: Long): String =
            String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
