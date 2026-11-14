package com.tumuyan.ncnn.realsr.ui.state

import androidx.compose.runtime.Immutable
import com.tumuyan.ncnn.realsr.core.plan.ImageType
import com.tumuyan.ncnn.realsr.core.plan.JobPrefs
import com.tumuyan.ncnn.realsr.core.plan.ModeId
import com.tumuyan.ncnn.realsr.core.plan.Plan
import com.tumuyan.ncnn.realsr.core.plan.PlanLevel
import com.tumuyan.ncnn.realsr.core.plan.OutputFormatPref
import com.tumuyan.ncnn.realsr.core.plan.TilePreset
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

    @Serializable
    data object Upscales : Route

    @Serializable
    data object Gallery : Route

    @Serializable
    data object Models : Route

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
    val dynamicColor: Boolean = false,
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

@Immutable
data class ToastMessage(val id: Long, val text: String)

@Immutable
data class ModelDownloadUi(
    val modelId: String,
    val progress: Float = 0f,
    val state: String = "idle",
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
    val photosLoading: Boolean = false,
    val job: JobUi? = null,
    val toast: ToastMessage? = null,
    val downloads: Map<String, ModelDownloadUi> = emptyMap(),
    val selectedUpscales: Set<String> = emptySet(),
) {
    val route: Route get() = routes.last()
    val canGoBack: Boolean get() = routes.size > 1

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
