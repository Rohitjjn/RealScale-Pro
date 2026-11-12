package com.tumuyan.ncnn.realsr.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.tumuyan.ncnn.realsr.ImageProcessor
import com.tumuyan.ncnn.realsr.ProcessingService
import com.tumuyan.ncnn.realsr.ProgressLogHelper
import com.tumuyan.ncnn.realsr.core.database.RecentCreationDao
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class ProcessingServiceEngine(
    context: Context,
    private val recentCreationDao: RecentCreationDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UpscaleEngine {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val prefsStore = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _consoleLog = MutableStateFlow("")
    override val consoleLog: StateFlow<String> = _consoleLog.asStateFlow()

    private val _preferences = MutableStateFlow(loadPreferences())
    override val preferences: StateFlow<EnginePreferences> = _preferences.asStateFlow()

    @Volatile
    private var boundService: ProcessingService? = null

    @Volatile
    private var serviceDeferred = CompletableDeferred<ProcessingService>()

    @Volatile
    private var isBound = false

    private var activeJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? ProcessingService.LocalBinder)?.service
            if (service != null) {
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
            .apply()
    }

    override fun startUpscale(request: UpscaleRequest) {
        if (request.inputUris.isEmpty()) return
        activeJob?.cancel()
        activeJob = scope.launch {
            executeUpscaleBatch(request)
        }
    }

    override fun startDirectoryUpscale(
        directoryUri: Uri,
        preset: UpscalePreset,
        scale: Int,
        modelSpec: ModelExecutionSpec?
    ) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val dir = DocumentFile.fromTreeUri(appContext, directoryUri)
            if (dir == null || !dir.isDirectory) {
                _state.value = EngineState.Failed(
                    userMessage = "Could not open the selected folder.",
                    technicalOutput = "Invalid tree URI: $directoryUri"
                )
                return@launch
            }
            val imageUris = dir.listFiles()
                .filter { file ->
                    file.isFile && (file.type?.startsWith("image/") == true || hasImageExtension(file.name))
                }
                .map { it.uri }

            if (imageUris.isEmpty()) {
                _state.value = EngineState.Failed(
                    userMessage = "No supported images were found in the selected folder.",
                    technicalOutput = "Empty directory scan: $directoryUri"
                )
                return@launch
            }
            executeUpscaleBatch(
                UpscaleRequest(
                    inputUris = imageUris,
                    preset = preset,
                    scale = scale,
                    modelSpec = modelSpec
                )
            )
        }
    }

    override fun runAdvancedCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return
        activeJob?.cancel()
        activeJob = scope.launch {
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
            _state.value = EngineState.Running(
                jobId = jobId,
                presetTitle = "Advanced Command",
                progressFraction = 0f,
                currentFileIndex = 1,
                totalFiles = 1,
                currentFileName = trimmed.substringBefore(' '),
                etaMillis = null,
                previewUri = null,
                latestStatusLine = "Running command…"
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
                                latestStatusLine = line
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

    override fun cancel() {
        val job = activeJob
        activeJob = null
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

    private suspend fun executeUpscaleBatch(request: UpscaleRequest) {
        val jobId = UUID.randomUUID().toString().take(8)
        val batchStartMs = System.currentTimeMillis()
        val prefs = _preferences.value

        try {
            _state.value = EngineState.Preparing("Preparing upscaling engine…")
            val realsrDir = ensureInitialized(forceReset = false).getOrElse { err ->
                _state.value = EngineState.Failed(
                    userMessage = "Could not prepare native upscaling engine.",
                    technicalOutput = err.stackTraceToString()
                )
                return
            }

            val service = obtainForegroundService()
            val creations = mutableListOf<RecentCreationEntity>()
            val totalFiles = request.inputUris.size

            for ((index, uri) in request.inputUris.withIndex()) {
                val fileStartMs = System.currentTimeMillis()
                val displayName = resolveDisplayName(uri) ?: "Photo ${index + 1}"
                val inputExt = resolveInputExtension(displayName, uri)
                val beforeFile = File(creationsDir, "before_${batchStartMs}_${index}.$inputExt")
                copyUriToFile(uri, beforeFile)

                val (inW, inH) = decodeDimensions(beforeFile)
                val outExt = prefs.outputFormat.extension
                val outputFile = File(
                    creationsDir,
                    "RealScale_${batchStartMs}_${index}_${request.preset.id.name.lowercase(Locale.US)}_${request.scale}x.$outExt"
                )

                val command = PresetCommandResolver.buildUpscaleCommand(
                    preset = request.preset,
                    scale = request.scale,
                    inputPath = beforeFile.absolutePath,
                    outputPath = outputFile.absolutePath,
                    preferences = prefs,
                    modelOverride = request.modelSpec,
                    modelsRootDir = modelsRootDir
                )

                if (!prefs.hideProgramOutput) {
                    appendLog("$ $command")
                }

                _state.value = EngineState.Running(
                    jobId = jobId,
                    presetTitle = "${request.preset.title} · ${request.scale}×",
                    progressFraction = index.toFloat() / totalFiles.toFloat(),
                    currentFileIndex = index + 1,
                    totalFiles = totalFiles,
                    currentFileName = displayName,
                    etaMillis = null,
                    previewUri = beforeFile.toURI().toString(),
                    latestStatusLine = "Starting ${request.preset.title} upscale…"
                )

                val cmdResult = runServiceCommand(
                    service = service,
                    command = command,
                    workingDir = realsrDir.absolutePath
                ) { line ->
                    if (!prefs.hideProgramOutput) {
                        appendLog(line)
                    }
                    val filePct = parsePercentage(line)
                    val overallFraction = if (filePct != null) {
                        ((index.toFloat() + filePct) / totalFiles.toFloat()).coerceIn(0f, 0.99f)
                    } else {
                        null
                    }
                    val elapsed = System.currentTimeMillis() - batchStartMs
                    val eta = if (overallFraction != null && overallFraction >= 0.04f && elapsed >= 600L) {
                        val totalEstimated = (elapsed / overallFraction).toLong()
                        (totalEstimated - elapsed).coerceAtLeast(500L)
                    } else {
                        null
                    }
                    _state.update { prev ->
                        if (prev is EngineState.Running) {
                            prev.copy(
                                progressFraction = overallFraction ?: prev.progressFraction,
                                etaMillis = eta ?: prev.etaMillis,
                                latestStatusLine = humanizeStatusLine(line)
                            )
                        } else {
                            prev
                        }
                    }
                }

                if (!cmdResult.success || !outputFile.exists() || outputFile.length() == 0L) {
                    beforeFile.delete()
                    outputFile.delete()
                    _state.value = EngineState.Failed(
                        userMessage = buildFriendlyFailureMessage(cmdResult.output, request.preset),
                        technicalOutput = cmdResult.output
                    )
                    return
                }

                val exportedUri = prefs.outputTreeUri?.let { treeUriStr ->
                    exportToConfiguredFolder(outputFile, Uri.parse(treeUriStr))
                }

                val (outW, outH) = decodeDimensions(outputFile)
                val fileDuration = (System.currentTimeMillis() - fileStartMs).coerceAtLeast(1L)
                val modelIdUsed = request.modelSpec?.modelId ?: request.preset.defaultModelId
                val creation = RecentCreationEntity(
                    sourceUri = uri.toString(),
                    beforeImagePath = beforeFile.absolutePath,
                    outputPath = outputFile.absolutePath,
                    savedUri = exportedUri?.toString(),
                    presetId = request.preset.id.name,
                    presetLabel = "${request.preset.title} · ${request.scale}×",
                    modelId = modelIdUsed,
                    scale = request.scale,
                    inputWidth = inW,
                    inputHeight = inH,
                    outputWidth = outW,
                    outputHeight = outH,
                    durationMs = fileDuration,
                    createdAtEpochMs = System.currentTimeMillis()
                )
                val insertedId = recentCreationDao.insert(creation)
                creations += creation.copy(id = insertedId)
            }

            val totalDuration = (System.currentTimeMillis() - batchStartMs).coerceAtLeast(1L)
            _state.value = EngineState.Completed(
                UpscaleResult(
                    creations = creations,
                    totalDurationMs = totalDuration
                )
            )
        } catch (_: CancellationException) {
            _state.value = EngineState.Cancelled
        } catch (t: Throwable) {
            _state.value = EngineState.Failed(
                userMessage = "Upscaling stopped unexpectedly. Try switching to CPU or smaller tiles in Settings.",
                technicalOutput = t.stackTraceToString()
            )
        }
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

    private fun copyUriToFile(uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        if (uri.scheme == "file") {
            val src = File(requireNotNull(uri.path))
            src.copyTo(target, overwrite = true)
            return
        }
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open selected image: $uri")
        input.use { inStream ->
            FileOutputStream(target).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
    }

    private fun exportToConfiguredFolder(sourceFile: File, treeUri: Uri): Uri? = runCatching {
        val dir = DocumentFile.fromTreeUri(appContext, treeUri) ?: return null
        val mime = when (sourceFile.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        val doc = dir.createFile(mime, sourceFile.name) ?: return null
        appContext.contentResolver.openOutputStream(doc.uri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        }
        doc.uri
    }.getOrNull()

    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return File(requireNotNull(uri.path)).name
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    private fun resolveInputExtension(displayName: String, uri: Uri): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase(Locale.US)
        if (fromName in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")) {
            return if (fromName == "jpeg") "jpg" else fromName
        }
        val mime = runCatching { appContext.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return when {
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "png"
        }
    }

    private fun hasImageExtension(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.US) ?: return false
        return ext in setOf("png", "jpg", "jpeg", "webp", "bmp")
    }

    private fun decodeDimensions(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    private fun parsePercentage(line: String): Float? {
        if (!ProgressLogHelper.isProgressLine(line)) {
            val match = PERCENT_REGEX.find(line) ?: return null
            val pctVal = match.groupValues[1].toFloatOrNull() ?: return null
            return (pctVal / 100f).coerceIn(0f, 1f)
        }
        val trimmed = line.trim().removeSuffix("%")
        val pctVal = trimmed.toFloatOrNull() ?: return null
        return (pctVal / 100f).coerceIn(0f, 1f)
    }

    private fun humanizeStatusLine(line: String): String {
        val trimmed = line.trim()
        return if (ProgressLogHelper.isProgressLine(trimmed)) {
            "Enhancing details · $trimmed"
        } else {
            trimmed.take(80)
        }
    }

    private fun buildFriendlyFailureMessage(output: String, preset: UpscalePreset): String {
        val lower = output.lowercase(Locale.US)
        return when {
            lower.contains("vkcreateinstance") || lower.contains("vulkan") ->
                "Your device GPU rejected Vulkan acceleration. Switch Processing Device to CPU in Settings and try again."
            lower.contains("fopen") || lower.contains("failed") && preset.requiresDownload ->
                "Model files for ${preset.title} were not found. Re-download the model in Model Hub."
            lower.contains("out of memory") || lower.contains("oom") || lower.contains("alloc") ->
                "Ran out of graphics memory on this photo. Set Tile Size to 128 or 64 in Settings."
            else ->
                "Could not finish upscaling this image. Check Settings → Advanced console for details."
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
        return EnginePreferences(
            backend = backend,
            tileSize = prefsStore.getInt(KEY_TILE_SIZE, 0),
            threadCount = prefsStore.getString(KEY_THREADS, "").orEmpty(),
            outputFormat = format,
            outputTreeUri = prefsStore.getString(KEY_OUTPUT_TREE_URI, null),
            hideProgramOutput = prefsStore.getBoolean(KEY_HIDE_OUTPUT, false)
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
        private const val NOTIFY_DETAILED_AUTO_DISMISS = 3
        private const val MAX_LOG_CHARS = 24_000
        private val PERCENT_REGEX = Regex("""(\d{1,3}(?:\.\d+)?)%""")
    }
}
