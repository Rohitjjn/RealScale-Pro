package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.EditorialTopBar
import com.tumuyan.ncnn.realsr.ui.components.HairlineProgressBar
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.formatBytesHuman
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing

private enum class HubFilter(val label: String) {
    ALL("All"),
    INSTALLED("Installed"),
    PHOTO("Photo"),
    ANIME("Anime & Art"),
    MAXIMUM("Maximum")
}

@Composable
fun ModelHubScreen(
    models: List<CatalogModelState>,
    totalInstalledBytes: Long,
    onDownloadOrResume: (String) -> Unit,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    var selectedFilter by rememberSaveable { mutableStateOf(HubFilter.ALL) }

    val filteredModels = models.filter { state ->
        when (selectedFilter) {
            HubFilter.ALL -> true
            HubFilter.INSTALLED -> state.isInstalled
            HubFilter.PHOTO -> state.catalog.category == "PHOTO"
            HubFilter.ANIME -> state.catalog.category == "ANIME"
            HubFilter.MAXIMUM -> state.catalog.category == "MAXIMUM"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        EditorialTopBar(
            title = "Model Hub",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
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
                    Text(
                        text = "Storage used by installed models: ${formatBytesHuman(totalInstalledBytes)} · Instant mode is always built-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items(HubFilter.entries) { filter ->
                        val active = filter == selectedFilter
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
                            }
                        }
                    }
                }
            }

            items(filteredModels, key = { it.catalog.id }) { modelState ->
                ModelCatalogCard(
                    modelState = modelState,
                    onDownloadOrResume = { onDownloadOrResume(modelState.catalog.id) },
                    onPause = { onPause(modelState.catalog.id) },
                    onCancel = { onCancel(modelState.catalog.id) },
                    onDelete = { onDelete(modelState.catalog.id) }
                )
            }
        }
    }
}

@Composable
private fun ModelCatalogCard(
    modelState: CatalogModelState,
    onDownloadOrResume: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val item = modelState.catalog
    val scalesText = item.supportedScales.joinToString(" / ") { "${it}×" }

    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (modelState.isInstalled) colors.surfaceSoft else colors.surfaceCard,
        contentPadding = PaddingValues(Spacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$scalesText · ${formatBytesHuman(item.totalBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted
                )
            }
            if (modelState.isInstalled) {
                Surface(
                    shape = CircleShape,
                    color = colors.canvas,
                    border = BorderStroke(1.dp, colors.hairline)
                ) {
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.success,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.body
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        when (modelState.status) {
            ModelInstallStatus.NOT_INSTALLED -> {
                QuietOutlineButton(
                    text = "Download · ${formatBytesHuman(item.totalBytes)}",
                    leadingIcon = RealScaleIcons.Download,
                    onClick = onDownloadOrResume,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ModelInstallStatus.QUEUED,
            ModelInstallStatus.DOWNLOADING,
            ModelInstallStatus.VERIFYING -> {
                val statusLabel = when {
                    modelState.status == ModelInstallStatus.VERIFYING -> "Verifying SHA-256 checksum…"
                    modelState.speedBytesPerSec > 0L ->
                        "${formatBytesHuman(modelState.downloadedBytes)} / ${formatBytesHuman(modelState.totalBytes)} · ${formatBytesHuman(modelState.speedBytesPerSec)}/s"
                    else ->
                        "${formatBytesHuman(modelState.downloadedBytes)} / ${formatBytesHuman(modelState.totalBytes)}"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
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

            ModelInstallStatus.PAUSED -> {
                Text(
                    text = "Paused at ${formatBytesHuman(modelState.downloadedBytes)} of ${formatBytesHuman(modelState.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
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

            ModelInstallStatus.INSTALLED -> {
                QuietOutlineButton(
                    text = "Delete model",
                    leadingIcon = RealScaleIcons.Trash,
                    tint = colors.error,
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ModelInstallStatus.FAILED -> {
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
