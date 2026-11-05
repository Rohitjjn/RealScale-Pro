import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/app_exception.dart';
import '../core/app_logger.dart';
import '../core/feature_gates.dart';
import '../engine/engine_types.dart';
import '../engine/image_bitmap.dart';
import '../services/device_profiler/device_profiler.dart';
import '../services/face_detection/face_detection.dart';
import '../services/feasibility/feasibility_checker.dart';
import '../services/image_service/image_service.dart';
import '../services/job_manager/job_manager.dart';
import '../services/model_catalog/model_catalog.dart';
import '../services/model_manager/model_manager.dart';
import '../services/permission_service/permission_service.dart';
import '../services/pipeline_planner/pipeline_planner.dart';
import '../services/settings_service/settings_service.dart';
import '../services/storage_service/storage_service.dart';

/// Composition root: every service, created once in main() and handed to the
/// widget tree via [servicesProvider].
class Services {
  Services({
    required this.settings,
    required this.permissions,
    required this.profiler,
    required this.image,
    required this.storage,
    required this.planner,
    required this.catalog,
    required this.modelManager,
    required this.jobManager,
    required this.engineRegistry,
    required this.modelDir,
    required this.tempDir,
  });

  final SettingsService settings;
  final PermissionService permissions;
  final DeviceProfiler profiler;
  final ImageService image;
  final StorageService storage;
  final PipelinePlanner planner;
  final ModelCatalog catalog;
  final ModelManager modelManager;
  final JobManager jobManager;
  final EngineRegistry engineRegistry;
  final String modelDir;
  final String tempDir;

  /// Picks the best engine for a model right now.
  UpscaleEngine? engineFor(ModelDef model, DeviceProfile device) =>
      engineRegistry.pick(model, device);
}

final servicesProvider = Provider<Services>(
  (ref) => throw UnimplementedError(
    'Services must be overridden in ProviderScope (main.dart)',
  ),
);

// --- Settings -----------------------------------------------------------------

final settingsProvider = Provider<SettingsService>(
  (ref) => ref.watch(servicesProvider).settings,
);

/// Revision counter bumped after every settings write (theme, language,
/// output format, quality …). Widgets that read [SettingsService] directly
/// watch this so they rebuild live instead of showing stale values.
final settingsRevisionProvider = NotifierProvider<SettingsRevisionNotifier, int>(
  SettingsRevisionNotifier.new,
);

class SettingsRevisionNotifier extends Notifier<int> {
  @override
  int build() => 0;

  /// Call after a setting has been persisted.
  void bump() => state = state + 1;
}

/// Convenience: bump the settings revision from anywhere.
void invalidateSettings(WidgetRef ref) =>
    ref.read(settingsRevisionProvider.notifier).bump();

/// Theme mode snapshot so MaterialApp rebuilds on change.
final themeModeNotifier = NotifierProvider<ThemeModeNotifier, ThemeMode>(
  ThemeModeNotifier.new,
);

class ThemeModeNotifier extends Notifier<ThemeMode> {
  @override
  ThemeMode build() {
    final s = ref.watch(settingsProvider);
    return _parse(s.themeMode);
  }

  ThemeMode _parse(String v) => switch (v) {
    'light' => ThemeMode.light,
    'dark' => ThemeMode.dark,
    _ => ThemeMode.system,
  };

  Future<void> set(ThemeMode mode) async {
    await ref.read(settingsProvider).setThemeMode(switch (mode) {
      ThemeMode.light => 'light',
      ThemeMode.dark => 'dark',
      ThemeMode.system => 'system',
    });
    state = mode;
  }
}

// --- Device & catalog ----------------------------------------------------------

final deviceProfileProvider = FutureProvider<DeviceProfile>((ref) async {
  final prefs = await SharedPreferences.getInstance();
  return DeviceProfiler(prefs: prefs).profile();
});

final catalogProvider = FutureProvider<ModelCatalog>((ref) async {
  final services = ref.watch(servicesProvider);
  return services.catalog;
});

final plannerProvider = FutureProvider<PipelinePlanner>(
  (ref) => ref.watch(servicesProvider).planner,
);

// --- Model install states --------------------------------------------------------

final installStatesProvider = StreamProvider<Map<String, ModelInstallInfo>>(
  (ref) => ref.watch(servicesProvider).modelManager.states,
);

