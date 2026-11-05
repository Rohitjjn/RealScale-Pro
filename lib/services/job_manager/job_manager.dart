import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import '../../core/app_exception.dart';
import '../../core/app_logger.dart';
import '../../engine/engine_types.dart';
import '../../engine/image_bitmap.dart';
import '../../engine/recovery_chain.dart';
import '../image_service/image_service.dart';
import '../storage_service/storage_service.dart';

/// Face detector contract — real crops from the detection helper.
class FaceCrop {
  const FaceCrop({
    required this.left,
    required this.top,
    required this.width,
    required this.height,
    required this.confidence,
  });
  final int left;
  final int top;
  final int width;
  final int height;
  final double confidence;
}

/// Face restorer contract (native GFPGAN or test fake). Receives an RGBA
/// crop at output scale, returns the restored RGBA crop of the same size.
abstract class FaceRestorer {
  Future<Uint8List> restore(
    FaceCrop crop,
    Uint8List rgba,
    int width,
    int height,
  );
}

/// JobManager (architecture.md §4): one active job, cancel within 2 s,
/// temp-file hygiene, ETA moving average, haptic checkpoints, and the
/// Phase 2 face pipeline order upscale → restore faces → blend.
class JobManager {
  JobManager({
    required this.engine,
    required this.recoveryChainFactory,
    required this.storage,
    required this.haptics,
    this.engineSelector,
  });

  final UpscaleEngine engine;
  final RecoveryChain Function(PipelinePlan plan) recoveryChainFactory;
  final StorageService storage;
  final JobHaptics haptics;
  final UpscaleEngine Function(PipelineContext ctx)? engineSelector;

  UpscaleEngine? _activeEngine;

  JobStage get stage => _stage;
  JobStage _stage = JobStage.preparing;

  String? _activeJobId;
  StreamSubscription<ProgressEvent>? _sub;
  final _progressController = StreamController<ProgressEvent>.broadcast();

  /// Live progress for the processing screen.
  Stream<ProgressEvent> get progress => _progressController.stream;

  FaceRestorer? faceRestorer;
  Future<List<FaceCrop>> Function(Uint8List rgba, int width, int height)?
  faceDetector;

  double _lastHapticCheckpoint = 0;

  /// Runs a full job end-to-end. Returns the saved record path via the
  /// final ProgressEvent (stage done/failed/cancelled).
  Future<ProgressEvent> runJob({
    required JobSpec spec,
    required PipelineContext ctx,
    required String originalName,
    required Uint8List originalJpegBytes,
    required bool keepMetadata,
    required OutputKind outputKind,
    required int outputQuality,
    required bool hasAlpha,
  }) async {
    if (_activeJobId != null) {
      throw const EngineException('Another job is already running');
    }
    _activeJobId = spec.jobId;
    _lastHapticCheckpoint = 0;
    final completer = Completer<ProgressEvent>();
    _pendingCompleter = completer;
    _cancelRequested = false;

    final selectedEngine = engineSelector?.call(ctx);
    _activeEngine = selectedEngine ?? engine;
    final chain = selectedEngine != null
        ? RecoveryChain(
            engine: selectedEngine,
            basePlan: ctx.plan,
            replanForRetry: replanStandard,
          )
        : recoveryChainFactory(ctx.plan);
    _sub = chain
        .runWithRecovery(spec, ctx)
        .listen(
          (event) {
            if (!_progressController.isClosed) _progressController.add(event);
            _onEvent(
              event,
              spec,
              ctx,
              completer,
              originalName: originalName,
              originalJpegBytes: originalJpegBytes,
              keepMetadata: keepMetadata,
              outputKind: outputKind,
              outputQuality: outputQuality,
              hasAlpha: hasAlpha,
            );
          },
          onError: (Object e, StackTrace st) {
            AppLogger.e('JobManager', 'engine stream error', e, st);
            final fail = ProgressEvent(
              jobId: spec.jobId,
              stage: JobStage.failed,
              percent: 0,
              message: 'The upscale could not finish. Try safer settings.',
            );
            _finish(fail, completer, spec);
          },
          cancelOnError: true,
        );
    return completer.future;
  }

