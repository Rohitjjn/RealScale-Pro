package com.tumuyan.ncnn.realsr.core.engine.planner

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pipeline Planner — port of the design's `src/lib/plan.ts`, extended for the real engine.
 * Pure Kotlin, no Android dependencies: every decision rule lives here, and the UI only
 * renders the resulting [Plan]. Unit-tested in PipelinePlannerTest.
 *
 * Deliberate deviations from the prototype (documented in docs/FIDELITY.md):
 *  - `faces`/`faceQuality` are NOT modelled: no face pipeline exists on-device.
 *  - `overlap` is NOT modelled: the upstream CLI exposes no overlap flag (it is internal to ncnn).
 *  - `tta`/`precision` are NOT modelled: the upstream CLI exposes no such flags.
 *  - `compression`/`formatQ` are NOT modelled: local runs are bounded by pixels, not bytes,
 *    and the CLI takes only a format flag — no quality knob.
 *  - `cap` is NOT modelled: beyond-cap jobs are rejected by the red check in [Plan.check].
 *  - ETA seconds-per-megapixel is NOT a catalog constant; callers inject rolling averages
 *    from real finished jobs (see EtaTracker). ETA stays null while no sample exists.
 *  - scale==5 (or a scale the chosen model lacks) is executed as the nearest supported
 *    native scale plus one lanczos post-resize, like the design's "extra resize step".
 */

@kotlinx.serialization.Serializable
enum class ModeId { UPSCALE, SUPER }
@kotlinx.serialization.Serializable
enum class Level { DEFAULT, MANUAL, ADVANCED }
@kotlinx.serialization.Serializable
enum class TilePreset { SAFE, BALANCED, FAST, MAX, CUSTOM }
@kotlinx.serialization.Serializable
enum class ImageType { PHOTO, ANIME }
@kotlinx.serialization.Serializable
enum class ComputePref { AUTO, GPU, CPU }
@kotlinx.serialization.Serializable
enum class OutputFormatPref { DEFAULT, JPEG, PNG, WEBP }

@kotlinx.serialization.Serializable
data class JobPrefs(
    val level: Level = Level.DEFAULT,
    val type: ImageType? = null,          // null = Auto (classifier)
    val modelId: String = "auto",
    val scale: Int = 2,
    val tile: TilePreset = TilePreset.FAST,
    val tileCustom: Int = 320,
    val compute: ComputePref = ComputePref.AUTO,
    val threads: Int = 0,                  // 0 = engine default
    val format: OutputFormatPref = OutputFormatPref.DEFAULT,
    val keepMeta: Boolean = true,
    /** Denoise level for waifu2x/realcugan/srmd (-n 0..3); null = model/preset default. */
    val denoise: Int? = null,
) {
    companion object {
        fun default() = JobPrefs()
    }
}

data class TilePresetInfo(val id: TilePreset, val label: String, val px: Int, val mem: String, val factor: Double, val recommended: Boolean, val disabled: String?)

val TILE_PRESETS: List<TilePresetInfo> = listOf(
    TilePresetInfo(TilePreset.SAFE, "Safe", 128, "0.4 GB", 1.3, recommended = false, disabled = null),
    TilePresetInfo(TilePreset.BALANCED, "Balanced", 256, "0.9 GB", 1.12, recommended = false, disabled = null),
    TilePresetInfo(TilePreset.FAST, "Fast", 384, "1.6 GB", 1.0, recommended = true, disabled = null),
    TilePresetInfo(TilePreset.MAX, "Maximum", 0, "No tiling", 0.92, recommended = false,
        disabled = "Maximum needs a Powerful device with 12 GB or more memory."),
)

/** Planner-facing view of a catalog model (manifest mapped in ModelCatalogMapper). */
data class PlannerModel(
    val id: String,
    val displayName: String,
    val kind: ModelKind,
    val scales: List<Int>,
    val sizeMB: Double,
    val minTier: Int,
    val license: String,
    val required: Boolean,
    /**
     * Seconds per OUTPUT megapixel measured from the user's own finished jobs, or null when
     * fewer than [EtaTracker.MIN_SAMPLES] samples exist. The planner never invents a constant.
     */
    val secPerMP: Double? = null,
) {
    enum class ModelKind { PHOTO, ANIME, OTHER }
}

