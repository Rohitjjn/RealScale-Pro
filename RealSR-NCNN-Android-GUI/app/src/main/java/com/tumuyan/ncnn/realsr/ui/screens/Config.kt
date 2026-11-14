package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box as BoxLayout
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.device.HapticKind
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
import com.tumuyan.ncnn.realsr.core.plan.fmtEta
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.core.plan.fmtMP
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsChip
import com.tumuyan.ncnn.realsr.design.RsField
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsProgressBar
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsSegmented
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsSlider
import com.tumuyan.ncnn.realsr.design.RsStepper
import com.tumuyan.ncnn.realsr.design.RsSwitchRow
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.Skeleton
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem
import com.tumuyan.ncnn.realsr.ui.state.Route
import kotlinx.coroutines.delay

private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }

@Composable
fun ConfigScreen(store: AppStore, photoId: String) {
    val c = LocalRsColors.current
    val state = store.state.value
    val photo = store.photoById(photoId)
    val mode = state.mode

    if (photo == null) {
        Column(Modifier.fillMaxSize().background(c.canvas)) {
            RsTopBar(title = "Image unavailable", onBack = { store.pop() })
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("This image is no longer available.", style = RsType.display(28f), color = c.ink)
                Text(
                    "It may have been moved or deleted. Pick another one to continue.",
                    style = RsType.body(14f, lineHeightRatio = 1.55f),
                    color = c.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                RsButton(onClick = { store.home() }, modifier = Modifier.padding(top = 20.dp), label = "Back to Home")
            }
        }
        return
    }

    val prefs = state.prefsFor(mode)
    var large by remember(photo.id) { mutableStateOf("optimize") }
    val plan = store.planFor(photo, mode, large)
    val level = prefs.level
    val manual = level != PlanLevel.DEFAULT
    val adv = level == PlanLevel.ADVANCED
    var detecting by remember(photo.id) { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val busy = state.job?.status == com.tumuyan.ncnn.realsr.ui.state.JobStatus.Running
    val blockedReason = when {
        detecting -> "Analyzing photo…"
        busy -> "Another upscale is running"
        else -> null
    }

    LaunchedEffect(photo.id, large, prefs) {
        if (detecting) {
            delay(650)
            detecting = false
        }
    }

    fun setPrefs(block: (JobPrefs) -> JobPrefs) = store.updatePrefs(mode) { block(it) }

    fun start(force: Boolean) {
        if (blockedReason != null) return
        if (!force && plan.check !is PlanCheck.Green) {
            store.haptic(HapticKind.Warning)
            sheet = "check"
            return
        }
        store.haptic(HapticKind.Medium)
        store.startJob(photo, plan, mode)
    }

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize()) {
            RsTopBar(
                title = if (mode == ModeId.SUPER) "Super Resolution Mode" else "Upscale Mode",
                onBack = { store.pop() },
                scrolled = scroll.value > 8,
                right = {
                    com.tumuyan.ncnn.realsr.design.RsIconButton(
                        icon = LucideIcons.Replace,
                        label = "Choose a different image",
                        onClick = { sheet = "swap" },
                    )
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                /* 5.1 preview */
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .aspectRatio((photo.width.toFloat() / photo.height).coerceIn(0.4f, 2.5f))
                        .clip(RoundedCornerShape(RsRadius.lg.dp))
                        .background(c.card)
                        .clickable { store.push(Route.Viewer(photo.id, single = true)) },
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Open ${photo.name} in full view",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c.stage.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LucideIcons.Expand, contentDescription = null, tint = c.onDark, modifier = Modifier.size(16.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(photo.name, style = RsType.mono(13f), color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(
                        "${fmtDims(photo.width, photo.height)} · ${fmtMB(photo.sizeMB)}",
                        style = RsType.body(13f).copy(fontFeatureSettings = "tnum"),
                        color = c.muted,
                    )
                }

                if (mode == ModeId.SUPER) {
                    Row(
                        Modifier
                            .padding(top = 20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RsRadius.md.dp))
                            .background(c.dark)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(LucideIcons.Gauge, contentDescription = null, tint = c.onDark, modifier = Modifier.size(20.dp))
                        Text(
                            "Recommended for high-end devices and advanced users.",
                            style = RsType.body(14f, lineHeightRatio = 1.35f),
                            color = c.onDark,
                            modifier = Modifier.weight(1f),
                        )
                        RsBadge("OK fit", tone = RsBadgeTone.Dark)
                    }
                }

                Box(Modifier.padding(top = 20.dp)) {
                    RsSegmented(
                        label = "Setting level",
                        value = level,
                        options = listOf(
                            PlanLevel.DEFAULT to "Default",
                            PlanLevel.MANUAL to "Manual",
                            PlanLevel.ADVANCED to "Advanced",
                        ),
                        onChange = {
                            store.haptic(HapticKind.Light)
                            setPrefs { prefs2 -> prefs2.copy(level = it) }
                        },
                    )
                }

                if (!manual) DefaultCard(store, photo, plan, detecting) {
                    setPrefs { it.copy(level = PlanLevel.MANUAL) }
                }

                if (manual && !adv) ManualSections(store, photo, plan, prefs, detecting, large, { large = it }, { sheet = "models" }, ::setPrefs)

                if (adv) AdvancedSections(store, photo, plan, prefs, mode, large, { large = it }, { sheet = "crop" }, ::setPrefs)

                Spacer(Modifier.height(24.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.canvas)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                RsButton(
                    onClick = { start(false) },
                    variant = RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    enabled = blockedReason == null,
                    content = { _ ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                blockedReason ?: "Upscale",
                                style = RsType.body(17f, FontWeight.SemiBold, 1.2f),
                            )
                            if (blockedReason == null) {
                                Text(
                                    "${fmtDims(plan.outW, plan.outH)} · about ${fmtEta(plan.etaSec)}",
                                    style = RsType.body(13f, FontWeight.Medium, 1.2f),
                                )
                            }
                        }
                    },
                )
            }
        }

        ModelPickerSheet(
            store = store,
            open = sheet == "models",
            mode = mode,
            type = plan.type,
            selectedId = if (plan.isAuto) "auto" else plan.model.id,
            onSelect = { id ->
                setPrefs { it.copy(modelId = id) }
                sheet = null
            },
            onClose = { sheet = null },
        )

        CheckSheet(
            store = store,
            plan = plan,
            open = sheet == "check",
            onClose = { sheet = null },
            onContinue = {
                sheet = null
                start(true)
            },
            onScale = { n ->
                setPrefs { it.copy(scale = n, level = if (manual) it.level else PlanLevel.MANUAL) }
                sheet = null
            },
            onFaster = {
                if (!manual) setPrefs { it.copy(level = PlanLevel.MANUAL) }
                sheet = "models"
            },
            onPng = {
                setPrefs { it.copy(format = OutputFormatPref.PNG) }
                sheet = null
            },
            onOther = { sheet = "swap" },
            onOptimize = {
                large = "optimize"
                sheet = null
            },
        )

        SwapSheet(store, photo, open = sheet == "swap", onClose = { sheet = null })

        CropSheet(store, photo, plan, open = sheet == "crop", onClose = { sheet = null })
    }
}

