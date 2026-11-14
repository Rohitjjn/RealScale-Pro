package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.core.plan.JobPrefs
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.ModelCatalog
import com.tumuyan.ncnn.realsr.core.plan.OutputFormatPref
import com.tumuyan.ncnn.realsr.core.plan.Plan
import com.tumuyan.ncnn.realsr.core.plan.PlanCheck
import com.tumuyan.ncnn.realsr.core.plan.PlanLevel
import com.tumuyan.ncnn.realsr.core.plan.TILE_PRESETS
import com.tumuyan.ncnn.realsr.core.plan.TilePreset
import com.tumuyan.ncnn.realsr.core.plan.fmtDims
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsChip
import com.tumuyan.ncnn.realsr.design.RsField
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsSegmented
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsSlider
import com.tumuyan.ncnn.realsr.design.RsStepper
import com.tumuyan.ncnn.realsr.design.RsSwitchRow
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.Skeleton
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem
import com.tumuyan.ncnn.realsr.ui.state.Route
import kotlinx.coroutines.delay

@Composable
fun AdvancedSections(
    store: AppStore,
    photo: PhotoItem,
    plan: Plan,
    prefs: JobPrefs,
    mode: ModeId,
    large: String,
    onLarge: (String) -> Unit,
    onCrop: () -> Unit,
    setPrefs: ((JobPrefs) -> JobPrefs) -> Unit,
) {
    val c = LocalRsColors.current
    val device = store.device
    val isSuper = mode == ModeId.SUPER
    val lossy = plan.format != "PNG"

    Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Performance", style = RsType.display(26f), color = c.ink)

        RsField(
            label = "Tile size",
            hint = "Fast is recommended for your device. Maximum needs a Powerful device with 12 GB or more memory.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TILE_PRESETS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            RsChip(
                                text = preset.label,
                                sub = if (preset.px > 0) "${preset.px} px · ${preset.mem}" else preset.mem,
                                selected = prefs.tile == preset.id,
                                enabled = preset.disabledReason == null,
                                modifier = Modifier.weight(1f),
                                onClick = { setPrefs { it.copy(tile = preset.id) } },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                RsChip(
                    text = "Custom",
                    sub = "64 to 1024 px, in steps of 32",
                    selected = prefs.tile == TilePreset.CUSTOM,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { setPrefs { it.copy(tile = TilePreset.CUSTOM) } },
                )
            }
            if (prefs.tile == TilePreset.CUSTOM) {
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.card)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tile size", style = RsType.body(14f), color = c.ink)
                        Spacer(Modifier.weight(1f))
                        RsStepper(
                            value = prefs.tileCustom,
                            min = 64,
                            max = 1024,
                            step = 32,
                            label = "tile size",
                            onChange = { setPrefs { prefs2 -> prefs2.copy(tileCustom = it) } },
                        )
                    }
                    Column {
                        Text("Overlap", style = RsType.body(14f), color = c.ink, modifier = Modifier.padding(bottom = 8.dp))
                        RsSegmented(
                            label = "Tile overlap",
                            value = prefs.overlap,
                            options = listOf("auto" to "Auto", "16" to "16", "32" to "32", "64" to "64"),
                            onChange = { setPrefs { prefs2 -> prefs2.copy(overlap = it) } },
                        )
                    }
                }
            }
        }

        RsField(
            label = "Compute device",
            hint = "Detected ${device.gpu} with Vulkan ${device.vulkan}. Auto follows the preference in Settings (now ${plan.compute}).",
        ) {
            RsSegmented(
                label = "Compute device",
                value = prefs.compute,
                options = listOf("auto" to "Auto", "gpu" to "GPU", "cpu" to "CPU"),
                onChange = { setPrefs { prefs2 -> prefs2.copy(compute = it) } },
            )
            if (prefs.compute == "cpu") {
                Row(
                    Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("CPU threads", style = RsType.body(14f), color = c.ink)
                    Spacer(Modifier.weight(1f))
                    RsStepper(
                        value = prefs.threads,
                        min = 0,
                        max = device.cores,
                        step = 1,
                        label = "CPU threads",
                        onChange = { setPrefs { prefs2 -> prefs2.copy(threads = it) } },
                    )
                }
            }
        }

        RsField(label = "Numeric precision", hint = "FP16 is faster and lighter. FP32 is more exact.") {
            RsSegmented(
                label = "Numeric precision",
                value = prefs.precision,
                options = listOf("auto" to "Auto", "fp16" to "FP16", "fp32" to "FP32"),
                onChange = { setPrefs { prefs2 -> prefs2.copy(precision = it) } },
            )
        }

        RsGroup {
            RsSwitchRow(
                title = "Enhanced quality (TTA)",
                sub = "Runs the image several ways and averages. Up to 8× slower.",
                checked = prefs.tta,
                onChange = { setPrefs { prefs2 -> prefs2.copy(tta = it) } },
            )
        }

        Text("Input and output", style = RsType.display(26f), color = c.ink)

        if (!isSuper) {
            RsField(label = "Image compression", hint = "Auto re-encodes files over 2 MB at 90% quality into a temporary copy.") {
                RsSegmented(
                    label = "Image compression",
                    value = prefs.compression,
                    options = listOf("auto" to "Auto", "off" to "Off", "custom" to "Custom"),
                    onChange = { setPrefs { prefs2 -> prefs2.copy(compression = it) } },
                )
                if (prefs.compression == "custom") {
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("Quality", style = RsType.body(14f), color = c.ink)
                        RsSlider(
                            value = prefs.compressionQ,
                            min = 60,
                            max = 100,
                            label = "Compression quality",
                            suffix = "%",
                            onChange = { setPrefs { prefs2 -> prefs2.copy(compressionQ = it) } },
                        )
                    }
                }
            }
        }

        RsField(
            label = "Output format",
            hint = if (isSuper) "PNG is lossless and the default in this mode." else "Source keeps ${if (photo.ext == "png") "PNG" else "JPEG"} for this image.",
        ) {
            val options = if (isSuper) {
                listOf(
                    OutputFormatPref.DEFAULT to "PNG",
                    OutputFormatPref.JPEG to "JPEG",
                    OutputFormatPref.WEBP to "WebP",
                )
            } else {
                listOf(
                    OutputFormatPref.DEFAULT to "Source",
                    OutputFormatPref.JPEG to "JPEG",
                    OutputFormatPref.PNG to "PNG",
                    OutputFormatPref.WEBP to "WebP",
                )
            }
            RsSegmented(
                label = "Output format",
                value = if (prefs.format == OutputFormatPref.PNG && isSuper) OutputFormatPref.DEFAULT else prefs.format,
                options = options,
                onChange = { setPrefs { prefs2 -> prefs2.copy(format = it) } },
            )
            if (lossy) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text("${plan.format} quality", style = RsType.body(14f), color = c.ink)
                    RsSlider(
                        value = prefs.formatQ,
                        min = 60,
                        max = 100,
                        label = "Output quality",
                        suffix = "%",
                        onChange = { setPrefs { prefs2 -> prefs2.copy(formatQ = it) } },
                    )
                }
            }
        }

        if (plan.model.denoise) {
            RsField(label = "Denoise strength", hint = "Supported by ${plan.model.name}.") {
                RsSlider(
                    value = prefs.denoise,
                    min = 0,
                    max = 100,
                    label = "Denoise strength",
                    onChange = { setPrefs { prefs2 -> prefs2.copy(denoise = it) } },
                )
            }
        }

        RsGroup {
            RsSwitchRow(
                title = "Keep photo metadata",
                sub = "Date, camera and location stay in the result.",
                checked = prefs.keepMeta,
                onChange = { setPrefs { prefs2 -> prefs2.copy(keepMeta = it) } },
            )
        }

        if (isSuper) {
            Text("Finishing", style = RsType.display(26f), color = c.ink)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
                    .clickable { onCrop() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(LucideIcons.Crop, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Preview crop", style = RsType.body(15f, FontWeight.Medium), color = c.ink)
                    Text(
                        "Test these settings on a 512 × 512 area in seconds.",
                        style = RsType.body(13f, lineHeightRatio = 1.3f),
                        color = c.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(LucideIcons.ChevronRight, contentDescription = null, tint = c.mutedSoft, modifier = Modifier.size(16.dp))
            }

            RsField(label = "Sharpen after upscale") {
                RsSlider(
                    value = prefs.sharpen,
                    min = 0,
                    max = 100,
                    label = "Sharpen after upscale",
                    onChange = { setPrefs { prefs2 -> prefs2.copy(sharpen = it) } },
                )
            }

            if (plan.scale == 3 || plan.scale == 5) {
                RsField(label = "Resize method for ${plan.scale}×") {
                    RsSegmented(
                        label = "Resize method",
                        value = prefs.resize,
                        options = listOf("lanczos" to "Lanczos", "bicubic" to "Bicubic"),
                        onChange = { setPrefs { prefs2 -> prefs2.copy(resize = it) } },
                    )
                }
            }

            RsField(label = "Color profile") {
                RsSegmented(
                    label = "Color profile",
                    value = prefs.color,
                    options = listOf("keep" to "Keep original", "srgb" to "Convert to sRGB"),
                    onChange = { setPrefs { prefs2 -> prefs2.copy(color = it) } },
                )
            }

            RsField(label = "Output size cap", hint = "Optional limit on the longest side of the result.") {
                RsSegmented(
                    label = "Output size cap",
                    value = prefs.cap,
                    options = listOf("none" to "None", "8k" to "8K", "16k" to "16K"),
                    onChange = { setPrefs { prefs2 -> prefs2.copy(cap = it) } },
                )
            }
        }

        RsButton(
            onClick = {
                store.resetPrefs(mode)
            },
            full = true,
            icon = LucideIcons.RotateCcw,
            label = "Reset to defaults",
        )
    }
}

