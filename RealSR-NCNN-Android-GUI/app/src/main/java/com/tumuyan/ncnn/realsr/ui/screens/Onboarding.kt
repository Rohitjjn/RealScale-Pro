package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tumuyan.ncnn.realsr.core.device.HapticKind
import com.tumuyan.ncnn.realsr.core.plan.ModelCatalog
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.design.Dot
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.GroupTopDivider
import com.tumuyan.ncnn.realsr.design.HairlineSoftDivider
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.Mark
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsDotTone
import com.tumuyan.ncnn.realsr.design.RsIconButton
import com.tumuyan.ncnn.realsr.design.RsProgressBar
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.Skeleton
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Onboarding: splash, the four-page guide, the two permission primers, the device check and the
 * Starter Pack download. Every number comes from the device and from the real downloader.
 */
@Composable
fun Onboarding(store: AppStore, phase: Phase) {
    val c = LocalRsColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(c.canvas),
    ) {
        when (phase) {
            Phase.Splash -> Splash(store)
            Phase.Guide -> Guide(store)
            Phase.PermPhotos -> Primer(store, photos = true)
            Phase.PermNotif -> Primer(store, photos = false)
            Phase.Device -> DeviceCheck(store)
            Phase.Setup -> Setup(store)
            Phase.App -> Unit
        }
    }
}

/* ---------- 3.1 Splash ---------- */