final installStatesSnapshotProvider = Provider<Map<String, ModelInstallInfo>>(
  (ref) =>
      ref.watch(installStatesProvider).value ??
      ref.watch(servicesProvider).modelManager.currentState,
);

// --- Photo library (recents) ------------------------------------------------------

/// One recent-photo entry for the Home strip and gallery.
class PhotoEntry {
  PhotoEntry({required this.asset, this.bytes, this.width, this.height});

  final AssetEntity asset;
  Uint8List? bytes;
  int? width;
  int? height;
}

enum LibraryStatus { loading, ready, denied, empty }

final libraryProvider = NotifierProvider<LibraryNotifier, LibraryState>(
  LibraryNotifier.new,
);

class LibraryState {
  LibraryState({
    this.status = LibraryStatus.loading,
    this.photos = const [],
    this.selectedPhotosOnly = false,
  });
  final LibraryStatus status;
  final List<PhotoEntry> photos;
  final bool selectedPhotosOnly;
}

class LibraryNotifier extends Notifier<LibraryState> {
  @override
  LibraryState build() => LibraryState();

  Future<void> load({int limit = 30}) async {
    state = LibraryState(status: LibraryStatus.loading);
    final services = ref.read(servicesProvider);
    final outcome = await services.permissions.photosStatus();
    if (outcome == PermissionOutcome.permanentlyDenied ||
        outcome == PermissionOutcome.denied) {
      state = LibraryState(status: LibraryStatus.denied);
      return;
    }
    final hasPermission = await PhotoManager.requestPermissionExtend();
    if (!hasPermission.hasAccess) {
      state = LibraryState(
        status: LibraryStatus.denied,
        selectedPhotosOnly: hasPermission.isLimited,
      );
      return;
    }
    final assets = await PhotoManager.getAssetListRange(
      filterOption: FilterOptionGroup(
        orders: const [OrderOption(type: OrderOptionType.createDate)],
        createTimeCond: DateTimeCond(min: DateTime(2000), max: DateTime(2100)),
      ),
      start: 0,
      end: limit,
    );
    if (assets.isEmpty) {
      state = LibraryState(status: LibraryStatus.empty);
      return;
    }
    final entries = [
      for (final a in assets)
        PhotoEntry(asset: a, width: a.width, height: a.height),
    ];
    state = LibraryState(status: LibraryStatus.ready, photos: entries);
    // Lazy thumbnails: load small data progressively.
    for (final entry in entries) {
      final thumb = await entry.asset.thumbnailDataWithSize(
        const ThumbnailSize(360, 360),
      );
      entry.bytes = thumb?.buffer.asUint8List();
      if (!identical(state, state)) break;
      state = LibraryState(
        status: LibraryStatus.ready,
        photos: List.of(state.photos),
        selectedPhotosOnly: hasPermission.isLimited,
      );
    }
  }

  Future<PhotoEntry?> pickFromSystem() async {
    final assets = await PhotoManager.getAssetListRange(start: 0, end: 1);
    return assets.isEmpty
        ? null
        : PhotoEntry(
            asset: assets.first,
            width: assets.first.width,
            height: assets.first.height,
          );
  }
}

// --- Upscale configuration --------------------------------------------------------

/// Advanced level overrides (Phase 3). Every field is an "auto" default;
/// the planner keeps deciding when the user has not overridden.
class AdvancedOptions {
  const AdvancedOptions({
    this.tileSize = 0, // 0 = auto
    this.overlap = 'auto', // auto|none|small|medium|large
    this.threads = 0, // 0 = auto
    this.compute = 'auto', // auto|gpu|cpu
    this.format = 'sameAsSource', // sameAsSource|jpeg|png
    this.quality = 92,
    this.outputCapMp = 0, // 0 = tier default
  });

  final int tileSize;
  final String overlap;
  final int threads;
  final String compute;
  final String format;
  final int quality;
  final int outputCapMp;

  AdvancedOptions copyWith({
    int? tileSize,
    String? overlap,
    int? threads,
    String? compute,
    String? format,
    int? quality,
    int? outputCapMp,
  }) => AdvancedOptions(
    tileSize: tileSize ?? this.tileSize,
    overlap: overlap ?? this.overlap,
    threads: threads ?? this.threads,
    compute: compute ?? this.compute,
    format: format ?? this.format,
    quality: quality ?? this.quality,
    outputCapMp: outputCapMp ?? this.outputCapMp,
  );

