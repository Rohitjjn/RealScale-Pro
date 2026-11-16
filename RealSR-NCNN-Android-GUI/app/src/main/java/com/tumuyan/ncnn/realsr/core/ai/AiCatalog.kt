package com.tumuyan.ncnn.realsr.core.ai

/**
 * Bring-your-own-model catalog for **AI Upscale Mode** (Round 5).
 *
 * Everything here is metadata: which provider endpoints exist, which image models can take a whole
 * photo plus a prompt and hand back a reconstructed image, and what each one costs in time. The
 * network work lives in [AiImageClient]; the keys never leave the device except as an
 * `Authorization` header for the provider the user picked.
 */
enum class AiFamily {
    /** `POST {base}/images/edits`, multipart, `data[0].b64_json` back. */
    OPENAI,

    /** `POST .../models/{model}:generateContent` with an inline image part. */
    GEMINI,

    /** `POST {base}/stable-image/{requestId}`, multipart, binary image back. */
    STABILITY,

    /** `POST api.replicate.com/v1/models/{requestId}/predictions` (requestId = `owner/name`). */
    REPLICATE,

    /** Any endpoint that speaks the OpenAI images-edit dialect (gateways, self-hosted). */
    CUSTOM_OPENAI,
}

data class AiProvider(
    val id: String,
    val name: String,
    val family: AiFamily,
    /** Shown under the key field, e.g. `sk-…`. */
    val keyHint: String,
    /** Cheap local sanity check; never sent anywhere. */
    val keyPrefix: String?,
    val docsUrl: String,
    val baseUrl: String,
    val note: String,
    val allowsCustomBaseUrl: Boolean = false,
    val allowsCustomModel: Boolean = false,
)

data class AiModel(
    /** Stable catalog id, `provider:model`. */
    val id: String,
    val providerId: String,
    val label: String,
    val desc: String,
    /** The factors the user can ask for; the client resizes to the exact size afterwards. */
    val scales: List<Int>,
    /** Above this the app downsizes before uploading (cost and provider limits). */
    val maxInputMP: Int,
    val approxSec: Int,
    val badge: String,
    /** The string the provider's API expects (model name, or a path segment for Stability). */
    val requestId: String,
    val supportsFaceEnhance: Boolean = false,
    val supportsStrength: Boolean = false,
    val promptDriven: Boolean = true,
)

object AiCatalog {

    /**
     * The system prompt every AI upscale is built on: reconstruction first, invention never. The
     * Advanced level can append to it, not replace the "keep the composition" instruction.
     */
    const val DEFAULT_PROMPT =
        "Reconstruct and upscale this image. Restore fine detail, texture and clean edges; remove " +
            "JPEG artifacts, noise, blur and banding. Keep the exact same composition, framing, " +
            "perspective, colours and subject identity — do not add, remove or move anything. " +
            "Return a sharp, natural, high-resolution version of the same picture."

    const val PROMPT_TAIL =
        " Do not invent new objects, do not change faces, do not add text or watermarks."

    val PROVIDERS: List<AiProvider> = listOf(
        AiProvider(
            id = "gemini",
            name = "Google Gemini",
            family = AiFamily.GEMINI,
            keyHint = "AIza…",
            keyPrefix = "AIza",
            docsUrl = "https://aistudio.google.com/app/apikey",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            note = "Free tier available in AI Studio. Image output is included in the Flash Image model.",
        ),
        AiProvider(
            id = "openai",
            name = "OpenAI",
            family = AiFamily.OPENAI,
            keyHint = "sk-…",
            keyPrefix = "sk-",
            docsUrl = "https://platform.openai.com/api-keys",
            baseUrl = "https://api.openai.com/v1",
            note = "Uses the image edits endpoint, so the result is a reconstruction of your photo.",
        ),
        AiProvider(
            id = "stability",
            name = "Stability AI",
            family = AiFamily.STABILITY,
            keyHint = "sk-…",
            keyPrefix = "sk-",
            docsUrl = "https://platform.stability.ai/account/keys",
            baseUrl = "https://api.stability.ai/v2beta",
            note = "Dedicated upscale endpoints: 4× output, no prompt needed for Fast and Conservative.",
        ),
        AiProvider(
            id = "replicate",
            name = "Replicate",
            family = AiFamily.REPLICATE,
            keyHint = "r8_…",
            keyPrefix = "r8_",
            docsUrl = "https://replicate.com/account/api-tokens",
            baseUrl = "https://api.replicate.com/v1",
            note = "Runs Real-ESRGAN in the cloud; face enhancement and seed are supported.",
        ),
        AiProvider(
            id = "custom",
            name = "Custom endpoint",
            family = AiFamily.CUSTOM_OPENAI,
            keyHint = "your gateway key",
            keyPrefix = null,
            docsUrl = "",
            baseUrl = "https://",
            note = "Any server that accepts OpenAI-style multipart image edits. You provide the base URL and model id.",
            allowsCustomBaseUrl = true,
            allowsCustomModel = true,
        ),
    )

