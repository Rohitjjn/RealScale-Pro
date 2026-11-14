package com.tumuyan.ncnn.realsr.ui.state

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.device.DeviceProfile
import com.tumuyan.ncnn.realsr.core.device.DeviceProfiler
import com.tumuyan.ncnn.realsr.core.device.HapticKind
import com.tumuyan.ncnn.realsr.core.device.Haptics
import com.tumuyan.ncnn.realsr.core.engine.EnginePreferences
import com.tumuyan.ncnn.realsr.core.engine.ImageIntakeManager
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.ExecutionBackend
import com.tumuyan.ncnn.realsr.core.engine.ModelExecutionSpec
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.engine.UpscaleRequest
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.core.plan.JobPrefs
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.ModelCatalog
import com.tumuyan.ncnn.realsr.core.plan.PipelinePlanner
import com.tumuyan.ncnn.realsr.core.plan.Plan
import com.tumuyan.ncnn.realsr.core.plan.PlanInput
import com.tumuyan.ncnn.realsr.core.plan.PlanLevel
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import com.tumuyan.ncnn.realsr.ui.media.MediaLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var benchmarkSamples = mutableListOf<Pair<String, Double>>()

    init {
        restore()
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
        if (saved == null) {
            _state.value = base.copy(phase = Phase.Splash)
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
        update { it.copy(phase = Phase.App, onboarded = true, routes = listOf(Route.Home)) }
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
        update { it }
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

    private fun mergeImported(current: List<PhotoItem>, loaded: List<PhotoItem>): List<PhotoItem> {
        val importedOnly = current.filter { it.imported }
        val byId = (importedOnly + loaded).associateBy { it.id }
        return byId.values.sortedByDescending { it.id }
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

    fun takePhoto() {
        if (mediaIntents == null) toast("No camera available") else mediaIntents?.takePhoto()
    }

    fun pasteFromClipboard() {
        val pasted = mediaIntents?.pasteImage() ?: false
        if (!pasted) toast("No image on the clipboard")
    }

    fun onImportedPhoto(photo: PhotoItem?) {
        if (photo == null) {
            toast("That image could not be opened")
            return
        }
        addImportedPhoto(photo)
        toast("${photo.name} added")
        push(Route.Config(photo.id))
    }

    /* ------------------------------------------------------------------ results */

    private fun observeResults() {
        resultsJob = scope.launch {
            container.database.recentCreationDao().observeRecent(200).collect { rows ->
                update { it.copy(results = rows.map(::toResultItem)) }
            }
        }
    }

    private fun toResultItem(row: RecentCreationEntity): ResultItem {
        val model = ModelCatalog.byId(row.modelId)
        return ResultItem(
            id = row.id.toString(),
            photoId = row.sourceUri,
            sourceUri = row.sourceUri,
            outputPath = row.outputPath,
            savedUri = row.savedUri,
            mode = if (row.presetId == "MAXIMUM") ModeId.SUPER else ModeId.UPSCALE,
            scale = row.scale,
            modelId = row.modelId,
            modelName = model.name,
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

    private fun observeCatalog() {
        scope.launch {
            container.modelRepository.catalogModels.collect { states ->
                // The repository speaks manifest ids (HF directories); the UI speaks catalog ids.
                val installed = buildList {
                    addAll(ModelCatalog.BUNDLED_IDS)
                    states.filter { it.isInstalled }.forEach { state ->
                        ModelCatalog.byManifestId(state.catalog.id)?.let { add(it.id) }
                    }
                }.distinct()
                val downloads = states
                    .filter { it.status.name != "INSTALLED" }
                    .mapNotNull { state ->
                        val entry = ModelCatalog.byManifestId(state.catalog.id) ?: return@mapNotNull null
                        entry.id to ModelDownloadUi(
                            modelId = entry.id,
                            progress = state.progressFraction,
                            state = state.status.name.lowercase(),
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

    fun downloadModel(modelId: String) {
        val entry = ModelCatalog.byId(modelId)
        val manifestId = entry.manifestId
        if (manifestId == null) {
            toast(
                if (entry.blocked != null) "${entry.name} is not downloadable yet"
                else "${entry.name} ships with the app",
            )
            return
        }
        scope.launch {
            container.modelRepository.startOrResumeDownload(manifestId, wifiOnly = _state.value.settings.wifiOnly)
            toast("Downloading ${entry.name}")
        }
    }

    fun pauseModel(modelId: String) {
        val manifestId = manifestIdFor(modelId) ?: return
        scope.launch {
            container.modelRepository.pauseDownload(manifestId)
            toast("Download paused")
        }
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

    /** Feedback is stored locally; there is no backend and no upload. */
    fun saveFeedback(text: String, attachDevice: Boolean) {
        val trimmed = text.trim()
        if (trimmed.length < 3) return
        val dir = File(context.filesDir, "feedback").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val body = buildString {
            append(trimmed).append("\n\n---\n")
            if (attachDevice) {
                append("device: ${device.label}\n")
                append("ramGB: ${device.ramGB}\ncores: ${device.cores}\ngpu: ${device.gpu}\n")
            }
            append("version: 2.0\n")
        }
        runCatching { File(dir, "feedback-$stamp.txt").writeText(body) }
        toast("Thanks. Your feedback was saved to send.")
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
                update { state ->
                    val job = state.job
                    if (job == null) {
                        state
                    } else {
                        state.copy(
                            job = job.copy(
                                progress = 1f,
                                status = JobStatus.Done,
                                resultId = first?.id?.toString(),
                                elapsedSec = elapsed,
                                remainingSec = 0,
                                stageLabel = "Saved",
                            ),
                        )
                    }
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
     * Starts the job described by [plan]. The command itself is built by the untouched engine layer
     * (`PresetCommandResolver` + upstream `CommandBuilder`); this only translates the plan into the
     * engine's request shape.
     */
    fun startJob(photo: PhotoItem, plan: Plan, mode: ModeId = _state.value.mode) {
        val settings = _state.value.settings
        val entry = plan.model
        val modelDir = File(container.modelRepository.modelsRootDir, entry.manifestId ?: entry.id)
        val modelPath = entry.entryFile?.let { File(modelDir, it).absolutePath } ?: modelDir.absolutePath

        val spec = ModelExecutionSpec(
            modelId = entry.id,
            displayName = entry.name,
            engineTool = entry.engineTool,
            modelPath = modelPath,
            scale = plan.scale,
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
                notifyOnCompletion = settings.haptics && _state.value.notificationsAsked,
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

    fun acknowledgeJob() {
        container.engine.acknowledgeTerminalState()
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
                after.cameraAsked != before.cameraAsked || after.superIntroSeen != before.superIntroSeen
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
    fun pickDocument()
    fun takePhoto()

    /** Opens the system share sheet for [uri]; false when nothing can receive it. */
    fun share(uri: Uri, name: String, mime: String): Boolean

    /** Returns true when a bitmap was found on the clipboard and imported. */
    fun pasteImage(): Boolean
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
)
