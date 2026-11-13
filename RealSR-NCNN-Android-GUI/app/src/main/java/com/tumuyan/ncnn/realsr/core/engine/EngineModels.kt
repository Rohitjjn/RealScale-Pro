package com.tumuyan.ncnn.realsr.core.engine

import android.net.Uri
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity

enum class ExecutionBackend {
    AUTO_GPU,
    CPU
}

enum class OutputFormat(val extension: String, val cliFormat: String) {
    PNG("png", "png"),
    WEBP("webp", "webp"),
    JPG("jpg", "jpg")
}

data class EnginePreferences(
    val backend: ExecutionBackend = ExecutionBackend.AUTO_GPU,
    val tileSize: Int = 0,
    val threadCount: String = "",
    val outputFormat: OutputFormat = OutputFormat.PNG,
    val outputTreeUri: String? = null,
    val hideProgramOutput: Boolean = false
)

data class ModelExecutionSpec(
    val modelId: String,
    val displayName: String,
    val engineTool: String,
    val modelPath: String,
    val scale: Int,
    val denoiseLevel: Int? = null
)

data class UpscaleRequest(
    val inputUris: List<Uri>,
    val preset: UpscalePreset,
    val scale: Int = preset.defaultScale,
    val modelSpec: ModelExecutionSpec? = null
)

data class UpscaleResult(
    val creations: List<RecentCreationEntity>,
    val totalDurationMs: Long
) {
    val primaryCreation: RecentCreationEntity
        get() = creations.first()
}

sealed interface EngineState {
    data object Idle : EngineState

    data class Preparing(
        val label: String
    ) : EngineState

    data class Running(
        val jobId: String,
        val presetTitle: String,
        val progressFraction: Float,
        val currentFileIndex: Int,
        val totalFiles: Int,
        val currentFileName: String,
        val etaMillis: Long?,
        val previewUri: String?,
        val latestStatusLine: String
    ) : EngineState

    data class Completed(
        val result: UpscaleResult
    ) : EngineState

    data object Cancelled : EngineState

    data class Failed(
        val userMessage: String,
        val technicalOutput: String
    ) : EngineState
}
