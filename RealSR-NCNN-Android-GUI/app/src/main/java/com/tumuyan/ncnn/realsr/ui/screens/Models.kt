package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.ModelCatalog
import com.tumuyan.ncnn.realsr.core.plan.ModelEntry
import com.tumuyan.ncnn.realsr.core.plan.PipelinePlanner
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.Dot
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsChip
import com.tumuyan.ncnn.realsr.design.RsConfirmSheet
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsIconButton
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsRing
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem
import com.tumuyan.ncnn.realsr.ui.state.Route
import com.tumuyan.ncnn.realsr.ui.state.rememberState
import kotlinx.coroutines.delay

@Composable
fun ModelsScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    val device = store.device
    var filter by remember { mutableStateOf("all") }
    var pendingDelete by remember { mutableStateOf<ModelEntry?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    val installedModels = ModelCatalog.MODELS.filter { it.id in state.installed }
    val usedMB = installedModels.sumOf { it.sizeMB }
    val freeGB = (device.freeGB - usedMB / 1000.0).coerceAtLeast(0.0)
    val otherPct = ((device.totalGB - device.freeGB) / device.totalGB.toDouble() * 100).coerceIn(0.0, 100.0)
    val modelsPct = maxOf(1.2, usedMB / 1000.0 / device.totalGB * 100)

    LaunchedEffect(checking) {
        if (checking) {
            delay(1200)
            checking = false
            store.toast("All models are up to date")
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(title = "Model Manager", onBack = { store.pop() }, scrolled = scroll.value > 8)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            /* Storage */
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.card)
                    .padding(20.dp),
            ) {
                EyebrowLabel("Storage", color = c.body)
                Text(
                    fmtMB(usedMB),
                    style = RsType.display(36f).copy(fontFeatureSettings = "tnum"),
                    color = c.ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "used by ${state.installed.size} models · ${"%.1f".format(freeGB)} GB free of ${device.totalGB} GB",
                    style = RsType.body(14f),
                    color = c.body,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        Modifier
                            .weight(modelsPct.toFloat().coerceAtLeast(1f))
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(c.ink),
                    )
                    Box(
                        Modifier
                            .weight(otherPct.toFloat().coerceAtLeast(1f))
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(c.mutedSoft),
                    )
                    Box(
                        Modifier
                            .weight(100f)
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(c.canvas),
                    )
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot("Models", c.ink)
                    LegendDot("Other", c.mutedSoft)
                    LegendRing("Free")
                }
            }

            Row(
                Modifier
                    .padding(top = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("all", "photo", "anime").forEach { value ->
                    RsChip(
                        text = value.replaceFirstChar { it.uppercase() },
                        selected = filter == value,
                        onClick = { filter = value },
                    )
                }
            }

            ModelCatalog.PACKS.forEach { pack ->
                val models = ModelCatalog.MODELS.filter { model ->
                    model.pack == pack.id && when (filter) {
                        "photo" -> model.kind == ImageType.Photo
                        "anime" -> model.kind == ImageType.Anime
                        else -> true
                    }
                }
                if (models.isEmpty()) return@forEach
                val pending = models.filter {
                    it.id !in state.installed && state.downloads[it.id] == null && ModelCatalog.fit(it, device.tier).tone != "no"
                }
                Column(Modifier.padding(top = 32.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.name, style = RsType.display(26f), color = c.ink)
                            Text(pack.note, style = RsType.body(13f, lineHeightRatio = 1.35f), color = c.muted, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (pending.size > 1) {
                            Column(
                                Modifier
                                    .clickable { pending.forEach { store.downloadModel(it.id) } }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text("Download all", style = RsType.body(14f, FontWeight.Medium, 1.2f), color = c.link)
                                Text(fmtMB(pending.sumOf { it.sizeMB }), style = RsType.body(12f), color = c.muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    RsGroup {
                        models.forEachIndexed { index, model ->
                            ModelRow(
                                store = store,
                                model = model,
                                variant = "manage",
                                topDivider = index > 0,
                                onDelete = { pendingDelete = model },
                            )
                        }
                    }
                }
            }

            RsButton(
                onClick = { checking = true },
                modifier = Modifier.padding(top = 32.dp),
                full = true,
                enabled = !checking,
                icon = LucideIcons.RefreshCw,
                label = if (checking) "Checking…" else "Check for updates",
            )
        }
    }

    val deleting = pendingDelete
    RsConfirmSheet(
        open = deleting != null,
        onClose = { pendingDelete = null },
        title = "Delete ${deleting?.name ?: "model"}?",
        body = "This frees ${deleting?.let { fmtMB(it.sizeMB) } ?: ""}. You can download it again at any time. Settings that use it switch to the automatic choice.",
        confirmLabel = "Delete model",
        danger = true,
        onConfirm = {
            deleting?.let { model ->
                store.deleteModel(model.id)
                store.updatePrefs(ModeId.UPSCALE) { if (it.modelId == model.id) it.copy(modelId = "auto") else it }
                store.updatePrefs(ModeId.SUPER) { if (it.modelId == model.id) it.copy(modelId = "auto") else it }
            }
            pendingDelete = null
        },
    )
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    val c = LocalRsColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = RsType.body(12f), color = c.body)
    }
}

