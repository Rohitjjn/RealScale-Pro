package com.tumuyan.ncnn.realsr.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tumuyan.ncnn.realsr.BuildConfig
import com.tumuyan.ncnn.realsr.core.engine.EnginePreferences
import com.tumuyan.ncnn.realsr.core.engine.ExecutionBackend
import com.tumuyan.ncnn.realsr.core.engine.ExternalOpenMode
import com.tumuyan.ncnn.realsr.core.engine.ImageIntakeManager
import com.tumuyan.ncnn.realsr.core.engine.OutputFormat
import com.tumuyan.ncnn.realsr.core.engine.PresetCommandResolver
import com.tumuyan.ncnn.realsr.core.engine.PresetId
import com.tumuyan.ncnn.realsr.ui.components.BrandLogoMedallion
import com.tumuyan.ncnn.realsr.ui.components.EditorialCard
import com.tumuyan.ncnn.realsr.ui.components.EditorialTopBar
import com.tumuyan.ncnn.realsr.ui.components.QuietOutlineButton
import com.tumuyan.ncnn.realsr.ui.components.TerracottaPrimaryButton
import com.tumuyan.ncnn.realsr.ui.components.formatBytesHuman
import com.tumuyan.ncnn.realsr.ui.theme.ConsoleMonoFamily
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleIcons
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleThemeTokens
import com.tumuyan.ncnn.realsr.ui.theme.Spacing
import com.tumuyan.ncnn.realsr.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    preferences: EnginePreferences,
    consoleLog: String,
    discoveredCommandsProvider: () -> List<PresetCommandResolver.DiscoveredCommand>,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDefaultPresetChanged: (PresetId?) -> Unit = {},
    onKeepScreenOnChanged: (Boolean) -> Unit = {},
    onHapticsChanged: (Boolean) -> Unit = {},
    onWifiOnlyChanged: (Boolean) -> Unit = {},
    onExternalOpenModeChanged: (ExternalOpenMode) -> Unit = {},
    onBackendChanged: (ExecutionBackend) -> Unit,
    onTileSizeChanged: (Int) -> Unit,
    onOutputFormatChanged: (OutputFormat) -> Unit,
    onCustomOutputFolderChanged: (Uri?) -> Unit,
    onThreadCountChanged: (String) -> Unit,
    onHideProgramOutputChanged: (Boolean) -> Unit,
    onClearTempCache: () -> Unit = {},
    onRunFolderBatch: (Uri) -> Unit,
    onRunRawCommand: (String) -> Unit,
    onResetEngineCache: () -> Unit,
    onBack: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    val context = LocalContext.current
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var rawCommandText by rememberSaveable { mutableStateOf("./realsr-ncnn -h") }
    var threadInput by rememberSaveable(preferences.threadCount) { mutableStateOf(preferences.threadCount) }
    var tempCacheBytes by remember { mutableLongStateOf(0L) }
    var cacheRefreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(cacheRefreshTick) {
        tempCacheBytes = withContext(Dispatchers.IO) {
            ImageIntakeManager.measureTemporaryCacheBytes(context)
        }
    }

    val outputFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onCustomOutputFolderChanged(uri)
        }
    }

    val batchFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onRunFolderBatch(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        EditorialTopBar(
            title = "Settings",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // 1. Appearance
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Warm Light",
                        ThemeMode.DARK to "Warm Dark"
                    ).forEach { (mode, label) ->
                        ChoiceChipItem(
                            label = label,
                            selected = themeMode == mode,
                            onClick = { onThemeModeChanged(mode) }
                        )
                    }
                }
            }

            // 2. Defaults & Studio Experience
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Defaults & Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Default enhancement preset",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.body
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose which preset opens on cold start, or remember whichever preset you used last.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf<Pair<PresetId?, String>>(
                        null to "Last used",
                        PresetId.INSTANT to "Instant",
                        PresetId.PHOTO to "Photo",
                        PresetId.ANIME to "Anime & Art",
                        PresetId.MAXIMUM to "Maximum"
                    ).forEach { (presetOption, label) ->
                        ChoiceChipItem(
                            label = label,
                            selected = preferences.defaultPresetId == presetOption,
                            onClick = { onDefaultPresetChanged(presetOption) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(Spacing.sm))

                SettingsSwitchRow(
                    title = "Keep screen on while enhancing",
                    subtitle = "Prevent the display from sleeping during foreground upscaling.",
                    checked = preferences.keepScreenOnDuringEnhance,
                    onCheckedChange = onKeepScreenOnChanged
                )

                Spacer(modifier = Modifier.height(Spacing.xs))
                HorizontalDivider(thickness = 1.dp, color = colors.hairlineSoft)
                Spacer(modifier = Modifier.height(Spacing.xs))

                SettingsSwitchRow(
                    title = "Tactile haptics",
                    subtitle = "Subtle haptic ticks when switching presets, scales, or long-pressing cards.",
                    checked = preferences.hapticsEnabled,
                    onCheckedChange = onHapticsChanged
                )
            }

            // 3. System Integration & Downloads
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "When opened from other apps",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose how RealScale-Pro responds when you tap Open with on an image in Gallery, Files, or chat apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(
                        ExternalOpenMode.QUICK_SHEET to "Quick sheet",
                        ExternalOpenMode.FULL_APP to "Full app"
                    ).forEach { (mode, label) ->
                        ChoiceChipItem(
                            label = label,
                            selected = preferences.externalOpenMode == mode,
                            onClick = { onExternalOpenModeChanged(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(Spacing.sm))

                SettingsSwitchRow(
                    title = "Wi-Fi only model downloads",
                    subtitle = "Wait for an unmetered Wi-Fi connection before downloading AI models.",
                    checked = preferences.wifiOnlyDownloads,
                    onCheckedChange = onWifiOnlyChanged
                )
            }

            // 4. Saving & Format
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Saving & format",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Image format",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.body
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(
                        OutputFormat.PNG to "PNG · Lossless",
                        OutputFormat.WEBP to "WebP · Compact",
                        OutputFormat.JPG to "JPG · Universal"
                    ).forEach { (fmt, label) ->
                        ChoiceChipItem(
                            label = label,
                            selected = preferences.outputFormat == fmt,
                            onClick = { onOutputFormatChanged(fmt) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Output folder",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.body
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preferences.outputTreeUri ?: "Default · Gallery (Pictures/RealScale-Pro)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    QuietOutlineButton(
                        text = "Choose folder",
                        onClick = { outputFolderLauncher.launch(null) }
                    )
                    if (preferences.outputTreeUri != null) {
                        QuietOutlineButton(
                            text = "Use default",
                            tint = colors.muted,
                            onClick = { onCustomOutputFolderChanged(null) }
                        )
                    }
                }
            }

            // 5. Performance & Storage Cache
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Performance & storage",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Processing device",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.body
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "GPU uses Vulkan/OpenCL for speed; switch to CPU if an older device shows graphics artifacts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    ChoiceChipItem(
                        label = "Automatic (GPU)",
                        selected = preferences.backend == ExecutionBackend.AUTO_GPU,
                        onClick = { onBackendChanged(ExecutionBackend.AUTO_GPU) }
                    )
                    ChoiceChipItem(
                        label = "CPU Compatibility",
                        selected = preferences.backend == ExecutionBackend.CPU,
                        onClick = { onBackendChanged(ExecutionBackend.CPU) }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Memory tile size",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.body
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Smaller tiles reduce peak memory on high-megapixel photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    listOf(
                        0 to "Auto",
                        256 to "256 px",
                        128 to "128 px",
                        64 to "64 px"
                    ).forEach { (tile, label) ->
                        ChoiceChipItem(
                            label = label,
                            selected = preferences.tileSize == tile,
                            onClick = { onTileSizeChanged(tile) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = "Temporary image cache",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.body
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Currently using ${formatBytesHuman(tempCacheBytes)} for staged inputs. Clearing keeps all downloaded AI models and library creations intact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                QuietOutlineButton(
                    text = "Clear temporary cache",
                    onClick = {
                        onClearTempCache()
                        tempCacheBytes = 0L
                        cacheRefreshTick += 1
                    }
                )
            }

            // 6. Collapsible Advanced Section
            EditorialCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = colors.surfaceSoft
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Spacing.minTouchTarget)
                        .clickable(role = Role.Button) { showAdvanced = !showAdvanced },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Advanced",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.ink
                        )
                        Text(
                            text = "Batch folder processing, thread tuning, custom CLI & raw console",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }
                    Icon(
                        imageVector = if (showAdvanced) RealScaleIcons.ChevronUp else RealScaleIcons.ChevronDown,
                        contentDescription = if (showAdvanced) "Collapse Advanced" else "Expand Advanced",
                        tint = colors.ink
                    )
                }

                if (showAdvanced) {
                    val discovered = remember(showAdvanced) { discoveredCommandsProvider() }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                    Spacer(modifier = Modifier.height(Spacing.md))

                    Text(
                        text = "Folder batch upscale",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Upscale every image inside a folder using your active Studio preset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    QuietOutlineButton(
                        text = "Select folder to upscale",
                        onClick = { batchFolderLauncher.launch(null) }
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Text(
                        text = "Custom NCNN thread profile (-j)",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = threadInput,
                        onValueChange = {
                            threadInput = it
                            onThreadCountChanged(it)
                        },
                        placeholder = { Text("Leave blank for automatic (e.g. 1:2:2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    SettingsSwitchRow(
                        title = "Hide program output",
                        subtitle = "Suppress verbose engine stdout lines in the console log",
                        checked = preferences.hideProgramOutput,
                        onCheckedChange = onHideProgramOutputChanged
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Text(
                        text = "Engine command & diagnostics (${discovered.size} presets indexed)",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        listOf(
                            "./resize-ncnn -h",
                            "./realsr-ncnn -h",
                            "./GetMNNInfo",
                            "./magick -version"
                        ).forEach { quickCmd ->
                            ChoiceChipItem(
                                label = quickCmd.removePrefix("./"),
                                selected = rawCommandText == quickCmd,
                                onClick = { rawCommandText = quickCmd }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = rawCommandText,
                        onValueChange = { rawCommandText = it },
                        textStyle = TextStyle(fontFamily = ConsoleMonoFamily, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TerracottaPrimaryButton(
                            text = "Run",
                            onClick = { onRunRawCommand(rawCommandText) }
                        )
                        QuietOutlineButton(
                            text = "Reset engine cache",
                            onClick = onResetEngineCache
                        )
                    }

                    if (consoleLog.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colors.canvas,
                            border = BorderStroke(1.dp, colors.hairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Console output",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.muted
                                    )
                                    Text(
                                        text = "Copy",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.terracotta,
                                        modifier = Modifier
                                            .clickable {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("RealScale Console", consoleLog))
                                            }
                                            .padding(Spacing.xs)
                                    )
                                }
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = consoleLog,
                                    style = TextStyle(
                                        fontFamily = ConsoleMonoFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = colors.body
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 7. About & Upstream Open-Source Credits
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandLogoMedallion(
                        size = 56.dp,
                        contentDescription = "RealScale-Pro logo"
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "About RealScale-Pro",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · On-Device Image Upscaling",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.body
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Zero analytics, zero accounts, zero ads, and zero cloud uploads. Network access is used exclusively when you download models from Model Hub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "Upstream Credits & License (MIT / BSD)",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Built upon RealSR-NCNN-Android by tumuyan (MIT), Tencent NCNN & realsr/waifu2x/realcugan/srmd-ncnn-vulkan by nihui (MIT / BSD), Alibaba MNN (Apache-2.0), Anime4KCPP by TianZerL, and ImageMagick.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.minTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onTerracotta,
                checkedTrackColor = colors.terracotta,
                uncheckedThumbColor = colors.muted,
                uncheckedTrackColor = colors.surfaceCard,
                uncheckedBorderColor = colors.hairline
            )
        )
    }
}

@Composable
private fun ChoiceChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = RealScaleThemeTokens.colors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.terracotta else colors.surfaceSoft,
        border = BorderStroke(1.dp, if (selected) colors.terracotta else colors.hairline),
        modifier = Modifier
            .defaultMinSize(minWidth = Spacing.minTouchTarget, minHeight = 42.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.onTerracotta else colors.ink
            )
        }
    }
}
