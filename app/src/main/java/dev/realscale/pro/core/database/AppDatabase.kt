package dev.realscale.pro.core.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "creations")
data class CreationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inputPath: String, val outputPath: String, val modelName: String,
    val scale: Int, val createdAt: Long, val durationMs: Long,
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String, val name: String, val engine: String, val scale: Int,
    val localDir: String, val sha256: String, val downloadedAt: Long,
)

@Dao interface CreationDao {
    @Query("SELECT * FROM creations ORDER BY createdAt DESC") fun observeAll(): Flow<List<CreationEntity>>
    @Query("SELECT * FROM creations ORDER BY createdAt DESC") suspend fun all(): List<CreationEntity>
    @Insert suspend fun insert(e: CreationEntity): Long
    @Delete suspend fun delete(e: CreationEntity)
}

@Dao interface ModelDao {
    @Query("SELECT * FROM models") fun observeAll(): Flow<List<ModelEntity>>
    @Query("SELECT * FROM models WHERE id = :id") suspend fun get(id: String): ModelEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: ModelEntity)
    @Query("DELETE FROM models WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [CreationEntity::class, ModelEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun creations(): CreationDao
    abstract fun models(): ModelDao
    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "realscale.db").build()
    }
}