/** Mappers derive planner kinds/tiers from the real manifest (keep ModelRepository untouched). */
data class ModelCatalogMapper(
    /** id -> min tier (0 Light / 1 Standard / 2 Powerful); unknown ids map to 0. */
    val minTierById: Map<String, Int> = emptyMap(),
    val requiredIds: Set<String> = STARTER_IDS,
    /** id -> seconds per output megapixel from EtaTracker; missing ids stay null (no estimate). */
    val secPerMpById: Map<String, Double> = emptyMap(),
) {
    fun toPlannerModel(item: com.tumuyan.ncnn.realsr.core.model.ModelCatalogItem): PlannerModel {
        val kind = when (item.category.uppercase()) {
            "PHOTO" -> PlannerModel.ModelKind.PHOTO
            "ANIME" -> PlannerModel.ModelKind.ANIME
            else -> PlannerModel.ModelKind.OTHER
        }
        return PlannerModel(
            id = item.id,
            displayName = item.displayName,
            kind = kind,
            scales = item.supportedScales,
            sizeMB = item.totalBytes / 1_000_000.0,
            minTier = minTierById[item.id] ?: 0,
            license = "Model licence — see About · Licences",
            required = item.id in requiredIds,
            secPerMP = secPerMpById[item.id],
        )
    }
}

/** Design starter pack (everything Upscale Mode needs). Installed during setup. */
val STARTER_IDS: Set<String> = setOf(
    "models-upconv_7_photo",              // Gentle Photo 2x/4x - default photo model
    "models-upconv_7_anime_style_art_rgb", // Anime & Illustration 2x/4x - default anime model
    "models-RealeSR-general-v3",           // 4x photo, powers the engine's PHOTO preset
)

/** Design "Pro" grouping: everything else in the real manifest (single additional pack). */
val PRO_IDS: Set<String> = setOf(
    "models-DF2K",
    "models-DF2K_JPEG",
    "models-ESRGAN-Nomos8kSC",
    "models-ESRGAN-UltraSharp-fp16",
    "models-ESRGAN-Remacri",
    "models-ESRGAN-UltraMix_Balanced",
    "models-cunet",
    "models-srmd",
    "models-MNN-RealESRGAN_x4plus_anime_6B",
    "models-ESRGAN-AnimeSharpLite",
    "models-ESRGAN-AnimeSharp",
)

/** What the planner wants installed for each (mode, type). */
fun recommendedId(mode: ModeId, type: ImageType): String = when (mode) {
    // Upscale (fast): small, fast, native 2x models.
    ModeId.UPSCALE -> if (type == ImageType.PHOTO) "models-upconv_7_photo" else "models-upconv_7_anime_style_art_rgb"
    // Super Resolution (heavy): bigger models, one extra quality tier.
    ModeId.SUPER -> if (type == ImageType.PHOTO) "models-DF2K" else "models-MNN-RealESRGAN_x4plus_anime_6B"
}

/** Guaranteed-starter fallback when the wanted model is missing but the app should still work. */
fun fallbackId(type: ImageType): String =
    if (type == ImageType.PHOTO) "models-upconv_7_photo" else "models-upconv_7_anime_style_art_rgb"

sealed interface Check {
    data object Green : Check
    data class Amber(val kind: AmberKind, val title: String, val body: String) : Check
    data class Red(val title: String, val body: String, val maxScale: Int?) : Check

    enum class AmberKind { TIME, FORMAT }
}

