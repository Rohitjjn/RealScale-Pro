package com.tumuyan.ncnn.realsr.core.plan

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pipeline Planner — a 1:1 Kotlin port of the prototype's `src/lib/plan.ts` (PRD 8 and 12.1).
 *
 * Every decision rule lives here; the UI only renders the resulting [Plan]. The TypeScript original
 * is the specification: `tools/plan-vectors` runs it under Node over a grid of inputs and the
 * golden JSON is asserted by [com.tumuyan.ncnn.realsr.core.plan.PipelinePlannerGoldenTest].
 */
enum class ModeId(val id: String) {
    UPSCALE("upscale"),
    SUPER("super"),
}

enum class ImageType { Photo, Anime }

enum class PlanLevel(val id: String) {
    DEFAULT("default"),
    MANUAL("manual"),
    ADVANCED("advanced"),
}

enum class TilePreset(val id: String) {
    SAFE("safe"),
    BALANCED("balanced"),
    FAST("fast"),
    MAX("max"),
    CUSTOM("custom"),
}

enum class OutputFormatPref(val id: String) {
    DEFAULT("default"),
    JPEG("jpeg"),
    PNG("png"),
    WEBP("webp"),
}

/** Everything the planner needs from a photo; the UI's Photo maps onto this. */
data class PlanInput(
    val width: Int,
    val height: Int,
    val sizeMB: Double,
    val ext: String,
    val type: ImageType,
    val lowConfidence: Boolean = false,
)

/** The subset of the device check the planner depends on. */
data class PlanDevice(
    val tier: Int,
    val inputLimitMP: Int,
    val outputCapMP: Int,
    val superCapMP: Int,
) {
    companion object {
        /** Values used by the prototype's design mock and by the golden vectors. */
        val Reference = PlanDevice(tier = 2, inputLimitMP = 4, outputCapMP = 64, superCapMP = 128)
    }
}

/** User-tunable job preferences, per mode. Field names and defaults match `JobPrefs`. */
data class JobPrefs(
    val level: PlanLevel = PlanLevel.DEFAULT,
    val type: String = "auto",
    val modelId: String = "auto",
    val scale: Int = 2,
    val tile: TilePreset = TilePreset.FAST,
    val tileCustom: Int = 320,
    val overlap: String = "auto",
    val compute: String = "auto",
    val threads: Int = 0,
    val compression: String = "auto",
    val compressionQ: Int = 90,
    val format: OutputFormatPref = OutputFormatPref.DEFAULT,
    val formatQ: Int = 95,
    val denoise: Int = 30,
    val tta: Boolean = false,
    val precision: String = "auto",
    val keepMeta: Boolean = true,
    val sharpen: Int = 0,
    val resize: String = "lanczos",
    val color: String = "keep",
    val cap: String = "none",
)

data class TilePresetInfo(
    val id: TilePreset,
    val label: String,
    val px: Int,
    val mem: String,
    val factor: Double,
    val recommended: Boolean = false,
    val disabledReason: String? = null,
)

val TILE_PRESETS: List<TilePresetInfo> = listOf(
    TilePresetInfo(TilePreset.SAFE, "Safe", 128, "0.4 GB", 1.3),
    TilePresetInfo(TilePreset.BALANCED, "Balanced", 256, "0.9 GB", 1.12),
    TilePresetInfo(TilePreset.FAST, "Fast", 384, "1.6 GB", 1.0, recommended = true),
    TilePresetInfo(
        TilePreset.MAX,
        "Maximum",
        0,
        "No tiling",
        0.92,
        disabledReason = "Maximum needs a Powerful device with 12 GB or more memory.",
    ),
)

sealed interface PlanCheck {
    data object Green : PlanCheck
    data class Amber(val kind: String, val title: String, val body: String) : PlanCheck
    data class Red(val title: String, val body: String, val maxScale: Int?) : PlanCheck
}

data class Plan(
    val level: PlanLevel,
    val type: ImageType,
    val scale: Int,
    val model: ModelEntry,
    val isAuto: Boolean,
    val wanted: ModelEntry?,
    val tilePx: Int,
    val compute: String,
    val inW: Int,
    val inH: Int,
    val inMP: Double,
    val overLimit: Boolean,
    val optimized: Boolean,
    val outW: Int,
    val outH: Int,
    val outMP: Double,
    val inputNote: String,
    val format: String,
    val ext: String,
    val etaSec: Int,
    val sizeMB: Double,
    val cols: Int,
    val rows: Int,
    val tiles: Int,
    val extraResize: Boolean,
    val check: PlanCheck,
)

object PipelinePlanner {

