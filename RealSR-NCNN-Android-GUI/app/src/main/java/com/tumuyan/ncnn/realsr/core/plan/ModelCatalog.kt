package com.tumuyan.ncnn.realsr.core.plan

/**
 * The model catalog the UI shows.
 *
 * Every entry is a real model published in the upstream mirror
 * (`huggingface.co/tumuyan2/realsr-models`) and every entry maps onto a real engine invocation:
 * [engineTool] is the binary the resolver launches and [sourceDir]/[entryFile] is what has to sit
 * under `models/<id>/`. Sizes are the measured byte totals of those files
 * (`docs/model-facts.json`, produced by `.github/workflows/model-facts.yml`); Anime4K needs no
 * weights because it is a shader pipeline inside the bundled binary, hence 0 MB.
 *
 * `secPerMP` is the seconds-per-output-megapixel used by the time estimate on the reference device
 * (Adreno 730 class, the prototype's DEVICE). Real jobs replace it with a measured rolling average.
 */
data class ModelEntry(
    val id: String,
    val name: String,
    val desc: String,
    val kind: ImageType,
    val scales: List<Int>,
    val engine: String,
    val sizeMB: Double,
    val speed: String,
    val badge: String,
    /** Lowest device tier that can run this model, 0-based (`DeviceProfile.tier` is 1..3). */
    val minTier: Int,
    val pack: String,
    val license: String,
    val secPerMP: Double,
    val denoise: Boolean = false,
    val blocked: String? = null,
    val required: Boolean = false,
    /** Binary the command builder launches. */
    val engineTool: String,
    /** Directory (or single file) inside the Hugging Face mirror. */
    val sourceDir: String,
    /**
     * Id of this model in `assets/model_manifest.json` and the name of its folder under
     * `filesDir/models/`. Null for models whose weights ship inside the APK (Anime4K shaders).
     */
    val manifestId: String? = null,
    /** For single-file MNN models: the file the engine is pointed at. */
    val entryFile: String? = null,
    /** Default `-n` level when the model takes one. */
    val denoiseLevel: Int? = null,
)

data class Pack(val id: String, val name: String, val note: String)

object ModelCatalog {

    val PACKS: List<Pack> = listOf(
        Pack("starter", "Starter models", "The two small models the app installs during setup."),
        Pack("catalog", "All models", "Every model the bundled ncnn / MNN engine can run."),
    )

