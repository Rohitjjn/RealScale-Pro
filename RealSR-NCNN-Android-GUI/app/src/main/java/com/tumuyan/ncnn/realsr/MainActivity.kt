package com.tumuyan.ncnn.realsr

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.tumuyan.ncnn.realsr.ui.MediaIntentsHost
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import com.tumuyan.ncnn.realsr.ui.RealScaleDesignApp
import com.tumuyan.ncnn.realsr.ui.state.AppStore
import com.tumuyan.ncnn.realsr.ui.state.ThemePref
import com.tumuyan.ncnn.realsr.widget.RealScaleWidgetsUpdater
import kotlinx.coroutines.launch

/**
 * The one activity of the redesigned app: it owns the store, the system dialogs that the store asks
 * for through [AppStore.permissionRequester] and [AppStore.mediaIntents], and the incoming-image
 * intents (Open with / share target / widget / notification).
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: RealScaleAppContainer
    private lateinit var store: AppStore
    private lateinit var intents: MediaIntentsHost

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        applyEdgeToEdge()

        container = RealScaleAppContainer.getInstance(this)
        store = AppStore(this, container)
        intents = MediaIntentsHost(this, container, store)

        // Rotations and process death restore the Compose tree, not a new activity instance.
        if (savedInstanceState == null) consumeIncomingIntent(intent)

        setContent { RealScaleDesignApp(store) }
    }

    /**
     * System bars follow the app's own theme, not the system one: with Settings forced to Dark on a
     * light system (or the reverse) the status bar used to be tinted for the wrong theme, which
     * reads as a dark strip across the top of every screen.
     */
    private fun applyEdgeToEdge() {
        val pref = AppStore.persistedTheme(this)
        val systemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val appDark = when (pref) {
            ThemePref.Dark -> true
            ThemePref.Light -> false
            ThemePref.System -> systemDark
        }
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent) { appDark },
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { appDark },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (store.state.value.photoAccess) store.refreshPhotos()
        store.refreshDevice()
        // Keep the home-screen widgets in step with the app: the recent-creations widget shows the
        // last three results, so it has to be redrawn whenever the app comes back to the front.
        RealScaleWidgetsUpdater.refreshSoon(this)
    }

    private fun consumeIncomingIntent(incoming: Intent?) {
        if (incoming == null) return

        if (incoming.getBooleanExtra(EXTRA_OPEN_PHOTO_PICKER, false)) {
            incoming.removeExtra(EXTRA_OPEN_PHOTO_PICKER)
            intents.pickDocument()
            return
        }

        val creationId = incoming.getLongExtra(ProcessingService.EXTRA_OPEN_CREATION_ID, -1L)
        if (creationId > 0L) {
            incoming.removeExtra(ProcessingService.EXTRA_OPEN_CREATION_ID)
            lifecycleScope.launch {
                val row = runCatching { container.database.recentCreationDao().getById(creationId) }.getOrNull()
                if (row != null) store.openResult(row.id.toString()) else store.toast("That result is no longer available")
            }
            return
        }

        val uris = when (incoming.action) {
            Intent.ACTION_VIEW -> listOfNotNull(incoming.data)
            Intent.ACTION_SEND -> listOfNotNull(extractParcelableUri(incoming) ?: incoming.data)
            Intent.ACTION_SEND_MULTIPLE -> extractParcelableUriList(incoming)
            else -> emptyList()
        }.filter { it.scheme != "realscale" }
        if (uris.isEmpty()) return

        lifecycleScope.launch {
            uris.forEach { uri -> runCatching { intents.importFromUri(uri) } }
        }
    }

    private fun extractParcelableUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun extractParcelableUriList(intent: Intent): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }

    /** Test seam for the instrumentation smoke test. */
    internal fun storeForTest(): AppStore = store

    /** True when the current photo permission grants access to the whole library. */
    fun hasPhotoAccess(): Boolean {
        val permission = photoPermission()
        return ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun photoPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    @Suppress("unused")
    private fun systemPickVisual(): PickVisualMediaRequest =
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    companion object {
        const val EXTRA_OPEN_PHOTO_PICKER = "com.tumuyan.ncnn.realsr.EXTRA_OPEN_PHOTO_PICKER"
    }
}