@Composable
private fun Splash(store: AppStore) {
    val c = LocalRsColors.current
    LaunchedEffect(Unit) {
        delay(1500)
        store.setPhase(if (store.state.value.onboarded) Phase.App else Phase.Guide)
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Mark(size = 144.dp, animate = true)
        Text(
            text = "RealScale-Pro",
            style = RsType.display(40f),
            color = c.ink,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            text = "On-Device Image Upscaling",
            style = RsType.body(14f),
            color = c.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/* ---------- 3.2 Guide ---------- */

private data class Slide(val title: String, val body: String, val art: Int)

private val SLIDES = listOf(
    Slide(
        "Make any photo sharper",
        "Old, small or blurry. Pick a photo, tap Upscale and get a bigger, cleaner image.",
        0,
    ),
    Slide(
        "Works on every device",
        "From a budget phone to a gaming PC. The app checks your device and adapts on its own.",
        1,
    ),
    Slide(
        "Two modes",
        "Upscale Mode for quick everyday results. Super Resolution Mode when you want the very best.",
        2,
    ),
    Slide(
        "Private and offline",
        "Your photos never leave your device. No account, no upload, no internet after setup.",
        3,
    ),
)

@Composable
private fun Guide(store: AppStore) {
    val c = LocalRsColors.current
    val pager = rememberPagerState { SLIDES.size }
    val last = pager.currentPage == SLIDES.size - 1
    val replay = store.state.value.phase == Phase.Guide && store.state.value.onboarded
    val finish = {
        store.setPhase(if (store.state.value.onboarded) Phase.App else Phase.PermPhotos)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mark(size = 28.dp)
            Spacer(Modifier.weight(1f))
            if (store.state.value.onboarded) {
                RsIconButton(icon = LucideIcons.X, label = "Close guide", variant = com.tumuyan.ncnn.realsr.design.RsIconButtonVariant.Plain, onClick = finish)
            } else {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    RsButton(onClick = finish, variant = RsButtonVariant.Ghost, label = "Skip")
                }
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (SLIDES[page].art) {
                        0 -> ArtSharper(store)
                        1 -> ArtDevices(store)
                        2 -> ArtModes()
                        else -> ArtPrivate()
                    }
                }
                Column(Modifier.padding(top = 12.dp, bottom = 20.dp)) {
                    Text(SLIDES[page].title, style = RsType.display(38f), color = c.ink)
                    Text(
                        SLIDES[page].body,
                        style = RsType.body(16f, lineHeightRatio = 1.6f),
                        color = c.body,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Row(
                Modifier.padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(SLIDES.size) { index ->
                    val selected = index == pager.currentPage
                    val width by animateFloatAsState(
                        targetValue = if (selected) 24f else 6f,
                        animationSpec = tween(300),
                        label = "dot",
                    )
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(width.dp)
                            .clip(CircleShape)
                            .background(if (selected) c.ink else c.mutedSoft.copy(alpha = 0.5f)),
                    )
                }
            }
            val scope = rememberCoroutineScope()
            RsButton(
                onClick = {
                    if (pager.currentPage == SLIDES.size - 1) {
                        finish()
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                label = if (last) (if (store.state.value.onboarded) "Done" else "Get started") else "Continue",
            )
        }
    }
}

/* ---------- art ---------- */

@Composable
private fun ArtSharper(store: AppStore) {
    val c = LocalRsColors.current
    val photo = store.state.value.photos.firstOrNull()
    Box(
        Modifier
            .aspectRatio(0.75f)
            .heightIn(max = 416.dp)
            .clip(RoundedCornerShape(RsRadius.lg.dp))
            .background(c.card),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            coil3.compose.AsyncImage(
                model = photo.uri,
                contentDescription = "Your most recent photo, before and after upscaling",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Mark(size = 96.dp)
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(CircleShape)
                .background(c.stage.copy(alpha = 0.75f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("Before", style = RsType.body(12f), color = c.onDark)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(CircleShape)
                .background(c.stage.copy(alpha = 0.75f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("After", style = RsType.body(12f), color = c.onDark)
        }
    }
}

@Composable
private fun ArtDevices(store: AppStore) {
    val c = LocalRsColors.current
    val device = store.device
    val rows = listOf(
        Triple(LucideIcons.Smartphone, "Light", "Budget phones with 2 GB"),
        Triple(LucideIcons.Tablet, "Standard", "Most phones and laptops"),
        Triple(LucideIcons.Laptop, "Powerful", "Flagships and gaming PCs"),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEachIndexed { index, (icon, name, note) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.card)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                androidx.compose.material3.Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = RsType.body(15f, androidx.compose.ui.text.font.FontWeight.Medium), color = c.ink)
                    Text(note, style = RsType.body(13f), color = c.body)
                }
                Meter(bars = index + 1, highlight = device.label == name)
            }
        }
    }
}

@Composable
private fun Meter(bars: Int, highlight: Boolean = false) {
    val c = LocalRsColors.current
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(21.dp),
    ) {
        repeat(3) { n ->
            Box(
                Modifier
                    .width(6.dp)
                    .height((6 + (n + 1) * 5).dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            n >= bars -> c.mutedSoft.copy(alpha = 0.4f)
                            highlight -> c.primary
                            else -> c.ink
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ArtModes() {
    val c = LocalRsColors.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .background(c.inverse)
                .padding(20.dp),
        ) {
            EyebrowLabel("Default", color = c.onInverseSoft)
            Text("Upscale Mode", style = RsType.display(30f), color = c.onInverse, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Fast and simple, for everyday photos.",
                style = RsType.body(14f, lineHeightRatio = 1.55f),
                color = c.onInverseSoft,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp))
                .padding(20.dp),
        ) {
            RsBadge("Pro", tone = RsBadgeTone.Amber)
            Text("Super Resolution", style = RsType.display(30f), color = c.ink, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Maximum quality from the original file.",
                style = RsType.body(14f, lineHeightRatio = 1.55f),
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ArtPrivate() {
    val c = LocalRsColors.current
    val rows = listOf(
        "account" to "none",
        "uploads" to "0 photos",
        "processing" to "this device",
        "network" to "model downloads only",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.dark)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(LucideIcons.Lock, contentDescription = null, tint = c.onDarkSoft, modifier = Modifier.size(16.dp))
            Text("privacy.status", style = RsType.mono(13f), color = c.onDarkSoft)
        }
        Spacer(Modifier.height(12.dp))
        rows.forEach { (key, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(key, style = RsType.mono(13f), color = c.onDarkSoft)
                Text(value, style = RsType.mono(13f), color = c.onDark)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.onDark.copy(alpha = 0.1f)))
        }
        Row(
            Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Dot(RsDotTone.Good)
            Text("works offline after setup", style = RsType.mono(13f), color = c.teal)
        }
    }
}

/* ---------- 3.3 Permission primers ---------- */

@Composable
private fun Primer(store: AppStore, photos: Boolean) {
    val c = LocalRsColors.current
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(RsRadius.lg.dp))
                    .background(c.card),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    if (photos) LucideIcons.Images else LucideIcons.Bell,
                    contentDescription = null,
                    tint = c.ink,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            EyebrowLabel(if (photos) "Permission 1 of 2" else "Permission 2 of 2 · Optional")
            Text(
                if (photos) "See your recent photos" else "Know when it's done",
                style = RsType.display(38f),
                color = c.ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                if (photos) {
                    "RealScale-Pro shows your latest photos on Home, so upscaling one takes a single tap."
                } else {
                    "Get an alert when an upscale finishes while the app is in the background."
                },
                style = RsType.body(16f, lineHeightRatio = 1.6f),
                color = c.body,
                modifier = Modifier.padding(top = 16.dp),
            )
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BulletRow(
                    icon = LucideIcons.Check,
                    text = if (photos) "Used only to show and open your photos." else "Only about your own upscales.",
                )
                BulletRow(
                    icon = LucideIcons.ShieldCheck,
                    text = if (photos) "The app never uploads, edits or deletes them." else "No marketing or reminders, ever.",
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RsButton(
                onClick = {
                    store.haptic(HapticKind.Light)
                    if (photos) store.requestPhotoPermission() else store.finishNotifications(accepted = true)
                },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                label = if (photos) "Allow photo access" else "Allow notifications",
            )
            RsButton(
                onClick = { if (photos) store.skipPhotoPermission() else store.finishNotifications(accepted = false) },
                variant = RsButtonVariant.Ghost,
                full = true,
                label = "Not now",
            )
        }
    }
}

@Composable
private fun BulletRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val c = LocalRsColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
        Text(text, style = RsType.body(15f, lineHeightRatio = 1.35f), color = c.body)
    }
}

/* ---------- 3.4 Device check ---------- */

@Composable
private fun DeviceCheck(store: AppStore) {
    val c = LocalRsColors.current
    val device = store.device
    val checks = remember(device) {
        listOf(
            LucideIcons.MemoryStick to ("Memory" to "${device.ramGB} GB"),
            LucideIcons.Cpu to ("Processor" to "${device.cores} cores"),
            LucideIcons.Zap to ("Graphics" to "Vulkan ${device.vulkan}"),
            LucideIcons.HardDrive to ("Free storage" to "${device.freeGB} GB"),
            LucideIcons.Gauge to ("Speed test" to "%.2f s per tile".format(device.benchmarkSecPerTile)),
        )
    }
    var done by remember { mutableIntStateOf(0) }
    val complete = done >= checks.size
    LaunchedEffect(done) {
        if (complete) {
            store.haptic(HapticKind.Success)
        } else {
            delay(if (done == checks.size - 1) 520 else 500)
            done += 1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            EyebrowLabel("Device check")
            Text(
                if (complete) "Your device is ready." else "Checking your device",
                style = RsType.display(38f),
                color = c.ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            Column(
                Modifier
                    .padding(top = 28.dp)
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp)),
            ) {
                checks.forEachIndexed { index, (icon, pair) ->
                    if (index > 0) HairlineSoftDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            icon,
                            contentDescription = null,
                            tint = if (index <= done) c.ink else c.mutedSoft,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            pair.first,
                            style = RsType.body(15f, androidx.compose.ui.text.font.FontWeight.Medium),
                            color = if (index <= done) c.ink else c.mutedSoft,
                            modifier = Modifier.weight(1f),
                        )
                        when {
                            index < done -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(pair.second, style = RsType.mono(13f), color = c.body)
                                androidx.compose.material3.Icon(LucideIcons.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
                            }
                            index == done -> Skeleton(Modifier.width(80.dp).height(12.dp))
                            else -> Unit
                        }
                    }
                }
            }
            if (complete) {
                Column(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.md.dp))
                        .background(c.card)
                        .padding(20.dp),
                ) {
                    EyebrowLabel("Performance", color = c.body)
                    Text(device.label, style = RsType.display(34f), color = c.ink, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Light", "Standard", "Powerful").forEachIndexed { index, label ->
                            Column(Modifier.weight(1f)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(if (index <= device.tier - 1) c.ink else c.mutedSoft.copy(alpha = 0.35f)),
                                )
                                Text(
                                    label,
                                    style = RsType.body(
                                        12f,
                                        if (label == device.label) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                                    ),
                                    color = if (label == device.label) c.ink else c.body,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    Text(
                        "The right model and settings are picked for you automatically.",
                        style = RsType.body(14f, lineHeightRatio = 1.55f),
                        color = c.body,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            RsButton(
                onClick = { store.setPhase(Phase.Setup) },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                enabled = complete,
                label = if (complete) "Continue" else "Checking…",
            )
        }
    }
}

/* ---------- 3.5 Setup / Starter Pack ---------- */

@Composable
private fun Setup(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.state.value
    val starters = remember { ModelCatalog.STARTER_IDS.map { ModelCatalog.byId(it) } }
    val total = starters.sumOf { it.sizeMB }
    val installedBytes = starters.sumOf { model ->
        if (model.id in state.installed) model.sizeMB else 0.0
    }
    val active = starters.firstOrNull { it.id !in state.installed }
    val fractional = active?.let { model ->
        (state.downloads[model.id]?.progress ?: 0f) * model.sizeMB
    } ?: 0.0
    val mb = installedBytes + fractional
    val complete = starters.all { it.id in state.installed }
    var verified by remember { mutableStateOf(false) }
    LaunchedEffect(complete) {
        if (complete) {
            delay(700)
            verified = true
            store.haptic(HapticKind.Success)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            EyebrowLabel(if (verified) "Setup complete" else "Setup")
            Text(
                if (verified) "You're all set." else "Setting up RealScale-Pro",
                style = RsType.display(38f),
                color = c.ink,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                if (verified) {
                    "The Starter Pack is installed. Everything now works offline."
                } else {
                    "Downloading the Starter Pack over Wi-Fi so upscaling works offline."
                },
                style = RsType.body(16f, lineHeightRatio = 1.6f),
                color = c.body,
                modifier = Modifier.padding(top = 12.dp),
            )
            Column(
                Modifier
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .border(1.dp, c.hairline, RoundedCornerShape(RsRadius.md.dp)),
            ) {
                starters.forEachIndexed { index, model ->
                    if (index > 0) HairlineSoftDivider()
                    val installed = model.id in state.installed
                    val download = state.downloads[model.id]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.name,
                                style = RsType.body(15f, androidx.compose.ui.text.font.FontWeight.Medium),
                                color = if (installed || download != null) c.ink else c.muted,
                            )
                            Text(fmtMB(model.sizeMB), style = RsType.mono(12f), color = c.muted)
                        }
                        when {
                            verified || installed -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                androidx.compose.material3.Icon(LucideIcons.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
                                Text("Done", style = RsType.body(13f), color = c.ink)
                            }
                            download != null && download.state == "paused" -> Text("Paused", style = RsType.body(13f), color = c.body)
                            download != null -> Text(
                                "${(download.progress * 100).toInt()}%",
                                style = RsType.mono(13f),
                                color = c.ink,
                            )
                            else -> Text("Queued", style = RsType.body(13f), color = c.muted)
                        }
                    }
                }
            }
            Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${if (total > 0) (mb / total * 100).toInt() else 0}%",
                        style = RsType.display(44f).copy(fontFeatureSettings = "tnum"),
                        color = c.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "%.1f of %.1f MB".format(mb, total),
                        style = RsType.body(13f).copy(fontFeatureSettings = "tnum"),
                        color = c.muted,
                        textAlign = TextAlign.End,
                    )
                }
                RsProgressBar(
                    value = if (total > 0) (mb / total).toFloat() else 0f,
                    label = "Starter Pack download",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (verified) {
                RsButton(
                    onClick = { store.finishSetup() },
                    variant = RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    label = "Start upscaling",
                )
            } else {
                RsButton(
                    onClick = {
                        starters.filter { it.id !in state.installed }.forEach { model ->
                            val download = state.downloads[model.id]
                            if (download == null) store.downloadModel(model.id) else store.pauseModel(model.id)
                        }
                    },
                    size = RsButtonSize.Lg,
                    full = true,
                    icon = if (state.downloads.values.any { it.state == "paused" }) LucideIcons.Play else LucideIcons.Pause,
                    label = if (state.downloads.values.any { it.state == "paused" }) "Resume download" else "Pause download",
                )
            }
        }
    }
}
