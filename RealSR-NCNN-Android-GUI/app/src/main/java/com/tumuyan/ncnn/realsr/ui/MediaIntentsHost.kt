package com.tumuyan.ncnn.realsr.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tumuyan.ncnn.realsr.core.device.HapticKind
import com.tumuyan.ncnn.realsr.ui.media.MediaLibrary
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.MediaIntents
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns every entry point that leaves the app's own screens. Registered in the activity's
 * `onCreate`, so the contracts are always available before the user can reach a button.
 */
class MediaIntentsHost(
    private val activity: ComponentActivity,
    private val container: RealScaleAppContainer,
    private val store: AppStore,
) : MediaIntents {

    private val counter = AtomicInteger(0)

    /** Set just before `TakePicture` launches; the contract writes the full image here. */
    private var cameraUri: Uri? = null

    private val media = MediaLibrary(activity)

    private val pickDocument = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUris(listOf(uri))
    }

    private val pickVisual = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importUris(listOf(uri))
    }

    private val takePicture = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        cameraUri = null
        if (saved && uri != null) importUris(listOf(uri)) else if (!saved) store.toast("No photo taken")
    }

    private val photoPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val full = result[Manifest.permission.READ_MEDIA_IMAGES] == true ||
            result[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        val limited = Build.VERSION.SDK_INT >= 34 &&
            result[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        store.onPermissionResult(photos = true, notifications = false, granted = full || limited, limited = limited)
        if (!full && !limited) store.toast("You can still pick single photos with the system picker")
    }

    private val notificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        store.onPermissionResult(photos = false, notifications = true, granted = granted, limited = false)
    }

    init {
        store.mediaIntents = this
        store.permissionRequester = { photos, notifications ->
            when {
                photos -> requestPhotos()
                notifications -> requestNotifications()
            }
        }
    }

    /** The permission set a modern Android install needs; Play reviews the limited variant too. */
    fun photoPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun requestPhotos() {
        val granted = photoPermissions().any {
            ContextCompat.checkSelfPermission(activity, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            store.grantPhotoAccess(limited = Build.VERSION.SDK_INT >= 34 &&
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.READ_MEDIA_IMAGES,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED)
        } else {
            photoPermission.launch(photoPermissions())
        }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) {
            store.finishNotifications(accepted = true)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) store.finishNotifications(accepted = true) else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun pickDocument() {
        // The photo picker needs no permission; the document picker keeps SD cards and cloud files.
        if (Build.VERSION.SDK_INT >= 33) {
            pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            pickDocument.launch(arrayOf("image/*"))
        }
    }

    override fun takePhoto() {
        store.markCameraAsked()
        val dir = File(activity.cacheDir, "realsr").apply { mkdirs() }
        val file = File(dir, "camera-${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            store.toast("The camera is not available")
            return
        }
        cameraUri = uri
        runCatching { takePicture.launch(uri) }.onFailure {
            cameraUri = null
            store.toast("The camera is not available")
        }
    }

    override fun share(uri: Uri, name: String, mime: String): Boolean {
        val sendable = when (uri.scheme) {
            "content" -> uri
            "file" -> runCatching {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", File(uri.path!!))
            }.getOrNull()
            else -> uri
        } ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, sendable)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (activity.packageManager.resolveActivity(chooser, 0) == null) return false
        runCatching { activity.startActivity(chooser) }
        return true
    }

    override fun pasteImage(): Boolean {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = clipboard.primaryClip ?: return false
        if (clip.itemCount == 0) return false
        val uri = clip.getItemAt(0).uri ?: return false
        return importUris(listOf(uri))
    }

    /** Import one image that arrived from another app. */
    fun importFromUri(uri: Uri): Boolean = importUris(listOf(uri))

    /**
     * Imports through a cache copy, so the image still opens after the sending app is gone. Pushes
     * the config screen once, for the first image of the batch.
     */
    fun importUris(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        val imported = uris.mapNotNull { uri -> copyToCache(uri)?.let { media.photoFromUri(it) } }
        if (imported.isEmpty()) {
            store.toast("That image could not be opened")
            return false
        }
        imported.forEachIndexed { index, photo ->
            if (index == 0) store.onImportedPhoto(photo) else store.addImportedPhoto(photo)
        }
        if (imported.size > 1) store.toast("${imported.size} images added")
        store.haptic(HapticKind.Light)
        return true
    }

    private fun copyToCache(uri: Uri): Uri? {
        if (uri.scheme == "file") return uri
        val dir = File(activity.cacheDir, "imports").apply { mkdirs() }
        val ext = (activity.contentResolver.getType(uri) ?: "image/jpeg").substringAfterLast('/').takeIf {
            it in setOf("jpeg", "jpg", "png", "webp", "heic")
        } ?: "jpg"
        val target = File(dir, "import-${System.currentTimeMillis()}-${counter.incrementAndGet()}.$ext")
        return runCatching {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            // MediaStore needs a real row; a file in the app cache is already readable.
            Uri.fromFile(target)
        }.getOrNull()
    }
}
