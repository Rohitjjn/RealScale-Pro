package com.tumuyan.ncnn.realsr.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
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

    /**
     * Settings → Output → Save folder. `OpenDocumentTree` is the only picker that yields a tree the
     * app can keep writing to after a reboot, provided the grant is persisted.
     */
    private val pickFolder = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { activity.contentResolver.takePersistableUriPermission(uri, flags) }
        val label = runCatching { DocumentFile.fromTreeUri(activity, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
        store.onSaveFolderPicked(uri.toString(), label)
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

    override fun pickFromFiles() {
        // `OpenDocument` is the full DocumentsUI browser: internal storage, SD cards, Drive, and it
        // needs no permission because the user hands us a single URI.
        runCatching { pickDocument.launch(arrayOf("image/*")) }.onFailure {
            store.toast("No file browser on this device")
        }
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

    /**
     * Feedback: opens the mail app with the user's text already in the body. `ACTION_SENDTO` +
     * `mailto:` is the only form every mail client (Gmail, Outlook, Samsung Mail) accepts; the
     * `EXTRA_SUBJECT` / `EXTRA_TEXT` extras prefill the compose screen.
     */
    override fun sendFeedbackMail(subject: String, body: String): Boolean {
        val address = AppStore.FEEDBACK_EMAIL
        val sendTo = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { activity.startActivity(Intent.createChooser(sendTo, "Send feedback")) }.isSuccess
        if (opened) return true
        // Some tablets ship without a mailto handler; fall back to a plain text share.
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { activity.startActivity(Intent.createChooser(share, "Send feedback")) }.isSuccess
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

    override fun pickSaveFolder() {
        runCatching { pickFolder.launch(null) }.onFailure {
            store.toast("No folder picker on this device")
        }
    }

    /** Import one image that arrived from another app. */
    fun importFromUri(uri: Uri): Boolean = importUris(listOf(uri))

    /**
     * Imports through a cache copy, so the image still opens after the sending app is gone. Pushes
     * the config screen once, for the first image of the batch.
     */
    fun importUris(uris: List<Uri>): Boolean {
        val imported = importQuiet(uris)
        if (!imported) store.toast("That image could not be opened")
        return imported
    }

    /** Same intake without the failure toast, so a batch keeps one message at most. */
    private fun importQuiet(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        // One unreadable uri must not abort a batch that also contains a good image.
        val imported = uris.mapNotNull { uri ->
            runCatching { copyToCache(uri)?.let { media.photoFromUri(it) } }.getOrNull()
        }
        if (imported.isEmpty()) return false
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
        val copied = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false) || runCatching {
            // Some OEM providers refuse openInputStream but still hand out a readable descriptor.
            activity.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } != null
        }.getOrDefault(false)
        if (!copied || !target.exists() || target.length() == 0L) {
            target.delete()
            return null
        }
        // MediaStore needs a real row; a file in the app cache is already readable.
        return Uri.fromFile(target)
    }
}