  TileOverlap get overlapEnum => switch (overlap) {
    'none' => TileOverlap.none,
    'small' => TileOverlap.small,
    'large' => TileOverlap.large,
    _ => TileOverlap.medium,
  };

  bool get overlapIsAuto => overlap == 'auto';
}

/// Pixel-space preview crop rectangle (left, top, width, height).
typedef PixelRect = (int, int, int, int);

/// Everything the config screen edits before building a JobSpec.
class ConfigState {
  const ConfigState({
    this.path,
    this.originalBytes,
    this.imageType = ImageKind.photo,
    this.confidence = 0,
    this.scale = 2,
    this.level = SettingLevel.defaultLevel,
    this.selectedModelId,
    this.faceEnabled = false,
    this.tempInputUsed = false,
    this.effectiveBytes,
    this.faceHint,
    this.mode = UpscaleMode.upscale,
    this.advanced = const AdvancedOptions(),
    this.previewCrop,
  });

  final String? path;
  final Uint8List? originalBytes;
  final ImageKind imageType;
  final double confidence; // classifier confidence 0..1
  final int scale; // 2..5
  final SettingLevel level;
  final String? selectedModelId; // Manual level
  final bool faceEnabled;
  final bool tempInputUsed; // 2 MB rule applied
  final Uint8List? effectiveBytes; // after 2 MB rule

  /// Face-detection hint (task 2.7) — null until detection has run.
  final FaceDetectionResult? faceHint;

  /// Pipeline mode (Phase 3): Upscale default, Super Resolution Pro.
  final UpscaleMode mode;

  /// Advanced level overrides.
  final AdvancedOptions advanced;

  /// Super Resolution preview crop (pixel rect) — null = full image.
  final PixelRect? previewCrop;

  bool get isSrMode => mode == UpscaleMode.superResolution;

  ConfigState copyWith({
    String? path,
    Uint8List? originalBytes,
    ImageKind? imageType,
    double? confidence,
    int? scale,
    SettingLevel? level,
    String? selectedModelId,
    bool clearModel = false,
    bool? faceEnabled,
    bool? tempInputUsed,
    Uint8List? effectiveBytes,
    FaceDetectionResult? faceHint,
    bool clearFaceHint = false,
    UpscaleMode? mode,
    AdvancedOptions? advanced,
    PixelRect? previewCrop,
    bool clearPreviewCrop = false,
  }) => ConfigState(
    path: path ?? this.path,
    originalBytes: originalBytes ?? this.originalBytes,
    imageType: imageType ?? this.imageType,
    confidence: confidence ?? this.confidence,
    scale: scale ?? this.scale,
    level: level ?? this.level,
    selectedModelId: clearModel
        ? null
        : (selectedModelId ?? this.selectedModelId),
    faceEnabled: faceEnabled ?? this.faceEnabled,
    tempInputUsed: tempInputUsed ?? this.tempInputUsed,
    effectiveBytes: effectiveBytes ?? this.effectiveBytes,
    faceHint: clearFaceHint ? null : (faceHint ?? this.faceHint),
    mode: mode ?? this.mode,
    advanced: advanced ?? this.advanced,
    previewCrop: clearPreviewCrop ? null : (previewCrop ?? this.previewCrop),
  );

  String get typeName => imageType == ImageKind.anime ? 'anime' : 'photo';
}

/// Decoded dimensions per source path (populated by ConfigNotifier.loadFrom).
final dimsCache = <String?, (int, int)>{};

final configProvider = NotifierProvider<ConfigNotifier, ConfigState>(
  ConfigNotifier.new,
);

class ConfigNotifier extends Notifier<ConfigState> {
  @override
  ConfigState build() => const ConfigState();

  /// Loads the Advanced overrides from Settings once (Phase 3).
  void loadAdvancedFromSettings() {
    final s = ref.read(settingsProvider);
    state = state.copyWith(
      advanced: AdvancedOptions(
        tileSize: s.advancedTileSize,
        overlap: s.advancedOverlap,
        threads: s.advancedThreads,
        compute: s.advancedCompute,
        format: s.advancedFormat,
        quality: s.advancedQuality,
        outputCapMp: s.advancedOutputCap,
      ),
    );
  }

