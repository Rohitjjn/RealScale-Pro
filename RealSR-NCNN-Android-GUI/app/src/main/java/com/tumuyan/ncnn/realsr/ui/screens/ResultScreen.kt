package com.tumuyan.ncnn.realsr.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.ui.MediaExportHelper
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.DetailList
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleBeforeAfter
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.launch
import java.io.File

/** Result screen — before/after compare, details, share. Shows the last completed creation. */
@Composable
fun ResultScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by viewModel.lastResult.collectAsState()

    val creation: RecentCreationEntity? = result?.creations?.firstOrNull()
    val beforeUri: Uri? = creation?.sourceUri?.let { Uri.parse(it) }
    val afterUri: Uri? = creation?.savedUri?.let { Uri.parse(it) }
        ?: creation?.outputPath?.let { Uri.fromFile(File(it)) }

    if (creation == null) {
        Box(Modifier.fillMaxSize().background(c.canvas), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No result yet", style = RealScaleType.Display24, color = c.ink)
                Spacer(Modifier.height(16.dp))
                RealScaleButton(onClick = { navController.popBackStack() }, variant = ButtonVariant.Primary, size = ButtonSize.Lg, text = "Back")
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding()) {
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
                    Text("Result", style = RealScaleType.Display24, color = c.ink)
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
            // Before / after
            RealScaleBeforeAfter(
                beforeModel = beforeUri,
                afterModel = afterUri,
                altDescription = "Before and after upscale: ${creation.presetLabel}",
                modifier = Modifier.fillMaxWidth().height(520.dp),
            )
            Spacer(Modifier.height(20.dp))

            // Details
            DetailList(
                rows = listOf(
                    "Preset" to creation.presetLabel,
                    "Model" to creation.modelId,
                    "Scale" to "${creation.scale}×",
                    "Input" to "${creation.inputWidth} × ${creation.inputHeight}",
                    "Output" to "${creation.outputWidth} × ${creation.outputHeight}",
                    "Time" to fmtDuration(creation.durationMs),
                    "Saved" to fmtDateTime(creation.createdAtEpochMs),
                ),
            )
            Spacer(Modifier.height(24.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RealScaleButton(
                    onClick = {
                        afterUri?.path?.let { path ->
                            scope.launch {
                                MediaExportHelper.shareImage(context, java.io.File(path))
                            }
                        }
                    },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Lg,
                    icon = painterResource(R.drawable.ic_lucide_share_2),
                    text = "Share",
                    modifier = Modifier.weight(1f),
                    enabled = afterUri?.scheme == "file",
                )
                RealScaleButton(
                    onClick = { viewModel.acknowledgeResult(); navController.popBackStack() },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Lg,
                    text = "Done",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Saved to Pictures/RealScale-Pro. Your original photo is unchanged.",
                style = RealScaleType.Body13, color = c.muted,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
