package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.PreflightMitigation
import com.tumuyan.ncnn.realsr.core.engine.PreflightVerdict
import com.tumuyan.ncnn.realsr.core.engine.QueuedJobItem
import com.tumuyan.ncnn.realsr.core.engine.RecoveryAction
import com.tumuyan.ncnn.realsr.core.engine.StagedInputPhoto
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.ui.components.AccessibleIconButton
import com.tumuyan.ncnn.realsr.ui.components.BeforeAfterSplitView
import com.tumuyan.ncnn.realsr.ui.components.BrandLogoMedallion
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.EditorialOverline
import com.tumuyan.ncnn.realsr.ui.components.HairlineProgressBar
import com.tumuyan.ncnn.realsr.ui.components.IndeterminateHairlineBar
import com.tumuyan.ncnn.realsr.ui.components.PreflightAdvisoryDialog
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.formatDurationHuman
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun QuickUpscaleSheet(
    stagedPhoto: StagedInputPhoto?,
    intakeErrorMessage: String?,
    selectedPreset: UpscalePreset,
    selectedScale: Int,
    outputFormat: OutputFormat,
    catalogModels: List<CatalogModelState>,
    engineState: EngineState,
    queue: List<QueuedJobItem>,
    completedCreation: RecentCreationEntity?,
    savedConfirmationMessage: String?,
    showNotificationRationale: Boolean,
    preflightAdvisory: PreflightVerdict.Advisory? = null,
    onSelectPreset: (UpscalePreset) -> Unit,
    onSelectScale: (Int) -> Unit,
    onDownloadModel: (String) -> Unit,
    onEnhance: () -> Unit,
    onApplyPreflightMitigation: (PreflightMitigation) -> Unit = {},
    onConfirmPreflightProceedAnyway: () -> Unit = {},
    onDismissPreflightAdvisory: () -> Unit = {},
    onApplyRecovery: (RecoveryAction) -> Unit = {},
    onRunInBackground: () -> Unit,
    onConfirmNotificationPermissionAndBackground: () -> Unit,
    onSkipNotificationPermissionAndBackground: () -> Unit,
    onCancelJob: () -> Unit,
    onCancelQueuedItem: (String) -> Unit,
    onSaveCompleted: (RecentCreationEntity) -> Unit,
    onShareCompleted: (android.content.Context, RecentCreationEntity) -> Unit,
    onOpenInFullApp: (RecentCreationEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val context = LocalContext.current
    val isRunning = engineState is EngineState.Preparing || engineState is EngineState.Running

    preflightAdvisory?.let { advisory ->
        PreflightAdvisoryDialog(
            advisory = advisory,
            onApplyMitigation = onApplyPreflightMitigation,
            onProceedAnyway = onConfirmPreflightProceedAnyway,
            onDismiss = onDismissPreflightAdvisory
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.44f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!isRunning) onDismiss()
                }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume taps inside sheet */ }
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = colors.canvas,
            border = BorderStroke(1.dp, colors.hairline),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            ) {
                // Subtle sheet handle pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.hairline)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogoMedallion(size = 26.dp)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Quick Upscale",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.ink
                        )
                    }
                    AccessibleIconButton(
                        icon = RealScaleIcons.Close,
                        contentDescription = "Close Quick Upscale",
                        onClick = {
                            if (isRunning) onRunInBackground() else onDismiss()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                if (intakeErrorMessage != null) {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceSoft
                    ) {
                        Text(
                            text = "Could not open image",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = intakeErrorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    TerracottaPrimaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                    return@Column
                }

                // Completed state: inline Before/After preview + Save / Share / Open
                if (completedCreation != null) {
                    BeforeAfterSplitView(
                        beforeFile = File(completedCreation.beforeImagePath),
                        afterFile = File(completedCreation.outputPath),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    val resolutionSummary = if (completedCreation.outputWidth > 0 && completedCreation.outputHeight > 0) {
                        "${completedCreation.inputWidth} × ${completedCreation.inputHeight} → ${completedCreation.outputWidth} × ${completedCreation.outputHeight}"
                    } else {
                        completedCreation.presetLabel
                    }
                    Text(
                        text = savedConfirmationMessage ?: "$resolutionSummary · Saved to Pictures/RealScale-Pro",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        TerracottaPrimaryButton(
                            text = "Save",
                            onClick = { onSaveCompleted(completedCreation) },
                            modifier = Modifier.weight(1f)
                        )
                        QuietOutlineButton(
                            text = "Share",
                            onClick = { onShareCompleted(context, completedCreation) },
                            modifier = Modifier.weight(1f)
                        )
                        QuietOutlineButton(
                            text = "Open",
                            onClick = { onOpenInFullApp(completedCreation) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    return@Column
                }

                // Source thumbnail + resolution card
                if (stagedPhoto != null) {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceSoft,
                        contentPadding = PaddingValues(Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = stagedPhoto.stagedFile,
                                contentDescription = "Source photo thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(colors.surfaceCard)
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stagedPhoto.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val dimLabel = if (stagedPhoto.width > 0 && stagedPhoto.height > 0) {
                                    "${stagedPhoto.width} × ${stagedPhoto.height} · ${stagedPhoto.formatLabel}"
                                } else {
                                    stagedPhoto.formatLabel
                                }
                                Text(
                                    text = dimLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                // Active Processing or Queue state
                if (isRunning) {
                    val running = engineState as? EngineState.Running
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceCard,
                        contentPadding = PaddingValues(Spacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val stageLabel = when (engineState) {
                                is EngineState.Preparing -> "Preparing"
                                is EngineState.Running -> running?.stage?.humanLabel ?: "Enhancing"
                                else -> "Enhancing"
                            }
                            val batchBadge = if (running != null && running.totalFiles > 1) {
                                " · ${running.currentFileIndex} of ${running.totalFiles}"
                            } else {
                                ""
                            }
                            Text(
                                text = "$stageLabel$batchBadge",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink
                            )
                            val rightMetric = when {
                                running?.hasDeterminateProgress == true -> {
                                    val pct = (running.progressFraction * 100f).roundToInt().coerceIn(1, 99)
                                    val eta = running.etaMillis?.let { " · ~${formatDurationHuman(it)} left" }.orEmpty()
                                    "$pct%$eta"
                                }
                                running != null -> {
                                    val sec = (running.elapsedMillis / 1000L).coerceAtLeast(1L)
                                    "${sec}s elapsed"
                                }
                                else -> "Starting…"
                            }
                            Text(
                                text = rightMetric,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.muted
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        if (running?.hasDeterminateProgress == true) {
                            HairlineProgressBar(fraction = running.progressFraction.coerceAtLeast(0.03f))
                        } else {
                            IndeterminateHairlineBar()
                        }
                    }

                    if (queue.size > 1) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = colors.surfaceSoft,
                            contentPadding = PaddingValues(Spacing.sm)
                        ) {
                            EditorialOverline(text = "Queue · ${queue.size} items")
                            Spacer(modifier = Modifier.height(4.dp))
                            queue.take(4).forEach { qItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${qItem.positionInBatch} of ${qItem.totalInBatch} · ${qItem.fileName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (qItem.isRunning) colors.ink else colors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (qItem.isRunning) "Running" else "Cancel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (qItem.isRunning) colors.muted else colors.terracotta,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable(enabled = !qItem.isRunning) {
                                                onCancelQueuedItem(qItem.id)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showNotificationRationale) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = colors.terracottaTint,
                            borderColor = colors.terracotta,
                            contentPadding = PaddingValues(Spacing.md)
                        ) {
                            Text(
                                text = "Notify when finished?",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Allow notifications so RealScale-Pro can show a preview with Share & Open actions when background enhancement completes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.body
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                            ) {
                                TerracottaPrimaryButton(
                                    text = "Allow",
                                    onClick = onConfirmNotificationPermissionAndBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                QuietOutlineButton(
                                    text = "Continue quietly",
                                    onClick = onSkipNotificationPermissionAndBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        TerracottaPrimaryButton(
                            text = "Run in background",
                            onClick = onRunInBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        QuietOutlineButton(
                            text = "Cancel",
                            onClick = onCancelJob,
                            modifier = Modifier.weight(1f)
                        )
                        QuietOutlineButton(
                            text = "Open in RealScale-Pro",
                            onClick = { onOpenInFullApp(null) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    return@Column
                }

                // Error state inside sheet with one-tap recovery actions
                if (engineState is EngineState.Failed) {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceCard,
                        contentPadding = PaddingValues(Spacing.md)
                    ) {
                        Text(
                            text = "Could not complete upscale",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = engineState.userMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.body
                        )
                        if (engineState.recoveryActions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                engineState.recoveryActions.forEachIndexed { idx, action ->
                                    if (idx == 0) {
                                        TerracottaPrimaryButton(
                                            text = action.label,
                                            onClick = { onApplyRecovery(action) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        QuietOutlineButton(
                                            text = action.label,
                                            onClick = { onApplyRecovery(action) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                }

                // Compact Preset Picker (last-used preselected; uninstalled shows "Get model")
                EditorialOverline(text = "Preset")
                Spacer(modifier = Modifier.height(Spacing.xs))

                val isSelectedPresetReady = isPresetReady(selectedPreset, catalogModels)
                val selectedCatalogModel = catalogModels.firstOrNull { it.catalog.id == selectedPreset.defaultModelId }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    UpscalePreset.ALL.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            rowPresets.forEach { preset ->
                                val active = preset.id == selectedPreset.id
                                val ready = isPresetReady(preset, catalogModels)
                                val modelState = catalogModels.firstOrNull { it.catalog.id == preset.defaultModelId }
                                val downloading = modelState?.status == ModelInstallStatus.DOWNLOADING ||
                                    modelState?.status == ModelInstallStatus.QUEUED ||
                                    modelState?.status == ModelInstallStatus.VERIFYING

                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (active) colors.surfaceCard else colors.surfaceSoft,
                                    border = BorderStroke(
                                        width = if (active) 1.5.dp else 1.dp,
                                        color = if (active) colors.terracotta else colors.hairline
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 56.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable(role = Role.RadioButton) { onSelectPreset(preset) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.ink
                                        )
                                        val subLabel = when {
                                            !preset.requiresDownload -> "Ready"
                                            ready -> "Installed"
                                            downloading -> "Downloading…"
                                            else -> "Get model"
                                        }
                                        Text(
                                            text = subLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (ready) colors.muted else colors.terracotta
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // 2x / 4x Toggle + Estimated Output Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        listOf(2, 4).forEach { scaleOption ->
                            val supported = scaleOption in selectedPreset.supportedScales
                            val active = scaleOption == selectedScale
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    active -> colors.terracotta
                                    supported -> colors.surfaceSoft
                                    else -> colors.canvas
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (active) colors.terracotta else colors.hairline
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 52.dp, minHeight = 40.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = supported, role = Role.RadioButton) {
                                        onSelectScale(scaleOption)
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${scaleOption}×",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when {
                                            active -> colors.onTerracotta
                                            supported -> colors.ink
                                            else -> colors.subtle
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val estimateLabel = buildOutputEstimateLabel(
                        stagedPhoto = stagedPhoto,
                        scale = selectedScale,
                        format = outputFormat
                    )
                    Text(
                        text = estimateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(thickness = 1.dp, color = colors.hairlineSoft)
                Spacer(modifier = Modifier.height(Spacing.md))

                // Primary Enhance or Get Model CTA
                if (isSelectedPresetReady) {
                    TerracottaPrimaryButton(
                        text = "Enhance",
                        leadingIcon = RealScaleIcons.Sparkle,
                        enabled = stagedPhoto != null,
                        onClick = onEnhance,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val dlStatus = selectedCatalogModel?.status
                    val isBusy = dlStatus == ModelInstallStatus.DOWNLOADING ||
                        dlStatus == ModelInstallStatus.QUEUED ||
                        dlStatus == ModelInstallStatus.VERIFYING
                    TerracottaPrimaryButton(
                        text = if (isBusy) {
                            "Downloading ${selectedPreset.title} model…"
                        } else {
                            String.format(
                                Locale.US,
                                "Get model · %.1f MB",
                                selectedPreset.approxDownloadMb
                            )
                        },
                        leadingIcon = RealScaleIcons.Download,
                        enabled = !isBusy,
                        onClick = { onDownloadModel(selectedPreset.defaultModelId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                QuietOutlineButton(
                    text = "Open in RealScale-Pro",
                    onClick = { onOpenInFullApp(null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun isPresetReady(preset: UpscalePreset, catalogModels: List<CatalogModelState>): Boolean {
    if (!preset.requiresDownload) return true
    return catalogModels.any {
        (it.catalog.id == preset.defaultModelId || it.catalog.category == preset.id.name) && it.isInstalled
    }
}

internal fun buildOutputEstimateLabel(
    stagedPhoto: StagedInputPhoto?,
    scale: Int,
    format: OutputFormat
): String {
    if (stagedPhoto == null || stagedPhoto.width <= 0 || stagedPhoto.height <= 0) {
        return "Output · ${scale}× ${format.extension.uppercase(Locale.US)}"
    }
    val outW = stagedPhoto.width.toLong() * scale.toLong()
    val outH = stagedPhoto.height.toLong() * scale.toLong()
    val pixels = (outW * outH).coerceAtLeast(1L)
    val bytesPerPixel = when (format) {
        OutputFormat.PNG -> 1.65
        OutputFormat.WEBP -> 0.55
        OutputFormat.JPG -> 0.65
    }
    val estMb = (pixels * bytesPerPixel) / (1024.0 * 1024.0)
    val mbStr = if (estMb < 0.1) "<0.1 MB" else String.format(Locale.US, "~%.1f MB", estMb)
    return "$outW × $outH · $mbStr"
}