    /** Automatic model selection; Upscale Mode prefers the small/fast models. */
    fun recommendedId(mode: ModeId, type: ImageType): String = when (mode) {
        ModeId.UPSCALE -> if (type == ImageType.Photo) "esrgan-general-v3" else "realcugan-se2"
        ModeId.SUPER -> if (type == ImageType.Photo) "esrgan-x4plus" else "esrgan-anime-x4"
    }

    fun makePlan(
        photo: PlanInput,
        mode: ModeId,
        prefs: JobPrefs,
        installed: List<String>,
        device: PlanDevice,
        large: String = "optimize",
        settingsCompute: String = "auto",
        settingsFormat: String = "auto",
    ): Plan {
        val level = prefs.level
        val manual = level != PlanLevel.DEFAULT
        val adv = level == PlanLevel.ADVANCED
        val has = { id: String -> installed.contains(id) }

        val type = if (manual && prefs.type != "auto") {
            if (prefs.type == "anime") ImageType.Anime else ImageType.Photo
        } else {
            photo.type
        }
        val scale = if (manual) prefs.scale else 2

        // Model
        val wantedId = recommendedId(mode, type)
        val fallbackId = if (type == ImageType.Photo) "esrgan-general-v3" else "esrgan-v3-anime"
        var isAuto = true
        var wanted: ModelEntry? = null
        val model: ModelEntry = when {
            manual && prefs.modelId != "auto" && has(prefs.modelId) -> {
                isAuto = false
                ModelCatalog.byId(prefs.modelId)
            }
            has(wantedId) -> ModelCatalog.byId(wantedId)
            else -> {
                wanted = ModelCatalog.byId(wantedId)
                ModelCatalog.byId(fallbackId)
            }
        }

        // Tiles and compute
        val preset = TILE_PRESETS.firstOrNull { it.id == prefs.tile }
        val tilePx = when {
            !adv -> 384
            prefs.tile == TilePreset.CUSTOM -> prefs.tileCustom
            else -> preset?.px ?: 384
        }
        val tileFactor = when {
            !adv -> 1.0
            prefs.tile == TilePreset.CUSTOM -> when {
                tilePx >= 384 -> 1.0
                tilePx >= 256 -> 1.12
                tilePx >= 128 -> 1.3
                else -> 1.6
            }
            else -> preset?.factor ?: 1.0
        }
        val computePref = if (adv && prefs.compute != "auto") prefs.compute else settingsCompute
        val compute = if (computePref == "cpu") "CPU" else "GPU"

        // Input
        val rawMP = (photo.width.toDouble() * photo.height) / 1e6
        val overLimit = mode == ModeId.UPSCALE && rawMP > device.inputLimitMP
        val optimized = overLimit && ((!manual) || large != "full")
        var inW = photo.width
        var inH = photo.height
        if (optimized) {
            val k = sqrt(device.inputLimitMP / rawMP)
            inW = (photo.width * k).roundToInt()
            inH = (photo.height * k).roundToInt()
        }
        val outW = inW * scale
        val outH = inH * scale
        val outMP = (outW.toDouble() * outH) / 1e6

        val inputNote: String = when {
            mode == ModeId.SUPER -> "Original file, no compression"
            optimized -> "Large photo optimized for your device (${rawMP.roundToInt()} MP to ${device.inputLimitMP} MP)"
            else -> {
                val c = if (adv) prefs.compression else "auto"
                when {
                    c == "off" -> "Not compressed"
                    c == "custom" -> "Re-encoded at ${prefs.compressionQ}% quality"
                    photo.sizeMB > 2 -> "Re-encoded at 90% quality (file is over 2 MB)"
                    else -> "Used as is (2 MB or smaller)"
                }
            }
        }

        // Output format
        val source = if (photo.ext == "png") "PNG" else "JPEG"
        val formatPref = when {
            adv && prefs.format != OutputFormatPref.DEFAULT -> prefs.format.id
            settingsFormat != "auto" -> settingsFormat
            else -> null
        }
        val chosen = when (formatPref) {
            "jpeg" -> "JPEG"
            "png" -> "PNG"
            "webp" -> "WebP"
            else -> null
        }
        val format = chosen ?: if (mode == ModeId.SUPER) "PNG" else source
        val ext = when (format) {
            "PNG" -> "png"
            "WebP" -> "webp"
            else -> "jpg"
        }

        // Time estimate
        var sec = outMP * model.secPerMP * tileFactor * (if (compute == "CPU") 4 else 1)
        if (adv && prefs.tta) sec *= 8
        if (adv && prefs.precision == "fp32") sec *= 1.25
        if (scale == 5) sec += 1 + outMP * 0.05
        sec += 2
        val etaSec = max(5, sec.roundToInt())

        val perMP = when (format) {
            "PNG" -> 2.4
            "WebP" -> 0.45
            else -> 0.8
        }
        val sizeMB = max(0.1, (outMP * perMP * 10).roundToInt() / 10.0)

        val cols = if (tilePx > 0) max(1, ceil(inW.toDouble() / tilePx).toInt()) else 1
        val rows = if (tilePx > 0) max(1, ceil(inH.toDouble() / tilePx).toInt()) else 1

        // Feasibility
        val cap = if (mode == ModeId.UPSCALE) device.outputCapMP else device.superCapMP
        var check: PlanCheck = PlanCheck.Green
        if (outMP > cap + 0.01) {
            var maxScale: Int? = null
            for (s in intArrayOf(4, 3, 2)) {
                if (s < scale && (inW.toDouble() * inH * s * s) / 1e6 <= cap) {
                    maxScale = s
                    break
                }
            }
            check = PlanCheck.Red(
                title = "This is too large for your device.",
                body = "${fmtDims(outW, outH)} is ${outMP.roundToInt()} MP. Your device can safely produce up to $cap MP in this mode.",
                maxScale = maxScale,
            )
        } else if (format == "WebP" && max(outW, outH) > 16383) {
            check = PlanCheck.Amber(
                kind = "format",
                title = "Too large for WebP.",
                body = "WebP supports up to 16,383 px per side. PNG has no such limit.",
            )
        } else if (etaSec > 600) {
            check = PlanCheck.Amber(
                kind = "time",
                title = "This will take about ${fmtEta(etaSec)} on your device.",
                body = "You can keep going, or make it faster with a lower scale or a lighter model.",
            )
        }

        return Plan(
            level = level,
            type = type,
            scale = scale,
            model = model,
            isAuto = isAuto,
            wanted = wanted,
            tilePx = tilePx,
            compute = compute,
            inW = inW,
            inH = inH,
            inMP = (inW.toDouble() * inH) / 1e6,
            overLimit = overLimit,
            optimized = optimized,
            outW = outW,
            outH = outH,
            outMP = outMP,
            inputNote = inputNote,
            format = format,
            ext = ext,
            etaSec = etaSec,
            sizeMB = sizeMB,
            cols = cols,
            rows = rows,
            tiles = cols * rows,
            extraResize = scale == 5,
            check = check,
        )
    }
}

