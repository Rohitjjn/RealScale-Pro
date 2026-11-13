package com.tumuyan.ncnn.realsr.ui.screens

import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.model.HubFilterChip
import com.tumuyan.ncnn.realsr.core.model.HubSortOption
import com.tumuyan.ncnn.realsr.core.model.ModelHubQueryEngine
import com.tumuyan.ncnn.realsr.ui.components.AccessibleIconButton
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.EditorialOverline
import com.tumuyan.ncnn.realsr.ui.components.EditorialTopBar
import com.tumuyan.ncnn.realsr.ui.components.HairlineProgressBar
import com.tumuyan.ncnn.realsr.ui.components.IndeterminateHairlineBar
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.formatBytesHuman
import com.tumuyan.ncnn.realsr.ui.components.formatDurationHuman
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.util.Locale

@Composable
fun ModelHubScreen(
    models: List<CatalogModelState>,
    totalInstalledBytes: Long,
    wifiOnlyDownloads: Boolean = false,
    onWifiOnlyChanged: (Boolean) -> Unit = {},
    onDownloadOrResume: (String) -> Unit,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    showTopBackBar: Boolean = true
) {
    val colors = RealScaleThemeTokens.colors
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(HubFilterChip.ALL) }
    var selectedSort by rememberSaveable { mutableStateOf(HubSortOption.RECOMMENDED) }
    var expandedIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    val freeDeviceBytes = remember(totalInstalledBytes) {
        runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrDefault(0L)
    }

    val sections = remember(models, searchQuery, selectedFilter, selectedSort) {
        ModelHubQueryEngine.buildSections(
            allModels = models,
            searchQuery = searchQuery,
            selectedFilter = selectedFilter,
            sortOption = selectedSort
        )
    }

    val activeDownloads = remember(models) {
        models.filter {
            it.status == ModelInstallStatus.DOWNLOADING ||
                it.status == ModelInstallStatus.QUEUED ||
                it.status == ModelInstallStatus.VERIFYING
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        if (showTopBackBar) {
            EditorialTopBar(
                title = "Model Hub",
                onBack = onBack
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Active Downloads Summary Banner inside Hub when downloads are in flight
            if (activeDownloads.isNotEmpty()) {
                item(key = "active_downloads_banner") {
                    ActiveModelDownloadsCard(
                        activeModels = activeDownloads,
                        wifiOnly = wifiOnlyDownloads,
                        onOpenHub = null
                    )
                }
            }

            // Storage Summary + Wi-Fi only toggle
            item(key = "storage_summary") {
                EditorialCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = colors.surfaceSoft,
                    contentPadding = PaddingValues(Spacing.md)
                ) {
                    Text(
                        text = "Download once, enhance offline forever.",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val freePart = if (freeDeviceBytes > 0L) {
                        " · ${formatFreeStorageLabel(freeDeviceBytes)} free on device"
                    } else {
                        ""
                    }
                    Text(
                        text = "Models installed: ${formatBytesHuman(totalInstalledBytes)}$freePart",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    HorizontalDivider(thickness = 1.dp, color = colors.hairlineSoft)
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wi-Fi only downloads",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.ink
                            )
                            Text(
                                text = "Pause model downloads automatically on mobile data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                        Switch(
                            checked = wifiOnlyDownloads,
                            onCheckedChange = onWifiOnlyChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onTerracotta,
                                checkedTrackColor = colors.terracotta,
                                uncheckedThumbColor = colors.muted,
                                uncheckedTrackColor = colors.surfaceCard,
                                uncheckedBorderColor = colors.hairline
                            )
                        )
                    }
                }
            }

            // Search Input + Sort Selector
            item(key = "search_and_sort") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Search models by name, style, or engine…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = RealScaleIcons.Search,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                AccessibleIconButton(
                                    icon = RealScaleIcons.Close,
                                    contentDescription = "Clear search",
                                    onClick = { searchQuery = "" }
                                )
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.terracotta,
                            unfocusedBorderColor = colors.hairline,
                            focusedContainerColor = colors.surfaceCard,
                            unfocusedContainerColor = colors.surfaceCard,
                            focusedTextColor = colors.ink,
                            unfocusedTextColor = colors.ink
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sort",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.muted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            HubSortOption.entries.forEach { option ->
                                val selected = option == selectedSort
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) colors.surfaceStrong else colors.canvas,
                                    border = BorderStroke(1.dp, if (selected) colors.ink else colors.hairline),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 34.dp)
                                        .clip(CircleShape)
                                        .clickable(role = Role.RadioButton) { selectedSort = option }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.ink
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Filter Chips with Tabular Counts
            item(key = "filter_chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(HubFilterChip.entries, key = { it.name }) { filter ->
                        val active = filter == selectedFilter
                        val count = sections.filterCounts[filter] ?: 0
                        Surface(
                            shape = CircleShape,
                            color = if (active) colors.terracotta else colors.surfaceSoft,
                            border = BorderStroke(1.dp, if (active) colors.terracotta else colors.hairline),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Tab) { selectedFilter = filter }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 9.dp)
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) colors.onTerracotta else colors.ink
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) colors.onTerracotta.copy(alpha = 0.85f) else colors.muted
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Recommended for you (max 2 starter models)
            if (sections.recommendedStarter.isNotEmpty()) {
                item(key = "header_recommended") {
                    EditorialOverline(text = "Recommended for you")
                }
                items(sections.recommendedStarter, key = { "rec_${it.catalog.id}" }) { modelState ->
                    val id = modelState.catalog.id
                    ExpandableModelCatalogCard(
                        modelState = modelState,
                        expanded = id in expandedIds,
                        wifiOnly = wifiOnlyDownloads,
                        onToggleExpand = {
                            expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                        },
                        onDownloadOrResume = { onDownloadOrResume(id) },
                        onPause = { onPause(id) },
                        onCancel = { onCancel(id) },
                        onDelete = { onDelete(id) }
                    )
                }
            }

            // Section 2: Installed
            if (sections.installed.isNotEmpty()) {
                item(key = "header_installed") {
                    EditorialOverline(text = "Installed · ${sections.installed.size}")
                }
                items(sections.installed, key = { "inst_${it.catalog.id}" }) { modelState ->
                    val id = modelState.catalog.id
                    ExpandableModelCatalogCard(
                        modelState = modelState,
                        expanded = id in expandedIds,
                        wifiOnly = wifiOnlyDownloads,
                        onToggleExpand = {
                            expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                        },
                        onDownloadOrResume = { onDownloadOrResume(id) },
                        onPause = { onPause(id) },
                        onCancel = { onCancel(id) },
                        onDelete = { onDelete(id) }
                    )
                }
            }

            // Section 3: Available / Filtered results
            if (sections.available.isNotEmpty()) {
                val sectionTitle = if (searchQuery.isBlank() && selectedFilter == HubFilterChip.ALL) {
                    "Available · ${sections.available.size}"
                } else {
                    "Models · ${sections.available.size}"
                }
                item(key = "header_available") {
                    EditorialOverline(text = sectionTitle)
                }
                items(sections.available, key = { "avail_${it.catalog.id}" }) { modelState ->
                    val id = modelState.catalog.id
                    ExpandableModelCatalogCard(
                        modelState = modelState,
                        expanded = id in expandedIds,
                        wifiOnly = wifiOnlyDownloads,
                        onToggleExpand = {
                            expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                        },
                        onDownloadOrResume = { onDownloadOrResume(id) },
                        onPause = { onPause(id) },
                        onCancel = { onCancel(id) },
                        onDelete = { onDelete(id) }
                    )
                }
            }

            // Empty search / filter state
            if (sections.flatFiltered.isEmpty()) {
                item(key = "empty_filter") {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceSoft,
                        contentPadding = PaddingValues(Spacing.lg)
                    ) {
                        Text(
                            text = "No matching models",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedFilter == HubFilterChip.INSTALLED) {
                                "You haven't downloaded any extra AI models yet. Starter models take just a few megabytes."
                            } else {
                                "Try clearing your search or choosing another filter."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        QuietOutlineButton(
                            text = "Show all models",
                            onClick = {
                                searchQuery = ""
                                selectedFilter = HubFilterChip.ALL
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveModelDownloadsCard(
    activeModels: List<CatalogModelState>,
    wifiOnly: Boolean,
    onOpenHub: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (activeModels.isEmpty()) return
    val colors = RealScaleThemeTokens.colors
    val lead = activeModels.firstOrNull { it.status == ModelInstallStatus.DOWNLOADING } ?: activeModels.first()
    val etaMs = ModelHubQueryEngine.estimateDownloadEtaMillis(
        downloadedBytes = lead.downloadedBytes,
        totalBytes = lead.totalBytes,
        speedBytesPerSec = lead.speedBytesPerSec
    )

    EditorialCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.terracottaTint,
        borderColor = colors.terracotta.copy(alpha = 0.55f),
        onClick = onOpenHub,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = if (activeModels.size == 1) {
                "Downloading ${lead.catalog.displayName}"
            } else {
                "Downloading ${activeModels.size} models · ${lead.catalog.displayName}"
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val detail = when (lead.status) {
                ModelInstallStatus.VERIFYING -> "Verifying…"
                ModelInstallStatus.QUEUED -> if (wifiOnly) "Waiting for Wi-Fi" else "Queued"
                else -> {
                    val etaPart = etaMs?.let { " · ~${formatDurationHuman(it)}" }.orEmpty()
                    "${(lead.progressFraction * 100f).toInt()}%$etaPart"
                }
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.muted
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (lead.status == ModelInstallStatus.DOWNLOADING) {
            HairlineProgressBar(fraction = lead.progressFraction.coerceAtLeast(0.03f))
        } else {
            IndeterminateHairlineBar()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandableModelCatalogCard(
    modelState: CatalogModelState,
    expanded: Boolean,
    wifiOnly: Boolean,
    onToggleExpand: () -> Unit,
    onDownloadOrResume: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val item = modelState.catalog
    val speedTier = ModelHubQueryEngine.speedTierFor(item)
    val bestFor = ModelHubQueryEngine.bestForBadge(item)
    val scalesText = item.supportedScales.joinToString(" / ") { "${it}×" }

    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (modelState.isInstalled) colors.surfaceSoft else colors.surfaceCard,
        contentPadding = PaddingValues(Spacing.md)
    ) {
        // Header row (clickable to expand/collapse details)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onToggleExpand),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (modelState.isInstalled) {
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Surface(
                            shape = CircleShape,
                            color = colors.canvas,
                            border = BorderStroke(1.dp, colors.hairline)
                        ) {
                            Text(
                                text = "Installed",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.success,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Icon(
                imageVector = if (expanded) RealScaleIcons.ChevronUp else RealScaleIcons.ChevronDown,
                contentDescription = if (expanded) "Collapse details" else "Expand details",
                tint = colors.muted,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Compact metadata badges: Size · Speed · Best for
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactMetadataBadge(text = formatBytesHuman(item.totalBytes))
            CompactMetadataBadge(text = speedTier.badgeLabel)
            CompactMetadataBadge(text = bestFor)
            CompactMetadataBadge(text = scalesText)
        }

        // Expanded technical & delete section
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                HorizontalDivider(thickness = 1.dp, color = colors.hairlineSoft)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Engine: ${item.engineTool} · Scales: $scalesText · ${item.files.size} SHA-256 verified file${if (item.files.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
                if (modelState.isInstalled) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    QuietOutlineButton(
                        text = "Delete model",
                        leadingIcon = RealScaleIcons.Trash,
                        tint = colors.error,
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // State machine actions (collapsed or expanded)
        when (modelState.status) {
            ModelInstallStatus.NOT_INSTALLED -> {
                Spacer(modifier = Modifier.height(Spacing.sm))
                QuietOutlineButton(
                    text = "Download · ${formatBytesHuman(item.totalBytes)}",
                    leadingIcon = RealScaleIcons.Download,
                    onClick = onDownloadOrResume,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ModelInstallStatus.QUEUED -> {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = if (wifiOnly) "Queued · Waiting for Wi-Fi…" else "Queued…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                IndeterminateHairlineBar()
                Spacer(modifier = Modifier.height(Spacing.xs))
                QuietOutlineButton(
                    text = "Cancel",
                    leadingIcon = RealScaleIcons.Close,
                    onClick = onCancel,
                    tint = colors.muted,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ModelInstallStatus.DOWNLOADING -> {
                Spacer(modifier = Modifier.height(Spacing.sm))
                val etaMs = ModelHubQueryEngine.estimateDownloadEtaMillis(
                    downloadedBytes = modelState.downloadedBytes,
                    totalBytes = modelState.totalBytes,
                    speedBytesPerSec = modelState.speedBytesPerSec
                )
                val statusLabel = buildString {
                    append("${formatBytesHuman(modelState.downloadedBytes)} / ${formatBytesHuman(modelState.totalBytes)}")
                    if (modelState.speedBytesPerSec > 0L) {
                        append(" · ${formatBytesHuman(modelState.speedBytesPerSec)}/s")
                    }
                    if (etaMs != null) {
                        append(" · ~${formatDurationHuman(etaMs)} left")
                    }
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                HairlineProgressBar(fraction = modelState.progressFraction.coerceAtLeast(0.02f))
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    QuietOutlineButton(
                        text = "Pause",
                        leadingIcon = RealScaleIcons.Pause,
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    )
                    QuietOutlineButton(
                        text = "Cancel",
                        leadingIcon = RealScaleIcons.Close,
                        onClick = onCancel,
                        tint = colors.muted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ModelInstallStatus.VERIFYING -> {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Verifying SHA-256 checksum…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                IndeterminateHairlineBar()
            }

            ModelInstallStatus.PAUSED -> {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Paused at ${formatBytesHuman(modelState.downloadedBytes)} of ${formatBytesHuman(modelState.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                HairlineProgressBar(fraction = modelState.progressFraction)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TerracottaPrimaryButton(
                        text = "Resume",
                        leadingIcon = RealScaleIcons.Play,
                        onClick = onDownloadOrResume,
                        modifier = Modifier.weight(1f)
                    )
                    QuietOutlineButton(
                        text = "Cancel",
                        leadingIcon = RealScaleIcons.Close,
                        onClick = onCancel,
                        tint = colors.muted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ModelInstallStatus.INSTALLED -> Unit

            ModelInstallStatus.FAILED -> {
                Spacer(modifier = Modifier.height(Spacing.sm))
                if (!modelState.errorMessage.isNullOrBlank()) {
                    Text(
                        text = modelState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TerracottaPrimaryButton(
                        text = "Retry",
                        leadingIcon = RealScaleIcons.Refresh,
                        onClick = onDownloadOrResume,
                        modifier = Modifier.weight(1f)
                    )
                    QuietOutlineButton(
                        text = "Cancel",
                        leadingIcon = RealScaleIcons.Close,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactMetadataBadge(text: String) {
    val colors = RealScaleThemeTokens.colors
    Surface(
        shape = CircleShape,
        color = colors.surfaceSoft,
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatFreeStorageLabel(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        formatBytesHuman(bytes)
    }
}
