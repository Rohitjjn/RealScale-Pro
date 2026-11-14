package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.Mark
import com.tumuyan.ncnn.realsr.design.RadioMark
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsConfirmSheet
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.Route
import com.tumuyan.ncnn.realsr.ui.state.rememberState

@Composable
fun UpscalesScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    val results = state.results
    var selecting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val selected = state.selectedUpscales

    fun exit() {
        selecting = false
        store.clearUpscaleSelection()
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(
            title = if (selecting) "${selected.size} selected" else "My Upscales",
            onBack = { if (selecting) exit() else store.pop() },
            close = selecting,
            right = {
                if (results.isNotEmpty()) {
                    Text(
                        if (selecting) (if (selected.size == results.size) "Clear" else "Select all") else "Select",
                        style = RsType.body(15f, FontWeight.Medium),
                        color = c.link,
                        modifier = Modifier
                            .clip(RoundedCornerShape(RsRadius.md.dp))
                            .clickable {
                                if (selecting) {
                                    if (selected.size == results.size) store.clearUpscaleSelection()
                                    else results.forEach { if (it.id !in selected) store.toggleSelectedUpscale(it.id) }
                                } else {
                                    selecting = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            },
        )

        if (results.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(80.dp).clip(RoundedCornerShape(RsRadius.lg.dp)).background(c.card),
                    contentAlignment = Alignment.Center,
                ) {
                    Mark(size = 36.dp)
                }
                Text("No upscales yet", style = RsType.display(32f), color = c.ink, modifier = Modifier.padding(top = 24.dp))
                Text(
                    "Every result is saved here and in Pictures/RealScale-Pro on your device.",
                    style = RsType.body(15f, lineHeightRatio = 1.6f),
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                RsButton(
                    onClick = { store.home() },
                    variant = RsButtonVariant.Primary,
                    modifier = Modifier.padding(top = 24.dp),
                    label = "Choose a photo",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(span = { GridItemSpan(3) }) {
                    Text(
                        "${results.size} ${if (results.size == 1) "image" else "images"} · Saved in Pictures/RealScale-Pro",
                        style = RsType.body(13f),
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                items(results, key = { it.id }) { result ->
                    val on = result.id in selected
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(RsRadius.sm.dp))
                            .background(c.card)
                            .clickable {
                                if (selecting) store.toggleSelectedUpscale(result.id)
                                else store.push(Route.Result(result.id))
                            },
                    ) {
                        AsyncImage(
                            model = "file://${result.outputPath}",
                            contentDescription = "${result.fileName}, upscaled ${result.scale} times",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().padding(if (on) 4.dp else 0.dp),
                        )
                        Text(
                            "${result.scale}×",
                            style = RsType.mono(12f),
                            color = c.onDark,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(RsRadius.xs.dp))
                                .background(c.stage.copy(alpha = 0.75f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        if (selecting) {
                            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                                RadioMark(
                                    checked = on,
                                    borderColor = if (on) null else androidx.compose.ui.graphics.Color.White,
                                    fillColor = if (on) null else c.stage.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }

            if (selecting) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RsButton(
                        onClick = {
                            if (selected.size == 1) {
                                val item = results.firstOrNull { it.id == selected.first() }
                                if (item != null) store.shareResult(item)
                            } else {
                                store.toast("Sharing several images uses the system share sheet on a phone.")
                            }
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        icon = LucideIcons.Share2,
                        label = "Share",
                    )
                    RsButton(
                        onClick = { confirm = true },
                        variant = RsButtonVariant.Danger,
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        icon = LucideIcons.Trash2,
                        label = "Delete",
                    )
                }
            }
        }
    }

    RsConfirmSheet(
        open = confirm,
        onClose = { confirm = false },
        title = "Delete ${selected.size} ${if (selected.size == 1) "result" else "results"}?",
        body = "The upscaled files are removed from Pictures/RealScale-Pro. Your original photos are not affected.",
        confirmLabel = "Delete",
        danger = true,
        onConfirm = {
            store.deleteResults(selected)
            confirm = false
            exit()
        },
    )
}

@Composable
fun GalleryScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    var tab by remember { mutableStateOf("photos") }
    var album by remember { mutableStateOf<String?>(null) }

    val albums = remember(state.photos) {
        state.photos.groupBy { it.album }.toList().sortedByDescending { it.second.size }
    }
    val list = album?.let { name -> state.photos.filter { it.album == name } } ?: state.photos
    val showGrid = tab == "photos" || album != null

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(
            title = album ?: "Gallery",
            onBack = { if (album != null) album = null else store.pop() },
        )

        if (!state.photoAccess) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                PhotoAccessCard(store)
                RsButton(
                    onClick = { store.pickFile() },
                    modifier = Modifier.padding(top = 12.dp),
                    full = true,
                    icon = LucideIcons.FolderOpen,
                    label = "Pick with the system picker instead",
                )
            }
            return@Column
        }

        if (album == null) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("photos", "albums").forEach { value ->
                    Text(
                        value.replaceFirstChar { it.uppercase() },
                        style = RsType.body(14f, FontWeight.Medium),
                        color = if (tab == value) c.ink else c.muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(RsRadius.md.dp))
                            .background(if (tab == value) c.card else c.canvas)
                            .clickable { tab = value }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (showGrid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(span = { GridItemSpan(3) }) {
                    Text(
                        "${list.size} ${if (list.size == 1) "photo" else "photos"} · Tap one to upscale",
                        style = RsType.body(13f),
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                items(list, key = { it.id }) { photo ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(RsRadius.sm.dp))
                            .background(c.card)
                            .clickable { store.openConfig(photo) },
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = "Upscale ${photo.name}, ${photo.width} by ${photo.height} pixels",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(albums, key = { it.first }) { (name, photos) ->
                    Column(Modifier.clickable { album = name }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(RsRadius.lg.dp))
                                .background(c.card),
                        ) {
                            AsyncImage(
                                model = photos.first().uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(name, style = RsType.body(15f, FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            "${photos.size} ${if (photos.size == 1) "photo" else "photos"}",
                            style = RsType.body(13f),
                            color = c.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color, size: Int = 6) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}
