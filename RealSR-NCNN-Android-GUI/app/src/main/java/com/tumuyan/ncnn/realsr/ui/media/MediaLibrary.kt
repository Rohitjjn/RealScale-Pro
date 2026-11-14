package com.tumuyan.ncnn.realsr.ui.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.ui.state.PhotoItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Real photos from the device: the newest `MediaStore.Images` rows, exactly what the prototype
 * fakes with SAMPLE_PHOTOS. Nothing here is mocked — name, size, dimensions, album and date come
 * from the media database, and the date label is built the way the prototype prints it
 * ("Today, 6:42 PM", "Yesterday, 9:58 PM", "Sunday, 5:03 PM", "Last week", "12 Mar 2024").
 */
class MediaLibrary(private val context: Context) {

    fun queryImages(limit: Int = 240): List<PhotoItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.RELATIVE_PATH,
        )
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val items = ArrayList<PhotoItem>(limit)
        val cursor: Cursor? = runCatching {
            context.contentResolver.query(collection, projection, null, null, "$sort LIMIT $limit")
        }.getOrNull()
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: "IMG_$id.jpg"
                val width = c.getInt(wCol)
                val height = c.getInt(hCol)
                if (width <= 0 || height <= 0) continue
                val sizeBytes = c.getLong(sizeCol)
                val addedSec = c.getLong(dateCol)
                val bucket = c.getString(bucketCol) ?: ""
                val mime = c.getString(mimeCol) ?: ""
                val relativePath = runCatching { c.getString(pathCol) }.getOrNull() ?: ""
                val ext = extensionOf(name, mime)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                items.add(
                    PhotoItem(
                        id = "media:$id",
                        uri = uri,
                        name = name,
                        width = width,
                        height = height,
                        sizeMB = Math.round(sizeBytes / 1048576.0 * 10) / 10.0,
                        dateLabel = dateLabel(addedSec * 1000),
                        type = classify(name, mime, relativePath),
                        lowConfidence = false,
                        album = bucket.ifBlank { "Pictures" },
                        ext = ext,
                        dateTaken = addedSec * 1000L,
                    ),
                )
            }
        }
        return items
    }

    /** Reads the metadata of a single (possibly freshly imported) document. */
    fun photoFromUri(uri: Uri): PhotoItem? {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "photo.jpg"
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val sizeBytes = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }.takeIf { it != null && it > 0 }
                ?: resolver.openInputStream(uri)?.use { it.available().toLong() }
        }.getOrNull() ?: 0L
        runCatching {
            resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        }
        val width = bounds.outWidth.coerceAtLeast(1)
        val height = bounds.outHeight.coerceAtLeast(1)
        return PhotoItem(
            id = "uri:$uri",
            uri = uri.toString(),
            name = name,
            width = width,
            height = height,
            sizeMB = Math.round(sizeBytes / 1048576.0 * 10) / 10.0,
            dateLabel = "Just now",
            type = classify(name, mime, uri.toString()),
            album = "Imported",
            ext = extensionOf(name, mime),
            imported = true,
            dateTaken = System.currentTimeMillis(),
        )
    }

    private fun extensionOf(name: String, mime: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (fromName in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")) {
            return if (fromName == "jpeg") "jpg" else fromName
        }
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase(Locale.US) ?: "jpg"
        return if (fromMime == "jpeg") "jpg" else fromMime
    }

    /**
     * There is no on-device illustration classifier in this app, so the type is a documented
     * heuristic: PNG/WebP art in illustration-ish folders counts as anime, everything the camera
     * produced counts as a photo. The user can always override it in the manual settings.
     */
    private fun classify(name: String, mime: String, path: String): ImageType {
        val haystack = "$name $path".lowercase(Locale.US)
        val artHints = listOf("illust", "anime", "art", "manga", "pixiv", "sketch", "drawing", "waifu")
        val flat = mime == "image/png" || mime == "image/webp"
        val looksLikeArt = artHints.any { haystack.contains(it) } || (flat && !haystack.contains("screenshot"))
        return if (looksLikeArt) ImageType.Anime else ImageType.Photo
    }

    private fun dateLabel(epochMs: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = epochMs }
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "Today, $time"
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        ) {
            return "Yesterday, $time"
        }
        val days = ((now.timeInMillis - epochMs) / 86_400_000L).toInt()
        if (days in 2..6) return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(epochMs)) + ", $time"
        if (days in 7..13) return "Last week"
        return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
    }
}
