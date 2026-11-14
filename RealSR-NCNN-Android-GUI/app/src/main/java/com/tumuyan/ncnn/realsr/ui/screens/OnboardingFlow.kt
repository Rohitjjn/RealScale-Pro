package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.engine.planner.ImageType
import com.tumuyan.ncnn.realsr.core.engine.planner.ModeId
import com.tumuyan.ncnn.realsr.core.engine.planner.STARTER_IDS
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.ModelRow
import com.tumuyan.ncnn.realsr.ui.components.ModelRowVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleBadge
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDivider
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleMark
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRing
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.state.HapticKind
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.launch

private const val PAGE_WELCOME = 0
private const val PAGE_DEVICE = 1
private const val PAGE_MODELS = 2
private const val PAGE_FINISH = 3
private const val PAGE_COUNT = 4

/** First-run flow: welcome → real device check → starter model downloads → finish. */
@Composable
fun OnboardingFlow(viewModel: AppViewModel) {
    val c = LocalRealScaleColors.current

    @Composable
    fun deviceRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = RealScaleType.Body13, color = c.muted)
            Spacer(Modifier.weight(1f))
            Text(value, style = RealScaleType.Body15Medium, color = c.ink)
        }
    }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(PAGE_WELCOME) }
    val catalog by viewModel.modelCatalog.collectAsState()
    val deviceProfile by viewModel.deviceProfile.collectAsState()
    val tierLevel = deviceProfile?.tier?.level ?: 1

    val starterStates = STARTER_IDS.mapNotNull { id ->
        catalog.firstOrNull { it.catalog.id == id }
    }

    fun next() {
        if (page < PAGE_COUNT - 1) {
            page++
            viewModel.haptic(HapticKind.TICK)
        } else {
            viewModel.completeOnboarding()
        }
    }

    fun back() {
        if (page > PAGE_WELCOME) page--
    }

    fun download(id: String) {
        scope.launch { viewModel.modelRepository.startOrResumeDownload(id) }
    }

    fun cancel(id: String) {
        scope.launch { viewModel.modelRepository.cancelDownload(id) }
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
                    if (page > PAGE_WELCOME) {
                        RealScaleIconButton(
                            onClick = { back() },
                            variant = IconButtonVariant.Dark,
                            contentDescription = "Back",
                            icon = androidx.compose.ui.res.painterResource(com.tumuyan.ncnn.realsr.R.drawable.ic_lucide_chevron_left),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Step ${page + 1} of $PAGE_COUNT", style = RealScaleType.Caption12, color = c.muted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (page) {
                PAGE_WELCOME -> {
                    RealScaleMark(size = 56.dp)
                    Spacer(Modifier.height(28.dp))
                    Text("Welcome to RealScale-Pro", style = RealScaleType.Display34, color = c.ink)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Photos get sharper and bigger right on your device. No upload, no account, " +
                            "no cloud. Models download once and work offline.",
                        style = RealScaleType.Body16,
                        color = c.body,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                PAGE_DEVICE -> {
                    Text("Your device", style = RealScaleType.Display28, color = c.ink)
                    Spacer(Modifier.height(16.dp))
                    val dp = deviceProfile
                    if (dp == null) {
                        Text("Probing device…", style = RealScaleType.Body15, color = c.muted)
                        scope.launch { viewModel.probeBinariesThenRefresh() }
                    } else {
                        deviceRow("Tier", dp.tier.label)
                        deviceRow("RAM", "${dp.ramGb} GB")
                        deviceRow("CPU cores", dp.cores.toString())
                        deviceRow("Native binaries", if (dp.binariesPresent) "Ready" else "Missing")
                        deviceRow("Storage free", "${dp.freeStorageGb.toInt()} GB")
                    }
                }
                PAGE_MODELS -> {
                    Text("Starter models", style = RealScaleType.Display28, color = c.ink)
                    Text("Three models download once, then everything works offline.", style = RealScaleType.Body15, color = c.muted, modifier = Modifier.padding(bottom = 16.dp))
                    starterStates.forEach { state ->
                        val m = state.catalog
                        val downloading = state.status in listOf(ModelInstallStatus.DOWNLOADING, ModelInstallStatus.QUEUED, ModelInstallStatus.VERIFYING, ModelInstallStatus.PAUSED)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, style = RealScaleType.Body15Medium, color = c.ink)
                                Text(m.description, style = RealScaleType.Body13, color = c.body, modifier = Modifier.padding(top = 2.dp))
                            }
                            when {
                                state.isInstalled -> RealScaleBadge(text = "Installed", tone = com.tumuyan.ncnn.realsr.ui.components.BadgeTone.Strong)
                                downloading -> RealScaleRing(progress = state.progressFraction, size = 26.dp, contentDescription = "Downloading")
                                else -> RealScaleButton(
                                    onClick = { download(m.id) },
                                    variant = ButtonVariant.Secondary,
                                    size = ButtonSize.Sm,
                                    text = com.tumuyan.ncnn.realsr.ui.screens.fmtBytesMB(m.totalBytes),
                                )
                            }
                            if (downloading) {
                                RealScaleButton(
                                    onClick = { cancel(m.id) },
                                    variant = ButtonVariant.Secondary,
                                    size = ButtonSize.Sm,
                                    text = "Cancel",
                                )
                            }
                        }
                        if (state != starterStates.last()) RealScaleDivider()
                    }
                    if (starterStates.all { it.isInstalled }) {
                        Spacer(Modifier.height(12.dp))
                        Text("Starter models ready.", style = RealScaleType.Body14, color = c.ink)
                    }
                }
                PAGE_FINISH -> {
                    RealScaleIconButton(
                        onClick = {},
                        variant = IconButtonVariant.Dark,
                        contentDescription = "Done",
                        icon = painterResource(R.drawable.ic_lucide_circle_check),
                    )
                    Spacer(Modifier.height(28.dp))
                    Text("You're all set", style = RealScaleType.Display34, color = c.ink)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Pick a photo from the home screen and upscale it. Everything runs on your device.",
                        style = RealScaleType.Body16,
                        color = c.body,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        // Page dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(PAGE_COUNT) { i ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (i == page) c.ink else c.mutedSoft),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > PAGE_WELCOME) {
                RealScaleButton(
                    onClick = { back() },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Sm,
                    text = "Back",
                )
            }
            Spacer(Modifier.weight(1f))
            if (page == PAGE_MODELS && !starterStates.all { it.isInstalled }) {
                RealScaleButton(
                    onClick = { next() },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Lg,
                    text = "Skip",
                    enabled = starterStates.any { it.isInstalled },
                )
            }
            RealScaleButton(
                onClick = { next() },
                variant = ButtonVariant.Primary,
                size = ButtonSize.Lg,
                text = when (page) {
                    PAGE_FINISH -> "Start using RealScale-Pro"
                    else -> "Next"
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
