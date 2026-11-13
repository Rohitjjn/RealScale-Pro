package com.tumuyan.ncnn.realsr

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tumuyan.ncnn.realsr.core.database.RecentCreationEntity
import com.tumuyan.ncnn.realsr.core.engine.EngineState
import com.tumuyan.ncnn.realsr.core.engine.ExternalOpenMode
import com.tumuyan.ncnn.realsr.core.engine.ImageIntakeManager
import com.tumuyan.ncnn.realsr.core.engine.OutputNameFormatter
import com.tumuyan.ncnn.realsr.core.engine.PresetId
import com.tumuyan.ncnn.realsr.core.engine.StagedInputPhoto
import com.tumuyan.ncnn.realsr.core.engine.UpscalePreset
import com.tumuyan.ncnn.realsr.core.engine.UpscaleRequest
import com.tumuyan.ncnn.realsr.core.model.CatalogModelState
import com.tumuyan.ncnn.realsr.ui.MediaExportHelper
import com.tumuyan.ncnn.realsr.ui.RealScaleAppContainer
import com.tumuyan.ncnn.realsr.ui.screens.QuickUpscaleSheet
import com.tumuyan.ncnn.realsr.ui.theme.RealScaleTheme
import com.tumuyan.ncnn.realsr.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.io.File

/**
 * Compact translucent bottom-sheet activity launched when opening an image from any external
 * Android app (`ACTION_VIEW`, `image/*`). Runs in its own task affinity excluded from Recents.
 */
class QuickUpscaleActivity : ComponentActivity() {

    private val container by lazy { RealScaleAppContainer.getInstance(application) }
    private val uiPrefs by lazy { getSharedPreferences("realscale_ui_prefs", Context.MODE_PRIVATE) }

    private var stagedPhotoState by mutableStateOf<StagedInputPhoto?>(null)
    private var intakeErrorState by mutableStateOf<String?>(null)
    private var selectedPresetState by mutableStateOf(UpscalePreset.INSTANT)
    private var selectedScaleState by mutableIntStateOf(2)
    private var startedFromThisSheet by mutableStateOf(false)
    private var completedCreationState by mutableStateOf<RecentCreationEntity?>(null)
    private var savedMessageState by mutableStateOf<String?>(null)
    private var showNotifRationaleState by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Regardless of whether notifications were allowed or denied, background processing continues
        proceedToBackgroundAndClose()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val incomingUri = extractIncomingUri(intent)
        val forceQuickSheet = intent?.getBooleanExtra(EXTRA_FORCE_QUICK_SHEET, false) == true
        val prefs = container.engine.preferences.value

