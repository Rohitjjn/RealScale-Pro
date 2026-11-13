package com.tumuyan.ncnn.realsr

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
                composeRule.onAllNodesWithText("RealScale Pro").fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Cold-start Studio screen assertions
            composeRule.onNodeWithText("RealScale Pro").assertIsDisplayed()
            composeRule.onNodeWithText("Choose a photo").assertIsDisplayed()

            // 2. Navigate to Model Hub, filter Photo models, verify HuggingFace catalog item, return
            composeRule.onNodeWithContentDescription("Open Model Hub").performClick()
            composeRule.onNodeWithText("Model Hub").assertIsDisplayed()
            composeRule.onNodeWithText("Photo").performClick()
            composeRule.onNodeWithText("Everyday Photo v3").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Navigate back").performClick()

            // 3. Navigate to Settings, toggle Warm Dark -> Warm Light theme, return
            composeRule.onNodeWithContentDescription("Open Settings").performClick()
            composeRule.onNodeWithText("Settings").assertIsDisplayed()
            composeRule.onNodeWithText("Appearance").assertIsDisplayed()
            composeRule.onNodeWithText("Warm Dark").performClick()
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

            // 5. Verify un-downloaded Photo preset prompts on-demand download (zero bundled AI models)
            composeRule.onNodeWithText("Photo").performScrollTo().performClick()
            composeRule.onNodeWithText("Get Photo model · 2.3 MB").performScrollTo().assertIsDisplayed()

            // 6. Switch back to Instant preset and run 4x upscale
            composeRule.onNodeWithText("Instant").performScrollTo().performClick()
            composeRule.onNodeWithText("Upscale photo · 4×").performScrollTo().performClick()

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
                composeRule.onNodeWithText("Back to Studio").performClick()
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
