package com.tumuyan.ncnn.realsr.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.EnginePreferences
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.ExecutionBackend
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.PresetCommandResolver
import com.tumuyan.ncnn.realsr.core.engine.PresetId
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.engine.UpscaleRequest
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class StudioSelectionState(
    val selectedUris: List<Uri> = emptyList(),
    val selectedPreset: UpscalePreset = UpscalePreset.INSTANT,
    val selectedScale: Int = UpscalePreset.INSTANT.defaultScale,
    val selectedModelOverrideId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val resultCreations: List<RecentCreationEntity> = emptyList(),
    val selectedResultIndex: Int = 0,
    val bannerMessage: String? = null
)

data class RealScaleUiState(
    val selection: StudioSelectionState = StudioSelectionState(),
    val engineState: EngineState = EngineState.Idle,
    val enginePreferences: EnginePreferences = EnginePreferences(),
    val consoleLog: String = "",
    val recentCreations: List<RecentCreationEntity> = emptyList(),
    val catalogModels: List<CatalogModelState> = emptyList(),
    val totalInstalledBytes: Long = 0L
) {
    val activeDownloadsCount: Int
        get() = catalogModels.count {
            it.status == ModelInstallStatus.DOWNLOADING ||
                it.status == ModelInstallStatus.QUEUED ||
                it.status == ModelInstallStatus.VERIFYING
        }

    fun isPresetReadyOffline(preset: UpscalePreset): Boolean {
        if (!preset.requiresDownload) return true
        val overrideId = selection.selectedModelOverrideId
        if (overrideId != null) {
            val match = catalogModels.firstOrNull { it.catalog.id == overrideId }
            if (match?.isInstalled == true) return true
        }
        return catalogModels.any {
            (it.catalog.id == preset.defaultModelId || it.catalog.category == preset.id.name) && it.isInstalled
        }
    }

    fun defaultCatalogStateFor(preset: UpscalePreset): CatalogModelState? =
        catalogModels.firstOrNull { it.catalog.id == preset.defaultModelId }
}

