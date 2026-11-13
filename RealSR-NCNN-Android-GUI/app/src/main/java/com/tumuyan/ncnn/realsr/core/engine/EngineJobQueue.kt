package com.tumuyan.ncnn.realsr.core.engine

import android.net.Uri
import java.util.Locale
import java.util.UUID

/**
 * Thread-safe FIFO queue enforcing strictly 1 active GPU/NCNN job at a time while
 * tracking human-readable "2 of 5" session positions and per-item cancellation.
 */
class EngineJobQueue {

    data class PendingEntry(
        val id: String = UUID.randomUUID().toString().take(10),
        val uri: Uri,
        val fileNameHint: String,
        val preset: UpscalePreset,
        val scale: Int,
        val modelSpec: ModelExecutionSpec?,
        val autoSaveToGallery: Boolean,
        val notifyOnCompletion: Boolean,
        val maxInputMP: Double? = null
    )

    data class ActiveLease(
        val entry: PendingEntry,
        val positionInSession: Int,
        val totalInSession: Int
    )

    enum class CancelItemResult {
        NOT_FOUND,
        REMOVED_PENDING,
        CANCEL_RUNNING
    }

    private val lock = Any()
    private val pending = ArrayDeque<PendingEntry>()
    private var activeEntry: PendingEntry? = null
    private var completedInSession: Int = 0
    private var sessionNotifyOnCompletion: Boolean = false

    fun enqueue(
        request: UpscaleRequest,
        fileNameResolver: (Int, Uri) -> String
    ): List<String> = synchronized(lock) {
        if (activeEntry == null && pending.isEmpty()) {
            completedInSession = 0
            sessionNotifyOnCompletion = false
        }
        if (request.notifyOnCompletion) {
            sessionNotifyOnCompletion = true
        }
        val ids = mutableListOf<String>()
        request.inputUris.forEachIndexed { idx, uri ->
            val entry = PendingEntry(
                uri = uri,
                fileNameHint = fileNameResolver(idx, uri),
                preset = request.preset,
                scale = request.scale,
                modelSpec = request.modelSpec,
                autoSaveToGallery = request.autoSaveToGallery,
                notifyOnCompletion = request.notifyOnCompletion,
                maxInputMP = request.maxInputMP
            )
            pending.addLast(entry)
            ids += entry.id
        }
        ids
    }

    fun pollNextLease(): ActiveLease? = synchronized(lock) {
        val next = pending.removeFirstOrNull()
        if (next == null) {
            activeEntry = null
            return null
        }
        activeEntry = next
        val pos = completedInSession + 1
        val total = completedInSession + 1 + pending.size
        ActiveLease(
            entry = next,
            positionInSession = pos,
            totalInSession = total
        )
    }

    fun markActiveFinished() = synchronized(lock) {
        if (activeEntry != null) {
            completedInSession += 1
            activeEntry = null
        }
        if (pending.isEmpty()) {
            completedInSession = 0
        }
    }

    fun enableSessionCompletionNotification() = synchronized(lock) {
        sessionNotifyOnCompletion = true
    }

    fun shouldNotifyOnSessionComplete(): Boolean = synchronized(lock) {
        sessionNotifyOnCompletion
    }

    fun cancelItem(itemId: String): CancelItemResult = synchronized(lock) {
        if (activeEntry?.id == itemId) {
            return CancelItemResult.CANCEL_RUNNING
        }
        val removed = pending.removeAll { it.id == itemId }
        if (removed) CancelItemResult.REMOVED_PENDING else CancelItemResult.NOT_FOUND
    }

    fun clearAll() = synchronized(lock) {
        pending.clear()
        activeEntry = null
        completedInSession = 0
        sessionNotifyOnCompletion = false
    }

    fun isIdle(): Boolean = synchronized(lock) {
        activeEntry == null && pending.isEmpty()
    }

    fun snapshot(): List<QueuedJobItem> = synchronized(lock) {
        val total = completedInSession + (if (activeEntry != null) 1 else 0) + pending.size
        val result = ArrayList<QueuedJobItem>((if (activeEntry != null) 1 else 0) + pending.size)
        val running = activeEntry
        var cursor = completedInSession + 1
        if (running != null) {
            result += QueuedJobItem(
                id = running.id,
                sourceUri = running.uri.toString(),
                fileName = running.fileNameHint,
                presetTitle = running.preset.title,
                scale = running.scale,
                isRunning = true,
                positionInBatch = cursor,
                totalInBatch = total
            )
            cursor += 1
        }
        for (item in pending) {
            result += QueuedJobItem(
                id = item.id,
                sourceUri = item.uri.toString(),
                fileName = item.fileNameHint,
                presetTitle = item.preset.title,
                scale = item.scale,
                isRunning = false,
                positionInBatch = cursor,
                totalInBatch = total
            )
            cursor += 1
        }
        result
    }
}

object OutputNameFormatter {
    private val UNSAFE_CHARS = Regex("""[^a-zA-Z0-9._\- ]""")

    /**
     * Produces "<original_stem>_x<scale>.<ext>" (e.g. "IMG_2048_x4.png"),
     * never mutating the original source name and stripping path/illegal characters.
     */
    fun buildGalleryFileName(
        originalDisplayName: String?,
        scale: Int,
        outputExtension: String
    ): String {
        val cleanExt = outputExtension.trim().trimStart('.').lowercase(Locale.US).ifEmpty { "png" }
        val rawBase = originalDisplayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            .orEmpty()
        val stem = if (rawBase.contains('.')) {
            rawBase.substringBeforeLast('.')
        } else {
            rawBase
        }
        val sanitizedStem = stem
            .replace(UNSAFE_CHARS, "_")
            .trim()
            .trim('.')
            .take(80)
            .ifEmpty { "Photo" }
        return "${sanitizedStem}_x${scale}.${cleanExt}"
    }
}