    /**
     * Exactly the model folders the engine ships with, one entry per folder of the upstream
     * `tumuyan/RealSR-NCNN-Android` asset set (mirrored as `models-*` on
     * `huggingface.co/tumuyan2/realsr-models`, see `assets/model_manifest.json`).
     *
     * The mapping folder -> binary is the one upstream's own `CommandListManager` uses:
     * `models-upconv*` / `models-cunet` / `models-cugan*` -> `waifu2x-ncnn`, `models-srmd*` ->
     * `srmd-ncnn`, `models-MNN*` (`*.mnn`) -> `mnnsr-ncnn`, everything else (`models-DF2K*`,
     * `models-ESRGAN-*`, `models-RealeSR-*`) -> `realsr-ncnn`. Anything that is not in that list is
     * not in this catalog either: a row the engine cannot run is worse than no row.
     */
    val MODELS: List<ModelEntry> = listOf(
        /* ---- Starter: the only two models downloaded during setup ---- */
        ModelEntry(
            id = "esrgan-general-v3", name = "Real-ESRGAN General v3",
            desc = "Everyday photos. Small, fast and the best first model.", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 2.4, speed = "Very fast", badge = "Starter",
            minTier = 0, pack = "starter", license = "BSD-3-Clause", secPerMP = 3.0,
            required = true, engineTool = "realsr-ncnn", sourceDir = "models-RealeSR-general-v3",
            manifestId = "models-RealeSR-general-v3",
        ),
        ModelEntry(
            id = "waifu2x-anime", name = "Waifu2x Anime",
            desc = "Anime and illustration. Small, fast, noise-aware.", kind = ImageType.Anime,
            scales = listOf(2), engine = "ncnn", sizeMB = 5.5, speed = "Very fast", badge = "Starter",
            minTier = 0, pack = "starter", license = "MIT", secPerMP = 4.0,
            denoise = true, required = true, engineTool = "waifu2x-ncnn",
            sourceDir = "models-upconv_7_anime_style_art_rgb", denoiseLevel = 1,
            manifestId = "models-upconv_7_anime_style_art_rgb",
        ),

        /* ---- Photo models ---- */
        ModelEntry(
            id = "waifu2x-photo", name = "Waifu2x Photo",
            desc = "Photographs with light noise, very light on memory.", kind = ImageType.Photo,
            scales = listOf(2), engine = "ncnn", sizeMB = 5.5, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "catalog", license = "MIT", secPerMP = 4.0,
            denoise = true, engineTool = "waifu2x-ncnn", sourceDir = "models-upconv_7_photo",
            denoiseLevel = 1, manifestId = "models-upconv_7_photo",
        ),
        ModelEntry(
            id = "srmd-x2", name = "SRMD",
            desc = "Photos and scans. Handles 2×, 3× and 4× with the noise slider.", kind = ImageType.Photo,
            scales = listOf(2, 3, 4), engine = "ncnn", sizeMB = 18.4, speed = "Fast", badge = "Balanced",
            minTier = 0, pack = "catalog", license = "MIT", secPerMP = 5.0,
            denoise = true, engineTool = "srmd-ncnn", sourceDir = "models-srmd",
            denoiseLevel = 0, manifestId = "models-srmd",
        ),
        ModelEntry(
            id = "df2k", name = "RealSR DF2K",
            desc = "Detailed photos. Classic RealSR weights at 4×.", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 33.5, speed = "Medium", badge = "Balanced",
            minTier = 0, pack = "catalog", license = "MIT", secPerMP = 12.0,
            engineTool = "realsr-ncnn", sourceDir = "models-DF2K", manifestId = "models-DF2K",
        ),
        ModelEntry(
            id = "df2k-jpeg", name = "RealSR DF2K JPEG",
            desc = "Photos that came back from JPEG compression.", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 33.5, speed = "Medium", badge = "Balanced",
            minTier = 0, pack = "catalog", license = "MIT", secPerMP = 12.0,
            engineTool = "realsr-ncnn", sourceDir = "models-DF2K_JPEG", manifestId = "models-DF2K_JPEG",
        ),
        ModelEntry(
            id = "esrgan-remacri", name = "Remacri x4",
            desc = "Detailed textures and landscapes. Super Resolution favourite.", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 33.6, speed = "Medium", badge = "Best quality",
            minTier = 1, pack = "catalog", license = "CC-BY-NC-SA-4.0", secPerMP = 14.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-Remacri",
            manifestId = "models-ESRGAN-Remacri",
        ),
        ModelEntry(
            id = "esrgan-ultramix", name = "UltraMix Balanced x4",
            desc = "Balanced photos, keeps natural colours.", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 33.6, speed = "Medium", badge = "Balanced",
            minTier = 1, pack = "catalog", license = "CC-BY-NC-SA-4.0", secPerMP = 13.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-UltraMix_Balanced",
            manifestId = "models-ESRGAN-UltraMix_Balanced",
        ),
        ModelEntry(
            id = "esrgan-ultrasharp", name = "UltraSharp x4 (fp16)",
            desc = "Crisp edges on photos and screenshots.", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 33.6, speed = "Medium", badge = "Best quality",
            minTier = 1, pack = "catalog", license = "CC-BY-NC-SA-4.0", secPerMP = 14.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-UltraSharp-fp16",
            manifestId = "models-ESRGAN-UltraSharp-fp16",
        ),
        ModelEntry(
            id = "esrgan-nomos8ksc", name = "Nomos8kSC x4",
            desc = "Heavy, detailed photos. Best on powerful phones.", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 33.5, speed = "Slow", badge = "Best quality",
            minTier = 2, pack = "catalog", license = "CC-BY-NC-SA-4.0", secPerMP = 18.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-Nomos8kSC",
            manifestId = "models-ESRGAN-Nomos8kSC",
        ),

        /* ---- Anime models ---- */
        ModelEntry(
            id = "waifu2x-cunet", name = "Waifu2x CUnet",
            desc = "Anime with stronger noise removal, still only 2×.", kind = ImageType.Anime,
            scales = listOf(2), engine = "ncnn", sizeMB = 13.9, speed = "Fast", badge = "Balanced",
            minTier = 0, pack = "catalog", license = "MIT", secPerMP = 6.0,
            denoise = true, engineTool = "waifu2x-ncnn", sourceDir = "models-cunet",
            denoiseLevel = 1, manifestId = "models-cunet",
        ),
        ModelEntry(
            id = "esrgan-anime-sharp-lite", name = "AnimeSharp Lite x4",
            desc = "Anime and art, light and sharp.", kind = ImageType.Anime,
            scales = listOf(4), engine = "ncnn", sizeMB = 20.1, speed = "Fast", badge = "Balanced",
            minTier = 0, pack = "catalog", license = "CC-BY-NC-SA-4.0", secPerMP = 8.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-AnimeSharpLite",
            manifestId = "models-ESRGAN-AnimeSharpLite",
        ),
        ModelEntry(
            id = "esrgan-anime-sharp", name = "AnimeSharp x4",
            desc = "Anime and art, high quality. Super Resolution favourite.", kind = ImageType.Anime,
            scales = listOf(4), engine = "ncnn", sizeMB = 33.5, speed = "Medium", badge = "Best quality",
            minTier = 1, pack = "catalog", license = "CC-BY-NC-SA-4.0", secPerMP = 12.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-AnimeSharp",
            manifestId = "models-ESRGAN-AnimeSharp",
        ),
        ModelEntry(
            id = "mnn-anime-6b", name = "RealESRGAN Anime 6B (MNN)",
            desc = "Six-block anime model on the MNN engine.", kind = ImageType.Anime,
            scales = listOf(4), engine = "MNN", sizeMB = 17.9, speed = "Fast", badge = "Balanced",
            minTier = 0, pack = "catalog", license = "BSD-3-Clause", secPerMP = 9.0,
            engineTool = "mnnsr-ncnn", sourceDir = "models-MNNSR",
            entryFile = "RealESRGAN_x4plus_anime_6B-x4.mnn",
            manifestId = "models-MNN-RealESRGAN_x4plus_anime_6B",
        ),

        /* ---- Bundled: shaders inside the Anime4k binary, nothing to download ---- */
        ModelEntry(
            id = "anime4k-x2", name = "Anime4K 2×",
            desc = "Anime, the fastest option there is. No download.", kind = ImageType.Anime,
            scales = listOf(2), engine = "ncnn", sizeMB = 0.0, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "catalog", license = "MIT", secPerMP = 1.6,
            engineTool = "Anime4k", sourceDir = "",
        ),
        ModelEntry(
            id = "anime4k-x4", name = "Anime4K 4×",
            desc = "Anime at 4× from the bundled shaders. No download.", kind = ImageType.Anime,
            scales = listOf(4), engine = "ncnn", sizeMB = 0.0, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "catalog", license = "MIT", secPerMP = 2.2,
            engineTool = "Anime4k", sourceDir = "",
        ),
    )

