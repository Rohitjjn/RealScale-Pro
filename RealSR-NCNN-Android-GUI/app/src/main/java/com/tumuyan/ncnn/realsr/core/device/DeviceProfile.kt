package com.tumuyan.ncnn.realsr.core.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.os.Build
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tumuyan.ncnn.realsr.core.plan.PlanDevice
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Real device facts. The prototype hard-codes `DEVICE` (8 GB, 8 cores, Vulkan 1.3, 41 GB free);
 * this replaces every one of those numbers with a measured value.
 *
 * The tier is derived from memory, cores and Vulkan support, and it drives:
 *  - the planner's limits ([inputLimitMP], [outputCapMP], [superCapMP]),
 *  - which catalog models are offered ("Needs a Powerful device"),
 *  - the size of the automatic model choice.
 */
data class DeviceProfile(
    val tier: Int,
    val label: String,
    val ramGB: Int,
    val cores: Int,
    val gpu: String,
    val vulkan: String,
    val freeGB: Double,
    val totalGB: Int,
    val inputLimitMP: Int,
    val outputCapMP: Int,
    val superCapMP: Int,
    val benchmarkSecPerTile: Double,
) {
    fun toPlanDevice(): PlanDevice = PlanDevice(tier, inputLimitMP, outputCapMP, superCapMP)

    companion object {
        const val TIER_LIGHT = 1
        const val TIER_STANDARD = 2
        const val TIER_POWERFUL = 3

        fun labelFor(tier: Int): String = when (tier) {
            TIER_POWERFUL -> "Powerful"
            TIER_STANDARD -> "Standard"
            else -> "Light"
        }

        /** Limits per tier, in output megapixels; tier 2 is the prototype's reference device. */
        fun inputLimitMp(tier: Int): Int = when (tier) {
            TIER_POWERFUL -> 8
            TIER_STANDARD -> 4
            else -> 2
        }

        fun outputCapMp(tier: Int): Int = when (tier) {
            TIER_POWERFUL -> 96
            TIER_STANDARD -> 64
            else -> 24
        }

        fun superCapMp(tier: Int): Int = when (tier) {
            TIER_POWERFUL -> 192
            TIER_STANDARD -> 128
            else -> 48
        }

        /** A conservative placeholder until the first real benchmark finishes. */
        fun reference(tier: Int = TIER_STANDARD) = DeviceProfile(
            tier = tier,
            label = labelFor(tier),
            ramGB = 8,
            cores = 8,
            gpu = "Unknown GPU",
            vulkan = "1.1",
            freeGB = 4.0,
            totalGB = 64,
            inputLimitMP = inputLimitMp(tier),
            outputCapMP = outputCapMp(tier),
            superCapMP = superCapMp(tier),
            benchmarkSecPerTile = 0.42,
        )
    }
}

object DeviceProfiler {
    private val cached = AtomicReference<DeviceProfile?>(null)

    fun cached(): DeviceProfile? = cached.get()

    /** Reads memory, cores, Vulkan support and free storage. Cheap enough to call on resume. */
    fun read(context: Context, benchmarkSecPerTile: Double? = null): DeviceProfile {
        val app = context.applicationContext
        val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val ramGB = maxOf(1, Math.round(memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)).toInt())
        val cores = Runtime.getRuntime().availableProcessors()

        val vulkanSupport = runCatching {
            app.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        }.getOrDefault(false)
        val vulkanVersion = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val v = app.packageManager.systemAvailableFeatures
                    .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
                    ?.version
                if (v != null && v > 0) "${v shr 22}.${(v shr 12) and 0x3FF}.${v and 0xFFF}" else "1.0"
            } else {
                "1.0"
            }
        }.getOrDefault("1.0")

        val gpu = runCatching { GLES20.glGetString(GLES20.GL_RENDERER)?.trim().orEmpty() }
            .getOrDefault("")
            .ifBlank { "Unknown GPU" }

        val stat = runCatching { StatFs(app.filesDir.absolutePath) }.getOrNull()
        val totalBytes = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
        val freeBytes = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
        val totalGB = maxOf(1, Math.round(totalBytes.toDouble() / (1024 * 1024 * 1024)).toInt())
        val freeGB = Math.round(freeBytes.toDouble() / (1024 * 1024 * 1024) * 10) / 10.0

        val tier = when {
            ramGB >= 8 && cores >= 8 && vulkanSupport -> DeviceProfile.TIER_POWERFUL
            ramGB >= 6 || (ramGB >= 6 && vulkanSupport) -> DeviceProfile.TIER_STANDARD
            ramGB >= 4 && cores >= 4 -> DeviceProfile.TIER_STANDARD
            else -> DeviceProfile.TIER_LIGHT
        }

        val profile = DeviceProfile(
            tier = tier,
            label = DeviceProfile.labelFor(tier),
            ramGB = ramGB,
            cores = cores,
            gpu = gpu,
            vulkan = vulkanVersion,
            freeGB = freeGB,
            totalGB = totalGB,
            inputLimitMP = DeviceProfile.inputLimitMp(tier),
            outputCapMP = DeviceProfile.outputCapMp(tier),
            superCapMP = DeviceProfile.superCapMp(tier),
            benchmarkSecPerTile = benchmarkSecPerTile ?: cached.get()?.benchmarkSecPerTile ?: 0.42,
        )
        cached.set(profile)
        return profile
    }

    fun freeBytes(context: Context): Long =
        runCatching { StatFs(context.filesDir.absolutePath).availableBlocksLong * StatFs(context.filesDir.absolutePath).blockSizeLong }
            .getOrDefault(0L)

    fun cacheDirSizeBytes(context: Context): Long {
        val dir = File(context.cacheDir, "realsr")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

/** The five haptic kinds of the prototype (`light`, `medium`, `tick`, `success`, `warning`). */
enum class HapticKind { Light, Medium, Tick, Success, Warning }

class Haptics(context: Context) {
    private val app = context.applicationContext
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    /** Mirrors the prototype's patterns: 6–18 ms single taps and short two-pulse patterns. */
    fun play(kind: HapticKind, enabled: Boolean = true, api26: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!enabled) return
        val vibrator = vibrator ?: return
        runCatching {
            if (api26) {
                val effect = when (kind) {
                    HapticKind.Light -> VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticKind.Medium -> VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticKind.Tick -> VibrationEffect.createOneShot(6, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticKind.Success -> VibrationEffect.createWaveform(longArrayOf(0, 14, 70, 24), -1)
                    HapticKind.Warning -> VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    (longArrayOf(0, 14, 70, 24))
                )
            }
        }
    }
}