/* ---------- Feasibility check ---------- */

@Composable
fun CheckSheet(
    store: AppStore,
    plan: Plan,
    open: Boolean,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onScale: (Int) -> Unit,
    onFaster: () -> Unit,
    onPng: () -> Unit,
    onOther: () -> Unit,
    onOptimize: () -> Unit,
) {
    val c = LocalRsColors.current
    val check = plan.check
    val red = check is PlanCheck.Red
    val amber = check as? PlanCheck.Amber
    RsSheet(
        open = open && check !is PlanCheck.Green,
        onClose = onClose,
        title = when (check) {
            is PlanCheck.Red -> check.title
            is PlanCheck.Amber -> check.title
            else -> ""
        },
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (amber != null && amber.kind == "time") {
                    RsButton(onClick = onContinue, variant = RsButtonVariant.Primary, size = RsButtonSize.Lg, full = true, label = "Continue")
                    RsButton(
                        onClick = { if (plan.scale > 2) onScale(plan.scale - 1) else onFaster() },
                        full = true,
                        label = if (plan.scale > 2) "Lower the scale to ${plan.scale - 1}×" else "Pick a faster model",
                    )
                }
                if (amber != null && amber.kind == "format") {
                    RsButton(onClick = onPng, variant = RsButtonVariant.Primary, size = RsButtonSize.Lg, full = true, label = "Switch to PNG")
                }
                if (check is PlanCheck.Red) {
                    when {
                        check.maxScale != null -> RsButton(
                            onClick = { onScale(check.maxScale) },
                            variant = RsButtonVariant.Primary,
                            size = RsButtonSize.Lg,
                            full = true,
                            label = "Use ${check.maxScale}× instead",
                        )
                        plan.overLimit && !plan.optimized -> RsButton(
                            onClick = onOptimize,
                            variant = RsButtonVariant.Primary,
                            size = RsButtonSize.Lg,
                            full = true,
                            label = "Optimize size instead",
                        )
                        else -> RsButton(onClick = onOther, variant = RsButtonVariant.Primary, size = RsButtonSize.Lg, full = true, label = "Pick another image")
                    }
                    if (plan.level == PlanLevel.ADVANCED) {
                        RsButton(onClick = onContinue, variant = RsButtonVariant.Danger, full = true, label = "Continue anyway")
                    }
                }
                RsButton(onClick = onClose, variant = RsButtonVariant.Ghost, full = true, label = "Cancel")
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            Icon(
                if (red) LucideIcons.CircleAlert else LucideIcons.TriangleAlert,
                contentDescription = null,
                tint = c.ink,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    when (check) {
                        is PlanCheck.Red -> check.body
                        is PlanCheck.Amber -> check.body
                        else -> ""
                    },
                    style = RsType.body(15f, lineHeightRatio = 1.55f),
                    color = c.body,
                )
                if (check is PlanCheck.Red && check.maxScale != null) {
                    Text(
                        "Largest scale that fits: ${check.maxScale}×.",
                        style = RsType.body(15f, FontWeight.Medium),
                        color = c.ink,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (check is PlanCheck.Red && plan.level != PlanLevel.ADVANCED) {
                    Text(
                        "Advanced settings allow continuing anyway.",
                        style = RsType.body(13f),
                        color = c.muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/* ---------- Swap sheet ---------- */

@Composable
fun SwapSheet(store: AppStore, photo: PhotoItem, open: Boolean, onClose: () -> Unit) {
    val state = store.state.value
    RsSheet(open = open, onClose = onClose, title = "Choose another image", tall = true) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(320.dp),
        ) {
            items(state.photos, key = { it.id }) { item ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(RsRadius.sm.dp))
                        .background(LocalRsColors.current.card)
                        .then(
                            if (item.id == photo.id) {
                                Modifier.border(2.dp, LocalRsColors.current.inverse, RoundedCornerShape(RsRadius.sm.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable {
                            onClose()
                            if (item.id != photo.id) store.replaceTop(Route.Config(item.id))
                        },
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = "Use ${item.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        RsButton(
            onClick = {
                onClose()
                store.pickFile()
            },
            full = true,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
            icon = LucideIcons.FolderOpen,
            label = "Browse files",
        )
    }
}

/* ---------- Preview crop ---------- */

@Composable
fun CropSheet(store: AppStore, photo: PhotoItem, plan: Plan, open: Boolean, onClose: () -> Unit) {
    val c = LocalRsColors.current
    var x by remember(photo.id) { mutableIntStateOf(50) }
    var y by remember(photo.id) { mutableIntStateOf(42) }
    var running by remember { mutableStateOf(true) }
    var elapsedMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(open, x, y) {
        if (!open) return@LaunchedEffect
        running = true
        elapsedMs = 0
        val start = System.currentTimeMillis()
        // A real crop-preview job runs the pipeline on a 512x512 area; the sheet shows the wait.
        store.requestCropPreview(photo, plan, x, y)
        delay(400)
        while (System.currentTimeMillis() - start < 1300) {
            delay(100)
            elapsedMs = (System.currentTimeMillis() - start).toInt()
        }
        running = false
    }
    RsSheet(open = open, onClose = onClose, title = "Preview crop", tall = true) {
        Text(
            "Tap the photo to choose a 512 × 512 area. The full pipeline runs on that area only.",
            style = RsType.body(14f, lineHeightRatio = 1.55f),
            color = c.body,
        )
        Box(
            Modifier
                .padding(top = 16.dp)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.8f)
                .aspectRatio((photo.width.toFloat() / photo.height).coerceIn(0.6f, 1.8f))
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .background(c.card)
                .clickable { },
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = "Choose the preview area",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.card),
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text("Original", style = RsType.body(13f, FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 8.dp))
            }
            Column(Modifier.weight(1f)) {
                if (running) {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(RsRadius.md.dp))
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(RsRadius.md.dp))
                            .background(c.card),
                    ) {
                        AsyncImage(
                            model = store.cropPreviewUri ?: photo.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    if (running) "Running preview…" else "Preview",
                    style = RsType.body(13f, FontWeight.Medium),
                    color = c.ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Text(
            "${plan.model.name} · ${if (running) "estimating" else "finished in about ${(elapsedMs / 1000).coerceAtLeast(1)} s"}",
            style = RsType.body(13f, lineHeightRatio = 1.3f),
            color = c.muted,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )
    }
}
