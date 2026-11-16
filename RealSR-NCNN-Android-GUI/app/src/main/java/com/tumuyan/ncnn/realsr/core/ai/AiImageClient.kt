package com.tumuyan.ncnn.realsr.core.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AiClientException(message: String, val status: Int = 0) : Exception(message)

data class AiRequest(
    val key: AiKeyRecord,
    val provider: AiProvider,
    val model: AiModel,
    val imageBytes: ByteArray,
    val mime: String,
    val prompt: String,
    val scale: Int,
    val format: String,
    val faceEnhance: Boolean,
    val strength: String,
)

data class AiRawResult(val bytes: ByteArray, val mime: String)

/**
 * The network half of AI Upscale Mode.
 *
 * Four dialects cover every provider in [AiCatalog]: OpenAI-style multipart edits, Gemini
 * `generateContent`, Stability's upscale endpoints and Replicate's predictions API. Plain
 * `HttpURLConnection` is used on purpose — the app ships no HTTP client and none of these calls
 * needs one.
 */
object AiImageClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 300_000
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(request: AiRequest, onStage: (String) -> Unit = {}): AiRawResult =
        withContext(Dispatchers.IO) {
            try {
                when (request.provider.family) {
                    AiFamily.OPENAI, AiFamily.CUSTOM_OPENAI -> {
                        onStage("Uploading and reconstructing…")
                        openAiEdit(request)
                    }

                    AiFamily.GEMINI -> {
                        onStage("Uploading and reconstructing…")
                        geminiGenerate(request)
                    }

                    AiFamily.STABILITY -> {
                        onStage("Upscaling on Stability…")
                        stabilityUpscale(request)
                    }

                    AiFamily.REPLICATE -> replicateRun(request, onStage)
                }
            } catch (e: AiClientException) {
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw AiClientException(
                    "AI upscale failed: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }

    /* ------------------------------------------------------------------ OpenAI / custom */

    private fun openAiEdit(request: AiRequest): AiRawResult {
        val base = request.key.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?: request.provider.baseUrl
        val modelId = request.key.modelId?.trim()?.takeIf { it.isNotEmpty() } ?: request.model.requestId
        val boundary = "----RealScalePro${System.currentTimeMillis()}"
        val fields = linkedMapOf(
            "model" to modelId,
            "prompt" to request.prompt,
            "n" to "1",
        )
        val body = multipart(
            boundary = boundary,
            fields = fields,
            fileField = "image",
            fileName = "input.jpg",
            fileType = request.mime,
            fileBytes = request.imageBytes,
        )
        val response = http(
            url = "$base/images/edits",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer ${request.key.key}",
                "Content-Type" to "multipart/form-data; boundary=$boundary",
            ),
            body = body,
        )
        if (response.code !in 200..299) throw failure(response.code, response.text)
        val data = parse(response.text)["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AiClientException("The provider returned no image.")
        val b64 = data.str("b64_json")
        if (!b64.isNullOrBlank()) {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            return AiRawResult(bytes, AiImagePrep.sniffMime(bytes))
        }
        val url = data.str("url")
            ?: throw AiClientException("The provider returned no image data.")
        val downloaded = http(url, "GET", emptyMap(), null)
        if (downloaded.code !in 200..299) throw AiClientException("The result could not be downloaded.")
        return AiRawResult(downloaded.bytes, AiImagePrep.sniffMime(downloaded.bytes))
    }

    /* ------------------------------------------------------------------ Gemini */

    private fun geminiGenerate(request: AiRequest): AiRawResult {
        val b64 = Base64.encodeToString(request.imageBytes, Base64.NO_WRAP)
        val payload = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", request.prompt) })
                            add(
                                buildJsonObject {
                                    putJsonObject("inline_data") {
                                        put("mime_type", request.mime)
                                        put("data", b64)
                                    }
                                },
                            )
                        }
                    },
                )
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(JsonPrimitive("IMAGE")) }
            }
        }
        val response = http(
            url = "${request.provider.baseUrl}/models/${request.model.requestId}:generateContent",
            method = "POST",
            headers = mapOf(
                "x-goog-api-key" to request.key.key,
                "Content-Type" to "application/json",
            ),
            body = payload.toString().toByteArray(Charsets.UTF_8),
        )
        if (response.code !in 200..299) throw failure(response.code, response.text)
        val root = parse(response.text)
        val parts = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
        parts?.forEach { part ->
            val obj = part.jsonObject
            val inline = obj["inlineData"]?.jsonObject ?: obj["inline_data"]?.jsonObject
            val data = inline?.str("data")
            if (!data.isNullOrBlank()) {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                return AiRawResult(bytes, AiImagePrep.sniffMime(bytes))
            }
        }
        val blocked = root["promptFeedback"]?.jsonObject?.str("blockReason")
        throw AiClientException(
            if (blocked != null) "Gemini blocked this image ($blocked)." else "Gemini returned no image.",
        )
    }

    /* ------------------------------------------------------------------ Stability */

    private fun stabilityUpscale(request: AiRequest): AiRawResult {
        val boundary = "----RealScalePro${System.currentTimeMillis()}"
        val fields = linkedMapOf("output_format" to request.format.lowercase())
        if (request.model.supportsStrength) {
            // `creativity` glues the result to the original at the low end.
            fields["creativity"] = if (request.strength == "faithful") "0.1" else "0.3"
        }
        val body = multipart(
            boundary = boundary,
            fields = fields,
            fileField = "image",
            fileName = "input.jpg",
            fileType = request.mime,
            fileBytes = request.imageBytes,
        )
        val response = http(
            url = "${request.provider.baseUrl}/${request.model.requestId}",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer ${request.key.key}",
                "Accept" to "image/*",
                "Content-Type" to "multipart/form-data; boundary=$boundary",
            ),
            body = body,
        )
        if (response.code !in 200..299) throw failure(response.code, response.text)
        if (response.bytes.isEmpty()) throw AiClientException("Stability returned an empty image.")
        return AiRawResult(response.bytes, AiImagePrep.sniffMime(response.bytes))
    }

    /* ------------------------------------------------------------------ Replicate */

    private fun replicateRun(request: AiRequest, onStage: (String) -> Unit): AiRawResult {
        val b64 = Base64.encodeToString(request.imageBytes, Base64.NO_WRAP)
        val payload = buildJsonObject {
            putJsonObject("input") {
                put("image", "data:${request.mime};base64,$b64")
                put("scale", request.scale)
                put("face_enhance", request.faceEnhance)
            }
        }
        val created = http(
            url = "${request.provider.baseUrl}/models/${request.model.requestId}/predictions",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer ${request.key.key}",
                "Content-Type" to "application/json",
                "Prefer" to "wait=60",
            ),
            body = payload.toString().toByteArray(Charsets.UTF_8),
        )
        if (created.code !in 200..299) throw failure(created.code, created.text)
        var prediction = parse(created.text)
        var status = prediction.str("status").orEmpty()
        val getUrl = prediction["urls"]?.jsonObject?.str("get")
        var waited = 0
        while (status != "succeeded" && status != "failed" && status != "canceled") {
            if (getUrl == null || waited > 240_000) {
                throw AiClientException("Replicate is still running this job. Try again in a minute.")
            }
            onStage("Reconstructing on Replicate…")
            delay(2_500)
            waited += 2_500
            val polled = http(
                url = getUrl,
                method = "GET",
                headers = mapOf("Authorization" to "Bearer ${request.key.key}"),
                body = null,
            )
            if (polled.code !in 200..299) throw failure(polled.code, polled.text)
            prediction = parse(polled.text)
            status = prediction.str("status").orEmpty()
        }
        if (status != "succeeded") {
            throw AiClientException(prediction.str("error") ?: "Replicate failed this job.")
        }
        val output = prediction["output"]
        val url = when (output) {
            null, is JsonNull -> null
            is JsonArray -> output.firstOrNull()?.str()
            else -> output.str()
        } ?: throw AiClientException("Replicate returned no image.")
        onStage("Downloading the result…")
        val downloaded = http(url, "GET", emptyMap(), null)
        if (downloaded.code !in 200..299) throw AiClientException("The result could not be downloaded.")
        return AiRawResult(downloaded.bytes, AiImagePrep.sniffMime(downloaded.bytes))
    }

    /* ------------------------------------------------------------------ plumbing */

    private data class HttpResult(val code: Int, val bytes: ByteArray) {
        val text: String get() = bytes.toString(Charsets.UTF_8)
    }

    private fun http(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): HttpResult {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }
            .getOrElse { throw AiClientException("That endpoint could not be reached.") }
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return HttpResult(code, bytes)
        } catch (e: AiClientException) {
            throw e
        } catch (e: Exception) {
            throw AiClientException(
                "Network error: ${e.message ?: e.javaClass.simpleName}. Check the connection and the key.",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun multipart(
        boundary: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        fileType: String,
        fileBytes: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = DataOutputStream(out)
        fields.forEach { (key, value) ->
            writer.writeBytes("--$boundary\r\n")
            writer.writeBytes("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
            writer.write(value.toByteArray(Charsets.UTF_8))
            writer.writeBytes("\r\n")
        }
        writer.writeBytes("--$boundary\r\n")
        writer.writeBytes("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n")
        writer.writeBytes("Content-Type: $fileType\r\n\r\n")
        writer.write(fileBytes)
        writer.writeBytes("\r\n--$boundary--\r\n")
        writer.flush()
        return out.toByteArray()
    }

    private fun parse(text: String): JsonObject =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw AiClientException("The provider sent an unreadable answer.")
        }

    /** Turns a provider error body into one sentence the user can act on. */
    private fun failure(code: Int, body: String): AiClientException {
        val detail = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            when (val error = root["error"]) {
                null -> root.str("message")
                is JsonObject -> error.str("message")
                else -> error.str()
            }
        }.getOrNull()
        val friendly = when {
            code == 401 || code == 403 -> "That API key was rejected. Check it in AI settings."
            code == 402 -> "This account is out of credits."
            code == 429 -> "Rate limit or quota reached. Try again later or use another key."
            code in 500..599 -> "The provider had a problem ($code). Try again."
            !detail.isNullOrBlank() -> detail
            else -> "The provider refused the request ($code)."
        }
        return AiClientException(friendly, code)
    }
}

/* ------------------------------------------------------------------ JSON helpers */

/** `JsonPrimitive.content` throws on a JSON null, and most of these fields are optional. */
internal fun JsonElement.str(): String? = (this as? JsonPrimitive)?.let { if (it is JsonNull) null else it.content }

internal fun JsonObject.str(key: String): String? = this[key]?.str()