  Future<void> _onEvent(
    ProgressEvent event,
    JobSpec spec,
    PipelineContext ctx,
    Completer<ProgressEvent> completer, {
    required String originalName,
    required Uint8List originalJpegBytes,
    required bool keepMetadata,
    required OutputKind outputKind,
    required int outputQuality,
    required bool hasAlpha,
  }) async {
    _stage = event.stage;

    // Honest haptic checkpoints at 25/50/75% (design-system §7).
    if (event.stage == JobStage.upscaling) {
      for (final cp in const [0.25, 0.5, 0.75]) {
        if (event.percent >= cp && _lastHapticCheckpoint < cp) {
          _lastHapticCheckpoint = cp;
          haptics.checkpoint();
        }
      }
    }

    switch (event.stage) {
      case JobStage.done:
        try {
          // Face pipeline (Phase 2): upscale → restore faces → blend.
          var facesRestored = 0;
          if (spec.faceEnhancement &&
              faceDetector != null &&
              faceRestorer != null) {
            await _runFaceStage(
              spec,
              ctx,
              event,
              (count) => facesRestored = count,
            );
          }
          final savedPath = await _saveResult(
            event: event,
            spec: spec,
            originalName: originalName,
            originalJpegBytes: keepMetadata ? originalJpegBytes : null,
            outputKind: outputKind,
            outputQuality: outputQuality,
            hasAlpha: hasAlpha,
          );
          _finish(
            ProgressEvent(
              jobId: event.jobId,
              stage: JobStage.done,
              percent: 1,
              message: savedPath,
              result: _withFaces(event.result!, facesRestored),
            ),
            completer,
            spec,
          );
        } on AppException catch (e) {
          _finish(
            ProgressEvent(
              jobId: event.jobId,
              stage: JobStage.failed,
              percent: 0,
              message: e.message,
            ),
            completer,
            spec,
          );
        } catch (e, st) {
          AppLogger.e('JobManager', 'save failed', e, st);
          _finish(
            ProgressEvent(
              jobId: event.jobId,
              stage: JobStage.failed,
              percent: 0,
              message: 'Could not save the result. Check free storage.',
            ),
            completer,
            spec,
          );
        }
      case JobStage.failed:
      case JobStage.cancelled:
        _finish(event, completer, spec);
      default:
        break;
    }
  }

  /// Face stage with real `faces` progress (task 2.9): whole image already
  /// upscaled; detect crops on the OUTPUT, restore each crop, soft-blend.
  Future<void> _runFaceStage(
    JobSpec spec,
    PipelineContext ctx,
    ProgressEvent doneEvent,
    void Function(int) reportCount,
  ) async {
    final result = doneEvent.result!;
    final bitmap = await _readBitmap(result.outputPath);
    final faces = await faceDetector!(
      bitmap.pixels,
      bitmap.width,
      bitmap.height,
    );
    if (faces.isEmpty) return;
    final maxCrops = faces.take(12).toList();

    for (var i = 0; i < maxCrops.length; i++) {
      if (_cancelled) {
        throw const CancelledException();
      }
      final crop = maxCrops[i];
      final clamped = FaceCrop(
        left: crop.left.clamp(0, bitmap.width - 1),
        top: crop.top.clamp(0, bitmap.height - 1),
        width: crop.width.clamp(16, bitmap.width - crop.left),
        height: crop.height.clamp(16, bitmap.height - crop.top),
        confidence: crop.confidence,
      );
      final sub = bitmap.crop(
        clamped.left,
        clamped.top,
        clamped.width,
        clamped.height,
      );
      final restored = await faceRestorer!.restore(
        clamped,
        sub.pixels,
        sub.width,
        sub.height,
      );
      // Soft-mask blend: feather 12% of crop size on every edge.
      final feather = (clamped.width * 0.12).round().clamp(4, 64);
      final patch = Bitmap(
        clamped.width,
        clamped.height,
        Uint8List.fromList(restored),
      );
      bitmap.blendPatch(patch, clamped.left, clamped.top, feather);

      if (!_progressController.isClosed) {
        _progressController.add(
          ProgressEvent(
            jobId: spec.jobId,
            stage: JobStage.faces,
            percent: 0.85 + 0.1 * (i + 1) / maxCrops.length,
            tileIndex: i + 1,
            tileTotal: maxCrops.length,
          ),
        );
      }
    }
    await ctx.outputWriter(result.outputPath, bitmap);
    reportCount(maxCrops.length);
  }

  Future<Bitmap> _readBitmap(String path) async {
    // PNG decode lives in ImageService; JobManager reads via the registered
    // reader to keep the engine stack IO-light.
    final bytes = await File(path).readAsBytes();
    final reader = _bitmapReader;
    if (reader == null) {
      throw const EngineException('No bitmap reader wired');
    }
    return reader(bytes);
  }