  Future<void> loadFrom({
    required String path,
    required Uint8List bytes,
    required SettingLevel level,
    UpscaleMode mode = UpscaleMode.upscale,
  }) async {
    final services = ref.read(servicesProvider);
    final decoded = await services.image.decodeForEngine(
      bytes,
      sourceName: path,
    );
    final classification = services.image.classify(decoded);
    // Dimension cache for output/ETA estimates on the config screen.
    dimsCache[path] = (decoded.width, decoded.height);
    state = ConfigState(
      path: path,
      originalBytes: bytes,
      imageType: classification.kind,
      confidence: classification.confidence,
      scale: services.settings.manualScale,
      level: level,
      mode: mode,
    );
    loadAdvancedFromSettings();
    // Face-detection hint off the UI thread (task 2.7). A short isolate
    // run — awaited so the state emits once, with the hint included.
    final hint = await _runFaceHint(decoded);
    if (state.path == path && hint != null) {
      state = state.copyWith(faceHint: hint);
    }
  }

  Future<FaceDetectionResult?> _runFaceHint(DecodedImage decoded) async {
    const helper = FaceDetectionHelper();
    return helper.detect(decoded.pixels, decoded.width, decoded.height);
  }

  void setScale(int scale) {
    state = state.copyWith(scale: scale);
    unawaited(ref.read(settingsProvider).setManualScale(scale));
  }

  void setLevel(SettingLevel level) => state = state.copyWith(level: level);

  void setImageType(ImageType type) {
    if (type == ImageType.auto) return;
    state = state.copyWith(
      imageType: type == ImageType.photo ? ImageKind.photo : ImageKind.anime,
      confidence: 0.95,
    );
  }

  void selectModel(String? modelId) {
    if (modelId == null) {
      state = state.copyWith(clearModel: true);
    } else {
      state = state.copyWith(selectedModelId: modelId);
      unawaited(
        ref.read(settingsProvider).setManualModel(state.typeName, modelId),
      );
    }
  }

  void setFaceEnabled(bool v) => state = state.copyWith(faceEnabled: v);

  void setPreviewCrop(PixelRect? rect) =>
      state = state.copyWith(previewCrop: rect, clearPreviewCrop: rect == null);

  void setAdvanced(AdvancedOptions options) {
    state = state.copyWith(advanced: options);
    final s = ref.read(settingsProvider);
    unawaited(s.setAdvancedTileSize(options.tileSize));
    unawaited(s.setAdvancedOverlap(options.overlap));
    unawaited(s.setAdvancedThreads(options.threads));
    unawaited(s.setAdvancedCompute(options.compute));
    unawaited(s.setAdvancedFormat(options.format));
    unawaited(s.setAdvancedQuality(options.quality));
    unawaited(s.setAdvancedOutputCap(options.outputCapMp));
  }

  /// Applies the 2 MB rule (prd §5.2) exactly once per photo. Super
  /// Resolution Mode (Phase 3) runs compression-OFF: the original file's
  /// pixels are used as-is, no quality-90 temp copy is ever made.
  Future<void> ensureInputRule() async {
    if (state.originalBytes == null || state.effectiveBytes != null) return;
    if (state.isSrMode) {
      state = state.copyWith(
        effectiveBytes: state.originalBytes,
        tempInputUsed: false,
      );
      return;
    }
    final services = ref.read(servicesProvider);
    final rule = await services.image.applyInputRule(state.originalBytes!);
    state = state.copyWith(
      effectiveBytes: rule.bytes,
      tempInputUsed: rule.usedTemp,
    );
  }

  /// Clears the configuration and deletes any temp input the 2 MB rule
  /// created (rules.md golden rule 8: no temp litter).
  Future<void> reset() async {
    final previous = state;
    state = const ConfigState();
    await _cleanupTempInput(previous);
  }

  Future<void> _cleanupTempInput(ConfigState previous) async {
    final path = previous.tempInputUsed ? previous.path : null;
    if (path == null) return;
    try {
      final f = File(path);
      if (await f.exists()) await f.delete();
    } catch (e) {
      AppLogger.w('ConfigNotifier', 'temp input cleanup failed: $e');
    }
  }
}

// --- Job runner ---------------------------------------------------------------------

/// Progress view for the processing screen.
class JobViewState {
  const JobViewState({
    this.stage = JobStage.preparing,
    this.percent = 0,
    this.tileIndex = 0,
    this.tileTotal = 0,
    this.etaSeconds,
    this.message,
    this.result,
  });
  final JobStage stage;
  final double percent;
  final int tileIndex;
  final int tileTotal;
  final int? etaSeconds;
  final String? message;
  final EngineResult? result;
}

