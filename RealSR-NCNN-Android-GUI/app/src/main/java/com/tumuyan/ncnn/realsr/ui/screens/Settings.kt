package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.Mark
import com.tumuyan.ncnn.realsr.design.RadioMark
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsConfirmSheet
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsRow
import com.tumuyan.ncnn.realsr.design.RsSegmented
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsSwitchRow
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.ComputePref
import com.tumuyan.ncnn.realsr.ui.state.FormatSetting
import com.tumuyan.ncnn.realsr.ui.state.LaunchMode
import com.tumuyan.ncnn.realsr.ui.state.Route
import com.tumuyan.ncnn.realsr.ui.state.ThemePref
import kotlinx.coroutines.delay

private const val FORMAT_LABEL_AUTO = "Automatic"

private fun launchLabel(mode: LaunchMode): String = when (mode) {
    LaunchMode.Last -> "Last used"
    LaunchMode.Upscale -> "Upscale Mode"
    LaunchMode.Super -> "Super Resolution"
}

private fun computeLabel(pref: ComputePref): String = when (pref) {
    ComputePref.Auto -> "Auto"
    ComputePref.Gpu -> "GPU"
    ComputePref.Cpu -> "CPU"
}

private fun formatLabel(setting: FormatSetting): String = when (setting) {
    FormatSetting.Auto -> FORMAT_LABEL_AUTO
    FormatSetting.Jpeg -> "JPEG"
    FormatSetting.Png -> "PNG"
    FormatSetting.Webp -> "WebP"
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    EyebrowLabel(text, modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp))
}

