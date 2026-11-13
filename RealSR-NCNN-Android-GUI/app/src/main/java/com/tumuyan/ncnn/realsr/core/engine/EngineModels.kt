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

enum class ExternalOpenMode {
    QUICK_SHEET,
    FULL_APP
}

enum class ProcessingStage(val humanLabel: String) {
    PREPARING("Preparing"),
    ENHANCING("Enhancing"),
    SAVING("Saving")
}

enum class RecoveryAction(val label: String) {
    RETRY_ON_CPU("Retry on CPU"),
    SMALLER_TILE_SIZE("Smaller tile size (64 px)"),
    TRY_INSTANT("Try Instant")
}

data class EnginePreferences(
    val backend: ExecutionBackend = ExecutionBackend.AUTO_GPU,
    val tileSize: Int = 0,
    val threadCount: String = "",
    val outputFormat: OutputFormat = OutputFormat.PNG,
    val outputTreeUri: String? = null,
    val hideProgramOutput: Boolean = false,
    val externalOpenMode: ExternalOpenMode = ExternalOpenMode.QUICK_SHEET,
    val lastUsedPresetId: PresetId = PresetId.INSTANT,
    val lastUsedScale: Int = 2,
    val defaultPresetId: PresetId? = null,
    val keepScreenOnDuringEnhance: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val wifiOnlyDownloads: Boolean = false
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
    val modelSpec: ModelExecutionSpec? = null,
    val autoSaveToGallery: Boolean = true,
    val notifyOnCompletion: Boolean = false
)

data class QueuedJobItem(
    val id: String,
    val sourceUri: String,
    val fileName: String,
    val presetTitle: String,
    val scale: Int,
    val isRunning: Boolean,
    val positionInBatch: Int,
    val totalInBatch: Int
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
        val latestStatusLine: String,
        val stage: ProcessingStage = ProcessingStage.ENHANCING,
        val hasDeterminateProgress: Boolean = false,
        val elapsedMillis: Long = 0L,
        val currentItemId: String = jobId
    ) : EngineState

    data class Completed(
        val result: UpscaleResult
    ) : EngineState

    data object Cancelled : EngineState

    data class Failed(
        val userMessage: String,
        val technicalOutput: String,
        val recoveryActions: List<RecoveryAction> = emptyList(),
        val failedRequest: UpscaleRequest? = null
    ) : EngineState
}
