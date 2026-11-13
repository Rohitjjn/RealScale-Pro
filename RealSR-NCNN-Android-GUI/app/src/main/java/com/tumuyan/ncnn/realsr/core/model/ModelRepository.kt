package com.tumuyan.ncnn.realsr.core.model

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tumuyan.ncnn.realsr.core.database.DownloadedModelDao
import com.tumuyan.ncnn.realsr.core.database.DownloadedModelEntity
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.engine.ModelExecutionSpec
import com.tumuyan.ncnn.realsr.core.engine.PresetId
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

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
    private val workManager by lazy { WorkManager.getInstance(appContext) }
    private val manifestFlow = MutableStateFlow(loadActiveManifestStatic(appContext))

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

    suspend fun startOrResumeDownload(
        modelId: String,
        wifiOnly: Boolean = false
    ) = withContext(ioDispatcher) {
        val item = manifestFlow.value.models.firstOrNull { it.id == modelId } ?: return@withContext
        val existing = downloadedModelDao.getById(modelId)
        val preservedBytes = if (existing?.status == ModelInstallStatus.PAUSED || existing?.status == ModelInstallStatus.FAILED) {
            existing.downloadedBytes
        } else {
            0L
        }
        downloadedModelDao.upsert(
            DownloadedModelEntity(
                modelId = item.id,
                displayName = item.displayName,
                category = item.category,
                engineTool = item.engineTool,
                defaultScale = item.defaultScale,
                supportedScalesCsv = item.supportedScales.joinToString(","),
                installDir = File(modelsRootDir, item.id).absolutePath,
                status = ModelInstallStatus.QUEUED,
                downloadedBytes = preservedBytes,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L,
                manifestVersion = manifestFlow.value.manifestVersion,
                sha256Summary = item.files.first().sha256,
                errorMessage = null,
                installedAtEpochMs = null
            )
        )

        val requiredNetwork = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(requiredNetwork)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to modelId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            ModelDownloadWorker.uniqueWorkName(modelId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun pauseDownload(modelId: String) = withContext(ioDispatcher) {
        val existing = downloadedModelDao.getById(modelId)
        if (existing != null) {
            downloadedModelDao.updateProgress(
                modelId = modelId,
                status = ModelInstallStatus.PAUSED,
                downloadedBytes = existing.downloadedBytes,
                totalBytes = existing.totalBytes,
                speedBytesPerSec = 0L,
                errorMessage = null
            )
        }
        workManager.cancelUniqueWork(ModelDownloadWorker.uniqueWorkName(modelId))
    }

    suspend fun cancelDownload(modelId: String) = withContext(ioDispatcher) {
        workManager.cancelUniqueWork(ModelDownloadWorker.uniqueWorkName(modelId))
        File(stagingRootDir, modelId).deleteRecursively()
        downloadedModelDao.deleteById(modelId)
    }

    suspend fun deleteInstalledModel(modelId: String) = withContext(ioDispatcher) {
        workManager.cancelUniqueWork(ModelDownloadWorker.uniqueWorkName(modelId))
        File(stagingRootDir, modelId).deleteRecursively()
        File(modelsRootDir, modelId).deleteRecursively()
        downloadedModelDao.deleteById(modelId)
    }

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
