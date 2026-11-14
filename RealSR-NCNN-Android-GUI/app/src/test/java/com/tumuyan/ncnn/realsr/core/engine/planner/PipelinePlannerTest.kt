package com.tumuyan.ncnn.realsr.core.engine.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val device = DeviceProfile(
    tier = DeviceTier.STANDARD,
    ramGb = 8.0,
    cores = 8,
    glEsVersion = "3.2",
    vulkanLevel = 1,
    freeStorageGb = 40.0,
    totalStorageGb = 128.0,
    binariesPresent = true,
)

private fun starterModels(vararg withSecPerMP: Pair<String, Double> = emptyArray()): List<PlannerModel> {
    val secMap = withSecPerMP.toMap()
    return listOf(
        PlannerModel("models-upconv_7_photo", "Gentle Photo 2x", PlannerModel.ModelKind.PHOTO, listOf(2, 4), 5.5, 0, "MIT", required = true, secPerMP = secMap["models-upconv_7_photo"]),
        PlannerModel("models-upconv_7_anime_style_art_rgb", "Anime & Illustration", PlannerModel.ModelKind.ANIME, listOf(2, 4), 5.5, 0, "MIT", required = true, secPerMP = secMap["models-upconv_7_anime_style_art_rgb"]),
        PlannerModel("models-RealeSR-general-v3", "Everyday Photo v3", PlannerModel.ModelKind.PHOTO, listOf(4), 2.4, 0, "BSD-3-Clause", required = true, secPerMP = secMap["models-RealeSR-general-v3"]),
        PlannerModel("models-DF2K", "Maximum Detail DF2K", PlannerModel.ModelKind.PHOTO, listOf(4), 33.5, 1, "BSD-3-Clause", required = false, secPerMP = secMap["models-DF2K"]),
        PlannerModel("models-MNN-RealESRGAN_x4plus_anime_6B", "Anime 6B", PlannerModel.ModelKind.ANIME, listOf(4), 17.9, 1, "Apache-2.0", required = false, secPerMP = secMap["models-MNN-RealESRGAN_x4plus_anime_6B"]),
        PlannerModel("models-srmd", "Clean Studio SRMD", PlannerModel.ModelKind.PHOTO, listOf(2, 3, 4), 18.4, 2, "MIT", required = false, secPerMP = secMap["models-srmd"]),
    )
}

private fun plan(
    w: Int = 1200,
    h: Int = 800,
    ext: String = "jpg",
    sizeMB: Double = 2.0,
    mode: ModeId = ModeId.UPSCALE,
    prefs: JobPrefs = JobPrefs.default(),
    installed: List<PlannerModel> = starterModels(),
    dev: DeviceProfile = device,
    opts: PipelinePlanner.Opts = PipelinePlanner.Opts(),
) = PipelinePlanner.makePlan(w, h, ext, sizeMB, mode, prefs, installed, dev, opts)

class PipelinePlannerTest {

    @Test
    fun defaultUpscalePlanUsesStarterModelScale2Gpu() {
        val p = plan()
        assertEquals(Level.DEFAULT, p.level)
        assertEquals(2, p.scale)
        assertEquals("models-upconv_7_photo", p.model.id)
        assertTrue(p.isAuto)
        assertEquals(Plan.ComputeKind.GPU, p.compute)
        assertEquals(384, p.tilePx)
        assertEquals(2400, p.outW)
        assertEquals(1600, p.outH)
        assertEquals(Check.Green, p.check)
        assertEquals(1200, p.inW) // not capped
        assertFalse(p.inW != 1200)
        assertEquals("Used as is", p.inputNote)
    }

    @Test
    fun superModeUsesHeavyWantedModelAndFallsBackWithWanted() {
        // DF2K not installed -> fallback + wanted
        val starterOnly = starterModels().filter { it.required }
        val p = plan(mode = ModeId.SUPER, installed = starterOnly)
        assertEquals("models-upconv_7_photo", p.model.id)
        assertEquals("models-DF2K", p.wanted?.id)

        // DF2K installed -> wanted resolved
        val p2 = plan(mode = ModeId.SUPER)
        assertEquals("models-DF2K", p2.model.id)
        assertNull(p2.wanted)
    }