    val MODELS: List<AiModel> = listOf(
        AiModel(
            id = "gemini:flash-image",
            providerId = "gemini",
            label = "Gemini 2.5 Flash Image",
            desc = "Nano Banana. Strong at photo reconstruction and detail recovery, fast.",
            scales = listOf(2, 4),
            maxInputMP = 4,
            approxSec = 18,
            badge = "Balanced",
            requestId = "gemini-2.5-flash-image",
        ),
        AiModel(
            id = "openai:gpt-image-1",
            providerId = "openai",
            label = "GPT Image 1",
            desc = "Instruction-following image model. Very clean results, slower and pricier.",
            scales = listOf(2, 4),
            maxInputMP = 4,
            approxSec = 35,
            badge = "Best quality",
            requestId = "gpt-image-1",
        ),
        AiModel(
            id = "stability:upscale-fast",
            providerId = "stability",
            label = "Stability Fast Upscale",
            desc = "Purpose-built 4× upscaler. No prompt, keeps the photo exactly as it is.",
            scales = listOf(4),
            maxInputMP = 6,
            approxSec = 12,
            badge = "Fast",
            requestId = "stable-image/upscale/fast",
            promptDriven = false,
        ),
        AiModel(
            id = "stability:upscale-conservative",
            providerId = "stability",
            label = "Stability Conservative",
            desc = "4× upscale that keeps colours and grain closest to the original.",
            scales = listOf(4),
            maxInputMP = 6,
            approxSec = 20,
            badge = "Faithful",
            requestId = "stable-image/upscale/conservative",
            promptDriven = false,
            supportsStrength = true,
        ),
        AiModel(
            id = "replicate:real-esrgan",
            providerId = "replicate",
            label = "Real-ESRGAN (cloud)",
            desc = "The familiar 2×/4× Real-ESRGAN, run on Replicate. Optional face enhancement.",
            scales = listOf(2, 4),
            maxInputMP = 8,
            approxSec = 30,
            badge = "Sharp",
            requestId = "nightmareai/real-esrgan",
            supportsFaceEnhance = true,
            promptDriven = false,
        ),
    )

    fun provider(id: String): AiProvider = PROVIDERS.firstOrNull { it.id == id } ?: PROVIDERS.first()

    fun model(id: String): AiModel? = MODELS.firstOrNull { it.id == id }

    fun modelsFor(providerId: String): List<AiModel> = MODELS.filter { it.providerId == providerId }

    /** Every model reachable with the providers the user actually saved a key for. */
    fun modelsForProviders(providerIds: Set<String>): List<AiModel> =
        MODELS.filter { it.providerId in providerIds }

    /** Human label for a model id, including ids that are no longer in [MODELS]. */
    fun labelFor(modelId: String): String = model(modelId)?.label ?: modelId.substringAfter(':')

    fun promptFor(custom: String): String {
        val trimmed = custom.trim()
        return if (trimmed.isEmpty()) DEFAULT_PROMPT else trimmed + PROMPT_TAIL
    }
}
