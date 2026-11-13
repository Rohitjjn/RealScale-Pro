package com.tumuyan.ncnn.realsr.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class RealScaleTypeConverters {
    @TypeConverter
    fun fromStatus(status: ModelInstallStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ModelInstallStatus =
        runCatching { ModelInstallStatus.valueOf(value) }.getOrDefault(ModelInstallStatus.NOT_INSTALLED)
}

@Database(
    entities = [
        RecentCreationEntity::class,
        DownloadedModelEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RealScaleTypeConverters::class)
abstract class RealScaleDatabase : RoomDatabase() {
    abstract fun recentCreationDao(): RecentCreationDao
    abstract fun downloadedModelDao(): DownloadedModelDao

    companion object {
        @Volatile
        private var INSTANCE: RealScaleDatabase? = null

        fun getInstance(context: Context): RealScaleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RealScaleDatabase::class.java,
                    "realscale_pro.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
