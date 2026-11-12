package dev.realscale.pro.data.models

/** One downloadable model pack. Files land in filesDir/models/<dir>/ and are passed to NCNN via -m. */
data class ModelPack(
    val id: String, val name: String, val engine: String, val binary: String,
    val scale: Int, val dir: String, val files: List<String>, val blurb: String,
) {
    fun url(file: String) = "https://huggingface.co/tumuyan2/realsr-models/resolve/main/$dir/$file"
}

object ModelCatalog {
    val packs = listOf(
        ModelPack("realesrgan-anime", "Real-ESRGAN AnimeSharp", "Real-ESRGAN", "realsr-ncnn", 4,
            "models-ESRGAN-AnimeSharp", listOf("x4.bin", "x4.param"), "Crisp anime & illustration upscaling ×4."),
        ModelPack("realesrgan-remacri", "Real-ESRGAN Remacri", "Real-ESRGAN", "realsr-ncnn", 4,
            "models-ESRGAN-Remacri", listOf("x4.bin", "x4.param"), "Balanced photo restoration ×4."),
        ModelPack("realesrgan-df2k", "Real-ESRGAN DF2K", "Real-ESRGAN", "realsr-ncnn", 4,
            "models-DF2K", listOf("x4.bin", "x4.param"), "General-purpose photo upscaling ×4."),
        ModelPack("waifu2x-cunet", "Waifu2x cunet", "Waifu2x", "waifu2x-ncnn", 2,
            "models-cunet", listOf("noise0_scale2.0x_model.bin", "noise0_scale2.0x_model.param",
                "scale2.0x_model.bin", "scale2.0x_model.param"), "Anime denoising + ×2 enlargement."),
        ModelPack("waifu2x-photo", "Waifu2x Photo", "Waifu2x", "waifu2x-ncnn", 2,
            "models-upconv_7_photo", listOf("scale2.0x_model.bin", "scale2.0x_model.param"), "Photo ×2 enlargement."),
    )
    val classical = ModelPack("classical-lanczos", "Classical Lanczos ×2/×4", "Classical", "resize-ncnn", 2,
        "", emptyList(), "Offline interpolation — no download needed.")
    fun byId(id: String) = packs.firstOrNull { it.id == id }
}
