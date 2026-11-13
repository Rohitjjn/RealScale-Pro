package com.tumuyan.ncnn.realsr.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.EnginePreferences
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.ExecutionBackend
import com.tumuyan.ncnn.realsr.core.engine.ExternalOpenMode
import com.tumuyan.ncnn.realsr.core.engine.ImageIntakeManager
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.OutputNameFormatter
import com.tumuyan.ncnn.realsr.core.engine.PreflightAnalyzer
import com.tumuyan.ncnn.realsr.core.engine.PreflightMitigation
import com.tumuyan.ncnn.realsr.core.engine.PreflightVerdict
import com.tumuyan.ncnn.realsr.core.engine.PresetCommandResolver
import com.tumuyan.ncnn.realsr.core.engine.PresetId
import com.tumuyan.ncnn.realsr.core.engine.QueuedJobItem
import com.tumuyan.ncnn.realsr.core.engine.RecoveryAction
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
    val bannerMessage: String? = null,
    val hasPlayedLaunchFade: Boolean = false,
    val pendingOpenResultNavigation: Boolean = false,
    val preflightAdvisory: PreflightVerdict.Advisory? = null
)

data class RealScaleUiState(
    val selection: StudioSelectionState = StudioSelectionState(),
    val engineState: EngineState = EngineState.Idle,
    val enginePreferences: EnginePreferences = EnginePreferences(),
    val queue: List<QueuedJobItem> = emptyList(),
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

private data class CoreEngineSnapshot(
    val state: EngineState,
    val prefs: EnginePreferences,
    val log: String,
    val queue: List<QueuedJobItem>
)

class RealScaleViewModel(
    application: Application,
    private val container: RealScaleAppContainer,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {

    private val uiPrefs = application.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
    private val initialEnginePrefs = container.engine.preferences.value

    private val restoredPreset: UpscalePreset = run {
        val savedName = savedStateHandle.get<String>(STATE_SELECTED_PRESET)
        val presetId = savedName?.let { runCatching { PresetId.valueOf(it) }.getOrNull() }
            ?: initialEnginePrefs.lastUsedPresetId
        UpscalePreset.fromId(presetId)
    }

    private val restoredScale: Int = run {
        val candidate = savedStateHandle.get<Int>(STATE_SELECTED_SCALE) ?: initialEnginePrefs.lastUsedScale
        if (candidate in restoredPreset.supportedScales) candidate else restoredPreset.defaultScale
    }

    private val restoredUris: List<Uri> = savedStateHandle
        .get<ArrayList<String>>(STATE_SELECTED_URIS)
        ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        .orEmpty()

    private val selectionFlow = MutableStateFlow(
        StudioSelectionState(
            selectedUris = restoredUris,
            selectedPreset = restoredPreset,
            selectedScale = restoredScale,
            selectedModelOverrideId = savedStateHandle.get<String>(STATE_MODEL_OVERRIDE),
            themeMode = loadThemeMode(),
            selectedResultIndex = savedStateHandle.get<Int>(STATE_SELECTED_RESULT_INDEX) ?: 0
        )
    )

    private val coreFlows = combine(
        container.engine.state,
        container.engine.preferences,
        container.engine.consoleLog,
        container.engine.queue
    ) { engineState, prefs, log, queue ->
        CoreEngineSnapshot(engineState, prefs, log, queue)
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
    ) { sel, core, (recent, models, installedBytes) ->
        val updatedSelection = if (core.state is EngineState.Completed && sel.resultCreations != core.state.result.creations) {
            sel.copy(
                resultCreations = core.state.result.creations,
                selectedResultIndex = 0
            )
        } else {
            sel
        }
        RealScaleUiState(
            selection = updatedSelection,
            engineState = core.state,
            enginePreferences = core.prefs,
            queue = core.queue,
            consoleLog = core.log,
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
        viewModelScope.launch {
            container.engine.ensureInitialized(forceReset = false)
        }
        // Restore active Result creations across process death if SavedStateHandle has IDs
        val savedResultIds = savedStateHandle.get<LongArray>(STATE_RESULT_IDS)
        if (savedResultIds != null && savedResultIds.isNotEmpty()) {
            viewModelScope.launch {
                val dao = container.database.recentCreationDao()
                val restored = savedResultIds.toList().mapNotNull { dao.getById(it) }
                if (restored.isNotEmpty() && selectionFlow.value.resultCreations.isEmpty()) {
                    selectionFlow.update { prev ->
                        prev.copy(
                            resultCreations = restored,
                            selectedResultIndex = prev.selectedResultIndex.coerceIn(0, restored.lastIndex)
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            container.engine.state.collect { st ->
                if (st is EngineState.Completed) {
                    val creations = st.result.creations
                    savedStateHandle[STATE_RESULT_IDS] = creations.map { it.id }.toLongArray()
                    savedStateHandle[STATE_SELECTED_RESULT_INDEX] = 0
                    selectionFlow.update {
                        it.copy(
                            resultCreations = creations,
                            selectedResultIndex = 0
                        )
                    }
                }
            }
        }
    }

    fun onPhotosSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        persistSelectedUris(uris)
        selectionFlow.update { it.copy(selectedUris = uris, bannerMessage = null, preflightAdvisory = null) }
    }

    /**
     * Stages externally shared / opened URIs into app cache immediately while caller URI
     * permission is active, falling back to the raw URIs if staging is skipped in unit tests.
     */
    fun onExternalPhotosReceived(uris: List<Uri>) {
        if (uris.isEmpty()) return
        persistSelectedUris(uris)
        selectionFlow.update { it.copy(selectedUris = uris, bannerMessage = null, preflightAdvisory = null) }
        viewModelScope.launch {
            val staged = uris.mapNotNull { uri ->
                ImageIntakeManager.stageIncomingUri(getApplication(), uri).getOrNull()?.stagedUri
            }
            if (staged.size == uris.size) {
                persistSelectedUris(staged)
                selectionFlow.update { it.copy(selectedUris = staged) }
            }
        }
    }

    fun clearSelectedPhotos() {
        persistSelectedUris(emptyList())
        selectionFlow.update { it.copy(selectedUris = emptyList(), preflightAdvisory = null) }
    }

    fun selectPreset(preset: UpscalePreset) {
        selectionFlow.update { prev ->
            val nextScale = if (prev.selectedScale in preset.supportedScales) {
                prev.selectedScale
            } else {
                preset.defaultScale
            }
            savedStateHandle[STATE_SELECTED_PRESET] = preset.id.name
            savedStateHandle[STATE_SELECTED_SCALE] = nextScale
            savedStateHandle[STATE_MODEL_OVERRIDE] = null
            prev.copy(
                selectedPreset = preset,
                selectedScale = nextScale,
                selectedModelOverrideId = null,
                preflightAdvisory = null
            )
        }
    }

    fun selectScale(scale: Int) {
        savedStateHandle[STATE_SELECTED_SCALE] = scale
        selectionFlow.update { it.copy(selectedScale = scale, preflightAdvisory = null) }
    }

    fun selectModelOverride(modelId: String?) {
        selectionFlow.update { prev ->
            val catalogItem = uiState.value.catalogModels.firstOrNull { it.catalog.id == modelId }?.catalog
            val nextScale = if (catalogItem != null && prev.selectedScale !in catalogItem.supportedScales) {
                catalogItem.defaultScale
            } else {
                prev.selectedScale
            }
            savedStateHandle[STATE_MODEL_OVERRIDE] = modelId
            savedStateHandle[STATE_SELECTED_SCALE] = nextScale
            prev.copy(
                selectedModelOverrideId = modelId,
                selectedScale = nextScale,
                preflightAdvisory = null
            )
        }
    }

    /**
     * Evaluates pre-flight memory, dimension, and storage safety before launching the upscale job.
     * If safe, begins immediately; otherwise surfaces a calm advisory with one-tap mitigations.
     */
    fun requestPrimaryUpscale() {
        val current = uiState.value
        val uris = current.selection.selectedUris
        if (uris.isEmpty()) return

        val preset = current.selection.selectedPreset
        val spec = container.modelRepository.resolveExecutionSpec(
            preset = preset,
            scale = current.selection.selectedScale,
            catalogStates = current.catalogModels,
            overrideModelId = current.selection.selectedModelOverrideId
        )
        if (preset.requiresDownload && spec == null) return

        val effectiveScale = spec?.scale ?: current.selection.selectedScale
        viewModelScope.launch {
            val verdict = PreflightAnalyzer.inspectAndEvaluate(
                context = getApplication(),
                uris = uris,
                scale = effectiveScale,
                preset = preset,
                preferences = current.enginePreferences
            )
            when (verdict) {
                PreflightVerdict.Safe -> startPrimaryUpscale()
                is PreflightVerdict.Advisory -> {
                    selectionFlow.update { it.copy(preflightAdvisory = verdict) }
                }
            }
        }
    }

    fun applyPreflightMitigation(mitigation: PreflightMitigation) {
        when (mitigation) {
            PreflightMitigation.LowerScaleTo2x -> selectScale(2)
            is PreflightMitigation.ProcessInTiles -> setTileSize(mitigation.tileSize)
            is PreflightMitigation.SmallerTileSize -> setTileSize(mitigation.tileSize)
        }
        selectionFlow.update { it.copy(preflightAdvisory = null) }
        startPrimaryUpscale()
    }

    fun confirmPreflightProceedAnyway() {
        selectionFlow.update { it.copy(preflightAdvisory = null) }
        startPrimaryUpscale()
    }

    fun dismissPreflightAdvisory() {
        selectionFlow.update { it.copy(preflightAdvisory = null) }
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
        selectionFlow.update { it.copy(preflightAdvisory = null) }
        container.engine.startUpscale(
            UpscaleRequest(
                inputUris = uris,
                preset = preset,
                scale = effectiveScale,
                modelSpec = if (preset.id == PresetId.INSTANT && current.selection.selectedModelOverrideId == null) {
                    null
                } else {
                    spec
                },
                autoSaveToGallery = true
            )
        )
        return true
    }

    fun applyErrorRecovery(action: RecoveryAction): Boolean {
        return container.engine.retryWithRecovery(action)
    }

    fun cancelProcessing() {
        container.engine.cancel()
    }

    fun cancelQueuedItem(itemId: String) {
        container.engine.cancelQueuedItem(itemId)
    }

    fun enableBackgroundCompletionNotification() {
        container.engine.requestCompletionNotificationForActiveSession()
    }

    fun acknowledgeEngineState() {
        container.engine.acknowledgeTerminalState()
    }

    fun openRecentCreation(entity: RecentCreationEntity) {
        savedStateHandle[STATE_RESULT_IDS] = longArrayOf(entity.id)
        savedStateHandle[STATE_SELECTED_RESULT_INDEX] = 0
        selectionFlow.update {
            it.copy(
                resultCreations = listOf(entity),
                selectedResultIndex = 0
            )
        }
    }

    fun openCreationById(creationId: Long) {
        viewModelScope.launch {
            val found = container.database.recentCreationDao().getById(creationId) ?: return@launch
            savedStateHandle[STATE_RESULT_IDS] = longArrayOf(found.id)
            savedStateHandle[STATE_SELECTED_RESULT_INDEX] = 0
            selectionFlow.update {
                it.copy(
                    resultCreations = listOf(found),
                    selectedResultIndex = 0,
                    pendingOpenResultNavigation = true
                )
            }
        }
    }

    fun consumePendingResultNavigation() {
        selectionFlow.update { it.copy(pendingOpenResultNavigation = false) }
    }

    fun selectResultIndex(index: Int) {
        selectionFlow.update { prev ->
            val clamped = index.coerceIn(0, (prev.resultCreations.size - 1).coerceAtLeast(0))
            savedStateHandle[STATE_SELECTED_RESULT_INDEX] = clamped
            prev.copy(selectedResultIndex = clamped)
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
        val uris = listOf(uriToLoad)
        persistSelectedUris(uris)
        selectionFlow.update { prev ->
            prev.copy(selectedUris = uris, preflightAdvisory = null)
        }
    }

    fun saveResultToGallery(creation: RecentCreationEntity) {
        viewModelScope.launch {
            val file = File(creation.outputPath)
            if (!file.isFile) {
                postBanner("Output file is no longer available.")
                return@launch
            }
            val origName = ImageIntakeManager.resolveDisplayName(getApplication(), Uri.parse(creation.sourceUri))
            val targetName = OutputNameFormatter.buildGalleryFileName(
                originalDisplayName = origName,
                scale = creation.scale,
                outputExtension = file.extension
            )
            val res = MediaExportHelper.saveToGalleryOrFolder(
                context = getApplication(),
                sourceFile = file,
                customTreeUri = uiState.value.enginePreferences.outputTreeUri,
                displayFileName = targetName
            )
            res.fold(
                onSuccess = { savedUri ->
                    container.database.recentCreationDao().updateSavedUri(creation.id, savedUri.toString())
                    selectionFlow.update { prev ->
                        prev.copy(
                            resultCreations = prev.resultCreations.map { item ->
                                if (item.id == creation.id) item.copy(savedUri = savedUri.toString()) else item
                            },
                            bannerMessage = "Saved $targetName to Pictures/RealScale-Pro"
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

    fun setExternalOpenMode(mode: ExternalOpenMode) {
        container.engine.updatePreferences { it.copy(externalOpenMode = mode) }
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

    fun markLaunchFadePlayed() {
        if (!selectionFlow.value.hasPlayedLaunchFade) {
            selectionFlow.update { it.copy(hasPlayedLaunchFade = true) }
        }
    }

    private fun persistSelectedUris(uris: List<Uri>) {
        savedStateHandle[STATE_SELECTED_URIS] = ArrayList(uris.map { it.toString() })
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
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return RealScaleViewModel(
                application = application,
                container = RealScaleAppContainer.getInstance(application),
                savedStateHandle = extras.createSavedStateHandle()
            ) as T
        }

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
        private const val STATE_SELECTED_URIS = "selected_uris"
        private const val STATE_SELECTED_PRESET = "selected_preset"
        private const val STATE_SELECTED_SCALE = "selected_scale"
        private const val STATE_MODEL_OVERRIDE = "selected_model_override"
        private const val STATE_RESULT_IDS = "result_creation_ids"
        private const val STATE_SELECTED_RESULT_INDEX = "selected_result_index"
    }
}
