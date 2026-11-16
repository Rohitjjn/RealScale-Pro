package com.tumuyan.ncnn.realsr.ui.state

import androidx.compose.runtime.Immutable
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.core.plan.JobPrefs
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.Plan
import com.tumuyan.ncnn.realsr.core.plan.PlanLevel
import com.tumuyan.ncnn.realsr.core.plan.OutputFormatPref
import com.tumuyan.ncnn.realsr.core.plan.TilePreset
import com.tumuyan.ncnn.realsr.core.ai.AiKeyRecord
import com.tumuyan.ncnn.realsr.core.ai.AiPrefs
import kotlinx.serialization.Serializable

/** Navigation targets, one to one with the prototype's `Route` union. */
@Serializable
sealed interface Route {
    @Serializable
    data object Home : Route

    @Serializable
    data class Viewer(val photoId: String, val single: Boolean = false) : Route

    @Serializable
    data class Config(val photoId: String) : Route

    @Serializable
    data object Processing : Route

    @Serializable
    data class Result(val resultId: String) : Route

    /** The finished upscale, full screen and zoomable. */
    @Serializable
    data class ResultFull(val resultId: String) : Route

    @Serializable
    data object Upscales : Route

    @Serializable
    data object Gallery : Route

    @Serializable
    data object Models : Route

    /** API keys and usage for AI Upscale Mode (Round 5). */
    @Serializable
    data object AiKeys : Route

    /** The Default / Manual / Advanced screen for one photo, with AI models instead of tiles. */
    @Serializable
    data class AiConfig(val photoId: String) : Route

    /** Upload → reconstruct → download, with its own animation. */
    @Serializable
    data object AiProcessing : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Help : Route

    @Serializable
    data object About : Route
}

/** Onboarding phases, same order and names as the prototype. */
@Serializable
enum class Phase { Splash, Guide, PermPhotos, PermNotif, Device, Setup, App }

@Serializable
enum class ThemePref { System, Light, Dark }

@Serializable
enum class LaunchMode { Last, Upscale, Super }

@Serializable
enum class ComputePref { Auto, Gpu, Cpu }

@Serializable
enum class FormatSetting { Auto, Jpeg, Png, Webp }

/** Settings screen state; mirrors the prototype's `Settings` object. */
@Immutable
@Serializable
data class AppSettings(
    val theme: ThemePref = ThemePref.System,
    val haptics: Boolean = true,
    val launchMode: LaunchMode = LaunchMode.Last,
    val compute: ComputePref = ComputePref.Auto,
    val keepAwake: Boolean = true,
    val throttle: Boolean = true,
    val format: FormatSetting = FormatSetting.Auto,
    val keepMeta: Boolean = true,
    val wifiOnly: Boolean = false,
    val analytics: Boolean = false,
    val crashReports: Boolean = false,
    /** SAF tree the user picked in Settings → Output → Save folder. Null = Pictures/RealScale-Pro. */
    val saveFolderUri: String? = null,
    /** Human label of [saveFolderUri] (the folder's display name). */
    val saveFolderLabel: String? = null,
)

/** A photo as the UI needs it: MediaStore row, or a file the user opened with the app. */
@Immutable
data class PhotoItem(
    val id: String,
    val uri: String,
    val name: String,
    val width: Int,
    val height: Int,
    val sizeMB: Double,
    val dateLabel: String,
    val type: ImageType,
    val lowConfidence: Boolean = false,
    val album: String = "",
    val ext: String = "jpg",
    val imported: Boolean = false,
    /**
     * Capture time in epoch millis. MediaStore's `DATE_ADDED` for library rows, import time for
     * files opened with the app. The Home strip sorts on this, never on the string id: "media:100"
     * sorts after "media:99" as text, which is why the row used to look shuffled.
     */
    val dateTaken: Long = 0L,
) {
    val mp: Double get() = width.toDouble() * height / 1_000_000
}

/** A finished upscale, backed by a `recent_creations` row. */
@Immutable
data class ResultItem(
    val id: String,
    val photoId: String,
    val sourceUri: String,
    val outputPath: String,
    val savedUri: String?,
    val mode: ModeId,
    val scale: Int,
    val modelId: String,
    val modelName: String,
    val outW: Int,
    val outH: Int,
    val sizeMB: Double,
    val timeSec: Int,
    val format: String,
    val fileName: String,
    val createdAt: Long,
)

/** The live job, while one is running or just finished in the background. */
@Immutable
data class JobUi(
    val photoId: String,
    val mode: ModeId,
    val plan: Plan,
    val progress: Float,
    val status: JobStatus,
    val background: Boolean = false,
    val resultId: String? = null,
    val elapsedSec: Int = 0,
    val remainingSec: Int = 0,
    val stageLabel: String = "",
    val tilesDone: Int = 0,
    val tilesTotal: Int = 0,
)

enum class JobStatus { Running, Done, Failed, Cancelled }

/**
 * The AI job as the UI needs it: which model and key are being used, how long it has been running
 * and what the provider is doing right now. Deliberately separate from [JobUi] — the local engine
 * has plans, tiles and per-tile progress; a cloud model has one request and one answer.
 */
