package dev.realscale.pro.ui

import android.app.Application
import android.graphics.*
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.realscale.pro.RealScaleApp
import dev.realscale.pro.core.database.CreationEntity
import dev.realscale.pro.core.engine.*
import dev.realscale.pro.data.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class StudioState(
    val input: Bitmap? = null, val output: Bitmap? = null,
    val selected: ModelPack = ModelCatalog.classical,
    val processing: Boolean = false, val fraction: Float = 0f,
    val status: String = "Pick an image to begin.", val startedAt: Long = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val appRef = app as RealScaleApp
    val models = appRef.modelRepo
    val studio = MutableStateFlow(StudioState())
    val creations: StateFlow<List<CreationEntity>> = appRef.db.creations().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val installedModels = models.installed().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var job: Job? = null
    private var inputFile: File? = null

    fun select(pack: ModelPack) { studio.value = studio.value.copy(selected = pack, output = null) }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@launch
            val dir = File(ctx.cacheDir, "realsr").apply { mkdirs() }
            inputFile = File(dir, "input.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            studio.value = studio.value.copy(input = bmp, output = null, status = "Ready — ${bmp.width}×${bmp.height}")
        }
    }

    fun onSharedImage(uri: Uri) = onImagePicked(uri)

    fun upscale() {
        val input = studio.value.input ?: return
        val pack = studio.value.selected
        job = viewModelScope.launch {
            studio.value = studio.value.copy(processing = true, fraction = 0f, startedAt = System.currentTimeMillis(), status = "Preparing engine…")
            try {
                val out = withContext(Dispatchers.IO) { runUpscale(input, pack) }
                val dt = System.currentTimeMillis() - studio.value.startedAt
                inputFile?.parentFile?.let { dir ->
                    appRef.db.creations().insert(CreationEntity(inputPath = File(dir, "input.png").absolutePath,
                        outputPath = File(dir, "output.png").absolutePath, modelName = pack.name,
                        scale = pack.scale, createdAt = System.currentTimeMillis(), durationMs = dt))
                }
                studio.value = studio.value.copy(processing = false, fraction = 1f, output = out, status = "Done in ${dt / 1000}s — drag the divider to compare.")
            } catch (e: CancellationException) {
                appRef.engine.cancel(); studio.value = studio.value.copy(processing = false, status = "Cancelled.")
            } catch (e: Exception) {
                studio.value = studio.value.copy(processing = false, status = e.message ?: "Processing failed.")
            }
        }
    }

    private suspend fun runUpscale(input: Bitmap, pack: ModelPack): Bitmap {
        val engine = appRef.engine
        val ready = engine.prepare()
        val dir = engine.workDir
        val outFile = File(dir, "output.png")
        if (pack.id == ModelCatalog.classical.id) { // in-app Lanczos-3: offline, no binary or model needed
            val (px, size) = LanczosResampler.resize(pixelsOf(input), input.width, input.height, pack.scale)
            return Bitmap.createBitmap(px, size[0], size[1], Bitmap.Config.ARGB_8888)
                .also { b -> outFile.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        check(ready) { "Core NCNN binaries unavailable on this build. Classical Lanczos works offline — select it, or use a CI build with binaries." }
        val modelDir = when {
            pack.id == ModelCatalog.classical.id -> null
            else -> models.dirFor(pack).also { check(models.isInstalled(pack)) { "Model not downloaded yet — get it in Model Hub." } }
        }
        val cmd = if (modelDir == null) NcnnCommandBuilder.classicalResize("input.png", "output.png", pack.scale)
            else NcnnCommandBuilder.upscale(pack.binary, "input.png", "output.png", modelDir.absolutePath, pack.scale)
        engine.run(cmd).collect { p -> if (p.fraction > 0f) studio.value = studio.value.copy(fraction = p.fraction, status = p.logLine.take(80)) }
        check(outFile.exists()) { "Engine produced no output." }
        return BitmapFactory.decodeFile(outFile.absolutePath)
    }

    fun cancel() { job?.cancel() }

    private fun pixelsOf(b: Bitmap): IntArray { val a = IntArray(b.width * b.height); b.getPixels(a, 0, b.width, 0, 0, b.width, b.height); return a }
}
