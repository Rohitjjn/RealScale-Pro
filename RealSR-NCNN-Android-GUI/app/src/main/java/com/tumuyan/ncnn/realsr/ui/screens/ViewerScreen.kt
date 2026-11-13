package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.DetailList
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSheet
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/**
 * Full-bleed viewer for one photo. Pinch to zoom (1–4×), drag to pan,
 * double-tap to toggle zoom, tap to show/hide chrome. Back / the button returns.
 */
@Composable
fun ViewerScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val photo by viewModel.viewerTarget.collectAsState()
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val current = photo
    if (current == null) {
        Box(Modifier.fillMaxSize().background(c.stage), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No photo selected.", style = RealScaleType.Display24, color = c.onDark)
                Spacer(Modifier.height(16.dp))
                RealScaleButton(onClick = { navController.popBackStack() }, variant = ButtonVariant.Dark, size = ButtonSize.Lg, text = "Back")
            }
        }
        return
    }

    fun clampOffset(raw: Offset, targetScale: Float): Offset {
        val maxX = 1200f * (targetScale - 1f)
        val maxY = 1800f * (targetScale - 1f)
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    val animScale by animateFloatAsState(targetValue = scale, label = "zoom")
    val animOffset by androidx.compose.animation.core.animateOffsetAsState(targetValue = offset, label = "pan")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.stage)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val next = (scale * zoomChange).coerceIn(1f, 4f)
                    zoomed = next > 1.02f
                    offset = clampOffset(offset + pan * next, next)
                    scale = next
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                        zoomed = scale > 1f
                    },
                    onTap = { chrome = !chrome },
                )
            },
    ) {
        AsyncImage(
            model = current.uri,
            contentDescription = current.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animScale
                    scaleY = animScale
                    translationX = animOffset.x
                    translationY = animOffset.y
                },
        )

        if (chrome) {
            // Top bar
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.stage.copy(alpha = 0.85f), Color.Transparent))),
            ) {
                androidx.compose.foundation.layout.Box(Modifier.padding(top = 0.dp)) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 12.dp, top = 40.dp) // status bar
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RealScaleIconButton(
                                onClick = { navController.popBackStack() },
                                variant = IconButtonVariant.Dark,
                                contentDescription = "Back",
                                icon = painterResource(R.drawable.ic_lucide_chevron_left),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(current.name, style = RealScaleType.Mono13, color = c.onDark, maxLines = 1)
                                Text(
                                    "${current.width} × ${current.height} · ${com.tumuyan.ncnn.realsr.ui.screens.fmtBytesMB(current.sizeBytes)}" +
                                        (current.dateTakenMs?.let { " · ${fmtDateTime(it)}" } ?: ""),
                                    style = RealScaleType.Caption12,
                                    color = c.onDarkSoft,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, c.stage.copy(alpha = 0.9f))))
                    .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RealScaleIconButton(
                    onClick = { detailsOpen = true },
                    variant = IconButtonVariant.Dark,
                    contentDescription = "Photo details",
                    icon = painterResource(R.drawable.ic_lucide_info),
                )
                RealScaleIconButton(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = current.mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, current.uri)
                            clipData = android.content.ClipData.newRawUri("Photo", current.uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Share photo")) }
                            .onFailure { viewModel.showToast("No app available to share this photo.") }
                    },
                    variant = IconButtonVariant.Dark,
                    contentDescription = "Share photo",
                    icon = painterResource(R.drawable.ic_lucide_share_2),
                )
                RealScaleButton(
                    onClick = {
                        viewModel.setPendingInput(current)
                        navController.navigate(Routes.CONFIG)
                    },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Lg,
                    icon = painterResource(R.drawable.ic_lucide_image_upscale),
                    text = "Upscale",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    RealScaleSheet(open = detailsOpen, onClose = { detailsOpen = false }, title = "Details") {
        DetailList(
            rows = listOf(
                "Name" to current.name,
                "Resolution" to "${current.width} × ${current.height} · ${String.format(java.util.Locale.US, "%.1f", current.megapixels)} MP",
                "File size" to com.tumuyan.ncnn.realsr.ui.screens.fmtBytesMB(current.sizeBytes),
                "Format" to current.extension.uppercase(),
                "Taken" to (current.dateTakenMs?.let { fmtDateTime(it) } ?: "Unknown"),
                "Album" to (current.bucketName ?: "—"),
            ),
        )
        Spacer(Modifier.height(8.dp))
    }
}
