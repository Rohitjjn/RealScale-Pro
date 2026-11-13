package com.tumuyan.ncnn.realsr.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.model.ModelInstallStatus
import com.tumuyan.ncnn.realsr.core.engine.planner.ModeId
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType

/* ---------- Photo access missing ---------- */
@Composable
fun PhotoAccessCard(
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRealScaleColors.current
    Column(
        modifier = modifier
            .clip(RealScaleShape.Lg)
            .background(c.card)
            .padding(20.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lucide_images), contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
        Text("Allow photo access", style = RealScaleType.Body16Medium, color = c.ink, modifier = Modifier.padding(top = 12.dp))
        Text(
            "See your latest photos here and upscale one in a single tap. Gallery, Files and Camera work without it.",
            style = RealScaleType.Body14,
            color = c.body,
            modifier = Modifier.padding(top = 4.dp),
        )
        RealScaleButton(
            onClick = onRequestAccess,
            variant = ButtonVariant.Primary,
            text = "Allow photo access",
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/* ---------- Detail list ---------- */
@Composable
fun DetailList(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val c = LocalRealScaleColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (k, v) ->
            if (index > 0) RealScaleDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(k, style = RealScaleType.Body14, color = c.muted, modifier = Modifier.weight(0.4f))
                Text(
                    v,
                    style = RealScaleType.Body14,
                    color = c.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
    }
}

/* ---------- Share the app (About / overflow menu) ---------- */
@Composable
fun ShareAppSheet(
    open: Boolean,
    onClose: () -> Unit,
    viewModel: AppViewModel,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    RealScaleSheet(open = open, onClose = onClose, title = "Share RealScale-Pro") {
        Text(
            "A rating helps other people find the app.",
            style = RealScaleType.Body15,
            color = LocalRealScaleColors.current.body,
        )
        Spacer(Modifier.height(16.dp))
        RealScaleButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "RealScale-Pro makes photos sharper and bigger, right on your device — no account, no upload: https://github.com/Rohitjjn/RealScale-Pro")
                }
                runCatching { context.startActivity(Intent.createChooser(intent, "Share RealScale-Pro")) }
                onClose()
            },
            variant = ButtonVariant.Primary,
            size = ButtonSize.Lg,
            full = true,
            icon = painterResource(R.drawable.ic_lucide_share_2),
            text = "Share the app",
        )
        Spacer(Modifier.height(8.dp))
        RealScaleButton(
            onClick = onClose,
            variant = ButtonVariant.Ghost,
            full = true,
            text = "Close",
        )
        Spacer(Modifier.height(4.dp))
    }
}

/* ================= Model rows (shared by Model Manager and the picker) ================= */

/** Minimum device tier that comfortably runs the model (0 Light, 1 Standard, 2 Powerful). */
fun minTierOf(modelId: String): Int = when (modelId) {
    "models-DF2K", "models-DF2K_JPEG", "models-ESRGAN-Nomos8kSC", "models-ESRGAN-UltraSharp-fp16",
    "models-ESRGAN-Remacri", "models-ESRGAN-UltraMix_Balanced", "models-ESRGAN-AnimeSharp",
    "models-MNN-RealESRGAN_x4plus_anime_6B", "models-cunet", "models-srmd",
    -> 1
    else -> 0
}

/** Honest device-fit label; nothing in the real catalog is hard-blocked. */
data class ModelFit(val tone: DotTone, val label: String)

fun fitOf(state: CatalogModelState, tierLevel: Int): ModelFit {
    val min = minTierOf(state.catalog.id)
    return when {
        min > tierLevel -> ModelFit(DotTone.Ok, "Runs, may be slow on this device")
        min == tierLevel && min > 0 -> ModelFit(DotTone.Good, "Great on your device")
        else -> ModelFit(DotTone.Good, "Great on your device")
    }
}

private fun formatModelSize(bytes: Long): String =
    if (bytes >= 1000L * 1000L) {
        val mb = bytes / 1_000_000.0
        if (mb >= 100) String.format(java.util.Locale.US, "%.0f MB", mb) else String.format(java.util.Locale.US, "%.1f MB", mb)
    } else "${bytes / 1000} KB"

