package com.tumuyan.ncnn.realsr.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_creations")
data class RecentCreationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sourceUri: String,
    val beforeImagePath: String,
    val outputPath: String,
    val savedUri: String? = null,
    val presetId: String,
    val presetLabel: String,
    val modelId: String,
    val scale: Int,
    val inputWidth: Int,
    val inputHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val durationMs: Long,
    val createdAtEpochMs: Long
)
