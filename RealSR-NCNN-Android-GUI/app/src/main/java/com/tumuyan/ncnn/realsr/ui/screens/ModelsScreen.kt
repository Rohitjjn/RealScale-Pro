package com.tumuyan.ncnn.realsr.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.database.ModelInstallStatus
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.BadgeTone
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.tumuyan.ncnn.realsr.ui.components.ModelRow
import com.tumuyan.ncnn.realsr.ui.components.ModelRowVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleBadge
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleGroup
import com.tumuyan.ncnn.realsr.ui.components.RealScaleConfirmSheet
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDivider
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDot
import com.tumuyan.ncnn.realsr.ui.components.RealScaleIconButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleRow
import com.tumuyan.ncnn.realsr.ui.components.RealScaleSheet
import com.tumuyan.ncnn.realsr.ui.state.AppViewModel
import com.tumuyan.ncnn.realsr.ui.state.HapticKind
import com.tumuyan.ncnn.realsr.ui.theme.LocalRealScaleColors
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleShape
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Models — install, update and delete catalog models. Categories come from the manifest. */
@Composable
fun ModelsScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog by viewModel.modelCatalog.collectAsState()
    val deviceProfile by viewModel.deviceProfile.collectAsState()
    val tierLevel = deviceProfile?.tier?.level ?: 1
    val tierLabel = deviceProfile?.tier?.label ?: "Measuring…"
    var deleteConfirm by remember { mutableStateOf<String?>(null) }

    val byCategory = catalog.groupBy { it.catalog.category.uppercase() }
    val categoryOrder = listOf("PHOTO", "ANIME", "MAXIMUM")

    fun download(id: String) {
        scope.launch { viewModel.modelRepository.startOrResumeDownload(id) }
        viewModel.haptic(HapticKind.TICK)
    }

    fun cancel(id: String) {
        scope.launch { viewModel.modelRepository.cancelDownload(id) }
    }

    fun askDelete(id: String) { deleteConfirm = id }

    fun doDelete() {
        val id = deleteConfirm ?: return
        deleteConfirm = null
        scope.launch {
            viewModel.modelRepository.deleteInstalledModel(id)
            viewModel.showToast("Model removed")
        }
    }

    Column(Modifier.fillMaxSize().background(c.canvas)) {
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
                    Text("Models", style = RealScaleType.Display24, color = c.ink)
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
            // Device info
            RealScaleGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RealScaleShape.Lg)
                        .border(1.dp, c.hairline, RealScaleShape.Lg)
                        .background(c.card)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_lucide_cpu), contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Device tier", style = RealScaleType.Body15Medium, color = c.ink)
                        Text("$tierLabel device · ${deviceProfile?.ramGb?.takeIf { it > 0 } ?: 0.0} GB RAM",
                            style = RealScaleType.Body13, color = c.muted)
                    }
                    RealScaleBadge(text = "Level $tierLevel", tone = when {
                        tierLevel >= 2 -> BadgeTone.Strong
                        tierLevel == 1 -> BadgeTone.Amber
                        else -> BadgeTone.Neutral
                    })
                }
            }
            Spacer(Modifier.height(20.dp))

            categoryOrder.forEach { category ->
                val models = byCategory[category] ?: emptyList()
                if (models.isEmpty()) return@forEach
                Text(category, style = RealScaleType.Body15Medium, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
                models.forEach { state ->
                    ModelRow(
                        state = state,
                        variant = ModelRowVariant.MANAGE,
                        modifier = Modifier.fillMaxWidth(),
                        deviceTierLevel = tierLevel,
                        onDownload = { download(state.catalog.id) },
                        onCancelDownload = { cancel(state.catalog.id) },
                        onDelete = { askDelete(state.catalog.id) },
                    )
                    if (state != models.last()) RealScaleDivider()
                }
                Spacer(Modifier.height(20.dp))
            }

            Text("Installed models count: ${catalog.count { it.isInstalled }}",
                style = RealScaleType.Body13, color = c.muted)
            Spacer(Modifier.height(24.dp))
        }
    }

    RealScaleConfirmSheet(
        open = deleteConfirm != null,
        onClose = { deleteConfirm = null },
        title = "Delete model?",
        body = "Remove this model from the device. It can be re-downloaded from the catalog later.",
        confirmLabel = "Delete",
        onConfirm = { doDelete() },
        cancelLabel = "Cancel",
        danger = true,
    )
}