@Composable
fun ModelRow(
    state: CatalogModelState,
    variant: ModelRowVariant,
    modifier: Modifier = Modifier,
    deviceTierLevel: Int = 1,
    recommended: Boolean = false,
    selected: Boolean = false,
    required: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val c = LocalRealScaleColors.current
    val m = state.catalog
    val downloading = state.status == ModelInstallStatus.DOWNLOADING || state.status == ModelInstallStatus.QUEUED || state.status == ModelInstallStatus.VERIFYING
    val installed = state.isInstalled
    val fit = fitOf(state, deviceTierLevel)

    val info: @Composable () -> Unit = {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.displayName,
                    style = RealScaleType.Body15Medium,
                    color = c.ink,
                    maxLines = 2,
                )
                if (recommended) {
                    RealScaleBadge(text = "Recommended", tone = BadgeTone.Amber)
                }
            }
            Text(m.description, style = RealScaleType.Body13, color = c.body, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(formatModelSize(m.totalBytes), style = RealScaleType.Mono12, color = c.muted)
                Text("×${m.supportedScales.joinToString("/") { it.toString() }}", style = RealScaleType.Mono12, color = c.muted)
                if (state.status == ModelInstallStatus.FAILED && !state.errorMessage.isNullOrBlank()) {
                    Text("Failed", style = RealScaleType.Mono12, color = c.error)
                }
            }
            when {
                variant == ModelRowVariant.MANAGE && installed -> Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lucide_check),
                        contentDescription = null,
                        tint = c.ink,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("Installed", style = RealScaleType.Body13Strong, color = c.ink)
                }
                !installed && !downloading -> Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RealScaleDot(fit.tone)
                    Text(fit.label, style = RealScaleType.Caption12, color = c.muted)
                }
                else -> Unit
            }
        }
    }

    val action: @Composable () -> Unit = when {
        downloading -> {
            {
                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onCancelDownload?.invoke() },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.status == ModelInstallStatus.VERIFYING) "Verifying" else "${(state.progressFraction * 100).toInt()}%",
                        style = RealScaleType.Mono12,
                        color = c.muted,
                    )
                    RealScaleRing(progress = state.progressFraction, size = 26.dp, contentDescription = "Downloading ${m.displayName}")
                }
            }
        }
        installed && variant == ModelRowVariant.PICK -> {
            { RealScaleRadioMark(checked = selected) }
        }
        installed && required -> {
            { RealScaleBadge(text = "Required", tone = BadgeTone.Outline) }
        }
        installed -> {
            {
                RealScaleIconButton(
                    onClick = { onDelete?.invoke() },
                    variant = IconButtonVariant.Outline,
                    contentDescription = "Delete ${m.displayName}",
                    icon = painterResource(R.drawable.ic_lucide_trash_2),
                )
            }
        }
        else -> {
            {
                RealScaleButton(
                    onClick = { onDownload?.invoke() },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Sm,
                    icon = painterResource(R.drawable.ic_lucide_download),
                    text = formatModelSize(m.totalBytes),
                    contentDescription = "Download ${m.displayName}, ${formatModelSize(m.totalBytes)}",
                )
            }
        }
    }

    val clickable = variant == ModelRowVariant.PICK && installed && !downloading && onSelect != null
    Row(
        modifier = modifier
            .then(if (clickable) Modifier.clickable { onSelect?.invoke() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        info()
        action()
    }
}

enum class ModelRowVariant { PICK, MANAGE }

/* ---------- Model picker (Manual and Advanced levels in Config) ---------- */
@Composable
fun ModelPickerSheet(
    open: Boolean,
    onClose: () -> Unit,
    mode: ModeId,
    type: com.tumuyan.ncnn.realsr.core.engine.planner.ImageType,
    selectedId: String,
    installedStates: List<CatalogModelState>,
    deviceTierLevel: Int,
    onSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
) {
    val filter = remember { mutableStateOf("all") } // all | photo | anime | installed
    val pending = remember { mutableStateOf<String?>(null) }

    val installedIds = installedStates.filter { it.isInstalled }.map { it.catalog.id }.toSet()
    val recommended = com.tumuyan.ncnn.realsr.core.engine.planner.recommendedId(mode, type)

    // A model downloaded from this sheet becomes the selection once installed.
    LaunchedEffect(installedIds, pending) {
        val p = pending
        if (p != null && p in installedIds) {
            onSelect(p)
            pending.value = null
            onClose()
        }
    }

    val filtered = installedStates.filter { state ->
        val kind = state.catalog.category.uppercase()
        when (filter.value) {
            "photo" -> kind == "PHOTO"
            "anime" -> kind == "ANIME"
            "installed" -> state.isInstalled
            else -> kind == "PHOTO" || kind == "ANIME" || kind == "MAXIMUM"
        }
    }
    val ranked = filtered.sortedWith(
        compareBy(
            { state -> if (state.catalog.id == recommended) 0 else if (state.isInstalled) 1 else 2 },
            { state -> state.catalog.category },
            { state -> state.catalog.displayName },
        ),
    )

    val c = LocalRealScaleColors.current
    RealScaleSheet(open = open, onClose = onClose, title = "Choose a model", tall = true) {
        Text(
            "Every upscale model in the catalog for ${if (mode == ModeId.UPSCALE) "Upscale Mode" else "Super Resolution Mode"}. Download any of them here and it becomes your selection.",
            style = RealScaleType.Body14,
            color = c.body,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "photo" to "Photo", "anime" to "Anime", "installed" to "Installed").forEach { (value, label) ->
                RealScaleChip(label = label, selected = filter.value == value, onClick = { filter.value = value })
            }
        }
        Spacer(Modifier.height(16.dp))
        RealScaleGroup {
            if (filter.value == "all") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                onSelect("auto")
                                onClose()
                            },
                        )
                        .background(if (selectedId == "auto") c.soft else Color.Transparent)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic", style = RealScaleType.Body15Medium, color = c.ink)
                        Text(
                            "Picks the best installed model for each image and your device.",
                            style = RealScaleType.Body13,
                            color = c.body,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    RealScaleRadioMark(checked = selectedId == "auto")
                }
                RealScaleDivider()
            }
            ranked.forEachIndexed { index, state ->
                if (index > 0) RealScaleDivider()
                ModelRow(
                    state = state,
                    variant = ModelRowVariant.PICK,
                    deviceTierLevel = deviceTierLevel,
                    recommended = state.catalog.id == recommended,
                    selected = selectedId == state.catalog.id,
                    onSelect = {
                        onSelect(state.catalog.id)
                        onClose()
                    },
                    onDownload = {
                        pending.value = state.catalog.id
                        onDownload(state.catalog.id)
                    },
                    onCancelDownload = { onCancelDownload(state.catalog.id) },
                )
            }
            if (ranked.isEmpty()) {
                Text(
                    "No models match this filter.",
                    style = RealScaleType.Body14,
                    color = c.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/* ---------- Permission + import plumbing shared by onboarding and Home ---------- */

/** Requests the photo-read permission appropriate for this API level. */
@Composable
fun rememberPhotoPermissionRequest(onResult: () -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onResult()
    }
    return {
        val missing = com.tumuyan.ncnn.realsr.core.media.GalleryMediaStore.requiredPermissions().filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onResult() else launcher.launch(missing.toTypedArray())
    }
}

/** Opens the system camera with a FileProvider output; no CAMERA permission required. */
@Composable
fun rememberCameraCapture(onCaptured: (Uri) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingUri = remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = pendingUri.value
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            onCaptured(uri)
        } else {
            onError("Camera cancelled.")
        }
    }
    return {
        runCatching {
            val dir = java.io.File(context.cacheDir, "capture").apply { mkdirs() }
            val file = java.io.File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            pendingUri.value = uri
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launcher.launch(intent)
        }.onFailure {
            onError("No camera app found.")
        }
    }
}

