package com.tumuyan.ncnn.realsr.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.ImageContentClassifier
import com.tumuyan.ncnn.realsr.core.engine.PreflightMitigation
import com.tumuyan.ncnn.realsr.core.engine.SelectionInspectionSummary
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.ui.RealScaleUiState
import com.tumuyan.ncnn.realsr.ui.components.AccessibleIconButton
import com.tumuyan.ncnn.realsr.ui.components.BrandLogoMedallion
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.HairlineProgressBar
import com.tumuyan.ncnn.realsr.ui.components.PreflightAdvisoryDialog
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.rememberReduceMotion
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.io.File
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    state: RealScaleUiState,
    onPhotosPicked: (List<Uri>) -> Unit,
    onClearPhotos: () -> Unit,
    onPresetSelected: (UpscalePreset) -> Unit,
    onScaleSelected: (Int) -> Unit,
    onModelOverrideSelected: (String?) -> Unit,
    onStartUpscale: () -> Unit,
    onApplyPreflightMitigation: (PreflightMitigation) -> Unit = {},
    onConfirmPreflightProceedAnyway: () -> Unit = {},
    onDismissPreflightAdvisory: () -> Unit = {},
    onQuickDownloadModel: (String) -> Unit,
    onOpenModelHub: () -> Unit,
    onOpenLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenRecentCreation: (RecentCreationEntity) -> Unit,
    onShareRecentCreation: (Context, RecentCreationEntity) -> Unit = { _, _ -> },
    onRerunRecentCreation: (RecentCreationEntity) -> Unit = {},
    onDeleteRecentCreation: (RecentCreationEntity) -> Unit = {},
    onConsumePendingPhotoPicker: () -> Unit = {},
    onLaunchFadePlayed: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val colors = RealScaleThemeTokens.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = state.enginePreferences.hapticsEnabled
    val greeting = remember { editorialGreeting() }
    val reduceMotion = rememberReduceMotion()
    val shouldAnimateGreeting = !reduceMotion && !state.selection.hasPlayedLaunchFade
    val greetingProgress = remember {
        Animatable(if (shouldAnimateGreeting) 0f else 1f)
    }
    LaunchedEffect(shouldAnimateGreeting) {
        if (shouldAnimateGreeting) {
            greetingProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
            onLaunchFadePlayed()
        } else {
            greetingProgress.snapTo(1f)
            onLaunchFadePlayed()
        }
    }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 6.dp.toPx() } * (1f - greetingProgress.value)

    var inspectionSummary by remember { mutableStateOf<SelectionInspectionSummary?>(null) }
    LaunchedEffect(state.selection.selectedUris) {
        inspectionSummary = if (state.selection.selectedUris.isEmpty()) {
            null
        } else {
            ImageContentClassifier.inspectSelection(context, state.selection.selectedUris)
        }
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosPicked(uris)
        }
    }

    val launchPicker = {
        if (hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        multiPhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(state.selection.pendingOpenPhotoPicker) {
        if (state.selection.pendingOpenPhotoPicker) {
            onConsumePendingPhotoPicker()
            multiPhotoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val selectedPreset = state.selection.selectedPreset
    val isPresetReady = state.isPresetReadyOffline(selectedPreset)
    val defaultCatalogForPreset = state.defaultCatalogStateFor(selectedPreset)
    val installedCategoryModels = state.catalogModels.filter {
        it.isInstalled && it.catalog.category == selectedPreset.id.name
    }

    state.selection.preflightAdvisory?.let { advisory ->
        PreflightAdvisoryDialog(
            advisory = advisory,
            onApplyMitigation = onApplyPreflightMitigation,
            onProceedAnyway = onConfirmPreflightProceedAnyway,
            onDismiss = onDismissPreflightAdvisory
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        // Scrollable Studio canvas
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl)
        ) {
            // Top header bar with master brand logo + wordmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.xs)
                    .graphicsLayer {
                        alpha = greetingProgress.value
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandLogoMedallion(
                        size = 28.dp,
                        contentDescription = "RealScale-Pro brand emblem"
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "RealScale-Pro",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccessibleIconButton(
                        icon = RealScaleIcons.ModelHub,
                        contentDescription = "Open Model Hub",
                        badgeDot = state.activeDownloadsCount > 0,
                        onClick = onOpenModelHub
                    )
                    AccessibleIconButton(
                        icon = RealScaleIcons.Settings,
                        contentDescription = "Open Settings",
                        onClick = onOpenSettings
                    )
                }
            }

            // Warm editorial serif greeting
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    .graphicsLayer {
                        alpha = greetingProgress.value
                        translationY = slideOffsetPx
                    }
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "Enhance photos privately on your device — no uploads, no accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Single Photo Drop Zone (no duplicate inner button) or Crisp Picked / Batch Preview
            if (state.selection.selectedUris.isEmpty()) {
                EditorialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .defaultMinSize(minHeight = 164.dp),
                    containerColor = colors.surfaceCard,
                    borderColor = colors.hairline,
                    onClick = launchPicker,
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xl)
                ) {
                    Icon(
                        imageVector = RealScaleIcons.PhotoFrame,
                        contentDescription = null,
                        tint = colors.terracotta,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = "Choose a photo",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Tap anywhere here to pick one or several photos, or use Open with from Gallery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted
                    )
                }
            } else {
                val uris = state.selection.selectedUris
                val primaryUri = uris.first()
                val count = uris.size
                EditorialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    containerColor = colors.surfaceSoft,
                    contentPadding = PaddingValues(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(216.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(colors.surfaceCard)
                            .clickable(role = Role.Button, onClick = launchPicker)
                    ) {
                        AsyncImage(
                            model = primaryUri,
                            contentDescription = "Selected photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        val pillLabel = when {
                            count > 1 -> "Batch of $count"
                            inspectionSummary != null && inspectionSummary!!.primaryWidth > 0 ->
                                "${inspectionSummary!!.primaryWidth} × ${inspectionSummary!!.primaryHeight}"
                            else -> null
                        }
                        if (pillLabel != null) {
                            Surface(
                                shape = CircleShape,
                                color = colors.canvas.copy(alpha = 0.90f),
                                border = BorderStroke(1.dp, colors.hairline),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(Spacing.sm)
                            ) {
                                Text(
                                    text = pillLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.ink,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Stacked horizontal strip when a batch of N > 1 photos is picked
                    if (count > 1) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            items(uris, key = { it.toString() }) { itemUri ->
                                AsyncImage(
                                    model = itemUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(colors.surfaceCard)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuietOutlineButton(
                            text = "Change photo",
                            onClick = launchPicker
                        )
                        QuietOutlineButton(
                            text = "Clear",
                            onClick = onClearPhotos,
                            tint = colors.muted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Enhancement Style / Preset Selector with Smart Local Suggestion Badge
            Text(
                text = "Enhancement style",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))

            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                UpscalePreset.ALL.forEach { preset ->
                    val selected = preset.id == selectedPreset.id
                    val ready = state.isPresetReadyOffline(preset)
                    val catalogState = state.defaultCatalogStateFor(preset)
                    val isDownloading = catalogState?.status == ModelInstallStatus.DOWNLOADING ||
                        catalogState?.status == ModelInstallStatus.QUEUED ||
                        catalogState?.status == ModelInstallStatus.VERIFYING
                    val isSuggested = inspectionSummary?.suggestedPreset == preset.id

                    val animatedBorder by animateColorAsState(
                        targetValue = if (selected) colors.terracotta else colors.hairline,
                        animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                        label = "preset_border"
                    )

                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (selected) colors.surfaceCard else colors.canvas,
                        borderColor = animatedBorder,
                        onClick = {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onPresetSelected(preset)
                        },
                        contentPadding = PaddingValues(Spacing.md)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                val badgeText = when {
                                    !preset.requiresDownload -> "Built-in · Ready"
                                    ready -> "Installed"
                                    isDownloading -> "Downloading…"
                                    else -> String.format(Locale.US, "Get model · %.1f MB", preset.approxDownloadMb)
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = if (ready) colors.surfaceSoft else colors.terracottaTint,
                                    border = BorderStroke(1.dp, colors.hairline),
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (ready) colors.muted else colors.ink,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (isSuggested) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colors.terracottaTint,
                                        border = BorderStroke(1.dp, colors.terracotta.copy(alpha = 0.45f)),
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(
                                            text = "Suggested for this photo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.ink,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }

                        if (isDownloading && catalogState != null) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            HairlineProgressBar(fraction = catalogState.progressFraction)
                        }

                        // Optional installed model variant selector within this category
                        if (selected && installedCategoryModels.size > 1) {
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                items(installedCategoryModels, key = { it.catalog.id }) { m ->
                                    val activeModel = (state.selection.selectedModelOverrideId ?: preset.defaultModelId) == m.catalog.id
                                    Surface(
                                        shape = CircleShape,
                                        color = if (activeModel) colors.surfaceStrong else colors.surfaceSoft,
                                        border = BorderStroke(1.dp, if (activeModel) colors.ink else colors.hairline),
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { onModelOverrideSelected(m.catalog.id) }
                                    ) {
                                        Text(
                                            text = m.catalog.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.ink,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Dedicated 2x / 4x Scale Selector with clear explanation when a scale is unavailable
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Text(
                    text = "Output scale",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(2, 4).forEach { scaleOption ->
                        val supported = scaleOption in selectedPreset.supportedScales
                        val active = state.selection.selectedScale == scaleOption
                        Surface(
                            shape = MaterialTheme.shapes.medium,
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
                                .defaultMinSize(minWidth = 68.dp, minHeight = 44.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = supported, role = Role.RadioButton) {
                                    if (hapticsEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onScaleSelected(scaleOption)
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "${scaleOption}×",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        active -> colors.onTerracotta
                                        supported -> colors.ink
                                        else -> colors.subtle
                                    }
                                )
                            }
                        }
                    }

                    if (2 !in selectedPreset.supportedScales) {
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "${selectedPreset.title} runs natively at 4×",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }
                }
            }

            // Recent Creations strip + shared-element transition + long-press (Share / Re-run / Delete)
            Spacer(modifier = Modifier.height(Spacing.xl))
            HorizontalDivider(
                thickness = 1.dp,
                color = colors.hairlineSoft,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent creations",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                if (state.recentCreations.size > 3) {
                    Text(
                        text = "See all (${state.recentCreations.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.terracotta,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onOpenLibrary)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            if (state.recentCreations.isNotEmpty()) {
                val studioPreviewItems = remember(state.recentCreations) { state.recentCreations.take(8) }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(studioPreviewItems, key = { it.id }) { item ->
                        RecentCreationCard(
                            creation = item,
                            reduceMotion = reduceMotion,
                            hapticsEnabled = hapticsEnabled,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onOpenRecentCreation(item) },
                            onShare = { onShareRecentCreation(context, item) },
                            onRerun = { onRerunRecentCreation(item) },
                            onDelete = { onDeleteRecentCreation(item) }
                        )
                    }
                }
            } else {
                EditorialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    containerColor = colors.surfaceSoft,
                    borderColor = colors.hairlineSoft,
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.lg)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BrandLogoMedallion(size = 44.dp)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Your enhanced photos will appear here",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Every upscale runs privately on your device and stays in your local history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Sticky Bottom Primary CTA Bar with Live Tabular Summary
        val hasPhotos = state.selection.selectedUris.isNotEmpty()
        val liveSummary = ImageContentClassifier.buildLiveEnhanceSummary(
            inspection = inspectionSummary,
            scale = state.selection.selectedScale,
            preset = selectedPreset,
            format = state.enginePreferences.outputFormat,
            backend = state.enginePreferences.backend
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.canvas,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                ) {
                    Text(
                        text = liveSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasPhotos) colors.ink else colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    if (isPresetReady) {
                        TerracottaPrimaryButton(
                            text = "Enhance",
                            leadingIcon = RealScaleIcons.Sparkle,
                            enabled = hasPhotos,
                            onClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onStartUpscale()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val dlStatus = defaultCatalogForPreset?.status
                        val isBusy = dlStatus == ModelInstallStatus.DOWNLOADING ||
                            dlStatus == ModelInstallStatus.QUEUED ||
                            dlStatus == ModelInstallStatus.VERIFYING
                        TerracottaPrimaryButton(
                            text = if (isBusy) {
                                "Downloading ${selectedPreset.title} model…"
                            } else {
                                String.format(
                                    Locale.US,
                                    "Get %s model · %.1f MB",
                                    selectedPreset.title,
                                    selectedPreset.approxDownloadMb
                                )
                            },
                            leadingIcon = RealScaleIcons.Download,
                            enabled = !isBusy,
                            onClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onQuickDownloadModel(selectedPreset.defaultModelId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun RecentCreationCard(
    creation: RecentCreationEntity,
    reduceMotion: Boolean,
    hapticsEnabled: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val sharedModifier = if (!reduceMotion && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "creation-${creation.id}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }

    Box {
        Surface(
            modifier = Modifier
                .width(156.dp)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        showMenu = true
                    }
                ),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceSoft,
            border = BorderStroke(1.dp, colors.hairline),
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(Spacing.xs)) {
                AsyncImage(
                    model = File(creation.outputPath),
                    contentDescription = "${creation.presetLabel} creation",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .then(sharedModifier)
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(colors.surfaceCard)
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = creation.presetLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (creation.outputWidth > 0 && creation.outputHeight > 0) {
                    Text(
                        text = "${creation.outputWidth} × ${creation.outputHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    showMenu = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text("Re-run") },
                onClick = {
                    showMenu = false
                    onRerun()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}

private fun editorialGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
