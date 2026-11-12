package dev.realscale.pro.core.engine

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class EngineProgress(val fraction: Float, val logLine: String)

/**
 * Encapsulates the upstream NCNN execution shell: extracts `assets/realsr`
 * (core binaries only — models are never bundled) to cacheDir, chmod +x,
 * then runs the CLI via `sh` in that dir, exactly like upstream MainActivity.
 */
class NcnnEngine(private val context: Context) {
    val workDir: File by lazy { File(context.cacheDir, "realsr") }
    @Volatile private var process: Process? = null

    fun binary(name: String) = File(workDir, name)
    fun binariesReady() = CORE_BINARIES.any { binary(it).exists() }

    suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val assets = try { context.assets.list("realsr")?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        for (name in assets) {
            if (name.startsWith("models-")) continue // never extract bundled models
            val out = File(workDir, name); if (out.exists()) continue
            try { context.assets.open("realsr/$name").use { i -> out.outputStream().use { i.copyTo(it) } } } catch (e: Exception) { /* dir entry */ }
        }
        workDir.listFiles()?.forEach { it.setExecutable(true, false) }
        binariesReady()
    }

    fun run(command: List<String>): Flow<EngineProgress> = flow {
        val pb = ProcessBuilder(listOf("/system/bin/sh", "-c", command.joinToString(" ") { shellQuote(it) }))
            .directory(workDir).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = workDir.absolutePath
        val p = pb.start(); process = p
        try {
            val reader = p.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                emit(EngineProgress(NcnnCommandBuilder.parseProgress(line) ?: 0f, line))
            }
            if (p.waitFor() != 0) throw IllegalStateException("Engine exited with code ${p.exitValue()}")
            emit(EngineProgress(1f, "done"))
        } finally { process = null }
    }.flowOn(Dispatchers.IO)

    fun cancel() { process?.destroy() }

    private fun shellQuote(s: String) = if (s.startsWith("./") || s.all { it.isLetterOrDigit() || it in "-_./=" }) s
        else "'" + s.replace("'", "'\\''") + "'"

    companion object {
        val CORE_BINARIES = listOf("realsr-ncnn", "realcugan-ncnn", "waifu2x-ncnn", "srmd-ncnn", "resize-ncnn", "Anime4k", "magick")
    }
}
