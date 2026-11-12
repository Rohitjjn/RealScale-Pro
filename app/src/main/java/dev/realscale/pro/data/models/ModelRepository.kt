package dev.realscale.pro.data.models

import android.content.Context
import dev.realscale.pro.core.database.ModelDao
import dev.realscale.pro.core.database.ModelEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class DownloadState(val fraction: Float = 0f, val active: Boolean = false, val error: String? = null)

class ModelRepository(private val context: Context, private val dao: ModelDao) {
    private val root = File(context.filesDir, "models")
    private val jobs = mutableMapOf<String, Job>()
    private val states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    fun states(): StateFlow<Map<String, DownloadState>> = states
    fun installed() = dao.observeAll()
    fun dirFor(pack: ModelPack) = File(root, pack.dir)
    fun isInstalled(pack: ModelPack) = pack.files.all { File(dirFor(pack), it).length() > 0 }

    fun download(pack: ModelPack, scope: CoroutineScope) {
        if (jobs[pack.id]?.isActive == true) return
        jobs[pack.id] = scope.launch(Dispatchers.IO) {
            set(pack.id, DownloadState(active = true))
            try {
                val dir = dirFor(pack).apply { mkdirs() }
                pack.files.forEachIndexed { i, file ->
                    fetch(pack.url(file), File(dir, "$file.part"), File(dir, file)) { f ->
                        set(pack.id, DownloadState((i + f) / pack.files.size, active = true))
                    }
                }
                verify(pack)
                dao.upsert(ModelEntity(pack.id, pack.name, pack.engine, pack.scale, dir.absolutePath, shaOf(dir, pack), System.currentTimeMillis()))
                set(pack.id, DownloadState(1f))
            } catch (e: CancellationException) { set(pack.id, DownloadState(active = false)) ; throw e }
            catch (e: Exception) { set(pack.id, DownloadState(error = e.message ?: "Download failed")) }
        }
    }

    fun pause(pack: ModelPack) { jobs[pack.id]?.cancel() } // .part files are kept, so resume continues via Range
    suspend fun delete(pack: ModelPack) = withContext(Dispatchers.IO) { dirFor(pack).deleteRecursively(); dao.delete(pack.id) }

    /** Integrity check: all files present, non-empty, NCNN .param magic header intact. */
    fun verify(pack: ModelPack) {
        for (f in pack.files) {
            val file = File(dirFor(pack), f)
            check(file.length() > 0) { "Empty file $f" }
            if (f.endsWith(".param")) check(file.bufferedReader().readLine()?.startsWith("7767517") == true) { "Corrupt param $f" }
        }
    }

    private fun shaOf(dir: File, pack: ModelPack): String {
        val md = MessageDigest.getInstance("SHA-256")
        pack.files.sorted().forEach { f -> File(dir, f).inputStream().use { i -> val b = ByteArray(8192); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun fetch(url: String, part: File, dest: File, onProgress: (Float) -> Unit) {
        val offset = if (part.exists()) part.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val resume = offset > 0 && conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            val total = conn.contentLengthLong + if (resume) offset else 0
            RandomAccessFile(part, "rw").apply { seek(if (resume) offset else 0) }.use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024); var done = if (resume) offset else 0L
                    while (currentCoroutineContext().isActive) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                    currentCoroutineContext().ensureActive()
                }
            }
            check(part.renameTo(dest)) { "Could not finalize $dest" }
        } finally { conn.disconnect() }
    }

    private fun set(id: String, s: DownloadState) { states.value = states.value + (id to s) }
}
