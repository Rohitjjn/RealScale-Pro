package com.tumuyan.ncnn.realsr.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.ui.RealScaleUiState
import com.tumuyan.ncnn.realsr.ui.components.AccessibleIconButton
import com.tumuyan.ncnn.realsr.ui.components.BrandLogoMedallion
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.HairlineProgressBar
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.rememberReduceMotion
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.io.File
import java.util.Calendar
import java.util.Locale

@Composable
fun StudioScreen(
    state: RealScaleUiState,
    onPhotosPicked: (List<Uri>) -> Unit,
    onClearPhotos: () -> Unit,
    onPresetSelected: (UpscalePreset) -> Unit,
    onScaleSelected: (Int) -> Unit,
    onModelOverrideSelected: (String?) -> Unit,
    onStartUpscale: () -> Unit,
    onQuickDownloadModel: (String) -> Unit,
    onOpenModelHub: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecentCreation: (RecentCreationEntity) -> Unit,
    onLaunchFadePlayed: () -> Unit = {}
) {
    val colors = RealScaleThemeTokens.colors
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

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosPicked(uris)
        }
    }

    val launchPicker = {
        multiPhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val selectedPreset = state.selection.selectedPreset
    val isPresetReady = state.isPresetReadyOffline(selectedPreset)
    val defaultCatalogForPreset = state.defaultCatalogStateFor(selectedPreset)
    val installedCategoryModels = state.catalogModels.filter {
        it.isInstalled && it.catalog.category == selectedPreset.id.name
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.xxl)
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

        // Warm editorial serif greeting (subtle one-time <600ms entrance, skipped on reduce motion)
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

        // Hero Photo Picker / Preview Card (Tap 1)
        if (state.selection.selectedUris.isEmpty()) {
            EditorialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
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
                    text = "Select one or multiple images from your library, or share straight from Gallery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                QuietOutlineButton(
                    text = "Browse photos",
                    onClick = launchPicker,
                    leadingIcon = RealScaleIcons.PhotoFrame
                )
            }
        } else {
            val primaryUri = state.selection.selectedUris.first()
            val count = state.selection.selectedUris.size
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
                        .height(230.dp)
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
                    if (count > 1) {
                        Surface(
                            shape = CircleShape,
                            color = colors.canvas.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, colors.hairline),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(Spacing.sm)
                        ) {
                            Text(
                                text = "$count photos selected",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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

        // Style / Preset Selector
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

                EditorialCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (selected) colors.surfaceCard else colors.canvas,
                    borderColor = if (selected) colors.terracotta else colors.hairline,
                    onClick = { onPresetSelected(preset) },
                    contentPadding = PaddingValues(Spacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                val badgeText = when {
                                    !preset.requiresDownload -> "Built-in · Ready"
                                    ready -> "Installed"
                                    isDownloading -> "Downloading…"
                                    else -> String.format(Locale.US, "Get model · %.1f MB", preset.approxDownloadMb)
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = if (ready) colors.surfaceSoft else colors.terracottaTint,
                                    border = BorderStroke(1.dp, colors.hairline)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (ready) colors.muted else colors.ink,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                    }

                    if (isDownloading && catalogState != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        HairlineProgressBar(fraction = catalogState.progressFraction)
                    }

                    // Scale selector when selected and multiple scales supported
                    if (selected && preset.supportedScales.size > 1) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scale:",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                            preset.supportedScales.forEach { scaleOption ->
                                val activeScale = state.selection.selectedScale == scaleOption
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (activeScale) colors.terracotta else colors.surfaceSoft,
                                    border = BorderStroke(
                                        1.dp,
                                        if (activeScale) colors.terracotta else colors.hairline
                                    ),
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 52.dp, minHeight = 36.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable(role = Role.RadioButton) {
                                            onScaleSelected(scaleOption)
                                        }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${scaleOption}×",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (activeScale) colors.onTerracotta else colors.ink
                                        )
                                    }
                                }
                            }
                        }
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

        // Primary CTA (Tap 3 — or quiet model fetch CTA when un-downloaded preset chosen)
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            val hasPhotos = state.selection.selectedUris.isNotEmpty()
            val photoCount = state.selection.selectedUris.size
            if (isPresetReady) {
                val ctaLabel = when {
                    !hasPhotos -> "Choose a photo to begin"
                    photoCount == 1 -> "Upscale photo · ${state.selection.selectedScale}×"
                    else -> "Upscale $photoCount photos · ${state.selection.selectedScale}×"
                }
                TerracottaPrimaryButton(
                    text = ctaLabel,
                    leadingIcon = RealScaleIcons.Sparkle,
                    onClick = {
                        if (!hasPhotos) launchPicker() else onStartUpscale()
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
                    onClick = { onQuickDownloadModel(selectedPreset.defaultModelId) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                QuietOutlineButton(
                    text = "Browse all models in Model Hub",
                    onClick = onOpenModelHub,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Recent Creations strip + designed empty state featuring brand emblem
        Spacer(modifier = Modifier.height(Spacing.xl))
        HorizontalDivider(
            thickness = 1.dp,
            color = colors.hairlineSoft,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = "Recent creations",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        if (state.recentCreations.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(state.recentCreations, key = { it.id }) { item ->
                    RecentCreationCard(
                        creation = item,
                        onClick = { onOpenRecentCreation(item) }
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
}

@Composable
private fun RecentCreationCard(
    creation: RecentCreationEntity,
    onClick: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    EditorialCard(
        modifier = Modifier.width(156.dp),
        containerColor = colors.surfaceSoft,
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.xs)
    ) {
        AsyncImage(
            model = File(creation.outputPath),
            contentDescription = "${creation.presetLabel} creation",
            contentScale = ContentScale.Crop,
            modifier = Modifier
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

private fun editorialGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
