package com.tumuyan.ncnn.realsr.core.model

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ModelHubQueryEngineTest {

    private fun loadBundledStates(installedIds: Set<String> = emptySet()): List<CatalogModelState> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val raw = app.assets.open("model_manifest.json").bufferedReader().use { it.readText() }
        val manifest = ModelManifestParser.parseAndValidate(raw)
        return manifest.models.map { item ->
            val inst = item.id in installedIds
            CatalogModelState(
                catalog = item,
                status = if (inst) ModelInstallStatus.INSTALLED else ModelInstallStatus.NOT_INSTALLED,
                downloadedBytes = if (inst) item.totalBytes else 0L,
                totalBytes = item.totalBytes,
                speedBytesPerSec = 0L,
                errorMessage = null
            )
        }
    }

    @Test
    fun defaultBrowseSeparatesTwoStarterRecommendationsInstalledAndAvailableWithCounts() {
        val states = loadBundledStates(installedIds = setOf("models-DF2K"))
        val sections = ModelHubQueryEngine.buildSections(
            allModels = states,
            searchQuery = "",
            selectedFilter = HubFilterChip.ALL,
            sortOption = HubSortOption.RECOMMENDED
        )

        assertEquals(14, sections.filterCounts[HubFilterChip.ALL])
        assertEquals(1, sections.filterCounts[HubFilterChip.INSTALLED])
        assertTrue((sections.filterCounts[HubFilterChip.PHOTO] ?: 0) >= 3)
        assertTrue((sections.filterCounts[HubFilterChip.ANIME] ?: 0) >= 5)
        assertTrue((sections.filterCounts[HubFilterChip.FAST] ?: 0) >= 3)

        // Curated max 2 starter models
        assertEquals(
            listOf("models-RealeSR-general-v3", "models-upconv_7_anime_style_art_rgb"),
            sections.recommendedStarter.map { it.catalog.id }
        )
        assertEquals(listOf("models-DF2K"), sections.installed.map { it.catalog.id })
        // Total across Recommended + Installed + Available equals full 14-model catalog without duplicates
        assertEquals(
            14,
            sections.recommendedStarter.size + sections.installed.size + sections.available.size
        )
    }

    @Test
    fun searchFilterSortAndDownloadEtaBehavePredictably() {
        val states = loadBundledStates()

        val sortedBySize = ModelHubQueryEngine.buildSections(
            allModels = states,
            searchQuery = "",
            selectedFilter = HubFilterChip.PHOTO,
            sortOption = HubSortOption.SIZE
        ).flatFiltered
        assertTrue(sortedBySize.zipWithNext().all { (a, b) -> a.catalog.totalBytes <= b.catalog.totalBytes })

        val sortedByName = ModelHubQueryEngine.buildSections(
            allModels = states,
            searchQuery = "",
            selectedFilter = HubFilterChip.ANIME,
            sortOption = HubSortOption.NAME
        ).flatFiltered
        assertTrue(
            sortedByName.zipWithNext().all { (a, b) ->
                a.catalog.displayName.lowercase() <= b.catalog.displayName.lowercase()
            }
        )

        val searched = ModelHubQueryEngine.buildSections(
            allModels = states,
            searchQuery = "mnnsr-ncnn",
            selectedFilter = HubFilterChip.ALL,
            sortOption = HubSortOption.RECOMMENDED
        ).flatFiltered
        assertEquals(1, searched.size)
        assertEquals("mnnsr-ncnn", searched.first().catalog.engineTool)

        // 5 MB remaining at 1 MB/s -> ~5000 ms ETA
        val eta = ModelHubQueryEngine.estimateDownloadEtaMillis(
            downloadedBytes = 3L * 1024L * 1024L,
            totalBytes = 8L * 1024L * 1024L,
            speedBytesPerSec = 1L * 1024L * 1024L
        )
        assertEquals(5_000L, eta)
        assertNull(ModelHubQueryEngine.estimateDownloadEtaMillis(0L, 8_000_000L, 0L))
    }
}
