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
import com.tumuyan.ncnn.realsr.ui.state.ModelDownloadUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
    fun designOnboardingReachesSetupScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Start from a clean install state so the first-run guide is the entry point.
        context.getSharedPreferences("realscale_state", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("RealScale-Pro", 12_000L)
            waitForText("Skip", 12_000L)
            clickText("Skip")
            waitForText("See your recent photos", 8_000L)
            clickText("Not now")
            waitForText("Know when it's done", 8_000L)
            clickText("Not now")
            waitForText("Continue", 25_000L)
            clickText("Continue")

            // The Setup screen downloads the Starter Pack; on a CI emulator it stays in progress.
            waitForText("Setting up RealScale-Pro", 12_000L)
            composeRule.onNodeWithText("Setting up RealScale-Pro").assertIsDisplayed()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "07_setup.png")?.recycle()
        } finally {
            runCatching {
                scenario.onActivity { it.finish() }
                context.stopService(Intent(context, ProcessingService::class.java))
            }
        }
    }

    /**
     * The Starter Pack screen has to behave like a download queue, not a Pause button that starts
     * nothing: it begins by itself, moves one model at a time, and Pause / Resume toggle the queue
     * without raising a message.
     */
    @Test
    fun designSetupScreenDownloadsByItselfAndTogglesPauseSilently() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "huggingface.co is not reachable from this device/emulator",
            canReach("https://huggingface.co/"),
        )
        context.getSharedPreferences("realscale_state", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("RealScale-Pro", 12_000L)
            waitForText("Skip", 12_000L)
            clickText("Skip")
            waitForText("See your recent photos", 8_000L)
            clickText("Not now")
            waitForText("Know when it's done", 8_000L)
            clickText("Not now")
            waitForText("Continue", 25_000L)
            clickText("Continue")
            waitForText("Setting up RealScale-Pro", 12_000L)

            // 1. Pause flips the button and says nothing at all.
            assumeTrue(
                "Starter Pack finished before the pause could be tested",
                composeRule.onAllNodesWithText("Pause download").fetchSemanticsNodes().isNotEmpty(),
            )
            clickText("Pause download")
            waitForText("Resume download", 8_000L)
            composeRule.waitForIdle()
            assertEquals("Pause should not raise a message", null, toastOf(scenario))
            captureComposeRootBitmap(context, "07b_setup_paused.png")?.recycle()

            // 2. Resume flips it back, silently.
            clickText("Resume download")
            waitForText("Pause download", 8_000L)
            assertEquals("Resume should not raise a message", null, toastOf(scenario))

            // 3. The queue moves on its own, one model at a time, and the byte counter is real.
            val moved = waitUntil(180_000L) {
                val downloads = downloadsOf(scenario)
                downloads.values.any { it.progress > 0f } || installedCount(scenario) >= 2
            }
            assertTrue("Starter Pack never downloaded anything by itself", moved)
            val active = downloadsOf(scenario).values.count { it.state == "downloading" || it.state == "verifying" }
            assertTrue("Expected at most one active download, saw $active", active <= 1)
        } finally {
            runCatching {
                scenario.onActivity { it.finish() }
                context.stopService(Intent(context, ProcessingService::class.java))
            }
        }
    }

    private fun installedCount(scenario: ActivityScenario<MainActivity>): Int {
        var value = 0
        scenario.onActivity { value = it.storeForTest().state.value.installed.size }
        return value
    }

    private fun downloadsOf(scenario: ActivityScenario<MainActivity>): Map<String, ModelDownloadUi> {
        var value: Map<String, ModelDownloadUi> = emptyMap()
        scenario.onActivity { value = it.storeForTest().state.value.downloads }
        return value
    }

    private fun toastOf(scenario: ActivityScenario<MainActivity>): com.tumuyan.ncnn.realsr.ui.state.ToastMessage? {
        var value: com.tumuyan.ncnn.realsr.ui.state.ToastMessage? = null
        scenario.onActivity { value = it.storeForTest().state.value.toast }
        return value
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(500L)
        }
        return condition()
    }

    private fun canReach(url: String): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "HEAD"
            instanceFollowRedirects = true
        }
        try {
            conn.responseCode in 200..399
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    @Test
    fun designHomeSettingsAndModelManager() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("realscale_state", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitForText("RealScale-Pro", 12_000L)
            scenario.onActivity { activity -> activity.storeForTest().skipOnboardingForTest() }

            waitForText("Recent photos", 12_000L)
            composeRule.onNodeWithText("Sharper, bigger photos. Nothing leaves your device.")
                .assertIsDisplayed()
            composeRule.onAllNodesWithText("Allow photo access")[0].assertIsDisplayed()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "08_home.png")?.recycle()

            // Settings: theme switch, planner rows, back to Home.
            composeRule.onNodeWithContentDescription("Settings").performClick()
            waitForText("Theme", 8_000L)
            composeRule.onNodeWithText("Dynamic color").assertIsDisplayed()
            composeRule.onNodeWithText("Compute preference").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Dark").performClick()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "09_settings_dark.png")?.recycle()
            composeRule.onNodeWithText("System").performClick()
            composeRule.onNodeWithContentDescription("Back").performClick()
            waitForText("Recent photos", 8_000L)

            // Model Manager: storage card, packs and the update check.
            composeRule.onNodeWithContentDescription("Models").performClick()
            waitForText("Model Manager", 8_000L)
            // Eyebrow labels render in caps (EyebrowLabel uppercases its text).
            composeRule.onNodeWithText("STORAGE").assertIsDisplayed()
            composeRule.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
            composeRule.waitForIdle()
            captureComposeRootBitmap(context, "10_models.png")?.recycle()
            composeRule.onNodeWithContentDescription("Back").performClick()
            waitForText("Recent photos", 8_000L)
        } finally {
            runCatching {
                scenario.onActivity { it.finish() }
                context.stopService(Intent(context, ProcessingService::class.java))
            }
        }
    }

    private fun waitForText(text: String, timeoutMs: Long) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickText(text: String) {
        composeRule.onNodeWithText(text).performClick()
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
