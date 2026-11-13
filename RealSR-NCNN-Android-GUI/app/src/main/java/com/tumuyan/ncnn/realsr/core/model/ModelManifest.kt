package com.tumuyan.ncnn.realsr.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ModelManifest(
    val manifestVersion: Int,
    val updatedAt: String,
    val sourceRepo: String,
    val mirrorInfo: String? = null,
    val models: List<ModelCatalogItem>
)

@Serializable
data class ModelCatalogItem(
    val id: String,
    val displayName: String,
    val description: String,
    val category: String,
    val recommendedForPreset: String? = null,
    val engineTool: String,
    val entryFile: String? = null,
    val defaultScale: Int,
    val supportedScales: List<Int>,
    val denoiseLevel: Int? = null,
    val totalBytes: Long,
    val files: List<ModelFileSpec>
)

@Serializable
data class ModelFileSpec(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String
)

object ModelManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val sha256Regex = Regex("^[0-9a-fA-F]{64}$")

    fun parseAndValidate(rawJson: String): ModelManifest {
        val manifest = json.decodeFromString(ModelManifest.serializer(), rawJson)
        require(manifest.manifestVersion >= 1) {
            "Invalid manifestVersion: ${manifest.manifestVersion}"
        }
        require(manifest.models.isNotEmpty()) {
            "Model manifest must contain at least one model"
        }
        for (model in manifest.models) {
            require(model.id.isNotBlank() && !model.id.contains("..") && !model.id.contains('/')) {
                "Invalid model id: ${model.id}"
            }
            require(model.files.isNotEmpty()) {
                "Model ${model.id} has no files"
            }
            val sumBytes = model.files.sumOf { it.sizeBytes }
            require(model.totalBytes == sumBytes && sumBytes > 0L) {
                "Model ${model.id} totalBytes (${model.totalBytes}) != sum of file sizes ($sumBytes)"
            }
            for (file in model.files) {
                require(file.fileName.isNotBlank() && !file.fileName.contains("..") && !file.fileName.contains('/')) {
                    "Invalid fileName '${file.fileName}' in model ${model.id}"
                }
                require(file.url.startsWith("https://")) {
                    "Insecure or invalid model URL '${file.url}' in model ${model.id}"
                }
                require(sha256Regex.matches(file.sha256)) {
                    "Invalid SHA-256 '${file.sha256}' for ${model.id}/${file.fileName}"
                }
            }
        }
        return manifest
    }
}
