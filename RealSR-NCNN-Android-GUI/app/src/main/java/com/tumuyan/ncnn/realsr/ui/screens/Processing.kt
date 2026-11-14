package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.plan.fmtClock
import com.tumuyan.ncnn.realsr.core.plan.fmtLeft
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
import kotlinx.coroutines.delay

private val TIPS = listOf(
    "4× works best on photos under 1000 px.",
    "Your original photo is never changed.",
    "Anime and illustrations get their own model automatically.",
    "Results are saved to Pictures/RealScale-Pro.",
    "Anime4K is the fastest choice for anime and illustrations.",
    "Everything runs on this device. No upload, no account.",
)

/**
 * Tile fill: the real output grid, painted tile by tile as the engine reports progress. Cells come
 * from the plan (cols × rows of the actual tile size), never from a decorative animation.
 */
@Composable
fun TileFill(
    photoUri: String,
    cols: Int,
    rows: Int,
    done: Int,
    active: Int,
    modifier: Modifier = Modifier,
) {
    val c = LocalRsColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(RsRadius.sm.dp))
            .background(c.darkElevated),
    ) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.35f,
        )
        Column(Modifier.fillMaxSize()) {
            repeat(rows) { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(cols) { col ->
                        val index = row * cols + col
                        val filled = index < done
                        val isActive = index == active
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(0.5.dp)
                                .background(
                                    when {
                                        filled -> c.primary.copy(alpha = 0.85f)
                                        isActive -> c.primary.copy(alpha = 0.45f)
                                        else -> c.darkSoft.copy(alpha = 0.25f)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessingScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.state.value
    val job = state.job
    var confirm by remember { mutableStateOf(false) }
    var tip by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            tip = (tip + 1) % TIPS.size
        }
    }

    if (job == null) {
        LaunchedEffect(Unit) { store.home() }
        return
    }
    val photo = store.photoById(job.photoId)
    if (photo == null) {
        LaunchedEffect(Unit) { store.home() }
        return
    }

    // Hand over to the result as soon as the job is saved.
    LaunchedEffect(job.status, job.resultId) {
        if (job.status == JobStatus.Done) {
            val id = job.resultId
            if (id != null) {
                store.acknowledgeJob()
                store.clearJob()
                store.replaceTop(Route.Result(id))
            } else {
                store.clearJob()
                store.home()
            }
        }
    }

    val plan = job.plan
    val p = job.progress
    val stage = when {
        p < 0.06f -> "Preparing image"
        p < 0.94f -> "Enhancing details"
        else -> "Finishing and saving"
    }
    val tileProgress = ((p - 0.06f) / (0.94f - 0.06f)).coerceIn(0f, 1f)
    val tileNow = minOf(plan.tiles, (tileProgress * plan.tiles).toInt() + 1)
    val cols = plan.cols.coerceAtMost(12)
    val rows = plan.rows.coerceAtMost(12)
    val cells = cols * rows
    val doneCells = (tileProgress * cells).toInt()
    val active = if (tileProgress < 1f) doneCells else -1
    val left = plan.etaSec * (1 - p).toDouble()
    val throttled = plan.etaSec > 90 && p > 0.5f && p < 0.7f
    val aspect = (photo.width.toFloat() / photo.height).coerceIn(0.4f, 2.5f)

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(title = "Upscaling")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.dark)
                    .padding(12.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TileFill(
                        photoUri = photo.uri,
                        cols = cols,
                        rows = rows,
                        done = doneCells,
                        active = active,
                        modifier = Modifier
                            .width(280.dp)
                            .aspectRatio(aspect),
                    )
                }
                Row(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Tile $tileNow of ${plan.tiles}",
                        style = RsType.mono(12f).copy(fontFeatureSettings = "tnum"),
                        color = c.onDark,
                    )
                    Text(
                        "${if (plan.tilePx > 0) "${plan.tilePx} px" else "no tiling"} · ${plan.compute} · ${plan.scale}×",
                        style = RsType.mono(12f).copy(fontFeatureSettings = "tnum"),
                        color = c.onDarkSoft,
                    )
                }
            }

            Row(Modifier.padding(top = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${(p * 100).toInt()}",
                    style = RsType.display(68f).copy(fontFeatureSettings = "tnum"),
                    color = c.ink,
                )
                Text("%", style = RsType.display(36f).copy(fontFeatureSettings = "tnum"), color = c.ink)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        if (p < 0.04f) "Estimating…" else "About ${fmtLeft(left)} left",
                        style = RsType.body(15f, FontWeight.Medium).copy(fontFeatureSettings = "tnum"),
                        color = c.ink,
                    )
                    Text(
                        "Elapsed ${fmtClock(plan.etaSec * p.toDouble())}",
                        style = RsType.body(13f).copy(fontFeatureSettings = "tnum"),
                        color = c.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            RsProgressBar(p, "Upscale progress", Modifier.padding(top = 16.dp))
            Text(stage, style = RsType.body(15f, FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 12.dp))

            if (throttled) {
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.card)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(LucideIcons.Thermometer, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Text("Slowing down to keep your phone cool.", style = RsType.body(14f, lineHeightRatio = 1.35f), color = c.body)
                }
            }

            Column(Modifier.padding(top = 24.dp)) {
                com.tumuyan.ncnn.realsr.design.EyebrowLabel("Tip")
                Text(TIPS[tip], style = RsType.body(14f, lineHeightRatio = 1.6f), color = c.body, modifier = Modifier.padding(top = 6.dp))
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
            RsButton(
                onClick = {
                    store.setJobBackground(true)
                    store.home()
                },
                size = RsButtonSize.Lg,
                full = true,
                label = "Continue in background",
            )
            RsButton(onClick = { confirm = true }, variant = RsButtonVariant.Ghost, full = true, label = "Cancel")
        }
    }

    RsConfirmSheet(
        open = confirm,
        onClose = { confirm = false },
        title = "Cancel this upscale?",
        body = "${(p * 100).toInt()}% is done. Progress will be lost and you will need to start again.",
        confirmLabel = "Cancel upscale",
        cancelLabel = "Keep going",
        danger = true,
        onConfirm = {
            confirm = false
            store.cancelJob()
            store.clearJob()
            store.replaceTop(Route.Config(photo.id))
            store.toast("Upscale cancelled. Temporary files removed.")
        },
    )
}
