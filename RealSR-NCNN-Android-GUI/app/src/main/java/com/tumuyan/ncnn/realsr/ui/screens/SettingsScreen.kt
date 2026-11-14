package com.tumuyan.ncnn.realsr.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.BuildConfig
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.BadgeTone
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleBadge
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSegmented
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSwitchRow
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.state.HapticKind
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.launch
import java.io.File

/** Settings — implementable rows only, backed by real DataStore + DeviceProfile state. */
@Composable
fun SettingsScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val deviceProfile by viewModel.deviceProfile.collectAsState()

    var binariesChecked by remember { mutableStateOf(false) }
    val binariesPresent = viewModel.binariesPresent()

    fun probe() {
        binariesChecked = true
        scope.launch { viewModel.probeBinariesThenRefresh() }
        viewModel.haptic(HapticKind.TICK)
    }

    fun clearCache() {
        scope.launch {
            val cacheDir = context.cacheDir
            val freed = dirSize(cacheDir)
            recursiveDelete(cacheDir)
            cacheDir.mkdirs()
            viewModel.showToast("Cache cleared (${fmtBytesMB(freed)})")
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp)
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
                    Text("Settings", style = RealScaleType.Display24, color = c.ink)
                    Spacer(Modifier.weight(1f))
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
            // Device check
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
                    Column(Modifier.weight(1f)) {
                        Text("Engine binaries", style = RealScaleType.Body15Medium, color = c.ink)
                        val binariesStatus = when {
                            !binariesChecked -> "Tap to check native engines."
                            binariesPresent -> "Native engines ready."
                            else -> "Native engines missing — upscale will fall back."
                        }
                        Text(binariesStatus, style = RealScaleType.Body13, color = c.muted)
                    }
                    RealScaleButton(
                        onClick = { probe() },
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Sm,
                        text = if (binariesChecked) "Re-check" else "Check",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Device profile
            deviceProfile?.let { dp ->
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
                        Column(Modifier.weight(1f)) {
                            Text("Device tier", style = RealScaleType.Body15Medium, color = c.ink)
                            Text("${dp.tier.label} · ${dp.ramGb} GB RAM · ${dp.cores} cores",
                                style = RealScaleType.Body13, color = c.muted)
                        }
                        RealScaleBadge(text = "Input ≤ ${dp.inputLimitMP.toInt()} MP", tone = BadgeTone.Neutral)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Appearance
            RealScaleGroup {
                RealScaleSwitchRow(
                    title = "Dynamic color",
                    checked = settings.dynamicColor,
                    onCheckedChange = { checked -> viewModel.updateSettings { it.copy(dynamicColor = checked) } },
                    sub = "Match app colors to your wallpaper (Android 12+).",
                    icon = painterResource(R.drawable.ic_lucide_settings),
                )
                androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                RealScaleSegmented(
                    options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                    selected = settings.theme,
                    onSelect = { value -> viewModel.updateSettings { s -> s.copy(theme = value) } },
                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Behavior
            RealScaleGroup {
                RealScaleSwitchRow(
                    title = "Haptics",
                    checked = settings.haptics,
                    onCheckedChange = { checked -> viewModel.updateSettings { it.copy(haptics = checked) } },
                    sub = "Light feedback on taps.",
                    icon = painterResource(R.drawable.ic_lucide_zap),
                )
                androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                RealScaleSwitchRow(
                    title = "Keep screen on while upscaling",
                    checked = settings.keepAwake,
                    onCheckedChange = { checked -> viewModel.updateSettings { it.copy(keepAwake = checked) } },
                    sub = "Prevents sleep during long jobs.",
                    icon = painterResource(R.drawable.ic_lucide_thermometer),
                )
                androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                RealScaleSwitchRow(
                    title = "Keep EXIF metadata",
                    checked = settings.keepMeta,
                    onCheckedChange = { checked -> viewModel.updateSettings { it.copy(keepMeta = checked) } },
                    sub = "Copy camera info into results.",
                    icon = painterResource(R.drawable.ic_lucide_circle_check),
                )
                androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                RealScaleSwitchRow(
                    title = "Wi‑Fi only downloads",
                    checked = settings.wifiOnly,
                    onCheckedChange = { checked -> viewModel.updateSettings { it.copy(wifiOnly = checked) } },
                    sub = "Avoid downloading models over mobile data.",
                    icon = painterResource(R.drawable.ic_lucide_cloud),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Defaults
            RealScaleGroup {
                Text("Default compute", style = RealScaleType.Body15Medium, color = c.ink)
                RealScaleSegmented(
                    options = listOf("auto" to "Auto", "gpu" to "GPU", "cpu" to "CPU"),
                    selected = settings.compute,
                    onSelect = { value -> viewModel.updateSettings { s -> s.copy(compute = value) } },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                )
                androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                Text("Default format", style = RealScaleType.Body15Medium, color = c.ink, modifier = Modifier.padding(top = 8.dp))
                RealScaleSegmented(
                    options = listOf("auto" to "Auto", "png" to "PNG", "jpg" to "JPEG", "webp" to "WEBP"),
                    selected = settings.format,
                    onSelect = { value -> viewModel.updateSettings { s -> s.copy(format = value) } },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Actions
            RealScaleGroup {
                RealScaleRow(
                    title = "Clear cache",
                    sub = "Remove temporary files. Models and results are kept.",
                    icon = painterResource(R.drawable.ic_lucide_trash_2),
                    onClick = clearCache,
                )
                androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                RealScaleRow(
                    title = "Replay onboarding guide",
                    sub = "Restart the first-run setup.",
                    icon = painterResource(R.drawable.ic_lucide_book_open),
                    onClick = { viewModel.updateSettings { it.copy(onboarded = false) } },
                )
                androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                RealScaleRow(
                    title = "Version",
                    value = BuildConfig.VERSION_NAME,
                    icon = painterResource(R.drawable.ic_lucide_info),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun dirSize(dir: File): Long =
    dir.listFiles()?.sumOf { if (it.isDirectory) dirSize(it) else it.length() } ?: 0

private fun recursiveDelete(dir: File) {
    dir.listFiles()?.forEach { if (it.isDirectory) recursiveDelete(it) else it.delete() }
    dir.delete()
}
