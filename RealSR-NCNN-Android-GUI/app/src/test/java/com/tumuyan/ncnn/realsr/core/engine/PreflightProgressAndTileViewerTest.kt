package com.tumuyan.ncnn.realsr.core.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.tumuyan.ncnn.realsr.core.engine.OomSafeImageDecoders
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
class PreflightProgressAndTileViewerTest {

    @Test
    fun engineProgressParserHandlesAllNativeCliFormatsAndRejectsNonProgressLines() {
        val realsr = EngineProgressParser.parseLine(" 42.50%\t[ 3.12s / 4.20 ETA]")
        assertNotNull(realsr)
        assertEquals(0.425f, realsr!!.fraction, 0.001f)
        assertEquals(4200L, realsr.nativeEtaMillis)

        val anime4k = EngineProgressParser.parseLine("  10.00%   \r  75.00%     elpsed:    6.00s    remaining:    2.00s\r")
        assertNotNull(anime4k)
        assertEquals(0.75f, anime4k!!.fraction, 0.001f)
        assertEquals(2000L, anime4k.nativeEtaMillis)

        val realcugan = EngineProgressParser.parseLine("63.80%\n")
        assertNotNull(realcugan)
        assertEquals(0.638f, realcugan!!.fraction, 0.001f)
        assertNull(realcugan.nativeEtaMillis)

        assertNull(EngineProgressParser.parseLine("[0 GeForce] queueC=0[1]"))
        assertNull(EngineProgressParser.parseLine("./resize-ncnn -i input_100%.png -o out.png -s 4"))
    }

    @Test
    fun preflightAnalyzerPassesNormalJobsAndWarnsWithMitigationsOnHighMegapixelOrLowDisk() {
        val gb = 1024L * 1024L * 1024L
        val mb = 1024L * 1024L

        val safe = PreflightAnalyzer.evaluate(
            inputDimensions = listOf(1280 to 960),
            scale = 4,
            preset = UpscalePreset.ANIME,
            tileSize = 0,
            outputFormat = OutputFormat.PNG,
            deviceRamBytes = 6L * gb,
            freeStorageBytes = 8L * gb
        )
        assertEquals(PreflightVerdict.Safe, safe)

        val hugeWarning = PreflightAnalyzer.evaluate(
            inputDimensions = listOf(4032 to 3024),
            scale = 4,
            preset = UpscalePreset.ANIME,
            tileSize = 0,
            outputFormat = OutputFormat.PNG,
            deviceRamBytes = 6L * gb,
            freeStorageBytes = 8L * gb
        )
        assertTrue(hugeWarning is PreflightVerdict.Advisory)
        val advisory = hugeWarning as PreflightVerdict.Advisory
        assertEquals(PreflightSeverity.WARNING, advisory.severity)
        assertTrue(advisory.canProceedAnyway)
        assertTrue(advisory.mitigations.contains(PreflightMitigation.LowerScaleTo2x))
        assertTrue(advisory.mitigations.any { it is PreflightMitigation.ProcessInTiles })
        assertTrue(advisory.mitigations.any { it is PreflightMitigation.SmallerTileSize })

        val lowDisk = PreflightAnalyzer.evaluate(
            inputDimensions = listOf(1280 to 960),
            scale = 4,
            preset = UpscalePreset.INSTANT,
            tileSize = 0,
            outputFormat = OutputFormat.PNG,
            deviceRamBytes = 6L * gb,
            freeStorageBytes = 10L * mb
        )
        assertTrue(lowDisk is PreflightVerdict.Advisory)
        val diskAdvisory = lowDisk as PreflightVerdict.Advisory
        assertEquals(PreflightSeverity.BLOCKING_STORAGE, diskAdvisory.severity)
        assertFalse(diskAdvisory.canProceedAnyway)
    }

    @Test
    fun oomSafeImageDecodersDownsamplesLargeImagesAndDecodesZoomedRegionTiles() {
        assertEquals(8, OomSafeImageDecoders.computePowerOfTwoSampleSize(12_000, 9_000, maxEdgePx = 2048))
        assertEquals(1, OomSafeImageDecoders.computePowerOfTwoSampleSize(1_600, 1_200, maxEdgePx = 2048))

        val fitRect = OomSafeImageDecoders.computeVisibleSourceRect(
            imageWidth = 4000,
            imageHeight = 3000,
            viewportWidth = 1000f,
            viewportHeight = 750f,
            zoom = 1f,
            panX = 0f,
            panY = 0f
        )
        assertEquals(Rect(0, 0, 4000, 3000), fitRect)

        val zoomedRect = OomSafeImageDecoders.computeVisibleSourceRect(
            imageWidth = 4000,
            imageHeight = 3000,
            viewportWidth = 1000f,
            viewportHeight = 750f,
            zoom = 4f,
            panX = 0f,
            panY = 0f
        )
        assertEquals(Rect(1500, 1125, 2500, 1875), zoomedRect)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val hiResFile = File(app.cacheDir, "tile_test_image.png")
        val srcBmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        srcBmp.eraseColor(Color.rgb(189, 71, 7))
        FileOutputStream(hiResFile).use { srcBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        srcBmp.recycle()

        val downsampled = OomSafeImageDecoders.decodeDownsampledPreview(hiResFile, maxEdgePx = 256)
        assertNotNull(downsampled)
        assertEquals(640, downsampled!!.intrinsicWidth)
        assertEquals(480, downsampled.intrinsicHeight)
        assertTrue(downsampled.bitmap.width <= 256)

        val tile = OomSafeImageDecoders.decodeHighResRegionTile(
            hiResFile,
            Rect(160, 120, 320, 240),
            maxTileEdgePx = 512
        )
        assertNotNull(tile)
        assertEquals(160, tile!!.bitmap.width)
        assertEquals(120, tile.bitmap.height)
    }
}
