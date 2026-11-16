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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
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
import com.tumuyan.ncnn.realsr.ui.state.rememberState
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
 * Tile fill: the real output grid painted tile by tile as the engine reports progress.
 *
 * The photo starts as a monochrome, dimmed preview — nothing has been enhanced yet. Every finished
 * tile reveals the colour image inside its own cell, so the picture literally develops square by
 * square while the engine works. The active cell gets a thin amber ring and there is a hairline grid
 * so the tile count stays readable on top of a busy photo.
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
    val total = (cols * rows).coerceAtLeast(1)
    val limit = done.coerceIn(0, total)
    Box(
        modifier
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.soft),
    ) {
        // 1. Untouched area: the same photo, desaturated and dim.
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            alpha = 0.45f,
        )
        // 2. Finished tiles: the colour image, clipped to that tile only.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (cols <= 0 || rows <= 0 || limit <= 0) return@drawWithContent
                    val cellW = size.width / cols
                    val cellH = size.height / rows
                    for (index in 0 until limit) {
                        val row = index / cols
                        val col = index % cols
                        clipRect(
                            left = col * cellW,
                            top = row * cellH,
                            right = (col + 1) * cellW,
                            bottom = (row + 1) * cellH,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 3. Grid lines plus the tile being worked on right now.
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            if (cols <= 0 || rows <= 0) return@Canvas
            val cellW = size.width / cols
            val cellH = size.height / rows
            val line = c.onDark.copy(alpha = 0.10f)
            for (col in 1 until cols) {
                drawLine(line, Offset(col * cellW, 0f), Offset(col * cellW, size.height), 1f)
            }
            for (row in 1 until rows) {
                drawLine(line, Offset(0f, row * cellH), Offset(size.width, row * cellH), 1f)
            }
            if (active in 0 until total) {
                val row = active / cols
                val col = active % cols
                drawRect(
                    color = c.amber.copy(alpha = 0.9f),
                    topLeft = Offset(col * cellW + 0.5f, row * cellH + 0.5f),
                    size = androidx.compose.ui.geometry.Size(
                        (cellW - 1f).coerceAtLeast(1f),
                        (cellH - 1f).coerceAtLeast(1f),
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
        }
    }
}

@Composable
fun ProcessingScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    val job = state.job
    var confirm by remember { mutableStateOf(false) }
    var tip by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            tip = (tip + 1) % TIPS.size
        }
    }

    /*
     * Round 5, issue 1 (still reproducible in 2.2.2): this screen used to send the user Home in two
     * places — no job, or a photo that is no longer in the list. Both are transient states while the
     * engine finishes, and "Home" is exactly where the finished upscale must not end up. The job is
     * only missing after the store already rewrote the stack, so the only thing to do here is to
     * follow it to the result; the photo may legitimately be gone (a shared file, a revoked URI)
     * and that must not change where the screen goes.
     */
    if (job == null) {
        val lastId = state.lastResultId
        LaunchedEffect(lastId) {
            val route = store.state.value.route
            if (lastId != null && route !is Route.Result && route !is Route.ResultFull) {
                store.replaceTop(Route.Result(lastId))
            } else if (route is Route.Processing) {
                store.home()
            }
        }
        return
    }
    val photo = store.photoById(job.photoId)
    if (photo == null) {
        // Keep the screen: the job is still running, only the library row is gone.
        LaunchedEffect(job.photoId) { store.refreshPhotos() }
    }

    // Hand over to the result as soon as the job is saved. The store already rewrites the stack, so
    // this only makes sure a job that finished while the screen was off-stack still lands there.
    LaunchedEffect(job.status, job.resultId) {
        if (job.status == JobStatus.Done) {
            /*
             * Round-4, issue 1: the old fallback sent the user Home whenever `resultId` was null,
             * which is exactly the frame in which the engine's Completed and the Room flow cross.
             * The id now comes from the job *or* from the store's last completed creation, and Home
             * is only used when the app genuinely has no result to show.
             */
            val id = job.resultId ?: store.state.value.lastResultId
            store.acknowledgeJob()
            store.clearJob()
            val route = store.state.value.route
            if (id != null) {
                if (route !is Route.Result && route !is Route.ResultFull) store.replaceTop(Route.Result(id))
            } else if (route is Route.Processing) {
                store.toast("Saved · open My Upscales")
                store.home()
            }
        }
    }

    /*
     * "Keep screen awake" (Settings → General). The pref reached the engine but nothing ever set the
     * window flag, so the screen slept mid-job. Scoped to the live screen, released on the way out.
     */
    val keepAwake = state.settings.keepAwake
    val view = LocalView.current
    DisposableEffect(view, keepAwake, job.status) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepAwake && job.status == JobStatus.Running
        onDispose { view.keepScreenOn = previous }
    }

    /*
     * "Slow down when hot or low on battery": the banner used to be a guess based on the estimate.
     * It now reads the same guard the engine uses.
     */
    var throttleReason by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(job.status) {
        while (true) {
            throttleReason = store.throttleReason()
            delay(5_000)
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
    val throttled = throttleReason != null
    val aspect = photo?.let { (it.width.toFloat() / it.height).coerceIn(0.4f, 2.5f) } ?: 1f

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(title = "Upscaling")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            /*
             * The photo stands on its own: no dark card, no border, no padding box around the tile
             * grid. The only thing behind it is the screen background.
             */
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TileFill(
                    photoUri = photo?.uri ?: "",
                    cols = cols,
                    rows = rows,
                    done = doneCells,
                    active = active,
                    modifier = Modifier
                        .width(300.dp)
                        .aspectRatio(aspect),
                )
            }
            Row(
                Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Tile $tileNow of ${plan.tiles}",
                    style = RsType.mono(12f).copy(fontFeatureSettings = "tnum"),
                    color = c.muted,
                )
                Text(
                    "${if (plan.tilePx > 0) "${plan.tilePx} px" else "no tiling"} · ${plan.compute} · ${plan.scale}×",
                    style = RsType.mono(12f).copy(fontFeatureSettings = "tnum"),
                    color = c.muted,
                )
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
                    Text(
                        "${throttleReason ?: "Phone is warm"} · slowing down with smaller tiles so the job can finish.",
                        style = RsType.body(14f, lineHeightRatio = 1.35f),
                        color = c.body,
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
                Text(TIPS[tip], style = RsType.body(14f, lineHeightRatio = 1.5f), color = c.body)
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
            store.replaceTop(photo?.let { Route.Config(it.id) } ?: Route.Home)
            store.toast("Upscale cancelled. Temporary files removed.")
        },
    )
}
