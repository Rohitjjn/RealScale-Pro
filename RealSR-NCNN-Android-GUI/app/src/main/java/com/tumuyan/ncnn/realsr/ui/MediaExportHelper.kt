package com.tumuyan.ncnn.realsr.ui

import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object MediaExportHelper {

    suspend fun saveToGalleryOrFolder(
        context: Context,
        sourceFile: File,
        customTreeUri: String?,
        displayFileName: String? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val targetName = displayFileName?.takeIf { it.isNotBlank() } ?: sourceFile.name
            val mimeType = mimeTypeFor(sourceFile)
            if (!customTreeUri.isNullOrBlank()) {
                val treeUri = Uri.parse(customTreeUri)
                val folder = DocumentFile.fromTreeUri(context, treeUri)
                if (folder != null && folder.canWrite()) {
                    val created = folder.createFile(mimeType, targetName)
                        ?: throw IllegalStateException("Unable to create output file in selected folder")
                    context.contentResolver.openOutputStream(created.uri)?.use { out ->
                        FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Unable to write to selected folder")
                    return@runCatching created.uri
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, targetName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RealScale-Pro")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create Gallery entry")
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Could not write image to Gallery")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "RealScale-Pro"
                ).apply { mkdirs() }
                val outFile = uniqueFileInDir(picturesDir, targetName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(outFile).use { out -> input.copyTo(out) }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                Uri.fromFile(outFile)
            }
        }
    }

    fun shareImage(context: Context, file: File) {
        val chooser = createShareChooserIntent(context, file)
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun buildSharePendingIntent(context: Context, file: File, requestCode: Int): PendingIntent {
        val chooser = createShareChooserIntent(context, file).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(context, requestCode, chooser, flags)
    }

    private fun createShareChooserIntent(context: Context, file: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val shareUri = FileProvider.getUriForFile(context, authority, file)
        val mime = mimeTypeFor(file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newRawUri("Enhanced photo", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share enhanced photo").apply {
            clipData = ClipData.newRawUri("Enhanced photo", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun uniqueFileInDir(dir: File, desiredName: String): File {
        val initial = File(dir, desiredName)
        if (!initial.exists()) return initial
        val stem = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        for (i in 1..999) {
            val candidate = if (ext.isNotEmpty()) {
                File(dir, "${stem}_$i.$ext")
            } else {
                File(dir, "${stem}_$i")
            }
            if (!candidate.exists()) return candidate
        }
        return File(dir, "${System.currentTimeMillis()}_$desiredName")
    }

    fun mimeTypeFor(file: File): String =
        when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
}
