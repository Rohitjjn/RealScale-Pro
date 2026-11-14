package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.fmtDims
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.Dot
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsDotTone
import com.tumuyan.ncnn.realsr.design.RsIconButton
import com.tumuyan.ncnn.realsr.design.RsIconButtonVariant
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem
import com.tumuyan.ncnn.realsr.ui.state.Route
import kotlinx.coroutines.launch

/**
 * Full-screen photo viewer: swipe between photos, pinch to zoom, tap to hide the chrome — the
 * dark chrome of the prototype, not a light screen.
 */
@Composable
fun ViewerScreen(store: AppStore, photoId: String, single: Boolean) {
    val c = LocalRsColors.current
    val state = store.state.value
    val list = remember(state.photos, single, photoId) {
        if (single) state.photos.filter { it.id == photoId } else state.photos
    }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().background(c.stage))
        return
    }
    val start = list.indexOfFirst { it.id == photoId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { list.size }
    val scope = rememberCoroutineScope()
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var pendingMode by remember { mutableStateOf<ModeId?>(null) }
    val photo = list[pager.currentPage.coerceIn(0, list.size - 1)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomablePhoto(
                photo = list[page],
                onTap = { chrome = !chrome },
                onZoomChange = { zoomed = it },
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(c.stage.copy(alpha = 0.88f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RsIconButton(
                        icon = LucideIcons.ChevronLeft,
                        label = "Back",
                        variant = RsIconButtonVariant.Dark,
                        onClick = { store.pop() },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(photo.name, style = RsType.mono(13f), color = c.onDark, maxLines = 1)
                        Text(
                            "${fmtDims(photo.width, photo.height)} · ${fmtMB(photo.sizeMB)} · ${photo.dateLabel}",
                            style = RsType.body(12f),
                            color = c.onDarkSoft,
                            maxLines = 1,
                        )
                    }
                    if (list.size > 1) {
                        Text(
                            "${pager.currentPage + 1}/${list.size}",
                            style = RsType.mono(12f).copy(fontFeatureSettings = "tnum"),
                            color = c.onDarkSoft,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, c.stage.copy(alpha = 0.9f))))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp),
            ) {
                if (single) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RsIconButton(
                            icon = LucideIcons.Share2,
                            label = "Share",
                            variant = RsIconButtonVariant.Dark,
                            size = 52.dp,
                            onClick = { store.sharePhoto(photo) },
                        )
                        RsButton(
                            onClick = { store.pop() },
                            variant = RsButtonVariant.Dark,
                            size = RsButtonSize.Lg,
                            modifier = Modifier.weight(1f),
                            label = "Done",
                        )
                    }
                } else {
                    Row(
                        Modifier
                            .heightIn(min = 36.dp)
                            .clip(CircleShape)
                            .background(c.darkElevated.copy(alpha = 0.9f))
                            .pointerInput(state.mode) {
                                detectTapGestures {
                                    pendingMode = if (state.mode == ModeId.UPSCALE) ModeId.SUPER else ModeId.UPSCALE
                                }
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Dot(RsDotTone.Good)
                        Text(
                            if (state.mode == ModeId.SUPER) "Super Resolution" else "Upscale Mode",
                            style = RsType.body(13f, androidx.compose.ui.text.font.FontWeight.Medium),
                            color = c.onDark,
                        )
                        Icon(LucideIcons.ArrowLeftRight, contentDescription = null, tint = c.onDarkSoft, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RsIconButton(
                            icon = LucideIcons.Info,
                            label = "Details",
                            variant = RsIconButtonVariant.Dark,
                            size = 52.dp,
                            onClick = { sheet = "details" },
                        )
                        RsIconButton(
                            icon = LucideIcons.Share2,
                            label = "Share",
                            variant = RsIconButtonVariant.Dark,
                            size = 52.dp,
                            onClick = { store.sharePhoto(photo) },
                        )
                        RsButton(
                            onClick = { store.openConfig(photo) },
                            variant = RsButtonVariant.Primary,
                            size = RsButtonSize.Lg,
                            modifier = Modifier.weight(1f),
                            icon = LucideIcons.ImageUpscale,
                            label = "Upscale",
                        )
                    }
                }
            }
        }

        PhotoDetailsSheet(
            store = store,
            photo = photo,
            open = sheet == "details",
            onClose = { sheet = null },
        )
        ShareSheet(store = store, photo = photo, open = sheet == "share", onClose = { sheet = null })
        ModeSwitchSheet(
            store = store,
            pending = pendingMode,
            onDismiss = { pendingMode = null },
        )
    }
}

@Composable
private fun ZoomablePhoto(photo: PhotoItem, onTap: () -> Unit, onZoomChange: (Boolean) -> Unit) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(photo.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(photo.id) { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        val next = if (scale > 1.05f) 1f else 2.5f
                        scale = next
                        if (next == 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                        onZoomChange(next > 1.05f)
                    },
                )
            }
            .pointerInput(photo.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 4f)
                    scale = next
                    if (next > 1.01f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    onZoomChange(next > 1.05f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
        )
    }
}
