/// RealScale-Pro — processing job lifecycle.
///
/// State machine: queued → inspecting → planning → acquiringModel →
/// decoding → inferring → encoding → saving → done | failed | cancelled.
///
/// * Every progress event originates from a real processing step
///   (native tile callbacks, byte counters, phase transitions).
/// * ETA is shown only once per-tile timings make it meaningful
///   (>= 2 completed tiles) and is recomputed from measured data.
/// * Cancellation aborts between native tiles via the shared control
///   block; the original file is never modified at any point.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../domain/models.dart';
import '../engine/native/realscale_native.dart';
import '../services/image_service.dart';
import '../services/model_manager.dart';
import '../services/storage_service.dart';
import 'pipeline_planner.dart';

class JobManagerException implements Exception {
  JobManagerException(this.message, {this.phase});
  final String message;
  final JobPhase? phase;
  @override
  String toString() => 'JobManagerException: $message';
}

/// One in-flight job's observable state.
class JobState {
  const JobState({
    required this.phase,
    this.progress = const JobProgress(phase: JobPhase.queued, fraction: 0),
    this.plan,
    this.message,
    this.eta,
    this.result,
    this.error,
    this.startedAt,
    this.completedAt,
  });

  final JobPhase phase;
  final JobProgress progress;
  final PipelinePlan? plan;
  final String? message;
  final Duration? eta;
  final JobResult? result;
  final String? error;
  final DateTime? startedAt;
  final DateTime? completedAt;

  bool get isTerminal =>
      phase == JobPhase.done || phase == JobPhase.failed ||
      phase == JobPhase.cancelled;

  JobState copyWith({
    JobPhase? phase,
    JobProgress? progress,
    PipelinePlan? plan,
    String? message,
    Duration? eta,
    JobResult? result,
    String? error,
    DateTime? startedAt,
    DateTime? completedAt,
  }) =>
      JobState(
        phase: phase ?? this.phase,
        progress: progress ?? this.progress,
        plan: plan ?? this.plan,
        message: message,
        eta: eta,
        result: result ?? this.result,
        error: error,
        startedAt: startedAt ?? this.startedAt,
        completedAt: completedAt ?? this.completedAt,
      );
}

class JobManager {
  JobManager({
    required this.imageService,
    required this.storage,
    required this.modelManager,
    required this.engineFactory,
    this.maxImageEdge = 12000,
  });

  final ImageService imageService;
  final StorageService storage;
  final ModelManager modelManager;
  final EngineRunner Function() engineFactory;
  final int maxImageEdge;

  final _stateController = ValueNotifier<JobState>(
      const JobState(phase: JobPhase.queued));
  ValueListenable<JobState> get state => _stateController;

  ControlBlock? _activeControlBlock;

  void _set(JobState s) => _stateController.value = s;

