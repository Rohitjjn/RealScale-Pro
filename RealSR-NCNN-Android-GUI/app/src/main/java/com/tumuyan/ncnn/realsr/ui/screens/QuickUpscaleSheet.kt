package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.HairlineSoftDivider
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.Mark
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsChip
import com.tumuyan.ncnn.realsr.design.RsIconButton
import com.tumuyan.ncnn.realsr.design.RsIconButtonVariant
import com.tumuyan.ncnn.realsr.design.RsProgressBar
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The "Open with" sheet: a one-screen upscale for a single incoming image. Same contract and the
 * same visible strings as before the redesign, rebuilt from [com.tumuyan.ncnn.realsr.design].
 */
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
    val c = LocalRsColors.current
    val context = LocalContext.current
    val running = engineState as? EngineState.Running
    val isRunning = engineState is EngineState.Preparing || engineState is EngineState.Running

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!isRunning) onDismiss() },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = RsRadius.xxl.dp, topEnd = RsRadius.xxl.dp))
                .background(c.canvas)
                .border(
                    1.dp,
                    c.hairline,
                    RoundedCornerShape(topStart = RsRadius.xxl.dp, topEnd = RsRadius.xxl.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume taps inside the sheet */ },
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(c.hairline),
            )
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Mark(size = 26.dp)
                Text(
                    "Quick Upscale",
                    style = RsType.display(24f),
                    color = c.ink,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                RsIconButton(
                    icon = LucideIcons.X,
                    label = "Close Quick Upscale",
                    variant = RsIconButtonVariant.Plain,
                    onClick = { if (isRunning) onRunInBackground() else onDismiss() },
                )
            }
            Spacer(Modifier.height(12.dp))

            if (intakeErrorMessage != null) {
                Card(background = c.card) {
                    Text("Could not open image", style = RsType.body(16f, androidx.compose.ui.text.font.FontWeight.Medium), color = c.ink)
                    Text(
                        intakeErrorMessage,
                        style = RsType.body(14f, lineHeightRatio = 1.5f),
                        color = c.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                RsButton(onClick = onDismiss, variant = RsButtonVariant.Primary, size = RsButtonSize.Lg, full = true, label = "Close")
                return@Column
            }

            /* Finished: inline before/after plus Save, Share and Open. */
            if (completedCreation != null) {
                BeforeAfter(
                    beforeUri = "file://${completedCreation.beforeImagePath}",
                    afterUri = File(completedCreation.outputPath).let { "file://${it.absolutePath}" },
                    contentDescription = "Before and after of the finished upscale",
                    showOriginal = false,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                Spacer(Modifier.height(10.dp))
                val summary = if (completedCreation.outputWidth > 0 && completedCreation.outputHeight > 0) {
                    "${completedCreation.inputWidth} × ${completedCreation.inputHeight} → " +
                        "${completedCreation.outputWidth} × ${completedCreation.outputHeight}"
                } else {
                    completedCreation.presetLabel
                }
                Text(
                    savedConfirmationMessage ?: "$summary · Saved to Pictures/RealScale-Pro",
                    style = RsType.body(13f, lineHeightRatio = 1.4f),
                    color = c.muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RsButton(
                        onClick = { onSaveCompleted(completedCreation) },
                        variant = RsButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        label = "Save",
                    )
                    RsButton(
                        onClick = { onShareCompleted(context, completedCreation) },
                        modifier = Modifier.weight(1f),
                        icon = LucideIcons.Share2,
                        label = "Share",
                    )
                    RsButton(
                        onClick = { onOpenInFullApp(completedCreation) },
                        modifier = Modifier.weight(1f),
                        label = "Open",
                    )
                }
                return@Column
            }

            /* Source row: thumbnail, name and dimensions. */
            if (stagedPhoto != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.card)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = stagedPhoto.stagedFile,
                        contentDescription = "Source photo thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(RsRadius.sm.dp))
                            .background(c.soft),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            stagedPhoto.displayName,
                            style = RsType.body(15f, androidx.compose.ui.text.font.FontWeight.Medium),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (stagedPhoto.width > 0 && stagedPhoto.height > 0) {
                                "${stagedPhoto.width} × ${stagedPhoto.height} · ${stagedPhoto.formatLabel}"
                            } else {
                                stagedPhoto.formatLabel
                            },
                            style = RsType.body(13f),
                            color = c.muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            /* Preflight advisory, inline instead of a dialog. */
            preflightAdvisory?.let { advisory ->
                Card(background = c.card) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(LucideIcons.TriangleAlert, contentDescription = null, tint = c.warning, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(advisory.title, style = RsType.body(15f, androidx.compose.ui.text.font.FontWeight.Medium), color = c.ink)
                            Text(
                                advisory.message,
                                style = RsType.body(13f, lineHeightRatio = 1.5f),
                                color = c.body,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        advisory.mitigations.forEach { mitigation ->
                            RsButton(
                                onClick = { onApplyPreflightMitigation(mitigation) },
                                variant = RsButtonVariant.Primary,
                                size = RsButtonSize.Sm,
                                full = true,
                                label = mitigation.label,
                            )
                        }
                        if (advisory.canProceedAnyway) {
                            RsButton(
                                onClick = onConfirmPreflightProceedAnyway,
                                size = RsButtonSize.Sm,
                                full = true,
                                label = "Continue anyway",
                            )
                        }
                        RsButton(onClick = onDismissPreflightAdvisory, variant = RsButtonVariant.Ghost, full = true, label = "Cancel")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            /* Running / queued state. */
            if (isRunning) {
                Card(background = c.card) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val stageLabel = when (engineState) {
                            is EngineState.Preparing -> "Preparing"
                            is EngineState.Running -> running?.stage?.humanLabel ?: "Enhancing"
                            else -> "Enhancing"
                        }
                        val batch = if (running != null && running.totalFiles > 1) {
                            " · ${running.currentFileIndex} of ${running.totalFiles}"
                        } else {
                            ""
                        }
                        Text("$stageLabel$batch", style = RsType.body(15f, androidx.compose.ui.text.font.FontWeight.Medium), color = c.ink)
                        val metric = when {
                            running?.hasDeterminateProgress == true -> {
                                val pct = (running.progressFraction * 100f).roundToInt().coerceIn(1, 99)
                                val eta = running.etaMillis?.let {
                                    " · ~${com.tumuyan.ncnn.realsr.core.plan.fmtClock(it / 1000.0)} left"
                                }.orEmpty()
                                "$pct%$eta"
                            }
                            running != null -> "${(running.elapsedMillis / 1000L).coerceAtLeast(1L)} s elapsed"
                            else -> "Starting…"
                        }
                        Text(metric, style = RsType.mono(12f), color = c.muted)
                    }
                    RsProgressBar(
                        value = running?.progressFraction?.coerceAtLeast(0.03f) ?: 0.03f,
                        label = "Quick upscale progress",
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                if (queue.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Card(background = c.soft) {
                        EyebrowLabel("Queue · ${queue.size} items")
                        queue.take(4).forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${item.positionInBatch} of ${item.totalInBatch} · ${item.fileName}",
                                    style = RsType.body(13f),
                                    color = if (item.isRunning) c.ink else c.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (item.isRunning) "Running" else "Cancel",
                                    style = RsType.body(12f, androidx.compose.ui.text.font.FontWeight.Medium),
                                    color = if (item.isRunning) c.muted else c.primary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(enabled = !item.isRunning) { onCancelQueuedItem(item.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (showNotificationRationale) {
                    Card(background = c.card) {
                        Text("Notify when finished?", style = RsType.body(15f, androidx.compose.ui.text.font.FontWeight.Medium), color = c.ink)
                        Text(
                            "Allow notifications so RealScale-Pro can show a preview with Share & Open actions when background enhancement completes.",
                            style = RsType.body(13f, lineHeightRatio = 1.5f),
                            color = c.body,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RsButton(
                                onClick = onConfirmNotificationPermissionAndBackground,
                                variant = RsButtonVariant.Primary,
                                modifier = Modifier.weight(1f),
                                label = "Allow",
                            )
                            RsButton(
                                onClick = onSkipNotificationPermissionAndBackground,
                                modifier = Modifier.weight(1f),
                                label = "Continue quietly",
                            )
                        }
                    }
                } else {
                    RsButton(
                        onClick = onRunInBackground,
                        variant = RsButtonVariant.Primary,
                        size = RsButtonSize.Lg,
                        full = true,
                        label = "Run in background",
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RsButton(onClick = onCancelJob, modifier = Modifier.weight(1f), label = "Cancel")
                    RsButton(
                        onClick = { onOpenInFullApp(null) },
                        modifier = Modifier.weight(1f),
                        label = "Open in RealScale-Pro",
                    )
                }
                return@Column
            }

            /* Failure with one-tap recovery. */
            if (engineState is EngineState.Failed) {
                Card(background = c.card) {
                    Text("Could not complete upscale", style = RsType.body(16f, androidx.compose.ui.text.font.FontWeight.Medium), color = c.ink)
                    Text(
                        engineState.userMessage,
                        style = RsType.body(13f, lineHeightRatio = 1.5f),
                        color = c.body,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (engineState.recoveryActions.isNotEmpty()) {
                        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            engineState.recoveryActions.forEachIndexed { index, action ->
                                RsButton(
                                    onClick = { onApplyRecovery(action) },
                                    variant = if (index == 0) RsButtonVariant.Primary else RsButtonVariant.Secondary,
                                    full = true,
                                    label = action.label,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            /* Presets: last used is preselected, uninstalled ones offer Get model. */
            EyebrowLabel("Preset")
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UpscalePreset.ALL.chunked(2).forEach { rowPresets ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPresets.forEach { preset ->
                            val active = preset.id == selectedPreset.id
                            val ready = isPresetReady(preset, catalogModels)
                            val modelState = catalogModels.firstOrNull { it.catalog.id == preset.defaultModelId }
                            val downloading = modelState?.status == ModelInstallStatus.DOWNLOADING ||
                                modelState?.status == ModelInstallStatus.QUEUED ||
                                modelState?.status == ModelInstallStatus.VERIFYING
                            RsChip(
                                text = preset.title,
                                sub = when {
                                    !preset.requiresDownload -> "Ready"
                                    ready -> "Installed"
                                    downloading -> "Downloading…"
                                    else -> "Get model"
                                },
                                selected = active,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectPreset(preset) },
                            )
                        }
                        if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            /* Scale toggle and the estimated output size. */
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4).forEach { option ->
                        val supported = option in selectedPreset.supportedScales
                        RsChip(
                            text = "$option×",
                            selected = option == selectedScale,
                            enabled = supported,
                            onClick = { onSelectScale(option) },
                        )
                    }
                }
                Text(
                    buildOutputEstimateLabel(stagedPhoto, selectedScale, outputFormat),
                    style = RsType.bodyTnum(13f),
                    color = c.muted,
                )
            }

            Spacer(Modifier.height(16.dp))
            HairlineSoftDivider()
            Spacer(Modifier.height(16.dp))

            val ready = isPresetReady(selectedPreset, catalogModels)
            if (ready) {
                RsButton(
                    onClick = onEnhance,
                    variant = RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    enabled = stagedPhoto != null,
                    icon = LucideIcons.Zap,
                    label = "Enhance",
                )
            } else {
                val modelState = catalogModels.firstOrNull { it.catalog.id == selectedPreset.defaultModelId }
                val busy = modelState?.status == ModelInstallStatus.DOWNLOADING ||
                    modelState?.status == ModelInstallStatus.QUEUED ||
                    modelState?.status == ModelInstallStatus.VERIFYING
                RsButton(
                    onClick = { onDownloadModel(selectedPreset.defaultModelId) },
                    variant = RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    enabled = !busy,
                    icon = LucideIcons.Download,
                    label = if (busy) {
                        "Downloading ${selectedPreset.title} model…"
                    } else {
                        String.format(Locale.US, "Get model · %.1f MB", selectedPreset.approxDownloadMb)
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            RsButton(
                onClick = { onOpenInFullApp(null) },
                variant = RsButtonVariant.Ghost,
                full = true,
                label = "Open in RealScale-Pro",
            )
        }
    }
}

@Composable
private fun Card(
    background: androidx.compose.ui.graphics.Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalRsColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(background)
            .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
            .padding(14.dp),
        content = content,
    )
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
