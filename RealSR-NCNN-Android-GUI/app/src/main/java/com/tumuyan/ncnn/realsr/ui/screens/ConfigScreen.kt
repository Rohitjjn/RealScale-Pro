package com.tumuyan.ncnn.realsr.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.engine.ModelExecutionSpec
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.PreflightAnalyzer
import com.tumuyan.ncnn.realsr.core.engine.PreflightMitigation
import com.tumuyan.ncnn.realsr.core.engine.PreflightVerdict
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.engine.planner.ComputePref
import com.tumuyan.ncnn.realsr.core.engine.planner.ImageType
import com.tumuyan.ncnn.realsr.core.engine.planner.Level
import com.tumuyan.ncnn.realsr.core.engine.planner.ModeId
import com.tumuyan.ncnn.realsr.core.engine.planner.OutputFormatPref
import com.tumuyan.ncnn.realsr.core.engine.planner.TilePreset
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.model.ModelCatalogItem
import com.tumuyan.ncnn.realsr.core.model.ModelInstallStatus
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.BadgeTone
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.ModelPickerSheet
import com.tumuyan.ncnn.realsr.ui.components.RealScaleBadge
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleChip
import com.tumuyan.ncnn.realsr.ui.components.RealScaleConfirmSheet
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDivider
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDot
import com.tumuyan.ncnn.realsr.ui.components.RealScaleField
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRange
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSegmented
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSheet
import com.tumuyan.ncnn.realsr.ui.components.RealScaleStepper
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSwitchRow
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.state.HapticKind
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preset choices exposed on Config; the chosen preset drives the model & scale defaults. */
val CONFIG_PRESETS: List<UpscalePreset> = listOf(
    UpscalePreset.INSTANT,
    UpscalePreset.PHOTO,
    UpscalePreset.ANIME,
    UpscalePreset.MAXIMUM,
)

fun presetForMode(mode: ModeId, type: ImageType?): UpscalePreset = when (mode) {
    ModeId.UPSCALE -> when (type) {
        ImageType.ANIME -> UpscalePreset.ANIME
        else -> UpscalePreset.PHOTO
    }
    ModeId.SUPER -> UpscalePreset.MAXIMUM
}

