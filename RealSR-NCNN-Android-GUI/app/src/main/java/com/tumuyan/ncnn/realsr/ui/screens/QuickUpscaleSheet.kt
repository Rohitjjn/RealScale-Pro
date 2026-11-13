package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.QueuedJobItem
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.PresetId
import com.tumuyan.ncnn.realsr.core.engine.PreflightMitigation
import com.tumuyan.ncnn.realsr.core.engine.PreflightVerdict
import com.tumuyan.ncnn.realsr.core.engine.StagedInputPhoto
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.model.ModelInstallStatus
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleChip
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleProgressBar
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/** Preset metadata for the quick sheet — mirrors engine presets exactly. */
private val QUICK_PRESETS: List<UpscalePreset> = UpscalePreset.ALL

/**
 * Translucent quick-upscale sheet (ACTION_VIEW). Renders the staged photo, preset/scale pickers,
 * required-model download state and the live engine status. All state is supplied by the host activity.
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
    preflightAdvisory: PreflightVerdict.Advisory?,
    onSelectPreset: (UpscalePreset) -> Unit,
    onSelectScale: (Int) -> Unit,
    onDownloadModel: (String) -> Unit,
    onCancelModelDownload: (String) -> Unit,
    onEnhance: () -> Unit,
    onApplyPreflightMitigation: (PreflightMitigation) -> Unit,
    onConfirmPreflightProceedAnyway: () -> Unit,
    onDismissPreflightAdvisory: () -> Unit,
    onApplyRecovery: (com.tumuyan.ncnn.realsr.core.engine.RecoveryAction) -> Unit,
    onRunInBackground: () -> Unit,
    onConfirmNotificationPermissionAndBackground: () -> Unit,
    onSkipNotificationPermissionAndBackground: () -> Unit,
    onCancelJob: () -> Unit,
    onCancelQueuedItem: (String) -> Unit,
    onSaveCompleted: (RecentCreationEntity) -> Unit,
    onShareCompleted: (android.content.Context, RecentCreationEntity) -> Unit,
    onOpenInFullApp: (RecentCreationEntity?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val running = engineState is EngineState.Running || engineState is EngineState.Preparing
    val requiredModel = selectedPreset.defaultModelId.takeIf { selectedPreset.requiresDownload }
    val requiredState = requiredModel?.let { id -> catalogModels.firstOrNull { it.catalog.id == id } }
    val requiredInstalled = selectedPreset.id == PresetId.INSTANT || requiredState?.isInstalled == true
    val requiredDownloading = requiredState?.status == ModelInstallStatus.DOWNLOADING ||
        requiredState?.status == ModelInstallStatus.QUEUED || requiredState?.status == ModelInstallStatus.VERIFYING
    val requiredProgress = requiredState?.progressFraction ?: 0f

    Box(Modifier.fillMaxSize()) {
        // Scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                    indication = null,
                ) { onDismiss() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .clip(RealScaleShape.SheetTop)
                .background(c.canvas),
        ) {
            // Grab handle + close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(c.strong))
                }
                RealScaleIconButton(
                    onClick = onDismiss,
                    variant = IconButtonVariant.Plain,
                    contentDescription = "Close",
                    icon = painterResource(R.drawable.ic_lucide_x),
                )
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                if (completedCreation != null) {
                    CompletedContent(
                        creation = completedCreation,
                        savedMessage = savedConfirmationMessage,
                        onSave = { onSaveCompleted(completedCreation) },
                        onShare = { onShareCompleted(context, completedCreation) },
                        onOpenFullApp = { onOpenInFullApp(completedCreation) },
                    )
                } else if (intakeErrorMessage != null) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text("Can't open this image", style = RealScaleType.Display28, color = c.ink)
                        Text(intakeErrorMessage, style = RealScaleType.Body15, color = c.body, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                        RealScaleButton(onClick = onDismiss, variant = ButtonVariant.Primary, size = ButtonSize.Lg, full = true, text = "Close")
                    }
                } else {
                    // Staged preview
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RealScaleShape.Lg)
                            .background(c.card),
                    ) {
                        val preview = stagedPhoto?.stagedUri
                        if (preview != null) {
                            AsyncImage(
                                model = preview,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))),
                            )
                        }
                        Text(
                            text = stagedPhoto?.displayName ?: "Preparing…",
                            style = RealScaleType.Body13,
                            color = c.onDark,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    // Preset chips
                    Text("Preset", style = RealScaleType.Eyebrow, color = c.muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_PRESETS.forEach { preset ->
                            RealScaleChip(
                                label = preset.title,
                                selected = preset.id == selectedPreset.id,
                                onClick = { onSelectPreset(preset) },
                                enabled = !running,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Scale chips (only scales the preset supports)
                    Text("Scale", style = RealScaleType.Eyebrow, color = c.muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedPreset.supportedScales.forEach { scale ->
                            RealScaleChip(
                                label = "${scale}×",
                                selected = scale == selectedScale,
                                onClick = { onSelectScale(scale) },
                                enabled = !running,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    if (running) {
                        RunningContent(
                            state = engineState,
                            queue = queue,
                            onCancel = onCancelJob,
                            onCancelQueuedItem = onCancelQueuedItem,
                        )
                    } else {
                        // Required model state
                        if (!requiredInstalled && !requiredDownloading && requiredState != null) {
                            RealScaleGroup {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onDownloadModel(requiredState.catalog.id) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lucide_download),
                                        contentDescription = null,
                                        tint = c.ink,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text("Download ${requiredState.catalog.displayName}", style = RealScaleType.Body15Medium, color = c.ink)
                                        Text(
                                            "${requiredState.catalog.totalBytes / 1_000_000} MB · needed once, then it works offline",
                                            style = RealScaleType.Body13,
                                            color = c.body,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        } else if (requiredDownloading && requiredState != null) {
                            RealScaleGroup {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "Downloading ${requiredState.catalog.displayName} · ${(requiredProgress * 100).toInt()}%",
                                            style = RealScaleType.Body14,
                                            color = c.ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "Cancel",
                                            style = RealScaleType.Body14Medium,
                                            color = c.error,
                                            modifier = Modifier
                                                .clickable { onCancelModelDownload(requiredState.catalog.id) }
                                                .padding(8.dp),
                                        )
                                    }
                                    RealScaleProgressBar(progress = requiredProgress, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Preflight advisory (real engine verdicts only)
                        if (preflightAdvisory != null) {
                            AdvisoryCard(
                                advisory = preflightAdvisory,
                                onApply = onApplyPreflightMitigation,
                                onProceed = onConfirmPreflightProceedAnyway,
                                onDismiss = onDismissPreflightAdvisory,
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        if (engineState is EngineState.Failed) {
                            FailedCard(
                                message = engineState.userMessage,
                                recoveryActions = engineState.recoveryActions,
                                onApplyRecovery = onApplyRecovery,
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        RealScaleButton(
                            onClick = onEnhance,
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Lg,
                            full = true,
                            enabled = stagedPhoto != null && requiredInstalled && !requiredDownloading,
                            icon = painterResource(R.drawable.ic_lucide_sparkles),
                            text = if (requiredDownloading) "Downloading model…" else "Enhance photo",
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Runs fully on this device. The original is never changed.",
                            style = RealScaleType.Caption12,
                            color = c.muted,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun RunningContent(
    state: EngineState,
    queue: List<QueuedJobItem>,
    onCancel: () -> Unit,
    onCancelQueuedItem: (String) -> Unit,
) {
    val c = LocalRealScaleColors.current
    val pct = when (state) {
        is EngineState.Running -> (state.progressFraction * 100).toInt().coerceIn(0, 100)
        else -> 0
    }
    val fileName = when (state) {
        is EngineState.Running -> state.currentFileName
        else -> null
    }
    Text("Upscaling…", style = RealScaleType.Eyebrow, color = c.muted)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$pct%", style = RealScaleType.Display34, color = c.ink, modifier = Modifier.weight(1f))
        if (state is EngineState.Running && state.totalFiles > 1) {
            Text("Photo ${state.currentFileIndex} of ${state.totalFiles}", style = RealScaleType.Mono12, color = c.muted)
        }
    }
    RealScaleProgressBar(
        progress = when (state) {
            is EngineState.Running -> state.progressFraction
            else -> 0f
        },
        modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
    )
    if (fileName != null) {
        Text(fileName, style = RealScaleType.Mono12, color = c.muted, maxLines = 1)
    }
    Spacer(Modifier.height(12.dp))
    RealScaleButton(onClick = onCancel, variant = ButtonVariant.Secondary, full = true, text = "Cancel")
}

@Composable
private fun FailedCard(
    message: String,
    recoveryActions: List<com.tumuyan.ncnn.realsr.core.engine.RecoveryAction>,
    onApplyRecovery: (com.tumuyan.ncnn.realsr.core.engine.RecoveryAction) -> Unit,
) {
    val c = LocalRealScaleColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RealScaleShape.Md)
            .border(1.dp, c.error, RealScaleShape.Md)
            .padding(14.dp),
    ) {
        Text("Something went wrong", style = RealScaleType.Body15Strong, color = c.error)
        Text(message, style = RealScaleType.Body14, color = c.body, modifier = Modifier.padding(top = 6.dp))
        recoveryActions.forEach { action ->
            Spacer(Modifier.height(8.dp))
            RealScaleButton(onClick = { onApplyRecovery(action) }, variant = ButtonVariant.Secondary, size = ButtonSize.Sm, text = action.label)
        }
    }
}

@Composable
private fun AdvisoryCard(
    advisory: PreflightVerdict.Advisory,
    onApply: (PreflightMitigation) -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalRealScaleColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RealScaleShape.Md)
            .border(1.dp, c.amber, RealScaleShape.Md)
            .padding(14.dp),
    ) {
        Text(advisory.title, style = RealScaleType.Body15Strong, color = c.ink)
        Text(advisory.message, style = RealScaleType.Body14, color = c.body, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            advisory.mitigations.firstOrNull()?.let { mitigation ->
                RealScaleButton(onClick = { onApply(mitigation) }, variant = ButtonVariant.Primary, size = ButtonSize.Sm, text = mitigation.label)
            }
            if (advisory.canProceedAnyway) {
                RealScaleButton(onClick = onProceed, variant = ButtonVariant.Secondary, size = ButtonSize.Sm, text = "Proceed anyway")
            }
            RealScaleIconButton(onClick = onDismiss, variant = IconButtonVariant.Plain, contentDescription = "Dismiss", icon = painterResource(R.drawable.ic_lucide_x))
        }
    }
}

@Composable
private fun CompletedContent(
    creation: RecentCreationEntity,
    savedMessage: String?,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenFullApp: () -> Unit,
) {
    val c = LocalRealScaleColors.current
    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(R.drawable.ic_lucide_circle_check),
            contentDescription = null,
            tint = c.success,
            modifier = Modifier.size(56.dp),
        )
        Text("Upscale complete", style = RealScaleType.Display28, color = c.ink, modifier = Modifier.padding(top = 12.dp))
        Text(
            "${creation.outputWidth} × ${creation.outputHeight}",
            style = RealScaleType.Mono13,
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (savedMessage != null) {
            Text(savedMessage, style = RealScaleType.Body13, color = c.body, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RealScaleButton(onClick = onShare, variant = ButtonVariant.Primary, size = ButtonSize.Lg, full = true, icon = painterResource(R.drawable.ic_lucide_share_2), text = "Share", modifier = Modifier.weight(1f))
            RealScaleButton(onClick = onOpenFullApp, variant = ButtonVariant.Secondary, size = ButtonSize.Lg, full = true, text = "Open in app", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        RealScaleButton(onClick = onSave, variant = ButtonVariant.Ghost, full = true, text = "Save to gallery again")
    }
}
