package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.BuildConfig
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.media.GalleryImage
import com.tumuyan.ncnn.realsr.core.media.GalleryMediaStore
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.BadgeTone
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.PhotoAccessCard
import com.tumuyan.ncnn.realsr.ui.components.RealScaleBadge
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleMark
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRadioMark
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSheet
import com.tumuyan.ncnn.realsr.ui.components.rememberCameraCapture
import com.tumuyan.ncnn.realsr.ui.components.rememberImageFilePicker
import com.tumuyan.ncnn.realsr.ui.components.rememberPhotoPermissionRequest
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.state.HapticKind
import com.tumuyan.ncnn.realsr.ui.state.SessionPhoto
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MODES = listOf(
    Triple("upscale", "Upscale Mode", "Fast and simple. Everyday photos. Works on any device."),
    Triple("super", "Super Resolution Mode", "Maximum quality. Original file, no compression. For powerful devices."),
)

/** Design MODE_NAME — the home chip, mode cards and switch sheet all use it. */
fun modeName(mode: String): String = if (mode == "super") "Super Resolution" else "Upscale Mode"

private fun galleryToSession(image: GalleryImage): SessionPhoto = SessionPhoto(
    id = "gallery_${image.id}",
    uri = image.uri,
    name = image.name,
    width = image.width,
    height = image.height,
    sizeBytes = image.sizeBytes,
    mimeType = image.mimeType,
    bucketName = image.bucketName,
    dateTakenMs = image.dateTakenMs,
    source = SessionPhoto.Source.GALLERY,
)