@Composable
private fun DefaultCard(store: AppStore, photo: PhotoItem, plan: Plan, detecting: Boolean, onChange: () -> Unit) {
    val c = LocalRsColors.current
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EyebrowLabel(
                if (detecting) "Analyzing" else if (photo.lowConfidence) "Not sure" else "Detected",
                color = c.body,
            )
            Spacer(Modifier.weight(1f))
            if (!detecting) {
                Text(
                    "Change",
                    style = RsType.body(14f, FontWeight.Medium),
                    color = c.link,
                    modifier = Modifier.clip(RoundedCornerShape(RsRadius.md.dp)).clickable { onChange() }.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
        }
        if (detecting) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(Modifier.fillMaxWidth(0.75f).height(28.dp))
                Skeleton(Modifier.fillMaxWidth().height(12.dp))
                Skeleton(Modifier.fillMaxWidth(0.83f).height(12.dp))
                Skeleton(Modifier.fillMaxWidth(0.66f).height(12.dp))
            }
        } else {
            Text(
                if (photo.lowConfidence) "Using ${cap(if (plan.type == ImageType.Photo) "photo" else "anime")} · 2×"
                else "${cap(if (plan.type == ImageType.Photo) "photo" else "anime")} · 2× · Auto settings",
                style = RsType.display(28f),
                color = c.ink,
                modifier = Modifier.padding(top = 6.dp),
            )
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .border(1.dp, c.hairlineSoft, RoundedCornerShape(0.dp))
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlanLine("Model", plan.model.name)
                PlanLine("Tiles", "${plan.tilePx} px · ${plan.compute}")
                PlanLine("Input", plan.inputNote)
                PlanLine("Output", "${fmtDims(plan.outW, plan.outH)} · ${plan.format}")
            }
            plan.wanted?.let { wanted -> WantedHint(store, wanted.id, plan.model.name, Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun PlanLine(key: String, value: String) {
    val c = LocalRsColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(key, style = RsType.body(13f), color = c.body, modifier = Modifier.width(56.dp))
        Text(value, style = RsType.body(13f, lineHeightRatio = 1.35f), color = c.ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WantedHint(store: AppStore, wantedId: String, currentName: String, modifier: Modifier = Modifier) {
    val c = LocalRsColors.current
    val wanted = ModelCatalog.byId(wantedId)
    val download = store.state.value.downloads[wanted.id]
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.canvas)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (download != null) "Downloading ${wanted.name}" else "${wanted.name} is recommended here",
                style = RsType.body(14f, FontWeight.Medium, 1.3f),
                color = c.ink,
            )
            if (download != null) {
                Text(
                    "${(download.progress * 100).toInt()}% of ${fmtMB(wanted.sizeMB)}",
                    style = RsType.body(12f).copy(fontFeatureSettings = "tnum"),
                    color = c.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
                RsProgressBar(download.progress, "Downloading ${wanted.name}", Modifier.padding(top = 8.dp))
            } else {
                Text(
                    "Using $currentName until it is installed.",
                    style = RsType.body(12f, lineHeightRatio = 1.3f),
                    color = c.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (download == null) {
            RsButton(
                onClick = { store.downloadModel(wanted.id) },
                size = RsButtonSize.Sm,
                icon = LucideIcons.Download,
                label = fmtMB(wanted.sizeMB),
            )
        }
    }
}

@Composable
private fun ManualSections(
    store: AppStore,
    photo: PhotoItem,
    plan: Plan,
    prefs: JobPrefs,
    detecting: Boolean,
    large: String,
    onLarge: (String) -> Unit,
    onOpenModels: () -> Unit,
    setPrefs: ((JobPrefs) -> JobPrefs) -> Unit,
) {
    val c = LocalRsColors.current
    val recommended = com.tumuyan.ncnn.realsr.core.plan.PipelinePlanner.recommendedId(
        store.state.value.mode,
        plan.type,
    )
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (plan.overLimit) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
                    .padding(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(LucideIcons.TriangleAlert, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Large photo · ${fmtMP(photo.mp)}", style = RsType.body(15f, FontWeight.Medium), color = c.ink)
                        Text(
                            "Above the ${store.device.inputLimitMP} MP comfortable limit for your device. Optimizing resizes a temporary copy; the original stays untouched.",
                            style = RsType.body(13f, lineHeightRatio = 1.35f),
                            color = c.muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Box(Modifier.padding(top = 12.dp)) {
                    RsSegmented(
                        label = "Large photo handling",
                        value = large,
                        options = listOf("optimize" to "Optimize size", "full" to "Full size"),
                        onChange = onLarge,
                    )
                }
            }
        }

        RsField(
            label = "Image type",
            hint = if (detecting) "Detecting…" else "Detected: ${cap(if (photo.type == ImageType.Photo) "photo" else "anime")}${if (photo.lowConfidence) " (not sure)" else ""}",
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Auto", "photo" to "Photo", "anime" to "Anime").forEach { (value, label) ->
                    RsChip(
                        text = label,
                        selected = prefs.type == value,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            store.haptic(HapticKind.Light)
                            setPrefs { it.copy(type = value) }
                        },
                    )
                }
            }
        }

        RsField(label = "Model", hint = "${ModelCatalog.MODELS.size} models in the catalog. Tap to browse, download or switch.") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
                    .clickable { onOpenModels() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(plan.model.name, style = RsType.body(15f, FontWeight.Medium, 1.3f), color = c.ink)
                    Text(
                        "${plan.model.desc} · ${plan.model.badge}",
                        style = RsType.body(13f, lineHeightRatio = 1.3f),
                        color = c.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (plan.isAuto) RsBadge("Automatic")
                        if (plan.model.id == recommended) RsBadge("Recommended", tone = RsBadgeTone.Amber)
                    }
                }
                Icon(LucideIcons.ChevronRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
            }
            plan.wanted?.let { wanted ->
                Column(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.card)
                        .padding(bottom = 4.dp),
                ) {
                    WantedHint(store, wanted.id, plan.model.name)
                }
            }
        }

        RsField(
            label = "Scale",
            hint = "Output ${fmtDims(plan.outW, plan.outH)} · ${fmtMP(plan.outMP)} · about ${fmtEta(plan.etaSec)}" +
                if (plan.extraResize) "\n5× uses an extra resize step." else "",
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 4, 5).forEach { scale ->
                    RsChip(
                        text = "$scale×",
                        selected = prefs.scale == scale,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            store.haptic(HapticKind.Light)
                            setPrefs { it.copy(scale = scale) }
                        },
                    )
                }
            }
        }
    }
}
