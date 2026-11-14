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
        Pack("starter", "Starter", "Small, fast models installed during setup."),
        Pack("esrgan", "Real-ESRGAN", "Photo and anime models from the Real-ESRGAN family."),
        Pack("denoise", "Denoise", "Noise-aware models. The denoise slider picks the level."),
        Pack("anime4k", "Anime4K", "Shader-based anime upscaling, includes ACNet variants."),
    )

    val MODELS: List<ModelEntry> = listOf(
        // Starter: small, fast models installed during setup.
        ModelEntry(
            id = "esrgan-general-v3", name = "Real-ESRGAN general v3",
            desc = "Everyday photos, compact and fast", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 2.3, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "starter", license = "BSD-3-Clause", secPerMP = 3.0,
            required = true, engineTool = "realsr-ncnn", sourceDir = "models-RealeSR-general-v3",
            manifestId = "models-RealeSR-general-v3",
        ),
        ModelEntry(
            id = "waifu2x-anime", name = "waifu2x anime",
            desc = "Anime and illustration, noise-aware", kind = ImageType.Anime,
            scales = listOf(2), engine = "ncnn", sizeMB = 5.3, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "starter", license = "MIT", secPerMP = 4.0,
            denoise = true, required = true, engineTool = "waifu2x-ncnn",
            sourceDir = "models-upconv_7_anime_style_art_rgb", denoiseLevel = 1, manifestId = "models-upconv_7_anime_style_art_rgb",
        ),
        ModelEntry(
            id = "srmd-x2", name = "SRMD 2×",
            desc = "Photos with mild noise", kind = ImageType.Photo,
            scales = listOf(2), engine = "ncnn", sizeMB = 2.9, speed = "Very fast", badge = "Balanced",
            minTier = 0, pack = "starter", license = "MIT", secPerMP = 4.0,
            denoise = true, required = true, engineTool = "srmd-ncnn",
            sourceDir = "models-srmd", denoiseLevel = 0, manifestId = "models-srmd",
        ),
        ModelEntry(
            id = "anime4k-x2", name = "Anime4K 2×",
            desc = "Anime, fastest. ACNet included", kind = ImageType.Anime,
            scales = listOf(2), engine = "ncnn", sizeMB = 0.0, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "starter", license = "MIT", secPerMP = 1.6,
            required = true, engineTool = "Anime4k", sourceDir = "",
        ),

        // Real-ESRGAN family.
        ModelEntry(
            id = "esrgan-anime-sharp-lite", name = "AnimeSharp Lite x4",
            desc = "Anime and art, light and sharp", kind = ImageType.Anime,
            scales = listOf(4), engine = "ncnn", sizeMB = 19.2, speed = "Fast", badge = "Balanced",
            minTier = 0, pack = "esrgan", license = "To be verified", secPerMP = 8.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-AnimeSharpLite",
            manifestId = "models-ESRGAN-AnimeSharpLite",
        ),
        ModelEntry(
            id = "esrgan-anime-sharp", name = "AnimeSharp x4",
            desc = "Anime and art, high quality", kind = ImageType.Anime,
            scales = listOf(4), engine = "ncnn", sizeMB = 32.0, speed = "Medium", badge = "Best quality",
            minTier = 1, pack = "esrgan", license = "To be verified", secPerMP = 12.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-AnimeSharp",
            manifestId = "models-ESRGAN-AnimeSharp",
        ),
        ModelEntry(
            id = "esrgan-remacri", name = "Remacri x4",
            desc = "Detailed photos and textures", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 32.0, speed = "Medium", badge = "Best quality",
            minTier = 1, pack = "esrgan", license = "To be verified", secPerMP = 14.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-Remacri",
            manifestId = "models-ESRGAN-Remacri",
        ),
        ModelEntry(
            id = "esrgan-ultramix", name = "UltraMix Balanced x4",
            desc = "Balanced photos, good default", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 32.0, speed = "Medium", badge = "Balanced",
            minTier = 1, pack = "esrgan", license = "To be verified", secPerMP = 13.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-UltraMix_Balanced",
            manifestId = "models-ESRGAN-UltraMix_Balanced",
        ),
        ModelEntry(
            id = "esrgan-nomos8ksc", name = "Nomos8kSC x4",
            desc = "Detailed photos and textures", kind = ImageType.Photo,
            scales = listOf(4), engine = "ncnn", sizeMB = 32.0, speed = "Slow", badge = "Best quality",
            minTier = 2, pack = "esrgan", license = "To be verified", secPerMP = 18.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-Nomos8kSC",
            manifestId = "models-ESRGAN-Nomos8kSC",
        ),
        ModelEntry(
            id = "esrgan-sourcebook", name = "SourceBook x2",
            desc = "Scanned books and documents", kind = ImageType.Photo,
            scales = listOf(2), engine = "ncnn", sizeMB = 32.1, speed = "Medium", badge = "Balanced",
            minTier = 1, pack = "esrgan", license = "To be verified", secPerMP = 14.0,
            engineTool = "realsr-ncnn", sourceDir = "models-ESRGAN-SourceBook_v1",
            blocked = "Not in the mirror yet",
        ),
        ModelEntry(
            id = "mnn-moesr", name = "MoeSR illustration (MNN)",
            desc = "Japanese illustration style", kind = ImageType.Anime,
            scales = listOf(4), engine = "MNN", sizeMB = 32.1, speed = "Medium", badge = "Balanced",
            minTier = 1, pack = "esrgan", license = "To be verified", secPerMP = 14.0,
            engineTool = "mnnsr-ncnn", sourceDir = "models-MNNSR",
            entryFile = "MoeSR-ESRGAN-jp_Illustration-fix1-d-x4.mnn",
            blocked = "Not in the mirror yet",
        ),
        ModelEntry(
            id = "mnn-nomos8ksc", name = "Nomos8kSC (MNN)",
            desc = "Nomos8kSC on the MNN engine", kind = ImageType.Photo,
            scales = listOf(4), engine = "MNN", sizeMB = 32.0, speed = "Medium", badge = "Best quality",
            minTier = 2, pack = "esrgan", license = "To be verified", secPerMP = 16.0,
            engineTool = "mnnsr-ncnn", sourceDir = "models-MNNSR",
            entryFile = "ESRGAN-Nomos8kSC-x4.mnn",
            blocked = "Not in the mirror yet",
        ),

        // Noise-aware models: the denoise slider selects how hard they clean.
        ModelEntry(
            id = "waifu2x-cunet", name = "waifu2x CUnet",
            desc = "Anime, stronger noise removal", kind = ImageType.Anime,
            scales = listOf(2), engine = "ncnn", sizeMB = 13.3, speed = "Fast", badge = "Balanced",
            minTier = 0, pack = "denoise", license = "MIT", secPerMP = 6.0,
            denoise = true, engineTool = "waifu2x-ncnn", sourceDir = "models-cunet", denoiseLevel = 1, manifestId = "models-cunet",
        ),
        ModelEntry(
            id = "srmd-nf-x2", name = "SRMD 2× (no denoise)",
            desc = "Photos, keeps the grain", kind = ImageType.Photo,
            scales = listOf(2), engine = "ncnn", sizeMB = 2.9, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "denoise", license = "MIT", secPerMP = 4.0,
            engineTool = "srmd-ncnn", sourceDir = "models-srmd", manifestId = "models-srmd",
        ),

        // Anime4K family.
        ModelEntry(
            id = "anime4k-x4", name = "Anime4K 4×",
            desc = "Anime at 4×, fastest", kind = ImageType.Anime,
            scales = listOf(4), engine = "ncnn", sizeMB = 0.0, speed = "Very fast", badge = "Fast",
            minTier = 0, pack = "anime4k", license = "MIT", secPerMP = 2.2,
            engineTool = "Anime4k", sourceDir = "",
        ),
    )

    val STARTER_IDS: List<String> = MODELS.filter { it.required }.map { it.id }

    fun byId(id: String): ModelEntry = MODELS.firstOrNull { it.id == id } ?: MODELS.first()

    /** The catalog entry that a manifest id (an HF directory) installs into. */
    fun byManifestId(manifestId: String): ModelEntry? =
        MODELS.firstOrNull { it.manifestId == manifestId }

    /** Ids the planner can treat as installed without a download (Anime4K ships its shaders). */
    val BUNDLED_IDS: List<String> = MODELS.filter { it.manifestId == null && it.blocked == null }.map { it.id }

    fun packOf(id: String): Pack = PACKS.firstOrNull { it.id == id } ?: PACKS.first()

    /**
     * How this model behaves on a device of [tier]. `minTier` counts from 0,  so a model with
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
