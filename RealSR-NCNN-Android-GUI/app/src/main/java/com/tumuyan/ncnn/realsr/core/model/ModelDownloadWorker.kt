package com.tumuyan.ncnn.realsr.core.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tumuyan.ncnn.realsr.core.database.DownloadedModelEntity
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.database.RealScaleDatabase
import kotlinx.coroutines.CancellationException
import java.io.File

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val database = RealScaleDatabase.getInstance(applicationContext)
        val modelDao = database.downloadedModelDao()
        val manifest = ModelRepository.loadActiveManifestStatic(applicationContext)
        val item = manifest.models.firstOrNull { it.id == modelId }
            ?: return Result.failure(workDataOf(KEY_ERROR to "Unknown model: $modelId"))

        val modelsRootDir = File(applicationContext.filesDir, "models")
        val stagingRootDir = File(applicationContext.filesDir, "models_staging")
        val targetInstallDir = File(modelsRootDir, item.id)

        val existing = modelDao.getById(modelId)
        val baseEntity = DownloadedModelEntity(
            modelId = item.id,
            displayName = item.displayName,
            category = item.category,
            engineTool = item.engineTool,
            defaultScale = item.defaultScale,
            supportedScalesCsv = item.supportedScales.joinToString(","),
            installDir = targetInstallDir.absolutePath,
            status = ModelInstallStatus.DOWNLOADING,
            downloadedBytes = existing?.downloadedBytes ?: 0L,
            totalBytes = item.totalBytes,
            speedBytesPerSec = 0L,
            manifestVersion = manifest.manifestVersion,
            sha256Summary = item.files.first().sha256,
            errorMessage = null,
            installedAtEpochMs = null
        )
        modelDao.upsert(baseEntity)

        val installer = ResumableModelInstaller()
        return try {
            val installedDir = installer.downloadAndInstall(
                item = item,
                stagingRootDir = stagingRootDir,
                modelsRootDir = modelsRootDir
            ) { snap ->
                val status = if (snap.verifying) ModelInstallStatus.VERIFYING else ModelInstallStatus.DOWNLOADING
                modelDao.updateProgress(
                    modelId = item.id,
                    status = status,
                    downloadedBytes = snap.downloadedBytes,
                    totalBytes = snap.totalBytes,
                    speedBytesPerSec = snap.speedBytesPerSec,
                    errorMessage = null
                )
                setProgress(
                    workDataOf(
                        KEY_MODEL_ID to item.id,
                        KEY_DOWNLOADED_BYTES to snap.downloadedBytes,
                        KEY_TOTAL_BYTES to snap.totalBytes,
                        KEY_SPEED_BPS to snap.speedBytesPerSec
                    )
                )
            }

            modelDao.upsert(
                baseEntity.copy(
                    installDir = installedDir.absolutePath,
                    status = ModelInstallStatus.INSTALLED,
                    downloadedBytes = item.totalBytes,
                    totalBytes = item.totalBytes,
                    speedBytesPerSec = 0L,
                    errorMessage = null,
                    installedAtEpochMs = System.currentTimeMillis()
                )
            )
            Result.success()
        } catch (ce: CancellationException) {
            // Preserve partial .part files; keep PAUSED or NOT_INSTALLED depending on repository intent
            val latest = modelDao.getById(modelId)
            if (latest != null && latest.status == ModelInstallStatus.DOWNLOADING) {
                modelDao.updateProgress(
                    modelId = modelId,
                    status = ModelInstallStatus.PAUSED,
                    downloadedBytes = latest.downloadedBytes,
                    totalBytes = item.totalBytes,
                    speedBytesPerSec = 0L,
                    errorMessage = null
                )
            }
            throw ce
        } catch (te: TransientDownloadException) {
            if (runAttemptCount < MAX_AUTO_RETRIES) {
                modelDao.updateProgress(
                    modelId = modelId,
                    status = ModelInstallStatus.QUEUED,
                    downloadedBytes = modelDao.getById(modelId)?.downloadedBytes ?: 0L,
                    totalBytes = item.totalBytes,
                    speedBytesPerSec = 0L,
                    errorMessage = "Reconnecting…"
                )
                Result.retry()
            } else {
                modelDao.updateProgress(
                    modelId = modelId,
                    status = ModelInstallStatus.FAILED,
                    downloadedBytes = modelDao.getById(modelId)?.downloadedBytes ?: 0L,
                    totalBytes = item.totalBytes,
                    speedBytesPerSec = 0L,
                    errorMessage = te.message ?: "Network error while downloading model."
                )
                Result.failure(workDataOf(KEY_ERROR to (te.message ?: "Download failed")))
            }
        } catch (pe: PermanentDownloadException) {
            modelDao.updateProgress(
                modelId = modelId,
                status = ModelInstallStatus.FAILED,
                downloadedBytes = modelDao.getById(modelId)?.downloadedBytes ?: 0L,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L,
                errorMessage = pe.message ?: "Model verification failed."
            )
            Result.failure(workDataOf(KEY_ERROR to (pe.message ?: "Download failed")))
        } catch (t: Throwable) {
            modelDao.updateProgress(
                modelId = modelId,
                status = ModelInstallStatus.FAILED,
                downloadedBytes = modelDao.getById(modelId)?.downloadedBytes ?: 0L,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L,
                errorMessage = t.message ?: "Unexpected download error."
            )
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "Unexpected error")))
        }
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SPEED_BPS = "speed_bps"
        const val KEY_ERROR = "error_message"
        private const val MAX_AUTO_RETRIES = 3

        fun uniqueWorkName(modelId: String): String = "realscale_model_download_$modelId"
    }
}