  static Future<Bitmap> Function(Uint8List)? _bitmapReader;

  /// ImageService registers its PNG reader here at startup.
  static void registerBitmapReader(Future<Bitmap> Function(Uint8List) r) =>
      _bitmapReader = r;

  Future<String> _saveResult({
    required ProgressEvent event,
    required JobSpec spec,
    required String originalName,
    required Uint8List? originalJpegBytes,
    required OutputKind outputKind,
    required int outputQuality,
    required bool hasAlpha,
  }) async {
    final result = event.result!;
    // Output format rules (prd §7.3): transparency → PNG always.
    final kind = hasAlpha ? OutputKind.png : outputKind;
    final tempBytes = await File(result.outputPath).readAsBytes();
    final pngBitmap = await (_bitmapReader!(tempBytes));
    final ext = switch (kind) {
      OutputKind.png => 'png',
      OutputKind.jpeg => 'jpg',
      OutputKind.webp => 'webp',
      OutputKind.sameAsSource => 'png',
    };
    final encodedPath = '${result.outputPath}_saved.$ext';
    final saver = _encodedSaver;
    if (saver == null) throw const EngineException('No encoder wired');
    await saver(encodedPath, pngBitmap, kind, outputQuality, originalJpegBytes);
    final saved = await storage.saveResult(
      tempPath: encodedPath,
      originalName: originalName,
      width: result.outputWidth,
      height: result.outputHeight,
      scale: result.usedScale,
      modelId: result.modelId,
      elapsedMs: result.elapsedMs,
    );
    // Temp hygiene: engine temp files are deleted after the job (rules.md 8).
    await _deleteQuietly(result.outputPath);
    await _deleteQuietly(encodedPath);
    return saved.savedPath;
  }

  static Future<void> Function(
    String path,
    Bitmap bmp,
    OutputKind kind,
    int quality,
    Uint8List? metadataSource,
  )?
  _encodedSaver;

  /// ImageService registers the final encoder here at startup.
  static void registerEncodedSaver(
    Future<void> Function(String, Bitmap, OutputKind, int, Uint8List?) saver,
  ) => _encodedSaver = saver;

  Future<void> _deleteQuietly(String path) async {
    try {
      final f = File(path);
      if (await f.exists()) await f.delete();
    } catch (_) {}
  }

  EngineResult _withFaces(EngineResult r, int faces) => EngineResult(
    jobId: r.jobId,
    outputPath: r.outputPath,
    outputWidth: r.outputWidth,
    outputHeight: r.outputHeight,
    outputBytes: r.outputBytes,
    elapsedMs: r.elapsedMs,
    modelId: r.modelId,
    usedScale: r.usedScale,
    tileSizeUsed: r.tileSizeUsed,
    usedGpu: r.usedGpu,
    facesRestored: faces,
    recoverySteps: r.recoverySteps,
    tempInputUsed: r.tempInputUsed,
  );

  bool get _cancelled => _cancelRequested;

  var _cancelRequested = false;

  /// Cancels within 2 s (prd §7.1): engine cancel flag + subscription close.
  Future<void> cancel(String jobId) async {
    if (_activeJobId != jobId) return;
    _cancelRequested = true;
    await (_activeEngine ?? engine).cancel(jobId);
    _activeEngine = null;
    await _sub?.cancel();
    _sub = null;
    final pending = _pendingCompleter;
    _activeJobId = null;
    _pendingCompleter = null;
    _cancelRequested = false;
    if (pending != null && !pending.isCompleted) {
      pending.complete(
        ProgressEvent(jobId: jobId, stage: JobStage.cancelled, percent: 0),
      );
    }
  }

  Completer<ProgressEvent>? _pendingCompleter;

  void _finish(
    ProgressEvent e,
    Completer<ProgressEvent>? completer,
    JobSpec? spec,
  ) {
    _activeJobId = null;
    _activeEngine = null;
    _stage = e.stage;
    _pendingCompleter = null;
    if (completer != null && !completer.isCompleted) completer.complete(e);
  }

  Future<void> dispose() async {
    await _sub?.cancel();
    await _progressController.close();
  }
}

/// Haptic checkpoints interface (wired to the UI layer).
abstract class JobHaptics {
  void checkpoint();
}

class NoopHaptics implements JobHaptics {
  @override
  void checkpoint() {}
}