@Composable
fun SettingsScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.state.value
    val settings = state.settings
    var sheet by remember { mutableStateOf<String?>(null) }
    var resetConfirm by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val usedMB = com.tumuyan.ncnn.realsr.core.plan.ModelCatalog.MODELS
        .filter { it.id in state.installed }
        .sumOf { it.sizeMB }

    LaunchedEffect(checking) {
        if (checking) {
            delay(1100)
            checking = false
            store.refreshDevice()
            store.toast("Device checked: ${store.device.label}")
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(title = "Settings", onBack = { store.pop() }, scrolled = scroll.value > 8)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            SectionLabel("General")
            RsGroup {
                RsRow(title = "Language", value = "English", chevron = true, topDivider = false)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Theme", style = RsType.body(15f, FontWeight.Medium), color = c.ink, modifier = Modifier.padding(bottom = 10.dp))
                    RsSegmented(
                        label = "Theme",
                        value = settings.theme,
                        options = listOf(ThemePref.System to "System", ThemePref.Light to "Light", ThemePref.Dark to "Dark"),
                        onChange = { store.updateSettings { s -> s.copy(theme = it) } },
                    )
                }
                RsSwitchRow(
                    title = "Dynamic color",
                    sub = "Android 12 and later. Follows your wallpaper.",
                    checked = settings.dynamicColor,
                    onChange = { store.updateSettings { s -> s.copy(dynamicColor = it) } },
                )
                RsSwitchRow(
                    title = "Haptics",
                    sub = "Light feedback on taps and switches.",
                    checked = settings.haptics,
                    onChange = { store.updateSettings { s -> s.copy(haptics = it) } },
                )
                RsRow(
                    title = "Default mode on launch",
                    value = launchLabel(settings.launchMode),
                    chevron = true,
                    onClick = { sheet = "launch" },
                )
                RsRow(
                    title = "Compute preference",
                    value = computeLabel(settings.compute),
                    chevron = true,
                    onClick = { sheet = "compute" },
                )
                RsSwitchRow(
                    title = "Keep screen awake",
                    sub = "While an upscale is running.",
                    checked = settings.keepAwake,
                    onChange = { store.updateSettings { s -> s.copy(keepAwake = it) } },
                )
                RsSwitchRow(
                    title = "Slow down when hot or low on battery",
                    sub = "Protects your phone during long jobs.",
                    checked = settings.throttle,
                    onChange = { store.updateSettings { s -> s.copy(throttle = it) } },
                )
                RsRow(
                    title = "Re-run device check",
                    sub = "${store.device.label} · ${store.device.ramGB} GB · ${store.device.cores} cores",
                    value = if (checking) "Checking…" else null,
                    chevron = true,
                    onClick = { if (!checking) checking = true },
                )
            }

            SectionLabel("Output")
            RsGroup {
                RsRow(
                    title = "Save folder",
                    sub = "Pictures/RealScale-Pro · Can be changed on desktop.",
                    topDivider = false,
                )
                RsRow(
                    title = "Default output format",
                    value = formatLabel(settings.format),
                    chevron = true,
                    onClick = { sheet = "format" },
                )
                RsSwitchRow(
                    title = "Keep photo metadata",
                    sub = "Date, camera and location.",
                    checked = settings.keepMeta,
                    onChange = { store.updateSettings { s -> s.copy(keepMeta = it) } },
                )
            }

            SectionLabel("Models and storage")
            RsGroup {
                RsSwitchRow(
                    title = "Download on Wi-Fi only",
                    checked = settings.wifiOnly,
                    onChange = { store.updateSettings { s -> s.copy(wifiOnly = it) } },
                )
                RsRow(
                    title = "Model Manager",
                    value = fmtMB(usedMB),
                    chevron = true,
                    onClick = { store.push(Route.Models) },
                )
                RsRow(
                    title = "Clear cache",
                    sub = if (checking) "Clearing…" else "Temporary files only.",
                    chevron = true,
                    onClick = { store.clearCache() },
                )
            }

            SectionLabel("Privacy")
            RsGroup {
                RsSwitchRow(
                    title = "Usage analytics",
                    sub = "Off by default. Never includes images.",
                    checked = settings.analytics,
                    onChange = { store.updateSettings { s -> s.copy(analytics = it) } },
                )
                RsSwitchRow(
                    title = "Crash reports",
                    sub = "Off by default.",
                    checked = settings.crashReports,
                    onChange = { store.updateSettings { s -> s.copy(crashReports = it) } },
                )
            }
            Text(
                "Your images never leave this device. There is no account and no cloud processing.",
                style = RsType.body(13f, lineHeightRatio = 1.5f),
                color = c.muted,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp),
            )

            SectionLabel("Support")
            RsGroup {
                RsRow(title = "Replay the guide", chevron = true, topDivider = false, onClick = { store.replayGuide() })
                RsRow(title = "FAQ and feedback", chevron = true, onClick = { store.push(Route.Help) })
            }

            SectionLabel("About")
            RsGroup {
                RsRow(title = "Version", value = "2.0 · Images", topDivider = false)
                RsRow(
                    title = "Open-source licenses and credits",
                    chevron = true,
                    onClick = { store.push(Route.About) },
                )
            }

            Spacer(Modifier.height(28.dp))
            RsButton(
                onClick = { resetConfirm = true },
                variant = RsButtonVariant.Danger,
                full = true,
                label = "Reset the app",
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    RsSheet(open = sheet == "launch", onClose = { sheet = null }, title = "Default mode on launch") {
        OptionList(
            options = listOf(
                LaunchMode.Last to ("Last used" to null),
                LaunchMode.Upscale to ("Upscale Mode" to "Fast and simple, for everyday photos."),
                LaunchMode.Super to ("Super Resolution" to "Maximum quality from the original file."),
            ),
            selected = settings.launchMode,
            onSelect = {
                store.updateSettings { s -> s.copy(launchMode = it) }
                sheet = null
            },
        )
    }

    RsSheet(open = sheet == "compute", onClose = { sheet = null }, title = "Compute preference") {
        OptionList(
            options = listOf(
                ComputePref.Auto to ("Auto" to "Uses the GPU when the model supports it."),
                ComputePref.Gpu to ("GPU" to "Fastest on most phones."),
                ComputePref.Cpu to ("CPU" to "Slower, but works everywhere."),
            ),
            selected = settings.compute,
            onSelect = {
                store.updateSettings { s -> s.copy(compute = it) }
                sheet = null
            },
        )
    }

    RsSheet(open = sheet == "format", onClose = { sheet = null }, title = "Default output format") {
        OptionList(
            options = listOf(
                FormatSetting.Auto to ("Automatic" to "Keeps the source format in Upscale Mode."),
                FormatSetting.Jpeg to ("JPEG" to "Smaller files."),
                FormatSetting.Png to ("PNG" to "Lossless."),
                FormatSetting.Webp to ("WebP" to "Small and modern."),
            ),
            selected = settings.format,
            onSelect = {
                store.updateSettings { s -> s.copy(format = it) }
                sheet = null
            },
        )
    }

    RsConfirmSheet(
        open = resetConfirm,
        onClose = { resetConfirm = false },
        title = "Reset the app?",
        body = "Settings, history and downloaded models on this device are cleared. Your photos are never touched.",
        confirmLabel = "Reset everything",
        danger = true,
        onConfirm = {
            resetConfirm = false
            store.resetApp()
        },
    )
}

