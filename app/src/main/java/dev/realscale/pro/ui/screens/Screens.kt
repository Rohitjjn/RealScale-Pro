package dev.realscale.pro.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.realscale.pro.data.models.*
import dev.realscale.pro.ui.MainViewModel
import dev.realscale.pro.ui.components.*
import dev.realscale.pro.ui.theme.Warm
import java.io.File

@Composable fun HomeScreen(vm: MainViewModel) {
    val s by vm.studio.collectAsState()
    val creations by vm.creations.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(vm::onImagePicked) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { Spacer(Modifier.height(24.dp))
            Text("RealScale Pro", style = MaterialTheme.typography.displayMedium)
            Text("Enhance images on-device. Warm, calm, private.", style = MaterialTheme.typography.bodyLarge, color = Warm.Muted) }
        item { FeatureCard(Modifier.fillMaxWidth()) {
            Text("Home Studio", style = MaterialTheme.typography.titleMedium)
            Text(s.status, style = MaterialTheme.typography.bodySmall, color = Warm.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WarmPrimaryButton("Choose image") { picker.launch("image/*") }
                if (s.input != null && !s.processing) WarmSecondaryButton("Upscale ×${s.selected.scale}") { vm.upscale() }
            }
            Text("Quick model", style = MaterialTheme.typography.labelMedium, color = Warm.Muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ModelCatalog.packs + ModelCatalog.classical) { p ->
                    FilterChip(s.selected.id == p.id, { vm.select(p) }, { Text(p.name, maxLines = 1) },
                        shape = MaterialTheme.shapes.medium)
                }
            }
        } }
        if (s.processing) item {
            val eta = if (s.fraction > 0.02f) "~${((System.currentTimeMillis() - s.startedAt) * (1 - s.fraction) / s.fraction / 1000).toInt()}s left" else "estimating…"
            CalmProgress(s.fraction, s.selected.name, eta) { vm.cancel() }
        }
        val inp = s.input; val out = s.output
        if (inp != null && out != null) item { BeforeAfterCanvas(inp, out, Modifier.fillMaxWidth().aspectRatio(4f / 3f)) }
        else if (inp != null) item {
            Image(inp.asImageBitmap(), "Selected image", Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.extraLarge), contentScale = ContentScale.Fit)
        }
        if (creations.isNotEmpty()) {
            item { Text("Recent creations", style = MaterialTheme.typography.displaySmall) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(creations) { c ->
                    val bmp = remember(c.outputPath) { BitmapFactory.decodeFile(c.outputPath) }
                    FeatureCard(Modifier.width(180.dp)) {
                        if (bmp != null) Image(bmp.asImageBitmap(), c.modelName, Modifier.fillMaxWidth().height(100.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                        Text(c.modelName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("×${c.scale} · ${c.durationMs / 1000}s", style = MaterialTheme.typography.bodySmall, color = Warm.Muted)
                        BadgePill(File(c.outputPath).name)
                    }
                }
            } }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

@Composable fun ModelHubScreen(vm: MainViewModel) {
    val states by vm.models.states().collectAsState()
    val installed by vm.installedModels.collectAsState()
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(24.dp))
            Text("Model Hub", style = MaterialTheme.typography.displayMedium)
            Text("Models download on demand into private app storage — the base app stays light.", style = MaterialTheme.typography.bodyLarge, color = Warm.Muted) }
        item { FeatureCard(Modifier.fillMaxWidth()) {
            Text(ModelCatalog.classical.name, style = MaterialTheme.typography.titleMedium)
            Text(ModelCatalog.classical.blurb, style = MaterialTheme.typography.bodySmall, color = Warm.Muted)
            BadgePill("Built in · Offline", coral = true)
        } }
        items(ModelCatalog.packs) { pack ->
            val st = states[pack.id]
            val isIn = installed.any { it.id == pack.id } || remember(st) { vm.models.isInstalled(pack) }
            FeatureCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium)
                    BadgePill("${pack.engine} ×${pack.scale}", coral = isIn)
                }
                Text(pack.blurb, style = MaterialTheme.typography.bodySmall, color = Warm.Muted)
                if (st?.error != null) Text(st.error, style = MaterialTheme.typography.bodySmall, color = Warm.Error)
                when {
                    isIn -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BadgePill("Downloaded")
                        WarmSecondaryButton("Delete") { scope.launch { vm.models.delete(pack) } }
                        WarmSecondaryButton("Use") { vm.select(pack) }
                    }
                    st?.active == true -> {
                        LinearProgressIndicator({ st.fraction }, Modifier.fillMaxWidth(), color = Warm.Primary, trackColor = Warm.HairlineSoft)
                        WarmSecondaryButton(if (st.fraction > 0f) "Pause" else "Starting…") { vm.models.pause(pack) }
                    }
                    else -> WarmPrimaryButton(if (st != null && st.fraction > 0f) "Resume download" else "Download") {
                        vm.models.download(pack, scope)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}
