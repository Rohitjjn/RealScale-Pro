package com.tumuyan.ncnn.realsr.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.ImageIntakeManager
import com.tumuyan.ncnn.realsr.ui.screens.LibraryQueryEngine
import com.tumuyan.ncnn.realsr.ui.screens.LibrarySortOption
import com.tumuyan.ncnn.realsr.widget.RealScaleEnhanceWidgetReceiver
import com.tumuyan.ncnn.realsr.widget.RealScaleRecentWidgetReceiver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryAndCacheManagementTest {

    @Test
    fun libraryQueryEngineFiltersAndSortsByNewestLargestAndFastest() {
        val items = listOf(
            RecentCreationEntity(
                id = 1L,
                sourceUri = "file:///a.png",
                beforeImagePath = "/tmp/a_in.png",
                outputPath = "/tmp/a_out.png",
                presetId = "INSTANT",
                presetLabel = "Instant · 2×",
                modelId = "resize-ncnn",
                scale = 2,
                inputWidth = 500,
                inputHeight = 400,
                outputWidth = 1000,
                outputHeight = 800,
                durationMs = 350L,
                createdAtEpochMs = 1_000L
            ),
            RecentCreationEntity(
                id = 2L,
                sourceUri = "file:///b.png",
                beforeImagePath = "/tmp/b_in.png",
                outputPath = "/tmp/b_out.png",
                presetId = "PHOTO",
                presetLabel = "Photo · 4×",
                modelId = "models-RealeSR-general-v3",
                scale = 4,
                inputWidth = 1000,
                inputHeight = 1000,
                outputWidth = 4000,
                outputHeight = 4000,
                durationMs = 4_800L,
                createdAtEpochMs = 3_000L
            ),
            RecentCreationEntity(
                id = 3L,
                sourceUri = "file:///c.png",
                beforeImagePath = "/tmp/c_in.png",
                outputPath = "/tmp/c_out.png",
                presetId = "ANIME",
                presetLabel = "Anime & Art · 2×",
                modelId = "models-cunet",
                scale = 2,
                inputWidth = 800,
                inputHeight = 600,
                outputWidth = 1600,
                outputHeight = 1200,
                durationMs = 1_200L,
                createdAtEpochMs = 2_000L
            )
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            LibraryQueryEngine.filterAndSort(items, "", LibrarySortOption.NEWEST).map { it.id }
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            LibraryQueryEngine.filterAndSort(items, "", LibrarySortOption.LARGEST).map { it.id }
        )
        assertEquals(
            listOf(1L, 3L, 2L),
            LibraryQueryEngine.filterAndSort(items, "", LibrarySortOption.FASTEST).map { it.id }
        )
        assertEquals(
            listOf(2L),
            LibraryQueryEngine.filterAndSort(items, "4000x4000", LibrarySortOption.NEWEST).map { it.id }
        )
        assertEquals(
            listOf(3L),
            LibraryQueryEngine.filterAndSort(items, "cunet", LibrarySortOption.NEWEST).map { it.id }
        )
    }

    @Test
    fun temporaryCacheCleanerRemovesStagedImagesWhilePreservingExtractedBinaries() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val intakeDir = File(app.cacheDir, "intake").apply { mkdirs() }
        val realsrDir = File(app.cacheDir, "realsr").apply { mkdirs() }

        val stagedImg = File(intakeDir, "staged_test.png").apply { writeBytes(ByteArray(2048)) }
        val tempEngineImg = File(realsrDir, "output_temp.png").apply { writeBytes(ByteArray(1024)) }
        val elfBinary = File(realsrDir, "realsr-ncnn").apply { writeBytes(ByteArray(4096)) }

        val measured = ImageIntakeManager.measureTemporaryCacheBytes(app)
        assertTrue("Expected >= 3072 temporary cache bytes, got $measured", measured >= 3072L)

        val freed = ImageIntakeManager.clearTemporaryCache(app)
        assertTrue("Expected freed >= 3072 bytes, got $freed", freed >= 3072L)
        assertTrue("Staged image should be deleted", !stagedImg.exists())
        assertTrue("Transient engine image should be deleted", !tempEngineImg.exists())
        assertTrue("Extracted ELF binary must remain untouched", elfBinary.isFile && elfBinary.length() == 4096L)
    }

    @Test
    fun glanceHomeScreenWidgetReceiversProvideSmallAndMediumWidgets() {
        val small = RealScaleEnhanceWidgetReceiver()
        val medium = RealScaleRecentWidgetReceiver()
        assertNotNull(small.glanceAppWidget)
        assertNotNull(medium.glanceAppWidget)
    }
}
