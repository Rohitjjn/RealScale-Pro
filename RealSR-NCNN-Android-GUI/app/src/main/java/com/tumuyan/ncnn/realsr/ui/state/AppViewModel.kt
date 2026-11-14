package com.tumuyan.ncnn.realsr.ui.state

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tumuyan.ncnn.realsr.core.database.RealScaleDatabase
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.EtaTracker
import com.tumuyan.ncnn.realsr.core.engine.ImageContentClassifier
import com.tumuyan.ncnn.realsr.core.engine.UpscaleEngine
import com.tumuyan.ncnn.realsr.core.engine.UpscaleResult
import com.tumuyan.ncnn.realsr.core.engine.planner.DeviceProfile
import com.tumuyan.ncnn.realsr.core.engine.planner.DeviceProfiler
import com.tumuyan.ncnn.realsr.core.engine.planner.ImageType
import com.tumuyan.ncnn.realsr.core.engine.planner.JobPrefs
import com.tumuyan.ncnn.realsr.core.engine.planner.ModeId
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.core.model.ModelRepository
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

enum class HapticKind { LIGHT, MEDIUM, TICK, SUCCESS, WARNING }

/** An image the app is about to process — from gallery, files, camera, clipboard or a result. Session-scoped. */
data class SessionPhoto(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val bucketName: String?,
    val dateTakenMs: Long?,
    val source: Source,
    val detectedType: ImageType? = null,   // null = classify at plan time
) {
    enum class Source { GALLERY, IMPORTED, CAMERA, CLIPBOARD, RESULT }

    val megapixels: Double get() = (width.toLong() * height.toLong()) / 1e6
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase().ifBlank {
            when (mimeType) {
                "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg"
            }
        }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = RealScaleAppContainer.getInstance(application)
    val engine: UpscaleEngine = container.engine
    val modelRepository: ModelRepository = container.modelRepository
    val database: RealScaleDatabase = container.database
    private val dataStore = AppDataStore(application)
    private val etaTracker = EtaTracker(application)
    private val json = Json { ignoreUnknownKeys = true }

    val settings: StateFlow<AppSettings> = dataStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val engineState: StateFlow<EngineState> = engine.state
    val queue: StateFlow<List<com.tumuyan.ncnn.realsr.core.engine.QueuedJobItem>> = engine.queue

    val modelCatalog: StateFlow<List<CatalogModelState>> = modelRepository.catalogModels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalInstalledBytes: StateFlow<Long> = modelRepository.totalInstalledBytes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val recentCreations = database.recentCreationDao().observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast = _toast

    private val _pendingInput = MutableStateFlow<SessionPhoto?>(null)
    val pendingInput = _pendingInput.asStateFlow()

    private val _viewerTarget = MutableStateFlow<SessionPhoto?>(null)
    val viewerTarget = _viewerTarget.asStateFlow()

    private val _lastResult = MutableStateFlow<UpscaleResult?>(null)
    val lastResult = _lastResult.asStateFlow()

    private val _deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val deviceProfile = _deviceProfile.asStateFlow()

    private val _etaSecPerMp = MutableStateFlow<Map<String, Double>>(emptyMap())
    val etaSecPerMp = _etaSecPerMp.asStateFlow()

    private val _shareUris = MutableStateFlow<List<Uri>>(emptyList())
    val shareUris = _shareUris.asStateFlow()

    private val _albumBucketId = MutableStateFlow<String?>(null)
    val albumBucketId = _albumBucketId.asStateFlow()
    fun openAlbum(bucketId: String) { _albumBucketId.value = bucketId }

    fun showToast(message: String) = viewModelScope.launch { _toast.emit(message) }

    fun clearShareUris() { _shareUris.value = emptyList() }

    /** ACTION_SEND entry point from MainActivity. */
    fun acceptShareIntent(uris: List<Uri>) {
        if (uris.isNotEmpty()) _shareUris.value = uris
    }

    fun setPendingInput(photo: SessionPhoto) { _pendingInput.value = photo }
    fun clearPendingInput() { _pendingInput.value = null }

    fun openViewer(photo: SessionPhoto) { _viewerTarget.value = photo }

    fun clearViewerTarget() { _viewerTarget.value = null }

    fun acknowledgeResult() { _lastResult.value = null }

    /** View a specific past creation on the Result screen (e.g. tapped from My Upscales). */
    fun selectResult(creation: RecentCreationEntity) {
        _lastResult.value = UpscaleResult(creations = listOf(creation), totalDurationMs = creation.durationMs)
    }

    init {
        // Keep the last real result for the Result screen.
        viewModelScope.launch {
            engineState
                .filterIsInstance<EngineState.Completed>()
                .collect { _lastResult.value = it.result }
        }
    }

    fun refreshDeviceProfile() {
        viewModelScope.launch {
            _deviceProfile.value = withContext(Dispatchers.IO) {
                DeviceProfiler.probe(getApplication(), binariesPresent())
            }
        }
    }

    fun binariesPresent(): Boolean {
        val app = getApplication<Application>()
        val realsr = File(app.cacheDir, "realsr")
        val stamp = File(realsr, ".engine_ready_v${com.tumuyan.ncnn.realsr.BuildConfig.VERSION_CODE}")
        val resize = File(realsr, "resize-ncnn")
        return stamp.exists() && resize.exists() && resize.length() > 0L
    }

    fun probeBinariesThenRefresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { engine.ensureInitialized(forceReset = false) }
            }
            _deviceProfile.value = withContext(Dispatchers.IO) {
                DeviceProfiler.probe(getApplication(), binariesPresent())
            }
        }
    }

    fun refreshEta() {
        viewModelScope.launch {
            _etaSecPerMp.value = withContext(Dispatchers.IO) { etaTracker.snapshot() }
        }
    }

    /* ---------- JobPrefs ---------- */
    fun jobPrefs(mode: ModeId): JobPrefs {
        val raw = if (mode == ModeId.SUPER) settings.value.prefsSuper else settings.value.prefsUpscale
        if (raw.isBlank()) return JobPrefs.default()
        return runCatching { json.decodeFromString(JobPrefs.serializer(), raw) }.getOrDefault(JobPrefs.default())
    }

    fun updateJobPrefs(mode: ModeId, transform: (JobPrefs) -> JobPrefs) {
        viewModelScope.launch {
            dataStore.update { s ->
                val current = runCatching {
                    json.decodeFromString(JobPrefs.serializer(), if (mode == ModeId.SUPER) s.prefsSuper else s.prefsUpscale)
                }.getOrDefault(JobPrefs.default())
                val updated = transform(current)
                val encoded = json.encodeToString(JobPrefs.serializer(), updated)
                if (mode == ModeId.SUPER) s.copy(prefsSuper = encoded) else s.copy(prefsUpscale = encoded)
            }
        }
    }

    /* ---------- Settings ---------- */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = settings.value
            dataStore.update(transform)
            val after = dataStore.current()
            // Mirror engine-owned settings into engine prefs (they drive execution).
            if (before.wifiOnly != after.wifiOnly ||
                before.keepAwake != after.keepAwake ||
                before.haptics != after.haptics ||
                before.keepMeta != after.keepMeta
            ) {
                engine.updatePreferences {
                    it.copy(
                        wifiOnlyDownloads = after.wifiOnly,
                        keepScreenOnDuringEnhance = after.keepAwake,
                        hapticsEnabled = after.haptics,
                        keepMetadata = after.keepMeta,
                    )
                }
            }
        }
    }

    fun completeOnboarding() = viewModelScope.launch { dataStore.update { it.copy(onboarded = true) } }
    fun markSuperIntroSeen() = viewModelScope.launch { dataStore.update { it.copy(superIntroSeen = true) } }
    fun setMode(mode: String) = viewModelScope.launch { dataStore.update { it.copy(mode = mode) } }

    /* ---------- Haptics (design kinds; honored only when enabled) ---------- */
    fun haptic(kind: HapticKind) {
        if (!settings.value.haptics) return
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = app.getSystemService(VibratorManager::class.java)
            val vibrator = manager?.defaultVibrator ?: return
            val effect = when (kind) {
                HapticKind.LIGHT -> VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticKind.MEDIUM -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticKind.TICK -> VibrationEffect.createOneShot(6, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticKind.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 14, 70, 24), -1)
                HapticKind.WARNING -> VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), -1)
            }
            vibrator.vibrate(effect)
            return
        }
        @Suppress("DEPRECATION")
        val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        @Suppress("DEPRECATION")
        when (kind) {
            HapticKind.LIGHT -> vibrator.vibrate(10)
            HapticKind.MEDIUM -> vibrator.vibrate(20)
            HapticKind.TICK -> vibrator.vibrate(6)
            HapticKind.SUCCESS -> vibrator.vibrate(longArrayOf(0, 14, 70, 24), -1)
            HapticKind.WARNING -> vibrator.vibrate(longArrayOf(0, 30, 50, 30), -1)
        }
    }

    /* ---------- Session item construction ---------- */
    /** Builds a [SessionPhoto] from any image Uri (content:// or file://). Returns null when unreadable. */
    suspend fun probeSessionPhoto(uri: Uri, source: SessionPhoto.Source, fallbackName: String? = null): SessionPhoto? =
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val (name, size) = displayNameAndSize(uri, fallbackName)
            val mime = app.contentResolver.getType(uri) ?: mimeFromExtension(name)
            val bounds = runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                opts
            }.getOrNull()
            val w = bounds?.outWidth?.takeIf { it > 0 } ?: 0
            val h = bounds?.outHeight?.takeIf { it > 0 } ?: 0
            if (w <= 0 || h <= 0) return@withContext null
            SessionPhoto(
                uri = uri,
                name = name,
                width = w,
                height = h,
                sizeBytes = size,
                mimeType = mime,
                bucketName = null,
                dateTakenMs = null,
                source = source,
            )
        }

    /** Classify a session photo's content (photo vs anime) with the on-device heuristic. Best effort. */
    suspend fun classify(photo: SessionPhoto): ImageType? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val opts = BitmapFactory.Options().apply { inSampleSize = 32 }
            val bmp = app.contentResolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
            ImageContentClassifier.classifyBitmap(bmp)
        }.getOrNull()?.let { presetId ->
            when (presetId) {
                com.tumuyan.ncnn.realsr.core.engine.PresetId.ANIME -> ImageType.ANIME
                com.tumuyan.ncnn.realsr.core.engine.PresetId.PHOTO -> ImageType.PHOTO
                else -> null
            }
        }
    }

    private fun displayNameAndSize(uri: Uri, fallbackName: String?): Pair<String, Long> {
        val app = getApplication<Application>()
        var name = fallbackName
        var size = 0L
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        return (name ?: uri.lastPathSegment ?: "photo_${System.currentTimeMillis()}") to size
    }

    private fun mimeFromExtension(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "bmp" -> "image/bmp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}
