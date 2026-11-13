package com.tumuyan.ncnn.realsr.core.engine

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tumuyan.ncnn.realsr.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CropPreviewAndLocalizationTest {

    @Test
    fun extractHighDetailCropSelectsTexturedRegionOverFlatRegions() {
        // 600x600 image that is flat everywhere except bottom-right quadrant (checkerboard high gradient)
        val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(240, 238, 232))
        for (y in 340 until 540) {
            for (x in 340 until 540) {
                val dark = ((x / 4) + (y / 4)) % 2 == 0
                bmp.setPixel(x, y, if (dark) Color.rgb(20, 20, 19) else Color.rgb(250, 249, 245))
            }
        }

        val crop = CropPreviewEngine.extractHighDetailCrop(bmp, maxCropEdge = 200)
        assertEquals(200, crop.width)
        assertEquals(200, crop.height)

        // Verify the extracted crop contains the high-contrast dark pixels
        var darkPixels = 0
        for (y in 0 until crop.height step 4) {
            for (x in 0 until crop.width step 4) {
                if (Color.red(crop.getPixel(x, y)) < 50) darkPixels++
            }
        }
        assertTrue("Expected textured patch with dark checkerboard pixels, got $darkPixels", darkPixels > 50)
    }

    @Test
    fun generateCropPreviewProducesScaledOutputFile() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val inputFile = File(app.cacheDir, "preview_src.png")
        val src = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888)
        src.eraseColor(Color.rgb(189, 71, 7))
        FileOutputStream(inputFile).use { src.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val res = CropPreviewEngine.generateCropPreview(
            context = app,
            sourceUri = Uri.fromFile(inputFile),
            preset = UpscalePreset.INSTANT,
            scale = 2,
            preferences = EnginePreferences(),
            modelSpec = null,
            modelsRootDir = File(app.filesDir, "models")
        ).getOrThrow()

        assertTrue(res.beforeCropFile.isFile && res.beforeCropFile.length() > 0L)
        assertTrue(res.afterCropFile.isFile && res.afterCropFile.length() > 0L)
        assertEquals(240, res.outputWidth)
        assertEquals(180, res.outputHeight)
    }

    @Test
    fun hindiStringResourcesResolveCleanDevanagariTranslations() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        val hiConfig = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("hi"))
        }
        val app = base.createConfigurationContext(hiConfig)
        assertEquals("स्टूडियो", app.getString(R.string.ui_nav_studio))
        assertEquals("लाइब्रेरी", app.getString(R.string.ui_nav_library))
        assertEquals("मॉडल्स", app.getString(R.string.ui_nav_models))
        assertEquals("एन्हांस करें", app.getString(R.string.ui_enhance))
        assertEquals("फ़ोटो चुनें", app.getString(R.string.ui_choose_photo))
    }
}
