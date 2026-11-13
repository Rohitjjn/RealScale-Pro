package com.tumuyan.ncnn.realsr.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.QuickUpscaleActivity
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.QueuedJobItem
import com.tumuyan.ncnn.realsr.core.engine.RecoveryAction
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.EditorialOverline
import com.tumuyan.ncnn.realsr.ui.components.HairlineProgressBar
import com.tumuyan.ncnn.realsr.ui.components.IndeterminateHairlineBar
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
    queue: List<QueuedJobItem> = emptyList(),
    onCancel: () -> Unit,
    onCancelQueuedItem: (String) -> Unit = {},
    onApplyRecovery: (RecoveryAction) -> Unit = {},
    onRunInBackground: () -> Unit = {},
    onBackToStudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val context = LocalContext.current
    val uiPrefs = remember(context) {
        context.getSharedPreferences("realscale_ui_prefs", Context.MODE_PRIVATE)
    }
    var showNotifPrompt by remember { mutableStateOf(false) }
    var copiedDetails by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRunInBackground()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(Spacing.md))

        when (engineState) {
            is EngineState.Running -> {
                val pctInt = (engineState.progressFraction * 100f).roundToInt().coerceIn(1, 99)
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
                                    .height(210.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.lg))
                    }

                    EditorialOverline(text = engineState.stage.humanLabel)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (engineState.totalFiles > 1) {
                            "Photo ${engineState.currentFileIndex} of ${engineState.totalFiles}"
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
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colors.surfaceCard
                    ) {
                        if (engineState.hasDeterminateProgress) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "$pctInt%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.ink,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                                val etaText = engineState.etaMillis?.let {
                                    "About ${formatDurationHuman(it)} left"
                                } ?: formatElapsedLabel(engineState.elapsedMillis)
                                Text(
                                    text = etaText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            HairlineProgressBar(fraction = engineState.progressFraction.coerceAtLeast(0.03f))
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = engineState.stage.humanLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                                Text(
                                    text = formatElapsedLabel(engineState.elapsedMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            IndeterminateHairlineBar()
                        }
                    }

                    if (queue.size > 1) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = colors.surfaceSoft,
                            contentPadding = PaddingValues(Spacing.md)
                        ) {
                            EditorialOverline(text = "Queue · ${queue.size} photos")
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            queue.take(5).forEach { qItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
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
                                        text = if (qItem.isRunning) "Enhancing" else "Remove",
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

                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = "Runs safely in the background if you switch apps or lock your screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtle,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (showNotifPrompt) {
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = colors.terracottaTint,
                            borderColor = colors.terracotta,
                            contentPadding = PaddingValues(Spacing.md)
                        ) {
                            Text(
                                text = "Get notified when ready?",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "RealScale-Pro can show a preview notification with Share & Open actions when background work finishes.",
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
                                    onClick = {
                                        showNotifPrompt = false
                                        uiPrefs.edit().putBoolean(QuickUpscaleActivity.KEY_BG_NOTIF_EXPLAINED, true).apply()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onRunInBackground()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                QuietOutlineButton(
                                    text = "Continue quietly",
                                    onClick = {
                                        showNotifPrompt = false
                                        uiPrefs.edit().putBoolean(QuickUpscaleActivity.KEY_BG_NOTIF_EXPLAINED, true).apply()
                                        onRunInBackground()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        TerracottaPrimaryButton(
                            text = "Run in background",
                            onClick = {
                                val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED &&
                                    !uiPrefs.getBoolean(QuickUpscaleActivity.KEY_BG_NOTIF_EXPLAINED, false)
                                if (needsPrompt) {
                                    showNotifPrompt = true
                                } else {
                                    onRunInBackground()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    QuietOutlineButton(
                        text = "Cancel",
                        leadingIcon = RealScaleIcons.Close,
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            is EngineState.Preparing -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EditorialOverline(text = "Preparing")
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = engineState.label,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    IndeterminateHairlineBar()
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
                Spacer(modifier = Modifier.height(Spacing.md))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
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
                    if (engineState.recoveryActions.isEmpty()) {
                        TerracottaPrimaryButton(
                            text = "Back to Studio",
                            onClick = onBackToStudio,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        QuietOutlineButton(
                            text = "Back to Studio",
                            onClick = onBackToStudio,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    QuietOutlineButton(
                        text = "Open Settings",
                        leadingIcon = RealScaleIcons.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (engineState.technicalOutput.isNotBlank()) {
                        QuietOutlineButton(
                            text = if (copiedDetails) "Details copied" else "Copy details",
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("RealScale-Pro Error Details", engineState.technicalOutput))
                                copiedDetails = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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

private fun formatElapsedLabel(elapsedMs: Long): String {
    val sec = (elapsedMs / 1000L).coerceAtLeast(1L)
    return if (sec < 60L) {
        "${sec}s elapsed"
    } else {
        "${sec / 60L}m ${sec % 60L}s elapsed"
    }
}
