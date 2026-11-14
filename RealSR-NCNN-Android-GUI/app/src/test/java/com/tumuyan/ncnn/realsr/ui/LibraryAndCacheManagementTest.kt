package com.tumuyan.ncnn.realsr.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.ImageIntakeManager
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