final jobProvider = NotifierProvider<JobNotifier, JobViewState>(
  JobNotifier.new,
);

class JobNotifier extends Notifier<JobViewState> {
  StreamSubscription<ProgressEvent>? _sub;
  // The engine tracks jobs by spec.jobId, not by photo path — keep it here
  // so cancel() targets the running job (qa: cancel ≤ 2 s).
  String? _activeJobId;

  @override
  JobViewState build() => const JobViewState();

  /// Runs the full pipeline for the current config. Returns the saved path.
  Future<String?> run() async {
    final services = ref.read(servicesProvider);
    await ref.read(configProvider.notifier).ensureInputRule();
    final cfg = ref.read(configProvider);
    final device = await ref.read(deviceProfileProvider.future);
    if (cfg.path == null || cfg.originalBytes == null) return null;

    // Offline first: the four Starter-pack models ship inside the APK, so
    // make sure they are materialized before any model is resolved.
    await services.modelManager.ensureBundledFiles();

    // Resolve model (mode-aware: Super Resolution picks from the SR table).
    final effectiveType = cfg.typeName;
    final explicitModel =
        cfg.level != SettingLevel.defaultLevel && cfg.selectedModelId != null;
    final resolved = explicitModel
        ? cfg.selectedModelId
        : services.planner.autoModelId(
                mode: cfg.mode,
                imageType: effectiveType,
                tier: device.tier,
                catalog: services.catalog,
              ) ??
              services.modelManager.fallbackModel(effectiveType);
    final modelId = resolved ?? '';
    var model = services.catalog.byId(modelId);
    if (model == null || !model.available) {
      throw ModelMissingException(modelId);
    }
    if (cfg.isSrMode && !model.supportsMode('superResolution')) {
      throw ModelMissingException(modelId);
    }

    // A hand-picked (Manual/Advanced) or Super Resolution model must really
    // be installed — never silently swap the user's choice for another one.
    if (explicitModel || cfg.isSrMode) {
      if (!services.modelManager.isInstalled(model.id)) {
        throw ModelMissingException(model.id);
      }
    } else {
      // Auto: if the picked model can't run natively at this scale (only the
      // x4 weights ship offline for realesr-animevideov3), fall back to a
      // starter model whose files are actually on disk.
      model = _offlineReadyModel(services, model, cfg) ?? model;
    }

    final decoded = await services.image.decodeForEngine(
      cfg.effectiveBytes ?? cfg.originalBytes!,
      sourceName: cfg.path!,
    );
    final spec = _specFor(cfg, model.id, decoded);
    final plan = services.planner.plan(
      spec: spec,
      device: device,
      catalog: services.catalog,
      inputWidth: decoded.width,
      inputHeight: decoded.height,
    );
    final eta = services.planner.estimateSeconds(
      plan,
      decoded.width,
      decoded.height,
    );

    // Feasibility gate (Phase 3): a red Super Resolution run is blocked
    // here as a last line of defense — the config screen already showed
    // the check result.
    if (cfg.isSrMode) {
      final check = const FeasibilityChecker().check(
        mode: cfg.mode,
        inputWidth: decoded.width,
        inputHeight: decoded.height,
        targetScale: cfg.scale,
        outputCapMegapixels: plan.outputCapMegapixels,
        etaSeconds: eta,
        device: device,
      );
      if (check.blocked) {
        throw FeasibilityException(check);
      }
    }

    state = JobViewState(stage: JobStage.preparing);
    _activeJobId = spec.jobId;

    final ctx = PipelineContext(
      catalog: services.catalog,
      device: device,
      modelDir: services.modelDir,
      tempDir: services.tempDir,
      plan: plan,
      outputWriter: _outputWriter(services),
    );

    // Live progress (subscribe without awaiting — broadcast stream). The
    // previous subscription is always closed first so a second run never
    // double-emits into the same view.
    await _sub?.cancel();
    _sub = services.jobManager.progress.listen((event) {
      state = JobViewState(
        stage: event.stage,
        percent: event.percent,
        tileIndex: event.tileIndex,
        tileTotal: event.tileTotal,
        etaSeconds: event.stage == JobStage.upscaling && event.tileIndex <= 1
            ? eta
            : event.etaSeconds,
        message: event.message,
        result: event.result,
      );
    });

    // `late` (not a plain declaration): the finally block runs even when the
    // job throws, so definite-assignment analysis can't prove the variable
    // is set — and the throw propagates before it is ever read.
    late ProgressEvent result;
    try {
      result = await services.jobManager.runJob(
        spec: spec.copyWith(tileSize: plan.tileSize),
        ctx: ctx,
        originalName: cfg.path!
            .split(Platform.pathSeparator)
            .last
            .split('/')
            .last,
        originalJpegBytes: cfg.originalBytes!,
        keepMetadata: services.settings.keepMetadata,
        outputKind: _outputKind(services, cfg),
        outputQuality: _outputQuality(services, cfg),
        hasAlpha: decoded.hasAlpha,
      );
    } finally {
      await _sub?.cancel();
      _sub = null;
      _activeJobId = null;
    }

    if (result.stage == JobStage.done) {
      state = JobViewState(
        stage: JobStage.done,
        percent: 1,
        message: result.message,
        result: result.result,
      );
      return result.message;
    }
    state = JobViewState(
      stage: result.stage,
      percent: 0,
      message: result.message,
    );
    return null;
  }

