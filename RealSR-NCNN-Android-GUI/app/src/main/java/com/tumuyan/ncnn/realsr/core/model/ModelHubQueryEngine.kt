package com.tumuyan.ncnn.realsr.core.model

import java.util.Locale
import kotlin.math.roundToLong

enum class HubFilterChip(val label: String) {
    ALL("All"),
    INSTALLED("Installed"),
    PHOTO("Photo"),
    ANIME("Anime & Art"),
    FAST("Fast"),
    MAXIMUM("Maximum")
}

enum class HubSortOption(val label: String) {
    RECOMMENDED("Recommended"),
    SIZE("Size"),
    NAME("Name")
}

enum class ModelSpeedTier(val badgeLabel: String) {
    QUICK("Quick"),
    BALANCED("Balanced"),
    INTENSIVE("Intensive")
}

data class ModelHubSections(
    val filterCounts: Map<HubFilterChip, Int>,
    val recommendedStarter: List<CatalogModelState>,
    val installed: List<CatalogModelState>,
    val available: List<CatalogModelState>,
    val flatFiltered: List<CatalogModelState>
)

/**
 * Pure filtering, counting, sorting, curating, and download-ETA helper for the Scalable Model Hub.
 */
object ModelHubQueryEngine {

    private val STARTER_RECOMMENDED_IDS = listOf(
        "models-RealeSR-general-v3",
        "models-upconv_7_anime_style_art_rgb"
    )

    private const val FAST_THRESHOLD_BYTES = 9L * 1024L * 1024L

    fun speedTierFor(item: ModelCatalogItem): ModelSpeedTier {
        return when {
            item.totalBytes <= FAST_THRESHOLD_BYTES || item.engineTool == "Anime4k" -> ModelSpeedTier.QUICK
            item.totalBytes <= 36L * 1024L * 1024L -> ModelSpeedTier.BALANCED
            else -> ModelSpeedTier.INTENSIVE
        }
    }

    fun bestForBadge(item: ModelCatalogItem): String {
        return when (item.category) {
            "PHOTO" -> "For photos"
            "ANIME" -> "For anime & art"
            "MAXIMUM" -> "For max detail"
            else -> "General purpose"
        }
    }

    fun matchesFilter(state: CatalogModelState, filter: HubFilterChip): Boolean {
        return when (filter) {
            HubFilterChip.ALL -> true
            HubFilterChip.INSTALLED -> state.isInstalled
            HubFilterChip.PHOTO -> state.catalog.category == "PHOTO"
            HubFilterChip.ANIME -> state.catalog.category == "ANIME"
            HubFilterChip.FAST -> speedTierFor(state.catalog) == ModelSpeedTier.QUICK
            HubFilterChip.MAXIMUM -> state.catalog.category == "MAXIMUM"
        }
    }

    fun matchesSearch(state: CatalogModelState, rawQuery: String): Boolean {
        val q = rawQuery.trim().lowercase(Locale.US)
        if (q.isEmpty()) return true
        val c = state.catalog
        return c.displayName.lowercase(Locale.US).contains(q) ||
            c.description.lowercase(Locale.US).contains(q) ||
            c.engineTool.lowercase(Locale.US).contains(q) ||
            c.category.lowercase(Locale.US).contains(q) ||
            bestForBadge(c).lowercase(Locale.US).contains(q)
    }

    fun sortModels(
        list: List<CatalogModelState>,
        sort: HubSortOption,
        manifestOrderIndex: Map<String, Int>
    ): List<CatalogModelState> {
        return when (sort) {
            HubSortOption.RECOMMENDED -> list.sortedWith(
                compareBy<CatalogModelState> {
                    val starterIdx = STARTER_RECOMMENDED_IDS.indexOf(it.catalog.id)
                    if (starterIdx >= 0) starterIdx else (10 + (manifestOrderIndex[it.catalog.id] ?: 999))
                }
            )
            HubSortOption.SIZE -> list.sortedWith(
                compareBy<CatalogModelState> { it.catalog.totalBytes }
                    .thenBy { it.catalog.displayName.lowercase(Locale.US) }
            )
            HubSortOption.NAME -> list.sortedBy { it.catalog.displayName.lowercase(Locale.US) }
        }
    }

    fun buildSections(
        allModels: List<CatalogModelState>,
        searchQuery: String,
        selectedFilter: HubFilterChip,
        sortOption: HubSortOption
    ): ModelHubSections {
        val searchMatched = allModels.filter { matchesSearch(it, searchQuery) }
        val counts = HubFilterChip.entries.associateWith { chip ->
            searchMatched.count { matchesFilter(it, chip) }
        }

        val manifestOrder = allModels.mapIndexed { idx, st -> st.catalog.id to idx }.toMap()
        val filtered = sortModels(
            list = searchMatched.filter { matchesFilter(it, selectedFilter) },
            sort = sortOption,
            manifestOrderIndex = manifestOrder
        )

        val isDefaultBrowse = searchQuery.isBlank() && selectedFilter == HubFilterChip.ALL
        val starter = if (isDefaultBrowse) {
            STARTER_RECOMMENDED_IDS.mapNotNull { id ->
                allModels.firstOrNull { it.catalog.id == id && !it.isInstalled }
            }.take(2)
        } else {
            emptyList()
        }

        val starterIds = starter.map { it.catalog.id }.toSet()
        val installed = if (isDefaultBrowse) {
            filtered.filter { it.isInstalled }
        } else {
            emptyList()
        }
        val available = if (isDefaultBrowse) {
            filtered.filter { !it.isInstalled && it.catalog.id !in starterIds }
        } else {
            filtered
        }

        return ModelHubSections(
            filterCounts = counts,
            recommendedStarter = starter,
            installed = installed,
            available = available,
            flatFiltered = filtered
        )
    }

    fun estimateDownloadEtaMillis(downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long): Long? {
        if (speedBytesPerSec <= 1024L || totalBytes <= downloadedBytes) return null
        val remaining = (totalBytes - downloadedBytes).coerceAtLeast(0L)
        val sec = remaining.toDouble() / speedBytesPerSec.toDouble()
        if (!sec.isFinite() || sec <= 0.2) return null
        return (sec * 1000.0).roundToLong().coerceAtLeast(1_000L)
    }
}
