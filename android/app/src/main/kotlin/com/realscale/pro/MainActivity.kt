package com.realscale.pro

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.view.WindowManager
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "realscale/device")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "profile" -> profile(result)
                    "saveImage" -> saveImage(
                        call.argument<String>("path"),
                        call.argument<String>("name"),
                        call.argument<String>("mime"),
                        result,
                    )
                    "startProcessing" -> {
                        ContextCompat.startForegroundService(this, Intent(this, ProcessingService::class.java))
                        result.success(null)
                    }
                    "updateProcessing" -> {
                        val progress = call.argument<Int>("progress") ?: 0
                        ProcessingService.ensureChannel(this)
                        getSystemService(NotificationManager::class.java).notify(
                            ProcessingService.NOTIFICATION_ID,
                            ProcessingService.notification(this, progress),
                        )
                        result.success(null)
                    }
                    "stopProcessing" -> {
                        stopService(Intent(this, ProcessingService::class.java))
                        result.success(null)
                    }
                    "setKeepAwake" -> {
                        if (call.argument<Boolean>("enabled") == true) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun profile(result: MethodChannel.Result) {
        try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            val storage = StatFs(Environment.getDataDirectory().path)
            val hasVulkan = packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            result.success(
                mapOf(
                    "totalRamMb" to memory.totalMem / (1024L * 1024L),
                    "freeRamMb" to memory.availMem / (1024L * 1024L),
                    "gpuName" to if (hasVulkan) "Android Vulkan graphics" else "Android graphics",
                    "vulkanSupported" to hasVulkan,
                    "freeStorageMb" to storage.availableBytes / (1024L * 1024L),
                ),
            )
        } catch (error: Exception) {
            result.error("profile_unavailable", "Device details are unavailable", null)
        }
    }

    private fun saveImage(
        path: String?,
        name: String?,
        mime: String?,
        result: MethodChannel.Result,
    ) {
        if (path == null || name == null || mime == null) {
            result.error("save_failed", "The result details are incomplete", null)
            return
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RealScale-Pro")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Gallery location unavailable")
            contentResolver.openOutputStream(uri).use { output ->
                requireNotNull(output)
                File(path).inputStream().use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            result.success(uri.toString())
        } catch (error: Exception) {
            result.error("save_failed", "The gallery copy could not be saved", null)
        }
    }
}
