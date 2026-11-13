package com.tumuyan.ncnn.realsr.core.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun outputNameFormatterAppendsScaleSuffixAndSanitizesNames() {
        assertEquals(
            "IMG_2042_x4.png",
            OutputNameFormatter.buildGalleryFileName("IMG_2042.HEIC", scale = 4, outputExtension = "png")
        )
        assertEquals(
            "portrait_shot_x2.webp",
            OutputNameFormatter.buildGalleryFileName("/sdcard/DCIM/portrait_shot.jpg", scale = 2, outputExtension = "webp")
        )
        assertEquals(
            "Photo_x2.jpg",
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
        val ids = queue.enqueue(req) { _, uri -> uri.lastPathSegment ?: "photo" }
        assertEquals(3, ids.size)

        val initialSnap = queue.snapshot()
        assertEquals(3, initialSnap.size)
        assertEquals("1 of 3", "${initialSnap[0].positionInBatch} of ${initialSnap[0].totalInBatch}")
        assertEquals("2 of 3", "${initialSnap[1].positionInBatch} of ${initialSnap[1].totalInBatch}")
        assertEquals("3 of 3", "${initialSnap[2].positionInBatch} of ${initialSnap[2].totalInBatch}")

        // Poll first item for execution -> marked running
        val firstLease = queue.pollNextLease()
        assertNotNull(firstLease)
        assertEquals("a.png", firstLease!!.entry.fileNameHint)
        assertEquals(1, firstLease.positionInSession)
        assertEquals(3, firstLease.totalInSession)
        assertTrue(queue.snapshot().first().isRunning)

        // Cancel middle queued item while first is running
        assertEquals(EngineJobQueue.CancelItemResult.REMOVED_PENDING, queue.cancelItem(ids[1]))
        assertEquals(EngineJobQueue.CancelItemResult.CANCEL_RUNNING, queue.cancelItem(ids[0]))
        assertEquals(2, queue.snapshot().size)

        // Finish first item -> next polled item is c.png ("2 of 2")
        queue.markActiveFinished()
        val secondLease = queue.pollNextLease()
        assertNotNull(secondLease)
        assertEquals("c.png", secondLease!!.entry.fileNameHint)
        assertEquals(2, secondLease.positionInSession)
        assertEquals(2, secondLease.totalInSession)

        queue.markActiveFinished()
        assertNull(queue.pollNextLease())
        assertTrue(queue.isIdle())
    }

    @Test
    fun imageIntakeManagerDetectsMagicBytesAndStagesIncomingUri() = runBlocking {
        assertEquals(
            ImageIntakeManager.DetectedContainer.PNG,
            ImageIntakeManager.detectContainer(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0, 0, 0),
                12
            )
        )
        assertEquals(
            ImageIntakeManager.DetectedContainer.JPEG,
            ImageIntakeManager.detectContainer(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0),
                12
            )
        )
        val heicHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte()
        )
        assertEquals(
            ImageIntakeManager.DetectedContainer.HEIF,
            ImageIntakeManager.detectContainer(heicHeader, heicHeader.size)
        )

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