  /// Runs the full pipeline. Returns the job result.
  ///
  /// [preferGpu] opts into the experimental Vulkan backend (settings);
  /// the engine falls back to CPU automatically when unavailable.
  Future<JobResult> run({
    required File input,
    required ProcessingMode mode,
    required ModelInfo model,
    required DeviceProfile device,
    int jpegQuality = 92,
    bool preferGpu = false,
    RgbaImage? preDownsampled,
    void Function(JobState)? onState,
  }) async {
    final startedAt = DateTime.now();
    var state = const JobState(phase: JobPhase.queued);
    void update(JobState s) {
      state = s;
      _set(s);
      onState?.call(s);
    }

    update(JobState(
      phase: JobPhase.inspecting,
      startedAt: startedAt,
      message: 'Reading image…',
    ));

    final metadata = await imageService.inspect(input);
    if (metadata.width > maxImageEdge || metadata.height > maxImageEdge) {
      update(JobState(
        phase: JobPhase.failed,
        error: 'Image is ${metadata.width}×${metadata.height}; the safety '
            'limit is ${maxImageEdge}px per edge.',
        startedAt: startedAt,
        completedAt: DateTime.now(),
      ));
      throw JobManagerException(state.error!, phase: JobPhase.inspecting);
    }

    update(state.copyWith(
        phase: JobPhase.planning, message: 'Planning the pipeline…'));
    final planner = const PipelinePlanner();
    final plan = planner.plan(
      image: metadata,
      device: device,
      mode: mode,
      model: model,
      jpegQuality: jpegQuality,
    );

    // Acquire the model (download when the user consented earlier).
    if (!modelManager.isInstalled(model)) {
      update(state.copyWith(
          phase: JobPhase.acquiringModel,
          message: 'Downloading ${model.displayName}…',
          plan: plan));
      await modelManager.download(model, onProgress: (f) {
        update(state.copyWith(
            progress: JobProgress(
                phase: JobPhase.acquiringModel, fraction: f)));
      });
    }

    update(state.copyWith(
        phase: JobPhase.decoding,
        message: 'Decoding (orientation applied)…',
        plan: plan));
    final source = preDownsampled ?? await imageService.decodeToRgba(input);

    update(state.copyWith(
        phase: JobPhase.inferring,
        message: 'Running ${model.displayName} at ${plan.scale}×…'));

    // ---- real native inference --------------------------------------
    final engine = engineFactory();
    final paths = await modelManager.modelPaths(model);
    final tileStart = DateTime.now();
    final tileDurations = <Duration>[];
    final control = ControlBlock();
    _activeControlBlock = control;

    final stopwatch = Stopwatch()..start();
    late final NativeRunResult native;
    try {
      native = await engine.run(
        NativeJobRequest(
          paramPath: paths.param,
          binPath: paths.bin,
          rgba: source.pixels,
          width: source.width,
          height: source.height,
          scale: plan.scale,
          tileSize: plan.tileSize,
          tileOverlap: plan.tileOverlap,
          contextMargin: plan.contextMargin,
          threads: device.engineThreads,
          compute: preferGpu && device.gpuAvailable ? 2 : 1,
          // The engine facade injects the control-block address from
          // the `control` handle (web stubs have no native pointer).
          controlBlockAddress: 0,
        ),
        control: control,
        onProgress: (p) {
          final elapsed = DateTime.now().difference(tileStart);
          if (p.tilesDone > tileDurations.length) {
            tileDurations.add(elapsed);
          }
          Duration? eta;
          if (tileDurations.length >= 2 && p.tilesTotal > 0) {
            final meanMs = tileDurations
                    .map((d) => d.inMilliseconds)
                    .reduce((a, b) => a + b) /
                tileDurations.length;
            final remaining = (p.tilesTotal - p.tilesDone) * meanMs;
            // Only show an ETA while timings look stable (late tiles
            // within 3x of the mean).
            final last = tileDurations.last.inMilliseconds;
            if (last < meanMs * 3 && meanMs > 0) {
              eta = Duration(milliseconds: remaining.round());
            }
          }
          update(state.copyWith(
            progress: JobProgress(
              phase: JobPhase.inferring,
              fraction: p.fraction,
              tilesDone: p.tilesDone,
              tilesTotal: p.tilesTotal,
              attempt: p.attempt,
              tileSize: p.tileSize,
              message: p.attempt > 1
                  ? 'Memory pressure — retrying with ${p.tileSize}px tiles…'
                  : null,
            ),
            eta: eta,
          ));
        },
      );
    } on NativeEngineException catch (e) {
      stopwatch.stop();
      _activeControlBlock = null;
      final phase = e.isCancellation ? JobPhase.cancelled : JobPhase.failed;
      update(state.copyWith(
        phase: phase,
        error: e.isCancellation ? 'Cancelled.' : e.message,
        completedAt: DateTime.now(),
      ));
      throw JobManagerException(e.message, phase: phase);
    } finally {
      _activeControlBlock = null;
    }
    control.dispose();
    stopwatch.stop();

    if (native.width != source.width * plan.scale ||
        native.height != source.height * plan.scale) {
      update(state.copyWith(
          phase: JobPhase.failed,
          error: 'Engine returned ${native.width}×${native.height}, '
              'expected ${source.width * plan.scale}×${source.height * plan.scale}.',
          completedAt: DateTime.now()));
      throw JobManagerException(state.error!, phase: JobPhase.inferring);
    }

    update(state.copyWith(
        phase: JobPhase.encoding,
        progress: const JobProgress(
            phase: JobPhase.encoding, fraction: 0.95),
        message:
            'Encoding ${plan.outputFormat == OutputFormat.png ? 'PNG' : 'JPEG q${plan.jpegQuality}'}…'));

    final encoded = await compute(
      encodeRgbaIsolate,
      EncodePayload(
        pixels: native.rgba,
        width: native.width,
        height: native.height,
        format: plan.outputFormat,
        jpegQuality: plan.jpegQuality,
      ),
    );

    update(state.copyWith(phase: JobPhase.saving, message: 'Saving…'));

    final baseName = _baseNameFor(input, plan);
    final ext = plan.outputFormat == OutputFormat.png ? 'png' : 'jpg';
    final outFile = await storage.writeOutput(baseName, ext, encoded);

    // Android: also export the result to the shared gallery
    // (Pictures/RealScale-Pro). The app-documents copy always remains;
    // a gallery failure is surfaced honestly, never fatal.
    var savedToGallery = false;
    var galleryNote = '';
    if (device.platform == 'android') {
      try {
        final galleryPath = await storage.exportToGallery(
            outFile, 'image/${plan.outputFormat == OutputFormat.png ? 'png' : 'jpeg'}');
        if (galleryPath != null) {
          savedToGallery = true;
          galleryNote = galleryPath;
        }
      } on StorageException catch (e) {
        galleryNote = e.message;
      }
    }

    final backend = preferGpu && device.gpuAvailable
        ? 'ncnn-vulkan (experimental)'
        : 'ncnn-cpu';

    final result = JobResult(
      outputPath: outFile.path,
      inputWidth: metadata.width,
      inputHeight: metadata.height,
      outputWidth: native.width,
      outputHeight: native.height,
      inputFileBytes: input.lengthSync(),
      outputFileBytes: outFile.lengthSync(),
      duration: DateTime.now().difference(startedAt),
      modelId: model.id,
      mode: mode,
      outputFormat: plan.outputFormat,
      attempt: native.attempt,
      backend: backend,
      savedToGallery: savedToGallery,
      galleryNote: galleryNote,
    );
    update(state.copyWith(
      phase: JobPhase.done,
      progress: const JobProgress(phase: JobPhase.done, fraction: 1),
      result: result,
      message: null,
      eta: null,
      completedAt: DateTime.now(),
    ));
    return result;
  }

  /// Requests cancellation of the active run (between tiles).
  void cancel() => _activeControlBlock?.requestCancel();

  void dispose() => _stateController.dispose();

  String _baseNameFor(File input, PipelinePlan plan) {
    final raw = input.uri.pathSegments.last;
    final dot = raw.lastIndexOf('.');
    final stem = dot > 0 ? raw.substring(0, dot) : raw;
    final modeTag = plan.mode == ProcessingMode.upscale ? 'upscale' : 'sr';
    return '${stem}_${modeTag}_${plan.scale}x';
  }
}
