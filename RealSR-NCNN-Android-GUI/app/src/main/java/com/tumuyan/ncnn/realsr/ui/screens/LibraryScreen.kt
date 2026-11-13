package com.tumuyan.ncnn.realsr.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.ui.components.AccessibleIconButton
import com.tumuyan.ncnn.realsr.ui.components.BrandLogoMedallion
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.rememberReduceMotion
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.io.File
import java.util.Locale

enum class LibrarySortOption(val label: String) {
    NEWEST("Newest"),
    LARGEST("Largest"),
    FASTEST("Fastest")
}

object LibraryQueryEngine {
    fun filterAndSort(
        creations: List<RecentCreationEntity>,
        rawQuery: String,
        sort: LibrarySortOption
    ): List<RecentCreationEntity> {
        val q = rawQuery.trim().lowercase(Locale.US)
        val matched = if (q.isEmpty()) {
            creations
        } else {
            creations.filter { c ->
                val res = "${c.outputWidth}x${c.outputHeight}"
                val resTimes = "${c.outputWidth}×${c.outputHeight}"
                c.presetLabel.lowercase(Locale.US).contains(q) ||
                    c.modelId.lowercase(Locale.US).contains(q) ||
                    "${c.scale}x".contains(q) ||
                    "${c.scale}×".contains(q) ||
                    res.contains(q) ||
                    resTimes.contains(q)
            }
        }
        return when (sort) {
            LibrarySortOption.NEWEST -> matched.sortedByDescending { it.createdAtEpochMs }
            LibrarySortOption.LARGEST -> matched.sortedByDescending {
                it.outputWidth.toLong() * it.outputHeight.toLong()
            }
            LibrarySortOption.FASTEST -> matched.sortedBy { it.durationMs }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    creations: List<RecentCreationEntity>,
    hapticsEnabled: Boolean,
    onOpenCreation: (RecentCreationEntity) -> Unit,
    onCompareSelected: (List<RecentCreationEntity>) -> Unit,
    onShareSelected: (Context, List<RecentCreationEntity>) -> Unit,
    onDeleteSelected: (List<RecentCreationEntity>) -> Unit,
    onGoToStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val colors = RealScaleThemeTokens.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOption by rememberSaveable { mutableStateOf(LibrarySortOption.NEWEST) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }

    // Keep selectedIds synced with live DB items
    val existingIds = remember(creations) { creations.map { it.id }.toSet() }
    val validSelectedIds = selectedIds.intersect(existingIds)

    val filtered = remember(creations, searchQuery, sortOption) {
        LibraryQueryEngine.filterAndSort(creations, searchQuery, sortOption)
    }

    val selectedEntities = remember(creations, validSelectedIds) {
        creations.filter { it.id in validSelectedIds }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        // Editorial Library Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectionMode) "${validSelectedIds.size} selected" else "Library",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.ink
                )
                Text(
                    text = if (selectionMode) {
                        "Tap photos to compare, share, or remove"
                    } else {
                        "${creations.size} enhanced creation${if (creations.size == 1) "" else "s"} on this device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (creations.isNotEmpty()) {
                    QuietOutlineButton(
                        text = if (selectionMode) "Done" else "Select",
                        onClick = {
                            if (selectionMode) {
                                selectionMode = false
                                selectedIds = emptySet()
                            } else {
                                selectionMode = true
                            }
                        }
                    )
                }
                if (!selectionMode) {
                    AccessibleIconButton(
                        icon = RealScaleIcons.Settings,
                        contentDescription = "Open Settings",
                        onClick = onOpenSettings
                    )
                }
            }
        }

        if (creations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                EditorialCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = colors.surfaceSoft,
                    contentPadding = PaddingValues(Spacing.xl)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BrandLogoMedallion(size = 52.dp)
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = "No creations yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.ink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Every upscaled photo is saved privately in your local library for instant side-by-side comparison.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        TerracottaPrimaryButton(
                            text = "Enhance a photo",
                            leadingIcon = RealScaleIcons.Sparkle,
                            onClick = onGoToStudio
                        )
                    }
                }
            }
        } else {
            // Search + Sort controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Search by preset, scale, or resolution…",
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
                                contentDescription = "Clear library search",
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        val allFilteredIds = filtered.map { it.id }.toSet()
                        val allSelected = allFilteredIds.isNotEmpty() && allFilteredIds.all { it in validSelectedIds }
                        Text(
                            text = if (allSelected) "Deselect all" else "Select all (${filtered.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.terracotta,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    selectedIds = if (allSelected) emptySet() else allFilteredIds
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Sort",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.muted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        LibrarySortOption.entries.forEach { opt ->
                            val active = opt == sortOption
                            Surface(
                                shape = CircleShape,
                                color = if (active) colors.surfaceStrong else colors.canvas,
                                border = BorderStroke(1.dp, if (active) colors.ink else colors.hairline),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 34.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.RadioButton) { sortOption = opt }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = opt.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.ink
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.xs,
                    bottom = Spacing.xl
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { item ->
                    val isSelected = item.id in validSelectedIds
                    LibraryGridTile(
                        creation = item,
                        selectionMode = selectionMode,
                        selected = isSelected,
                        reduceMotion = reduceMotion,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (isSelected) validSelectedIds - item.id else validSelectedIds + item.id
                            } else {
                                onOpenCreation(item)
                            }
                        },
                        onLongClick = {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            selectionMode = true
                            selectedIds = validSelectedIds + item.id
                        }
                    )
                }
            }

            // Sticky Multi-Select Action Bar (Compare / Share / Delete)
            if (selectionMode && selectedEntities.isNotEmpty()) {
                Surface(
                    color = colors.canvas,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            TerracottaPrimaryButton(
                                text = "Compare (${selectedEntities.size})",
                                onClick = {
                                    val chosen = selectedEntities
                                    selectionMode = false
                                    selectedIds = emptySet()
                                    onCompareSelected(chosen)
                                },
                                modifier = Modifier.weight(1.15f)
                            )
                            QuietOutlineButton(
                                text = "Share",
                                leadingIcon = RealScaleIcons.Share,
                                onClick = {
                                    onShareSelected(context, selectedEntities)
                                },
                                modifier = Modifier.weight(0.95f)
                            )
                            QuietOutlineButton(
                                text = "Delete",
                                leadingIcon = RealScaleIcons.Trash,
                                tint = colors.error,
                                onClick = {
                                    val toRemove = selectedEntities
                                    selectedIds = emptySet()
                                    selectionMode = false
                                    onDeleteSelected(toRemove)
                                },
                                modifier = Modifier.weight(0.95f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun LibraryGridTile(
    creation: RecentCreationEntity,
    selectionMode: Boolean,
    selected: Boolean,
    reduceMotion: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val sharedMod = if (!reduceMotion && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "creation-${creation.id}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceCard,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.terracotta else colors.hairline
        ),
        modifier = Modifier
            .then(sharedMod)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(Spacing.xs)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceSoft)
            ) {
                AsyncImage(
                    model = File(creation.outputPath),
                    contentDescription = creation.presetLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (selectionMode) {
                    Surface(
                        shape = CircleShape,
                        color = if (selected) colors.terracotta else colors.canvas.copy(alpha = 0.88f),
                        border = BorderStroke(1.5.dp, if (selected) colors.onTerracotta else colors.hairline),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                    ) {
                        if (selected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = RealScaleIcons.Check,
                                    contentDescription = "Selected",
                                    tint = colors.onTerracotta,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
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
                    color = colors.muted,
                    maxLines = 1
                )
            }
        }
    }
}