/** Opens the system document picker for images. */
@Composable
fun rememberImageFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return { launcher.launch(arrayOf("image/*")) }
}

/** Reads an image off the clipboard (URI or legacy bitmap); copies to cache when needed. */
fun readClipboardImage(context: android.content.Context): Uri? {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return null
    if (!cm.hasPrimaryClip()) return null
    val clip = cm.primaryClip ?: return null
    for (i in 0 until clip.itemCount) {
        val item = clip.getItemAt(i)
        val uri = item.uri
        if (uri != null && (cm.primaryClipDescription?.getMimeType(i)?.startsWith("image") == true || uri.toString().let { s -> s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".webp") })) {
            // Persist a private copy (the grant may be temporary).
            return runCatching {
                val dir = java.io.File(context.cacheDir, "clipboard").apply { mkdirs() }
                val mime = context.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    mime.contains("bmp") -> "bmp"
                    else -> "jpg"
                }
                val out = java.io.File(dir, "clip_${System.currentTimeMillis()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                Uri.fromFile(out)
            }.getOrNull() ?: uri
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val bitmap = item.getBitmap()
            if (bitmap != null) {
                return runCatching {
                    val dir = java.io.File(context.cacheDir, "clipboard").apply { mkdirs() }
                    val out = java.io.File(dir, "clip_${System.currentTimeMillis()}.png")
                    java.io.FileOutputStream(out).use { output ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                    }
                    Uri.fromFile(out)
                }.getOrNull()
            }
        }
    }
    return null
}
