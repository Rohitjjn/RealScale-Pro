package com.tumuyan.ncnn.realsr.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.navigation.NavHostController
import com.tumuyan.ncnn.realsr.R
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.ui.MediaExportHelper
import com.tumuyan.ncnn.realsr.ui.Routes
import com.tumuyan.ncnn.realsr.ui.components.ButtonSize
import com.tumuyan.ncnn.realsr.ui.components.ButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.IconButtonVariant
import com.tumuyan.ncnn.realsr.ui.components.RealScaleButton
import com.tumuyan.ncnn.realsr.ui.components.RealScaleConfirmSheet
import com.tumuyan.ncnn.realsr.ui.components.RealScaleDivider
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
import java.io.File

/** My Upscales — recent creations; tap to view, long-press style select for share/delete. */
@Composable
fun UpscalesScreen(viewModel: AppViewModel, navController: NavHostController) {
    val c = LocalRealScaleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val creations by viewModel.recentCreations.collectAsState()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deleteOpen by remember { mutableStateOf(false) }

    val allSelected = creations.isNotEmpty() && creations.all { it.id in selected }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
        viewModel.haptic(HapticKind.TICK)
    }

    fun toggleAll() {
        selected = if (allSelected) emptySet() else creations.map { it.id }.toSet()
    }

    fun shareSelected() {
        val files = selected.mapNotNull { id ->
            creations.firstOrNull { it.id == id }?.outputPath?.let { File(it) }.takeIf { it.exists() }
        }
        if (files.isEmpty()) return
        selected = emptySet()
        scope.launch {
            MediaExportHelper.shareMultipleImages(context, files)
        }
    }

    fun deleteSelected() {
        deleteOpen = true
    }

    fun doDelete() {
        val ids = selected.toList()
        selected = emptySet()
        deleteOpen = false
        scope.launch {
            withContext(Dispatchers.IO) {
                viewModel.database.recentCreationDao().deleteByIds(ids)
            }
            viewModel.showToast("${ids.size} deleted")
        }
    }

    fun openResult(creation: RecentCreationEntity) {
        viewModel.selectResult(creation)
        navController.navigate(Routes.RESULT)
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
                    Text("My Upscales", style = RealScaleType.Display24, color = c.ink)
                    Spacer(Modifier.weight(1f))
                    if (selected.isNotEmpty()) {
                        RealScaleIconButton(
                            onClick = shareSelected,
                            variant = IconButtonVariant.Dark,
                            contentDescription = "Share",
                            icon = painterResource(R.drawable.ic_lucide_share_2),
                        )
                        RealScaleIconButton(
                            onClick = deleteSelected,
                            variant = IconButtonVariant.Dark,
                            contentDescription = "Delete",
                            icon = painterResource(R.drawable.ic_lucide_trash_2),
                        )
                    } else {
                        RealScaleIconButton(
                            onClick = { navController.navigate(Routes.SETTINGS) },
                            variant = IconButtonVariant.Plain,
                            contentDescription = "More",
                            icon = painterResource(R.drawable.ic_lucide_ellipsis),
                        )
                    }
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
            if (creations.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_lucide_images), contentDescription = null, tint = c.mutedSoft, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No upscales yet", style = RealScaleType.Display26, color = c.ink)
                    Text("Your finished results will appear here.", style = RealScaleType.Body15, color = c.muted)
                }
            } else {
                if (selected.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("${selected.size} selected", style = RealScaleType.Body15Medium, color = c.ink)
                        RealScaleButton(
                            onClick = shareSelected,
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Sm,
                            icon = painterResource(R.drawable.ic_lucide_share_2),
                            text = "Share",
                        )
                        RealScaleButton(
                            onClick = deleteSelected,
                            variant = ButtonVariant.Danger,
                            size = ButtonSize.Sm,
                            icon = painterResource(R.drawable.ic_lucide_trash_2),
                            text = "Delete",
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(creations, key = { it.id }) { creation ->
                        RealScaleRow(
                            title = creation.presetLabel,
                            sub = "${creation.scale}× · ${fmtBytesMB(File(creation.outputPath).length())}",
                            value = fmtDateTime(creation.createdAtEpochMs),
                            icon = null,
                            chevron = true,
                            onClick = { if (selected.isEmpty()) openResult(creation) else toggle(creation.id) },
                        )
                        RealScaleDivider()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    RealScaleConfirmSheet(
        open = deleteOpen,
        onClose = { deleteOpen = false },
        title = "Delete upscales?",
        body = "Remove ${selected.size} selected result${if (selected.size != 1) "s" else ""} from your history. The files stay on your device.",
        confirmLabel = "Delete",
        onConfirm = doDelete,
        cancelLabel = "Cancel",
        danger = true,
    )
}
