package com.tumuyan.ncnn.realsr.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.tumuyan.ncnn.realsr.core.database.RealScaleDatabase
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.UpscaleEngine
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.engine.UpscaleRequest
import com.tumuyan.ncnn.realsr.core.model.ModelRepository
import com.tumuyan.ncnn.realsr.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RealScaleViewModelTest {

    private lateinit var app: Application
    private lateinit var db: RealScaleDatabase
    private lateinit var fakeEngine: FakeUpscaleEngine
    private lateinit var container: RealScaleAppContainer
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        val wmConfig = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, wmConfig)

        db = Room.inMemoryDatabaseBuilder(app, RealScaleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeEngine = FakeUpscaleEngine()
        val modelRepo = ModelRepository(
            context = app,
            downloadedModelDao = db.downloadedModelDao(),
            ioDispatcher = testDispatcher
        )
        container = RealScaleAppContainer(
            context = app,
            database = db,
            engine = fakeEngine,
            modelRepository = modelRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun missingModelPresetPromptsDownloadWhileInstantPresetStartsEngineImmediately() = runBlocking {
        val sampleFile = createSamplePng("sample_input.png")
        val viewModel = RealScaleViewModel(app, container)

        val initial = withTimeout(5_000) {
            viewModel.uiState.first { it.models.size == 14 }
        }
        assertEquals(UpscalePreset.PHOTO, initial.selectedPreset)
        assertEquals(4, initial.selectedScale)

        viewModel.onPhotosSelected(listOf(Uri.fromFile(sampleFile)))
        withTimeout(5_000) {
            viewModel.uiState.first { it.selectedPhotos.size == 1 }
        }

        // Photo preset requires models-RealeSR-general-v3 which is not yet installed -> prompt sheet
        viewModel.onStartUpscaleClicked()
        val prompted = withTimeout(5_000) {
            viewModel.uiState.first { it.promptModelDownload != null }
        }
        assertEquals("models-RealeSR-general-v3", prompted.promptModelDownload?.id)
        assertNull(fakeEngine.lastRequest)

        viewModel.dismissModelPrompt()

        // Switching to Instant preset (zero AI model required) starts the engine immediately
        viewModel.onPresetSelected(UpscalePreset.INSTANT)
        withTimeout(5_000) {
            viewModel.uiState.first { it.selectedPreset == UpscalePreset.INSTANT }
        }
        viewModel.onStartUpscaleClicked()

        withTimeout(5_000) {
            while (fakeEngine.lastRequest == null) {
                kotlinx.coroutines.delay(20)
            }
        }
        val req = fakeEngine.lastRequest
        assertNotNull(req)
        assertEquals(UpscalePreset.INSTANT, req!!.preset)
        assertTrue(req.commandOverride?.startsWith("./resize-ncnn") == true)
    }

    @Test
    fun settingsChangesUpdateStateAndPersist() = runBlocking {
        val viewModel = RealScaleViewModel(app, container)
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setUseCpu(true)
        viewModel.setTileSize(128)
        viewModel.setOutputFormatIndex(1)

        val updated = withTimeout(5_000) {
            viewModel.uiState.first {
                it.themeMode == ThemeMode.DARK &&
                    it.settings.useCpu &&
                    it.settings.tileSize == 128 &&
                    it.settings.outputFormatIndex == 1
            }
        }
        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertEquals(128, updated.settings.tileSize)
    }

    private fun createSamplePng(name: String): File {
        val file = File(app.cacheDir, name)
        val bmp = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(204, 120, 92))
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private class FakeUpscaleEngine : UpscaleEngine {
        private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
        override val state: StateFlow<EngineState> = _state
        var lastRequest: UpscaleRequest? = null

        override suspend fun ensureEngineReady(): File = File("/tmp/realsr")

        override fun startUpscale(request: UpscaleRequest) {
            lastRequest = request
            _state.value = EngineState.Running(
                progressPercent = 42f,
                etaText = "00:02",
                currentFileIndex = 1,
                totalFiles = request.inputUris.size,
                presetLabel = request.presetLabel,
                logTail = "[fake] 42.00%"
            )
        }

        override fun cancel() {
            _state.value = EngineState.Cancelled("Cancelled")
        }

        override fun resetToIdle() {
            _state.value = EngineState.Idle
        }
    }
}