    @Test
    fun superAnimeRecommendsAnime6B() {
        val p = plan(mode = ModeId.SUPER, opts = PipelinePlanner.Opts(autoType = ImageType.ANIME))
        assertEquals("models-MNN-RealESRGAN_x4plus_anime_6B", p.model.id)
    }

    @Test
    fun hugeInputInUpscaleModeIsOptimizedToCap() {
        val p = plan(w = 6000, h = 4000, sizeMB = 18.4)
        assertTrue(p.overLimit)
        assertTrue(p.optimized)
        assertTrue(p.inMP <= 4.0 + 1e-6)
        assertTrue(p.inputNote.startsWith("Large photo optimized for your device"))
    }

    @Test
    fun superModeNeverOptimizesInput() {
        val p = plan(w = 6000, h = 4000, mode = ModeId.SUPER)
        assertFalse(p.overLimit)
        assertEquals(6000, p.inW)
        assertEquals("Original file, no compression", p.inputNote)
    }

    @Test
    fun superCapTriggersRedCheckWithMaxScaleSuggestion() {
        // 5x on a big input blows the 128 MP super cap.
        val prefs = JobPrefs(level = Level.MANUAL, scale = 5, modelId = "auto")
        val p = plan(w = 3200, h = 2400, mode = ModeId.SUPER, prefs = prefs)
        val check = p.check
        assertTrue(check is Check.Red)
        check as Check.Red
        assertEquals("This is too large for your device.", check.title)
        assertNotNull(check.maxScale)
        assertTrue(check.maxScale!! < 5)
    }

    @Test
    fun upscaleCapTriggersRedCheckForHugeOutput() {
        // input capped to 4 MP, 5x output = ~100 MP > 64 MP cap -> red with 4x suggestion
        val prefs = JobPrefs(level = Level.MANUAL, scale = 5)
        val p = plan(w = 6000, h = 4000, prefs = prefs)
        assertTrue(p.check is Check.Red)
        assertEquals(4, (p.check as Check.Red).maxScale)
        // default 2x on a capped input is comfortably green
        val ok = plan(w = 6000, h = 4000)
        assertEquals(Check.Green, ok.check)
        // KEEP_FULL + 5x -> way over cap
        val keep = plan(w = 6000, h = 4000, prefs = prefs, opts = PipelinePlanner.Opts(large = PipelinePlanner.LargeOption.KEEP_FULL))
        assertTrue(keep.check is Check.Red)
    }

    @Test
    fun webPOverMaxSideReturnsAmberFormat() {
        // Need outMP under cap but side > 16383: impossible for 16383+ since outW*outH grows too;
        // cap is 64 MP => max side ~ 8000 MP? Use super mode with native 2x and 4-sided limit.
        // Construct legal case: 9000x1000 px @2x = 18000 px wide, 18 MP out -> under super cap.
        val prefs = JobPrefs(level = Level.MANUAL, scale = 2, format = OutputFormatPref.WEBP)
        val p = plan(w = 9000, h = 1000, mode = ModeId.SUPER, prefs = prefs)
        assertEquals(18000, p.outW)
        val check = p.check
        assertTrue("expected amber format check, got $check", check is Check.Amber)
        check as Check.Amber
        assertEquals(Check.AmberKind.FORMAT, check.kind)
        assertEquals("Too large for WebP.", check.title)
    }

    @Test
    fun webpWidthJustUnderLimitStaysGreen() {
        val prefs = JobPrefs(level = Level.MANUAL, scale = 2, format = OutputFormatPref.WEBP)
        val p = plan(w = 8000, h = 1000, mode = ModeId.SUPER, prefs = prefs) // 16000 x 2000
        assertEquals(Check.Green, p.check)
    }

