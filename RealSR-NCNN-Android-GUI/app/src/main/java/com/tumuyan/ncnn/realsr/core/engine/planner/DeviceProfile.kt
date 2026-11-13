package com.tumuyan.ncnn.realsr.core.engine.planner

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.getSystemService

/** Device capability tier derived from total RAM. Mirrors the design's device check. */
enum class DeviceTier(val level: Int, val label: String) {
    LIGHT(0, "Light"),
    STANDARD(1, "Standard"),
    POWERFUL(2, "Powerful");

    companion object {
        fun fromRamGb(ramGb: Double): DeviceTier = when {
            ramGb < 4.0 -> LIGHT
            ramGb < 12.0 -> STANDARD
            else -> POWERFUL
        }
    }
}

/**
 * Real, on-device measurements for the upscaling pipeline — never placeholder numbers.
 * `inputLimitMP`/`outputCapMP`/`superCapMP` mirror the design's caps (4 / 64 / 128 MP);
 * they are deliberately conservative so a full-frame post-resize stays memory-safe.
 */
data class DeviceProfile(
    val tier: DeviceTier,
    val ramGb: Double,
    val cores: Int,
    val glEsVersion: String,
    val vulkanLevel: Int, // 0 = no Vulkan hardware feature
    val freeStorageGb: Double,
    val totalStorageGb: Double,
    val binariesPresent: Boolean,
    val inputLimitMP: Double = 4.0,
    val outputCapMP: Double = 64.0,
    val superCapMP: Double = 128.0,
)

object DeviceProfiler {
    fun probe(context: Context, binariesPresent: Boolean): DeviceProfile {
        val appContext = context.applicationContext
        val am = appContext.getSystemService<ActivityManager>()
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val ramGb = if (memInfo.totalMem > 0) memInfo.totalMem / (1024.0 * 1024.0 * 1024.0) else 0.0
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val glEs = am?.deviceConfigurationInfo?.glEsVersion?.takeIf { it.isNotBlank() } ?: "Unknown"
        val pm = appContext.packageManager
        val vulkan = vulkanLevel(pm)

        val filesDir = appContext.filesDir
        val statFs = runCatching { StatFs(filesDir.absolutePath) }.getOrNull()
        val freeGb = statFs?.let { it.availableBytes / (1024.0 * 1024.0 * 1024.0) } ?: 0.0
        val totalGb = statFs?.let { it.totalBytes / (1024.0 * 1024.0 * 1024.0) } ?: 0.0

        return DeviceProfile(
            tier = DeviceTier.fromRamGb(ramGb),
            ramGb = ramGb,
            cores = cores,
            glEsVersion = glEs,
            vulkanLevel = vulkan,
            freeStorageGb = freeGb,
            totalStorageGb = totalGb,
            binariesPresent = binariesPresent,
        )
    }

    /** 0 = no Vulkan hardware; otherwise the highest FEATURE_VULKAN_HARDWARE_LEVEL tier the device reports. */
    private fun vulkanLevel(pm: PackageManager): Int {
        return runCatching {
            when {
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 3) -> 3
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 2) -> 2
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) -> 1
                else -> 0
            }
        }.getOrDefault(0)
    }
}
