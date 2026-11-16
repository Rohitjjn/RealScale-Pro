package com.tumuyan.ncnn.realsr.ui.state

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import com.tumuyan.ncnn.realsr.core.ai.AiCatalog
import com.tumuyan.ncnn.realsr.core.ai.AiClientException
import com.tumuyan.ncnn.realsr.core.ai.AiImageClient
import com.tumuyan.ncnn.realsr.core.ai.AiImagePrep
import com.tumuyan.ncnn.realsr.core.ai.AiKeyRecord
import com.tumuyan.ncnn.realsr.core.ai.AiKeyStore
import com.tumuyan.ncnn.realsr.core.ai.AiModel
import com.tumuyan.ncnn.realsr.core.ai.AiPrefs
import com.tumuyan.ncnn.realsr.core.ai.AiRequest
import com.tumuyan.ncnn.realsr.core.ai.AiProvider
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.device.DeviceProfile
import com.tumuyan.ncnn.realsr.core.device.DeviceProfiler
import com.tumuyan.ncnn.realsr.core.device.HapticKind
import com.tumuyan.ncnn.realsr.core.device.Haptics
import com.tumuyan.ncnn.realsr.core.device.ThermalGuard
import com.tumuyan.ncnn.realsr.core.engine.EnginePreferences
import com.tumuyan.ncnn.realsr.core.engine.ImageIntakeManager
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.ExecutionBackend
import com.tumuyan.ncnn.realsr.core.engine.ModelExecutionSpec
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.OutputNameFormatter
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.engine.UpscaleRequest
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.core.plan.JobPrefs
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.ModelCatalog
import com.tumuyan.ncnn.realsr.core.plan.ModelEntry
import com.tumuyan.ncnn.realsr.core.plan.PipelinePlanner
import com.tumuyan.ncnn.realsr.core.plan.Plan
import com.tumuyan.ncnn.realsr.core.plan.PlanInput
import com.tumuyan.ncnn.realsr.core.plan.PlanLevel
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import com.tumuyan.ncnn.realsr.ui.MediaExportHelper
import com.tumuyan.ncnn.realsr.ui.media.MediaLibrary
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

/**
 * Single source of truth for the new UI: navigation, onboarding phase, per-mode job preferences,
 * settings, media, results, the live job and downloads.
 *
 * Persistence uses one SharedPreferences file holding JSON, so a process death restores the same
 * screen with the same numbers; engine state itself lives in [RealScaleAppContainer.engine] and is
 * never duplicated here.
 */
