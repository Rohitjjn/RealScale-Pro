package com.tumuyan.ncnn.realsr.core.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** A real photo from MediaStore — the only photos the app ever shows from the gallery. */
data class GalleryImage(
    val id: Long,
    val uri: Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val dateTakenMs: Long,
    val bucketId: String?,
    val bucketName: String?,
    val mimeType: String,
) {
    val megapixels: Double get() = (width.toLong() * height.toLong()) / 1e6
}

data class GalleryAlbum(
    val bucketId: String,
    val name: String,
    val cover: GalleryImage,
    val count: Int,
)

object GalleryMediaStore {

    const val PAGE_LIMIT = 800

    fun requiredPermissions(): List<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                add(Manifest.permission.READ_MEDIA_IMAGES)
            else -> add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }.distinct()

    fun hasReadImages(context: Context): Boolean {
        val pm = context.packageManager
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                pm.checkPermission(Manifest.permission.READ_MEDIA_IMAGES, context.packageName) == PackageManager.PERMISSION_GRANTED ||
                    pm.checkPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, context.packageName) == PackageManager.PERMISSION_GRANTED
            else ->
                pm.checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, context.packageName) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Recent images, newest first. Returns an empty list when permission is missing or the query fails. */
    fun queryRecentImages(context: Context, limit: Int = PAGE_LIMIT): List<GalleryImage> {
        if (!hasReadImages(context)) return emptyList()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val images = mutableListOf<GalleryImage>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                while (cursor.moveToNext() && images.size < limit) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    images += GalleryImage(
                        id = id,
                        uri = uri,
                        name = cursor.getString(nameCol) ?: "Photo ${id}",
                        width = cursor.getInt(wCol),
                        height = cursor.getInt(hCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        dateTakenMs = cursor.getLong(dateCol),
                        bucketId = if (bucketCol >= 0) cursor.getString(bucketCol) else null,
                        bucketName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null,
                        mimeType = if (mimeCol >= 0) (cursor.getString(mimeCol) ?: "image/jpeg") else "image/jpeg",
                    )
                }
            }
        }.onFailure {
            android.util.Log.w("GalleryMediaStore", "MediaStore query failed", it)
        }
        return images
    }

    /** Buckets derived from a recent-images snapshot, newest bucket first. */
    fun albumsFrom(images: List<GalleryImage>): List<GalleryAlbum> =
        images.filter { it.bucketId != null }
            .groupBy { it.bucketId!! }
            .map { (bucketId, items) ->
                val newest = items.maxByOrNull { it.dateTakenMs } ?: items.first()
                GalleryAlbum(
                    bucketId = bucketId,
                    name = newest.bucketName?.takeIf { it.isNotBlank() } ?: "Camera",
                    cover = newest,
                    count = items.size,
                )
            }
            .sortedByDescending { it.cover.dateTakenMs }

    fun imagesInBucket(images: List<GalleryImage>, bucketId: String): List<GalleryImage> =
        images.filter { it.bucketId == bucketId }.sortedByDescending { it.dateTakenMs }
}
