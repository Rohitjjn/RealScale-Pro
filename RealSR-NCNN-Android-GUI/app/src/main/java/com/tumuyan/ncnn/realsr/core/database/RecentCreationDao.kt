package com.tumuyan.ncnn.realsr.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentCreationDao {
    @Query("SELECT * FROM recent_creations ORDER BY createdAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentCreationEntity>>

    @Query("SELECT * FROM recent_creations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecentCreationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentCreationEntity): Long

    @Query("UPDATE recent_creations SET savedUri = :savedUri WHERE id = :id")
    suspend fun updateSavedUri(id: Long, savedUri: String)

    @Query("DELETE FROM recent_creations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recent_creations")
    suspend fun clearAll()
}