        if (!forceQuickSheet && prefs.externalOpenMode == ExternalOpenMode.FULL_APP && incomingUri != null) {
            forwardToFullApp(incomingUri, creationId = null)
            finish()
            return
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Preselect last-used preset & scale
        val initialPreset = UpscalePreset.fromId(prefs.lastUsedPresetId)
        selectedPresetState = initialPreset
        selectedScaleState = if (prefs.lastUsedScale in initialPreset.supportedScales) {
            prefs.lastUsedScale
        } else {
            initialPreset.defaultScale
        }

        if (incomingUri == null) {
            intakeErrorState = "No image was provided to enhance."
        } else {
            stageIncomingImage(incomingUri)
        }

        lifecycleScope.launch {
            container.engine.state.collect { st ->
                if (startedFromThisSheet && st is EngineState.Completed) {
                    val stagedUriStr = stagedPhotoState?.stagedUri?.toString()
                    val match = st.result.creations.lastOrNull { it.sourceUri == stagedUriStr }
                        ?: st.result.creations.lastOrNull()
                    if (match != null) {
                        completedCreationState = match
                    }
                }
            }
        }

        setContent {
            val engineState by container.engine.state.collectAsStateWithLifecycle()
            val enginePrefs by container.engine.preferences.collectAsStateWithLifecycle()
            val queue by container.engine.queue.collectAsStateWithLifecycle()
            val catalogModels by container.modelRepository.catalogModels.collectAsStateWithLifecycle(
                initialValue = emptyList()
            )
            val themeMode = loadThemeMode()

            RealScaleTheme(themeMode = themeMode) {
                QuickUpscaleSheet(
                    stagedPhoto = stagedPhotoState,
                    intakeErrorMessage = intakeErrorState,
                    selectedPreset = selectedPresetState,
                    selectedScale = selectedScaleState,
                    outputFormat = enginePrefs.outputFormat,
                    catalogModels = catalogModels,
                    engineState = engineState,
                    queue = queue,
                    completedCreation = completedCreationState,
                    savedConfirmationMessage = savedMessageState,
                    showNotificationRationale = showNotifRationaleState,
                    onSelectPreset = { preset ->
                        selectedPresetState = preset
                        if (selectedScaleState !in preset.supportedScales) {
                            selectedScaleState = preset.defaultScale
                        }
                    },
                    onSelectScale = { scale ->
                        selectedScaleState = scale
                    },
                    onDownloadModel = { modelId ->
                        lifecycleScope.launch {
                            container.modelRepository.startOrResumeDownload(modelId)
                        }
                    },
                    onEnhance = {
                        startSheetEnhance(catalogModels)
                    },
                    onRunInBackground = {
                        handleRunInBackgroundTap()
                    },
                    onConfirmNotificationPermissionAndBackground = {
                        showNotifRationaleState = false
                        uiPrefs.edit().putBoolean(KEY_BG_NOTIF_EXPLAINED, true).apply()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            proceedToBackgroundAndClose()
                        }
                    },
                    onSkipNotificationPermissionAndBackground = {
                        showNotifRationaleState = false
                        uiPrefs.edit().putBoolean(KEY_BG_NOTIF_EXPLAINED, true).apply()
                        proceedToBackgroundAndClose()
                    },
                    onCancelJob = {
                        container.engine.cancel()
                        startedFromThisSheet = false
                    },
                    onCancelQueuedItem = { itemId ->
                        container.engine.cancelQueuedItem(itemId)
                    },
                    onSaveCompleted = { creation ->
                        saveCreationExplicitly(creation)
                    },
                    onShareCompleted = { ctx, creation ->
                        val outFile = File(creation.outputPath)
                        if (outFile.isFile) {
                            MediaExportHelper.shareImage(ctx, outFile)
                        }
                    },
                    onOpenInFullApp = { creation ->
                        val uriToForward = stagedPhotoState?.stagedUri ?: incomingUri
                        forwardToFullApp(uriToForward, creation?.id)
                        finish()
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUri = extractIncomingUri(intent) ?: return
        completedCreationState = null
        savedMessageState = null
        intakeErrorState = null
        startedFromThisSheet = false
        stageIncomingImage(newUri)
    }

    private fun stageIncomingImage(uri: Uri) {
        lifecycleScope.launch {
            ImageIntakeManager.stageIncomingUri(applicationContext, uri).fold(
                onSuccess = { staged ->
                    stagedPhotoState = staged
                    intakeErrorState = null
                },
                onFailure = { err ->
                    intakeErrorState = err.message ?: "Unable to read the selected image."
                }
            )
        }
    }

    private fun startSheetEnhance(catalogModels: List<CatalogModelState>) {
        val staged = stagedPhotoState ?: return
        val preset = selectedPresetState
        val scale = selectedScaleState
        val spec = container.modelRepository.resolveExecutionSpec(
            preset = preset,
            scale = scale,
            catalogStates = catalogModels,
            overrideModelId = null
        )
        if (preset.requiresDownload && spec == null) return

        startedFromThisSheet = true
        completedCreationState = null
        savedMessageState = null
        container.engine.startUpscale(
            UpscaleRequest(
                inputUris = listOf(staged.stagedUri),
                preset = preset,
                scale = spec?.scale ?: scale,
                modelSpec = if (preset.id == PresetId.INSTANT) null else spec,
                autoSaveToGallery = true,
                notifyOnCompletion = false
            )
        )
    }

    private fun handleRunInBackgroundTap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            val alreadyExplained = uiPrefs.getBoolean(KEY_BG_NOTIF_EXPLAINED, false)
            if (!granted && !alreadyExplained) {
                showNotifRationaleState = true
                return
            }
        }
        proceedToBackgroundAndClose()
    }

    private fun proceedToBackgroundAndClose() {
        container.engine.requestCompletionNotificationForActiveSession()
        finish()
    }

    private fun saveCreationExplicitly(creation: RecentCreationEntity) {
        lifecycleScope.launch {
            val file = File(creation.outputPath)
            if (!file.isFile) {
                savedMessageState = "Output file is no longer available."
                return@launch
            }
            val desiredName = OutputNameFormatter.buildGalleryFileName(
                originalDisplayName = stagedPhotoState?.displayName,
                scale = creation.scale,
                outputExtension = file.extension
            )
            MediaExportHelper.saveToGalleryOrFolder(
                context = applicationContext,
                sourceFile = file,
                customTreeUri = container.engine.preferences.value.outputTreeUri,
                displayFileName = desiredName
            ).fold(
                onSuccess = { uri ->
                    container.database.recentCreationDao().updateSavedUri(creation.id, uri.toString())
                    savedMessageState = "Saved $desiredName to Pictures/RealScale-Pro"
                },
                onFailure = { err ->
                    savedMessageState = err.message ?: "Could not save photo."
                }
            )
        }
    }

    private fun forwardToFullApp(uri: Uri?, creationId: Long?) {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (creationId != null) {
                putExtra(ProcessingService.EXTRA_OPEN_CREATION_ID, creationId)
            } else if (uri != null) {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Selected photo", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(mainIntent)
    }

    private fun extractIncomingUri(incoming: Intent?): Uri? {
        if (incoming == null) return null
        incoming.data?.let { return it }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            incoming.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun loadThemeMode(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(uiPrefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)

    companion object {
        const val EXTRA_FORCE_QUICK_SHEET = "com.tumuyan.ncnn.realsr.EXTRA_FORCE_QUICK_SHEET"
        const val KEY_BG_NOTIF_EXPLAINED = "bg_notif_explained"
    }
}