@Composable
fun HomeScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val recentCreations by viewModel.recentCreations.collectAsState()
    val modelCatalog by viewModel.modelCatalog.collectAsState()
    val deviceProfile by viewModel.deviceProfile.collectAsState()
    val installedCount = modelCatalog.count { it.isInstalled }

    val scroll = rememberScrollState()
    var sheet by remember { mutableStateOf<String?>(null) }
    // Local, session-only camera primer (shown on first use per process).
    var cameraAsked by remember { mutableStateOf(false) }

    val permissionTick = remember { mutableIntStateOf(0) }
    val requestPhotoAccess = rememberPhotoPermissionRequest(onResult = { permissionTick.intValue++ })
    val hasAccess = remember(permissionTick.intValue) { GalleryMediaStore.hasReadImages(context) }
    val recent by produceState<List<GalleryImage>?>(null, permissionTick.intValue) {
        value = withContext(Dispatchers.IO) { GalleryMediaStore.queryRecentImages(context, 12) }
    }

    fun openViewer(photo: SessionPhoto) {
        viewModel.openViewer(photo)
        navController.navigate(Routes.VIEWER)
    }

    fun toConfig(photo: SessionPhoto) {
        viewModel.setPendingInput(photo)
        navController.navigate(Routes.CONFIG)
    }

    val probeAndOpen: (android.net.Uri, SessionPhoto.Source, String?) -> Unit = { uri, source, fallback ->
        scope.launch {
            val probed = viewModel.probeSessionPhoto(uri, source, fallback)
            if (probed != null) toConfig(probed) else viewModel.showToast("This file can't be opened. Try another image.")
        }
        Unit
    }

    val imageFilePicker = rememberImageFilePicker(onPicked = { uri -> probeAndOpen(uri, SessionPhoto.Source.IMPORTED, null) })
    val cameraCapture = rememberCameraCapture(
        onCaptured = { uri -> probeAndOpen(uri, SessionPhoto.Source.CAMERA, null) },
        onError = { msg -> viewModel.showToast(msg) },
    )

    fun requestMode(target: String) {
        if (target == settings.mode) return
        viewModel.haptic(HapticKind.LIGHT)
        if (target == "super" && !settings.superIntroSeen) {
            sheet = "super"
            return
        }
        viewModel.setMode(target)
    }

    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding()) {
        // Header bar (mark; wordmark appears once content scrolled past the hero title)
        val scrolled = scroll.value > 56
        Column {
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp)
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealScaleMark(size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    if (scrolled) {
                        Text("RealScale-Pro", style = RealScaleType.Display24, color = c.ink)
                    }
                    Spacer(Modifier.weight(1f))
                    RealScaleIconButton(
                        onClick = { navController.navigate(Routes.SETTINGS) },
                        variant = IconButtonVariant.Plain,
                        contentDescription = "Settings",
                        icon = painterResource(R.drawable.ic_lucide_settings),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(if (scrolled) c.hairline else androidx.compose.ui.graphics.Color.Transparent))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll),
        ) {
            // Hero
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 20.dp)) {
                Text("RealScale-Pro", style = RealScaleType.Display44, color = c.ink)
                Text(
                    "Sharper, bigger photos. Nothing leaves your device.",
                    style = RealScaleType.Body15,
                    color = c.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // Recent photos
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Recent photos", style = RealScaleType.Display26, color = c.ink)
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RealScaleShape.Pill)
                            .background(c.card)
                            .clickable { /* scroll target is the mode section below */ }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.teal))
                        Spacer(Modifier.width(6.dp))
                        Text(modeName(settings.mode), style = RealScaleType.Caption12, color = c.ink)
                    }
                }
                if (hasAccess) {
                    Row(
                        modifier = Modifier
                            .clip(RealScaleShape.Md)
                            .clickable { navController.navigate(Routes.GALLERY) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("See all", style = RealScaleType.Body14Medium, color = c.link)
                        Icon(painterResource(R.drawable.ic_lucide_chevron_right), contentDescription = null, tint = c.link, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (!hasAccess) {
                PhotoAccessCard(onRequestAccess = requestPhotoAccess, modifier = Modifier.padding(horizontal = 20.dp))
            } else {
                val items = recent
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        items == null -> repeat(4) {
                            Column(Modifier.width(148.dp)) {
                                Box(Modifier.size(width = 148.dp, height = 185.dp).clip(RealScaleShape.Lg).background(c.card))
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth().height(44.dp).clip(RealScaleShape.Md).background(c.card))
                            }
                        }
                        items.isEmpty() -> Text(
                            "No photos found in your gallery yet.",
                            style = RealScaleType.Body14,
                            color = c.muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        else -> items.forEach { image ->
                            Column(Modifier.width(148.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 148.dp, height = 185.dp)
                                        .clip(RealScaleShape.Lg)
                                        .background(c.card)
                                        .clickable { openViewer(galleryToSession(image)) },
                                ) {
                                    AsyncImage(
                                        model = image.uri,
                                        contentDescription = image.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Text(
                                        "${image.width}×${image.height}",
                                        style = RealScaleType.Mono12,
                                        color = c.onDark,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .clip(RealScaleShape.Sm)
                                            .background(c.stage.copy(alpha = 0.75f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                RealScaleButton(
                                    onClick = { toConfig(galleryToSession(image)) },
                                    variant = ButtonVariant.Secondary,
                                    size = ButtonSize.Sm,
                                    full = true,
                                    icon = painterResource(R.drawable.ic_lucide_image_upscale),
                                    text = "Upscale",
                                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // Choose another image
            Text(
                "Choose another image",
                style = RealScaleType.Display26,
                color = c.ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp).padding(top = 32.dp),
            )
            val tiles = listOf(
                Triple(R.drawable.ic_lucide_images, "Gallery" to "Albums and all photos") { navController.navigate(Routes.GALLERY) },
                Triple(R.drawable.ic_lucide_folder_open, "Files" to "Browse folders") { imageFilePicker() },
                Triple(R.drawable.ic_lucide_camera, "Camera" to "Take a photo") {
                    if (cameraAsked) cameraCapture() else sheet = "camera"
                },
                Triple(R.drawable.ic_lucide_ellipsis, "More" to "Clipboard, cloud, other apps") { sheet = "more" },
            )
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(232.dp).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(4) { index ->
                    val (icon, titles, action) = tiles[index]
                    TileButton(icon = icon, title = titles.first, sub = titles.second, onClick = action)
                }
            }

            // Mode selector
            Text(
                "Mode",
                style = RealScaleType.Display26,
                color = c.ink,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MODES.forEach { (id, title, desc) ->
                    val selected = settings.mode == id
                    ModeCard(
                        title = title,
                        desc = desc,
                        pro = id == "super",
                        selected = selected,
                        onClick = { requestMode(id) },
                    )
                }
            }

            // App options
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp)) {
                RealScaleGroup {
                    RealScaleRow(
                        title = "My Upscales",
                        value = if (recentCreations.isEmpty()) "Empty" else recentCreations.size.toString(),
                        icon = painterResource(R.drawable.ic_lucide_images),
                        chevron = true,
                        onClick = { navController.navigate(Routes.UPSCALES) },
                    )
                    androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                    RealScaleRow(
                        title = "Model Manager",
                        value = "$installedCount installed",
                        icon = painterResource(R.drawable.ic_lucide_layers),
                        chevron = true,
                        onClick = { navController.navigate(Routes.MODELS) },
                    )
                    androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                    RealScaleRow(
                        title = "Video Upscale",
                        icon = painterResource(R.drawable.ic_lucide_clapperboard),
                        onClick = { sheet = "video" },
                        trailing = { RealScaleBadge(text = "Coming soon", tone = BadgeTone.Neutral, leading = { Icon(painterResource(R.drawable.ic_lucide_lock), contentDescription = null, modifier = Modifier.size(12.dp), tint = LocalRealScaleColors.current.body) }) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                RealScaleGroup {
                    RealScaleRow(title = "Settings", icon = painterResource(R.drawable.ic_lucide_settings), chevron = true, onClick = { navController.navigate(Routes.SETTINGS) })
                    androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                    RealScaleRow(title = "Guide", icon = painterResource(R.drawable.ic_lucide_book_open), chevron = true, onClick = { viewModel.updateSettings { it.copy(onboarded = false) } })
                    androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                    RealScaleRow(title = "Help and feedback", icon = painterResource(R.drawable.ic_lucide_circle_help), chevron = true, onClick = { navController.navigate(Routes.HELP) })
                    androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                    RealScaleRow(title = "About and open-source licenses", icon = painterResource(R.drawable.ic_lucide_info), chevron = true, onClick = { navController.navigate(Routes.ABOUT) })
                    androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                    RealScaleRow(title = "Privacy", icon = painterResource(R.drawable.ic_lucide_shield_check), chevron = true, onClick = { sheet = "privacy" })
                    androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
                    RealScaleRow(title = "Rate and share the app", icon = painterResource(R.drawable.ic_lucide_star), chevron = true, onClick = { sheet = "rate" })
                }
                Text(
                    "RealScale-Pro ${BuildConfig.VERSION_NAME} · Works fully offline",
                    style = RealScaleType.Caption12,
                    color = c.muted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    /* ---------- Sheets ---------- */
    RealScaleSheet(open = sheet == "more", onClose = { sheet = null }, title = "More ways to import") {
        RealScaleGroup(Modifier.padding(bottom = 16.dp)) {
            RealScaleRow(
                title = "Paste from clipboard",
                sub = "Use an image you copied",
                icon = painterResource(R.drawable.ic_lucide_clipboard),
                chevron = true,
                onClick = {
                    sheet = null
                    scope.launch {
                        val uri = com.tumuyan.ncnn.realsr.ui.components.readClipboardImage(context)
                        if (uri != null) probeAndOpen(uri, SessionPhoto.Source.CLIPBOARD, "clipboard.png")
                        else viewModel.showToast("No image found on the clipboard.")
                    }
                },
            )
            androidx.compose.material3.HorizontalDivider(color = c.hairlineSoft, thickness = 1.dp)
            RealScaleRow(
                title = "Cloud drives and other folders",
                sub = "Opens the system picker",
                icon = painterResource(R.drawable.ic_lucide_cloud),
                chevron = true,
                onClick = {
                    sheet = null
                    imageFilePicker()
                },
            )
        }
    }

    RealScaleSheet(
        open = sheet == "camera",
        onClose = { sheet = null },
        title = "Use the camera",
        footer = {
            Column {
                RealScaleButton(
                    onClick = {
                        cameraAsked = true
                        sheet = null
                        cameraCapture()
                    },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Lg,
                    full = true,
                    text = "Allow camera",
                )
                Spacer(Modifier.height(8.dp))
                RealScaleButton(onClick = { sheet = null }, variant = ButtonVariant.Ghost, full = true, text = "Not now")
                Spacer(Modifier.height(4.dp))
            }
        },
    ) {
        Text("Take a photo and upscale it right away. The camera is used only while you are taking the picture.", style = RealScaleType.Body15, color = c.body)
        Text("Photos you take stay on this device.", style = RealScaleType.Body15, color = c.body, modifier = Modifier.padding(top = 10.dp, bottom = 16.dp))
    }

    RealScaleSheet(
        open = sheet == "video",
        onClose = { sheet = null },
        title = "Video Upscale",
        footer = { RealScaleButton(onClick = { sheet = null }, variant = ButtonVariant.Secondary, size = ButtonSize.Lg, full = true, text = "Close") },
    ) {
        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(painterResource(R.drawable.ic_lucide_lock), contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Text("Video upscaling is coming in a future update. This version is focused on making images as good as they can be.", style = RealScaleType.Body15, color = c.body)
        }
    }

    // First-time Super Resolution primer — confirms the heavy models and keeps the choice honest.
    RealScaleSheet(
        open = sheet == "super",
        onClose = { sheet = null },
        title = "Super Resolution Mode",
        footer = {
            Column {
                RealScaleButton(
                    onClick = {
                        viewModel.markSuperIntroSeen()
                        viewModel.setMode("super")
                        sheet = null
                    },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Lg,
                    full = true,
                    text = "Use Super Resolution",
                )
                Spacer(Modifier.height(8.dp))
                RealScaleButton(onClick = { sheet = null }, variant = ButtonVariant.Ghost, full = true, text = "Stay in Upscale Mode")
                Spacer(Modifier.height(4.dp))
            }
        },
    ) {
        Text("Recommended for high-end devices and detailed work.", style = RealScaleType.Body15, color = c.body)
        Column(
            modifier = Modifier
                .padding(top = 16.dp, bottom = 16.dp)
                .clip(RealScaleShape.Lg)
                .background(c.card)
                .padding(16.dp),
        ) {
            Text(
                "Your device: ${deviceProfile?.tier?.label ?: "Measuring…"}",
                style = RealScaleType.Body15Medium,
                color = c.ink,
            )
            Text(
                "Large photos may take a few minutes on this device.",
                style = RealScaleType.Body13,
                color = c.body,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        listOf(
            "Uses your original file. Nothing is compressed.",
            "Heavier models and lossless PNG output.",
            "Models download once, then everything works offline.",
        ).forEach { line ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.ic_lucide_check), contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(line, style = RealScaleType.Body14, color = c.body)
            }
        }
    }

    RealScaleSheet(open = sheet == "privacy", onClose = { sheet = null }, title = "Privacy") {
        Text(
            "Your images never leave this device. There is no account and no cloud processing. The network is used only to download models and check for model updates.",
            style = RealScaleType.Body15,
            color = c.body,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }

    RateSheet(
        open = sheet == "rate",
        onClose = { sheet = null },
        onToast = viewModel::showToast,
        onHaptic = { viewModel.haptic(HapticKind.LIGHT) },
    )

    // Navigate targets need pendingInput from the file picker path to be set before navigation commits.
    LaunchedEffect(Unit) { viewModel.refreshEta() }
}

@Composable
private fun TileButton(icon: Int, title: String, sub: String, onClick: () -> Unit) {
    val c = LocalRealScaleColors.current
    Column(
        modifier = Modifier
            .clip(RealScaleShape.Lg)
            .background(c.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(24.dp))
        Text(title, style = RealScaleType.Body15Medium, color = c.ink)
        Text(sub, style = RealScaleType.Body13, color = c.body, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ModeCard(title: String, desc: String, pro: Boolean, selected: Boolean, onClick: () -> Unit) {
    val c = LocalRealScaleColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RealScaleShape.Lg)
            .then(
                if (selected) Modifier.border(1.dp, c.inverse, RealScaleShape.Lg).background(c.inverse)
                else Modifier.border(1.dp, c.hairline, RealScaleShape.Lg).background(c.canvas),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = RealScaleType.Body16Medium, color = if (selected) c.onInverse else c.ink)
                if (pro) RealScaleBadge(text = "Pro", tone = BadgeTone.Amber)
            }
            Text(desc, style = RealScaleType.Body13, color = if (selected) c.onInverseSoft else c.muted, modifier = Modifier.padding(top = 4.dp))
        }
        RealScaleRadioMark(checked = selected)
    }
}

/** Rate dialog with real star selection + a working share intent. */
@Composable
private fun RateSheet(open: Boolean, onClose: () -> Unit, onToast: (String) -> Unit, onHaptic: () -> Unit) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    var stars by remember { mutableStateOf(0) }
    RealScaleSheet(
        open = open,
        onClose = onClose,
        title = "Enjoying RealScale-Pro?",
        footer = {
            Column {
                RealScaleButton(
                    onClick = {
                        onClose()
                        onToast("Thanks for rating RealScale-Pro")
                    },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Lg,
                    full = true,
                    enabled = stars > 0,
                    text = if (stars > 0) "Submit rating" else "Tap a star to rate",
                )
                Spacer(Modifier.height(8.dp))
                RealScaleButton(
                    onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "RealScale-Pro makes photos sharper and bigger, right on your device: https://github.com/Rohitjjn/RealScale-Pro")
                        }
                        runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share RealScale-Pro")) }
                        onClose()
                    },
                    variant = ButtonVariant.Secondary,
                    full = true,
                    icon = painterResource(R.drawable.ic_lucide_share_2),
                    text = "Share the app",
                )
                Spacer(Modifier.height(4.dp))
            }
        },
    ) {
        Text("A rating helps other people find the app.", style = RealScaleType.Body15, color = c.body)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            (1..5).forEach { n ->
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_star),
                    contentDescription = "$n star${if (n > 1) "s" else ""}",
                    tint = if (n <= stars) c.amber else c.mutedSoft,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RealScaleShape.Md)
                        .clickable {
                            stars = n
                            onHaptic()
                        }
                        .padding(6.dp),
                )
            }
        }
    }
}
