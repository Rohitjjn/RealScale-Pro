package com.tumuyan.ncnn.realsr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tumuyan.ncnn.realsr.core.ai.AiCatalog
import com.tumuyan.ncnn.realsr.core.ai.AiKeyRecord
import com.tumuyan.ncnn.realsr.design.EyebrowLabel
import com.tumuyan.ncnn.realsr.design.LocalRsColors
import com.tumuyan.ncnn.realsr.design.RsBadge
import com.tumuyan.ncnn.realsr.design.RsBadgeTone
import com.tumuyan.ncnn.realsr.design.RsButton
import com.tumuyan.ncnn.realsr.design.RsButtonSize
import com.tumuyan.ncnn.realsr.design.RsButtonVariant
import com.tumuyan.ncnn.realsr.design.RsChip
import com.tumuyan.ncnn.realsr.design.RsConfirmSheet
import com.tumuyan.ncnn.realsr.design.RsGroup
import com.tumuyan.ncnn.realsr.design.RsRadius
import com.tumuyan.ncnn.realsr.design.RsRow
import com.tumuyan.ncnn.realsr.design.RsSheet
import com.tumuyan.ncnn.realsr.design.RsStepper
import com.tumuyan.ncnn.realsr.design.RsSwitchRow
import com.tumuyan.ncnn.realsr.design.RsTopBar
import com.tumuyan.ncnn.realsr.design.RsType
import com.tumuyan.ncnn.realsr.design.icons.LucideIcons
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.rememberState

/**
 * AI Upscale Mode → **API setup / dashboard**.
 *
 * One screen for both cases the request describes: it is where the AI icon sends a user who has no
 * key yet, and it is the dashboard for a user who has several. Keys are stored in the app's private
 * prefs, never uploaded, and only ever used as the request header for the provider that was picked.
 */