@Composable
private fun <T> OptionList(options: List<Pair<T, Pair<String, String?>>>, selected: T, onSelect: (T) -> Unit) {
    val c = LocalRsColors.current
    Column(Modifier.padding(bottom = 16.dp)) {
        options.forEachIndexed { index, (value, labels) ->
            if (index > 0) com.tumuyan.ncnn.realsr.design.HairlineSoftDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(value) }
                    .background(if (value == selected) c.soft else c.canvas)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(labels.first, style = RsType.body(15f, FontWeight.Medium), color = c.ink)
                    labels.second?.let {
                        Text(it, style = RsType.body(13f, lineHeightRatio = 1.4f), color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                RadioMark(checked = value == selected)
            }
        }
    }
}

/* ---------- Help and feedback ---------- */

private val FAQ = listOf(
    "Why is upscaling slow on my phone?" to
        "Every pixel is rebuilt on your device instead of a server. Bigger photos, higher scales and heavier models take longer. Upscale Mode with the Default level is the fastest path.",
    "What is a tile?" to
        "A big image is cut into small squares, each square is enhanced, then they are joined again. Smaller tiles use less memory, which is how the app avoids crashes on phones with little RAM.",
    "Why does 5× look a little different?" to
        "Models work at 2×, 3× or 4×. For 5×, the app runs 4× and then resizes the result up by 125% with light sharpening, so there is one extra resize step.",
    "Do my photos leave my device?" to
        "No. There is no account and no cloud processing. The network is used only to download models and check for model updates.",
    "Which mode should I use?" to
        "Upscale Mode is right for everyday photos on any device. Super Resolution Mode keeps the original file untouched and uses heavier models, so it suits powerful devices and detailed work.",
)