/* ------------------------------------------------------------------------------------------- */
/* Formatting — identical output to the TypeScript helpers                                      */
/* ------------------------------------------------------------------------------------------- */

fun fmtDims(w: Int, h: Int) = "$w × $h"

fun fmtMP(mp: Double): String = if (mp >= 10) "${mp.roundToInt()} MP" else String.format(Locale.US, "%.1f MP", mp)

fun fmtMB(mb: Double): String = when {
    mb >= 1000 -> String.format(Locale.US, "%.1f GB", mb / 1000)
    mb >= 100 -> "${mb.roundToInt()} MB"
    else -> String.format(Locale.US, "%.1f MB", mb)
}

/** Rounded estimate: "15 s", "3 min", "1.2 h". */
fun fmtEta(sec: Int): String = when {
    sec < 60 -> "${max(5, (sec / 5.0).roundToInt() * 5)} s"
    sec < 3600 -> "${(sec / 60.0).roundToInt()} min"
    else -> String.format(Locale.US, "%.1f h", sec / 3600.0)
}

/** Countdown: "2 min 10 s", "48 s". */
fun fmtLeft(sec: Double): String {
    val s = max(1, ceil(sec).toInt())
    if (s < 60) return "$s s"
    val m = floor(s / 60.0).toInt()
    val r = ((s % 60) / 5.0).roundToInt() * 5
    return if (r != 0) "$m min $r s" else "$m min"
}

fun fmtClock(sec: Double): String = "${floor(sec / 60).toInt()}:${floor(sec % 60).toInt().toString().padStart(2, '0')}"

/** `IMG_4807.jpg` → `IMG_4807_RealScale_2x_20261114-1642.jpg`. */
fun resultFileName(photoName: String, scale: Int, ext: String, at: java.util.Calendar): String {
    val base = photoName.substringBeforeLast('.', photoName)
    fun p(n: Int) = n.toString().padStart(2, '0')
    val stamp = "${at.get(java.util.Calendar.YEAR)}${p(at.get(java.util.Calendar.MONTH) + 1)}${p(at.get(java.util.Calendar.DAY_OF_MONTH))}-" +
        "${p(at.get(java.util.Calendar.HOUR_OF_DAY))}${p(at.get(java.util.Calendar.MINUTE))}"
    return "${base}_RealScale_${scale}x_${stamp}.$ext"
}

internal fun clampInt(value: Int, lo: Int, hi: Int) = min(hi, max(lo, value))
