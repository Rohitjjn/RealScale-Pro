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
import com.tumuyan.ncnn.realsr.core.engine.EnginePreferences
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.ExecutionBackend
import com.tumuyan.ncnn.realsr.core.engine.ModelExecutionSpec
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun missingModelPresetRequiresDownloadWhileInstantPresetStartsEngineImmediately() = runBlocking {
        val sampleFile = createSamplePng("sample_input.png")
        val viewModel = RealScaleViewModel(app, container)

        val initial = withTimeout(5_000) {
            viewModel.uiState.first { it.catalogModels.size == 14 }
        }
        assertEquals(UpscalePreset.INSTANT, initial.selection.selectedPreset)
        assertTrue(initial.isPresetReadyOffline(UpscalePreset.INSTANT))
        assertFalse(initial.isPresetReadyOffline(UpscalePreset.PHOTO))
        assertEquals(
            "models-RealeSR-general-v3",
            initial.defaultCatalogStateFor(UpscalePreset.PHOTO)?.catalog?.id
        )

        viewModel.onPhotosSelected(listOf(Uri.fromFile(sampleFile)))
        withTimeout(5_000) {
            viewModel.uiState.first { it.selection.selectedUris.size == 1 }
        }

        // Photo preset requires models-RealeSR-general-v3 which is not yet installed -> returns false
        viewModel.selectPreset(UpscalePreset.PHOTO)
        withTimeout(5_000) {
            viewModel.uiState.first { it.selection.selectedPreset == UpscalePreset.PHOTO }
        }
        assertFalse(viewModel.startPrimaryUpscale())
        assertNull(fakeEngine.lastRequest)

        // Switching to Instant preset (zero AI model required) starts the engine immediately
        viewModel.selectPreset(UpscalePreset.INSTANT)
        viewModel.selectScale(4)
        withTimeout(5_000) {
            viewModel.uiState.first {
                it.selection.selectedPreset == UpscalePreset.INSTANT && it.selection.selectedScale == 4
            }
        }
        assertTrue(viewModel.startPrimaryUpscale())

        val req = fakeEngine.lastRequest
        assertNotNull(req)
        assertEquals(UpscalePreset.INSTANT, req!!.preset)
        assertEquals(4, req.scale)
        assertNull(req.modelSpec)
    }

    @Test
    fun settingsChangesUpdateUiStateAndPersistTheme() = runBlocking {
        val viewModel = RealScaleViewModel(app, container)
        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setExecutionBackend(ExecutionBackend.CPU)
        viewModel.setTileSize(128)
        viewModel.setOutputFormat(OutputFormat.WEBP)

        val updated = withTimeout(5_000) {
            viewModel.uiState.first {
                it.selection.themeMode == ThemeMode.DARK &&
                    it.enginePreferences.backend == ExecutionBackend.CPU &&
                    it.enginePreferences.tileSize == 128 &&
                    it.enginePreferences.outputFormat == OutputFormat.WEBP
            }
        }
        assertEquals(ThemeMode.DARK, updated.selection.themeMode)
        assertEquals(128, updated.enginePreferences.tileSize)

        // Recreated ViewModel restores persisted theme mode
        val recreated = RealScaleViewModel(app, container)
        val restored = withTimeout(5_000) {
            recreated.uiState.first { it.selection.themeMode == ThemeMode.DARK }
        }
        assertEquals(ThemeMode.DARK, restored.selection.themeMode)
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

        private val _consoleLog = MutableStateFlow("")
        override val consoleLog: StateFlow<String> = _consoleLog

        private val _preferences = MutableStateFlow(EnginePreferences())
        override val preferences: StateFlow<EnginePreferences> = _preferences

        var lastRequest: UpscaleRequest? = null

        override suspend fun ensureInitialized(forceReset: Boolean): Result<File> =
            Result.success(File("/tmp/realsr"))

        override fun updatePreferences(transform: (EnginePreferences) -> EnginePreferences) {
            _preferences.update(transform)
        }

        override fun startUpscale(request: UpscaleRequest) {
            lastRequest = request
            _state.value = EngineState.Running(
                jobId = "job-1",
                presetTitle = request.preset.title,
                progressFraction = 0.42f,
                currentFileIndex = 1,
                totalFiles = request.inputUris.size,
                currentFileName = "sample_input.png",
                etaMillis = 2_000L,
                previewUri = request.inputUris.firstOrNull()?.toString(),
                latestStatusLine = "42.00%"
            )
        }

        override fun startDirectoryUpscale(
            directoryUri: Uri,
            preset: UpscalePreset,
            scale: Int,
            modelSpec: ModelExecutionSpec?
        ) = Unit

        override fun runAdvancedCommand(rawCommand: String) = Unit

        override fun cancel() {
            _state.value = EngineState.Cancelled
        }

        override fun acknowledgeTerminalState() {
            _state.value = EngineState.Idle
        }
    }
}
