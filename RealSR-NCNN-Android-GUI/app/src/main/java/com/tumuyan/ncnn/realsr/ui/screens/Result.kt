package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.fmtDims
import com.tumuyan.ncnn.realsr.core.plan.fmtEta
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsConfirmSheet
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsIconButton
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsRow
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem
import com.tumuyan.ncnn.realsr.ui.state.ResultItem
import com.tumuyan.ncnn.realsr.ui.state.Route

/**
 * Before/after slider. The "before" side is the real source file, the "after" side is the real
 * saved output — the prototype fakes this with a blurred stand-in.
 */
@Composable
fun BeforeAfter(
    beforeUri: String,
    afterUri: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    initial: Float = 50f,
    showOriginal: Boolean = false,
    sweep: Boolean = true,
) {
    val c = LocalRsColors.current
    var pos by remember { mutableFloatStateOf(if (sweep) 94f else initial) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (sweep) {
            kotlinx.coroutines.delay(380)
            pos = initial
        }
    }
    val shown = if (showOriginal) 100f else pos
    Box(
        modifier
            .clip(RoundedCornerShape(RsRadius.lg.dp))
            .background(c.card)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    pos = (pos + dragAmount / size.width * 100f).coerceIn(0f, 100f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    pos = (offset.x / size.width * 100f).coerceIn(0f, 100f)
                }
            },
    ) {
        AsyncImage(
            model = afterUri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val content = this
                    clipRect(right = size.width * (shown / 100f).coerceIn(0f, 1f)) { content.drawContent() }
                },
        ) {
            AsyncImage(
                model = beforeUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!showOriginal) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val x = size.width * (shown / 100f).coerceIn(0f, 1f)
                drawLine(
                    color = androidx.compose.ui.graphics.Color.White,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { LabelPill("Before") }
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { LabelPill("After") }
        }
    }
}

@Composable
private fun LabelPill(text: String) {
    val c = LocalRsColors.current
    Text(
        text,
        style = RsType.body(12f, FontWeight.Medium),
        color = c.onDark,
        modifier = Modifier
            .clip(RoundedCornerShape(RsRadius.full.dp))
            .background(c.stage.copy(alpha = 0.75f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun ResultScreen(store: AppStore, resultId: String) {
    val c = LocalRsColors.current
    val state = store.state.value
    val result = state.results.firstOrNull { it.id == resultId }
    var hold by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    if (result == null) {
        Column(Modifier.fillMaxSize().background(c.canvas)) {
            RsTopBar(title = "Result", onBack = { store.pop() })
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("This result is no longer available.", style = RsType.display(28f), color = c.ink)
                Text("It may have been deleted.", style = RsType.body(14f), color = c.muted, modifier = Modifier.padding(top = 8.dp))
                RsButton(onClick = { store.home() }, modifier = Modifier.padding(top = 20.dp), label = "Back to Home")
            }
        }
        return
    }

    val photo = store.photoById(result.photoId) ?: store.state.value.photos.firstOrNull { it.uri == result.sourceUri }
    val aspect = if (photo != null) (photo.width.toFloat() / photo.height).coerceIn(0.4f, 2.5f) else 1f

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(
            title = "Result",
            onBack = { store.home() },
            close = true,
            scrolled = scroll.value > 8,
            right = {
                RsIconButton(icon = LucideIcons.Ellipsis, label = "More options", onClick = { sheet = "more" })
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            BeforeAfter(
                beforeUri = photo?.uri ?: result.sourceUri,
                afterUri = "file://${result.outputPath}",
                contentDescription = "Upscaled ${result.fileName}",
                showOriginal = hold,
                modifier = Modifier.fillMaxWidth(0.92f).aspectRatio(aspect),
            )

            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.card)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    hold = true
                                    tryAwaitRelease()
                                    hold = false
                                },
                            )
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(LucideIcons.Eye, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                    Text("Hold to see original", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
                }
                Spacer(Modifier.weight(1f))
                RsIconButton(
                    icon = LucideIcons.Expand,
                    label = "Open full screen",
                    onClick = {
                        val id = photo?.id
                        if (id != null) store.push(Route.Viewer(id, single = true))
                    },
                )
            }

            Column(Modifier.padding(top = 28.dp)) {
                EyebrowLabel("Upscaled ${result.scale}× in ${fmtEta(result.timeSec)}")
                Text(
                    fmtDims(result.outW, result.outH),
                    style = RsType.display(42f).copy(fontFeatureSettings = "tnum"),
                    color = c.ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "${fmtMB(result.sizeMB)} · up from ${photo?.let { fmtDims(it.width, it.height) } ?: "the original"}",
                    style = RsType.body(15f),
                    color = c.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                RsBadge(
                    "Saved to Pictures/RealScale-Pro",
                    tone = RsBadgeTone.Neutral,
                    icon = LucideIcons.Check,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Column(Modifier.padding(top = 20.dp)) {
                com.tumuyan.ncnn.realsr.design.HairlineDivider()
                DetailRow("Model", result.modelName)
                DetailRow("Mode", if (result.mode == ModeId.SUPER) "Super Resolution" else "Upscale Mode")
                DetailRow("Format", result.format)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.canvas)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RsButton(
                onClick = { store.shareResult(result) },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                icon = LucideIcons.Share2,
                label = "Share",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RsButton(
                    onClick = {
                        val id = photo?.id
                        if (id != null) {
                            store.setMode(result.mode)
                            store.replaceTop(Route.Config(id))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = LucideIcons.RotateCcw,
                    label = "Upscale again",
                )
                RsButton(
                    onClick = { store.home() },
                    modifier = Modifier.weight(1f),
                    icon = LucideIcons.ImagePlus,
                    label = "New image",
                )
            }
        }
    }

    RsSheet(open = sheet == "more", onClose = { sheet = null }, title = "More") {
        RsGroup(Modifier.padding(bottom = 16.dp)) {
            RsRow(
                title = "Open in My Upscales",
                icon = LucideIcons.Images,
                chevron = true,
                topDivider = false,
                onClick = {
                    sheet = null
                    store.replaceTop(Route.Upscales)
                },
            )
            RsRow(title = "Details", icon = LucideIcons.FileText, chevron = true, onClick = { sheet = "details" })
            RsRow(
                title = "Delete result",
                icon = LucideIcons.Trash2,
                onClick = { sheet = "delete" },
            )
        }
    }

    RsSheet(open = sheet == "details", onClose = { sheet = null }, title = "Details") {
        DetailRow("File", result.fileName, mono = true)
        DetailRow("Saved to", "Pictures/RealScale-Pro")
        DetailRow("Resolution", fmtDims(result.outW, result.outH))
        DetailRow("File size", fmtMB(result.sizeMB))
        DetailRow("Scale", "${result.scale}×")
        DetailRow("Model", result.modelName)
        DetailRow("Time taken", fmtEta(result.timeSec))
        DetailRow("Original", "${photo?.name ?: ""} · ${photo?.let { fmtDims(it.width, it.height) } ?: ""}")
        Spacer(Modifier.size(8.dp))
    }

    RsConfirmSheet(
        open = sheet == "delete",
        onClose = { sheet = null },
        title = "Delete this result?",
        body = "The upscaled file is removed from Pictures/RealScale-Pro. Your original photo is not affected.",
        confirmLabel = "Delete result",
        danger = true,
        onConfirm = {
            sheet = null
            store.deleteResults(setOf(result.id))
            store.pop()
            store.toast("Result deleted")
        },
    )
}