  /// JPEG quality actually used for the save: the Advanced level exposes its
  /// own slider, every other level uses the Settings value.
  int _outputQuality(Services services, ConfigState cfg) =>
      cfg.level == SettingLevel.advanced
      ? cfg.advanced.quality
      : services.settings.outputQuality;

  /// Offline starter fallback (Auto level only): returns a bundled starter
  /// model whose native files are really on disk when the Auto pick can't run
  /// at the requested scale offline — e.g. `realesr-animevideov3` ships only
  /// its x4 graph inside the APK, so an anime 2x/3x Auto run moves to
  /// `realcugan-se-2x-denoise1x` instead of failing.
  ModelDef? _offlineReadyModel(
    Services services,
    ModelDef model,
    ConfigState cfg,
  ) {
    final imageType = model.bestFor.contains('anime') ? 'anime' : cfg.typeName;
    final chain = services.planner.resolveScaleChain(
      model: model,
      targetScale: cfg.scale,
      imageType: imageType,
    );
    if (services.modelManager.hasUsableNativeFiles(model, chain.nativeScale)) {
      return null;
    }
    final preferred = <String>[
      if (imageType == 'anime') 'realcugan-se-2x-denoise1x',
      if (imageType == 'anime') 'waifu2x-upconv-anime-2x',
      if (imageType != 'anime') 'waifu2x-upconv-photo-2x',
      if (imageType != 'anime') 'realcugan-se-2x-denoise1x',
      'realesr-animevideov3',
    ];
    for (final id in preferred) {
      final candidate = services.catalog.byId(id);
      if (candidate == null || !candidate.available) continue;
      if (candidate.engine != 'ncnn') continue;
      if (!services.modelManager.isInstalled(candidate.id)) continue;
      final cChain = services.planner.resolveScaleChain(
        model: candidate,
        targetScale: cfg.scale,
        imageType: candidate.bestFor.contains('anime') ? 'anime' : 'photo',
      );
      if (services.modelManager.hasUsableNativeFiles(
        candidate,
        cChain.nativeScale,
      )) {
        return candidate;
      }
    }
    return null;
  }

  OutputKind _outputKind(Services services, ConfigState cfg) {
    // Super Resolution Mode promise: no recompression — always lossless
    // PNG out, metadata preserved (phases.md Phase 3).
    if (cfg.isSrMode) return OutputKind.png;
    final format = cfg.level == SettingLevel.advanced
        ? cfg.advanced.format
        : services.settings.outputFormat;
    return switch (format) {
      'jpeg' => OutputKind.jpeg,
      'webp' => OutputKind.webp,
      'png' => OutputKind.png,
      // 'sameAsSource' keeps the source format: a JPEG stays a JPEG
      // (re-encoded once at the chosen quality), everything else stays
      // lossless PNG (prd §7.3).
      _ => _sourceKind(cfg.path),
    };
  }

  OutputKind _sourceKind(String? path) {
    if (path == null) return OutputKind.png;
    return switch (path.toLowerCase().split('.').last) {
      'jpg' || 'jpeg' => OutputKind.jpeg,
      'webp' => OutputKind.webp,
      _ => OutputKind.png,
    };
  }