data class Plan(
    val level: Level,
    val type: ImageType,
    val scale: Int,
    val model: PlannerModel,
    val isAuto: Boolean,
    /** The model the planner prefers but that is not installed yet (download CTA). */
    val wanted: PlannerModel?,
    val tilePx: Int,                    // 0 = no tiling
    val compute: ComputeKind,
    val inW: Int, val inH: Int, val inMP: Double,
    val overLimit: Boolean, val optimized: Boolean,
    val outW: Int, val outH: Int, val outMP: Double,
    val inputNote: String,
    val format: OutputFormat,
    val ext: String,
    /** null when fewer than MIN_SAMPLES real job samples exist for the chosen model. */
    val etaSec: Long?,
    val sizeMB: Double,
    val cols: Int, val rows: Int, val tiles: Int,
    /** Executed natively at [runScale]; the extra resize (≈ sharpening-free) lands on top. */
    val runScale: Int,
    val postResize: Double?,            // null = single step; 1.25 for 5×; 1.5 for 3× fallback
    val check: Check,
) {
    enum class ComputeKind { GPU, CPU }
    enum class OutputFormat { JPEG, PNG, WEBP }
}

private fun fmtDims(w: Int, h: Int) = "$w × $h"

object PipelinePlanner {

    fun makePlan(
        inW: Int,
        inH: Int,
        sourceExt: String,                // "jpg" | "png" | "webp" | other
        sizeMB: Double,
        mode: ModeId,
        prefs: JobPrefs,
        installed: List<PlannerModel>,
        device: DeviceProfile,
        opts: Opts = Opts(),
    ): Plan {
        val level = prefs.level
        val manual = level != Level.DEFAULT
        val adv = level == Level.ADVANCED

        val type: ImageType = if (manual && prefs.type != null) prefs.type else (opts.autoType ?: ImageType.PHOTO)
        val requestedScale = if (manual) prefs.scale.coerceIn(2, 5) else 2

        // --- Model resolution (design 8.3) --------------------------------------------
        val wantedId = recommendedId(mode, type)
        val fbId = fallbackId(type)
        val installedById = installed.associateBy { it.id }
        val has = { id: String -> id in installedById }

        var isAuto = true
        var wanted: PlannerModel? = null
        val model: PlannerModel = when {
            manual && prefs.modelId != "auto" && has(prefs.modelId) -> {
                isAuto = false
                installedById.getValue(prefs.modelId)
            }
            has(wantedId) -> installedById.getValue(wantedId)
            has(fbId) -> {
                wanted = if (has(wantedId)) null else PlannerModel(
                    id = wantedId,
                    displayName = "",
                    kind = PlannerModel.ModelKind.OTHER,
                    scales = emptyList(),
                    sizeMB = 0.0,
                    minTier = 0,
                    license = "",
                    required = false,
                ) // wanted model not installed → download CTA target
                installedById.getValue(fbId)
            }
            // Nothing usable installed: fall back to any installed model so the UI can still explain.
            else -> installed.firstOrNull()
                ?: error("PipelinePlanner requires at least one installed model; starter pack installs during setup")
        }

        // --- Tiles and compute ---------------------------------------------------------
        val preset = TILE_PRESETS.find { it.id == prefs.tile }
        val tilePx: Int = when {
            !adv -> 384
            prefs.tile == TilePreset.CUSTOM -> prefs.tileCustom.coerceIn(64, 512)
            prefs.tile == TilePreset.MAX -> 0
            else -> preset?.px ?: 384
        }
        val tileFactor: Double = when {
            !adv -> 1.0
            prefs.tile == TilePreset.CUSTOM -> when {
                tilePx >= 384 -> 1.0
                tilePx >= 256 -> 1.12
                tilePx >= 128 -> 1.3
                else -> 1.6
            }
            else -> preset?.factor ?: 1.0
        }
        val computePref = if (adv && prefs.compute != ComputePref.AUTO) prefs.compute else opts.compute
        val compute = when (computePref) {
            ComputePref.CPU -> Plan.ComputeKind.CPU
            else -> Plan.ComputeKind.GPU
        }

        // --- Input (design 5.2 / 8.6) --------------------------------------------------
        val rawMP = (inW.toLong() * inH.toLong()) / 1e6
        val overLimit = mode == ModeId.UPSCALE && rawMP > device.inputLimitMP
        val optimized = overLimit && !(manual && opts.large == LargeOption.KEEP_FULL)
        var effW = inW
        var effH = inH
        if (optimized) {
            val k = sqrt(device.inputLimitMP / rawMP)
            effW = max(1, (inW * k).roundToInt())
            effH = max(1, (inH * k).roundToInt())
        }

        val inputNote: String = when {
            mode == ModeId.SUPER -> "Original file, no compression"
            optimized -> "Large photo optimized for your device (${rawMP.roundToInt()} MP to ${device.inputLimitMP.roundToInt()} MP)"
            else -> "Used as is"
        }

        // --- Native run scale + post-resize (design: 5× = 4× + resize) -----------------
        val (runScale, postResize) = nativePlan(model.scales, requestedScale)
        val outW = effW * requestedScale
        val outH = effH * requestedScale
        val outMP = (outW.toLong() * outH.toLong()) / 1e6
        // Tile grid: while a post-resize exists, the network sees runScale output.
        val nativeOutW = effW * runScale
        val nativeOutH = effH * runScale
        val cols = if (tilePx > 0) max(1, ceil(nativeOutW.toDouble() / tilePx).toInt()) else 1
        val rows = if (tilePx > 0) max(1, ceil(nativeOutH.toDouble() / tilePx).toInt()) else 1

        // --- Output format (design 7.3) -------------------------------------------------
        val source: Plan.OutputFormat = when (sourceExt.lowercase()) {
            "png" -> Plan.OutputFormat.PNG
            "webp" -> Plan.OutputFormat.WEBP
            else -> Plan.OutputFormat.JPEG
        }
        val formatPref = if (manual && prefs.format != OutputFormatPref.DEFAULT) prefs.format else opts.format
        val format: Plan.OutputFormat = when (formatPref) {
            OutputFormatPref.JPEG -> Plan.OutputFormat.JPEG
            OutputFormatPref.PNG -> Plan.OutputFormat.PNG
            OutputFormatPref.WEBP -> Plan.OutputFormat.WEBP
            OutputFormatPref.DEFAULT -> if (mode == ModeId.SUPER) Plan.OutputFormat.PNG else source
        }
        val ext = when (format) {
            Plan.OutputFormat.JPEG -> "jpg"
            Plan.OutputFormat.PNG -> "png"
            Plan.OutputFormat.WEBP -> "webp"
        }

        // --- Time estimate (rolling averages only — never constants) -------------------
        val etaSec: Long? = model.secPerMP?.let { perMp ->
            var sec = outMP * perMp * tileFactor * (if (compute == Plan.ComputeKind.CPU) 4.0 else 1.0)
            if (postResize != null) sec += 1.0 + outMP * 0.05
            sec += 2.0
            max(5L, sec.roundToInt().toLong())
        }

        val perMP = when (format) {
            Plan.OutputFormat.PNG -> 2.4
            Plan.OutputFormat.WEBP -> 0.45
            Plan.OutputFormat.JPEG -> 0.8
        }
        val sizeMB = max(0.1, ((outMP * perMP * 10).roundToInt() / 10.0))

        // --- Feasibility (design 6.5 / 8.6) ---------------------------------------------
        val cap = if (mode == ModeId.UPSCALE) device.outputCapMP else device.superCapMP
        var check: Check = Check.Green
        if (outMP > cap + 0.01) {
            var maxScale: Int? = null
            for (s in listOf(4, 3, 2)) {
                if (s < requestedScale && ((effW.toLong() * effH.toLong() * s * s) / 1e6) <= cap) {
                    maxScale = s
                    break
                }
            }
            check = Check.Red(
                title = "This is too large for your device.",
                body = "${fmtDims(outW, outH)} is ${outMP.roundToInt()} MP. Your device can safely produce up to ${cap.roundToInt()} MP in this mode.",
                maxScale = maxScale,
            )
        } else if (format == Plan.OutputFormat.WEBP && max(outW, outH) > 16383) {
            check = Check.Amber(
                Check.AmberKind.FORMAT,
                "Too large for WebP.",
                "WebP supports up to 16,383 px per side. PNG has no such limit.",
            )
        } else if (etaSec != null && etaSec > 600) {
            check = Check.Amber(
                Check.AmberKind.TIME,
                "This will take about ${fmtEta(etaSec)} on your device.",
                "You can keep going, or make it faster with a lower scale or a lighter model.",
            )
        }

        return Plan(
            level = level,
            type = type,
            scale = requestedScale,
            model = model,
            isAuto = isAuto,
            wanted = wanted,
            tilePx = tilePx,
            compute = compute,
            inW = effW, inH = effH, inMP = (effW.toLong() * effH.toLong()) / 1e6,
            overLimit = overLimit, optimized = optimized,
            outW = outW, outH = outH, outMP = outMP,
            inputNote = inputNote,
            format = format, ext = ext,
            etaSec = etaSec,
            sizeMB = sizeMB,
            cols = cols, rows = rows, tiles = cols * rows,
            runScale = runScale,
            postResize = postResize,
            check = check,
        )
    }

