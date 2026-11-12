package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.HairlineProgressBar
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.formatDurationHuman
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun ProcessingScreen(
    engineState: EngineState,
    onCancel: () -> Unit,
    onBackToStudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Spacing.md))

        when (engineState) {
            is EngineState.Running -> {
                val pctInt = (engineState.progressFraction * 100f).roundToInt().coerceIn(0, 99)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (engineState.previewUri != null) {
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = colors.surfaceSoft,
                            contentPadding = PaddingValues(Spacing.sm)
                        ) {
                            AsyncImage(
                                model = engineState.previewUri,
                                contentDescription = "Photo currently being enhanced",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }

                    Text(
                        text = if (engineState.totalFiles > 1) {
                            "Enhancing photo ${engineState.currentFileIndex} of ${engineState.totalFiles}"
                        } else {
                            "Enhancing your photo"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "${engineState.presetTitle} · ${engineState.currentFileName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceCard
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "$pctInt%",
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.ink,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                            val etaText = engineState.etaMillis?.let {
                                "About ${formatDurationHuman(it)} left"
                            } ?: "Estimating time…"
                            Text(
                                text = etaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        HairlineProgressBar(fraction = engineState.progressFraction.coerceAtLeast(0.03f))
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = engineState.latestStatusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = "Runs safely in the background if you switch apps or lock your screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtle,
                        textAlign = TextAlign.Center
                    )
                }

                QuietOutlineButton(
                    text = "Cancel",
                    leadingIcon = RealScaleIcons.Close,
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is EngineState.Preparing -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = engineState.label,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    HairlineProgressBar(fraction = 0.08f)
                }
                QuietOutlineButton(
                    text = "Cancel",
                    leadingIcon = RealScaleIcons.Close,
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is EngineState.Failed -> {
                EditorialCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = colors.surfaceCard
                ) {
                    Text(
                        text = "Could not complete upscale",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = engineState.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.body
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    TerracottaPrimaryButton(
                        text = "Back to Studio",
                        onClick = onBackToStudio,
                        modifier = Modifier.fillMaxWidth()
                    )
                    QuietOutlineButton(
                        text = "Open Settings",
                        leadingIcon = RealScaleIcons.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QuietOutlineButton(
                        text = "Return to Studio",
                        onClick = onBackToStudio
                    )
                }
            }
        }
    }
}
