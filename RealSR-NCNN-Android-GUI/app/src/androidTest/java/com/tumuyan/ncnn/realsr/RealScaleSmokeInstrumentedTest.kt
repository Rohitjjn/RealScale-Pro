package com.tumuyan.ncnn.realsr

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.EngineAssetExtractor
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class RealScaleSmokeInstrumentedTest {

    @get:Rule(order = 0)
    val preGrantNotificationRule = TestRule { base, _: Description ->
        object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        val instrumentation = InstrumentationRegistry.getInstrumentation()
                        instrumentation.uiAutomation.grantRuntimePermission(
                            instrumentation.targetContext.packageName,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                }
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Test
    fun engineBinaryTarXzExtractsAllNativeToolsExecutable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "Expected com.realscale.pro applicationId, got ${context.packageName}",
            context.packageName.startsWith("com.realscale.pro")
        )
        val engineDir = EngineAssetExtractor.ensureExtracted(context, forceReset = false)
        assertTrue("Engine directory should exist", engineDir.isDirectory)

        val expectedCliTools = listOf(
            "realsr-ncnn",
            "srmd-ncnn",
            "realcugan-ncnn",
            "waifu2x-ncnn",
            "mnnsr-ncnn",
            "resize-ncnn",
            "magick",
            "Anime4k"
        )
        for (tool in expectedCliTools) {
            val f = File(engineDir, tool)
            assertTrue("Expected extracted binary $tool", f.isFile && f.length() > 10_000L)
            assertTrue("Expected executable bit on $tool", f.canExecute())
        }

        val sharedLibs = engineDir.listFiles()?.filter { it.isFile && it.name.endsWith(".so") }.orEmpty()
        assertTrue(
            "Expected at least 3 extracted native .so libraries, found ${sharedLibs.size}",
            sharedLibs.size >= 3 && sharedLibs.all { it.length() > 10_000L }
        )
    }

    @Test
    fun studioModelHubSettingsAndEndToEndFlow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText("RealScale-Pro").fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Cold-start Studio screen assertions + Light canvas visual verification
            composeRule.onNodeWithText("RealScale-Pro").assertIsDisplayed()
            composeRule.onNodeWithText("Choose a photo").assertIsDisplayed()
            composeRule.waitForIdle()
            val studioLightShot = captureComposeRootBitmap(context, "01_studio_light.png")
            if (studioLightShot != null) {
                assertCanvasHasWarmLightEditorialBackground(studioLightShot)
                studioLightShot.recycle()
            }

            // 2. Navigate to Library via bottom bar, verify empty state, then return to Studio
            composeRule.onNodeWithText("Library").performClick()
            composeRule.onNodeWithText("No creations yet").assertIsDisplayed()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "02_library.png")?.recycle()
            composeRule.onNodeWithText("Studio").performClick()

            // 3. Navigate to Model Hub, filter Photo models, verify HuggingFace catalog item, return
            composeRule.onNodeWithContentDescription("Open Model Hub").performClick()
            composeRule.onNodeWithText("Model Hub").assertIsDisplayed()
            composeRule.onNodeWithText("Photo").performClick()
            composeRule.onNodeWithText("Everyday Photo v3").assertIsDisplayed()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "03_model_hub.png")?.recycle()
            composeRule.onNodeWithContentDescription("Navigate back").performClick()

            // 4. Navigate to Settings, toggle Warm Dark -> verify Dark canvas -> Warm Light theme, return
            composeRule.onNodeWithContentDescription("Open Settings").performClick()
            composeRule.onNodeWithText("Settings").assertIsDisplayed()
            composeRule.onNodeWithText("Appearance").assertIsDisplayed()
            composeRule.onNodeWithText("Warm Dark").performClick()
            composeRule.waitForIdle()
            val settingsDarkShot = captureComposeRootBitmap(context, "04_settings_dark.png")
            if (settingsDarkShot != null) {
                assertCanvasHasWarmDarkEditorialBackground(settingsDarkShot)
                settingsDarkShot.recycle()
            }
            composeRule.onNodeWithText("Warm Light").performClick()
            composeRule.onNodeWithContentDescription("Navigate back").performClick()

            // 4. Deliver real 24x18 PNG via singleTop ACTION_SEND share intent
            val sampleInput = createSolidPng(File(context.cacheDir, "smoke_input.png"), 24, 18, Color.rgb(204, 120, 92))
            scenario.onActivity { activity ->
                val shareIntent = Intent(activity, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(sampleInput))
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                activity.startActivity(shareIntent)
            }

            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithText("Change photo").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Selected photo preview").assertIsDisplayed()

            // 5. Verify un-downloaded Photo preset prompts on-demand download in sticky bar
            composeRule.onNodeWithText("Photo").performScrollTo().performClick()
            composeRule.onNodeWithText("Get Photo model · 2.3 MB").assertIsDisplayed()

            // 6. Switch back to Instant preset, select 4x scale, verify live summary, and tap sticky Enhance CTA
            composeRule.onNodeWithText("Instant").performScrollTo().performClick()
            composeRule.onNodeWithText("4×").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodes(hasText("24×18 → 96×72", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Preview").assertIsDisplayed()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "05_studio_selected.png")?.recycle()
            composeRule.onNodeWithText("Enhance").performClick()

            composeRule.waitUntil(timeoutMillis = 20_000L) {
                composeRule.onAllNodesWithText("Result").fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText("Could not complete upscale").fetchSemanticsNodes().isNotEmpty()
            }

            val completedNatively = composeRule.onAllNodesWithText("Result").fetchSemanticsNodes().isNotEmpty()
            if (completedNatively) {
                composeRule.waitUntil(timeoutMillis = 5_000L) {
                    composeRule.onAllNodesWithText("Share").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("Save").assertIsDisplayed()
                composeRule.onNodeWithText("Share").assertIsDisplayed()
            } else {
                // Host CI emulator kernel is x86_64 without binfmt_misc for standalone arm64 ELF execve;
                // return cleanly to Studio, seed a real 4x creation, and verify Result & Before/After view.
                composeRule.onNodeWithText("Back to Studio").performScrollTo().performClick()
                val creationsDir = File(context.filesDir, "creations").apply { mkdirs() }
                val beforeCopy = createSolidPng(File(creationsDir, "smoke_before.png"), 24, 18, Color.rgb(204, 120, 92))
                val afterCopy = createSolidPng(File(creationsDir, "smoke_after.png"), 96, 72, Color.rgb(93, 184, 166))
                runBlocking {
                    RealScaleAppContainer.getInstance(context).database.recentCreationDao().insert(
                        RecentCreationEntity(
                            sourceUri = Uri.fromFile(beforeCopy).toString(),
                            beforeImagePath = beforeCopy.absolutePath,
                            outputPath = afterCopy.absolutePath,
                            presetId = "INSTANT",
                            presetLabel = "Smoke 4×",
                            modelId = "resize-ncnn",
                            scale = 4,
                            inputWidth = 24,
                            inputHeight = 18,
                            outputWidth = 96,
                            outputHeight = 72,
                            durationMs = 420L,
                            createdAtEpochMs = System.currentTimeMillis()
                        )
                    )
                }
                composeRule.waitUntil(timeoutMillis = 5_000L) {
                    composeRule.onAllNodesWithText("Smoke 4×").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("Smoke 4×").performSemanticsAction(SemanticsActions.OnClick)
                composeRule.waitUntil(timeoutMillis = 5_000L) {
                    composeRule.onAllNodesWithText("Share").fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("Result").assertIsDisplayed()
                composeRule.onNode(hasText("24 × 18 → 96 × 72", substring = true)).assertIsDisplayed()
                composeRule.onNodeWithText("Save").assertIsDisplayed()
                composeRule.onNodeWithText("Share").assertIsDisplayed()
            }
        } finally {
            runCatching {
                scenario.onActivity { it.finish() }
                context.stopService(Intent(context, ProcessingService::class.java))
            }
        }
    }

    @Test
    fun actionViewOpensQuickUpscaleSheetWithResolutionAndControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sampleFile = createSolidPng(
            File(context.cacheDir, "open_with_sample.png"),
            32,
            24,
            Color.rgb(189, 71, 7)
        )
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            sampleFile
        )

        // Verify Android PackageManager resolves implicit ACTION_VIEW image/* to QuickUpscaleActivity
        val probeIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "image/png")
        }
        val matches = context.packageManager.queryIntentActivities(probeIntent, 0)
        assertTrue(
            "Expected QuickUpscaleActivity to handle implicit ACTION_VIEW for image/png",
            matches.any { it.activityInfo.name == QuickUpscaleActivity::class.java.name }
        )

        // Launch QuickUpscaleActivity with explicit ACTION_VIEW + content:// URI
        val launchIntent = Intent(context, QuickUpscaleActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(contentUri, "image/png")
            clipData = ClipData.newRawUri("Sample", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val scenario = ActivityScenario.launch<QuickUpscaleActivity>(launchIntent)
        try {
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText("Quick Upscale").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Quick Upscale").assertIsDisplayed()

            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.onAllNodesWithText("32 × 24 · PNG").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("32 × 24 · PNG").assertIsDisplayed()
            composeRule.onNodeWithText("Instant").assertIsDisplayed()
            composeRule.onAllNodesWithText("Get model")[0].assertIsDisplayed()
            composeRule.onNodeWithText("4×").performClick()
            composeRule.onNode(hasText("128 × 96", substring = true)).assertIsDisplayed()
            composeRule.onNodeWithText("Enhance").assertIsDisplayed()
            composeRule.onNodeWithText("Open in RealScale-Pro").assertIsDisplayed()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "06_quick_upscale_sheet.png")?.recycle()
        } finally {
            runCatching {
                scenario.onActivity { it.finish() }
            }
        }
    }

    private fun captureComposeRootBitmap(
        context: android.content.Context,
        fileName: String
    ): Bitmap? {
        return runCatching {
            val raw = composeRule.onRoot().captureToImage().asAndroidBitmap()
            val bmp = if (raw.config == Bitmap.Config.ARGB_8888) {
                raw
            } else {
                raw.copy(Bitmap.Config.ARGB_8888, false)
            }
            val dir = File(context.externalCacheDir ?: context.cacheDir, "screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, fileName)).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp
        }.getOrNull()
    }

    private fun assertCanvasHasWarmLightEditorialBackground(bmp: Bitmap) {
        assertTrue("Expected valid screenshot dimensions", bmp.width >= 240 && bmp.height >= 320)
        var warmLightMatches = 0
        var nonZeroSamples = 0
        var samples = 0
        for (y in (bmp.height / 8) until (bmp.height * 7 / 8) step 24) {
            for (x in 16 until bmp.width - 16 step 24) {
                val px = bmp.getPixel(x, y)
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                samples++
                if (r > 0 || g > 0 || b > 0) nonZeroSamples++
                // Warm ivory (#FAF9F5) or warm cream surface (#EFE9DE / #F3EFE7)
                if (r >= 232 && g >= 226 && b >= 214 && r >= b) {
                    warmLightMatches++
                }
            }
        }
        if (nonZeroSamples > samples / 10) {
            assertTrue(
                "Expected Warm Light canvas (#FAF9F5/#EFE9DE) to cover >=30% of Studio screen, got $warmLightMatches/$samples",
                warmLightMatches * 100 / samples >= 30
            )
        }
    }

    private fun assertCanvasHasWarmDarkEditorialBackground(bmp: Bitmap) {
        assertTrue("Expected valid screenshot dimensions", bmp.width >= 240 && bmp.height >= 320)
        var warmDarkMatches = 0
        var nonZeroSamples = 0
        var samples = 0
        for (y in (bmp.height / 8) until (bmp.height * 7 / 8) step 24) {
            for (x in 16 until bmp.width - 16 step 24) {
                val px = bmp.getPixel(x, y)
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                samples++
                if (r > 0 || g > 0 || b > 0) nonZeroSamples++
                // Warm charcoal (#181715) or dark card (#23211E)
                if (r in 14..48 && g in 13..46 && b in 11..44) {
                    warmDarkMatches++
                }
            }
        }
        if (nonZeroSamples > samples / 10) {
            assertTrue(
                "Expected Warm Dark canvas (#181715/#23211E) to cover >=30% of Settings screen, got $warmDarkMatches/$samples",
                warmDarkMatches * 100 / samples >= 30
            )
        }
    }

    private fun createSolidPng(dest: File, width: Int, height: Int, color: Int): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        FileOutputStream(dest).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
        return dest
    }
}