  JobSpec _specFor(ConfigState cfg, String modelId, DecodedImage decoded) {
    final services = ref.read(servicesProvider);
    final jobId = DateTime.now().microsecondsSinceEpoch.toString();
    final adv = cfg.advanced;
    return JobSpec(
      jobId: jobId,
      inputPath: cfg.path!,
      inputBytes: cfg.effectiveBytes ?? cfg.originalBytes!,
      mode: cfg.mode,
      settingLevel: cfg.level,
      imageType: cfg.imageType == ImageKind.anime
          ? ImageType.anime
          : ImageType.photo,
      scale: cfg.scale,
      modelId: modelId,
      faceEnhancement: FeatureGates.faceEnhancement && cfg.faceEnabled,
      faceModelId: 'gfpgan-v1.4',
      tileSize: cfg.level == SettingLevel.advanced ? adv.tileSize : 0,
      overlap: adv.overlapEnum,
      overlapExplicit: cfg.level == SettingLevel.advanced && !adv.overlapIsAuto,
      computeDevice: switch ((cfg.level == SettingLevel.advanced)
          ? adv.compute
          : services.settings.computePreference) {
        'gpu' => ComputeDevice.gpu,
        'cpu' => ComputeDevice.cpu,
        _ => ComputeDevice.auto,
      },
      threadCount: cfg.level == SettingLevel.advanced ? adv.threads : 0,
      outputCapMegapixels: cfg.level == SettingLevel.advanced
          ? adv.outputCapMp
          : 0,
      options: {
        'decodedPixels': decoded.pixels,
        'decodedWidth': decoded.width,
        'decodedHeight': decoded.height,
      },
    );
  }

  Future<void> Function(String path, Bitmap bmp) _outputWriter(
    Services services,
  ) {
    return (path, bmp) async {
      final bytes = await services.image.encodeOutput(
        rgba: bmp.pixels,
        width: bmp.width,
        height: bmp.height,
        hasAlpha: true,
        kind: OutputKind.png,
        quality: 100,
      );
      await File(path).writeAsBytes(bytes, flush: true);
    };
  }

  /// Clears the job view (called when leaving the result screen).
  void resetView() => state = const JobViewState();

  Future<void> cancel() async {
    final services = ref.read(servicesProvider);
    await services.jobManager.cancel(_activeJobId ?? '');
    await _sub?.cancel();
    state = const JobViewState(stage: JobStage.cancelled, message: '');
  }
}

// --- Super Resolution preview (Phase 3) --------------------------------------------

/// Result of a Super Resolution preview-crop run.
class PreviewState {
  const PreviewState({
    this.running = false,
    this.percent = 0,
    this.resultPath,
    this.resultWidth,
    this.resultHeight,
    this.cropBytes,
    this.elapsedMs,
    this.error,
  });

  final bool running;
  final double percent;

  /// Temp PNG of the upscaled crop (deleted when the sheet closes).
  final String? resultPath;
  final int? resultWidth;
  final int? resultHeight;

  /// PNG bytes of the original crop region (for before/after compare).
  final Uint8List? cropBytes;
  final int? elapsedMs;
  final String? error;

  bool get done => resultPath != null;
}

final previewProvider = NotifierProvider<PreviewNotifier, PreviewState>(
  PreviewNotifier.new,
);

/// Runs the SR pipeline on ONLY the preview crop — proves quality on the
/// user's own pixels before they commit to the long full run. Runs the
/// engine directly (not through JobManager): nothing is saved to the
/// gallery, the main job path stays untouched.
class PreviewNotifier extends Notifier<PreviewState> {
  var _cancelled = false;

  @override
  PreviewState build() => const PreviewState();