    /** The models the app installs on first run: one photo model and one anime model. */
    fun starterModels(): List<ModelEntry> = MODELS.filter { it.required }

    /** Everything the Model Manager lists under All / Photo / Anime, starter pack excluded. */
    fun catalogModels(filter: ImageType? = null): List<ModelEntry> = MODELS.filter { model ->
        !model.required && when (filter) {
            null -> true
            ImageType.Photo -> model.kind == ImageType.Photo
            ImageType.Anime -> model.kind == ImageType.Anime
        }
    }

    val STARTER_IDS: List<String> = MODELS.filter { it.required }.map { it.id }

    fun byId(id: String): ModelEntry = MODELS.firstOrNull { it.id == id } ?: MODELS.first()

    /** The catalog entry that a manifest id (an HF directory) installs into. */
    fun byManifestId(manifestId: String): ModelEntry? =
        MODELS.firstOrNull { it.manifestId == manifestId }

    /** Ids the planner can treat as installed without a download (Anime4K ships its shaders). */
    val BUNDLED_IDS: List<String> = MODELS.filter { it.manifestId == null && it.blocked == null }.map { it.id }

    fun packOf(id: String): Pack = PACKS.firstOrNull { it.id == id } ?: PACKS.first()

    /**
     * The factor a network should run at to satisfy a request for [scale].
     *
     * Real-ESRGAN weights are 4×-only, waifu2x 2×, SRMD 2/3/4 — running `-s 3` on a 4×-only network
     * is what silently produced the wrong size before. Pick the largest supported factor that does
     * not overshoot, otherwise the smallest one, and let the engine's resize pass reach `scale`.
     */
    fun modelScaleFor(model: ModelEntry, scale: Int): Int {
        val supported = model.scales.ifEmpty { listOf(4) }
        return supported.filter { it <= scale }.maxOrNull() ?: supported.min()
    }

    /**
     * How this model behaves on a device of [tier]. `minTier` counts from 0, so a model with
     * `minTier = 2` needs tier 3 (Powerful).
     */
    fun fit(model: ModelEntry, tier: Int): Fit = when {
        model.blocked != null -> Fit("no", "Not available")
        model.minTier >= tier -> {
            // A model with minTier = n needs device tier n + 1.
            val needed = when (model.minTier) {
                0 -> "compatible"
                1 -> "Standard"
                2 -> "Powerful"
                else -> "Powerful"
            }
            Fit("no", "Needs a $needed device")
        }
        model.speed == "Medium" || model.speed == "Slow" -> Fit("ok", "Runs OK on your device")
        else -> Fit("good", "Great on your device")
    }

    data class Fit(val tone: String, val label: String)
}