@Composable
fun HelpScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.state.value
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var attach by remember { mutableStateOf(true) }
    var expanded by remember { mutableIntStateOf(-1) }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(title = "Help and feedback", onBack = { store.pop() }, scrolled = scroll.value > 8)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("Common questions", style = RsType.display(28f), color = c.ink, modifier = Modifier.padding(top = 8.dp))
            Column(
                Modifier
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp)),
            ) {
                FAQ.forEachIndexed { index, (question, answer) ->
                    if (index > 0) com.tumuyan.ncnn.realsr.design.HairlineSoftDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = if (expanded == index) -1 else index }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(question, style = RsType.body(15f, FontWeight.Medium, 1.3f), color = c.ink, modifier = Modifier.weight(1f))
                            Icon(LucideIcons.ChevronDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                        }
                        if (expanded == index) {
                            Text(
                                answer,
                                style = RsType.body(14f, lineHeightRatio = 1.55f),
                                color = c.body,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            Column(
                Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.card)
                    .padding(20.dp),
            ) {
                Icon(LucideIcons.MessageSquare, contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
                Text("Something not working?", style = RsType.body(16f, FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "Tell us what happened. You can attach your device profile, never your images.",
                    style = RsType.body(14f, lineHeightRatio = 1.55f),
                    color = c.body,
                    modifier = Modifier.padding(top = 4.dp),
                )
                RsButton(
                    onClick = { open = true },
                    variant = RsButtonVariant.Primary,
                    modifier = Modifier.padding(top = 16.dp),
                    label = "Send feedback",
                )
            }

            RsGroup(Modifier.padding(top = 12.dp)) {
                RsRow(title = "Replay the guide", chevron = true, topDivider = false, onClick = { store.replayGuide() })
            }
        }
    }

    RsSheet(
        open = open,
        onClose = { open = false },
        title = "Send feedback",
        footer = {
            RsButton(
                onClick = {
                    open = false
                    text = ""
                    store.saveFeedback(text, attach)
                },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                enabled = text.trim().length >= 3,
                label = "Send",
            )
        },
    ) {
        Text("What happened?", style = RsType.body(15f, FontWeight.Medium), color = LocalRsColors.current.ink)
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .border(1.dp, LocalRsColors.current.hairline, RoundedCornerShape(RsRadius.md.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    "For example: the result looked too soft at 4×",
                    style = RsType.body(16f, lineHeightRatio = 1.55f),
                    color = LocalRsColors.current.mutedSoft,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = RsType.body(16f, lineHeightRatio = 1.55f).copy(color = LocalRsColors.current.ink),
                cursorBrush = SolidColor(LocalRsColors.current.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
            )
        }
        RsGroup(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            RsSwitchRow(
                title = "Attach device profile",
                sub = "${store.device.label} · ${store.device.ramGB} GB · ${store.device.gpu}. Images are never attached.",
                checked = attach,
                onChange = { attach = it },
            )
        }
    }
}

/* ---------- About ---------- */

private val LICENSES = listOf(
    Triple("ncnn", "BSD-3-Clause", "Primary on-device engine"),
    Triple("MNN", "Apache-2.0", "Engine for MNN-format models"),
    Triple("Real-ESRGAN", "BSD-3-Clause", "Photo and anime upscaling"),
    Triple("Real-CUGAN", "MIT", "Anime upscaling"),
    Triple("Anime4KCPP", "MIT", "Anime4K upscaling"),
    Triple("ImageMagick", "Apache-2.0", "Image conversion"),
)

@Composable
fun AboutScreen(store: AppStore) {
    val c = LocalRsColors.current
    var doc by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(title = "About", onBack = { store.pop() }, scrolled = scroll.value > 8)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                Mark(size = 80.dp)
                Text("RealScale-Pro", style = RsType.display(36f), color = c.ink, modifier = Modifier.padding(top = 16.dp))
                Text("Version 2.0 · Image upscaling", style = RsType.body(14f), color = c.muted, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "An offline, on-device AI image upscaler. Pick a photo, tap Upscale and get a sharper, bigger image with no account and no upload.",
                    style = RsType.body(15f, lineHeightRatio = 1.6f),
                    color = c.body,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Text("Open-source licenses", style = RsType.display(26f), color = c.ink, modifier = Modifier.padding(top = 36.dp))
            Text(
                "RealScale-Pro is built on these projects.",
                style = RsType.body(13f, lineHeightRatio = 1.4f),
                color = c.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            RsGroup(Modifier.padding(top = 12.dp)) {
                LICENSES.forEachIndexed { index, (name, license, use) ->
                    RsRow(
                        title = name,
                        sub = use,
                        value = license,
                        topDivider = index > 0,
                    )
                }
            }

            Text("Model credits", style = RsType.display(26f), color = c.ink, modifier = Modifier.padding(top = 36.dp))
            Text(
                "The models come from RealSR-NCNN-Android by tumuyan: the Real-ESRGAN, waifu2x, SRMD and Anime4K families, mirrored at huggingface.co/tumuyan2/realsr-models. Each model is used under its own license.",
                style = RsType.body(14f, lineHeightRatio = 1.6f),
                color = c.body,
                modifier = Modifier.padding(top = 8.dp),
            )

            RsGroup(Modifier.padding(top = 32.dp)) {
                RsRow(title = "Terms of use", chevron = true, topDivider = false, onClick = { doc = "terms" })
                RsRow(title = "Privacy policy", chevron = true, onClick = { doc = "privacy" })
            }

            Column(
                Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.card)
                    .padding(16.dp),
            ) {
                Text(
                    "Everything runs on your device. Models are downloaded once, then upscaling works fully offline.",
                    style = RsType.body(13f, lineHeightRatio = 1.55f),
                    color = c.body,
                )
            }
        }
    }

    RsSheet(open = doc != null, onClose = { doc = null }, title = if (doc == "terms") "Terms of use" else "Privacy policy") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            if (doc == "terms") {
                Text("RealScale-Pro is provided as is. You keep full ownership of every image you upscale.", style = RsType.body(15f, lineHeightRatio = 1.6f), color = LocalRsColors.current.body)
                Text("Use the app only with images you have the right to edit.", style = RsType.body(15f, lineHeightRatio = 1.6f), color = LocalRsColors.current.body)
            } else {
                Text("Images are processed on your device and are never uploaded.", style = RsType.body(15f, lineHeightRatio = 1.6f), color = LocalRsColors.current.body)
                Text(
                    "The network is used only to download models and to check for model updates. Analytics and crash reports are off unless you turn them on.",
                    style = RsType.body(15f, lineHeightRatio = 1.6f),
                    color = LocalRsColors.current.body,
                )
            }
        }
    }
}
