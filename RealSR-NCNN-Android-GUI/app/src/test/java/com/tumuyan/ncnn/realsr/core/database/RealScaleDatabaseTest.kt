package com.tumuyan.ncnn.realsr.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealScaleDatabaseTest {

    private lateinit var db: RealScaleDatabase
    private lateinit var recentDao: RecentCreationDao
    private lateinit var modelDao: DownloadedModelDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RealScaleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recentDao = db.recentCreationDao()
        modelDao = db.downloadedModelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recentCreationDaoInsertsUpdatesAndOrdersNewestFirst() = runTest {
        val olderId = recentDao.insert(
            RecentCreationEntity(
                sourceUri = "content://photos/1",
                beforeImagePath = "/files/before_1.png",
                outputPath = "/files/out_1.png",
                presetId = "INSTANT",
                presetLabel = "Instant · 2×",
                modelId = "builtin-resize-ncnn",
                scale = 2,
                inputWidth = 800,
                inputHeight = 600,
                outputWidth = 1600,
                outputHeight = 1200,
                durationMs = 420L,
                createdAtEpochMs = 1_000L
            )
        )
        val newerId = recentDao.insert(
            RecentCreationEntity(
                sourceUri = "content://photos/2",
                beforeImagePath = "/files/before_2.png",
                outputPath = "/files/out_2.png",
                presetId = "PHOTO",
                presetLabel = "Photo · 4×",
                modelId = "models-RealeSR-general-v3",
                scale = 4,
                inputWidth = 1000,
                inputHeight = 1000,
                outputWidth = 4000,
                outputHeight = 4000,
                durationMs = 3_200L,
                createdAtEpochMs = 2_000L
            )
        )

        val list = recentDao.observeRecent().first()
        assertEquals(2, list.size)
        assertEquals(newerId, list[0].id)
        assertEquals(olderId, list[1].id)

        recentDao.updateSavedUri(newerId, "content://media/external/images/media/99")
        assertEquals(
            "content://media/external/images/media/99",
            recentDao.getById(newerId)?.savedUri
        )

        recentDao.deleteById(olderId)
        assertNull(recentDao.getById(olderId))
    }

    @Test
    fun downloadedModelDaoTracksProgressInstalledBytesAndDeletion() = runTest {
        val entity = DownloadedModelEntity(
            modelId = "models-RealeSR-general-v3",
            displayName = "Photo General v3",
            category = "PHOTO",
            engineTool = "realsr-ncnn",
            defaultScale = 4,
            supportedScalesCsv = "4",
            installDir = "/files/models/models-RealeSR-general-v3",
            status = ModelInstallStatus.DOWNLOADING,
            downloadedBytes = 512_000L,
            totalBytes = 2_444_000L,
            speedBytesPerSec = 1_024_000L
        )
        modelDao.upsert(entity)
        assertEquals(0L, modelDao.observeTotalInstalledBytes().first())

        modelDao.updateProgress(
            modelId = "models-RealeSR-general-v3",
            status = ModelInstallStatus.INSTALLED,
            downloadedBytes = 2_444_000L,
            totalBytes = 2_444_000L,
            speedBytesPerSec = 0L
        )
        assertEquals(2_444_000L, modelDao.observeTotalInstalledBytes().first())
        assertEquals(1, modelDao.observeInstalled().first().size)

        modelDao.deleteById("models-RealeSR-general-v3")
        assertNull(modelDao.getById("models-RealeSR-general-v3"))
        assertEquals(0L, modelDao.observeTotalInstalledBytes().first())
    }
}
