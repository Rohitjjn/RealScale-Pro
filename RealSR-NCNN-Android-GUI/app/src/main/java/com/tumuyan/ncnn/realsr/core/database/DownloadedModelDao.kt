package com.tumuyan.ncnn.realsr.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedModelDao {
    @Query("SELECT * FROM downloaded_models ORDER BY displayName ASC")
    fun observeAll(): Flow<List<DownloadedModelEntity>>

    @Query("SELECT * FROM downloaded_models WHERE status = 'INSTALLED' ORDER BY displayName ASC")
    fun observeInstalled(): Flow<List<DownloadedModelEntity>>

    @Query("SELECT * FROM downloaded_models WHERE modelId = :modelId LIMIT 1")
    suspend fun getById(modelId: String): DownloadedModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedModelEntity)

    @Query(
        """
        UPDATE downloaded_models
        SET status = :status,
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speedBytesPerSec,
            errorMessage = :errorMessage
        WHERE modelId = :modelId
        """
    )
    suspend fun updateProgress(
        modelId: String,
        status: ModelInstallStatus,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        errorMessage: String? = null
    )

    @Query("DELETE FROM downloaded_models WHERE modelId = :modelId")
    suspend fun deleteById(modelId: String)

    @Query("SELECT COALESCE(SUM(totalBytes), 0) FROM downloaded_models WHERE status = 'INSTALLED'")
    fun observeTotalInstalledBytes(): Flow<Long>
}
