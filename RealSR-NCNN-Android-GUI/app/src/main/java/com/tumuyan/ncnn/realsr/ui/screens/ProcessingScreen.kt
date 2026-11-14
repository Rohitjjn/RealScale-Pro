package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.ProcessingStage
import com.tumuyan.ncnn.realsr.core.engine.RecoveryAction
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.DetailList
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleConfirmSheet
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleProgressBar
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRing
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSheet
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.state.HapticKind
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.launch

/**
 * Processing screen — renders the single source of truth: [EngineState].
 * ETA is nullable (“Estimating…” until the tracker has samples); stage drives the label;
 * Back leaves the job running in the background; Cancel confirms then stops it.
 */
@Composable
fun ProcessingScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val scope = rememberCoroutineScope()
    val state by viewModel.engineState.collectAsState()
    var cancelConfirm by remember { mutableStateOf(false) }
    var failedShow by remember { mutableStateOf(false) }

    val running = state as? EngineState.Running
    val preparing = state as? EngineState.Preparing
    val completed = state as? EngineState.Completed
    val failed = state as? EngineState.Failed

    val pct = (running?.progressFraction ?: 0f) * 100f
    val etaLabel = when {
        running?.etaMillis == null -> "Estimating…"
        running?.etaMillis == 0L -> "Just a moment…"
        else -> "${fmtDuration(running!!.etaMillis)} remaining"
    }
    val stageLabel = running?.stage?.humanLabel ?: when {
        preparing != null -> "Preparing"
        completed != null -> "Finished"
        failed != null -> "Failed"
        else -> "Ready"
    }

    fun cancelJob() {
        cancelConfirm = true
        viewModel.haptic(HapticKind.LIGHT)
    }

    fun doCancel() {
        cancelConfirm = false
        viewModel.engine.cancel()
        viewModel.showToast("Upscale cancelled")
        navController.popBackStack()
    }

    fun retry() {
        when (failed) {
            is EngineState.Failed -> {
                failed.recoveryActions.forEach { action ->
                    if (viewModel.engine.retryWithRecovery(action)) {
                        viewModel.showToast("Retrying: ${action.label}")
                        return@forEach
                    }
                }
                viewModel.showToast("Couldn’t retry automatically — try again from Config.")
            }
            else -> Unit
        }
    }

    fun openResult() {
        if (completed != null) {
            viewModel.acknowledgeResult()
            navController.navigate(Routes.RESULT)
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
                    Text("Processing", style = RealScaleType.Display24, color = c.ink)
                    Spacer(Modifier.weight(1f))
                    when {
                        running != null -> RealScaleIconButton(
                            onClick = { cancelJob() },
                            variant = IconButtonVariant.Dark,
                            contentDescription = "Cancel",
                            icon = painterResource(R.drawable.ic_lucide_x),
                        )
                        completed != null -> RealScaleIconButton(
                            onClick = { openResult() },
                            variant = IconButtonVariant.Dark,
                            contentDescription = "View result",
                            icon = painterResource(R.drawable.ic_lucide_chevron_right),
                        )
                        else -> RealScaleIconButton(
                            onClick = { navController.popBackStack() },
                            variant = IconButtonVariant.Dark,
                            contentDescription = "Close",
                            icon = painterResource(R.drawable.ic_lucide_x),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairlineSoft))
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state is EngineState.Idle -> {
                    Column {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_circle_check),
                            contentDescription = null,
                            tint = c.ink,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        Text("Ready to upscale", style = RealScaleType.Display34, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("No job is running. Pick a photo and start an upscale from the home screen.",
                            style = RealScaleType.Body15, color = c.muted)
                        Spacer(Modifier.height(24.dp))
                        RealScaleButton(
                            onClick = { navController.popBackStack() },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Lg,
                            text = "Go back",
                        )
                    }
                }
                preparing != null -> {
                    Column {
                        RealScaleRing(progress = 0f, size = 56.dp, contentDescription = "Preparing")
                        Spacer(Modifier.height(20.dp))
                        Text(preparing.label, style = RealScaleType.Display34, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("Getting the model and pipeline ready.", style = RealScaleType.Body15, color = c.muted)
                    }
                }
                running != null -> {
                    Column {
                        RealScaleRing(progress = running.progressFraction, size = 56.dp,
                            contentDescription = "Upscaling progress $pct percent")
                        Spacer(Modifier.height(20.dp))
                        Text("${pct.toInt()}%", style = RealScaleType.Display34, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text(stageLabel, style = RealScaleType.Body16Medium, color = c.body)
                        Text("$etaLabel · ${running.currentFileName}", style = RealScaleType.Body13,
                            color = c.muted, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(24.dp))
                        RealScaleProgressBar(progress = running.progressFraction,
                            modifier = Modifier.fillMaxWidth().width(320.dp))
                        Spacer(Modifier.height(16.dp))
                        RealScaleButton(
                            onClick = { cancelJob() },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Lg,
                            text = "Cancel",
                            modifier = Modifier.fillMaxWidth().width(320.dp),
                        )
                        if (running.latestStatusLine.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(running.latestStatusLine, style = RealScaleType.Mono12, color = c.mutedSoft)
                        }
                    }
                }
                completed != null -> {
                    Column {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_circle_check),
                            contentDescription = null,
                            tint = c.ink,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        Text("Upscale finished", style = RealScaleType.Display34, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("${completed.result.creations.size} image${if (completed.result.creations.size != 1) "s" else ""} " +
                            "saved in ${fmtDuration(completed.result.totalDurationMs)}.",
                            style = RealScaleType.Body15, color = c.muted)
                        Spacer(Modifier.height(24.dp))
                        RealScaleButton(
                            onClick = { openResult() },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Lg,
                            text = "View result",
                            modifier = Modifier.fillMaxWidth().width(320.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        RealScaleButton(
                            onClick = { navController.popBackStack() },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Lg,
                            text = "Done",
                            modifier = Modifier.fillMaxWidth().width(320.dp),
                        )
                    }
                }
                failed != null -> {
                    Column {
                        Icon(
                            painter = painterResource(R.drawable.ic_lucide_circle_alert),
                            contentDescription = null,
                            tint = c.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        Text("Upscale failed", style = RealScaleType.Display34, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text(failed.userMessage, style = RealScaleType.Body15, color = c.muted)
                        Spacer(Modifier.height(16.dp))
                        if (failed.recoveryActions.isNotEmpty()) {
                            RealScaleGroup {
                                failed.recoveryActions.forEach { action ->
                                    RealScaleButton(
                                        onClick = { retry() },
                                        variant = ButtonVariant.Secondary,
                                        size = ButtonSize.Sm,
                                        text = action.label,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        RealScaleButton(
                            onClick = { failedShow = true },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Sm,
                            text = "Technical details",
                        )
                        Spacer(Modifier.height(16.dp))
                        RealScaleButton(
                            onClick = { navController.popBackStack() },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Lg,
                            text = "Back",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                state == EngineState.Cancelled -> {
                    Column {
                        Text("Cancelled", style = RealScaleType.Display34, color = c.ink)
                        Spacer(Modifier.height(8.dp))
                        Text("The upscale was stopped.", style = RealScaleType.Body15, color = c.muted)
                        Spacer(Modifier.height(24.dp))
                        RealScaleButton(
                            onClick = { navController.popBackStack() },
                            variant = ButtonVariant.Primary,
                            size = ButtonSize.Lg,
                            text = "Back",
                        )
                    }
                }
            }
        }
    }

    RealScaleConfirmSheet(
        open = cancelConfirm,
        onClose = { cancelConfirm = false },
        title = "Cancel upscale?",
        body = "Stop the current upscale? Partial results won’t be saved.",
        confirmLabel = "Cancel",
        onConfirm = { doCancel() },
        cancelLabel = "Keep going",
    )

    RealScaleSheet(
        open = failedShow,
        onClose = { failedShow = false },
        title = "Technical details",
        footer = {
            RealScaleButton(onClick = { failedShow = false }, variant = ButtonVariant.Secondary, full = true, text = "Close")
            Spacer(Modifier.height(4.dp))
        },
    ) {
        DetailList(
            rows = listOf(
                "Message" to (failed?.userMessage ?: ""),
                "Technical output" to (failed?.technicalOutput ?: "").take(500),
            ),
        )
    }
}
