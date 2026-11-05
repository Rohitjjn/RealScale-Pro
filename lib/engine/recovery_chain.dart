import 'dart:async';

import '../core/app_logger.dart';
import 'engine_types.dart';
import 'ncnn/ncnn_ffi.dart';
import 'tiling_engine.dart' show CancelledJobException;

/// RecoveryChain (architecture.md §5, prd §8.5): when a tile/job fails the
/// engine retries automatically, in this order, before giving up with a
/// friendly failure:
///   1. halve the tile size
///   2. GPU → CPU
///   3. fewer threads
///   4. friendly failure
class RecoveryChain {
  RecoveryChain({
    required this.engine,
    required this.replanForRetry,
    required this.basePlan,
  });

  final UpscaleEngine engine;

  /// Returns an adjusted copy of [basePlan] for the given retry step.
  final PipelinePlan Function(PipelinePlan plan, int step) replanForRetry;
  final PipelinePlan basePlan;

  static const maxSteps = 3;

  /// Runs [engine.run], intercepting failures and retrying with safer
  /// settings. Emitted events carry honest recovery notes.
  Stream<ProgressEvent> runWithRecovery(JobSpec job, PipelineContext ctx) {
    final controller = StreamController<ProgressEvent>();
    final recoverySteps = <String>[];

    Future<void> attempt(int step) async {
      final plan = step == 0 ? basePlan : replanForRetry(basePlan, step);
      try {
        await for (final event in engine.run(job, _ctxWith(ctx, plan))) {
          if (event.stage == JobStage.done && event.result != null) {
            controller.add(_withRecovery(event, recoverySteps));
          } else {
            controller.add(event);
          }
        }
        await controller.close();
      } on CancelledJobException {
        controller.add(
          ProgressEvent(
            jobId: job.jobId,
            stage: JobStage.cancelled,
            percent: 0,
            message: 'Cancelled',
          ),
        );
        await controller.close();
      } on NcnnCancelledException {
        controller.add(
          ProgressEvent(
            jobId: job.jobId,
            stage: JobStage.cancelled,
            percent: 0,
            message: 'Cancelled',
          ),
        );
        await controller.close();
      } on NcnnOomException {
        if (step < maxSteps) {
          recoverySteps.add(
            'tile ${plan.tileSize}→${plan.tileSize ~/ 2} (out of memory)',
          );
          controller.add(
            ProgressEvent(
              jobId: job.jobId,
              stage: JobStage.upscaling,
              percent: 0,
              message: 'tile-halved',
            ),
          );
          await attempt(step + 1);
        } else {
          _fail(controller, job, 'Ran out of memory even with safer settings.');
        }
      } on NcnnErrorException catch (e) {
        if (step < maxSteps) {
          if (plan.useGpu) {
            recoverySteps.add('GPU → CPU');
          } else {
            recoverySteps.add(
              'threads ${plan.threadCount}→${(plan.threadCount / 2).ceil()}',
            );
          }
          await attempt(step + 1);
        } else {
          _fail(controller, job, _friendly(e.detail));
        }
      } catch (e, st) {
        AppLogger.e('RecoveryChain', 'job failed after recovery chain', e, st);
        _fail(controller, job, _friendly(e.toString()));
      }
    }

    controller.onListen = () => attempt(0);
    return controller.stream;
  }

  PipelineContext _ctxWith(PipelineContext ctx, PipelinePlan plan) =>
      PipelineContext(
        catalog: ctx.catalog,
        device: ctx.device,
        modelDir: ctx.modelDir,
        tempDir: ctx.tempDir,
        plan: plan,
        outputWriter: ctx.outputWriter,
      );

  ProgressEvent _withRecovery(ProgressEvent e, List<String> steps) {
    if (steps.isEmpty) return e;
    final result = e.result!;
    return ProgressEvent(
      jobId: e.jobId,
      stage: e.stage,
      percent: e.percent,
      tileIndex: e.tileIndex,
      tileTotal: e.tileTotal,
      etaSeconds: e.etaSeconds,
      message: e.message,
      result: EngineResult(
        jobId: result.jobId,
        outputPath: result.outputPath,
        outputWidth: result.outputWidth,
        outputHeight: result.outputHeight,
        outputBytes: result.outputBytes,
        elapsedMs: result.elapsedMs,
        modelId: result.modelId,
        usedScale: result.usedScale,
        tileSizeUsed: result.tileSizeUsed,
        usedGpu: result.usedGpu,
        facesRestored: result.facesRestored,
        recoverySteps: List.of(steps),
        tempInputUsed: result.tempInputUsed,
      ),
    );
  }

  void _fail(StreamController<ProgressEvent> c, JobSpec job, String message) {
    c.add(
      ProgressEvent(
        jobId: job.jobId,
        stage: JobStage.failed,
        percent: 0,
        message: message,
      ),
    );
    c.close();
  }

  static String _friendly(String raw) =>
      'The upscale could not finish on this device. Try safer settings.';
}

/// Adjusts a plan for a recovery step: 1) halve tile, 2) CPU, 3) fewer threads.
PipelinePlan replanStandard(PipelinePlan plan, int step) {
  switch (step) {
    case 1:
      return _copy(
        plan,
        tileSize: (plan.tileSize ~/ 2).clamp(64, plan.tileSize),
      );
    case 2:
      return _copy(plan, useGpu: false);
    case 3:
      return _copy(
        plan,
        threadCount: (plan.threadCount / 2).ceil().clamp(1, 4),
      );
    default:
      return plan;
  }
}

PipelinePlan _copy(
  PipelinePlan p, {
  int? tileSize,
  bool? useGpu,
  int? threadCount,
}) => PipelinePlan(
  model: p.model,
  nativeScale: p.nativeScale,
  resamplePercent: p.resamplePercent,
  extraResizeStep: p.extraResizeStep,
  tileSize: tileSize ?? p.tileSize,
  overlapPx: p.overlapPx,
  threadCount: threadCount ?? p.threadCount,
  useGpu: useGpu ?? p.useGpu,
  inputPixelLimit: p.inputPixelLimit,
  outputCapMegapixels: p.outputCapMegapixels,
  outputWidth: p.outputWidth,
  outputHeight: p.outputHeight,
  etaBaselineMsPerTile: p.etaBaselineMsPerTile * (tileSize != null ? 2 : 1),
);
