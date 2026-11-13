package com.tumuyan.ncnn.realsr.core.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EngineJobQueueAndNamingTest {

    @Test
    fun outputNameFormatterAppendsScaleSuffixAndStripsPriorScale() {
        assertEquals(
            "IMG_2042_x4.png",
            OutputNameFormatter.buildGalleryFileName("IMG_2042.HEIC", scale = 4, outputExtension = "png")
        )
        assertEquals(
            "portrait_x4.webp",
            OutputNameFormatter.buildGalleryFileName("portrait_x2.jpg", scale = 4, outputExtension = "webp")
        )
        assertEquals(
            "RealScale_x2.jpg",
            OutputNameFormatter.buildGalleryFileName("   ", scale = 2, outputExtension = ".JPG")
        )
    }

    @Test
    fun engineJobQueueProcessesSequentiallyAndAllowsPerItemCancellation() {
        val queue = EngineJobQueue()
        val req = UpscaleRequest(
            inputUris = listOf(
                Uri.parse("file:///tmp/a.png"),
                Uri.parse("file:///tmp/b.png"),
                Uri.parse("file:///tmp/c.png")
            ),
            preset = UpscalePreset.INSTANT,
            scale = 2
        )
        val created = queue.enqueueRequest(req) { uri -> uri.lastPathSegment ?: "photo" }
        assertEquals(3, created.size)
        assertEquals("1 of 3", "${created[0].positionInBatch} of ${created[0].totalInBatch}")
        assertEquals("2 of 3", "${created[1].positionInBatch} of ${created[1].totalInBatch}")
        assertEquals("3 of 3", "${created[2].positionInBatch} of ${created[2].totalInBatch}")

        // Poll first item for execution -> marked running
        val firstWork = queue.pollNextForExecution()
        assertNotNull(firstWork)
        assertEquals("a.png", firstWork!!.item.fileName)
        assertTrue(queue.items.value.first().isRunning)

        // Cancel middle queued item while first is running
        assertTrue(queue.cancelWaitingItem(created[1].id))
        // Cannot cancel currently running item via cancelWaitingItem
        assertFalse(queue.cancelWaitingItem(created[0].id))
        assertEquals(2, queue.items.value.size)

        // Finish first item -> next polled item is c.png
        queue.markActiveFinished(firstWork.item.id)
        val secondWork = queue.pollNextForExecution()
        assertNotNull(secondWork)
        assertEquals("c.png", secondWork!!.item.fileName)

        queue.markActiveFinished(secondWork.item.id)
        assertNull(queue.pollNextForExecution())
        assertTrue(queue.items.value.isEmpty())
    }

    @Test
    fun imageIntakeManagerDetectsMagicBytesAndStagesIncomingUri() = runBlocking {
        assertEquals(
            ImageIntakeManager.ContainerKind.PNG,
            ImageIntakeManager.detectContainer(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0, 0, 0)
            )
        )
        assertEquals(
            ImageIntakeManager.ContainerKind.JPEG,
            ImageIntakeManager.detectContainer(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0)
            )
        )
        val heicHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte()
        )
        assertEquals(ImageIntakeManager.ContainerKind.HEIF, ImageIntakeManager.detectContainer(heicHeader))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val source = File(app.cacheDir, "external_source.png")
        val bmp = Bitmap.createBitmap(28, 20, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(189, 71, 7))
        FileOutputStream(source).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val staged = ImageIntakeManager.stageIncomingUri(app, Uri.fromFile(source)).getOrThrow()
        assertTrue(staged.stagedFile.isFile)
        assertEquals(28, staged.width)
        assertEquals(20, staged.height)
        assertEquals("PNG", staged.formatLabel)
    }
}
