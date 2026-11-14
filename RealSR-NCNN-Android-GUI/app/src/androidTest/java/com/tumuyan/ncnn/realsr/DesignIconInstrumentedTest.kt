package com.tumuyan.ncnn.realsr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tumuyan.ncnn.realsr.design.RsTheme
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two glyphs on the onboarding permission screens are the only icons a first-run user sees
 * before the app is usable, so they get a pixel test: the generated Lucide vectors must actually
 * rasterise ink, not just compile.
 */
@RunWith(AndroidJUnit4::class)
class DesignIconInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun photoAndBellGlyphsRasteriseInkPixels() {
        composeRule.setContent {
            RsTheme(darkTheme = false) {
                Column(Modifier.testTag(MARKS)) {
                    Box(Modifier.size(48.dp).background(Color.White)) {
                        Icon(LucideIcons.Images, contentDescription = null, tint = Color.Black, modifier = Modifier.size(48.dp))
                    }
                    Box(Modifier.size(48.dp).background(Color.White)) {
                        Icon(LucideIcons.Bell, contentDescription = null, tint = Color.Black, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onNodeWithTag(MARKS).captureToImage().toPixelMap()
        var ink = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.alpha > 0.5f && (color.red + color.green + color.blue) / 3f < 0.5f) ink++
            }
        }
        // A 48 dp stroked glyph at 2 px per dp covers far more than 120 pixels; an empty vector is 0.
        assertTrue("Photo + notification glyphs drew only $ink ink pixels", ink > 120)
    }

    private companion object {
        const val MARKS = "primer_marks"
    }
}
