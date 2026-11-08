/// Web stub for the native engine bindings. dart:ffi is unavailable on
/// the web; the app reports the engine as unavailable there instead of
/// pretending to run inference. Mirrors the IO facade's API surface.
library;

import 'dart:typed_data';

class NativeEngineException implements Exception {
  NativeEngineException(this.statusCode, this.message);
  final int statusCode;
  final String message;

  bool get isCancellation => statusCode == 8; // RsStatus.cancelled

  @override
  String toString() =>
      'NativeEngineException(status $statusCode): $message';
}

class NativeProgress {
  const NativeProgress({
    required this.stage,
    required this.tilesDone,
    required this.tilesTotal,
    required this.attempt,
    required this.tileSize,
    required this.fraction,
  });
  final int stage;
  final int tilesDone;
  final int tilesTotal;
  final int attempt;
  final int tileSize;
  final double fraction;
}

class NativeRunResult {
  const NativeRunResult({
    required this.rgba,
    required this.width,
    required this.height,
    required this.attempt,
  });
  final Uint8List rgba;
  final int width;
  final int height;
  final int attempt;
}

class NativeDeviceInfo {
  const NativeDeviceInfo({
    required this.gpuAvailable,
    required this.usingGpu,
    required this.threadCount,
    required this.fp16Storage,
  });
  final int gpuAvailable;
  final int usingGpu;
  final int threadCount;
  final int fp16Storage;
}

class ModelLoadReport {
  const ModelLoadReport({
    required this.statusCode,
    required this.message,
    required this.paramBytes,
    required this.binBytes,
    required this.tileHint,
  });
  final int statusCode;
  final String message;
  final int paramBytes;
  final int binBytes;
  final int tileHint;
  bool get ok => statusCode == 0;
}

class NativeJobRequest {
  const NativeJobRequest({
    required this.paramPath,
    required this.binPath,
    required this.rgba,
    required this.width,
    required this.height,
    required this.scale,
    required this.tileSize,
    required this.tileOverlap,
    required this.contextMargin,
    required this.threads,
    required this.controlBlockAddress,
    this.compute = 1, // rs_compute: 0 AUTO, 1 CPU, 2 GPU
  });
  final String paramPath;
  final String binPath;
  final Uint8List rgba;
  final int width;
  final int height;
  final int scale;
  final int tileSize;
  final int tileOverlap;
  final int contextMargin;
  final int threads;
  final int controlBlockAddress;
  final int compute;
}

class ControlBlock {
  void requestCancel() {}
  bool get cancelRequested => false;
  NativeProgress read() => const NativeProgress(
      stage: 0,
      tilesDone: 0,
      tilesTotal: 0,
      attempt: 1,
      tileSize: 0,
      fraction: 0);
  void dispose() {}
}

/// Minimal engine capability needed by the JobManager (implemented by
/// [NativeEngine] on IO platforms; faked in unit tests).
abstract class EngineRunner {
  Future<NativeRunResult> run(
    NativeJobRequest job, {
    ControlBlock? control,
    void Function(NativeProgress)? onProgress,
    Duration pollInterval = const Duration(milliseconds: 120),
  });
}

class NativeEngine implements EngineRunner {
  NativeDeviceInfo get deviceInfo => const NativeDeviceInfo(
      gpuAvailable: 0, usingGpu: 0, threadCount: 0, fp16Storage: 0);

  static NativeEngine create(
          {int threads = 0, int useFp16 = -1, int compute = 1}) =>
      throw NativeEngineException(
          7,
          'The RealScale native inference engine is not available on the '
          'web platform. Real AI upscaling requires the Android app.');

  ModelLoadReport loadModel(String paramPath, String binPath) =>
      throw NativeEngineException(7, 'engine unavailable on web');

  int estimatePeakMemory(int w, int h, int scale, int tileSize) => -1;

  @override
  Future<NativeRunResult> run(
    NativeJobRequest job, {
    ControlBlock? control,
    void Function(NativeProgress)? onProgress,
    Duration pollInterval = const Duration(milliseconds: 120),
  }) =>
      throw NativeEngineException(7, 'engine unavailable on web');

  void dispose() {}
}

abstract final class RsStatus {
  static const int ok = 0;
  static const int cancelled = 8;
  static const int unsupported = 7;
}

abstract final class RsStage {
  static const int queued = 0;
  static const int preprocess = 1;
  static const int inference = 2;
  static const int blend = 3;
  static const int postprocess = 4;
  static const int done = 5;
}
