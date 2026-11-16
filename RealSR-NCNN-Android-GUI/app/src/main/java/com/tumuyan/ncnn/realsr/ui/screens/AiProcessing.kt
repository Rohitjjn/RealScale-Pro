package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.device.HapticKind
import com.tumuyan.ncnn.realsr.design.LocalReduceMotion
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsConfirmSheet
import com.tumuyan.ncnn.realsr.design.RsProgressBar
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.JobStatus
import com.tumuyan.ncnn.realsr.ui.state.Route
import com.tumuyan.ncnn.realsr.ui.state.rememberState

private val AI_TIPS = listOf(
    "The model rebuilds details instead of blurring them: edges, fabric, skin and text come back sharp.",
    "Only this photo is sent, once. Nothing is kept on the provider's side by this app.",
    "AI mode can invent detail — keep Faithful or the Conservative model for archival photos.",
    "You can cancel at any time; no credit is charged for an answer you never received.",
)

/**
 * AI Upscale Mode → **processing**.
 *
 * A different animation from the on-device job on purpose: the engine screen shows a tile grid being
 * filled (work inside this phone), this one shows a single beam sweeping the whole frame (one request
 * being rebuilt in the cloud), plus the stage the provider is at and the elapsed seconds.
 */
@Composable
fun AiProcessingScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    val job = state.aiJob

    if (job == null) {
        // Nothing to animate: the job is over and the store already moved the stack.
        LaunchedEffect(Unit) {
            val last = store.state.value.lastResultId
            val route = store.state.value.route
            if (last != null && route !is Route.Result && route !is Route.ResultFull) {
                store.replaceTop(Route.Result(last))
            } else if (route is Route.AiProcessing) {
                store.pop()
            }
        }
        return
    }

    val photo = store.photoById(job.photoId)
    var confirm by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf(AI_TIPS.first()) }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            kotlinx.coroutines.delay(5_000)
            index = (index + 1) % AI_TIPS.size
            tip = AI_TIPS[index]
        }
    }

    val transition = rememberInfiniteTransition(label = "ai-scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val ratio = photo?.let { (it.width.toFloat() / it.height).coerceIn(0.35f, 3f) } ?: 1f
    val running = job.status == JobStatus.Running

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(title = "AI Upscale")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(LucideIcons.Sparkles, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                Text(
                    job.modelLabel,
                    style = RsType.body(15f, FontWeight.Medium),
                    color = c.ink,
                )
                Text("·", style = RsType.body(15f), color = c.mutedSoft)
                Text(job.providerName, style = RsType.body(15f), color = c.muted)
            }

            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                val width = maxWidth
                val height = (width / ratio).coerceIn(200.dp, 380.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(RsRadius.lg.dp))
                        .background(c.card)
                        .drawBehind {
                            if (running) {
                                /*
                                 * One beam sweeping the frame, with a soft band ahead of it: the AI
                                 * counterpart of the engine screen's tile grid.
                                 */
                                val y = size.height * sweep
                                val bandHeight = size.height * 0.22f
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            c.amber.copy(alpha = 0.10f * pulse),
                                            c.amber.copy(alpha = 0.22f * pulse),
                                            Color.Transparent,
                                        ),
                                        startY = y - bandHeight,
                                        endY = y + bandHeight / 3f,
                                    ),
                                    topLeft = Offset(0f, (y - bandHeight).coerceAtLeast(0f)),
                                    size = androidx.compose.ui.geometry.Size(size.width, bandHeight * 1.4f),
                                )
                                drawLine(
                                    color = c.amber.copy(alpha = 0.85f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 2f,
                                )
                            }
                        },
                ) {
                    if (photo != null) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = "Image being upscaled",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(c.soft))
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(RsRadius.sm.dp))
                            .background(c.stage.copy(alpha = 0.72f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (reduceMotion) "AI" else "${job.scale}× · AI",
                            style = RsType.body(11f, FontWeight.Medium),
                            color = c.onDark,
                        )
                    }
                }
            }

            Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(job.progress * 100).toInt()}",
                    style = RsType.display(64f).copy(fontFeatureSettings = "tnum"),
                    color = c.ink,
                )
                Text("%", style = RsType.display(32f).copy(fontFeatureSettings = "tnum"), color = c.ink)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        when {
                            job.status == JobStatus.Failed -> "Stopped"
                            job.status == JobStatus.Done -> "Saved"
                            job.etaSec > 0 -> "About ${job.etaSec}s left"
                            else -> "Waiting for the provider…"
                        },
                        style = RsType.body(15f, FontWeight.Medium).copy(fontFeatureSettings = "tnum"),
                        color = c.ink,
                    )
                    Text(
                        "Elapsed ${job.elapsedSec}s",
                        style = RsType.body(13f).copy(fontFeatureSettings = "tnum"),
                        color = c.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            RsProgressBar(job.progress, "AI upscale progress", Modifier.padding(top = 16.dp))
            Text(
                job.stageLabel,
                style = RsType.body(15f, FontWeight.Medium),
                color = c.ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (job.status == JobStatus.Failed) {
                Column(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.soft)
                        .padding(14.dp),
                ) {
                    Text("AI upscale failed", style = RsType.body(15f, FontWeight.Medium), color = c.ink)
                    Text(
                        job.error ?: "The provider did not return an image.",
                        style = RsType.body(13f, lineHeightRatio = 1.5f),
                        color = c.body,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Row(
                Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.card)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(LucideIcons.Zap, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                Text(tip, style = RsType.body(14f, lineHeightRatio = 1.5f), color = c.body)
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.canvas)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (job.status == JobStatus.Failed) {
                RsButton(
                    onClick = {
                        store.clearAiJob()
                        store.startAiJob(job.photoId)
                    },
                    variant = RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    icon = LucideIcons.RefreshCw,
                    label = "Try again",
                )
                RsButton(
                    onClick = {
                        store.clearAiJob()
                        store.replaceTop(Route.AiKeys)
                    },
                    variant = RsButtonVariant.Ghost,
                    full = true,
                    label = "Check API keys",
                )
            } else {
                RsButton(
                    onClick = {
                        store.toast("You can keep using the app while this finishes")
                        store.pop()
                    },
                    size = RsButtonSize.Lg,
                    full = true,
                    enabled = job.status == JobStatus.Running,
                    label = "Continue in background",
                )
                RsButton(
                    onClick = { confirm = true },
                    variant = RsButtonVariant.Ghost,
                    full = true,
                    enabled = running,
                    label = "Cancel",
                )
            }
        }
    }

    RsConfirmSheet(
        open = confirm,
        onClose = { confirm = false },
        title = "Cancel this AI upscale?",
        body = "The request is already with ${job.providerName}. Cancelling stops the download and saves nothing.",
        confirmLabel = "Cancel upscale",
        cancelLabel = "Keep going",
        danger = true,
        onConfirm = {
            confirm = false
            store.haptic(HapticKind.Warning)
            store.cancelAiJob()
        },
    )
}
