package com.tumuyan.ncnn.realsr.core.engine

enum class PresetId {
    INSTANT,
    PHOTO,
    ANIME,
    MAXIMUM
}

data class UpscalePreset(
    val id: PresetId,
    val title: String,
    val subtitle: String,
    val requiresDownload: Boolean,
    val engineTool: String,
    val defaultModelId: String,
    val approxDownloadMb: Double,
    val defaultScale: Int,
    val supportedScales: List<Int>,
    val denoiseLevel: Int? = null
) {
    companion object {
        val INSTANT = UpscalePreset(
            id = PresetId.INSTANT,
            title = "Instant",
            subtitle = "Crisp Lanczos upscale · Ready immediately offline",
            requiresDownload = false,
            engineTool = "resize-ncnn",
            defaultModelId = "builtin-resize-ncnn",
            approxDownloadMb = 0.0,
            defaultScale = 2,
            supportedScales = listOf(2, 4)
        )

        val PHOTO = UpscalePreset(
            id = PresetId.PHOTO,
            title = "Photo",
            subtitle = "Natural texture & clarity for everyday photos",
            requiresDownload = true,
            engineTool = "realsr-ncnn",
            defaultModelId = "models-RealeSR-general-v3",
            approxDownloadMb = 2.3,
            defaultScale = 4,
            supportedScales = listOf(4)
        )

        val ANIME = UpscalePreset(
            id = PresetId.ANIME,
            title = "Anime & Art",
            subtitle = "Clean ink lines & smooth color for illustrations",
            requiresDownload = true,
            engineTool = "waifu2x-ncnn",
            defaultModelId = "models-upconv_7_anime_style_art_rgb",
            approxDownloadMb = 5.3,
            defaultScale = 2,
            supportedScales = listOf(2, 4),
            denoiseLevel = 2
        )

        val MAXIMUM = UpscalePreset(
            id = PresetId.MAXIMUM,
            title = "Maximum",
            subtitle = "Heavy generative detail recovery for Challenging shots",
            requiresDownload = true,
            engineTool = "realsr-ncnn",
            defaultModelId = "models-DF2K",
            approxDownloadMb = 32.0,
            defaultScale = 4,
            supportedScales = listOf(4)
        )

        val ALL: List<UpscalePreset> = listOf(INSTANT, PHOTO, ANIME, MAXIMUM)

        fun fromId(id: PresetId): UpscalePreset = ALL.first { it.id == id }
    }
}
