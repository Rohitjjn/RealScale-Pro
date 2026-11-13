package com.tumuyan.ncnn.realsr.core.engine

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageContentClassifierTest {

    @Test
    fun flatCelShadedBitmapSuggestsAnimeWhileTexturedNaturalPhotoSuggestsPhoto() {
        // 1. Flat cel-shaded illustration with crisp color blocks -> ANIME
        val animeBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val c = when {
                    x == 32 || y == 32 -> Color.rgb(15, 15, 18) // crisp ink contour
                    x < 32 && y < 32 -> Color.rgb(245, 210, 195)
                    x >= 32 && y < 32 -> Color.rgb(88, 132, 214)
                    else -> Color.rgb(250, 249, 245)
                }
                animeBmp.setPixel(x, y, c)
            }
        }
        assertEquals(PresetId.ANIME, ImageContentClassifier.classifyBitmap(animeBmp))
        animeBmp.recycle()

        // 2. Continuous natural gradient + realistic camera sensor micro-variance -> PHOTO
        val rng = Random(42)
        val photoBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val r = (40 + x * 2 + rng.nextInt(-9, 10)).coerceIn(0, 255)
                val g = (55 + y * 2 + rng.nextInt(-9, 10)).coerceIn(0, 255)
                val b = (70 + (x + y) + rng.nextInt(-9, 10)).coerceIn(0, 255)
                photoBmp.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        assertEquals(PresetId.PHOTO, ImageContentClassifier.classifyBitmap(photoBmp))
        photoBmp.recycle()

        // 3. Smooth sky gradient with gentle sensor variance (+/- 4) -> PHOTO (not falsely Anime)
        val skyBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val r = (110 + y + rng.nextInt(-4, 5)).coerceIn(0, 255)
                val g = (155 + y + rng.nextInt(-4, 5)).coerceIn(0, 255)
                val b = (215 + y / 2 + rng.nextInt(-4, 5)).coerceIn(0, 255)
                skyBmp.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        assertEquals(PresetId.PHOTO, ImageContentClassifier.classifyBitmap(skyBmp))
        skyBmp.recycle()
    }

    @Test
    fun buildLiveEnhanceSummaryFormatsTabularDimensionsTimeAndSize() {
        val single = SelectionInspectionSummary(
            count = 1,
            primaryWidth = 1080,
            primaryHeight = 1440,
            totalPixels = 1080L * 1440L,
            suggestedPreset = PresetId.PHOTO
        )
        val summary = ImageContentClassifier.buildLiveEnhanceSummary(
            inspection = single,
            scale = 4,
            preset = UpscalePreset.PHOTO,
            format = OutputFormat.PNG,
            backend = ExecutionBackend.AUTO_GPU
        )
        assertTrue("Expected dimensions in '$summary'", summary.contains("1080×1440 → 4320×5760"))
        assertTrue("Expected ~time in '$summary'", summary.contains("~"))
        assertTrue("Expected MB estimate in '$summary'", summary.contains("MB"))

        val batch = SelectionInspectionSummary(
            count = 4,
            primaryWidth = 1000,
            primaryHeight = 1000,
            totalPixels = 4_000_000L,
            suggestedPreset = PresetId.ANIME
        )
        val batchSummary = ImageContentClassifier.buildLiveEnhanceSummary(
            inspection = batch,
            scale = 2,
            preset = UpscalePreset.ANIME,
            format = OutputFormat.WEBP,
            backend = ExecutionBackend.AUTO_GPU
        )
        assertTrue("Expected Batch of 4 in '$batchSummary'", batchSummary.startsWith("Batch of 4 · 2×"))
    }
}