    /** Pick the native scale to run and the post-resize factor to land on [requestedScale]. */
    fun nativePlan(supported: List<Int>, requestedScale: Int): Pair<Int, Double?> {
        if (supported.isEmpty()) return requestedScale to null // e.g. resize-ncnn path handles any scale
        if (requestedScale in supported) return requestedScale to null
        val below = supported.filter { it < requestedScale }
        val base = below.maxOrNull() ?: supported.minOrNull()!!
        return base to requestedScale.toDouble() / base.toDouble()
    }

    data class Opts(
        val large: LargeOption = LargeOption.OPTIMIZE,
        val compute: ComputePref = ComputePref.AUTO,
        val format: OutputFormatPref = OutputFormatPref.DEFAULT,
        val autoType: ImageType? = null,
    )

    enum class LargeOption { OPTIMIZE, KEEP_FULL }
}

/* ---------- Formatting (design helpers) ---------- */
val fmtDimsPublic: (Int, Int) -> String = { w, h -> "$w × $h" }
fun fmtMP(mp: Double): String {
    if (mp >= 10) return "${mp.roundToInt()} MP"
    val tenths = (mp * 10).toInt() % 10
    if (tenths < 5) return "${mp.toInt()} MP"
    return String.format(java.util.Locale.US, "%.1f MP", mp)
}
fun fmtMB(mb: Double): String = when {
    mb >= 1000 -> String.format(java.util.Locale.US, "%.1f GB", mb / 1000)
    mb >= 100 -> "${mb.roundToInt()} MB"
    else -> {
        val s = String.format(java.util.Locale.US, "%.1f MB", mb)
        if (s.endsWith(".0 MB")) s.dropLast(5) + " MB" else s
    }
}
fun fmtEta(sec: Long): String = when {
    sec < 60 -> "${max(5L, ((sec + 2) / 5 * 5))} s"
    sec < 3600 -> "${(sec + 30) / 60} min"
    else -> String.format(java.util.Locale.US, "%.1f h", sec / 3600.0)
}
/** Countdown: "2 min 10 s", "48 s". */
fun fmtLeft(sec: Long): String {
    val s = max(1L, sec)
    if (s < 60) return "$s s"
    val m = s / 60
    val r = ((s % 60 + 2) / 5 * 5) % 60
    return if (r == 0L) "$m min" else "$m min $r s"
}
fun fmtClock(sec: Long): String = String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60)

/** `<base>_RealScale_<scale>x_<yyyymmdd-HHmm>.<ext>` — deterministic given [at]. */
fun resultFileName(sourceName: String, scale: Int, ext: String, at: java.time.LocalDateTime): String {
    val base = sourceName.substringBeforeLast('.', sourceName)
    val stamp = at.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    return "${base}_RealScale_${scale}x_$stamp.$ext"
}