  Future<void> run() async {
    final services = ref.read(servicesProvider);
    final cfg = ref.read(configProvider);
    final device = await ref.read(deviceProfileProvider.future);
    final rect = cfg.previewCrop;
    if (cfg.path == null || cfg.originalBytes == null || rect == null) {
      return;
    }

    _cancelled = false;
    state = const PreviewState(running: true);
    try {
      // 1) Decode once and crop the region.
      final decoded = await services.image.decodeForEngine(
        cfg.originalBytes!,
        sourceName: cfg.path!,
      );
      final (cl, ct, cw, ch) = rect;
      final crop = Bitmap(decoded.width, decoded.height, decoded.pixels).crop(
        cl.clamp(0, decoded.width - 1),
        ct.clamp(0, decoded.height - 1),
        cw.clamp(8, decoded.width - cl),
        ch.clamp(8, decoded.height - ct),
      );
      if (_cancelled) return;

      // 2) Model + plan for the CROP dimensions (tiny → feasibility green).
      final modelId =
          services.planner.autoModelId(
            mode: UpscaleMode.superResolution,
            imageType: cfg.typeName,
            tier: device.tier,
            catalog: services.catalog,
          ) ??
          cfg.selectedModelId ??
          services.modelManager.fallbackModel(cfg.typeName);
      final model = services.catalog.byId(modelId ?? '');
      if (model == null || !model.available) {
        throw ModelMissingException(modelId ?? '');
      }
      final jobId = 'preview-$jobIdCounter';
      jobIdCounter++;
      final spec = JobSpec(
        jobId: jobId,
        inputPath: cfg.path!,
        inputBytes: cfg.originalBytes!,
        mode: UpscaleMode.superResolution,
        settingLevel: cfg.level,
        imageType: cfg.imageType == ImageKind.anime
            ? ImageType.anime
            : ImageType.photo,
        scale: cfg.scale,
        modelId: model.id,
        computeDevice: ComputeDevice.auto,
        options: {
          'decodedPixels': crop.pixels,
          'decodedWidth': crop.width,
          'decodedHeight': crop.height,
        },
      );
      final plan = services.planner.plan(
        spec: spec,
        device: device,
        catalog: services.catalog,
        inputWidth: crop.width,
        inputHeight: crop.height,
      );

      // 3) Run the engine directly (no gallery save for previews).
      final engine = services.engineFor(model, device);
      if (engine == null) {
        throw const EngineException('No engine available for this model');
      }
      final stopwatch = Stopwatch()..start();
      EngineResult? result;
      await for (final event in engine.run(
        spec,
        _previewCtx(services, plan, device),
      )) {
        if (_cancelled) {
          unawaited(engine.cancel(jobId));
          state = const PreviewState();
          return;
        }
        state = PreviewState(
          running: true,
          percent: event.percent,
          cropBytes: state.cropBytes,
        );
        if (event.stage == JobStage.done) result = event.result;
        if (event.stage == JobStage.failed) {
          throw EngineException(event.message ?? 'Preview failed');
        }
      }
      stopwatch.stop();
      if (result == null) {
        throw const EngineException('Preview did not finish');
      }

      // 4) Before/after bytes: encode the original crop for the compare.
      final cropPng = await services.image.encodeOutput(
        rgba: crop.pixels,
        width: crop.width,
        height: crop.height,
        hasAlpha: decoded.hasAlpha,
        kind: OutputKind.png,
        quality: 100,
      );
      state = PreviewState(
        running: false,
        percent: 1,
        resultPath: result.outputPath,
        resultWidth: result.outputWidth,
        resultHeight: result.outputHeight,
        cropBytes: cropPng,
        elapsedMs: stopwatch.elapsedMilliseconds,
      );
    } on AppException catch (e) {
      state = PreviewState(error: e.message);
    } catch (e) {
      AppLogger.e('Preview', 'preview run failed', e);
      state = const PreviewState(
        error: 'The preview could not finish. Try a smaller area.',
      );
    }
  }

  PipelineContext _previewCtx(
    Services services,
    PipelinePlan plan,
    DeviceProfile device,
  ) {
    return PipelineContext(
      catalog: services.catalog,
      device: device,
      modelDir: services.modelDir,
      tempDir: services.tempDir,
      plan: plan,
      outputWriter: (path, bmp) async {
        final bytes = await services.image.encodeOutput(
          rgba: bmp.pixels,
          width: bmp.width,
          height: bmp.height,
          hasAlpha: true,
          kind: OutputKind.png,
          quality: 100,
        );
        await File(path).writeAsBytes(bytes, flush: true);
      },
    );
  }

  /// Closes the preview sheet: deletes the temp result file.
  Future<void> close() async {
    _cancelled = true;
    final path = state.resultPath;
    state = const PreviewState();
    if (path != null) {
      try {
        final f = File(path);
        if (await f.exists()) await f.delete();
      } catch (_) {}
    }
  }

  void cancel() => _cancelled = true;
}

var jobIdCounter = 0;

// ---- static wiring done in main() ----
