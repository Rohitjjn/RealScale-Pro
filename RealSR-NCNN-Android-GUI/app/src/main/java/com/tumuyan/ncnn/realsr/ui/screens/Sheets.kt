package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.ModelCatalog
import com.tumuyan.ncnn.realsr.core.plan.fmtDims
import com.tumuyan.ncnn.realsr.core.plan.fmtMB
import com.tumuyan.ncnn.realsr.core.plan.fmtMP
import com.tumuyan.ncnn.realsr.design.Dot
import com.tumuyan.ncnn.realsr.design.HairlineSoftDivider
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsDotTone
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem

@Composable
fun DetailRow(key: String, value: String, mono: Boolean = false) {
    val c = LocalRsColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(key, style = RsType.body(14f), color = c.muted, modifier = Modifier.width(96.dp))
        Text(
            value,
            style = if (mono) RsType.mono(13f) else RsType.body(14f),
            color = c.ink,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
    HairlineSoftDivider()
}

@Composable
fun PhotoDetailsSheet(store: AppStore, photo: PhotoItem, open: Boolean, onClose: () -> Unit) {
    RsSheet(open = open, onClose = onClose, title = "Details") {
        DetailRow("Name", photo.name, mono = true)
        DetailRow("Resolution", "${fmtDims(photo.width, photo.height)} · ${fmtMP(photo.mp)}")
        DetailRow("File size", fmtMB(photo.sizeMB))
        DetailRow("Format", if (photo.ext == "jpg") "JPEG" else photo.ext.uppercase())
        DetailRow("Date", photo.dateLabel)
        DetailRow("Album", photo.album)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ShareSheet(store: AppStore, photo: PhotoItem, open: Boolean, onClose: () -> Unit) {
    val c = LocalRsColors.current
    RsSheet(open = open, onClose = onClose, title = "Share") {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .background(c.card)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(RsRadius.md.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(photo.name, style = RsType.mono(13f), color = c.ink, maxLines = 1)
                Text("${fmtDims(photo.width, photo.height)} · ${fmtMB(photo.sizeMB)}", style = RsType.body(13f), color = c.body)
            }
        }
        Text(
            "On a phone this opens the system share sheet with the full-resolution file.",
            style = RsType.body(13f, lineHeightRatio = 1.4f),
            color = c.muted,
            modifier = Modifier.padding(top = 12.dp),
        )
        Column(Modifier.padding(top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RsButton(
                onClick = {
                    store.sharePhoto(photo)
                    onClose()
                },
                variant = RsButtonVariant.Primary,
                size = RsButtonSize.Lg,
                full = true,
                icon = LucideIcons.Download,
                label = "Share image",
            )
            RsButton(
                onClick = {
                    store.copyFileName(photo)
                    onClose()
                },
                full = true,
                label = "Copy file name",
            )
        }
    }
}

/** First-time Super Resolution explanation; the prototype gates the mode switch behind it. */
@Composable
fun ModeSwitchSheet(store: AppStore, pending: ModeId?, onDismiss: () -> Unit) {
    val c = LocalRsColors.current
    val device = store.device
    val pro = ModelCatalog.byId("esrgan-remacri")
    RsSheet(
        open = pending != null,
        onClose = onDismiss,
        title = "Super Resolution Mode",
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RsButton(
                    onClick = {
                        store.markSuperIntroSeen()
                        store.setMode(ModeId.SUPER)
                        if (pro.id !in store.state.value.installed) store.downloadModel(pro.id)
                        onDismiss()
                    },
                    variant = RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    label = "Use Super Resolution",
                )
                RsButton(onClick = onDismiss, variant = RsButtonVariant.Ghost, full = true, label = "Stay in Upscale Mode")
            }
        },
    ) {
        Text(
            "Recommended for high-end devices and advanced users.",
            style = RsType.body(15f, lineHeightRatio = 1.6f),
            color = c.body,
        )
        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(RsRadius.md.dp))
                .background(c.card)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(LucideIcons.Gauge, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Your device: ${device.label}", style = RsType.body(15f, FontWeight.Medium), color = c.ink)
                Text(
                    "OK fit. Large photos may take a few minutes.",
                    style = RsType.body(13f, lineHeightRatio = 1.4f),
                    color = c.body,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            RsBadge("OK", tone = RsBadgeTone.Strong, dot = RsDotTone.Ok)
        }
        Column(Modifier.padding(top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "Uses your original file. Nothing is compressed.",
                "Higher-quality models and lossless PNG output.",
                "Downloads ${pro.name} (${fmtMB(pro.sizeMB)}) over Wi-Fi.",
            ).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(LucideIcons.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
                    Text(line, style = RsType.body(15f, lineHeightRatio = 1.35f), color = c.body)
                }
            }
        }
    }
}

@Composable
fun DotLine(text: String, tone: RsDotTone = RsDotTone.Good) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(tone)
        Text(text, style = RsType.body(13f), color = LocalRsColors.current.body)
    }
}

@Composable
fun BoxedNote(text: String) {
    val c = LocalRsColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RsRadius.md.dp))
            .background(c.card)
            .padding(16.dp),
    ) {
        Text(text, style = RsType.body(14f, lineHeightRatio = 1.5f), color = c.body)
    }
}
