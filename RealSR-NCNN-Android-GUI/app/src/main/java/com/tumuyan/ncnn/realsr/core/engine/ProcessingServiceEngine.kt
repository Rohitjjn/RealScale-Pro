package com.tumuyan.ncnn.realsr.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import com.tumuyan.ncnn.realsr.ImageProcessor
import com.tumuyan.ncnn.realsr.ProcessingService
import com.tumuyan.ncnn.realsr.ProgressLogHelper
import com.tumuyan.ncnn.realsr.core.database.RecentCreationDao
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.ui.MediaExportHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class ProcessingServiceEngine(
    context: Context,
    private val recentCreationDao: RecentCreationDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UpscaleEngine {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val prefsStore = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val jobQueue = EngineJobQueue()

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _consoleLog = MutableStateFlow("")
    override val consoleLog: StateFlow<String> = _consoleLog.asStateFlow()

    private val _preferences = MutableStateFlow(loadPreferences())
    override val preferences: StateFlow<EnginePreferences> = _preferences.asStateFlow()

    private val _queue = MutableStateFlow<List<QueuedJobItem>>(emptyList())
    override val queue: StateFlow<List<QueuedJobItem>> = _queue.asStateFlow()

    @Volatile
    private var boundService: ProcessingService? = null

    @Volatile
    private var serviceDeferred = CompletableDeferred<ProcessingService>()

    @Volatile
    private var isBound = false

    private var workerJob: Job? = null

    @Volatile
    private var cancelCurrentItemOnly: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? ProcessingService.LocalBinder)?.service
            if (service != null) {
                service.setCancelListener {
                    cancel()
                }
                boundService = service
                isBound = true
                if (!serviceDeferred.isCompleted) {
                    serviceDeferred.complete(service)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
            serviceDeferred = CompletableDeferred()
        }
    }

    val modelsRootDir: File
        get() = File(appContext.filesDir, "models").apply { mkdirs() }

    private val creationsDir: File
        get() = File(appContext.filesDir, "creations").apply { mkdirs() }

    override suspend fun ensureInitialized(forceReset: Boolean): Result<File> =
        withContext(ioDispatcher) {
            runCatching {
                EngineAssetExtractor.ensureExtracted(appContext, forceReset = forceReset)
            }
        }

    override fun updatePreferences(transform: (EnginePreferences) -> EnginePreferences) {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        prefsStore.edit()
            .putString(KEY_BACKEND, updated.backend.name)
            .putInt(KEY_TILE_SIZE, updated.tileSize)
            .putString(KEY_THREADS, updated.threadCount)
            .putString(KEY_OUTPUT_FORMAT, updated.outputFormat.name)
            .putString(KEY_OUTPUT_TREE_URI, updated.outputTreeUri)
            .putBoolean(KEY_HIDE_OUTPUT, updated.hideProgramOutput)
            .putString(KEY_EXTERNAL_OPEN_MODE, updated.externalOpenMode.name)
            .putString(KEY_LAST_PRESET, updated.lastUsedPresetId.name)
            .putInt(KEY_LAST_SCALE, updated.lastUsedScale)
            .putString(KEY_DEFAULT_PRESET, updated.defaultPresetId?.name)
            .putBoolean(KEY_KEEP_SCREEN_ON, updated.keepScreenOnDuringEnhance)
            .putBoolean(KEY_HAPTICS_ENABLED, updated.hapticsEnabled)
            .putBoolean(KEY_WIFI_ONLY_DOWNLOADS, updated.wifiOnlyDownloads)
            .apply()
    }

    override fun startUpscale(request: UpscaleRequest) {
        if (request.inputUris.isEmpty()) return
        // Remember last-used preset & scale for Studio and Quick Sheet
        updatePreferences {
            it.copy(
                lastUsedPresetId = request.preset.id,
                lastUsedScale = request.scale
            )
        }
        jobQueue.enqueue(request) { idx, uri ->
            ImageIntakeManager.resolveDisplayName(appContext, uri) ?: "Photo ${idx + 1}"
        }
        refreshQueueSnapshot()
        ensureQueueWorkerRunning()
    }

    override fun startDirectoryUpscale(
        directoryUri: Uri,
        preset: UpscalePreset,
        scale: Int,
        modelSpec: ModelExecutionSpec?
    ) {
        scope.launch {
            val dir = DocumentFile.fromTreeUri(appContext, directoryUri)
            if (dir == null || !dir.isDirectory) {
                if (jobQueue.isIdle()) {
                    _state.value = EngineState.Failed(
                        userMessage = "Could not open the selected folder.",
                        technicalOutput = "Invalid tree URI: $directoryUri"
                    )
                }
                return@launch
            }
            val imageUris = dir.listFiles()
                .filter { file ->
                    file.isFile && (file.type?.startsWith("image/") == true || hasImageExtension(file.name))
                }
                .map { it.uri }

            if (imageUris.isEmpty()) {
                if (jobQueue.isIdle()) {
                    _state.value = EngineState.Failed(
                        userMessage = "No supported images were found in the selected folder.",
                        technicalOutput = "Empty directory scan: $directoryUri"
                    )
                }
                return@launch
            }
            startUpscale(
                UpscaleRequest(
                    inputUris = imageUris,
                    preset = preset,
                    scale = scale,
                    modelSpec = modelSpec,
                    autoSaveToGallery = true
                )
            )
        }
    }

    override fun runAdvancedCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return
        cancel()
        workerJob = scope.launch {
            val jobId = UUID.randomUUID().toString().take(8)
            _state.value = EngineState.Preparing("Preparing engine…")
            val realsrDir = ensureInitialized(forceReset = false).getOrElse { err ->
                _state.value = EngineState.Failed(
                    userMessage = "Engine initialization failed.",
                    technicalOutput = err.stackTraceToString()
                )
                return@launch
            }
            appendLog("$ $trimmed")
            val startMs = System.currentTimeMillis()
            _state.value = EngineState.Running(
                jobId = jobId,
                presetTitle = "Advanced Command",
                progressFraction = 0f,
                currentFileIndex = 1,
                totalFiles = 1,
                currentFileName = trimmed.substringBefore(' '),
                etaMillis = null,
                previewUri = null,
                latestStatusLine = "Running command",
                stage = ProcessingStage.ENHANCING,
                hasDeterminateProgress = false,
                elapsedMillis = 0L
            )
            try {
                val service = obtainForegroundService()
                val cmdResult = runServiceCommand(
                    service = service,
                    command = trimmed,
                    workingDir = realsrDir.absolutePath
                ) { line ->
                    if (!_preferences.value.hideProgramOutput) {
                        appendLog(line)
                    }
                    val pct = parsePercentage(line)
                    _state.update { prev ->
                        if (prev is EngineState.Running) {
                            prev.copy(
                                progressFraction = pct ?: prev.progressFraction,
                                hasDeterminateProgress = pct != null || prev.hasDeterminateProgress,
                                elapsedMillis = System.currentTimeMillis() - startMs,
                                latestStatusLine = "Running command"
                            )
                        } else {
                            prev
                        }
                    }
                }
                if (cmdResult.success) {
                    appendLog("Command completed.")
                    _state.value = EngineState.Idle
                } else {
                    _state.value = EngineState.Failed(
                        userMessage = "Command exited with an error.",
                        technicalOutput = cmdResult.output
                    )
                }
            } catch (_: CancellationException) {
                _state.value = EngineState.Cancelled
            } catch (t: Throwable) {
                _state.value = EngineState.Failed(
                    userMessage = t.message ?: "Command execution failed.",
                    technicalOutput = t.stackTraceToString()
                )
            }
        }
    }

    override fun cancelQueuedItem(itemId: String) {
        when (jobQueue.cancelItem(itemId)) {
            EngineJobQueue.CancelItemResult.REMOVED_PENDING -> {
                refreshQueueSnapshot()
            }
            EngineJobQueue.CancelItemResult.CANCEL_RUNNING -> {
                cancelCurrentItemOnly = true
                boundService?.cancelTask()
            }
            EngineJobQueue.CancelItemResult.NOT_FOUND -> Unit
        }
    }

    override fun requestCompletionNotificationForActiveSession() {
        jobQueue.enableSessionCompletionNotification()
    }

    override fun retryWithRecovery(action: RecoveryAction): Boolean {
        val failed = _state.value as? EngineState.Failed ?: return false
        val baseReq = failed.failedRequest ?: return false
        return when (action) {
            RecoveryAction.RETRY_ON_CPU -> {
                updatePreferences { it.copy(backend = ExecutionBackend.CPU) }
                startUpscale(baseReq)
                true
            }
            RecoveryAction.SMALLER_TILE_SIZE -> {
                updatePreferences { it.copy(tileSize = 64) }
                startUpscale(baseReq)
                true
            }
            RecoveryAction.TRY_INSTANT -> {
                val instantScale = if (baseReq.scale in UpscalePreset.INSTANT.supportedScales) {
                    baseReq.scale
                } else {
                    UpscalePreset.INSTANT.defaultScale
                }
                startUpscale(
                    baseReq.copy(
                        preset = UpscalePreset.INSTANT,
                        scale = instantScale,
                        modelSpec = null
                    )
                )
                true
            }
        }
    }

    override fun cancel() {
        jobQueue.clearAll()
        refreshQueueSnapshot()
        cancelCurrentItemOnly = false
        val job = workerJob
        workerJob = null
        boundService?.cancelTask()
        job?.cancel()
        runCatching {
            if (isBound) {
                appContext.unbindService(connection)
                isBound = false
            }
            boundService = null
            serviceDeferred = CompletableDeferred()
            appContext.startService(
                Intent(appContext, ProcessingService::class.java).apply {
                    action = ProcessingService.ACTION_STOP_TASK
                }
            )
        }
        appendLog("Cancelled by user.")
        _state.value = EngineState.Cancelled
    }

    override fun acknowledgeTerminalState() {
        val current = _state.value
        if (current is EngineState.Completed || current is EngineState.Failed || current is EngineState.Cancelled) {
            _state.value = EngineState.Idle
        }
    }

    @Synchronized
    private fun ensureQueueWorkerRunning() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            drainQueueSequentially()
        }
    }

    private suspend fun drainQueueSequentially() {
        val sessionId = UUID.randomUUID().toString().take(8)
        val sessionStartMs = System.currentTimeMillis()
        val sessionCreations = mutableListOf<RecentCreationEntity>()
        var lastFailureState: EngineState.Failed? = null

        try {
            _state.value = EngineState.Preparing("Preparing photo…")
            val realsrDir = ensureInitialized(forceReset = false).getOrElse { err ->
                jobQueue.clearAll()
                refreshQueueSnapshot()
                _state.value = EngineState.Failed(
                    userMessage = "Could not prepare the upscaling engine.",
                    technicalOutput = err.stackTraceToString()
                )
                return
            }

            val service = obtainForegroundService()

            while (true) {
                val notifyWhenDone = jobQueue.shouldNotifyOnSessionComplete()
                val lease = jobQueue.pollNextLease() ?: break
                refreshQueueSnapshot()

                val entry = lease.entry
                val pos = lease.positionInSession
                val total = lease.totalInSession
                val prefs = _preferences.value
                val itemStartMs = System.currentTimeMillis()
                cancelCurrentItemOnly = false

                val batchTitlePrefix = if (total > 1) "$pos of $total · " else ""
                val notifTitle = "${batchTitlePrefix}${entry.preset.title} ${entry.scale}×"

                // Stage 1: Preparing (copy stream + convert HEIC/AVIF/GIF if needed)
                _state.value = EngineState.Running(
                    jobId = sessionId,
                    presetTitle = "${entry.preset.title} · ${entry.scale}×",
                    progressFraction = ((pos - 1).toFloat() / total.toFloat()).coerceIn(0f, 0.99f),
                    currentFileIndex = pos,
                    totalFiles = total,
                    currentFileName = entry.fileNameHint,
                    etaMillis = null,
                    previewUri = entry.uri.toString(),
                    latestStatusLine = "Preparing",
                    stage = ProcessingStage.PREPARING,
                    hasDeterminateProgress = false,
                    elapsedMillis = 0L,
                    currentItemId = entry.id
                )
                service.updateStructuredProgress(notifTitle, "Preparing photo…", -1)

                val beforeFile = try {
                    ImageIntakeManager.prepareEngineInputFile(
                        context = appContext,
                        uri = entry.uri,
                        creationsDir = creationsDir,
                        filePrefix = "before_${itemStartMs}_${pos}",
                        realsrDir = realsrDir,
                        service = service
                    )
                } catch (e: UnsupportedImageFormatException) {
                    lastFailureState = EngineState.Failed(
                        userMessage = e.message ?: "Unsupported image format.",
                        technicalOutput = e.stackTraceToString(),
                        recoveryActions = emptyList(),
                        failedRequest = null
                    )
                    jobQueue.markActiveFinished()
                    refreshQueueSnapshot()
                    continue
                }

                val (inW, inH) = decodeDimensions(beforeFile)
                val outExt = prefs.outputFormat.extension
                val outputFile = File(
                    creationsDir,
                    "RealScale_${itemStartMs}_${pos}_${entry.preset.id.name.lowercase(Locale.US)}_${entry.scale}x.$outExt"
                )

                val command = PresetCommandResolver.buildUpscaleCommand(
                    preset = entry.preset,
                    scale = entry.scale,
                    inputPath = beforeFile.absolutePath,
                    outputPath = outputFile.absolutePath,
                    preferences = prefs,
                    modelOverride = entry.modelSpec,
                    modelsRootDir = modelsRootDir
                )

                if (!prefs.hideProgramOutput) {
                    appendLog("$ $command")
                }

                // Stage 2: Enhancing (with 1s elapsed-time ticker for indeterminate tools like Instant)
                val enhanceStartMs = System.currentTimeMillis()
                _state.value = EngineState.Running(
                    jobId = sessionId,
                    presetTitle = "${entry.preset.title} · ${entry.scale}×",
                    progressFraction = ((pos - 1).toFloat() / total.toFloat()).coerceIn(0f, 0.99f),
                    currentFileIndex = pos,
                    totalFiles = total,
                    currentFileName = entry.fileNameHint,
                    etaMillis = null,
                    previewUri = beforeFile.toURI().toString(),
                    latestStatusLine = "Enhancing",
                    stage = ProcessingStage.ENHANCING,
                    hasDeterminateProgress = false,
                    elapsedMillis = 0L,
                    currentItemId = entry.id
                )
                service.updateStructuredProgress(notifTitle, "Enhancing…", -1)

                val tickerJob = scope.launch {
                    while (isActive) {
                        delay(500L)
                        val elapsed = System.currentTimeMillis() - enhanceStartMs
                        _state.update { prev ->
                            if (prev is EngineState.Running && prev.currentItemId == entry.id) {
                                prev.copy(elapsedMillis = elapsed)
                            } else {
                                prev
                            }
                        }
                        val cur = _state.value
                        if (cur is EngineState.Running && cur.currentItemId == entry.id && !cur.hasDeterminateProgress) {
                            val sec = (elapsed / 1000L).coerceAtLeast(1L)
                            service.updateStructuredProgress(notifTitle, "Enhancing · ${sec}s", -1)
                        }
                    }
                }

                var smoothedRatePctPerMs: Double? = null
                var lastPctSample: Pair<Long, Float>? = null

                val cmdResult = try {
                    runServiceCommand(
                        service = service,
                        command = command,
                        workingDir = realsrDir.absolutePath
                    ) { line ->
                        if (!prefs.hideProgramOutput) {
                            appendLog(line)
                        }
                        val parsed = EngineProgressParser.parseLine(line)
                        val filePct = parsed?.fraction
                        if (filePct != null && filePct > 0f) {
                            val now = System.currentTimeMillis()
                            val prevSample = lastPctSample
                            if (prevSample != null && filePct > prevSample.second && now > prevSample.first) {
                                val instRate = (filePct - prevSample.second).toDouble() / (now - prevSample.first).toDouble()
                                smoothedRatePctPerMs = smoothedRatePctPerMs?.let { 0.7 * it + 0.3 * instRate } ?: instRate
                            } else if (prevSample == null && now > enhanceStartMs) {
                                smoothedRatePctPerMs = filePct.toDouble() / (now - enhanceStartMs).coerceAtLeast(1L).toDouble()
                            }
                            lastPctSample = now to filePct

                            val overallFraction = (((pos - 1).toFloat() + filePct) / total.toFloat()).coerceIn(0f, 0.99f)
                            val remainingItemsFraction = (total - pos).toFloat() + (1f - filePct)
                            val velocityEtaMs = smoothedRatePctPerMs?.takeIf { it > 1e-7 }?.let { rate ->
                                (remainingItemsFraction / rate).toLong().coerceAtLeast(500L)
                            }
                            val etaMs = when {
                                parsed.nativeEtaMillis != null && total == 1 -> parsed.nativeEtaMillis.coerceAtLeast(500L)
                                parsed.nativeEtaMillis != null && velocityEtaMs != null ->
                                    ((parsed.nativeEtaMillis + velocityEtaMs) / 2L).coerceAtLeast(500L)
                                else -> velocityEtaMs
                            }
                            val pctInt = (overallFraction * 100f).roundToInt().coerceIn(1, 99)

                            _state.update { prev ->
                                if (prev is EngineState.Running && prev.currentItemId == entry.id) {
                                    prev.copy(
                                        progressFraction = overallFraction,
                                        hasDeterminateProgress = true,
                                        etaMillis = etaMs ?: prev.etaMillis,
                                        elapsedMillis = now - enhanceStartMs,
                                        stage = ProcessingStage.ENHANCING,
                                        latestStatusLine = "Enhancing"
                                    )
                                } else {
                                    prev
                                }
                            }
                            service.updateStructuredProgress(notifTitle, "Enhancing · $pctInt%", pctInt)
                        }
                    }
                } finally {
                    tickerJob.cancel()
                }

                if (cancelCurrentItemOnly) {
                    beforeFile.delete()
                    outputFile.delete()
                    cancelCurrentItemOnly = false
                    jobQueue.markActiveFinished()
                    refreshQueueSnapshot()
                    continue
                }

                if (!cmdResult.success || !outputFile.exists() || outputFile.length() == 0L) {
                    beforeFile.delete()
                    outputFile.delete()
                    val retryReq = UpscaleRequest(
                        inputUris = listOf(entry.uri),
                        preset = entry.preset,
                        scale = entry.scale,
                        modelSpec = entry.modelSpec,
                        autoSaveToGallery = entry.autoSaveToGallery,
                        notifyOnCompletion = entry.notifyOnCompletion
                    )
                    val recovery = buildRecoveryActions(cmdResult.output, prefs, entry.preset)
                    val techReport = buildString {
                        appendLine("Command: $command")
                        appendLine("Preset: ${entry.preset.id} (${entry.scale}x)")
                        appendLine("Backend: ${prefs.backend} · TileSize: ${prefs.tileSize}")
                        appendLine("Device ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                        appendLine("--- Native Output ---")
                        append(cmdResult.output.ifBlank { _consoleLog.value.takeLast(2_000) })
                    }
                    lastFailureState = EngineState.Failed(
                        userMessage = buildFriendlyFailureMessage(cmdResult.output, entry.preset),
                        technicalOutput = techReport,
                        recoveryActions = recovery,
                        failedRequest = retryReq
                    )
                    jobQueue.markActiveFinished()
                    refreshQueueSnapshot()
                    continue
                }

                // Stage 3: Saving (write to Pictures/RealScale-Pro with <originalName>_x2/_x4)
                _state.update { prev ->
                    if (prev is EngineState.Running && prev.currentItemId == entry.id) {
                        prev.copy(
                            stage = ProcessingStage.SAVING,
                            latestStatusLine = "Saving"
                        )
                    } else {
                        prev
                    }
                }
                service.updateStructuredProgress(notifTitle, "Saving to Gallery…", 99)

                val galleryFileName = OutputNameFormatter.buildGalleryFileName(
                    originalDisplayName = entry.fileNameHint,
                    scale = entry.scale,
                    outputExtension = outExt
                )
                val savedUriString = if (entry.autoSaveToGallery) {
                    MediaExportHelper.saveToGalleryOrFolder(
                        context = appContext,
                        sourceFile = outputFile,
                        customTreeUri = prefs.outputTreeUri,
                        displayFileName = galleryFileName
                    ).getOrNull()?.toString()
                } else {
                    null
                }

                val (outW, outH) = decodeDimensions(outputFile)
                val fileDuration = (System.currentTimeMillis() - itemStartMs).coerceAtLeast(1L)
                val modelIdUsed = entry.modelSpec?.modelId ?: entry.preset.defaultModelId
                val creation = RecentCreationEntity(
                    sourceUri = entry.uri.toString(),
                    beforeImagePath = beforeFile.absolutePath,
                    outputPath = outputFile.absolutePath,
                    savedUri = savedUriString,
                    presetId = entry.preset.id.name,
                    presetLabel = "${entry.preset.title} · ${entry.scale}×",
                    modelId = modelIdUsed,
                    scale = entry.scale,
                    inputWidth = inW,
                    inputHeight = inH,
                    outputWidth = outW,
                    outputHeight = outH,
                    durationMs = fileDuration,
                    createdAtEpochMs = System.currentTimeMillis()
                )
                val insertedId = recentCreationDao.insert(creation)
                val savedEntity = creation.copy(id = insertedId)
                sessionCreations += savedEntity
                com.tumuyan.ncnn.realsr.widget.RealScaleWidgetsUpdater.refreshAll(appContext)

                val shouldPostNotif = notifyWhenDone || jobQueue.shouldNotifyOnSessionComplete()
                jobQueue.markActiveFinished()
                refreshQueueSnapshot()

                if (shouldPostNotif && jobQueue.isIdle()) {
                    val summary = if (sessionCreations.size == 1) {
                        "${savedEntity.outputWidth} × ${savedEntity.outputHeight} · Saved to Pictures/RealScale-Pro"
                    } else {
                        "${sessionCreations.size} photos enhanced · Saved to Pictures/RealScale-Pro"
                    }
                    service.postCompletionNotification(
                        savedEntity.id,
                        "Photo ready · ${entry.preset.title} ${entry.scale}×",
                        summary,
                        outputFile
                    )
                }
            }

            refreshQueueSnapshot()
            if (sessionCreations.isNotEmpty()) {
                val totalDuration = (System.currentTimeMillis() - sessionStartMs).coerceAtLeast(1L)
                _state.value = EngineState.Completed(
                    UpscaleResult(
                        creations = sessionCreations,
                        totalDurationMs = totalDuration
                    )
                )
            } else if (lastFailureState != null) {
                _state.value = lastFailureState!!
            } else {
                _state.value = EngineState.Cancelled
            }
        } catch (_: CancellationException) {
            _state.value = EngineState.Cancelled
        } catch (t: Throwable) {
            jobQueue.clearAll()
            refreshQueueSnapshot()
            _state.value = EngineState.Failed(
                userMessage = "Upscaling stopped unexpectedly. Try switching to CPU or smaller tiles in Settings.",
                technicalOutput = t.stackTraceToString()
            )
        }
    }

    private fun refreshQueueSnapshot() {
        _queue.value = jobQueue.snapshot()
    }

    private suspend fun obtainForegroundService(): ProcessingService {
        boundService?.let { return it }
        val intent = Intent(appContext, ProcessingService::class.java)
        appContext.startService(intent)
        if (!isBound) {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        return withTimeout(8_000L) {
            serviceDeferred.await()
        }
    }

    private data class ServiceCommandResult(
        val success: Boolean,
        val output: String
    )

    private suspend fun runServiceCommand(
        service: ProcessingService,
        command: String,
        workingDir: String,
        onLine: (String) -> Unit
    ): ServiceCommandResult = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            service.cancelTask()
        }
        service.startTask(
            command,
            workingDir,
            NOTIFY_DETAILED_AUTO_DISMISS,
            object : ImageProcessor.ProcessCallback {
                override fun onProgress(line: String?) {
                    if (!line.isNullOrBlank()) {
                        onLine(line)
                    }
                }

                override fun onCompleted(result: String?, success: Boolean) {
                    if (cont.isActive) {
                        cont.resume(ServiceCommandResult(success = success, output = result.orEmpty()))
                    }
                }

                override fun onError(error: String?) {
                    if (cont.isActive) {
                        cont.resume(ServiceCommandResult(success = false, output = error.orEmpty()))
                    }
                }
            }
        )
    }

    private fun hasImageExtension(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.US) ?: return false
        return ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "heic", "heif", "avif", "gif")
    }

    private fun decodeDimensions(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    internal fun parsePercentage(line: String): Float? {
        if (!ProgressLogHelper.isProgressLine(line)) {
            val match = PERCENT_REGEX.find(line) ?: return null
            val pctVal = match.groupValues[1].toFloatOrNull() ?: return null
            return (pctVal / 100f).coerceIn(0f, 1f)
        }
        val firstToken = line.trim().split(WHITESPACE_REGEX).firstOrNull().orEmpty().removeSuffix("%")
        val pctVal = firstToken.toFloatOrNull() ?: return null
        return (pctVal / 100f).coerceIn(0f, 1f)
    }

    private fun buildFriendlyFailureMessage(output: String, preset: UpscalePreset): String {
        val lower = output.lowercase(Locale.US)
        return when {
            lower.contains("vkcreateinstance") || lower.contains("vulkan") || lower.contains("vkqueuesubmit") || lower.contains("device lost") ->
                "Your device GPU rejected Vulkan acceleration. Retry on CPU or lower the tile size below."
            lower.contains("fopen") || lower.contains("load_param") || (lower.contains("failed") && preset.requiresDownload) ->
                "Model files for ${preset.title} could not be loaded cleanly. Try Instant below, or re-download the model in Model Hub."
            lower.contains("out of memory") || lower.contains("oom") || lower.contains("alloc") || lower.contains("killed") || lower.contains("signal 9") ->
                "Not enough graphics memory for this image at this tile size. Try a smaller tile size (64 px) or CPU mode."
            lower.contains("no space left") || lower.contains("enospc") ->
                "Device storage is full. Free some space and try again."
            else ->
                "Enhancement stopped before finishing. Try one of the recovery options below or copy technical details."
        }
    }

    internal fun buildRecoveryActions(
        output: String,
        prefs: EnginePreferences,
        preset: UpscalePreset
    ): List<RecoveryAction> {
        val lower = output.lowercase(Locale.US)
        if (lower.contains("no space left") || lower.contains("enospc")) {
            return emptyList()
        }
        return buildList {
            if (prefs.backend != ExecutionBackend.CPU) {
                add(RecoveryAction.RETRY_ON_CPU)
            }
            if (preset.id != PresetId.INSTANT && (prefs.tileSize == 0 || prefs.tileSize > 64)) {
                add(RecoveryAction.SMALLER_TILE_SIZE)
            }
            if (preset.id != PresetId.INSTANT) {
                add(RecoveryAction.TRY_INSTANT)
            }
        }
    }

    private fun appendLog(line: String) {
        _consoleLog.update { prev ->
            val combined = if (prev.isEmpty()) line else "$prev\n$line"
            if (combined.length > MAX_LOG_CHARS) {
                combined.takeLast(MAX_LOG_CHARS)
            } else {
                combined
            }
        }
    }

    private fun loadPreferences(): EnginePreferences {
        val backend = runCatching {
            ExecutionBackend.valueOf(
                prefsStore.getString(KEY_BACKEND, ExecutionBackend.AUTO_GPU.name)!!
            )
        }.getOrDefault(ExecutionBackend.AUTO_GPU)
        val format = runCatching {
            OutputFormat.valueOf(
                prefsStore.getString(KEY_OUTPUT_FORMAT, OutputFormat.PNG.name)!!
            )
        }.getOrDefault(OutputFormat.PNG)
        val externalMode = runCatching {
            ExternalOpenMode.valueOf(
                prefsStore.getString(KEY_EXTERNAL_OPEN_MODE, ExternalOpenMode.QUICK_SHEET.name)!!
            )
        }.getOrDefault(ExternalOpenMode.QUICK_SHEET)
        val lastPreset = runCatching {
            PresetId.valueOf(
                prefsStore.getString(KEY_LAST_PRESET, PresetId.INSTANT.name)!!
            )
        }.getOrDefault(PresetId.INSTANT)
        val presetObj = UpscalePreset.fromId(lastPreset)
        val rawScale = prefsStore.getInt(KEY_LAST_SCALE, presetObj.defaultScale)
        val validScale = if (rawScale in presetObj.supportedScales) rawScale else presetObj.defaultScale

        val defaultPreset = prefsStore.getString(KEY_DEFAULT_PRESET, null)
            ?.let { runCatching { PresetId.valueOf(it) }.getOrNull() }

        return EnginePreferences(
            backend = backend,
            tileSize = prefsStore.getInt(KEY_TILE_SIZE, 0),
            threadCount = prefsStore.getString(KEY_THREADS, "").orEmpty(),
            outputFormat = format,
            outputTreeUri = prefsStore.getString(KEY_OUTPUT_TREE_URI, null),
            hideProgramOutput = prefsStore.getBoolean(KEY_HIDE_OUTPUT, false),
            externalOpenMode = externalMode,
            lastUsedPresetId = lastPreset,
            lastUsedScale = validScale,
            defaultPresetId = defaultPreset,
            keepScreenOnDuringEnhance = prefsStore.getBoolean(KEY_KEEP_SCREEN_ON, true),
            hapticsEnabled = prefsStore.getBoolean(KEY_HAPTICS_ENABLED, true),
            wifiOnlyDownloads = prefsStore.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, false)
        )
    }

    companion object {
        private const val PREFS_NAME = "realscale_engine_prefs"
        private const val KEY_BACKEND = "backend"
        private const val KEY_TILE_SIZE = "tile_size"
        private const val KEY_THREADS = "threads"
        private const val KEY_OUTPUT_FORMAT = "output_format"
        private const val KEY_OUTPUT_TREE_URI = "output_tree_uri"
        private const val KEY_HIDE_OUTPUT = "hide_output"
        private const val KEY_EXTERNAL_OPEN_MODE = "external_open_mode"
        private const val KEY_LAST_PRESET = "last_preset_id"
        private const val KEY_LAST_SCALE = "last_scale"
        private const val KEY_DEFAULT_PRESET = "default_preset_id"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        private const val NOTIFY_DETAILED_AUTO_DISMISS = 3
        private const val MAX_LOG_CHARS = 24_000
        private val PERCENT_REGEX = Regex("""(\d{1,3}(?:\.\d+)?)%""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