    @Test
    fun fiveXRunsFourXWithPostResize() {
        val prefs = JobPrefs(level = Level.MANUAL, scale = 5)
        val p = plan(prefs = prefs) // RealeSR is 4x-only
        assertEquals(5, p.scale)
        assertEquals(4, p.runScale)
        assertEquals(1.25, p.postResize!!, 1e-9)
        assertEquals(6000, p.outW)
        assertEquals(4000, p.outH)
    }

    @Test
    fun threeXOnFourXOnlyModelDownsamplesFromFour() {
        // RealeSR supports only 4x. Requested 3x: no supported scale below 3, so run 4x and resample down.
        val prefs = JobPrefs(level = Level.MANUAL, scale = 3, modelId = "models-RealeSR-general-v3")
        val p = plan(prefs = prefs)
        assertEquals(4, p.runScale)
        assertEquals(0.75, p.postResize!!, 1e-9)
    }

    @Test
    fun threeXUsesSrmdNativelyWhenInstalledAndSelected() {
        val prefs = JobPrefs(level = Level.MANUAL, scale = 3, modelId = "models-srmd")
        val p = plan(prefs = prefs)
        assertEquals(3, p.runScale)
        assertNull(p.postResize)
    }

    @Test
    fun animeTwoAndFourNativeNoPostResize() {
        val prefs = JobPrefs(level = Level.MANUAL, scale = 2, modelId = "models-upconv_7_anime_style_art_rgb")
        val p = plan(prefs = prefs, opts = PipelinePlanner.Opts(autoType = ImageType.ANIME))
        assertEquals(2, p.runScale)
        assertNull(p.postResize)
    }

    @Test
    fun animeThreeFallsBackToTwoPlusResize() {
        val prefs = JobPrefs(level = Level.MANUAL, scale = 3, modelId = "models-upconv_7_anime_style_art_rgb")
        val p = plan(prefs = prefs, opts = PipelinePlanner.Opts(autoType = ImageType.ANIME))
        assertEquals(2, p.runScale)
        assertEquals(1.5, p.postResize!!, 1e-9)
    }

    @Test
    fun etaIsNullUntilSamplesExist() {
        val p = plan()
        assertNull(p.etaSec)
    }

    @Test
    fun etaUsesRollingAverageWithCpuFactor() {
        val installed = starterModels("models-upconv_7_photo" to 2.0) // 2 s per out MP
        val p = plan(installed = installed, opts = PipelinePlanner.Opts(compute = ComputePref.GPU))
        assertNotNull(p.etaSec)
        // outMP = 3.84 ; base = 7.68 ; +2 = ~10 s
        assertTrue(p.etaSec!! in 8..12)
        val cpu = plan(installed = installed, opts = PipelinePlanner.Opts(compute = ComputePref.CPU))
        assertTrue(cpu.etaSec!! > p.etaSec!!)
    }

    @Test
    fun ttaIsNotModelled() {
        // JobPrefs has no TTA field by design (no native support). Guard the API surface.
        assertFalse(JobPrefs::class.java.declaredFields.any { it.name.equals("tta", ignoreCase = true) })
    }

    @Test
    fun overlapIsNotModelled() {
        assertFalse(JobPrefs::class.java.declaredFields.any { it.name.equals("overlap", ignoreCase = true) })
    }

    @Test
    fun tileGridCountsUseNativeScaleNotRequested() {
        val prefs = JobPrefs(level = Level.ADVANCED, scale = 5, tile = TilePreset.FAST, modelId = "models-RealeSR-general-v3")
        val p = plan(w = 800, h = 800, prefs = prefs)
        // native 4x => 3200px / 384 -> 9 cols/rows = 81 tiles
        assertEquals(9, p.cols)
        assertEquals(81, p.tiles)
    }

    @Test
    fun customTileFactorApplies() {
        val prefs = JobPrefs(level = Level.ADVANCED, tile = TilePreset.CUSTOM, tileCustom = 128, scale = 2)
        val p = plan(prefs = prefs, installed = starterModels("models-upconv_7_photo" to 2.0))
        assertEquals(128, p.tilePx)
        assertNotNull(p.etaSec)
    }

