package com.tumuyan.ncnn.realsr.core.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

enum class PreflightSeverity {
    WARNING,
    BLOCKING_STORAGE
}

sealed interface PreflightMitigation {
    val label: String

    data object LowerScaleTo2x : PreflightMitigation {
        override val label: String = "Use 2× scale instead"
    }

    data class ProcessInTiles(val tileSize: Int = 128) : PreflightMitigation {
        override val label: String = "Process in ${tileSize} px tiles"
    }

    data class SmallerTileSize(val tileSize: Int = 64) : PreflightMitigation {
        override val label: String = "Use smaller tile size (${tileSize} px)"
    }
}

sealed interface PreflightVerdict {
    data object Safe : PreflightVerdict

    data class Advisory(
        val severity: PreflightSeverity,
        val title: String,
        val message: String,
        val mitigations: List<PreflightMitigation>,
        val canProceedAnyway: Boolean
    ) : PreflightVerdict
}

/**
 * Evaluates input dimensions, target scale, tile size, device RAM, and available storage
 * before launching native enhancement so large jobs offer one-tap mitigations up front.
 */
object PreflightAnalyzer {

    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB
    private const val MAX_COMFORTABLE_EDGE_PX = 8_192L

    fun evaluate(
        inputDimensions: List<Pair<Int, Int>>,
        scale: Int,
        preset: UpscalePreset,
        tileSize: Int,
        outputFormat: OutputFormat,
        deviceRamBytes: Long,
        freeStorageBytes: Long
    ): PreflightVerdict {
        val validDims = inputDimensions.filter { it.first > 0 && it.second > 0 }
        val bytesPerOutPixel = when (outputFormat) {
            OutputFormat.PNG -> 1.8
            OutputFormat.WEBP -> 0.65
            OutputFormat.JPG -> 0.75
        }

        val estimatedTotalOutputBytes = if (validDims.isEmpty()) {
            24L * MB
        } else {
            validDims.sumOf { (w, h) ->
                val outPixels = (w.toLong() * scale) * (h.toLong() * scale)
                (outPixels * bytesPerOutPixel).toLong().coerceAtLeast(1L * MB)
            }
        }

        // 1. Storage check (intermediate PNG + saved output headroom)
        val minimumBlockingBytes = (estimatedTotalOutputBytes + 16L * MB).coerceAtLeast(24L * MB)
        if (freeStorageBytes in 1 until minimumBlockingBytes) {
            val neededMb = minimumBlockingBytes / MB
            val freeMb = freeStorageBytes / MB
            val mitigations = buildList {
                if (scale > 2 && 2 in preset.supportedScales) {
                    add(PreflightMitigation.LowerScaleTo2x)
                }
            }
            return PreflightVerdict.Advisory(
                severity = PreflightSeverity.BLOCKING_STORAGE,
                title = "Low storage space",
                message = "Only ${freeMb} MB is free, and this output needs at least ~${neededMb} MB of temporary and gallery space. Free up space or lower the output scale.",
                mitigations = mitigations,
                canProceedAnyway = false
            )
        }

        if (validDims.isEmpty()) return PreflightVerdict.Safe

        // 2. Peak single-image output dimensions & megapixel pressure vs device RAM
        val peak = validDims.maxBy { (w, h) -> w.toLong() * h.toLong() }
        val outW = peak.first.toLong() * scale.toLong()
        val outH = peak.second.toLong() * scale.toLong()
        val outMegapixels = (outW * outH).toDouble() / 1_000_000.0

        val safeMegapixelCeiling = when {
            deviceRamBytes in 1 until (4L * GB) -> 20.0
            deviceRamBytes in (4L * GB) until (7L * GB) -> 34.0
            else -> 48.0
        }

        val exceedsEdge = outW > MAX_COMFORTABLE_EDGE_PX || outH > MAX_COMFORTABLE_EDGE_PX
        val exceedsMegapixels = outMegapixels > safeMegapixelCeiling
        val untiledHighRes = preset.id != PresetId.INSTANT &&
            outMegapixels > (safeMegapixelCeiling * 0.65) &&
            (tileSize == 0 || tileSize > 128)

        if (!exceedsEdge && !exceedsMegapixels && !untiledHighRes) {
            return PreflightVerdict.Safe
        }

        val mitigations = buildList {
            if (scale > 2 && 2 in preset.supportedScales) {
                add(PreflightMitigation.LowerScaleTo2x)
            }
            if (preset.id != PresetId.INSTANT && (tileSize == 0 || tileSize > 128)) {
                add(PreflightMitigation.ProcessInTiles(128))
            }
            if (preset.id != PresetId.INSTANT && (tileSize == 0 || tileSize > 64)) {
                add(PreflightMitigation.SmallerTileSize(64))
            }
        }

        val mpFormatted = String.format(Locale.US, "%.1f MP", outMegapixels)
        return PreflightVerdict.Advisory(
            severity = PreflightSeverity.WARNING,
            title = "Large output ($outW × $outH · $mpFormatted)",
            message = "Enhancing at ${scale}× creates a very large image that can exhaust graphics memory on mobile GPUs. Using smaller tiles or 2× scale keeps processing fast and reliable.",
            mitigations = mitigations,
            canProceedAnyway = true
        )
    }

    suspend fun inspectAndEvaluate(
        context: Context,
        uris: List<Uri>,
        scale: Int,
        preset: UpscalePreset,
        preferences: EnginePreferences
    ): PreflightVerdict = withContext(Dispatchers.IO) {
        val dims = uris.mapNotNull { uri ->
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    opts.outWidth to opts.outHeight
                } else {
                    null
                }
            }.getOrNull()
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem.takeIf { it > 0L } ?: (6L * GB)

        val freeDisk = runCatching {
            StatFs(context.filesDir.absolutePath).availableBytes
        }.getOrDefault(4L * GB)

        evaluate(
            inputDimensions = dims,
            scale = scale,
            preset = preset,
            tileSize = preferences.tileSize,
            outputFormat = preferences.outputFormat,
            deviceRamBytes = totalRam,
            freeStorageBytes = freeDisk
        )
    }
}