@Composable
private fun LegendRing(label: String) {
    val c = LocalRsColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(c.canvas)
                .border(1.dp, c.mutedSoft, CircleShape),
        )
        Text(label, style = RsType.body(12f), color = c.body)
    }
}

@Composable
fun ModelRow(
    store: AppStore,
    model: ModelEntry,
    variant: String,
    recommended: Boolean = false,
    selected: Boolean = false,
    topDivider: Boolean = true,
    onSelect: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    val installed = model.id in state.installed
    val download = state.downloads[model.id]
    val fit = ModelCatalog.fit(model, store.device.tier)
    val unavailable = fit.tone == "no"

    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (variant == "pick" && installed && download == null && onSelect != null) {
                Modifier.background(if (selected) c.soft else c.canvas).clickable { onSelect() }
            } else {
                Modifier
            },
        )
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Column {
        if (topDivider) com.tumuyan.ncnn.realsr.design.HairlineSoftDivider()
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        model.name,
                        style = RsType.body(15f, FontWeight.Medium, 1.3f),
                        color = if (unavailable) c.muted else c.ink,
                    )
                    if (recommended) RsBadge("Recommended", tone = RsBadgeTone.Amber)
                }
                Text(model.desc, style = RsType.body(13f, lineHeightRatio = 1.3f), color = c.body, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(fmtMB(model.sizeMB), style = RsType.mono(12f), color = c.muted)
                    Text(model.badge, style = RsType.body(12f), color = c.muted)
                    if (variant == "manage" && model.license != "To be verified") {
                        Text(model.license, style = RsType.body(12f), color = c.muted)
                    }
                }
                when {
                    model.blocked != null -> Text(
                        model.blocked,
                        style = RsType.body(12f, lineHeightRatio = 1.3f),
                        color = c.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    variant == "manage" && installed -> Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(LucideIcons.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(14.dp))
                        Text("Installed", style = RsType.body(12f, FontWeight.Medium), color = c.ink)
                    }
                    else -> Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Dot(
                            when (fit.tone) {
                                "good" -> com.tumuyan.ncnn.realsr.design.RsDotTone.Good
                                "ok" -> com.tumuyan.ncnn.realsr.design.RsDotTone.Ok
                                else -> com.tumuyan.ncnn.realsr.design.RsDotTone.No
                            },
                        )
                        Text(fit.label, style = RsType.body(12f), color = c.muted)
                    }
                }
            }
            Box(Modifier.width(108.dp), contentAlignment = Alignment.TopEnd) {
                when {
                    /*
                     * Every state gets its own always-visible affordance. The old trailing column
                     * was 96 dp wide and held a full text button ("Download 291 MB"), so the icon
                     * and part of the label were clipped away on narrow rows — which is why the
                     * download icon seemed to be missing and nothing could be tapped.
                     */
                    download != null && download.state == "failed" -> Column(horizontalAlignment = Alignment.End) {
                        RsIconButton(
                            icon = LucideIcons.RefreshCw,
                            label = "Retry ${model.name}",
                            onClick = { store.downloadModel(model.id) },
                        )
                        Text(
                            "Retry",
                            style = RsType.body(11f),
                            color = c.link,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    download != null -> Column(horizontalAlignment = Alignment.End) {
                        Row(
                            Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(RsRadius.md.dp))
                                .clickable { store.cancelDownload(model.id) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                when (download.state) {
                                    "queued" -> "Queued"
                                    "paused" -> "Paused"
                                    else -> "${(download.progress * 100).toInt()}%"
                                },
                                style = RsType.mono(12f),
                                color = c.muted,
                            )
                            RsRing(download.progress, "Downloading ${model.name}")
                        }
                        Text(
                            "Tap to cancel",
                            style = RsType.body(11f),
                            color = c.muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    installed && variant == "pick" -> com.tumuyan.ncnn.realsr.design.RadioMark(checked = selected)
                    installed && model.required -> RsBadge("Required", tone = RsBadgeTone.Outline)
                    installed && onDelete != null -> Column(horizontalAlignment = Alignment.End) {
                        RsIconButton(
                            icon = LucideIcons.Trash2,
                            label = "Delete ${model.name}",
                            onClick = onDelete,
                        )
                        Text(
                            "Delete",
                            style = RsType.body(11f),
                            color = c.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    installed -> RsBadge("Installed", tone = RsBadgeTone.Outline)
                    unavailable -> Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            LucideIcons.Lock,
                            contentDescription = "Not available on this device",
                            tint = c.muted,
                            modifier = Modifier.padding(top = 4.dp).size(20.dp),
                        )
                        Text(
                            "Too heavy",
                            style = RsType.body(11f),
                            color = c.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    else -> Column(horizontalAlignment = Alignment.End) {
                        RsIconButton(
                            icon = LucideIcons.Download,
                            label = "Download ${model.name}",
                            onClick = { store.downloadModel(model.id) },
                        )
                        Text(
                            fmtMB(model.sizeMB),
                            style = RsType.mono(11f),
                            color = c.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // The reason a download stopped, right under the row, instead of a toast that scrolls away.
        val failure = download?.takeIf { it.state == "failed" }?.message
        if (!failure.isNullOrBlank()) {
            Text(
                failure,
                style = RsType.body(12f, lineHeightRatio = 1.35f),
                color = c.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

/* ---------- Model picker ---------- */

@Composable
fun ModelPickerSheet(
    store: AppStore,
    open: Boolean,
    mode: ModeId,
    type: ImageType,
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    var filter by remember { mutableStateOf("all") }
    var pending by remember { mutableStateOf<String?>(null) }
    val recommended = PipelinePlanner.recommendedId(mode, type)

    LaunchedEffect(pending, state.installed) {
        val id = pending
        if (id != null && id in state.installed) {
            onSelect(id)
            pending = null
        }
    }

    fun rank(model: ModelEntry): Int = when {
        model.id == recommended -> 0
        model.id in state.installed -> 1
        ModelCatalog.fit(model, store.device.tier).tone == "no" -> 3
        else -> 2
    }

    val list = ModelCatalog.MODELS
        .filter { model ->
            when (filter) {
                "installed" -> model.id in state.installed
                "photo" -> model.kind == ImageType.Photo
                "anime" -> model.kind == ImageType.Anime
                else -> true
            }
        }
        .sortedBy { rank(it) }

    RsSheet(open = open, onClose = onClose, title = "Choose a model", tall = true) {
        Text(
            "Every model in the catalog for ${if (mode == ModeId.SUPER) "Super Resolution Mode" else "Upscale Mode"}. Download any of them here and it becomes your selection.",
            style = RsType.body(14f, lineHeightRatio = 1.55f),
            color = c.body,
        )
        Row(
            Modifier
                .padding(top = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("all", "photo", "anime", "installed").forEach { value ->
                RsChip(
                    text = value.replaceFirstChar { it.uppercase() },
                    selected = filter == value,
                    onClick = { filter = value },
                )
            }
        }
        Column(Modifier.padding(top = 16.dp, bottom = 16.dp).fillMaxWidth()) {
            RsGroup {
                if (filter == "all") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (selectedId == "auto") c.soft else c.canvas)
                            .clickable {
                                onSelect("auto")
                                onClose()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic", style = RsType.body(15f, FontWeight.Medium), color = c.ink)
                            Text(
                                "Picks the best installed model for each image and your device.",
                                style = RsType.body(13f, lineHeightRatio = 1.3f),
                                color = c.body,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        com.tumuyan.ncnn.realsr.design.RadioMark(checked = selectedId == "auto")
                    }
                }
                list.filter { it.id != "anime4k-x2" || true }.forEachIndexed { index, model ->
                    Box(
                        Modifier.clickable(enabled = model.id !in state.installed && ModelCatalog.fit(model, store.device.tier).tone != "no") {
                            pending = model.id
                            store.downloadModel(model.id)
                        },
                    ) {
                        ModelRow(
                            store = store,
                            model = model,
                            variant = "pick",
                            recommended = model.id == recommended,
                            selected = selectedId == model.id,
                            topDivider = index > 0 || filter == "all",
                            onSelect = {
                                onSelect(model.id)
                                onClose()
                            },
                        )
                    }
                }
                if (list.isEmpty()) {
                    Text(
                        "No models match this filter.",
                        style = RsType.body(14f),
                        color = c.muted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun PhotosEmptyHint(store: AppStore) {
    val c = LocalRsColors.current
    Text(
        "No photos yet. Import one from Files, Camera or the clipboard.",
        style = RsType.body(14f, lineHeightRatio = 1.5f),
        color = c.muted,
    )
}

@Composable
fun PhotoStrip(store: AppStore, photos: List<PhotoItem>) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        photos.forEach { photo ->
            Box(
                Modifier
                    .width(120.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(LocalRsColors.current.card)
                    .clickable { store.openViewer(photo) },
            ) {
                coil3.compose.AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
