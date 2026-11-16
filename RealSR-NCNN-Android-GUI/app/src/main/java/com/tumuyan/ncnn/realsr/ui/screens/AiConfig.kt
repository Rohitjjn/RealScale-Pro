package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.ai.AiCatalog
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.core.plan.PlanLevel
import com.tumuyan.ncnn.realsr.core.plan.fmtDims
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsChip
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsIconButton
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsRow
import com.tumuyan.ncnn.realsr.design.RsSegmented
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsStepper
import com.tumuyan.ncnn.realsr.design.RsSwitchRow
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.JobStatus
import com.tumuyan.ncnn.realsr.ui.state.Route
import com.tumuyan.ncnn.realsr.ui.state.rememberState

/**
 * AI Upscale Mode → **configuration**. The same three levels as the on-device screen — Default,
 * Manual, Advanced — but the choices are cloud models, scale, prompt and output format instead of
 * tiles and denoise.
 *
 * Default is deliberately opinionated: a model is picked automatically from the saved keys, the
 * photo is sent with the system reconstruction prompt, and the user only presses one button.
 */
@Composable
fun AiConfigScreen(store: AppStore, photoId: String) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    val photo = store.photoById(photoId)
    val prefs = state.aiPrefs

    if (photo == null) {
        Column(Modifier.fillMaxSize().background(c.canvas)) {
            RsTopBar(title = "AI Upscale", onBack = { store.pop() })
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("This image is no longer available.", style = RsType.display(26f), color = c.ink)
                RsButton(onClick = { store.home() }, modifier = Modifier.padding(top = 20.dp), label = "Back to Home")
            }
        }
        return
    }

    val scroll = rememberScrollState()
    var sheet by remember { mutableStateOf<String?>(null) }
    var seedBump by remember { mutableIntStateOf(0) }
    val target = store.aiTarget(photo.id)
    val available = store.aiAvailableModels()
    val level = prefs.level
    val ratio = (photo.width.toFloat() / photo.height).coerceIn(0.35f, 3f)
    val busy = state.aiJob?.status == JobStatus.Running

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(
            title = "AI Upscale",
            onBack = { store.pop() },
            scrolled = scroll.value > 8,
            right = {
                RsIconButton(
                    icon = LucideIcons.KeyRound,
                    label = "API keys",
                    onClick = { store.push(Route.AiKeys) },
                )
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            /*
             * Round 5, issue 3: the preview keeps a *stable* height budget. A 9:16 photo used to make
             * `aspectRatio` fall back to a narrow column, which shoved the whole screen sideways; the
             * card is now always full width with a clamped height and the image is fitted inside it.
             */
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                val width = maxWidth
                val height = (width / ratio).coerceIn(180.dp, 320.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(RsRadius.lg.dp))
                        .background(c.card)
                        .clickable { store.push(Route.Viewer(photo.id, single = true)) },
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Selected image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RsBadge("AI", tone = RsBadgeTone.Dark)
                    }
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
            }

            /* The selected image, in its own block: resolution, size, type, aspect. */
            EyebrowLabel("Selected image", Modifier.padding(top = 18.dp, start = 4.dp, bottom = 8.dp))
            RsGroup {
                RsRow(title = "Resolution", value = fmtDims(photo.width, photo.height), topDivider = false)
                RsRow(title = "File size", value = fmtMB(photo.sizeMB))
                RsRow(title = "Type", value = if (photo.type == ImageType.Anime) "Anime / art" else "Photo")
                RsRow(
                    title = "Aspect",
                    value = aspectLabel(photo.width, photo.height),
                )
            }

            Box(Modifier.padding(top = 22.dp)) {
                RsSegmented(
                    label = "Setting level",
                    value = level,
                    options = listOf(
                        PlanLevel.DEFAULT to "Default",
                        PlanLevel.MANUAL to "Manual",
                        PlanLevel.ADVANCED to "Advanced",
                    ),
                    onChange = { next -> store.updateAiPrefs { it.copy(level = next) } },
                )
            }

            when (level) {
                PlanLevel.DEFAULT -> DefaultAiCard(
                    modelLabel = target?.model?.label ?: "No model available",
                    providerLabel = target?.provider?.name ?: "Add an API key",
                    keyLabel = target?.key?.label,
                    approxSec = target?.model?.approxSec ?: 0,
                    scale = target?.scale ?: prefs.scale,
                    onShuffle = {
                        // A new photo-independent draw: advanced users can reroll the model.
                        val models = available
                        if (models.isNotEmpty()) {
                            seedBump++
                            val pick = models[(models.size - 1 - (seedBump % models.size)).coerceIn(0, models.size - 1)]
                            store.updateAiPrefs { it.copy(modelId = pick.id) }
                            store.toast("Model: ${pick.label}")
                        }
                    },
                )

                PlanLevel.MANUAL -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ModelSection(
                        target = target,
                        models = available,
                        autoAllowed = true,
                        autoSelected = prefs.modelId == "auto",
                        onPickModel = { id -> store.updateAiPrefs { it.copy(modelId = id) } },
                        onOpenSheet = { sheet = "models" },
                    )
                    ScaleSection(
                        scales = target?.model?.scales ?: listOf(2, 4),
                        selected = target?.scale ?: prefs.scale,
                        onPick = { next -> store.updateAiPrefs { it.copy(scale = next) } },
                    )
                    KeySection(
                        keys = state.aiKeys,
                        selectedId = prefs.keyId,
                        onPick = { id -> store.updateAiPrefs { it.copy(keyId = id) } },
                        onOpenSheet = { sheet = "keys" },
                    )
                    PromptSection(
                        prompt = prefs.prompt,
                        onChange = { text -> store.updateAiPrefs { it.copy(prompt = text) } },
                    )
                }

                PlanLevel.ADVANCED -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ModelSection(
                        target = target,
                        models = available,
                        autoAllowed = false,
                        autoSelected = false,
                        onPickModel = { id -> store.updateAiPrefs { it.copy(modelId = id) } },
                        onOpenSheet = { sheet = "models" },
                    )
                    ScaleSection(
                        scales = target?.model?.scales ?: listOf(2, 4),
                        selected = target?.scale ?: prefs.scale,
                        onPick = { next -> store.updateAiPrefs { it.copy(scale = next) } },
                    )
                    KeySection(
                        keys = state.aiKeys,
                        selectedId = prefs.keyId,
                        onPick = { id -> store.updateAiPrefs { it.copy(keyId = id) } },
                        onOpenSheet = { sheet = "keys" },
                    )
                    PromptSection(
                        prompt = prefs.prompt,
                        onChange = { text -> store.updateAiPrefs { it.copy(prompt = text) } },
                    )
                    FormatSection(
                        selected = prefs.format,
                        onPick = { next -> store.updateAiPrefs { it.copy(format = next) } },
                    )
                    if (target?.model?.supportsFaceEnhance == true) {
                        RsGroup {
                            RsSwitchRow(
                                title = "Face enhancement",
                                sub = "Runs a second pass on faces. Slower, and more likely to change a face slightly.",
                                checked = prefs.faceEnhance,
                                topDivider = false,
                                onChange = { on -> store.updateAiPrefs { it.copy(faceEnhance = on) } },
                            )
                        }
                    }
                    if (target?.model?.supportsStrength == true) {
                        Column {
                            Text("Faithfulness", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RsChip(
                                    text = "Faithful",
                                    sub = "Closest to the original",
                                    selected = prefs.strength == "faithful",
                                    onClick = { store.updateAiPrefs { it.copy(strength = "faithful") } },
                                    modifier = Modifier.weight(1f),
                                )
                                RsChip(
                                    text = "Balanced",
                                    sub = "A little more detail",
                                    selected = prefs.strength != "faithful",
                                    onClick = { store.updateAiPrefs { it.copy(strength = "balanced") } },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Column {
                        Text("Upload size limit", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RsStepper(
                                value = prefs.maxInputMP,
                                min = 1,
                                max = target?.model?.maxInputMP ?: 4,
                                step = 1,
                                onChange = { next -> store.updateAiPrefs { it.copy(maxInputMP = next) } },
                                label = "Upload size limit",
                                format = { "$it MP" },
                            )
                        }
                        Text(
                            "Bigger uploads cost more and may be refused by the provider. The result is still " +
                                "resized to the scale you asked for.",
                            style = RsType.body(12f, lineHeightRatio = 1.45f),
                            color = c.muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            if (target == null) {
                Column(
                    Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.soft)
                        .padding(14.dp),
                ) {
                    Text("No usable API key", style = RsType.body(15f, FontWeight.Medium), color = c.ink)
                    Text(
                        "Every saved key is either disabled or has reached its monthly limit.",
                        style = RsType.body(13f, lineHeightRatio = 1.5f),
                        color = c.body,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Text(
                "This mode uploads your photo to the provider you selected. The local modes never do.",
                style = RsType.body(12f, lineHeightRatio = 1.5f),
                color = c.muted,
                modifier = Modifier.padding(top = 18.dp),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.canvas)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                buildString {
                    append(target?.model?.label ?: "No model")
                    target?.let { append(" · ${it.provider.name}") }
                    target?.let { append(" · ${it.scale}×") }
                    if ((target?.model?.approxSec ?: 0) > 0) append(" · about ${target?.model?.approxSec}s")
                },
                style = RsType.body(12f),
                color = c.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RsButton(
                onClick = {
                    store.haptic(com.tumuyan.ncnn.realsr.core.device.HapticKind.Medium)
                    store.startAiJob(photo.id)
                },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                enabled = target != null && !busy,
                icon = LucideIcons.Sparkles,
                label = if (busy) "AI upscale running" else "AI Upscale",
            )
        }
    }

    /* ---------------- model picker ---------------- */
    RsSheet(open = sheet == "models", onClose = { sheet = null }, title = "AI model", tall = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiOptionRow(
                title = "Automatic",
                sub = "Pick an available model for me, seeded by the photo.",
                selected = prefs.modelId == "auto",
                onClick = {
                    store.updateAiPrefs { it.copy(modelId = "auto") }
                    sheet = null
                },
            )
            available.forEach { model ->
                val provider = AiCatalog.provider(model.providerId)
                AiOptionRow(
                    title = model.label,
                    sub = "${provider.name} · up to ${model.scales.max()}× · ~${model.approxSec}s · ${model.badge}",
                    selected = prefs.modelId == model.id,
                    onClick = {
                        store.updateAiPrefs { it.copy(modelId = model.id, scale = if (it.scale in model.scales) it.scale else model.scales.last()) }
                        sheet = null
                    },
                )
            }
            if (available.isEmpty()) {
                Text(
                    "Add an API key first.",
                    style = RsType.body(14f),
                    color = LocalRsColors.current.muted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    /* ---------------- key picker ---------------- */
    RsSheet(open = sheet == "keys", onClose = { sheet = null }, title = "API key") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiOptionRow(
                title = "Automatic",
                sub = "Use any enabled key with quota left.",
                selected = prefs.keyId == null,
                onClick = {
                    store.updateAiPrefs { it.copy(keyId = null) }
                    sheet = null
                },
            )
            state.aiKeys.forEach { key ->
                AiOptionRow(
                    title = key.label,
                    sub = "${key.provider.name} · ${key.maskedKey} · ${key.usageLabel}",
                    selected = prefs.keyId == key.id,
                    onClick = {
                        store.updateAiPrefs { it.copy(keyId = key.id) }
                        sheet = null
                    },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ sections */

@Composable
private fun DefaultAiCard(
    modelLabel: String,
    providerLabel: String,
    keyLabel: String?,
    approxSec: Int,
    scale: Int,
    onShuffle: () -> Unit,
) {
    val c = LocalRsColors.current
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.lg.dp))
            .background(c.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(LucideIcons.Sparkles, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
            Text("One tap", style = RsType.body(16f, FontWeight.Medium), color = c.ink)
            Spacer(Modifier.weight(1f))
            RsBadge("Recommended", tone = RsBadgeTone.Neutral)
        }
        Text(
            "A model is chosen automatically from your saved keys. Your photo goes up with the " +
                "reconstruction prompt and comes back at ${scale}×.",
            style = RsType.body(13f, lineHeightRatio = 1.5f),
            color = c.body,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(c.hairline))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Model", style = RsType.body(11f), color = c.muted)
                Text(modelLabel, style = RsType.body(14f, FontWeight.Medium, 1.3f), color = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Provider", style = RsType.body(11f), color = c.muted)
                Text(providerLabel, style = RsType.body(14f, FontWeight.Medium, 1.3f), color = c.ink)
            }
            Column(Modifier.weight(1f)) {
                Text("Expected", style = RsType.body(11f), color = c.muted)
                Text(if (approxSec > 0) "~${approxSec}s" else "—", style = RsType.body(14f, FontWeight.Medium, 1.3f), color = c.ink)
            }
        }
        if (keyLabel != null) {
            Text(
                "Using $keyLabel",
                style = RsType.body(12f),
                color = c.muted,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        RsButton(
            onClick = onShuffle,
            modifier = Modifier.padding(top = 14.dp),
            variant = RsButtonVariant.Secondary,
            size = RsButtonSize.Md,
            full = true,
            icon = LucideIcons.RefreshCw,
            label = "Pick another model",
        )
    }
}

@Composable
private fun ModelSection(
    target: com.tumuyan.ncnn.realsr.ui.state.AiTarget?,
    models: List<com.tumuyan.ncnn.realsr.core.ai.AiModel>,
    autoAllowed: Boolean,
    autoSelected: Boolean,
    onPickModel: (String) -> Unit,
    onOpenSheet: () -> Unit,
) {
    val c = LocalRsColors.current
    Column {
        Text("Model", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
        Row(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
                .clickable(onClick = onOpenSheet)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (autoSelected) "Automatic" else (target?.model?.label ?: "Choose a model"),
                    style = RsType.body(15f, FontWeight.Medium, 1.3f),
                    color = c.ink,
                )
                Text(
                    if (autoSelected) {
                        "A model is picked for you"
                    } else {
                        "${target?.provider?.name ?: ""} · ${models.size} available"
                    },
                    style = RsType.body(12f, lineHeightRatio = 1.4f),
                    color = c.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(LucideIcons.ChevronDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
        if (!autoAllowed) {
            Text(
                "Advanced always uses the model below — no automatic choice.",
                style = RsType.body(12f, lineHeightRatio = 1.45f),
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (autoAllowed && models.isNotEmpty() && autoSelected && target != null) {
            Text(
                "This photo → ${target.model.label}",
                style = RsType.body(12f),
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ScaleSection(
    scales: List<Int>,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    val c = LocalRsColors.current
    Column {
        Text("Scale", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            scales.distinct().sorted().forEach { factor ->
                RsChip(
                    text = "${factor}×",
                    selected = selected == factor,
                    onClick = { onPick(factor) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (scales.distinct().size == 1) {
            Text(
                "This model only outputs ${scales.first()}×.",
                style = RsType.body(12f),
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun KeySection(
    keys: List<com.tumuyan.ncnn.realsr.core.ai.AiKeyRecord>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onOpenSheet: () -> Unit,
) {
    val c = LocalRsColors.current
    val selected = keys.firstOrNull { it.id == selectedId }
    Column {
        Text("API key", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
        Row(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
                .clickable(onClick = onOpenSheet)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected?.label ?: "Automatic",
                    style = RsType.body(15f, FontWeight.Medium, 1.3f),
                    color = c.ink,
                )
                Text(
                    selected?.let { "${it.provider.name} · ${it.usageLabel}" } ?: "Any enabled key with quota left",
                    style = RsType.body(12f, lineHeightRatio = 1.4f),
                    color = c.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(LucideIcons.ChevronDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PromptSection(prompt: String, onChange: (String) -> Unit) {
    val c = LocalRsColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Prompt", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
            Spacer(Modifier.weight(1f))
            if (prompt.isNotBlank()) {
                Text(
                    "Reset",
                    style = RsType.body(12f, FontWeight.Medium),
                    color = c.ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(RsRadius.sm.dp))
                        .clickable { onChange("") }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        AiInput(
            value = prompt,
            onValueChange = onChange,
            modifier = Modifier.padding(top = 8.dp),
            singleLine = false,
            minHeight = 96,
            placeholder = AiCatalog.DEFAULT_PROMPT,
        )
        Text(
            "Left empty, the built-in reconstruction prompt is used: restore detail, keep the exact " +
                "composition, invent nothing.",
            style = RsType.body(12f, lineHeightRatio = 1.45f),
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun FormatSection(selected: String, onPick: (String) -> Unit) {
    val c = LocalRsColors.current
    Column {
        Text("Output format", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("png", "jpg", "webp").forEach { format ->
                RsChip(
                    text = format.uppercase(),
                    selected = selected == format,
                    onClick = { onPick(format) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "PNG keeps every pixel exact; JPG and WebP are much smaller.",
            style = RsType.body(12f, lineHeightRatio = 1.45f),
            color = c.muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun aspectLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "—"
    val g = gcd(width, height)
    val w = width / g
    val h = height / g
    return if (w > 40 || h > 40) {
        val ratio = width.toDouble() / height
        String.format(java.util.Locale.US, "%.2f : 1", ratio)
    } else {
        "$w:$h"
    }
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
