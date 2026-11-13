package com.tumuyan.ncnn.realsr.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ModelInstallStatus {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLED,
    FAILED
}

@Entity(tableName = "downloaded_models")
data class DownloadedModelEntity(
    @PrimaryKey
    val modelId: String,
    val displayName: String,
    val category: String,
    val engineTool: String,
    val defaultScale: Int,
    val supportedScalesCsv: String,
    val installDir: String,
    val status: ModelInstallStatus,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val manifestVersion: Int = 1,
    val sha256Summary: String = "",
    val errorMessage: String? = null,
    val installedAtEpochMs: Long? = null
)
