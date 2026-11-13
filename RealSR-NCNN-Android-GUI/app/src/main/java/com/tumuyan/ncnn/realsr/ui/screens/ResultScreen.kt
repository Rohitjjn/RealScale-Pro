package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.ui.components.AccessibleIconButton
import com.tumuyan.ncnn.realsr.ui.components.BeforeAfterSplitView
import com.tumuyan.ncnn.realsr.ui.components.BrandLogoMedallion
import com.tumuyan.ncnn.realsr.ui.components.EditorialTopBar
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.formatBytesHuman
import com.tumuyan.ncnn.realsr.ui.components.formatDurationHuman
import com.tumuyan.ncnn.realsr.ui.components.rememberReduceMotion
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import java.io.File

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ResultScreen(
    creations: List<RecentCreationEntity>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onSave: (RecentCreationEntity) -> Unit,
    onShare: (android.content.Context, RecentCreationEntity) -> Unit,
    onTryAnotherPreset: (RecentCreationEntity) -> Unit,
    onDelete: (RecentCreationEntity) -> Unit = {},
    onBackToStudio: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val colors = RealScaleThemeTokens.colors
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val active = creations.getOrNull(selectedIndex) ?: creations.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        EditorialTopBar(
            title = "Result",
            onBack = onBackToStudio,
            trailing = if (active != null) {
                {
                    AccessibleIconButton(
                        icon = RealScaleIcons.Trash,
                        contentDescription = "Delete result",
                        onClick = { onDelete(active) }
                    )
                }
            } else {
                null
            }
        )

        if (active == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandLogoMedallion(size = 56.dp)
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "No result loaded",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                TerracottaPrimaryButton(
                    text = "Back to Studio",
                    onClick = onBackToStudio
                )
            }
            return
        }

        val sharedViewerModifier = if (!reduceMotion && sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "creation-${active.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        } else {
            Modifier
        }

        val outputSizeBytes = remember(active.outputPath) {
            runCatching { File(active.outputPath).takeIf { it.isFile }?.length() ?: 0L }.getOrDefault(0L)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Near edge-to-edge canvas fit for interactive Before/After split slider
            BeforeAfterSplitView(
                beforeFile = File(active.beforeImagePath),
                afterFile = File(active.outputPath),
                modifier = Modifier
                    .then(sharedViewerModifier)
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.sm)
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
            ) {
                // Multi-photo batch / comparison switcher
                if (creations.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = Spacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        itemsIndexed(creations, key = { _, item -> item.id }) { idx, item ->
                            val isCurrent = idx == selectedIndex
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) colors.terracotta else colors.hairline
                                ),
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onSelectIndex(idx) }
                            ) {
                                AsyncImage(
                                    model = File(item.outputPath),
                                    contentDescription = "Result ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                }

                // Details row: dimensions, preset, duration, file size, and gesture hint
                val dimText = if (active.inputWidth > 0 && active.outputWidth > 0) {
                    "${active.inputWidth} × ${active.inputHeight} → ${active.outputWidth} × ${active.outputHeight}"
                } else {
                    active.presetLabel
                }
                val sizePart = if (outputSizeBytes > 0L) " · ${formatBytesHuman(outputSizeBytes)}" else ""
                Text(
                    text = "$dimText · ${active.presetLabel} · ${formatDurationHuman(active.durationMs)}$sizePart",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Hold image for original · Double-tap for 100% pixel view",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Primary & secondary actions (all >= 48dp touch targets)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TerracottaPrimaryButton(
                        text = if (active.savedUri != null) "Saved · Save copy" else "Save",
                        leadingIcon = if (active.savedUri != null) RealScaleIcons.Check else RealScaleIcons.Download,
                        onClick = { onSave(active) },
                        modifier = Modifier.weight(1f)
                    )
                    QuietOutlineButton(
                        text = "Share",
                        leadingIcon = RealScaleIcons.Share,
                        onClick = { onShare(context, active) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                QuietOutlineButton(
                    text = "Try another preset",
                    leadingIcon = RealScaleIcons.Refresh,
                    onClick = { onTryAnotherPreset(active) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
