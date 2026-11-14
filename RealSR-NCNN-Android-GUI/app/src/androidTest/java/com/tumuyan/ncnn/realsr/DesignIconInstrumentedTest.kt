package com.tumuyan.ncnn.realsr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tumuyan.ncnn.realsr.design.RsTheme
import com.tumuyan.ncnn.realsr.design.icons.AppGlyphs
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-run glyphs get a pixel test: they are the only illustration before the app is usable, so a
 * generated vector that compiles but rasterises nothing must fail the build instead of shipping an
 * empty square.
 *
 * The second test is an audit of the whole generated Lucide set — it reports (`AUDIT:` lines, which
 * the CI summary relay quotes) every glyph whose geometry parses to zero path nodes.
 */
@RunWith(AndroidJUnit4::class)
class DesignIconInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun photoGlyphRasterisesInkPixels() = assertGlyphDraws(AppGlyphs.Photo, "photo")

    @Test
    fun bellGlyphRasterisesInkPixels() = assertGlyphDraws(AppGlyphs.Bell, "bell")

    @Test
    fun generatedLucideGlyphsAllCarryGeometry() {
        val blank = mutableListOf<String>()
        var total = 0
        for ((name, glyph) in lucideGlyphs()) {
            total++
            val children = glyph.root.count()
            val nodes = glyph.root.sumOf { node -> (node as? VectorPath)?.pathData?.size ?: 0 }
            println("AUDIT: $name children=$children nodes=$nodes")
            if (nodes == 0) blank += name
        }
        println("AUDIT: checked $total glyphs, blank=${blank.size} ${blank.joinToString(",")}")
        assertTrue("Generated glyphs without geometry: ${blank.joinToString(", ")}", blank.isEmpty())
    }

    /**
     * Renders one glyph white-on-white-free (black stroke on white) at 96 dp and counts dark pixels
     * in the captured root. `onRoot()` is the capture path the smoke test already relies on.
     */
    private fun assertGlyphDraws(glyph: ImageVector, label: String) {
        composeRule.setContent {
            RsTheme(darkTheme = false) {
                Box(Modifier.size(140.dp).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(glyph, contentDescription = null, tint = Color.Black, modifier = Modifier.size(96.dp))
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        var ink = 0
        var white = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                val luminance = (color.red + color.green + color.blue) / 3f
                if (color.alpha > 0.5f && luminance < 0.35f) ink++
                if (color.alpha > 0.5f && luminance > 0.9f) white++
            }
        }
        println("AUDIT: glyph=$label ink=$ink white=$white")
        assertTrue("$label glyph drew no ink (capture ${pixels.width}x${pixels.height}, white=$white)", ink > 150)
    }

    private fun lucideGlyphs(): List<Pair<String, ImageVector>> =
        LucideIcons::class.java.methods
            .filter { method ->
                method.name.startsWith("get") &&
                    method.parameterCount == 0 &&
                    ImageVector::class.java.isAssignableFrom(method.returnType)
            }
            .map { method -> method.name.removePrefix("get") to (method.invoke(LucideIcons) as ImageVector) }
            .sortedBy { it.first }
}
