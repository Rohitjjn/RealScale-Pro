package com.tumuyan.ncnn.realsr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.core.engine.CropPreviewResult
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing

sealed interface CropPreviewUiState {
    data object Hidden : CropPreviewUiState
    data class Loading(val presetTitle: String, val scale: Int) : CropPreviewUiState
    data class Ready(val result: CropPreviewResult) : CropPreviewUiState
    data class Error(val message: String) : CropPreviewUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropPreviewSheet(
    previewState: CropPreviewUiState,
    supportedScales: List<Int>,
    selectedScale: Int,
    onSelectScaleAndRerun: (Int) -> Unit,
    onEnhanceFullPhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    if (previewState is CropPreviewUiState.Hidden) return
    val colors = RealScaleThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.canvas,
        contentColor = colors.ink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    EditorialOverline(text = "Fast detail check")
                    Text(
                        text = "Crop preview",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.ink
                    )
                }

                // Quick 2x / 4x toggle right inside preview sheet
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    listOf(2, 4).forEach { scaleOpt ->
                        val supported = scaleOpt in supportedScales
                        val active = selectedScale == scaleOpt
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = when {
                                active -> colors.terracotta
                                supported -> colors.surfaceSoft
                                else -> colors.canvas
                            },
                            border = BorderStroke(1.dp, if (active) colors.terracotta else colors.hairline),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 38.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(enabled = supported, role = Role.RadioButton) {
                                    onSelectScaleAndRerun(scaleOpt)
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${scaleOpt}×",
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
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (previewState) {
                is CropPreviewUiState.Loading -> {
                    Text(
                        text = "Rendering ${previewState.presetTitle} · ${previewState.scale}× sample patch…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    IndeterminateHairlineBar()
                    Spacer(modifier = Modifier.height(180.dp))
                }

                is CropPreviewUiState.Ready -> {
                    val res = previewState.result
                    Text(
                        text = "${res.presetTitle} · ${res.cropWidth}×${res.cropHeight} → ${res.outputWidth}×${res.outputHeight} · ${formatDurationHuman(res.elapsedMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    BeforeAfterSplitView(
                        beforeFile = res.beforeCropFile,
                        afterFile = res.afterCropFile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(268.dp)
                    )
                }

                is CropPreviewUiState.Error -> {
                    Text(
                        text = previewState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                CropPreviewUiState.Hidden -> Unit
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                TerracottaPrimaryButton(
                    text = "Enhance full photo",
                    leadingIcon = RealScaleIcons.Sparkle,
                    onClick = {
                        onDismiss()
                        onEnhanceFullPhoto()
                    },
                    modifier = Modifier.weight(1.25f)
                )
                QuietOutlineButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.75f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
