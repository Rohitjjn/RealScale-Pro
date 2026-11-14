package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.device.HapticKind
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.design.Dot
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.Mark
import com.tumuyan.ncnn.realsr.design.RadioMark
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsDotTone
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsIconButton
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsRow
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.Skeleton
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem
import com.tumuyan.ncnn.realsr.ui.state.Route

private val MODES = listOf(
    Triple(ModeId.UPSCALE, "Upscale Mode", "Fast and simple. Everyday photos. Works on any device."),
    Triple(ModeId.SUPER, "Super Resolution Mode", "Maximum quality. Original file, no compression. For powerful devices."),
)

@Composable
fun HomeScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.state.value
    var sheet by remember { mutableStateOf<String?>(null) }
    var pendingMode by remember { mutableStateOf<ModeId?>(null) }
    val scroll = rememberScrollState()
    val scrolled = scroll.value > 56

    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Mark(size = 36.dp)
                Text(
                    "RealScale-Pro",
                    style = RsType.display(22f, FontWeight.SemiBold, 1f),
                    color = c.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                RsIconButton(icon = LucideIcons.Layers, label = "Models", onClick = { store.push(Route.Models) })
                RsIconButton(icon = LucideIcons.Settings, label = "Settings", onClick = { store.push(Route.Settings) })
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(if (scrolled) c.hairline else androidx.compose.ui.graphics.Color.Transparent))

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
            ) {
                Text(
                    "Sharper, bigger photos. Nothing leaves your device.",
                    style = RsType.body(15f, lineHeightRatio = 1.6f),
                    color = c.muted,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                )

                /* 2. Recent photos */
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text("Recent photos", style = RsType.display(26f), color = c.ink)
                        Row(
                            Modifier
                                .padding(top = 8.dp)
                                .heightIn(min = 28.dp)
                                .clip(RoundedCornerShape(RsRadius.full.dp))
                                .background(c.card)
                                .clickable { pendingMode = null }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Dot(RsDotTone.Good)
                            Text(
                                if (state.mode == ModeId.SUPER) "Super Resolution" else "Upscale Mode",
                                style = RsType.body(13f, FontWeight.Medium),
                                color = c.ink,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.photoAccess) {
                        RsButton(
                            onClick = { store.push(Route.Gallery) },
                            variant = RsButtonVariant.Ghost,
                            size = RsButtonSize.Sm,
                            label = "See all",
                            icon = LucideIcons.ChevronRight,
                        )
                    }
                }

                if (!state.photoAccess) {
                    PhotoAccessCard(store, Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                } else {
                    Row(
                        Modifier
                            .padding(top = 16.dp, bottom = 4.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.photosLoading && state.photos.isEmpty()) {
                            repeat(4) { index ->
                                Column(Modifier.width(148.dp)) {
                                    Skeleton(
                                        Modifier.fillMaxWidth().aspectRatio(0.8f),
                                        shape = RoundedCornerShape(RsRadius.lg.dp),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Skeleton(Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(RsRadius.md.dp))
                                }
                            }
                        } else {
                            state.photos.forEach { photo ->
                                PhotoCard(store, photo)
                            }
                        }
                    }
                }

                /* 3. Choose another image */
                Text(
                    "Choose another image",
                    style = RsType.display(26f),
                    color = c.ink,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp),
                )
                val tiles = listOf(
                    Quad(LucideIcons.Images, "Gallery", "Albums and all photos") { store.push(Route.Gallery) },
                    Quad(LucideIcons.FolderOpen, "Files", "Browse folders") { store.pickFile() },
                    Quad(LucideIcons.Camera, "Camera", "Take a photo") {
                        if (!state.cameraAsked) sheet = "camera" else store.takePhoto()
                    },
                    Quad(LucideIcons.Ellipsis, "More", "Clipboard, cloud, other apps") { sheet = "more" },
                )
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    tiles.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { tile -> TileCard(tile, Modifier.weight(1f)) }
                        }
                    }
                }

                /* 4. Mode selector */
                Text(
                    "Mode",
                    style = RsType.display(26f),
                    color = c.ink,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp),
                )
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MODES.forEach { (id, title, desc) ->
                        val on = state.mode == id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RsRadius.md.dp))
                                .background(if (on) c.inverse else c.canvas)
                                .border(1.dp, if (on) c.inverse else c.hairline, RoundedCornerShape(RsRadius.md.dp))
                                .clickable(role = Role.RadioButton) {
                                    if (id == state.mode) return@clickable
                                    if (id == ModeId.SUPER && !state.superIntroSeen) pendingMode = id
                                    else store.setMode(id)
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        title,
                                        style = RsType.body(16f, FontWeight.Medium),
                                        color = if (on) c.onInverse else c.ink,
                                    )
                                    if (id == ModeId.SUPER) RsBadge("Pro", tone = RsBadgeTone.Amber)
                                }
                                Text(
                                    desc,
                                    style = RsType.body(14f, lineHeightRatio = 1.55f),
                                    color = if (on) c.onInverseSoft else c.muted,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            RadioMark(
                                checked = on,
                                modifier = Modifier.padding(top = 2.dp),
                                borderColor = if (on) c.onInverse else null,
                                fillColor = if (on) c.onInverse else null,
                                tint = if (on) c.inverse else null,
                            )
                        }
                    }
                }

                /* 5. App options */
                Column(Modifier.padding(horizontal = 20.dp, vertical = 40.dp)) {
                    RsGroup {
                        RsRow(
                            title = "My Upscales",
                            icon = LucideIcons.Images,
                            value = if (state.results.isEmpty()) "Empty" else state.results.size.toString(),
                            chevron = true,
                            topDivider = false,
                            onClick = { store.push(Route.Upscales) },
                        )
                        RsRow(
                            title = "Model Manager",
                            icon = LucideIcons.Layers,
                            value = "${state.installed.size} installed",
                            chevron = true,
                            onClick = { store.push(Route.Models) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    RsGroup {
                        RsRow(title = "Settings", icon = LucideIcons.Settings, chevron = true, topDivider = false, onClick = { store.push(Route.Settings) })
                        RsRow(title = "Guide", icon = LucideIcons.BookOpen, chevron = true, onClick = { store.replayGuide() })
                        RsRow(title = "Help and feedback", icon = LucideIcons.CircleHelp, chevron = true, onClick = { store.push(Route.Help) })
                        RsRow(
                            title = "About and open-source licenses",
                            icon = LucideIcons.Info,
                            chevron = true,
                            onClick = { store.push(Route.About) },
                        )
                    }
                    Text(
                        "RealScale-Pro 2.0 · Works fully offline",
                        style = RsType.body(13f),
                        color = c.muted,
                        modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        RsSheet(open = pendingMode != null, onClose = { pendingMode = null }, title = "Super Resolution Mode") {
            Text(
                "Super Resolution keeps your original file untouched, uses the heaviest models and takes longer. It is best on powerful devices.",
                style = RsType.body(15f, lineHeightRatio = 1.6f),
                color = LocalRsColors.current.body,
            )
            Spacer(Modifier.height(16.dp))
            RsButton(
                onClick = {
                    store.markSuperIntroSeen()
                    store.setMode(ModeId.SUPER)
                    pendingMode = null
                },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                label = "Use Super Resolution",
            )
            Spacer(Modifier.height(8.dp))
            RsButton(onClick = { pendingMode = null }, variant = RsButtonVariant.Ghost, full = true, label = "Stay on Upscale Mode")
        }

        RsSheet(open = sheet == "more", onClose = { sheet = null }, title = "More ways to import") {
            RsGroup(Modifier.padding(bottom = 16.dp)) {
                RsRow(
                    title = "Paste from clipboard",
                    sub = "Use an image you copied",
                    icon = LucideIcons.Clipboard,
                    chevron = true,
                    topDivider = false,
                    onClick = {
                        sheet = null
                        store.pasteFromClipboard()
                    },
                )
                RsRow(
                    title = "Cloud drives and other folders",
                    sub = "Opens the system picker",
                    icon = LucideIcons.Cloud,
                    chevron = true,
                    onClick = {
                        sheet = null
                        store.pickFile()
                    },
                )
                RsRow(
                    title = "Shared from other apps",
                    sub = "Send an image to RealScale-Pro from any app",
                    icon = LucideIcons.Share2,
                    trailing = { RsBadge("Later", tone = RsBadgeTone.Outline) },
                )
            }
        }

        RsSheet(
            open = sheet == "camera",
            onClose = { sheet = null },
            title = "Use the camera",
            footer = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RsButton(
                        onClick = {
                            store.markCameraAsked()
                            sheet = null
                            store.takePhoto()
                        },
                        variant = RsButtonVariant.Primary,
                        size = RsButtonSize.Lg,
                        full = true,
                        label = "Allow camera",
                    )
                    RsButton(onClick = { sheet = null }, variant = RsButtonVariant.Ghost, full = true, label = "Not now")
                }
            },
        ) {
            Text(
                "Take a photo and upscale it right away. The camera is used only while you are taking the picture.",
                style = RsType.body(15f, lineHeightRatio = 1.6f),
                color = LocalRsColors.current.body,
            )
            Text(
                "Photos you take stay on this device.",
                style = RsType.body(15f, lineHeightRatio = 1.6f),
                color = LocalRsColors.current.body,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

private data class Quad(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val sub: String,
    val onClick: () -> Unit,
)

@Composable
private fun TileCard(tile: Quad, modifier: Modifier = Modifier) {
    val c = LocalRsColors.current
    Column(
        modifier
            .heightIn(min = 108.dp)
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.card)
            .clickable { tile.onClick() }
            .padding(16.dp),
    ) {
        Icon(tile.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        Text(tile.title, style = RsType.body(15f, FontWeight.Medium), color = c.ink)
        Text(tile.sub, style = RsType.body(13f, lineHeightRatio = 1.35f), color = c.body, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PhotoCard(store: AppStore, photo: PhotoItem) {
    val c = LocalRsColors.current
    Column(Modifier.width(148.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(RsRadius.lg.dp))
                .background(c.card)
                .clickable { store.openViewer(photo) },
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = "View ${photo.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                "${photo.width}×${photo.height}",
                style = RsType.mono(12f),
                color = c.onDark,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(RsRadius.xs.dp))
                    .background(c.stage.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        RsButton(
            onClick = {
                store.haptic(HapticKind.Light)
                store.openConfig(photo)
            },
            modifier = Modifier.padding(top = 8.dp),
            full = true,
            label = "Upscale",
            icon = LucideIcons.ImageUpscale,
        )
    }
}

@Composable
fun PhotoAccessCard(store: AppStore, modifier: Modifier = Modifier) {
    val c = LocalRsColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.card)
            .padding(20.dp),
    ) {
        Icon(LucideIcons.Images, contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
        Text("Allow photo access", style = RsType.body(16f, FontWeight.Medium), color = c.ink, modifier = Modifier.padding(top = 12.dp))
        Text(
            "See your latest photos here and upscale one in a single tap. Gallery, Files and Camera work without it.",
            style = RsType.body(14f, lineHeightRatio = 1.55f),
            color = c.body,
            modifier = Modifier.padding(top = 4.dp),
        )
        RsButton(
            onClick = { store.requestPhotoPermission() },
            variant = RsButtonVariant.Primary,
            modifier = Modifier.padding(top = 16.dp),
            label = "Allow photo access",
        )
    }
}
