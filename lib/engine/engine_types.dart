import 'dart:typed_data';

import '../core/app_exception.dart';
import '../services/model_catalog/model_catalog.dart';
import 'image_bitmap.dart';
import 'upscale_engine.dart';

export 'upscale_engine.dart';

/// Everything an engine needs besides the JobSpec: resolved plan, catalog,
/// device profile and where model files live (architecture.md §5).
class PipelineContext {
  const PipelineContext({
    required this.catalog,
    required this.device,
    required this.modelDir,
    required this.tempDir,
    required this.plan,
    required this.outputWriter,
  });

  final ModelCatalog catalog;
  final DeviceProfile device;
  final String modelDir; // installed model files live here
  final String tempDir;
  final PipelinePlan plan;

  /// Writes the final bitmap to a temp file (ImageService owns encoders).
  final Future<void> Function(String path, Bitmap bmp) outputWriter;
}

/// The resolved plan (from PipelinePlanner). Kept in engine so engines never
/// import the planner service directly (dependencies point downward only).
class PipelinePlan {
  const PipelinePlan({
    required this.model,
    required this.nativeScale,
    required this.resamplePercent,
    required this.extraResizeStep,
    required this.tileSize,
    required this.overlapPx,
    required this.threadCount,
    required this.useGpu,
    required this.inputPixelLimit,
    required this.outputCapMegapixels,
    required this.outputWidth,
    required this.outputHeight,
    required this.etaBaselineMsPerTile,
  });

  final ModelDef model;
  final int nativeScale;
  final int resamplePercent; // 50/75/100/125 after the native run
  final bool extraResizeStep; // true for 5x
  final int tileSize;
  final int overlapPx;
  final int threadCount;
  final bool useGpu;
  final int inputPixelLimit;
  final int outputCapMegapixels;
  final int outputWidth;
  final int outputHeight;
  final int etaBaselineMsPerTile;
}

/// Per-tile input/output. Uint8List RGBA row-major.
class TileJob {
  const TileJob({
    required this.modelId,
    required this.nativeScale,
    required this.threadCount,
    required this.useGpu,
    required this.width,
    required this.height,
    required this.pixels,
    required this.modelDir,
  });
  final String modelId;
  final int nativeScale;
  final int threadCount;
  final bool useGpu;
  final int width;
  final int height;
  final Uint8List pixels;
  final String modelDir;
}

/// A function that upscales ONE tile by [TileJob.nativeScale].
typedef TileProcessor = Future<Uint8List> Function(TileJob job);

/// Engine interface (architecture.md §5). Implementations never import UI.
abstract class UpscaleEngine {
  String get engineId; // "ncnn" | "lanczos"

  /// Whether this engine can run [model] on [device] right now.
  bool supports(ModelDef m, DeviceProfile d);

  /// Single stream: progress events; the final event carries EngineResult.
  Stream<ProgressEvent> run(JobSpec job, PipelineContext ctx);

  Future<void> cancel(String jobId);

  Future<void> dispose();
}

/// Builds the engine for the current device + model availability.
/// The ncnn engine wins when the native library is present; otherwise the
/// honest pure-Dart engine is used so the app remains fully functional
/// (real work, real progress — never fake).
class EngineRegistry {
  EngineRegistry({required this.engines});

  final List<UpscaleEngine> engines;

  UpscaleEngine? pick(ModelDef model, DeviceProfile device) {
    for (final e in engines) {
      if (e.supports(model, device)) return e;
    }
    return null;
  }
}

base class EngineException extends AppException {
  const EngineException(super.message);
}
