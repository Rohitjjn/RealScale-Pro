package com.tumuyan.ncnn.realsr.ui

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
        customTreeUri: String?
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = mimeTypeFor(sourceFile)
            if (!customTreeUri.isNullOrBlank()) {
                val treeUri = Uri.parse(customTreeUri)
                val folder = DocumentFile.fromTreeUri(context, treeUri)
                if (folder != null && folder.canWrite()) {
                    val created = folder.createFile(mimeType, sourceFile.name)
                        ?: throw IllegalStateException("Unable to create output file in selected folder")
                    context.contentResolver.openOutputStream(created.uri)?.use { out ->
                        FileInputStream(sourceFile).use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("Unable to write to selected folder")
                    return@runCatching created.uri
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, sourceFile.name)
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
                val outFile = File(picturesDir, sourceFile.name)
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
        val authority = "${context.packageName}.fileprovider"
        val shareUri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file)
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share upscaled photo"))
    }

    private fun mimeTypeFor(file: File): String =
        when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
}
