package com.tumuyan.ncnn.realsr.core.engine

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Single Kotlin boundary between the UI/ViewModels and the native upscaling runtime.
 * Exposes reactive [StateFlow]s only — no callbacks or EventBus.
 */
interface UpscaleEngine {
    val state: StateFlow<EngineState>
    val consoleLog: StateFlow<String>
    val preferences: StateFlow<EnginePreferences>
    val queue: StateFlow<List<QueuedJobItem>>
        get() = MutableStateFlow(emptyList())

    suspend fun ensureInitialized(forceReset: Boolean = false): Result<File>

    fun updatePreferences(transform: (EnginePreferences) -> EnginePreferences)

    fun startUpscale(request: UpscaleRequest)

    fun startDirectoryUpscale(directoryUri: Uri, preset: UpscalePreset, scale: Int, modelSpec: ModelExecutionSpec? = null)

    fun runAdvancedCommand(rawCommand: String)

    fun retryWithRecovery(action: RecoveryAction): Boolean = false

    fun cancelQueuedItem(itemId: String) {}

    fun requestCompletionNotificationForActiveSession() {}

    fun cancel()

    fun acknowledgeTerminalState()
}
