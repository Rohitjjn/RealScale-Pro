package dev.realscale.pro

import dev.realscale.pro.core.engine.*
import dev.realscale.pro.data.models.ModelCatalog
import org.junit.Assert.*
import org.junit.Test

class NcnnCommandBuilderTest {
    @Test fun upscaleCommandMatchesUpstreamPreset() {
        assertEquals(listOf("./realsr-ncnn", "-i", "input.png", "-o", "output.png", "-m", "/data/models/models-DF2K", "-s", "4"),
            NcnnCommandBuilder.upscale("realsr-ncnn", "input.png", "output.png", "/data/models/models-DF2K", 4))
    }
    @Test fun classicalUsesLanczos() {
        assertEquals("lanczos", NcnnCommandBuilder.classicalResize("input.png", "output.png", 2)[6])
    }
    @Test fun parsesPercentProgress() {
        assertEquals(0.42f, NcnnCommandBuilder.parseProgress("42.00%")!!, 0.001f)
        assertNull(NcnnCommandBuilder.parseProgress("loading model"))
    }
}

class LanczosResamplerTest {
    @Test fun doublesDimensionsAndKeepsPixels() {
        val src = IntArray(4) { 0xFF804020.toInt() }
        val (dst, size) = LanczosResampler.resize(src, 2, 2, 2)
        assertArrayEquals(intArrayOf(4, 4), size)
        assertEquals(16, dst.size)
        dst.forEach { assertEquals(0xFF, it ushr 24) }
    }
    @Test fun kernelCenterIsOne() = assertEquals(1.0, LanczosResampler.kernel(0.0), 1e-9)
}

class ModelCatalogTest {
    @Test fun allPacksUseHttpsHfUrlsAndNcnnPairs() {
        ModelCatalog.packs.forEach { p ->
            p.files.forEach { f -> assertTrue(p.url(f).startsWith("https://huggingface.co/tumuyan2/realsr-models/resolve/main/")) }
            assertTrue(p.files.any { it.endsWith(".bin") } && p.files.any { it.endsWith(".param") })
        }
    }
    @Test fun classicalNeedsNoDownload() {
        assertTrue(ModelCatalog.classical.files.isEmpty())
    }
}