    @Test
    fun maxTilePresetDisabledForNonPowerfulTier() {
        // Planner itself doesn't gate MAX by tier (UI does); but tilePx==0 means "no tiling" and must not crash.
        val prefs = JobPrefs(level = Level.ADVANCED, tile = TilePreset.MAX, scale = 2)
        val p = plan(prefs = prefs)
        assertEquals(0, p.tilePx)
        assertEquals(1, p.tiles)
    }

    @Test
    fun maxTilePresetInfoCarriesDesignDisabledMessage() {
        val max = TILE_PRESETS.first { it.id == TilePreset.MAX }
        assertEquals("Maximum needs a Powerful device with 12 GB or more memory.", max.disabled)
        assertTrue(TILE_PRESETS.first { it.recommended || it.id == TilePreset.FAST }.recommended)
    }

    @Test
    fun outputFormatResolutionFollowsDesignRules() {
        // super -> PNG by default
        val sup = plan(mode = ModeId.SUPER)
        assertEquals(Plan.OutputFormat.PNG, sup.format)
        assertEquals("png", sup.ext)
        // source jpg kept in upscale mode
        val up = plan()
        assertEquals(Plan.OutputFormat.JPEG, up.format)
        // source png stays png
        val png = plan(ext = "png")
        assertEquals(Plan.OutputFormat.PNG, png.format)
        // advanced override respected
        val webp = plan(prefs = JobPrefs(level = Level.ADVANCED, format = OutputFormatPref.WEBP, scale = 2))
        assertEquals(Plan.OutputFormat.WEBP, webp.format)
    }

    @Test
    fun resultFileNameIsDeterministic() {
        val at = java.time.LocalDateTime.of(2026, 11, 13, 21, 7)
        assertEquals("IMG_0001_RealScale_4x_20261113-2107.png", resultFileName("IMG_0001.jpg", 4, "png", at))
        assertEquals("scan_RealScale_2x_20261113-2107.webp", resultFileName("scan.webp", 2, "webp", at))
    }

    @Test
    fun formattingHelpersMatchDesign() {
        assertEquals("3 min", fmtEta(170))
        assertEquals("50 s", fmtEta(48))
        assertEquals("1.5 h", fmtEta(5500))
        assertEquals("2 min 10 s", fmtLeft(130))
        assertEquals("48 s", fmtLeft(48))
        assertEquals("0:00", fmtClock(0))
        assertEquals("12:05", fmtClock(725))
        assertEquals("3.8 MP", fmtMP(3.84))
        assertEquals("4 MP", fmtMP(4.2))
        assertEquals("2.4 MB", fmtMB(2.4))
        assertEquals("12 MB", fmtMB(12.0))
        assertEquals("1.2 GB", fmtMB(1234.0))
    }

    @Test
    fun modelCatalogMapperMapsManifestCategoriesAndStarters() {
        val item = com.tumuyan.ncnn.realsr.core.model.ModelCatalogItem(
            id = "models-DF2K",
            displayName = "Maximum Detail DF2K",
            description = "d",
            category = "MAXIMUM",
            recommendedForPreset = null,
            engineTool = "realsr-ncnn",
            entryFile = null,
            defaultScale = 4,
            supportedScales = listOf(4),
            denoiseLevel = null,
            totalBytes = 33_500_000,
            files = listOf(
                com.tumuyan.ncnn.realsr.core.model.ModelFileSpec("a.param", "https://example.com/a.param", 1_000_000, "0".repeat(64)),
                com.tumuyan.ncnn.realsr.core.model.ModelFileSpec("a.bin", "https://example.com/a.bin", 32_500_000, "1".repeat(64)),
            ),
        )
        val mapped = ModelCatalogMapper().toPlannerModel(item)
        assertEquals(PlannerModel.ModelKind.OTHER, mapped.kind)
        assertEquals(33.5, mapped.sizeMB, 1e-6)
        assertFalse(mapped.required)
        assertNull(mapped.secPerMP)
    }
}