@Composable
fun ConfigScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsState()
    val modeId = if (settings.mode == "super") ModeId.SUPER else ModeId.UPSCALE
    val prefs = viewModel.jobPrefs(modeId)
    val catalog by viewModel.modelCatalog.collectAsState()
    val deviceProfile by viewModel.deviceProfile.collectAsState()
    val tierLevel = deviceProfile?.tier?.level ?: 1
    val engineState by viewModel.engineState.collectAsState()
    val busy = engineState is com.tumuyan.ncnn.realsr.core.engine.EngineState.Running ||
        engineState is com.tumuyan.ncnn.realsr.core.engine.EngineState.Preparing

    val pendingInput by viewModel.pendingInput.collectAsState()
    val inputPhoto = pendingInput
    var scale by remember { mutableStateOf(prefs.scale) }
    var level by remember { mutableStateOf(prefs.level) }
    var preset by remember { mutableStateOf(presetForMode(modeId, prefs.type)) }
    var modelId by remember { mutableStateOf(prefs.modelId) }
    var tile by remember { mutableStateOf(prefs.tile) }
    var tileCustom by remember { mutableStateOf(prefs.tileCustom) }
    var compute by remember { mutableStateOf(prefs.compute) }
    var threads by remember { mutableStateOf(prefs.threads.coerceAtLeast(0)) }
    var denoise by remember { mutableStateOf(prefs.denoise ?: 0) }
    var formatPref by remember { mutableStateOf(prefs.format) }
    var keepMeta by remember { mutableStateOf(prefs.keepMeta) }

    var modelPickerOpen by remember { mutableStateOf(false) }
    var confirmDeleteModel by remember { mutableStateOf<String?>(null) }
    var preflightOpen by remember { mutableStateOf(false) }
    var preflightVerdict by remember { mutableStateOf<PreflightVerdict.Advisory?>(null) }

    val chosenModelState: CatalogModelState? = catalog.firstOrNull { it.catalog.id == modelId }
    val chosenModel: ModelCatalogItem? = chosenModelState?.catalog
    val modelDownloading = chosenModelState?.status in listOf(
        ModelInstallStatus.DOWNLOADING, ModelInstallStatus.QUEUED, ModelInstallStatus.VERIFYING, ModelInstallStatus.PAUSED
    )
    val modelInstalled = chosenModelState?.isInstalled == true
    val scaleSupported = scale in (chosenModel?.supportedScales ?: preset.supportedScales)
    val maxScale = (chosenModel?.supportedScales ?: preset.supportedScales).maxOrNull() ?: preset.defaultScale
    val effectiveScale = if (scaleSupported) scale else maxScale.coerceAtLeast(2)

    val typeLabel = when (prefs.type) {
        ImageType.PHOTO -> "Photo"
        ImageType.ANIME -> "Anime & art"
        null -> "Auto"
    }

    fun persist() {
        viewModel.updateJobPrefs(modeId) {
            it.copy(
                level = level,
                type = prefs.type,
                modelId = modelId,
                scale = effectiveScale,
                tile = tile,
                tileCustom = tileCustom,
                compute = compute,
                threads = threads,
                format = formatPref,
                keepMeta = keepMeta,
                denoise = if (denoise == 0) null else denoise,
            )
        }
        viewModel.haptic(HapticKind.TICK)
    }

    fun startJob() {
        persist()
        val input = inputPhoto ?: return
        val spec: ModelExecutionSpec? = when {
            preset.id == com.tumuyan.ncnn.realsr.core.engine.PresetId.INSTANT && modelId == "auto" -> null
            else -> viewModel.modelRepository.resolveExecutionSpec(
                preset = preset,
                scale = effectiveScale,
                catalogStates = catalog,
                overrideModelId = if (modelId == "auto") null else modelId,
            )
        }
        if (spec == null && preset.requiresDownload && modelId != "auto") {
            viewModel.showToast("Model not installed. Choose ‘Automatic’ or download it first.")
            return
        }
        scope.launch {
            val verdict = withContext(Dispatchers.IO) {
                PreflightAnalyzer.evaluate(
                    inputDimensions = listOf(input.width to input.height),
                    scale = effectiveScale,
                    preset = preset,
                    tileSize = when (tile) {
                        TilePreset.CUSTOM -> tileCustom else tile.px
                    },
                    outputFormat = when (formatPref) {
                        OutputFormatPref.PNG -> OutputFormat.PNG
                        OutputFormatPref.JPEG -> OutputFormat.JPG
                        OutputFormatPref.WEBP -> OutputFormat.WEBP
                        else -> OutputFormat.PNG
                    },
                    deviceRamBytes = (deviceProfile?.ramGb ?: 8.0 * 1024).toLong() * 1024 * 1024,
                    freeStorageBytes = deviceProfile?.freeStorageGb ?: 32.0 * 1024 * 1024 * 1024,
                )
            }
            when (verdict) {
                is PreflightVerdict.Advisory -> {
                    preflightVerdict = verdict
                    preflightOpen = true
                }
                PreflightVerdict.Safe -> doLaunch()
            }
        }
    }

    private fun doLaunch() {
        viewModel.engine.startUpscale(
            com.tumuyan.ncnn.realsr.core.engine.UpscaleRequest(
                inputUris = listOf(inputPhoto.uri),
                preset = preset,
                scale = effectiveScale,
                modelSpec = when {
                    preset.id == com.tumuyan.ncnn.realsr.core.engine.PresetId.INSTANT && modelId == "auto" -> null
                    else -> viewModel.modelRepository.resolveExecutionSpec(
                        preset = preset,
                        scale = effectiveScale,
                        catalogStates = catalog,
                        overrideModelId = if (modelId == "auto") null else modelId,
                    )
                },
                autoSaveToGallery = true,
                notifyOnCompletion = true,
                maxInputMP = if (modeId == ModeId.UPSCALE) 4.0 else null,
            ),
        )
        navController.navigate(Routes.PROCESSING)
    }

    fun changeInput() {
        navController.popBackStack()
    }

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding()) {
        // Top bar
        Column {
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RealScaleIconButton(
                        onClick = { navController.popBackStack() },
                        variant = IconButtonVariant.Dark,
                        contentDescription = "Back",
                        icon = painterResource(R.drawable.ic_lucide_chevron_left),
                    )
                    Text(
                        if (modeId == ModeId.SUPER) "Configure Super Resolution" else "Configure Upscale",
                        style = RealScaleType.Display24,
                        color = c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    RealScaleIconButton(
                        onClick = { /* more options later */ },
                        variant = IconButtonVariant.Plain,
                        contentDescription = "More",
                        icon = painterResource(R.drawable.ic_lucide_ellipsis),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Input photo
            RealScaleGroup {
                if (inputPhoto != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = inputPhoto.uri,
                            contentDescription = inputPhoto.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(RealScaleShape.Lg).background(c.card),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(inputPhoto.name, style = RealScaleType.Body15Medium, color = c.ink, maxLines = 1)
                            Text(
                                "${inputPhoto.width}×${inputPhoto.height} · ${fmtBytesMB(inputPhoto.sizeBytes)}",
                                style = RealScaleType.Body13,
                                color = c.muted,
                            )
                        }
                        RealScaleButton(
                            onClick = { changeInput() },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Sm,
                            icon = painterResource(R.drawable.ic_lucide_image_upscale),
                            text = "Change",
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RealScaleShape.Lg)
                            .background(c.card)
                            .clickable { changeInput() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_lucide_images), contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Choose a photo", style = RealScaleType.Body15Medium, color = c.ink)
                            Text("Pick from gallery, files or camera", style = RealScaleType.Body13, color = c.muted)
                        }
                        Icon(painterResource(R.drawable.ic_lucide_chevron_right), contentDescription = null, tint = c.mutedSoft, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Mode
            RealScaleGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RealScaleShape.Lg)
                        .border(1.dp, c.hairline, RealScaleShape.Lg)
                        .background(c.card)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_lucide_layers), contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (modeId == ModeId.SUPER) "Super Resolution Mode" else "Upscale Mode", style = RealScaleType.Body15Medium, color = c.ink)
                        Text(
                            if (modeId == ModeId.SUPER) "Heavy models, lossless output, powerful devices" else "Fast, simple, works on any device",
                            style = RealScaleType.Body13, color = c.muted,
                        )
                    }
                    RealScaleChip(
                        label = modeName(settings.mode),
                        selected = true,
                        onClick = { },
                        enabled = false,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Level
            Text("Level", style = RealScaleType.Body15Medium, color = c.ink)
            RealScaleSegmented(
                options = listOf("default" to "Default", "manual" to "Manual", "advanced" to "Advanced"),
                selected = level.name.lowercase(),
                onSelect = { level = Level.valueOf(it.capitalize()) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )
            Text(
                when (level) {
                    Level.DEFAULT -> "Automatic scale, model and tiles — best for most photos."
                    Level.MANUAL -> "Pick scale, model and tile size yourself."
                    Level.ADVANCED -> "Full control: tile, compute, threads, denoise, format."
                },
                style = RealScaleType.Body13,
                color = c.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            // Preset chips
            Text("Preset", style = RealScaleType.Body15Medium, color = c.ink)
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CONFIG_PRESETS.forEach { p ->
                    val selected = preset.id == p.id
                    RealScaleChip(
                        label = p.title,
                        selected = selected,
                        onClick = {
                            preset = p
                            if (!scaleSupported || scale > (chosenModel?.supportedScales ?: preset.supportedScales).maxOrNull() ?: 4) {
                                scale = p.defaultScale
                            }
                            if (modelId != "auto" && chosenModel?.catalog.category.uppercase() != p.id.name.uppercase()) {
                                modelId = "auto"
                            }
                            persist()
                        },
                        sub = if (p.requiresDownload) java.lang.String.format(java.util.Locale.US, "%.1f MB", p.approxDownloadMb) else "Ready",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Scale
            Text("Scale", style = RealScaleType.Body15Medium, color = c.ink)
            val scaleOptions = listOf(2, 3, 4, 5)
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scaleOptions.forEach { s ->
                    val allowed = s in (chosenModel?.supportedScales ?: preset.supportedScales) || s == 5
                    val selected = effectiveScale == s
                    RealScaleChip(
                        label = "${s}×",
                        selected = selected,
                        onClick = { if (allowed) scale = s },
                        enabled = allowed,
                        sub = if (s == 5) "4× + resize" else null,
                    )
                }
            }
            if (effectiveScale == 5 || (scale == 5 && !scaleSupported)) {
                Text("5× runs the model at 4× then resizes the result up.", style = RealScaleType.Body13, color = c.muted, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))

            // Model
            Text("Model", style = RealScaleType.Body15Medium, color = c.ink)
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RealScaleChip(
                    label = "Automatic",
                    selected = modelId == "auto",
                    onClick = { modelId = "auto"; persist() },
                )
                RealScaleButton(
                    onClick = { modelPickerOpen = true },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Sm,
                    icon = painterResource(R.drawable.ic_lucide_sparkles),
                    text = modelId?.let { chosenModel?.displayName ?: it } ?: "Choose",
                    enabled = level != Level.DEFAULT,
                )
            }
            if (modelId != "auto") {
                chosenModel?.let { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RealScaleDot(com.tumuyan.ncnn.realsr.ui.components.DotTone.Good)
                        Text("${m.displayName} · ${fmtBytesMB(m.totalBytes)} ×${m.supportedScales.joinToString("/")}", style = RealScaleType.Body13, color = c.body)
                        if (modelDownloading) {
                            RealScaleIconButton(
                                onClick = { scope.launch { viewModel.modelRepository.cancelDownload(modelId) } },
                                variant = IconButtonVariant.Outline,
                                contentDescription = "Cancel download",
                                icon = painterResource(R.drawable.ic_lucide_x),
                            )
                        } else if (!modelInstalled && level != Level.DEFAULT) {
                            RealScaleButton(
                                onClick = { scope.launch { viewModel.modelRepository.startOrResumeDownload(modelId) } },
                                variant = ButtonVariant.Secondary,
                                size = ButtonSize.Sm,
                                icon = painterResource(R.drawable.ic_lucide_download),
                                text = fmtBytesMB(m.totalBytes),
                            )
                        } else if (modelInstalled && level != Level.DEFAULT) {
                            RealScaleIconButton(
                                onClick = { confirmDeleteModel = modelId },
                                variant = IconButtonVariant.Outline,
                                contentDescription = "Delete ${m.displayName}",
                                icon = painterResource(R.drawable.ic_lucide_trash_2),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Advanced (visible for Manual/Advanced)
            if (level != Level.DEFAULT) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("Advanced", style = RealScaleType.Body15Medium, color = c.ink)

                    // Tile
                    RealScaleField(
                        label = "Tile size",
                        hint = "Smaller tiles use less RAM; larger tiles are faster.",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                TilePreset.SAFE to "Safe 128",
                                TilePreset.BALANCED to "Balanced 256",
                                TilePreset.FAST to "Fast 384",
                                TilePreset.MAX to "Maximum",
                                TilePreset.CUSTOM to "Custom",
                            ).forEach { (t, label) ->
                                val disabled = t == TilePreset.MAX && !(tierLevel >= 2 && (deviceProfile?.ramGb ?: 0) >= 12.0)
                                RealScaleChip(
                                    label = label,
                                    selected = tile == t,
                                    onClick = { if (!disabled) tile = t },
                                    enabled = !disabled,
                                    sub = if (disabled) "Needs Powerful + 12 GB RAM" else null,
                                )
                            }
                        }
                    }
                    if (tile == TilePreset.CUSTOM) {
                        RealScaleRange(
                            value = tileCustom,
                            min = 64,
                            max = 512,
                            step = 32,
                            onValueChange = { tileCustom = it },
                            contentDescription = "Custom tile size",
                            suffix = " px",
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Compute
                    RealScaleField(label = "Compute") {
                        RealScaleSegmented(
                            options = listOf("auto" to "Auto", "gpu" to "GPU", "cpu" to "CPU"),
                            selected = compute.name.lowercase(),
                            onSelect = { compute = ComputePref.valueOf(it.uppercase()) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        )
                    }
                    if (compute == ComputePref.CPU) {
                        RealScaleStepper(
                            value = threads,
                            min = 1,
                            max = (deviceProfile?.cores ?: 4),
                            step = 1,
                            onValueChange = { threads = it },
                            contentDescription = "CPU threads",
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Denoise
                    RealScaleField(
                        label = "Denoise",
                        hint = "0 = off, 3 = strong. Anime models only.",
                    ) {
                        RealScaleStepper(
                            value = denoise,
                            min = 0,
                            max = 3,
                            step = 1,
                            onValueChange = { denoise = it },
                            contentDescription = "Denoise level",
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Format
                    RealScaleField(label = "Format") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                OutputFormatPref.DEFAULT to "Auto",
                                OutputFormatPref.PNG to "PNG",
                                OutputFormatPref.JPEG to "JPEG",
                                OutputFormatPref.WEBP to "WEBP",
                            ).forEach { (f, label) ->
                                RealScaleChip(
                                    label = label,
                                    selected = formatPref == f,
                                    onClick = { formatPref = f },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    RealScaleSwitchRow(
                        title = "Keep EXIF metadata",
                        checked = keepMeta,
                        onCheckedChange = { keepMeta = it },
                        sub = "Copy camera info into the result.",
                        icon = painterResource(R.drawable.ic_lucide_circle_check),
                    )

                    Spacer(Modifier.height(12.dp))

                    RealScaleButton(
                        onClick = {
                            level = Level.DEFAULT
                            tile = TilePreset.FAST
                            compute = ComputePref.AUTO
                            threads = 0
                            denoise = 0
                            formatPref = OutputFormatPref.DEFAULT
                            keepMeta = true
                            tileCustom = 320
                            persist()
                        },
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Sm,
                        text = "Reset to defaults",
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Start
            RealScaleButton(
                onClick = { startJob() },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Lg,
                full = true,
                enabled = !busy && inputPhoto != null,
                icon = painterResource(R.drawable.ic_lucide_play),
                text = if (busy) "Processing…" else "Start upscale",
            )
            Spacer(Modifier.height(16.dp))
        }

        // Sheets
        RealScaleSheet(
            open = modelPickerOpen,
            onClose = { modelPickerOpen = false },
            title = "Choose a model",
            tall = true,
        ) {
            ModelPickerSheet(
                open = true,
                onClose = { modelPickerOpen = false },
                mode = modeId,
                type = prefs.type ?: ImageType.PHOTO,
                selectedId = modelId,
                installedStates = catalog,
                deviceTierLevel = tierLevel,
                onSelect = { id -> modelId = id; modelPickerOpen = false; persist() },
                onDownload = { id -> scope.launch { viewModel.modelRepository.startOrResumeDownload(id) } },
                onCancelDownload = { id -> scope.launch { viewModel.modelRepository.cancelDownload(id) } },
            )
        }

        RealScaleConfirmSheet(
            open = confirmDeleteModel != null,
            onClose = { confirmDeleteModel = null },
            title = "Delete model?",
            body = "This removes ${chosenModel?.displayName ?: confirmDeleteModel ?: "this model"} from the device. It can be re-downloaded later.",
            confirmLabel = "Delete",
            onConfirm = {
                val id = confirmDeleteModel ?: return@onConfirm
                confirmDeleteModel = null
                scope.launch {
                    viewModel.modelRepository.deleteInstalledModel(id)
                    if (modelId == id) modelId = "auto"
                    persist()
                    viewModel.showToast("Model deleted")
                }
            },
            cancelLabel = "Cancel",
            danger = true,
        )

        RealScaleSheet(
            open = preflightOpen,
            onClose = { preflightOpen = false; preflightVerdict = null },
            title = preflightVerdict?.title ?: "Heads up",
            footer = {
                Column {
                    RealScaleButton(
                        onClick = { preflightOpen = false; preflightVerdict = null; startJob() },
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Lg,
                        full = true,
                        text = "Start anyway",
                    )
                    Spacer(Modifier.height(8.dp))
                    RealScaleButton(onClick = { preflightOpen = false; preflightVerdict = null }, variant = ButtonVariant.Ghost, full = true, text = "Cancel")
                    Spacer(Modifier.height(4.dp))
                }
            },
        ) {
            Text(preflightVerdict?.message ?: "", style = RealScaleType.Body15, color = c.body, modifier = Modifier.padding(bottom = 12.dp))
            preflightVerdict?.mitigations?.forEach { m ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RealScaleDot(com.tumuyan.ncnn.realsr.ui.components.DotTone.Ok)
                    Text(m.label, style = RealScaleType.Body14, color = c.body)
                }
            }
        }
    }
}

/** TilePreset pixel size for the rule above. */
private fun TilePreset.px: Int = when (this) {
    TilePreset.SAFE -> 128
    TilePreset.BALANCED -> 256
    TilePreset.FAST -> 384
    TilePreset.MAX -> 0
    TilePreset.CUSTOM -> 320
}

private fun String.capitalize(): String = if (isEmpty()) this else this.substring(0, 1).uppercase() + this.substring(1)
