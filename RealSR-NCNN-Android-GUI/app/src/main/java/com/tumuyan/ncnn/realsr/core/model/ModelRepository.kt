package com.tumuyan.ncnn.realsr.core.model

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tumuyan.ncnn.realsr.core.database.DownloadedModelDao
import com.tumuyan.ncnn.realsr.core.database.DownloadedModelEntity
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.engine.ModelExecutionSpec
import com.tumuyan.ncnn.realsr.core.engine.PresetId
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class CatalogModelState(
    val catalog: ModelCatalogItem,
    val status: ModelInstallStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val errorMessage: String?
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isInstalled: Boolean
        get() = status == ModelInstallStatus.INSTALLED
}

class ModelRepository(
    context: Context,
    private val downloadedModelDao: DownloadedModelDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext
    private val manifestFlow = MutableStateFlow(loadActiveManifestStatic(appContext))

    /**
     * Downloads run in the app process, not through WorkManager. A scheduled job can sit in the
     * queue for minutes on aggressive OEM builds (and never run under a metered network
     * constraint), which the user reads as "nothing downloads". An in-process coroutine starts on
     * the next frame, streams progress into the DAO and can be cancelled instantly for Pause; the
     * `.part` files it leaves behind are what Resume continues with HTTP Range.
     */
    private val downloadScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val installer = ResumableModelInstaller()

    /**
     * One transfer at a time, everywhere in the app. Queued models wait here with their row on
     * QUEUED, so a "download all" tap never turns into four half-speed transfers.
     */
    private val transferLock = Mutex()

    init {
        // A process death during a download leaves the row stuck on DOWNLOADING: nothing is
        // streaming, so surface it as Paused and let the user resume it.
        downloadScope.launch { markOrphanedRowsPaused() }
    }

    val modelsRootDir: File
        get() = File(appContext.filesDir, "models").apply { mkdirs() }

    private val stagingRootDir: File
        get() = File(appContext.filesDir, "models_staging").apply { mkdirs() }

    val catalogModels: Flow<List<CatalogModelState>> = combine(
        manifestFlow,
        downloadedModelDao.observeAll()
    ) { manifest, dbRows ->
        val byId = dbRows.associateBy { it.modelId }
        manifest.models.map { item ->
            val row = byId[item.id]
            val verifiedOnDisk = row?.status == ModelInstallStatus.INSTALLED && isModelPresentOnDisk(item)
            val effectiveStatus = when {
                row == null -> ModelInstallStatus.NOT_INSTALLED
                row.status == ModelInstallStatus.INSTALLED && !verifiedOnDisk -> ModelInstallStatus.NOT_INSTALLED
                else -> row.status
            }
            CatalogModelState(
                catalog = item,
                status = effectiveStatus,
                downloadedBytes = if (effectiveStatus == ModelInstallStatus.NOT_INSTALLED) 0L else (row?.downloadedBytes ?: 0L),
                totalBytes = item.totalBytes,
                speedBytesPerSec = row?.speedBytesPerSec ?: 0L,
                errorMessage = row?.errorMessage
            )
        }
    }

    val totalInstalledBytes: Flow<Long> = downloadedModelDao.observeTotalInstalledBytes()

    /**
     * Starts (or continues) one model. Safe to call repeatedly: an in-flight download for the same
     * model is restarted from its `.part` files, not from byte 0.
     */
    suspend fun startOrResumeDownload(
        modelId: String,
        wifiOnly: Boolean = false
    ) {
        val item = manifestFlow.value.models.firstOrNull { it.id == modelId } ?: return
        if (isModelPresentOnDisk(item)) {
            downloadedModelDao.upsert(row(item, ModelInstallStatus.INSTALLED, item.totalBytes, null))
            return
        }
        downloadJobs.remove(modelId)?.let { previous ->
            previous.cancel()
            previous.join()
        }
        val existing = withContext(ioDispatcher) { downloadedModelDao.getById(modelId) }
        val preserved = if (existing != null && existing.status != ModelInstallStatus.INSTALLED) {
            existing.downloadedBytes.coerceIn(0L, item.totalBytes)
        } else {
            0L
        }
        withContext(ioDispatcher) {
            downloadedModelDao.upsert(row(item, ModelInstallStatus.QUEUED, preserved, null))
        }
        downloadJobs[modelId] = downloadScope.launch { runDownload(item, wifiOnly) }
    }

    suspend fun pauseDownload(modelId: String) {
        // Write the terminal state before cancelling so the job's own cancellation handler sees
        // PAUSED and does not overwrite it.
        val existing = withContext(ioDispatcher) { downloadedModelDao.getById(modelId) }
        if (existing != null) {
            withContext(ioDispatcher) {
                downloadedModelDao.updateProgress(
                    modelId = modelId,
                    status = ModelInstallStatus.PAUSED,
                    downloadedBytes = existing.downloadedBytes.coerceAtMost(existing.totalBytes),
                    totalBytes = existing.totalBytes,
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
            }
        }
        downloadJobs.remove(modelId)?.let { job ->
            job.cancel()
            job.join()
        }
    }

    suspend fun cancelDownload(modelId: String) {
        downloadJobs.remove(modelId)?.let { job ->
            job.cancel()
            job.join()
        }
        withContext(ioDispatcher) {
            File(stagingRootDir, modelId).deleteRecursively()
            downloadedModelDao.deleteById(modelId)
        }
    }

    suspend fun deleteInstalledModel(modelId: String) {
        downloadJobs.remove(modelId)?.let { job ->
            job.cancel()
            job.join()
        }
        withContext(ioDispatcher) {
            File(stagingRootDir, modelId).deleteRecursively()
            File(modelsRootDir, modelId).deleteRecursively()
            downloadedModelDao.deleteById(modelId)
        }
    }

    /** True while this repository is streaming bytes for [modelId]. */
    fun isDownloading(modelId: String): Boolean = downloadJobs[modelId]?.isActive == true

    /** Stops every in-flight download without touching the partial files. */
    suspend fun pauseAllDownloads() {
        val ids = downloadJobs.keys.toList()
        ids.forEach { pauseDownload(it) }
    }

    private suspend fun runDownload(item: ModelCatalogItem, wifiOnly: Boolean) {
        try {
            transferLock.withLock { downloadLoop(item, wifiOnly) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            writeStatus(item, ModelInstallStatus.FAILED, error = t.message ?: "Unexpected download error.")
        }
    }

    private suspend fun downloadLoop(item: ModelCatalogItem, wifiOnly: Boolean) {
        val modelId = item.id
        var attempt = 0
        while (true) {
            attempt++
            if (!awaitConnection(item, wifiOnly)) return
            writeStatus(item, ModelInstallStatus.DOWNLOADING, preservingBytes = true)
            try {
                val installedDir = installer.downloadAndInstall(
                    item = item,
                    stagingRootDir = stagingRootDir,
                    modelsRootDir = modelsRootDir
                ) { snap ->
                    val status = if (snap.verifying) ModelInstallStatus.VERIFYING else ModelInstallStatus.DOWNLOADING
                    withContext(ioDispatcher) {
                        downloadedModelDao.updateProgress(
                            modelId = modelId,
                            status = status,
                            downloadedBytes = snap.downloadedBytes,
                            totalBytes = snap.totalBytes,
                            speedBytesPerSec = snap.speedBytesPerSec,
                            errorMessage = null
                        )
                    }
                }
                withContext(ioDispatcher) {
                    downloadedModelDao.upsert(
                        row(item, ModelInstallStatus.INSTALLED, item.totalBytes, null, installedDir)
                    )
                }
                return
            } catch (ce: CancellationException) {
                // Pause already wrote PAUSED; keep the bytes it recorded and let the .part files
                // stand for the next start.
                val latest = withContext(ioDispatcher) { downloadedModelDao.getById(modelId) }
                if (latest != null && latest.status != ModelInstallStatus.PAUSED) {
                    withContext(ioDispatcher) {
                        downloadedModelDao.updateProgress(
                            modelId = modelId,
                            status = ModelInstallStatus.PAUSED,
                            downloadedBytes = latest.downloadedBytes,
                            totalBytes = latest.totalBytes,
                            speedBytesPerSec = 0L,
                            errorMessage = null
                        )
                    }
                }
                throw ce
            } catch (te: TransientDownloadException) {
                if (attempt >= MAX_ATTEMPTS) {
                    writeStatus(item, ModelInstallStatus.FAILED, error = te.message ?: "Download failed.")
                    return
                }
                writeStatus(
                    item,
                    ModelInstallStatus.QUEUED,
                    preservingBytes = true,
                    error = "Reconnecting…",
                )
                delay(RETRY_BASE_DELAY_MS * attempt)
            } catch (pe: PermanentDownloadException) {
                writeStatus(item, ModelInstallStatus.FAILED, error = pe.message ?: "Download failed.")
                return
            }
        }
    }

    /**
     * Waits for a usable network instead of letting the transfer fail: on mobile data a download
     * always proceeds, and with "Wi-Fi only" switched on the row explains what it is waiting for
     * rather than silently doing nothing.
     */
    private suspend fun awaitConnection(item: ModelCatalogItem, wifiOnly: Boolean): Boolean {
        var waitedMs = 0L
        while (!hasUsableNetwork(wifiOnly)) {
            if (waitedMs >= MAX_CONNECTION_WAIT_MS) {
                writeStatus(
                    item,
                    ModelInstallStatus.FAILED,
                    error = if (wifiOnly) "No Wi-Fi yet. Turn off Wi-Fi only to use mobile data." else "No internet connection.",
                )
                return false
            }
            writeStatus(
                item,
                ModelInstallStatus.QUEUED,
                preservingBytes = true,
                error = if (wifiOnly) "Waiting for Wi-Fi…" else "Waiting for connection…",
            )
            delay(CONNECTION_POLL_MS)
            waitedMs += CONNECTION_POLL_MS
        }
        return true
    }

    private fun hasUsableNetwork(wifiOnly: Boolean): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        return true
    }

    private suspend fun markOrphanedRowsPaused() = withContext(ioDispatcher) {
        runCatching {
            downloadedModelDao.getAll()
                .filter { it.status == ModelInstallStatus.DOWNLOADING || it.status == ModelInstallStatus.VERIFYING || it.status == ModelInstallStatus.QUEUED }
                .forEach { stale ->
                    downloadedModelDao.updateProgress(
                        modelId = stale.modelId,
                        status = ModelInstallStatus.PAUSED,
                        downloadedBytes = stale.downloadedBytes,
                        totalBytes = stale.totalBytes,
                        speedBytesPerSec = 0L,
                        errorMessage = null
                    )
                }
        }
    }

    private suspend fun writeStatus(
        item: ModelCatalogItem,
        status: ModelInstallStatus,
        preservingBytes: Boolean = false,
        error: String? = null,
    ) = withContext(ioDispatcher) {
        val previous = downloadedModelDao.getById(item.id)
        val bytes = when {
            status == ModelInstallStatus.INSTALLED -> item.totalBytes
            preservingBytes -> (previous?.downloadedBytes ?: 0L).coerceIn(0L, item.totalBytes)
            else -> 0L
        }
        if (previous == null) {
            downloadedModelDao.upsert(row(item, status, bytes, error))
        } else {
            downloadedModelDao.updateProgress(
                modelId = item.id,
                status = status,
                downloadedBytes = bytes,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L,
                errorMessage = error
            )
        }
    }

    private fun row(
        item: ModelCatalogItem,
        status: ModelInstallStatus,
        downloadedBytes: Long,
        error: String?,
        installDir: File = File(modelsRootDir, item.id),
    ) = DownloadedModelEntity(
        modelId = item.id,
        displayName = item.displayName,
        category = item.category,
        engineTool = item.engineTool,
        defaultScale = item.defaultScale,
        supportedScalesCsv = item.supportedScales.joinToString(","),
        installDir = installDir.absolutePath,
        status = status,
        downloadedBytes = downloadedBytes,
        totalBytes = item.totalBytes,
        speedBytesPerSec = 0L,
        manifestVersion = manifestFlow.value.manifestVersion,
        sha256Summary = item.files.firstOrNull()?.sha256 ?: "",
        errorMessage = error,
        installedAtEpochMs = if (status == ModelInstallStatus.INSTALLED) System.currentTimeMillis() else null
    )

    suspend fun refreshRemoteManifest(remoteUrl: String): Result<ModelManifest> = withContext(ioDispatcher) {
        runCatching {
            require(remoteUrl.startsWith("https://")) { "Manifest refresh URL must use HTTPS" }
            val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
            }
            val raw = try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Manifest refresh HTTP $code")
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val parsed = ModelManifestParser.parseAndValidate(raw)
            val bundled = loadBundledManifestStatic(appContext)
            if (parsed.manifestVersion >= bundled.manifestVersion) {
                cachedManifestFile(appContext).writeText(raw, Charsets.UTF_8)
                manifestFlow.value = parsed
                parsed
            } else {
                manifestFlow.value
            }
        }
    }

    fun resolveExecutionSpec(
        preset: UpscalePreset,
        scale: Int,
        catalogStates: List<CatalogModelState>,
        overrideModelId: String? = null
    ): ModelExecutionSpec? {
        if (preset.id == PresetId.INSTANT && overrideModelId == null) {
            return ModelExecutionSpec(
                modelId = preset.defaultModelId,
                displayName = preset.title,
                engineTool = "resize-ncnn",
                modelPath = "",
                scale = scale
            )
        }
        val targetId = overrideModelId ?: preset.defaultModelId
        val match = catalogStates.firstOrNull { it.catalog.id == targetId && it.isInstalled }
            ?: catalogStates.firstOrNull { it.catalog.category == preset.id.name && it.isInstalled }
            ?: return null

        val dir = File(modelsRootDir, match.catalog.id)
        val modelPath = if (match.catalog.entryFile != null) {
            File(dir, match.catalog.entryFile).absolutePath
        } else {
            dir.absolutePath
        }
        val chosenScale = if (scale in match.catalog.supportedScales) {
            scale
        } else {
            match.catalog.defaultScale
        }
        return ModelExecutionSpec(
            modelId = match.catalog.id,
            displayName = match.catalog.displayName,
            engineTool = match.catalog.engineTool,
            modelPath = modelPath,
            scale = chosenScale,
            denoiseLevel = match.catalog.denoiseLevel
        )
    }

    private fun isModelPresentOnDisk(item: ModelCatalogItem): Boolean {
        val dir = File(modelsRootDir, item.id)
        if (!dir.isDirectory) return false
        return item.files.all { spec ->
            val f = File(dir, spec.fileName)
            f.isFile && f.length() == spec.sizeBytes
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
        private const val RETRY_BASE_DELAY_MS = 2_000L
        private const val CONNECTION_POLL_MS = 1_500L
        private const val MAX_CONNECTION_WAIT_MS = 10 * 60 * 1000L

        private const val ASSET_MANIFEST_NAME = "model_manifest.json"
        private const val CACHED_MANIFEST_NAME = "model_manifest_cached.json"

        private fun cachedManifestFile(context: Context): File =
            File(context.filesDir, CACHED_MANIFEST_NAME)

        fun loadBundledManifestStatic(context: Context): ModelManifest {
            val text = context.assets.open(ASSET_MANIFEST_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return ModelManifestParser.parseAndValidate(text)
        }

        fun loadActiveManifestStatic(context: Context): ModelManifest {
            val bundled = loadBundledManifestStatic(context)
            val cachedFile = cachedManifestFile(context)
            if (cachedFile.isFile) {
                val cached = runCatching {
                    ModelManifestParser.parseAndValidate(cachedFile.readText(Charsets.UTF_8))
                }.getOrNull()
                if (cached != null && cached.manifestVersion >= bundled.manifestVersion) {
                    return cached
                }
            }
            return bundled
        }
    }
}
