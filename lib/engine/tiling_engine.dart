import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';

import 'engine_types.dart';
import 'image_bitmap.dart';
import 'lanczos_resampler.dart';
import '../services/model_catalog/model_catalog.dart' show ModelDef;

/// Output writer injected via PipelineContext (ImageService owns encoders).
typedef OutputWriter = Future<void> Function(String path, Bitmap bmp);

/// TilingEngine — split → process → blend → write (architecture.md §5,
/// prd §8.5). Shared orchestration used by both engines: ncnn (FFI tiles) and
/// the pure-Dart fallback. Owns tiling, overlap feathering, honest progress
/// (completed tiles only), cancellation and the resample chain.
class TilingEngine implements UpscaleEngine {
  TilingEngine({
    required this.tileProcessor,
    this.engineId = 'ncnn',
    this.canProcessModel,
  });

  final TileProcessor tileProcessor;

  @override
  final String engineId;

  /// Extra availability rule (e.g. ncnn library loaded). Default: any model.
  final bool Function(ModelDef m, DeviceProfile d)? canProcessModel;

  final _cancelRequested = <String>{};

  @override
  bool supports(ModelDef m, DeviceProfile d) =>
      m.available && (canProcessModel?.call(m, d) ?? true);

  @override
  Stream<ProgressEvent> run(JobSpec job, PipelineContext ctx) => _run(job, ctx);

  Stream<ProgressEvent> _run(JobSpec job, PipelineContext ctx) async* {
    final plan = ctx.plan;
    final jobId = job.jobId;
    _cancelRequested.remove(jobId);
    final stopwatch = Stopwatch()..start();

    Future<void> throwIfCancelled() async {
      if (_cancelRequested.contains(jobId)) {
        _cancelRequested.remove(jobId);
        throw CancelledJobException(jobId);
      }
    }

    await throwIfCancelled();
    yield ProgressEvent(jobId: jobId, stage: JobStage.preparing, percent: 0);

    // 1) Decode input pixels (ImageService decoded them into job options).
    final src = _decodeInput(job);
    await throwIfCancelled();

    // 2) Tile → process → blend at the model's native scale.
    final nativeW = src.width * plan.nativeScale;
    final nativeH = src.height * plan.nativeScale;
    final native = Bitmap(nativeW, nativeH, Uint8List(nativeW * nativeH * 4));

    final step = math.max(1, plan.tileSize - plan.overlapPx);
    final cols = (src.width + step - 1) ~/ step;
    final rows = (src.height + step - 1) ~/ step;
    final totalTiles = cols * rows;
    var doneTiles = 0;
    final etaTracker = _Ema();

    for (var row = 0; row < rows; row++) {
      for (var col = 0; col < cols; col++) {
        await throwIfCancelled();
        final x = col * step;
        final y = row * step;
        final w = math.min(plan.tileSize, src.width - x);
        final h = math.min(plan.tileSize, src.height - y);
        if (w <= 0 || h <= 0) continue;

        // Input tile carries overlap margin; blending feathers interior
        // seams (prd §8.5).
        final ox = math.max(0, x - plan.overlapPx);
        final oy = math.max(0, y - plan.overlapPx);
        final ow = math.min(src.width, x + w + plan.overlapPx) - ox;
        final oh = math.min(src.height, y + h + plan.overlapPx) - oy;
        final tile = src.crop(ox, oy, ow, oh);

        final t0 = DateTime.now();
        final outPixels = await tileProcessor(
          TileJob(
            modelId: plan.model.id,
            nativeScale: plan.nativeScale,
            threadCount: plan.threadCount,
            useGpu: plan.useGpu,
            width: tile.width,
            height: tile.height,
            pixels: tile.pixels,
            modelDir: ctx.modelDir,
          ),
        );
        etaTracker.add(DateTime.now().difference(t0).inMilliseconds);

        final outW = ow * plan.nativeScale;
        final outH = oh * plan.nativeScale;
        final patch = Bitmap(outW, outH, outPixels);
        final destX = (x - ox) * plan.nativeScale;
        final destY = (y - oy) * plan.nativeScale;
        native.blendPatch(
          patch,
          destX,
          destY,
          plan.overlapPx * plan.nativeScale,
        );

        doneTiles++;
        final avg = etaTracker.average;
        final remaining = totalTiles - doneTiles;
        yield ProgressEvent(
          jobId: jobId,
          stage: JobStage.upscaling,
          percent: doneTiles / totalTiles * 0.85, // upscale = 0–85% of bar
          tileIndex: doneTiles,
          tileTotal: totalTiles,
          etaSeconds: remaining > 0 && avg > 0 ? (remaining * avg ~/ 1000) : 0,
        );
        // Yield to the event loop so the UI stays at 60 fps.
        await Future<void>.delayed(Duration.zero);
      }
    }

    await throwIfCancelled();

    // 3) Resample chain to the user's target scale (prd §8.4).
    var output = native;
    if (plan.resamplePercent != 100) {
      final dstW = (nativeW * plan.resamplePercent / 100).round();
      final dstH = (nativeH * plan.resamplePercent / 100).round();
      output = await LanczosResampler().resample(native, dstW, dstH);
      if (plan.extraResizeStep) {
        output = unsharpMask(output);
      }
    }

    // 4) Write temp output (engines never save to the gallery directly).
    yield ProgressEvent(jobId: jobId, stage: JobStage.saving, percent: 0.95);
    final outPath = '${ctx.tempDir}/rs_${job.jobId}_out.png';
    await ctx.outputWriter(outPath, output);

    stopwatch.stop();
    yield ProgressEvent(
      jobId: jobId,
      stage: JobStage.done,
      percent: 1,
      result: EngineResult(
        jobId: jobId,
        outputPath: outPath,
        outputWidth: output.width,
        outputHeight: output.height,
        outputBytes: output.byteLength,
        elapsedMs: stopwatch.elapsedMilliseconds,
        modelId: plan.model.id,
        usedScale: job.scale,
        tileSizeUsed: plan.tileSize,
        usedGpu: plan.useGpu,
        facesRestored: 0,
        recoverySteps: const [],
      ),
    );
  }

  Bitmap _decodeInput(JobSpec job) {
    final pixels = job.options['decodedPixels'];
    final width = job.options['decodedWidth'];
    final height = job.options['decodedHeight'];
    if (pixels is Uint8List && width is int && height is int) {
      return Bitmap(width, height, pixels);
    }
    throw const EngineException(
      'JobSpec carries no decoded pixels (ImageService must decode first)',
    );
  }

  @override
  Future<void> cancel(String jobId) async {
    _cancelRequested.add(jobId);
  }

  @override
  Future<void> dispose() async {
    _cancelRequested.clear();
  }
}

/// Thrown internally when the user cancelled; JobManager maps it to a
/// cancelled stage (not an error).
class CancelledJobException implements Exception {
  CancelledJobException(this.jobId);
  final String jobId;
}

final class _Ema {
  double _value = 0;
  var _count = 0;

  void add(int ms) {
    _count++;
    _value = _count == 1 ? ms.toDouble() : _value * 0.7 + ms * 0.3;
  }

  double get average => _value;
}
