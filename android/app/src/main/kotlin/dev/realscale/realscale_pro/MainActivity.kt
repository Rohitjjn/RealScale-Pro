package dev.realscale.realscale_pro

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

/**
 * RealScale-Pro host activity.
 *
 * MethodChannels:
 *  `realscale/device` — read-only device facts used by DeviceProfiler:
 *     totalMemoryMb, availableMemoryMb, androidVersion, cpuAbi,
 *     supportsVulkan, freeStorageBytes (StatFs on the save volume).
 *  `realscale/media`  — storage integration used by StorageService:
 *     saveToGallery(bytes, displayName, mimeType) -> {path, contentUri}
 *     shareFile(contentUri|path, mimeType, title)
 *
 * Everything reported here is read from the OS; nothing is guessed.
 * Vulkan support is detected by attempting to load libvulkan.so — the
 * only reliable per-app check without extra permissions (the inference
 * engine performs its own ncnn-level probe as well).
 *
 * Save strategy (documented in docs/DEVICE-COMPATIBILITY.md):
 *  - API 29+: MediaStore insert into Pictures/RealScale-Pro (scoped
 *    storage, no permission needed; visible in gallery apps).
 *  - API <29: app-specific external Pictures dir + media scanner (no
 *    WRITE_EXTERNAL_STORAGE permission is requested; the file is still
 *    user-accessible via the file system and scanned into MediaStore).
 */
class MainActivity : FlutterActivity() {
    private val deviceChannel = "realscale/device"
    private val mediaChannel = "realscale/media"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        MethodChannel(messenger, deviceChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "getDeviceInfo" -> {
                    try {
                        result.success(deviceInfo())
                    } catch (e: Exception) {
                        result.error("device_info_error", e.message, null)
                    }
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(messenger, mediaChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "saveToGallery" -> {
                    val bytes = call.argument<ByteArray>("bytes")
                    val displayName = call.argument<String>("displayName")
                    val mimeType = call.argument<String>("mimeType")
                    if (bytes == null || displayName == null || mimeType == null) {
                        result.error("bad_args", "bytes/displayName/mimeType required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        result.success(saveToGallery(bytes, displayName, mimeType))
                    } catch (e: Exception) {
                        result.error("save_error", e.message, null)
                    }
                }
                "shareFile" -> {
                    val uriOrPath = call.argument<String>("uri")
                    val mimeType = call.argument<String>("mimeType") ?: "image/*"
                    val title = call.argument<String>("title") ?: "RealScale-Pro result"
                    if (uriOrPath == null) {
                        result.error("bad_args", "uri required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        shareFile(uriOrPath, mimeType, title)
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("share_error", e.message, null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun deviceInfo(): Map<String, Any?> {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val supportsVulkan = try {
            System.loadLibrary("vulkan")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }

        val cpuAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }

        return mapOf(
            "totalMemoryMb" to (mi.totalMem / (1024L * 1024L)).toInt(),
            "availableMemoryMb" to (mi.availMem / (1024L * 1024L)).toInt(),
            "isLowMemoryDevice" to am.isLowRamDevice,
            "androidVersion" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "cpuAbi" to cpuAbi,
            "supportsVulkan" to supportsVulkan,
            "freeStorageBytes" to freeStorageBytes(),
        )
    }

    /** Free bytes on the primary external storage volume, or -1. */
    private fun freeStorageBytes(): Long {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES)
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Writes [bytes] as an image named [displayName] into the shared
     * Pictures/RealScale-Pro location. Returns the file path and a
     * shareable content URI.
     */
    private fun saveToGallery(bytes: ByteArray, displayName: String,
                              mimeType: String): Map<String, Any?> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/RealScale-Pro")
            }
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert returned null")
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("could not open output stream")
            return mapOf(
                "contentUri" to uri.toString(),
                "path" to queryDataPath(uri),
            )
        }

        // API < 29: app-visible external directory + media scan.
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "RealScale-Pro")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("could not create ${dir.absolutePath}")
        }
        val outFile = uniqueFile(dir, displayName)
        outFile.writeBytes(bytes)
        // Scan so gallery apps index the file; the scanner returns the
        // content URI we can share without a FileProvider.
        var scanned: Uri? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        android.media.MediaScannerConnection.scanFile(this,
            arrayOf(outFile.absolutePath), arrayOf(mimeType)) { _, uri ->
                scanned = uri
                latch.countDown()
        }
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        return mapOf(
            "contentUri" to scanned?.toString(),
            "path" to outFile.absolutePath,
        )
    }

    /** "name.png" -> "name (2).png" etc.; existing files are never overwritten. */
    private fun uniqueFile(dir: File, displayName: String): File {
        var candidate = File(dir, displayName)
        if (!candidate.exists()) {
            return candidate
        }
        val dot = displayName.lastIndexOf('.')
        val stem = if (dot > 0) displayName.substring(0, dot) else displayName
        val ext = if (dot > 0) displayName.substring(dot) else ""
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($counter)$ext")
            counter++
        }
        return candidate
    }

    private fun queryDataPath(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shareFile(uriOrPath: String, mimeType: String, title: String) {
        // Only content:// URIs are shareable with targetSdk >= 24 (raw
        // file URIs throw FileUriExposedException). The save step always
        // returns a content URI when sharing is possible.
        if (!uriOrPath.startsWith("content:")) {
            throw IllegalStateException(
                "no shareable content URI for this file (media scan failed)")
        }
        val uri = Uri.parse(uriOrPath)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }
}