@Composable
fun AiKeysScreen(store: AppStore) {
    val c = LocalRsColors.current
    val state = store.rememberState()
    val keys = state.aiKeys
    val scroll = rememberScrollState()

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiKeyRecord?>(null) }
    var deleting by remember { mutableStateOf<AiKeyRecord?>(null) }

    /** Form state, shared by "add" and "edit". */
    var providerId by remember { mutableStateOf(AiCatalog.PROVIDERS.first().id) }
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(0) }

    val provider = AiCatalog.provider(providerId)

    Column(Modifier.fillMaxSize().background(c.canvas)) {
        RsTopBar(
            title = "AI Upscaling",
            onBack = { store.pop() },
            scrolled = scroll.value > 8,
            right = {
                com.tumuyan.ncnn.realsr.design.RsIconButton(
                    icon = LucideIcons.Plus,
                    label = "Add API key",
                    onClick = {
                        providerId = AiCatalog.PROVIDERS.first().id
                        label = ""
                        secret = ""
                        baseUrl = ""
                        modelId = ""
                        limit = 0
                        adding = true
                    },
                )
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Bring your own model",
                style = RsType.display(30f),
                color = c.ink,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "AI Upscale Mode sends one photo to the image model you choose and gets a rebuilt, " +
                    "higher-resolution copy back. This is the only mode that uses the internet.",
                style = RsType.body(15f, lineHeightRatio = 1.6f),
                color = c.muted,
                modifier = Modifier.padding(top = 10.dp),
            )

            /* ---------------- dashboard ---------------- */
            Row(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.lg.dp))
                    .background(c.card)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Stat("Keys", keys.size.toString(), Modifier.weight(1f))
                Stat("Active", state.aiActiveKeys.size.toString(), Modifier.weight(1f))
                Stat("This month", state.aiUsedThisMonth().toString(), Modifier.weight(1f))
            }

            if (keys.isEmpty()) {
                Box(
                    Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RsRadius.lg.dp))
                        .background(c.soft)
                        .padding(20.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(LucideIcons.KeyRound, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                            Text("No API key yet", style = RsType.body(16f, FontWeight.Medium), color = c.ink)
                        }
                        Text(
                            "Add a key from any provider below. Nothing is charged by this app — usage is " +
                                "billed by the provider on your own account.",
                            style = RsType.body(14f, lineHeightRatio = 1.55f),
                            color = c.body,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        RsButton(
                            onClick = {
                                providerId = AiCatalog.PROVIDERS.first().id
                                label = ""
                                secret = ""
                                baseUrl = ""
                                modelId = ""
                                limit = 0
                                adding = true
                            },
                            modifier = Modifier.padding(top = 14.dp),
                            variant = RsButtonVariant.Primary,
                            size = RsButtonSize.Lg,
                            full = true,
                            icon = LucideIcons.Plus,
                            label = "Add API key",
                        )
                    }
                }
            } else {
                EyebrowLabel("Saved keys", Modifier.padding(top = 28.dp, start = 4.dp, bottom = 8.dp))
                RsGroup {
                    keys.forEachIndexed { index, key ->
                        RsRow(
                            title = key.label,
                            sub = "${key.provider.name} · ${key.maskedKey} · ${key.usageLabel}",
                            icon = LucideIcons.KeyRound,
                            value = when {
                                !key.enabled -> "Off"
                                key.quotaReached -> "Limit"
                                else -> null
                            },
                            chevron = true,
                            topDivider = index > 0,
                            onClick = {
                                editing = key
                                providerId = key.providerId
                                label = key.label
                                secret = key.key
                                baseUrl = key.baseUrl.orEmpty()
                                modelId = key.modelId.orEmpty()
                                limit = key.monthlyLimit
                            },
                        )
                    }
                }
                RsButton(
                    onClick = {
                        providerId = AiCatalog.PROVIDERS.first().id
                        label = ""
                        secret = ""
                        baseUrl = ""
                        modelId = ""
                        limit = 0
                        adding = true
                    },
                    modifier = Modifier.padding(top = 12.dp),
                    variant = RsButtonVariant.Secondary,
                    size = RsButtonSize.Lg,
                    full = true,
                    icon = LucideIcons.Plus,
                    label = "Add another key",
                )
            }

            /* ---------------- where to get a key ---------------- */
            EyebrowLabel("Where to get a key", Modifier.padding(top = 28.dp, start = 4.dp, bottom = 8.dp))
            RsGroup {
                AiCatalog.PROVIDERS.forEachIndexed { index, entry ->
                    RsRow(
                        title = entry.name,
                        sub = entry.note,
                        icon = LucideIcons.Cloud,
                        value = entry.keyHint,
                        chevron = entry.docsUrl.isNotBlank(),
                        topDivider = index > 0,
                        onClick = if (entry.docsUrl.isNotBlank()) {
                            { store.openLink(entry.docsUrl) }
                        } else {
                            null
                        },
                    )
                }
            }

            /* ---------------- privacy ---------------- */
            Row(
                Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RsRadius.md.dp))
                    .background(c.card)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(LucideIcons.ShieldCheck, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                Text(
                    "Keys stay in this app's private storage. Only the photo you are upscaling is sent, " +
                        "once, to the provider you selected — Upscale and Super Resolution never leave the device.",
                    style = RsType.body(13f, lineHeightRatio = 1.5f),
                    color = c.body,
                )
            }
        }
    }

    /* ---------------- add / edit sheet ---------------- */
    RsSheet(
        open = adding || editing != null,
        onClose = { adding = false; editing = null },
        title = if (editing != null) "Edit API key" else "Add API key",
        tall = true,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RsButton(
                    onClick = {
                        val target = editing
                        if (target != null) {
                            val current = AiCatalog.provider(target.providerId)
                            if (secret.trim().length < 8 || (current.keyPrefix != null && !secret.trim().startsWith(current.keyPrefix))) {
                                store.toast("That key looks wrong for ${current.name}")
                            } else {
                                store.updateAiKey(target.id) {
                                    it.copy(
                                        label = label.trim().ifEmpty { current.name },
                                        key = secret.trim(),
                                        baseUrl = baseUrl.trim().takeIf { v -> v.isNotEmpty() },
                                        modelId = modelId.trim().takeIf { v -> v.isNotEmpty() },
                                        monthlyLimit = limit,
                                    )
                                }
                                store.toast("${target.label} updated")
                                editing = null
                            }
                        } else {
                            val ok = store.addAiKey(
                                providerId = providerId,
                                label = label,
                                key = secret,
                                baseUrl = baseUrl,
                                modelId = modelId,
                                monthlyLimit = limit,
                            )
                            if (ok) adding = false
                        }
                    },
                    variant = RsButtonVariant.Primary,
                    size = RsButtonSize.Lg,
                    full = true,
                    label = if (editing != null) "Save changes" else "Save key",
                )
                if (editing != null) {
                    RsButton(
                        onClick = { deleting = editing },
                        variant = RsButtonVariant.Ghost,
                        full = true,
                        icon = LucideIcons.Trash2,
                        label = "Remove this key",
                    )
                }
            }
        },
    ) {
        if (editing == null) {
            Text("Provider", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiCatalog.PROVIDERS.chunked(2).forEach { row ->
                    if (row.size == 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { entry ->
                                RsChip(
                                    text = entry.name,
                                    selected = providerId == entry.id,
                                    onClick = { providerId = entry.id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        RsChip(text = row.first().name, selected = providerId == row.first().id, onClick = { providerId = row.first().id })
                    }
                }
            }
        }

        Column(Modifier.padding(top = 16.dp)) {
            AiInputRow(
                label = "API key",
                value = secret,
                onValueChange = { secret = it },
                placeholder = provider.keyHint,
                hint = if (provider.docsUrl.isNotBlank()) {
                    "${provider.name} → ${provider.docsUrl.removePrefix("https://")}"
                } else {
                    provider.note
                },
                secret = true,
            )
        }
        Column(Modifier.padding(top = 14.dp)) {
            AiInputRow(
                label = "Name",
                value = label,
                onValueChange = { label = it },
                placeholder = provider.name,
                hint = "Shown in the key list and on the AI Upscale screen.",
            )
        }
        if (provider.allowsCustomBaseUrl) {
            Column(Modifier.padding(top = 14.dp)) {
                AiInputRow(
                    label = "Base URL",
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    placeholder = "https://my-gateway.example.com/v1",
                    hint = "The endpoint must accept OpenAI-style multipart /images/edits.",
                )
            }
        }
        if (provider.allowsCustomModel) {
            Column(Modifier.padding(top = 14.dp)) {
                AiInputRow(
                    label = "Model id",
                    value = modelId,
                    onValueChange = { modelId = it },
                    placeholder = "my-upscale-model",
                    hint = "Exactly the id your gateway expects.",
                )
            }
        }
        Column(Modifier.padding(top = 18.dp)) {
            Text("Monthly image limit", style = RsType.body(14f, FontWeight.Medium), color = c.ink)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RsStepper(
                    value = limit,
                    min = 0,
                    max = 2000,
                    step = 10,
                    onChange = { limit = it },
                    label = "Monthly images",
                    format = { if (it == 0) "No limit" else "$it / month" },
                )
            }
            Text(
                "A limit only stops this app from using the key — it does not change what the provider bills you.",
                style = RsType.body(12f, lineHeightRatio = 1.45f),
                color = c.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (editing != null) {
            RsGroup(Modifier.padding(top = 18.dp, bottom = 16.dp)) {
                RsSwitchRow(
                    title = "Enabled",
                    sub = "Turn off to keep the key without using it.",
                    checked = editing?.enabled ?: true,
                    topDivider = false,
                    onChange = { on ->
                        editing?.let { key -> store.setAiKeyEnabled(key.id, on); editing = key.copy(enabled = on) }
                    },
                )
                RsRow(
                    title = "Reset usage",
                    sub = "Sets this month's counter back to zero.",
                    icon = LucideIcons.RotateCcw,
                    onClick = {
                        editing?.let { key ->
                            store.resetAiKeyUsage(key.id)
                            editing = key.copy(used = 0)
                        }
                    },
                )
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }

        if (editing != null) {
            Spacer(Modifier.height(8.dp))
            Row {
                RsBadge("Stored on this device", tone = RsBadgeTone.Neutral)
            }
        }
    }

    RsConfirmSheet(
        open = deleting != null,
        onClose = { deleting = null },
        title = "Remove this key?",
        body = "${deleting?.label ?: "This key"} will be deleted from this device. The provider account is not affected.",
        confirmLabel = "Remove key",
        danger = true,
        onConfirm = {
            deleting?.let { store.deleteAiKey(it.id) }
            deleting = null
            editing = null
        },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalRsColors.current
    Column(modifier) {
        Text(value, style = RsType.display(22f).copy(fontFeatureSettings = "tnum"), color = c.ink)
        Text(label, style = RsType.body(12f), color = c.muted, modifier = Modifier.padding(top = 2.dp))
    }
}