@Immutable
data class AiJobUi(
    val photoId: String,
    val modelId: String,
    val modelLabel: String,
    val providerName: String,
    val keyLabel: String,
    val scale: Int,
    val stageLabel: String = "Preparing image",
    /** 0..1, driven by the stage, not by fake stitching. */
    val progress: Float = 0.04f,
    val status: JobStatus = JobStatus.Running,
    val elapsedSec: Int = 0,
    val etaSec: Int = 0,
    val error: String? = null,
    val resultId: String? = null,
)

/** The key + model + scale a job will actually use, resolved by `AppStore.aiTarget()`. */
@Immutable
data class AiTarget(
    val key: AiKeyRecord,
    val model: com.tumuyan.ncnn.realsr.core.ai.AiModel,
    val provider: com.tumuyan.ncnn.realsr.core.ai.AiProvider,
    val scale: Int,
)

@Immutable
data class ToastMessage(val id: Long, val text: String)

@Immutable
data class ModelDownloadUi(
    val modelId: String,
    val progress: Float = 0f,
    val state: String = "idle",
    val speedBytesPerSec: Long = 0L,
    /** Repository note such as "Waiting for Wi-Fi…" or a failure reason. */
    val message: String? = null,
)

/** Everything one screen can read. */
@Immutable
data class AppUiState(
    val phase: Phase = Phase.Splash,
    val routes: List<Route> = listOf(Route.Home),
    val mode: ModeId = ModeId.UPSCALE,
    val superIntroSeen: Boolean = false,
    val onboarded: Boolean = false,
    val photoAccess: Boolean = false,
    val limitedAccess: Boolean = false,
    val notificationsAsked: Boolean = false,
    val cameraAsked: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val prefs: Map<ModeId, JobPrefs> = emptyMap(),
    val installed: List<String> = emptyList(),
    val photos: List<PhotoItem> = emptyList(),
    val results: List<ResultItem> = emptyList(),
    /**
     * Id of the creation the last finished job produced. The Room flow behind [results] re-emits one
     * frame later than the engine's Completed, so the Result screen uses this to tell "still loading"
     * apart from "deleted" and never bounces the user Home (Round-4, issue 1).
     */
    val lastResultId: String? = null,
    val photosLoading: Boolean = false,
    val job: JobUi? = null,
    /* ----------------------------- Round 5: AI Upscale Mode ----------------------------- */
    /** Home's third mode card. When on, picking an image goes straight to [Route.AiConfig]. */
    val aiMode: Boolean = false,
    val aiKeys: List<AiKeyRecord> = emptyList(),
    val aiPrefs: AiPrefs = AiPrefs(),
    val aiJob: AiJobUi? = null,
    val toast: ToastMessage? = null,
    val downloads: Map<String, ModelDownloadUi> = emptyMap(),
    val selectedUpscales: Set<String> = emptySet(),
    /** Set while the Starter Pack queue is paused from the setup screen. */
    val starterPackPaused: Boolean = false,
    /**
     * Bumped by `AppStore.refreshDevice()`. `device` is a plain field on the store, so this counter is
     * what tells the screens that the profile (tier, free space) was re-read.
     */
    val deviceRevision: Int = 0,
) {
    val route: Route get() = routes.last()
    val canGoBack: Boolean get() = routes.size > 1

    /** Keys that may actually be used right now: enabled, with quota left. */
    val aiActiveKeys: List<AiKeyRecord>
        get() = aiKeys.filter { it.enabled && !it.quotaReached }

    val aiReady: Boolean get() = aiActiveKeys.isNotEmpty()

    fun prefsFor(modeId: ModeId): JobPrefs = prefs[modeId] ?: defaultPrefsFor(modeId)

    companion object {
        /** The prototype's `defaultPrefs()`: Default level, 2x, Fast tiles, 30 % denoise. */
        fun defaultPrefsFor(mode: ModeId): JobPrefs = JobPrefs(
            level = PlanLevel.DEFAULT,
            scale = if (mode == ModeId.SUPER) 4 else 2,
        )
    }
}

/** A short, human toast, same phrasing as the prototype. */
fun toastForPrefsChange(old: JobPrefs, new: JobPrefs): String? = when {
    old.level != new.level && new.level == PlanLevel.DEFAULT -> "Back to recommended settings"
    old.modelId != new.modelId && new.modelId == "auto" -> "Automatic model selection"
    old.scale != new.scale -> "Scale ${new.scale}×"
    old.format != new.format -> when (new.format) {
        OutputFormatPref.DEFAULT -> "Format: automatic"
        else -> "Format: ${new.format.id.uppercase()}"
    }
    old.tile != new.tile -> when (new.tile) {
        TilePreset.SAFE -> "Tiles: safe, uses less memory"
        TilePreset.BALANCED -> "Tiles: balanced"
        TilePreset.FAST -> "Tiles: fast"
        TilePreset.MAX -> "Tiles: maximum, no tiling"
        TilePreset.CUSTOM -> "Tiles: custom"
    }
    else -> null
}