@Stable
class AppStore(
    private val context: Context,
    private val container: RealScaleAppContainer,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefsStore: SharedPreferences =
        context.getSharedPreferences("realscale_state", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val media = MediaLibrary(context)
    private val haptics = Haptics(context)

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    var device: DeviceProfile = DeviceProfiler.read(context)
        private set

    /** Set by the activity so onboarding can trigger the real system dialogs. */
    var permissionRequester: ((photos: Boolean, notifications: Boolean) -> Unit)? = null

    /** Set by the activity: the real pickers (documents, camera, clipboard). */
    var mediaIntents: MediaIntents? = null

    private var toastJob: Job? = null
    private var resultsJob: Job? = null
    private var jobStartedAt = 0L

    /* Round 5: AI Upscale Mode keeps its own store, its own clock and its own job. */
    private val aiKeyStore = AiKeyStore(context)
    private var aiJobTask: Job? = null
    private var aiJobStartedAt = 0L
    private var aiTickJob: Job? = null
    private var benchmarkSamples = mutableListOf<Pair<String, Double>>()

    init {
        restore()
        container.engine.updatePreferences {
            it.copy(outputTreeUri = _state.value.settings.saveFolderUri)
        }
        observeResults()
        observeCatalog()
        observeEngine()
        refreshPhotos()
    }

    /* ------------------------------------------------------------------ persistence */

    private fun restore() {
        val saved = prefsStore.getString(KEY_STATE, null)?.let {
            runCatching { json.decodeFromString(PersistedState.serializer(), it) }.getOrNull()
        }
        val base = AppUiState()
        val keys = aiKeyStore.keys()
        val aiPrefs = aiKeyStore.loadPrefs()
        if (saved == null) {
            _state.value = base.copy(phase = Phase.Splash, aiKeys = keys, aiPrefs = aiPrefs)
            return
        }
        _state.value = base.copy(
            phase = saved.phase,
            routes = saved.routes.ifEmpty { listOf(Route.Home) },
            mode = saved.mode,
            onboarded = saved.onboarded,
            photoAccess = saved.photoAccess,
            notificationsAsked = saved.notificationsAsked,
            cameraAsked = saved.cameraAsked,
            superIntroSeen = saved.superIntroSeen,
            settings = saved.settings,
            prefs = saved.prefs,
            aiMode = saved.aiMode,
            aiKeys = keys,
            aiPrefs = aiPrefs,
        )
    }

    private fun persist() {
        val s = _state.value
        val saved = PersistedState(
            phase = s.phase,
            routes = s.routes,
            mode = s.mode,
            onboarded = s.onboarded,
            photoAccess = s.photoAccess,
            notificationsAsked = s.notificationsAsked,
            cameraAsked = s.cameraAsked,
            superIntroSeen = s.superIntroSeen,
            settings = s.settings,
            prefs = s.prefs,
            aiMode = s.aiMode,
        )
        prefsStore.edit().putString(KEY_STATE, json.encodeToString(PersistedState.serializer(), saved)).apply()
    }

    /* ------------------------------------------------------------------ navigation */

    fun push(route: Route) = update { it.copy(routes = it.routes + route) }

    fun replaceTop(route: Route) = update { it.copy(routes = it.routes.dropLast(1) + route) }

    fun pop(times: Int = 1) = update {
        val remaining = it.routes.dropLast(times.coerceAtLeast(1))
        it.copy(routes = if (remaining.isEmpty()) listOf(Route.Home) else remaining)
    }

    fun home() = update { it.copy(routes = listOf(Route.Home)) }

    fun setPhase(phase: Phase) = update { it.copy(phase = phase) }

    fun setMode(mode: ModeId) = update { it.copy(mode = mode) }

    /* ------------------------------------------------------------------ settings and prefs */

    fun updateSettings(block: (AppSettings) -> AppSettings) = update { it.copy(settings = block(it.settings)) }

    fun prefsFor(mode: ModeId): JobPrefs = _state.value.prefs[mode] ?: AppUiState.defaultPrefsFor(mode)

    fun updatePrefs(mode: ModeId, block: (JobPrefs) -> JobPrefs) {
        val before = prefsFor(mode)
        val after = block(before)
        update { it.copy(prefs = it.prefs + (mode to after)) }
        if (after.level == PlanLevel.DEFAULT && before.level != PlanLevel.DEFAULT) {
            toast("Back to recommended settings")
        }
    }

    fun resetPrefs(mode: ModeId) {
        update { it.copy(prefs = it.prefs + (mode to AppUiState.defaultPrefsFor(mode))) }
        toast("Back to recommended settings")
    }

    /* ------------------------------------------------------------------ onboarding flags */

    fun requestPhotoPermission() {
        permissionRequester?.invoke(true, false)
    }

    fun requestNotificationPermission() {
        permissionRequester?.invoke(false, true)
    }

    fun skipPhotoPermission() {
        update { it.copy(photoAccess = false, phase = Phase.PermNotif) }
    }

    fun grantPhotoAccess(limited: Boolean) {
        update { it.copy(photoAccess = true, limitedAccess = limited, phase = Phase.PermNotif) }
        refreshPhotos()
    }

    fun finishNotifications(accepted: Boolean) {
        update { it.copy(notificationsAsked = true, phase = Phase.Device) }
    }

    fun onPermissionResult(photos: Boolean, notifications: Boolean, granted: Boolean, limited: Boolean) {
        when {
            photos && granted -> grantPhotoAccess(limited)
            photos -> skipPhotoPermission()
            notifications -> finishNotifications(granted)
        }
    }

    fun finishDeviceCheck() = update { it.copy(phase = Phase.Setup) }

    fun finishSetup() {
        update {
            it.copy(
                phase = Phase.App,
                onboarded = true,
                starterPackPaused = false,
                routes = listOf(Route.Home),
            )
        }
        refreshPhotos()
    }

    fun markSuperIntroSeen() = update { it.copy(superIntroSeen = true) }

    /**
     * Testing seam for the instrumentation smoke test: lands on Home without walking the guide,
     * the primers or the Starter Pack download. Not reachable from any screen.
     */
    internal fun skipOnboardingForTest() {
        update { it.copy(phase = Phase.App, onboarded = true, routes = listOf(Route.Home)) }
    }

    /* ------------------------------------------------------------------ feedback */

    fun toast(text: String) {
        val message = ToastMessage(SystemClock.elapsedRealtime(), text)
        update { it.copy(toast = message) }
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2400)
            update { if (it.toast?.id == message.id) it.copy(toast = null) else it }
        }
    }

    fun dismissToast() = update { it.copy(toast = null) }

    fun haptic(kind: HapticKind) = haptics.play(kind, enabled = _state.value.settings.haptics)

    fun replayGuide() {
        update { it.copy(phase = Phase.Guide, routes = listOf(Route.Home)) }
    }

    /* ------------------------------------------------------------------ device */

    fun refreshDevice() {
        device = DeviceProfiler.read(context, benchmarkSecPerTile = device.benchmarkSecPerTile)
        // `device` is a plain field, so the screens need a state change to re-read it.
        update { it.copy(deviceRevision = it.deviceRevision + 1) }
    }

    /* ------------------------------------------------------------------ media */

    fun refreshPhotos() {
        if (!_state.value.photoAccess) return
        update { it.copy(photosLoading = true) }
        scope.launch {
            val loaded = runCatching { media.queryImages(limit = 240) }.getOrDefault(emptyList())
            update { state ->
                val merged = mergeImported(state.photos, loaded)
                state.copy(photos = merged, photosLoading = false)
            }
        }
    }

    /**
     * Library rows (newest first from MediaStore) plus the files the user opened with the app.
     *
     * The order is capture time, newest first, so the Home strip reads left to right exactly like
     * the system gallery. Sorting on the id string — `"media:100" < "media:99"` — is what made the
     * row look shuffled after a refresh.
     */
    private fun mergeImported(current: List<PhotoItem>, loaded: List<PhotoItem>): List<PhotoItem> {
        val importedOnly = current.filter { it.imported }
        return (importedOnly + loaded)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<PhotoItem> { it.dateTaken }.thenByDescending { it.id })
    }

    /** A photo the user opened with the app (Open with, share target, picker). */
    fun addImportedPhoto(photo: PhotoItem) {
        update { state ->
            val existing = state.photos.filterNot { it.id == photo.id }
            state.copy(photos = (listOf(photo) + existing).distinctBy { it.id })
        }
    }

    fun photoById(id: String): PhotoItem? = _state.value.photos.firstOrNull { it.id == id }

    fun openViewer(photo: PhotoItem) = push(Route.Viewer(photo.id))

    fun sharePhoto(photo: PhotoItem) {
        val shared = mediaIntents?.share(Uri.parse(photo.uri), photo.name, "image/*") ?: false
        if (!shared) toast("No app can receive this image")
    }

    fun shareResult(result: ResultItem) {
        val uri = result.savedUri?.let { Uri.parse(it) } ?: Uri.fromFile(File(result.outputPath))
        val shared = mediaIntents?.share(uri, result.fileName, "image/*") ?: false
        if (!shared) toast("No app can receive this image")
    }

    fun copyFileName(photo: PhotoItem) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("File name", photo.name))
        toast("File name copied")
    }

    fun openConfig(photo: PhotoItem) = push(Route.Config(photo.id))

    fun openResult(resultId: String) = push(Route.Result(resultId))

    fun markCameraAsked() = update { it.copy(cameraAsked = true) }

    fun pickFile() {
        if (mediaIntents == null) toast("No file picker available") else mediaIntents?.pickDocument()
    }

    /** The Files tile on Home: the real system browser, not the photo-only picker. */
    fun pickFromFiles() {
        if (mediaIntents == null) toast("No file picker available") else mediaIntents?.pickFromFiles()
    }

    fun takePhoto() {
        if (mediaIntents == null) toast("No camera available") else mediaIntents?.takePhoto()
    }

    /**
     * Round 5, issue 2: the host now reports its own reason (empty clip, unreadable image, a
     * browser's HTML clip being downloaded), so this only has to make sure an exception can never
     * escape into the composition and leave the sheet looking broken.
     */
    fun pasteFromClipboard() {
        val intents = mediaIntents
        if (intents == null) {
            toast("The clipboard is not available")
            return
        }
        runCatching { intents.pasteImage() }
            .onFailure { toast("That clipboard image could not be opened") }
    }

    /**
     * Set by the home-screen widget before it opens the picker: the next imported image starts
     * upscaling immediately at this scale instead of stopping on the configuration screen.
     */
    var pendingAutoScale: Int? = null

    fun onImportedPhoto(photo: PhotoItem?) {
        if (photo == null) {
            toast("That image could not be opened")
            return
        }
        addImportedPhoto(photo)
        val auto = pendingAutoScale
        pendingAutoScale = null
        if (auto != null && auto in 2..5) {
            // One tap from the widget: recommended model, the scale the user tapped, no dialog.
            startWidgetJob(photo, auto)
            return
        }
        toast("${photo.name} added")
        /*
         * Round 5: when Home is on the AI mode card, the picked image goes to the AI configuration
         * screen — or to the key setup first when no key exists yet (the same two cases as the AI
         * icon on the configuration screen).
         */
        if (_state.value.aiMode) {
            if (_state.value.aiReady) push(Route.AiConfig(photo.id)) else push(Route.AiKeys)
            return
        }
        push(Route.Config(photo.id))
    }

    /**
     * Widget shortcut: build a plan at exactly [scale]× with the recommended model and start it.
     * `MANUAL` is only used to carry the scale through the planner; model selection stays automatic.
     */
    fun startWidgetJob(photo: PhotoItem, scale: Int) {
        val mode = ModeId.UPSCALE
        val base = prefsFor(mode)
        val prefs = if (scale == 2) base else base.copy(level = PlanLevel.MANUAL, scale = scale)
        val settings = _state.value.settings
        val plan = PipelinePlanner.makePlan(
            photo = PlanInput(photo.width, photo.height, photo.sizeMB, photo.ext, photo.type, photo.lowConfidence),
            mode = mode,
            prefs = prefs,
            installed = _state.value.installed,
            device = device.toPlanDevice(),
            settingsCompute = when (settings.compute) {
                ComputePref.Cpu -> "cpu"
                ComputePref.Gpu -> "gpu"
                ComputePref.Auto -> "auto"
            },
            settingsFormat = when (settings.format) {
                FormatSetting.Jpeg -> "jpeg"
                FormatSetting.Png -> "png"
                FormatSetting.Webp -> "webp"
                FormatSetting.Auto -> "auto"
            },
        )
        haptic(HapticKind.Medium)
        startJob(photo, plan, mode)
    }

    /* ------------------------------------------------------------------ results */

    private var lastResultIds: List<String> = emptyList()

    private fun observeResults() {
        resultsJob = scope.launch {
            container.database.recentCreationDao().observeRecent(200).collect { rows ->
                val mapped = rows.map(::toResultItem)
                val ids = mapped.map { it.id }
                update { state -> state.copy(results = mapped) }
                /*
                 * Round 5, issue 1: a second net. If the engine finished while this screen was the
                 * visible one and the stack rewrite above could not run (job already cleared, or the
                 * row arrived a frame later), the row appearing here is the moment to move on. It
                 * only ever acts from the processing screen, never against the user's navigation.
                 */
                val last = _state.value.lastResultId
                if (last != null && last in ids && _state.value.route is Route.Processing) {
                    replaceTop(Route.Result(last))
                }
                // A saved or deleted upscale changes the "Recent creations" widget.
                if (ids != lastResultIds) {
                    lastResultIds = ids
                    com.tumuyan.ncnn.realsr.widget.RealScaleWidgetsUpdater.refreshSoon(context)
                }
            }
        }
    }

    private fun toResultItem(row: RecentCreationEntity): ResultItem {
        // `nameFor` also knows the ids that left the catalog, so an old Anime4K creation keeps its name.
        // AI creations are stored as `ai:<catalog id>` because they never touched the local catalog.
        val modelName = if (row.modelId.startsWith("ai:")) {
            AiCatalog.labelFor(row.modelId.removePrefix("ai:"))
        } else {
            ModelCatalog.nameFor(row.modelId)
        }
        return ResultItem(
            id = row.id.toString(),
            photoId = row.sourceUri,
            sourceUri = row.sourceUri,
            outputPath = row.outputPath,
            savedUri = row.savedUri,
            mode = if (row.presetId == "MAXIMUM") ModeId.SUPER else ModeId.UPSCALE,
            scale = row.scale,
            modelId = row.modelId,
            modelName = modelName,
            outW = row.outputWidth,
            outH = row.outputHeight,
            sizeMB = runCatching { File(row.outputPath).length() / 1048576.0 }.getOrDefault(0.0),
            timeSec = (row.durationMs / 1000).toInt(),
            format = row.outputPath.substringAfterLast('.', "png").uppercase(),
            fileName = File(row.outputPath).name,
            createdAt = row.createdAtEpochMs,
        )
    }

    fun deleteResults(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            val numeric = ids.mapNotNull { it.toLongOrNull() }
            container.database.recentCreationDao().deleteByIds(numeric)
            update { it.copy(selectedUpscales = emptySet()) }
            toast(if (numeric.size == 1) "1 upscale deleted" else "${numeric.size} upscales deleted")
        }
    }

    fun toggleSelectedUpscale(id: String) = update {
        val next = if (id in it.selectedUpscales) it.selectedUpscales - id else it.selectedUpscales + id
        it.copy(selectedUpscales = next)
    }

    fun clearUpscaleSelection() = update { it.copy(selectedUpscales = emptySet()) }

    /* ------------------------------------------------------------------ catalog / downloads */

    private var lastCatalogEmitAt = 0L
    private var lastCatalogSignature: List<Pair<String, String>> = emptyList()

    private fun observeCatalog() {
        scope.launch {
            container.modelRepository.catalogModels.collect { states ->
                // A download reports every chunk it writes. Structural changes (start, pause,
                // install, fail) go straight through; plain progress is coalesced to ~10 fps so the
                // screens are not recomposed hundreds of times a second.
                val signature = states.map { it.catalog.id to it.status.name }
                val now = SystemClock.elapsedRealtime()
                val structural = signature != lastCatalogSignature
                if (!structural && now - lastCatalogEmitAt < 100L) return@collect
                lastCatalogSignature = signature
                lastCatalogEmitAt = now
                // The repository speaks manifest ids (HF directories); the UI speaks catalog ids.
                val installed = buildList {
                    addAll(ModelCatalog.BUNDLED_IDS)
                    states.filter { it.isInstalled }.forEach { state ->
                        ModelCatalog.byManifestId(state.catalog.id)?.let { add(it.id) }
                    }
                }.distinct()
                /*
                 * Every catalog row emits a state, and a model the user never touched reports
                 * NOT_INSTALLED. Mapping all of them into `downloads` made every card look like a
                 * transfer in progress — a dead 0 % ring with "Tap to cancel" and no way to start
                 * the download at all. Only a real, user-visible transfer state belongs here.
                 */
                val downloads = states
                    .filter { it.status.isActiveTransfer }
                    .mapNotNull { state ->
                        val entry = ModelCatalog.byManifestId(state.catalog.id) ?: return@mapNotNull null
                        entry.id to ModelDownloadUi(
                            modelId = entry.id,
                            progress = state.progressFraction,
                            state = state.status.name.lowercase(),
                            speedBytesPerSec = state.speedBytesPerSec,
                            message = state.errorMessage,
                        )
                    }
                    .toMap()
                update { it.copy(installed = installed, downloads = downloads) }
            }
        }
    }

    /** Catalog id -> manifest id, shared by every download action. */
    private fun manifestIdFor(modelId: String): String? = ModelCatalog.byId(modelId).takeIf {
        it.id == modelId
    }?.manifestId

    /**
     * Starts a download. [announce] keeps the toast for taps in Model Manager; the onboarding
     * queue starts on its own and stays silent. [allowMobileData] is set by the Starter Pack so the
     * first-run setup is never blocked by the "Wi-Fi only" preference.
     */
    fun downloadModel(
        modelId: String,
        announce: Boolean = true,
        allowMobileData: Boolean = false,
    ) {
        val entry = ModelCatalog.byId(modelId)
        val manifestId = entry.manifestId
        if (manifestId == null) {
            toast(
                if (entry.blocked != null) "${entry.name} is not downloadable yet"
                else "${entry.name} ships with the app",
            )
            return
        }
        val wifiOnly = !allowMobileData && _state.value.settings.wifiOnly
        scope.launch {
            container.modelRepository.startOrResumeDownload(manifestId, wifiOnly = wifiOnly)
            if (announce) toast("Downloading ${entry.name}")
        }
    }

    fun pauseModel(modelId: String, announce: Boolean = true) {
        val manifestId = manifestIdFor(modelId) ?: return
        scope.launch {
            container.modelRepository.pauseDownload(manifestId)
            if (announce) toast("Download paused")
        }
    }

    /* ---------------------------------------------------- Starter Pack queue (one at a time) */

    private var starterPackJob: Job? = null

    /**
     * Downloads the Starter Pack sequentially: the next model starts only once the previous one is
     * installed, so one row is moving and the rest read "Queued". Idempotent.
     */
    fun startStarterPack() {
        if (starterPackJob?.isActive == true) return
        starterPackJob = scope.launch {
            for (id in ModelCatalog.STARTER_IDS) {
                if (id in _state.value.installed) continue
                downloadOneStarter(id)
            }
        }
    }

    private suspend fun downloadOneStarter(modelId: String) {
        val manifestId = ModelCatalog.byId(modelId).manifestId ?: return
        var attempts = 0
        while (attempts < 3 && modelId !in _state.value.installed) {
            while (_state.value.starterPackPaused) delay(120)
            // Starter Pack ignores "Wi-Fi only": first-run setup has to finish on mobile data too.
            container.modelRepository.startOrResumeDownload(manifestId, wifiOnly = false)
            // Pause can land between the flag check above and this start; honour it either way.
            if (_state.value.starterPackPaused) container.modelRepository.pauseAllDownloads()
            when (awaitDownload(manifestId)) {
                ModelInstallStatus.INSTALLED -> return
                // Paused by the user: wait for Resume, which continues from the .part files.
                ModelInstallStatus.PAUSED -> Unit
                else -> {
                    attempts++
                    delay(1_500)
                }
            }
        }
    }

    private suspend fun awaitDownload(manifestId: String): ModelInstallStatus? =
        withTimeoutOrNull(20 * 60_000L) {
            container.modelRepository.catalogModels
                .mapNotNull { states -> states.firstOrNull { it.catalog.id == manifestId }?.status }
                .first {
                    it == ModelInstallStatus.INSTALLED ||
                        it == ModelInstallStatus.FAILED ||
                        it == ModelInstallStatus.PAUSED
                }
        } ?: ModelInstallStatus.FAILED

    /** Pause button on the setup screen: stops the queue and the transfer, without a toast. */
    fun pauseStarterPack() {
        if (_state.value.starterPackPaused) return
        update { it.copy(starterPackPaused = true) }
        scope.launch { container.modelRepository.pauseAllDownloads() }
    }

    fun resumeStarterPack() {
        if (!_state.value.starterPackPaused) return
        update { it.copy(starterPackPaused = false) }
        startStarterPack()
    }

    fun toggleStarterPackPause() {
        if (_state.value.starterPackPaused) resumeStarterPack() else pauseStarterPack()
    }

    fun cancelDownload(modelId: String) {
        val manifestId = manifestIdFor(modelId) ?: return
        scope.launch {
            container.modelRepository.cancelDownload(manifestId)
            toast("Download cancelled")
        }
    }

    fun deleteModel(modelId: String) {
        val entry = ModelCatalog.byId(modelId)
        val manifestId = entry.manifestId ?: return
        scope.launch {
            container.modelRepository.deleteInstalledModel(manifestId)
            toast("${entry.name} removed")
        }
    }

    /* ------------------------------------------------------------------ maintenance */

    /**
     * "Clear cache": the staged input copies and the transient engine images, never the extracted
     * binaries and never anything the user saved (see `ImageIntakeManager.clearTemporaryCache`).
     */
    fun clearCache() {
        scope.launch {
            val freed = runCatching { ImageIntakeManager.clearTemporaryCache(context) }.getOrDefault(0L)
            toast(if (freed > 0) "Cache cleared · ${fmtMB(freed / 1048576.0)}" else "Cache is already empty")
        }
    }

    /**
     * The reason the engine is running with smaller tiles (hot phone / low battery), or null.
     * Both the engine and the Processing banner read [ThermalGuard] so the message cannot lie.
     */
    fun throttleReason(): String? =
        if (_state.value.settings.throttle) ThermalGuard.reason(context) else null

    /* ------------------------------------------------------------------ save folder */

    /**
     * Settings → Output → Save folder. The row used to be a dead label: the engine already honoured
     * `prefs.outputTreeUri`, but nothing ever set it. The system picker (SAF) hands us a tree we can
     * keep writing to across reboots.
     */
    fun pickSaveFolder() {
        val intents = mediaIntents
        if (intents == null) toast("No folder picker available") else intents.pickSaveFolder()
    }

    fun onSaveFolderPicked(treeUri: String?, label: String?) {
        update { it.copy(settings = it.settings.copy(saveFolderUri = treeUri, saveFolderLabel = label)) }
        container.engine.updatePreferences { it.copy(outputTreeUri = treeUri) }
        toast(
            if (treeUri == null) "Saving to Pictures/RealScale-Pro"
            else "Saving to ${label ?: "the selected folder"}",
        )
    }

    /** Feedback is stored locally; there is no backend and no upload. */
    /**
     * "Send feedback": the message is written to a local copy *and* handed to the user's mail app
     * prefilled, so one tap on Send in Gmail/Outlook actually reaches us. The old version only saved
     * a text file on the device and nothing was ever delivered.
     */
    fun sendFeedback(text: String, attachDevice: Boolean) {
        val trimmed = text.trim()
        if (trimmed.length < 3) return
        val body = buildFeedbackBody(trimmed, attachDevice)
        runCatching {
            val dir = File(context.filesDir, "feedback").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
            File(dir, "feedback-$stamp.txt").writeText(body)
        }
        val opened = mediaIntents?.sendFeedbackMail("RealScale-Pro feedback", body) ?: false
        toast(if (opened) "Opening your mail app…" else "No email app installed")
    }

    private fun buildFeedbackBody(text: String, attachDevice: Boolean): String = buildString {
        append(text).append("\n\n---\n")
        if (attachDevice) {
            append("device: ").append(device.label).append('\n')
            append("ramGB: ").append(device.ramGB).append('\n')
            append("cores: ").append(device.cores).append('\n')
            append("gpu: ").append(device.gpu).append('\n')
        }
        append("version: 2.2.0\n")
    }

    /** Settings > Reset the app: settings, history and downloaded models. Photos are untouched. */
    fun resetApp() {
        scope.launch {
            runCatching {
                _state.value.installed.forEach { modelId ->
                    runCatching { container.modelRepository.deleteInstalledModel(modelId) }
                }
                container.database.recentCreationDao().clearAll()
            }
            prefsStore.edit().clear().apply()
            _state.value = AppUiState(phase = Phase.Splash)
            persist()
            toast("App reset")
        }
    }

    /* ------------------------------------------------------------------ planning */

    /** The 512 x 512 crop preview of the current settings, produced by the real engine. */
    var cropPreviewUri: String? = null
        private set

    fun requestCropPreview(photo: PhotoItem, plan: Plan, xPercent: Int, yPercent: Int) {
        scope.launch {
            cropPreviewUri = null
            val entry = plan.model
            val modelDir = File(container.modelRepository.modelsRootDir, entry.manifestId ?: entry.id)
            val modelPath = entry.entryFile?.let { File(modelDir, it).absolutePath } ?: modelDir.absolutePath
            val spec = ModelExecutionSpec(
                modelId = entry.id,
                displayName = entry.name,
                engineTool = entry.engineTool,
                modelPath = modelPath,
                scale = plan.scale,
                denoiseLevel = entry.denoiseLevel,
            )
            val result = com.tumuyan.ncnn.realsr.core.engine.CropPreviewEngine.generateCropPreview(
                context = context,
                sourceUri = Uri.parse(photo.uri),
                preset = if (_state.value.mode == ModeId.SUPER) UpscalePreset.MAXIMUM else UpscalePreset.PHOTO,
                scale = plan.scale,
                preferences = container.engine.preferences.value.copy(
                    tileSize = plan.tilePx,
                    backend = if (plan.compute == "CPU") ExecutionBackend.CPU else ExecutionBackend.AUTO_GPU,
                ),
                modelSpec = spec,
                modelsRootDir = container.modelRepository.modelsRootDir,
            )
            cropPreviewUri = result.getOrNull()?.afterCropFile?.let { "file://${it.absolutePath}" }
        }
    }

    fun planFor(photo: PhotoItem, mode: ModeId = _state.value.mode, large: String = "optimize"): Plan {
        val prefs = prefsFor(mode)
        val settings = _state.value.settings
        return PipelinePlanner.makePlan(
            photo = PlanInput(photo.width, photo.height, photo.sizeMB, photo.ext, photo.type, photo.lowConfidence),
            mode = mode,
            prefs = prefs,
            installed = _state.value.installed,
            device = device.toPlanDevice(),
            large = large,
            settingsCompute = when (settings.compute) {
                ComputePref.Cpu -> "cpu"
                ComputePref.Gpu -> "gpu"
                ComputePref.Auto -> "auto"
            },
            settingsFormat = when (settings.format) {
                FormatSetting.Jpeg -> "jpeg"
                FormatSetting.Png -> "png"
                FormatSetting.Webp -> "webp"
                FormatSetting.Auto -> "auto"
            },
        )
    }

    /* ------------------------------------------------------------------ job execution */

    private fun observeEngine() {
        scope.launch {
            container.engine.state.collect { engineState -> onEngineState(engineState) }
        }
    }

    private fun onEngineState(engineState: EngineState) {
        val current = _state.value.job
        when (engineState) {
            is EngineState.Preparing -> {
                if (current != null) update { it.copy(job = current.copy(stageLabel = engineState.label, status = JobStatus.Running)) }
            }

            is EngineState.Running -> {
                val started = if (jobStartedAt == 0L) SystemClock.elapsedRealtime() else jobStartedAt
                jobStartedAt = started
                val elapsed = ((SystemClock.elapsedRealtime() - started) / 1000).toInt()
                update { state ->
                    val job = state.job ?: return@update state
                    val progress = engineState.progressFraction.coerceIn(0f, 1f)
                    val remaining = if (progress > 0.02f) (elapsed / progress * (1 - progress)).toInt() else job.plan.etaSec
                    state.copy(
                        job = job.copy(
                            progress = progress,
                            status = JobStatus.Running,
                            elapsedSec = elapsed,
                            remainingSec = remaining.coerceAtLeast(0),
                            stageLabel = engineState.latestStatusLine,
                            tilesDone = engineState.currentFileIndex,
                            tilesTotal = engineState.totalFiles,
                        ),
                    )
                }
            }

            is EngineState.Completed -> {
                val result = engineState.result
                val first = result.creations.firstOrNull()
                val elapsed = ((SystemClock.elapsedRealtime() - jobStartedAt) / 1000).toInt().coerceAtLeast(1)
                jobStartedAt = 0L
                if (first != null) {
                    val secondsPerMp = elapsed.toDouble() / ((first.outputWidth.toDouble() * first.outputHeight) / 1_000_000).coerceAtLeast(0.1)
                    benchmarkSamples.add(first.modelId to secondsPerMp)
                    if (benchmarkSamples.size > 5) benchmarkSamples.removeAt(0)
                }
                val finishedId = first?.id?.toString()
                update { state ->
                    /*
                     * Land on the result. The job used to finish and quietly drop the user back on
                     * Home — the finished upscale was only reachable through My Upscales.
                     *
                     * Round 5, issue 1: this block used to bail out entirely when `job == null`, so
                     * any `clearJob()` racing the engine's Completed meant "no navigation at all".
                     * The stack is rewritten (not pushed) so back from the result goes to where the
                     * job was started, and the id is checked so a second emission cannot stack a
                     * duplicate screen.
                     */
                    val routes = if (finishedId != null && state.phase == Phase.App) {
                        val withoutJobScreens = state.routes.filterNot {
                            it is Route.Processing || it is Route.Result || it is Route.ResultFull
                        }
                        withoutJobScreens + Route.Result(finishedId)
                    } else {
                        state.routes
                    }
                    state.copy(
                        job = state.job?.copy(
                            progress = 1f,
                            status = JobStatus.Done,
                            resultId = finishedId,
                            elapsedSec = elapsed,
                            remainingSec = 0,
                            stageLabel = "Saved",
                        ),
                        /* Kept after `clearJob()`, so the Result screen can still resolve the
                         * fresh creation while the Room flow catches up (Round-4, issue 1). */
                        lastResultId = finishedId ?: state.lastResultId,
                        routes = routes,
                    )
                }
                container.engine.updatePreferences { prefs -> prefs.copy(lastUsedScale = prefs.lastUsedScale) }
            }

            is EngineState.Cancelled -> {
                jobStartedAt = 0L
                update { state -> state.copy(job = state.job?.copy(status = JobStatus.Cancelled, stageLabel = "Cancelled")) }
            }

            is EngineState.Failed -> {
                jobStartedAt = 0L
                update { state -> state.copy(job = state.job?.copy(status = JobStatus.Failed, stageLabel = engineState.userMessage)) }
                toast(engineState.userMessage)
            }

            EngineState.Idle -> Unit
        }
    }

    /**
     * Super Resolution runs on heavy models only: the planner's `wanted` entry is exactly the
     * recommended heavy model when it is *not* installed. Instead of quietly upscaling with the
     * small fallback (Round-4, issue 8) the job is refused and the download is started.
     */
    fun missingHeavyModel(plan: Plan, mode: ModeId = _state.value.mode): ModelEntry? =
        if (mode == ModeId.SUPER) plan.wanted else null

    /**
     * Starts the job described by [plan]. The command itself is built by the untouched engine layer
     * (`PresetCommandResolver` + upstream `CommandBuilder`); this only translates the plan into the
     * engine's request shape.
     */
    fun startJob(photo: PhotoItem, plan: Plan, mode: ModeId = _state.value.mode) {
        val settings = _state.value.settings
        val missing = missingHeavyModel(plan, mode)
        if (missing != null) {
            haptic(HapticKind.Warning)
            toast("${missing.name} is required for Super Resolution · downloading")
            downloadModel(missing.id)
            return
        }
        val entry = plan.model
        val modelDir = File(container.modelRepository.modelsRootDir, entry.manifestId ?: entry.id)
        val modelPath = entry.entryFile?.let { File(modelDir, it).absolutePath } ?: modelDir.absolutePath

        val spec = ModelExecutionSpec(
            modelId = entry.id,
            displayName = entry.name,
            engineTool = entry.engineTool,
            modelPath = modelPath,
            scale = plan.scale,
            modelScale = ModelCatalog.modelScaleFor(entry, plan.scale),
            denoiseLevel = entry.denoiseLevel ?: denoiseLevelFor(prefsFor(mode).denoise).takeIf { entry.denoise },
        )

        container.engine.updatePreferences { prefs ->
            prefs.copy(
                tileSize = plan.tilePx,
                backend = if (plan.compute == "CPU") ExecutionBackend.CPU else ExecutionBackend.AUTO_GPU,
                outputFormat = when (plan.format) {
                    "PNG" -> OutputFormat.PNG
                    "WebP" -> OutputFormat.WEBP
                    else -> OutputFormat.JPG
                },
                keepScreenOnDuringEnhance = settings.keepAwake,
                hapticsEnabled = settings.haptics,
                wifiOnlyDownloads = settings.wifiOnly,
                outputTreeUri = settings.saveFolderUri,
                tta = plan.level == PlanLevel.ADVANCED && prefsFor(mode).tta,
                /*
                 * Super Resolution is the "keep everything" mode: its copy promises the original
                 * file, so the input is never re-encoded there.
                 */
                compression = when {
                    mode == ModeId.SUPER -> "off"
                    plan.level == PlanLevel.ADVANCED -> prefsFor(mode).compression
                    else -> "auto"
                },
                compressionQuality = prefsFor(mode).compressionQ,
                outputQuality = if (plan.level == PlanLevel.ADVANCED) prefsFor(mode).formatQ else 95,
                keepMetadata = settings.keepMeta && prefsFor(mode).keepMeta,
                throttleWhenHot = settings.throttle,
            )
        }

        jobStartedAt = SystemClock.elapsedRealtime()
        update { state ->
            state.copy(
                mode = mode,
                job = JobUi(
                    photoId = photo.id,
                    mode = mode,
                    plan = plan,
                    progress = 0f,
                    status = JobStatus.Running,
                    remainingSec = plan.etaSec,
                    tilesTotal = plan.tiles,
                    stageLabel = "Preparing",
                ),
                routes = state.routes.filterNot { it is Route.Processing } + Route.Processing,
            )
        }
        haptic(HapticKind.Medium)
        container.engine.startUpscale(
            UpscaleRequest(
                inputUris = listOf(Uri.parse(photo.uri)),
                preset = if (mode == ModeId.SUPER) UpscalePreset.MAXIMUM else UpscalePreset.PHOTO,
                scale = plan.scale,
                modelSpec = spec,
                autoSaveToGallery = true,
                // The finished-job notification is about notifications, not about the haptic motor.
                notifyOnCompletion = _state.value.notificationsAsked,
            ),
        )
    }

    /** 0–100 % denoise maps onto the models' -1..3 `-n` levels. */
    private fun denoiseLevelFor(percent: Int): Int = when {
        percent <= 0 -> -1
        percent < 25 -> 0
        percent < 50 -> 1
        percent < 80 -> 2
        else -> 3
    }

    fun cancelJob() {
        container.engine.cancel()
        jobStartedAt = 0L
        haptic(HapticKind.Warning)
    }

    fun clearJob() = update { it.copy(job = null) }

    fun setJobBackground(background: Boolean) = update { it.copy(job = it.job?.copy(background = background)) }

    /**
     * Brings the running job back to the front: used by the "running in background" card and by a
     * tap on the ongoing notification. No-op when nothing is running or the screen is already up.
     */
    fun openProcessing() {
        val job = _state.value.job ?: return
        if (job.status != JobStatus.Running) return
        if (_state.value.route is Route.Processing) return
        update { state ->
            state.copy(
                job = job.copy(background = false),
                routes = state.routes.filterNot { it is Route.Processing } + Route.Processing,
            )
        }
    }

    fun acknowledgeJob() {
        container.engine.acknowledgeTerminalState()
    }

    /* ------------------------------------------------------------------ AI Upscale Mode */

    /** Home's third mode card, and the Settings row, both land here. */
    fun setAiMode(on: Boolean) {
        if (_state.value.aiMode == on) return
        haptic(HapticKind.Light)
        update { it.copy(aiMode = on) }
    }

    /** The dashboard: keys, usage, limits. */
    fun openAiKeys() = push(Route.AiKeys)

    /** Opens a provider's key page in the browser. Used by the AI setup screen. */
    fun openLink(url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("No browser available") }
    }

    fun updateAiPrefs(block: (AiPrefs) -> AiPrefs) {
        val next = block(_state.value.aiPrefs)
        if (next == _state.value.aiPrefs) return
        aiKeyStore.savePrefs(next)
        update { it.copy(aiPrefs = next) }
    }

    private fun saveAiKeys(next: List<AiKeyRecord>) {
        aiKeyStore.saveKeys(next)
        update { it.copy(aiKeys = next) }
    }

    /** Keys that can be used right now (enabled and under their monthly limit). */
    fun aiUsableKeys(): List<AiKeyRecord> = _state.value.aiActiveKeys

    /**
     * Saves a key. The format is checked locally — never by making a paid call — so a typo is caught
     * before the user waits for a 401.
     */
    fun addAiKey(
        providerId: String,
        label: String,
        key: String,
        baseUrl: String? = null,
        modelId: String? = null,
        monthlyLimit: Int = 0,
    ): Boolean {
        val trimmed = key.trim()
        val provider = AiCatalog.provider(providerId)
        if (trimmed.length < 8) {
            toast("That key looks too short")
            return false
        }
        if (provider.keyPrefix != null && !trimmed.startsWith(provider.keyPrefix)) {
            toast("${provider.name} keys start with ${provider.keyPrefix}")
            return false
        }
        if (provider.allowsCustomBaseUrl && !baseUrl.orEmpty().startsWith("http")) {
            toast("Add the gateway base URL (https://…)")
            return false
        }
        if (provider.allowsCustomModel && modelId.isNullOrBlank()) {
            toast("Add the model id your gateway expects")
            return false
        }
        val record = AiKeyRecord(
            id = "k" + System.currentTimeMillis().toString(36) + (100..999).random(),
            providerId = providerId,
            label = label.trim().ifEmpty { provider.name },
            key = trimmed,
            baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() },
            modelId = modelId?.trim()?.takeIf { it.isNotEmpty() },
            monthlyLimit = monthlyLimit.coerceAtLeast(0),
            used = 0,
            usedMonth = AiKeyStore.currentMonth(),
            enabled = true,
            createdAt = System.currentTimeMillis(),
        )
        saveAiKeys(_state.value.aiKeys + record)
        haptic(HapticKind.Success)
        toast("${record.label} added")
        return true
    }

    fun updateAiKey(id: String, block: (AiKeyRecord) -> AiKeyRecord) {
        saveAiKeys(_state.value.aiKeys.map { if (it.id == id) block(it) else it })
    }

    fun setAiKeyEnabled(id: String, enabled: Boolean) {
        updateAiKey(id) { it.copy(enabled = enabled) }
        haptic(HapticKind.Light)
    }

    fun resetAiKeyUsage(id: String) {
        updateAiKey(id) { it.copy(used = 0, usedMonth = AiKeyStore.currentMonth()) }
        toast("Usage reset")
    }

    fun deleteAiKey(id: String) {
        val removed = _state.value.aiKeys.firstOrNull { it.id == id }
        saveAiKeys(_state.value.aiKeys.filterNot { it.id == id })
        if (_state.value.aiPrefs.keyId == id) updateAiPrefs { it.copy(keyId = null) }
        removed?.let { toast("${it.label} removed") }
    }

    /** Total images used this month across every key. */
    fun aiUsedThisMonth(): Int = _state.value.aiKeys.sumOf { it.used }

    /** Every model the saved keys can reach, for the Manual picker. */
    fun aiAvailableModels(): List<AiModel> {
        val providers = _state.value.aiActiveKeys.map { it.providerId }.toSet()
        val catalog = AiCatalog.modelsForProviders(providers)
        val custom = _state.value.aiActiveKeys
            .filter { it.providerId == "custom" }
            .map { customModelFor(it) }
        return catalog + custom
    }

    private fun customModelFor(key: AiKeyRecord): AiModel = AiModel(
        id = "custom:" + (key.modelId ?: key.id),
        providerId = "custom",
        label = key.modelId ?: "Custom model",
        desc = "Your own endpoint, speaking the OpenAI image-edits dialect.",
        scales = listOf(2, 4),
        maxInputMP = 4,
        approxSec = 30,
        badge = "Custom",
        requestId = key.modelId ?: "",
    )

    /**
     * Which key and model this job will use.
     *
     * Default level is *random but stable*: the choice is seeded by the photo id, so the screen the
     * user is looking at does not change its mind on every recomposition, while two different photos
     * genuinely load-balance across the keys and models that are available.
     */
    fun aiTarget(photoId: String? = null): AiTarget? {
        val state = _state.value
        val active = state.aiActiveKeys
        if (active.isEmpty()) return null
        val prefs = state.aiPrefs
        val pinned = prefs.keyId?.let { id -> active.firstOrNull { it.id == id } }
        val wantedModel = if (prefs.modelId != "auto") AiCatalog.model(prefs.modelId) else null

        /*
         * The key and the model have to belong to the same provider, so a pinned key loses to an
         * explicitly chosen model, and an explicitly chosen model pulls in a key from its provider.
         */
        val key = pinned?.takeIf { wantedModel == null || it.providerId == wantedModel.providerId }
            ?: wantedModel?.let { model -> active.firstOrNull { it.providerId == model.providerId } }
            ?: pinned
            ?: if (photoId != null) {
                active[(photoId.hashCode().toLong().let { if (it < 0) -it else it } % active.size).toInt()]
            } else {
                active.first()
            }

        val models = if (key.providerId == "custom") listOf(customModelFor(key)) else AiCatalog.modelsFor(key.providerId)
        if (models.isEmpty()) return null
        val model = when {
            wantedModel != null -> models.firstOrNull { it.id == wantedModel.id } ?: models.first()
            else -> {
                // Default level: a stable pseudo-random model per photo (and per key when there is none).
                val seed = (photoId ?: key.id).hashCode().toLong().let { if (it < 0) -it else it }
                models[((seed / 7) % models.size).toInt()]
            }
        }
        val scale = if (prefs.scale in model.scales) prefs.scale else model.scales.last()
        return AiTarget(key = key, model = model, provider = AiCatalog.provider(key.providerId), scale = scale)
    }

    /**
     * The AI icon on the configuration screen: no key yet means the setup screen, a key means the AI
     * configuration screen. Exactly the two cases from the request.
     */
    fun openAiUpscale(photoId: String) {
        haptic(HapticKind.Light)
        if (!_state.value.aiReady) {
            toast("Add an API key to use AI upscaling")
            push(Route.AiKeys)
            return
        }
        push(Route.AiConfig(photoId))
    }

    /**
     * Upload, reconstruct, download, save — the whole AI job.
     *
     * The state machine is deliberately small: one request, one answer. [AiJobUi.progress] follows
     * the real stages (prepare → upload → provider → download → save) instead of a spinner, and the
     * key's monthly counter is charged only after the provider actually returned an image.
     */
    fun startAiJob(photoId: String) {
        if (_state.value.aiJob?.status == JobStatus.Running) {
            toast("An AI upscale is already running")
            return
        }
        val photo = photoById(photoId)
        if (photo == null) {
            toast("That image is no longer available")
            return
        }
        val target = aiTarget(photoId)
        if (target == null) {
            toast("Add an API key to use AI upscaling")
            push(Route.AiKeys)
            return
        }
        val prefs = _state.value.aiPrefs
        val settings = _state.value.settings
        val scale = target.scale
        val prompt = AiCatalog.promptFor(prefs.prompt)
        val format = prefs.format.lowercase().let { if (it == "jpeg") "jpg" else it }
        val started = SystemClock.elapsedRealtime()

        aiJobStartedAt = started
        update { state ->
            state.copy(
                aiMode = true,
                aiJob = AiJobUi(
                    photoId = photo.id,
                    modelId = target.model.id,
                    modelLabel = target.model.label,
                    providerName = target.provider.name,
                    keyLabel = target.key.label,
                    scale = scale,
                    stageLabel = "Preparing image",
                    progress = 0.04f,
                    status = JobStatus.Running,
                    etaSec = target.model.approxSec,
                ),
                routes = state.routes.filterNot { it is Route.AiProcessing } + Route.AiProcessing,
            )
        }
        haptic(HapticKind.Medium)
        startAiTicker()

        aiJobTask?.cancel()
        aiJobTask = scope.launch {
            try {
                val payload = withContext(Dispatchers.IO) {
                    AiImagePrep.prepare(context, Uri.parse(photo.uri), minOf(prefs.maxInputMP, target.model.maxInputMP))
                }
                aiStage("Sending to ${target.provider.name}…", 0.16f)
                val creationsDir = File(context.filesDir, "creations").apply { mkdirs() }
                val stamp = System.currentTimeMillis()
                val beforeFile = withContext(Dispatchers.IO) {
                    AiImagePrep.saveBeforeCopy(payload, creationsDir, "ai_before_$stamp.jpg")
                }
                val raw = AiImageClient.generate(
                    AiRequest(
                        key = target.key,
                        provider = target.provider,
                        model = target.model,
                        imageBytes = payload.bytes,
                        mime = payload.mime,
                        prompt = prompt,
                        scale = scale,
                        format = format,
                        faceEnhance = prefs.faceEnhance,
                        strength = prefs.strength,
                    ),
                ) { line -> aiStage(line, null) }

                aiStage("Finishing and saving…", 0.88f)
                val output = withContext(Dispatchers.IO) {
                    val bitmap = AiImagePrep.decodeResult(raw.bytes)
                    val bytes = if (bitmap != null) {
                        val fitted = AiImagePrep.fit(bitmap, payload.width * scale, payload.height * scale)
                        AiImagePrep.encode(fitted, format).also { if (!fitted.isRecycled) fitted.recycle() }
                    } else {
                        // Too large to decode: keep the provider's bytes exactly as they came.
                        raw.bytes
                    }
                    val file = File(creationsDir, "ai_result_$stamp.$format")
                    file.outputStream().use { it.write(bytes) }
                    payload.bitmap?.let { if (!it.isRecycled) it.recycle() }
                    file
                }
                val (outW, outH) = withContext(Dispatchers.IO) { AiImagePrep.boundsOf(output) }
                val galleryName = OutputNameFormatter.buildGalleryFileName(photo.name, scale, format)
                val savedUri = withContext(Dispatchers.IO) {
                    MediaExportHelper.saveToGalleryOrFolder(context, output, settings.saveFolderUri, galleryName)
                        .getOrNull()?.toString()
                }
                val elapsedMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(1L)
                val inserted = withContext(Dispatchers.IO) {
                    container.database.recentCreationDao().insert(
                        RecentCreationEntity(
                            sourceUri = photo.uri,
                            beforeImagePath = beforeFile.absolutePath,
                            outputPath = output.absolutePath,
                            savedUri = savedUri,
                            presetId = "AI",
                            presetLabel = "${target.model.label} · ${scale}×",
                            modelId = "ai:${target.model.id}",
                            scale = scale,
                            inputWidth = payload.width,
                            inputHeight = payload.height,
                            outputWidth = outW,
                            outputHeight = outH,
                            durationMs = elapsedMs,
                            createdAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
                chargeAiKey(target.key.id)
                val finishedId = inserted.toString()
                val elapsed = (elapsedMs / 1000).toInt().coerceAtLeast(1)
                aiTickJob?.cancel()
                update { state ->
                    state.copy(
                        aiJob = state.aiJob?.copy(
                            status = JobStatus.Done,
                            progress = 1f,
                            stageLabel = "Saved",
                            elapsedSec = elapsed,
                            etaSec = 0,
                            resultId = finishedId,
                        ),
                        lastResultId = finishedId,
                        routes = if (state.phase == Phase.App) {
                            state.routes.filterNot {
                                it is Route.AiProcessing || it is Route.Result || it is Route.ResultFull
                            } + Route.Result(finishedId)
                        } else {
                            state.routes
                        },
                    )
                }
                haptic(HapticKind.Success)
                toast("AI upscale saved")
                com.tumuyan.ncnn.realsr.widget.RealScaleWidgetsUpdater.refreshSoon(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                aiTickJob?.cancel()
                update { state ->
                    state.copy(
                        aiJob = state.aiJob?.copy(status = JobStatus.Cancelled, stageLabel = "Cancelled"),
                        routes = state.routes.filterNot { it is Route.AiProcessing },
                    )
                }
                throw e
            } catch (e: Throwable) {
                aiTickJob?.cancel()
                val message = (e as? AiClientException)?.message
                    ?: e.message
                    ?: "AI upscale failed. Try another model or key."
                update { state ->
                    state.copy(aiJob = state.aiJob?.copy(status = JobStatus.Failed, stageLabel = "Failed", error = message))
                }
                haptic(HapticKind.Warning)
                toast(message)
            }
        }
    }

    private fun aiStage(label: String, progress: Float?) {
        update { state ->
            val job = state.aiJob ?: return@update state
            state.copy(aiJob = job.copy(stageLabel = label, progress = progress ?: job.progress))
        }
    }

    /** Counts the elapsed seconds; the ETA is the model's own estimate minus what already ran. */
    private fun startAiTicker() {
        aiTickJob?.cancel()
        aiTickJob = scope.launch {
            while (true) {
                delay(500)
                val job = _state.value.aiJob ?: break
                if (job.status != JobStatus.Running) break
                val elapsed = ((SystemClock.elapsedRealtime() - aiJobStartedAt) / 1000).toInt()
                update { state ->
                    val current = state.aiJob ?: return@update state
                    // The ticker runs twice a second; the ETA only moves when the second does.
                    val eta = if (elapsed > current.elapsedSec) {
                        (current.etaSec - (elapsed - current.elapsedSec)).coerceAtLeast(0)
                    } else {
                        current.etaSec
                    }
                    state.copy(aiJob = current.copy(elapsedSec = elapsed, etaSec = eta))
                }
            }
        }
    }

    private fun chargeAiKey(id: String) {
        updateAiKey(id) { key -> key.copy(used = key.used + 1, usedMonth = AiKeyStore.currentMonth()) }
    }

    fun cancelAiJob() {
        aiJobTask?.cancel()
        aiJobTask = null
        aiTickJob?.cancel()
        update { state ->
            state.copy(
                aiJob = state.aiJob?.copy(status = JobStatus.Cancelled, stageLabel = "Cancelled"),
                routes = state.routes.filterNot { it is Route.AiProcessing },
            )
        }
        haptic(HapticKind.Warning)
    }

    fun clearAiJob() {
        aiTickJob?.cancel()
        update { it.copy(aiJob = null) }
    }

    /* ------------------------------------------------------------------ helpers */

    private fun update(block: (AppUiState) -> AppUiState) {
        val before = _state.value
        val after = block(before)
        if (after != before) {
            _state.value = after
            if (after.phase != before.phase || after.routes != before.routes || after.settings != before.settings ||
                after.prefs != before.prefs || after.mode != before.mode || after.onboarded != before.onboarded ||
                after.photoAccess != before.photoAccess || after.notificationsAsked != before.notificationsAsked ||
                after.cameraAsked != before.cameraAsked || after.superIntroSeen != before.superIntroSeen ||
                after.aiMode != before.aiMode
            ) {
                persist()
            }
        }
    }

    fun suggestedFileName(photo: PhotoItem, plan: Plan): String =
        com.tumuyan.ncnn.realsr.core.plan.resultFileName(photo.name, plan.scale, plan.ext, Calendar.getInstance())

    fun enginePreferences(): EnginePreferences = container.engine.preferences.value

    companion object {
        private const val KEY_STATE = "state_v1"

        /** Where the feedback form sends what the user wrote. */
        const val FEEDBACK_EMAIL = "userrohitai@gmail.com"

        /** The theme the user picked in Settings, readable by entry points that have no store. */
        fun persistedTheme(context: Context): ThemePref {
            val raw = context
                .getSharedPreferences("realscale_state", Context.MODE_PRIVATE)
                .getString(KEY_STATE, null) ?: return ThemePref.System
            return runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString(PersistedState.serializer(), raw)
                    .settings
                    .theme
            }.getOrDefault(ThemePref.System)
        }
    }
}

/** Implemented by the activity: every entry point that leaves the app's own screens. */
interface MediaIntents {
    /** System photo picker (single image, no permission needed). */
    fun pickDocument()

    /** System document browser: every folder, SD card and cloud provider, filtered to images. */
    fun pickFromFiles()

    fun takePhoto()

    /** Opens the system share sheet for [uri]; false when nothing can receive it. */
    fun share(uri: Uri, name: String, mime: String): Boolean

    /** Returns true when a bitmap was found on the clipboard and imported. */
    fun pasteImage(): Boolean

    /** System folder picker for Settings → Output → Save folder (SAF tree). */
    fun pickSaveFolder()

    /** Opens the user's mail app with the feedback text ready to send. False when none exists. */
    fun sendFeedbackMail(subject: String, body: String): Boolean
}

@kotlinx.serialization.Serializable
data class PersistedState(
    val phase: Phase = Phase.Splash,
    val routes: List<Route> = listOf(Route.Home),
    val mode: ModeId = ModeId.UPSCALE,
    val onboarded: Boolean = false,
    val photoAccess: Boolean = false,
    val notificationsAsked: Boolean = false,
    val cameraAsked: Boolean = false,
    val superIntroSeen: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val prefs: Map<ModeId, JobPrefs> = emptyMap(),
    /** Round 5: is Home on the AI Upscale mode card? */
    val aiMode: Boolean = false,
)