class RealScaleViewModel(
    application: Application,
    private val container: RealScaleAppContainer
) : AndroidViewModel(application) {

    private val uiPrefs = application.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
    private val selectionFlow = MutableStateFlow(
        StudioSelectionState(themeMode = loadThemeMode())
    )

    private val coreFlows = combine(
        container.engine.state,
        container.engine.preferences,
        container.engine.consoleLog
    ) { engineState, prefs, log ->
        Triple(engineState, prefs, log)
    }

    private val dataFlows = combine(
        container.database.recentCreationDao().observeRecent(24),
        container.modelRepository.catalogModels,
        container.modelRepository.totalInstalledBytes
    ) { recent, models, installedBytes ->
        Triple(recent, models, installedBytes)
    }

    val uiState: StateFlow<RealScaleUiState> = combine(
        selectionFlow,
        coreFlows,
        dataFlows
    ) { sel, (engineState, prefs, log), (recent, models, installedBytes) ->
        val updatedSelection = if (engineState is EngineState.Completed && sel.resultCreations != engineState.result.creations) {
            sel.copy(
                resultCreations = engineState.result.creations,
                selectedResultIndex = 0
            )
        } else {
            sel
        }
        RealScaleUiState(
            selection = updatedSelection,
            engineState = engineState,
            enginePreferences = prefs,
            consoleLog = log,
            recentCreations = recent,
            catalogModels = models,
            totalInstalledBytes = installedBytes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RealScaleUiState(selection = selectionFlow.value)
    )

    init {
        // Warm up native engine extraction on background thread so Instant upscale starts immediately
        viewModelScope.launch {
            container.engine.ensureInitialized(forceReset = false)
        }
        viewModelScope.launch {
            container.engine.state.collect { st ->
                if (st is EngineState.Completed) {
                    selectionFlow.update {
                        it.copy(
                            resultCreations = st.result.creations,
                            selectedResultIndex = 0
                        )
                    }
                }
            }
        }
    }

    fun onPhotosSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        selectionFlow.update { it.copy(selectedUris = uris, bannerMessage = null) }
    }

    fun clearSelectedPhotos() {
        selectionFlow.update { it.copy(selectedUris = emptyList()) }
    }

    fun selectPreset(preset: UpscalePreset) {
        selectionFlow.update { prev ->
            val nextScale = if (prev.selectedScale in preset.supportedScales) {
                prev.selectedScale
            } else {
                preset.defaultScale
            }
            prev.copy(
                selectedPreset = preset,
                selectedScale = nextScale,
                selectedModelOverrideId = null
            )
        }
    }

    fun selectScale(scale: Int) {
        selectionFlow.update { it.copy(selectedScale = scale) }
    }

    fun selectModelOverride(modelId: String?) {
        selectionFlow.update { prev ->
            val catalogItem = uiState.value.catalogModels.firstOrNull { it.catalog.id == modelId }?.catalog
            val nextScale = if (catalogItem != null && prev.selectedScale !in catalogItem.supportedScales) {
                catalogItem.defaultScale
            } else {
                prev.selectedScale
            }
            prev.copy(selectedModelOverrideId = modelId, selectedScale = nextScale)
        }
    }

    fun startPrimaryUpscale(): Boolean {
        val current = uiState.value
        val uris = current.selection.selectedUris
        if (uris.isEmpty()) return false

        val preset = current.selection.selectedPreset
        val spec = container.modelRepository.resolveExecutionSpec(
            preset = preset,
            scale = current.selection.selectedScale,
            catalogStates = current.catalogModels,
            overrideModelId = current.selection.selectedModelOverrideId
        )
        if (preset.requiresDownload && spec == null) {
            return false
        }

        val effectiveScale = spec?.scale ?: current.selection.selectedScale
        container.engine.startUpscale(
            UpscaleRequest(
                inputUris = uris,
                preset = preset,
                scale = effectiveScale,
                modelSpec = if (preset.id == PresetId.INSTANT && current.selection.selectedModelOverrideId == null) {
                    null
                } else {
                    spec
                }
            )
        )
        return true
    }

    fun cancelProcessing() {
        container.engine.cancel()
    }

    fun acknowledgeEngineState() {
        container.engine.acknowledgeTerminalState()
    }

    fun openRecentCreation(entity: RecentCreationEntity) {
        selectionFlow.update {
            it.copy(
                resultCreations = listOf(entity),
                selectedResultIndex = 0
            )
        }
    }

    fun selectResultIndex(index: Int) {
        selectionFlow.update { prev ->
            prev.copy(selectedResultIndex = index.coerceIn(0, (prev.resultCreations.size - 1).coerceAtLeast(0)))
        }
    }

    fun tryAnotherPresetFromResult(creation: RecentCreationEntity) {
        val beforeFile = File(creation.beforeImagePath)
        val uriToLoad = if (beforeFile.isFile) {
            Uri.fromFile(beforeFile)
        } else {
            Uri.parse(creation.sourceUri)
        }
        container.engine.acknowledgeTerminalState()
        selectionFlow.update { prev ->
            prev.copy(selectedUris = listOf(uriToLoad))
        }
    }

    fun saveResultToGallery(creation: RecentCreationEntity) {
        viewModelScope.launch {
            val file = File(creation.outputPath)
            if (!file.isFile) {
                postBanner("Output file is no longer available.")
                return@launch
            }
            val res = MediaExportHelper.saveToGalleryOrFolder(
                context = getApplication(),
                sourceFile = file,
                customTreeUri = uiState.value.enginePreferences.outputTreeUri
            )
            res.fold(
                onSuccess = { savedUri ->
                    container.database.recentCreationDao().updateSavedUri(creation.id, savedUri.toString())
                    selectionFlow.update { prev ->
                        prev.copy(
                            resultCreations = prev.resultCreations.map { item ->
                                if (item.id == creation.id) item.copy(savedUri = savedUri.toString()) else item
                            },
                            bannerMessage = "Saved to Gallery · Pictures/RealScale-Pro"
                        )
                    }
                },
                onFailure = { err ->
                    postBanner(err.message ?: "Could not save photo.")
                }
            )
        }
    }

    fun shareResult(context: Context, creation: RecentCreationEntity) {
        val file = File(creation.outputPath)
        if (!file.isFile) {
            postBanner("Output file is no longer available.")
            return
        }
        runCatching {
            MediaExportHelper.shareImage(context, file)
        }.onFailure { err ->
            postBanner(err.message ?: "Could not open share sheet.")
        }
    }

    fun deleteRecentCreation(creation: RecentCreationEntity) {
        viewModelScope.launch {
            File(creation.beforeImagePath).delete()
            File(creation.outputPath).delete()
            container.database.recentCreationDao().deleteById(creation.id)
        }
    }

    // Model Hub actions
    fun downloadOrResumeModel(modelId: String) {
        viewModelScope.launch {
            container.modelRepository.startOrResumeDownload(modelId)
        }
    }

    fun pauseModelDownload(modelId: String) {
        viewModelScope.launch {
            container.modelRepository.pauseDownload(modelId)
        }
    }

    fun cancelModelDownload(modelId: String) {
        viewModelScope.launch {
            container.modelRepository.cancelDownload(modelId)
        }
    }

    fun deleteInstalledModel(modelId: String) {
        viewModelScope.launch {
            container.modelRepository.deleteInstalledModel(modelId)
            selectionFlow.update { prev ->
                if (prev.selectedModelOverrideId == modelId) prev.copy(selectedModelOverrideId = null) else prev
            }
        }
    }

    // Settings actions
    fun setThemeMode(mode: ThemeMode) {
        uiPrefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        selectionFlow.update { it.copy(themeMode = mode) }
    }

    fun setExecutionBackend(backend: ExecutionBackend) {
        container.engine.updatePreferences { it.copy(backend = backend) }
    }

    fun setTileSize(tileSize: Int) {
        container.engine.updatePreferences { it.copy(tileSize = tileSize) }
    }

    fun setOutputFormat(format: OutputFormat) {
        container.engine.updatePreferences { it.copy(outputFormat = format) }
    }

    fun setCustomOutputFolder(treeUri: Uri?) {
        container.engine.updatePreferences { it.copy(outputTreeUri = treeUri?.toString()) }
    }

    fun setThreadCount(threads: String) {
        container.engine.updatePreferences { it.copy(threadCount = threads) }
    }

    fun setHideProgramOutput(hide: Boolean) {
        container.engine.updatePreferences { it.copy(hideProgramOutput = hide) }
    }

    fun runFolderBatchUpscale(directoryUri: Uri) {
        val current = uiState.value
        val preset = current.selection.selectedPreset
        val spec = container.modelRepository.resolveExecutionSpec(
            preset = preset,
            scale = current.selection.selectedScale,
            catalogStates = current.catalogModels,
            overrideModelId = current.selection.selectedModelOverrideId
        )
        if (preset.requiresDownload && spec == null) {
            postBanner("Download the ${preset.title} model first, or select Instant mode.")
            return
        }
        container.engine.startDirectoryUpscale(
            directoryUri = directoryUri,
            preset = preset,
            scale = spec?.scale ?: current.selection.selectedScale,
            modelSpec = if (preset.id == PresetId.INSTANT) null else spec
        )
    }

    fun runRawAdvancedCommand(command: String) {
        container.engine.runAdvancedCommand(command)
    }

    fun resetEngineCache() {
        viewModelScope.launch {
            val result = container.engine.ensureInitialized(forceReset = true)
            postBanner(
                if (result.isSuccess) "Engine runtime cache refreshed."
                else "Failed to refresh engine cache."
            )
        }
    }

    fun discoverAdvancedCommands(): List<PresetCommandResolver.DiscoveredCommand> {
        return PresetCommandResolver.discoverAdvancedCommands(
            modelsRootDir = container.modelRepository.modelsRootDir
        )
    }

    fun dismissBanner() {
        selectionFlow.update { it.copy(bannerMessage = null) }
    }

    private fun postBanner(msg: String) {
        selectionFlow.update { it.copy(bannerMessage = msg) }
    }

    private fun loadThemeMode(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(uiPrefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RealScaleViewModel(
                application = application,
                container = RealScaleAppContainer.getInstance(application)
            ) as T
        }
    }

    companion object {
        private const val UI_PREFS_NAME = "realscale_ui_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
