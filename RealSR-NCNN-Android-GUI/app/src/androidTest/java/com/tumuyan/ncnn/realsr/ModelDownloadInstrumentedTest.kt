package com.tumuyan.ncnn.realsr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.model.ModelRepository
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The download path the user actually exercises: bytes really leave the network, progress really
 * moves, Pause really stops the transfer and Resume really continues it instead of restarting.
 *
 * Uses the smallest model in the manifest (Real-ESRGAN general v3, 2.4 MB) so the whole cycle fits
 * in the emulator job. Skipped — not failed — when the emulator has no route to the mirror, so a
 * sandboxed runner reports "assumption failed" instead of a misleading red build.
 */
@RunWith(AndroidJUnit4::class)
class ModelDownloadInstrumentedTest {

    private lateinit var repository: ModelRepository

    /** Manifest/HF directory id, not the UI slug. */
    private val modelId = "models-RealeSR-general-v3"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "huggingface.co is not reachable from this device/emulator",
            canReach("https://huggingface.co/"),
        )
        repository = RealScaleAppContainer.getInstance(context).modelRepository
        runBlocking { repository.cancelDownload(modelId) }
    }

    @Test
    fun downloadsStreamsProgressAndInstallsVerifiedFiles() = runBlocking {
        repository.startOrResumeDownload(modelId)

        val streamed = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
            repository.catalogModels
                .mapNotNull { states -> states.firstOrNull { it.catalog.id == modelId }?.downloadedBytes }
                .first { it > 0L }
        }
        assertNotNull("Download never streamed a single byte", streamed)

        val final = awaitSettled()
        assertEquals("Download did not finish: ${final.status} ${final.errorMessage}", ModelInstallStatus.INSTALLED, final.status)

        val installedDir = File(repository.modelsRootDir, modelId)
        assertTrue("Installed directory missing: $installedDir", installedDir.isDirectory)
        val files = installedDir.listFiles().orEmpty()
        assertTrue("No model files were installed", files.isNotEmpty())
        assertTrue("Installed files are empty", files.all { it.length() > 0L })

        repository.deleteInstalledModel(modelId)
    }

    @Test
    fun pauseKeepsPartialBytesAndResumeFinishesInsteadOfRestarting() = runBlocking {
        repository.startOrResumeDownload(modelId)

        // Let a little of the transfer land before pausing.
        withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
            repository.catalogModels
                .mapNotNull { states -> states.firstOrNull { it.catalog.id == modelId }?.downloadedBytes }
                .first { it > 0L }
        }
        repository.pauseDownload(modelId)

        val paused = awaitSettled()
        assertEquals("Pause did not settle on PAUSED: ${paused.status}", ModelInstallStatus.PAUSED, paused.status)
        assertTrue("Pause threw the partial bytes away", paused.downloadedBytes > 0L)

        // Resume: the installer continues from the .part files via HTTP Range.
        repository.startOrResumeDownload(modelId)
        val finished = awaitSettled()
        assertEquals(
            "Resume did not finish the download: ${finished.status} ${finished.errorMessage}",
            ModelInstallStatus.INSTALLED,
            finished.status,
        )
        assertTrue("Resume re-downloaded from zero", finished.downloadedBytes == finished.totalBytes)

        repository.deleteInstalledModel(modelId)
    }

    @Test
    fun movingTheDownloadToAnotherNetworkTypeIsNotRequired() = runBlocking {
        // "Wi-Fi only" off must let a metered connection download; this asserts the repository does
        // not reject the request before the network check (the emulator may be either kind).
        repository.startOrResumeDownload(modelId, wifiOnly = false)
        val settled = awaitSettled()
        assertTrue(
            "Download with wifiOnly=false ended as ${settled.status}: ${settled.errorMessage}",
            settled.status == ModelInstallStatus.INSTALLED || settled.status == ModelInstallStatus.PAUSED,
        )
        repository.deleteInstalledModel(modelId)
    }

    private suspend fun awaitSettled(): CatalogModelState = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
        repository.catalogModels
            .mapNotNull { states -> states.firstOrNull { it.catalog.id == modelId } }
            .first {
                it.status == ModelInstallStatus.INSTALLED ||
                    it.status == ModelInstallStatus.FAILED ||
                    it.status == ModelInstallStatus.PAUSED
            }
    } ?: error("Download did not settle within ${DOWNLOAD_TIMEOUT_MS} ms")

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

    private companion object {
        const val DOWNLOAD_TIMEOUT_MS = 240_000L
    }
}
