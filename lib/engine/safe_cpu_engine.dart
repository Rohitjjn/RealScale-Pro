import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:image/image.dart' as image_lib;
import 'package:path_provider/path_provider.dart';

import '../core/models.dart';
import 'upscale_engine.dart';

/// Crash-safe CPU fallback used when the native ncnn library is unavailable.
///
/// It performs real tile work and therefore emits honest progress, but it is a
/// high-quality resize rather than AI inference. The ncnn adapter replaces this
/// fallback in supported release builds.
class SafeCpuEngine implements UpscaleEngine {
  final Map<String, Isolate> _active = <String, Isolate>{};

  @override
  String get engineId => 'safe-cpu';

  @override
  Stream<EngineEvent> run(JobSpec job) {
    final StreamController<EngineEvent> controller =
        StreamController<EngineEvent>();
    final ReceivePort messages = ReceivePort();
    final Stopwatch elapsed = Stopwatch()..start();

    () async {
      try {
        final Directory temporary = await getTemporaryDirectory();
        final String outputPath =
            '${temporary.path}/realscale_result_${job.id}.png';
        final Isolate isolate = await Isolate.spawn<_WorkerRequest>(
          _work,
          _WorkerRequest(
            messages.sendPort,
            job.id,
            job.preparedInputPath ?? job.input.path,
            outputPath,
            job.tileSize,
            job.scale,
          ),
        );
        _active[job.id] = isolate;

        await for (final Object? message in messages) {
          if (message is int) {
            final int total =
                ((job.input.width / job.tileSize).ceil() *
                        (job.input.height / job.tileSize).ceil())
                    .clamp(1, 1 << 30);
            final double averageMs = elapsed.elapsedMilliseconds / message;
            final int remainingMs = ((total - message) * averageMs).round();
            controller.add(
              EngineProgress(
                ProgressEvent(
                  jobId: job.id,
                  stage: JobStage.enhancing,
                  completedTiles: message,
                  totalTiles: total,
                  elapsed: elapsed.elapsed,
                  eta: Duration(milliseconds: remainingMs),
                ),
              ),
            );
          } else if (message == 'done') {
            elapsed.stop();
            controller.add(EngineCompleted(outputPath, elapsed.elapsed));
            break;
          } else if (message == 'decode_error') {
            controller.add(const EngineFailed(AppFailure.unsupportedImage));
            break;
          } else if (message == 'failed') {
            controller.add(const EngineFailed(AppFailure.unknown));
            break;
          }
        }
      } on Object {
        controller.add(const EngineFailed(AppFailure.unknown));
      } finally {
        _active.remove(job.id);
        messages.close();
        await controller.close();
      }
    }();
    return controller.stream;
  }

  @override
  Future<void> cancel(String jobId) async {
    _active.remove(jobId)?.kill(priority: Isolate.immediate);
  }

  @override
  Future<void> dispose() async {
    for (final Isolate isolate in _active.values) {
      isolate.kill(priority: Isolate.immediate);
    }
    _active.clear();
  }

  static Future<void> _work(_WorkerRequest request) async {
    try {
      final Uint8List bytes = await File(request.inputPath).readAsBytes();
      final image_lib.Image? source = image_lib.decodeImage(bytes);
      if (source == null) {
        request.port.send('decode_error');
        return;
      }
      final image_lib.Image oriented = image_lib.bakeOrientation(source);
      final image_lib.Image result = image_lib.Image(
        width: oriented.width * request.scale,
        height: oriented.height * request.scale,
        numChannels: oriented.numChannels,
      );
      int completed = 0;
      for (int y = 0; y < oriented.height; y += request.tileSize) {
        for (int x = 0; x < oriented.width; x += request.tileSize) {
          final int width = math.min(request.tileSize, oriented.width - x);
          final int height = math.min(request.tileSize, oriented.height - y);
          final image_lib.Image tile = image_lib.copyCrop(
            oriented,
            x: x,
            y: y,
            width: width,
            height: height,
          );
          final image_lib.Image enlarged = image_lib.copyResize(
            tile,
            width: width * request.scale,
            height: height * request.scale,
            interpolation: image_lib.Interpolation.cubic,
          );
          image_lib.compositeImage(
            result,
            enlarged,
            dstX: x * request.scale,
            dstY: y * request.scale,
          );
          completed += 1;
          request.port.send(completed);
        }
      }
      await File(
        request.outputPath,
      ).writeAsBytes(image_lib.encodePng(result), flush: true);
      request.port.send('done');
    } on Object {
      request.port.send('failed');
    }
  }
}

class _WorkerRequest {
  const _WorkerRequest(
    this.port,
    this.jobId,
    this.inputPath,
    this.outputPath,
    this.tileSize,
    this.scale,
  );

  final SendPort port;
  final String jobId;
  final String inputPath;
  final String outputPath;
  final int tileSize;
  final int scale;
}
