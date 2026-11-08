/// RealScale-Pro — IO-platform native engine facade.
///
/// Execution model (see docs/native-engine-design.md):
///  * A native "control block" (calloc'd) is shared between the main
///    isolate and one background worker isolate:
///      [0] cancel flag       (Int32, written by UI isolate)
///      [1] stage             (Int32, written by worker)
///      [2] tilesDone         (Int32, written by worker)
///      [3] tilesTotal        (Int32, written by worker)
///      [4] attempt           (Int32, written by worker)
///      [5] tileSize          (Int32, written by worker)
///      [6] fraction x1000    (Int32, written by worker)
///  * The worker isolate opens its own engine + model and calls the
///    blocking `rs_run` FFI function; the C progress callback writes
///    the control block (same process memory) and the engine polls the
///    cancel flag between tiles.
///  * The main isolate polls the control block on a timer purely as
///    transport; every displayed value originates from a real native
///    processing event, never from a fabricated timer.
///  * The final image/status leaves the worker via SendPort
///    (TransferableTypedData) — never through the control block, so a
///    partial image can never be mistaken for a result.
library;

import 'dart:async';
import 'dart:ffi';

import 'package:ffi/ffi.dart';
import 'dart:isolate';
import 'dart:typed_data';

import 'realscale_ffi.dart' as ffi;

export 'realscale_ffi.dart' show RsStatus, RsStage;

/// Exception carrying a native status code and message.
class NativeEngineException implements Exception {
  NativeEngineException(this.statusCode, this.message);
  final int statusCode;
  final String message;

  bool get isCancellation => statusCode == ffi.RsStatus.cancelled;

  @override
  String toString() =>
      'NativeEngineException(${ffi.RsStatus.describe(statusCode)}): '
      '$message';
}

/// Progress snapshot read from the control block.
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

/// Backend capability snapshot.
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

/// Result of a successful native run.
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

/// Model load outcome from the primary engine.
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
  bool get ok => statusCode == ffi.RsStatus.ok;
}

/// Job parameters for a native run.
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

// ------------------------------------------------------------------ //
// Shared control block + static C callback (single process-wide
// trampoline; safe because the callback only writes native memory).
// ------------------------------------------------------------------ //

Pointer<NativeFunction<ffi.RsProgressCB>>? _callbackPtr;

void _progressTrampoline(
    Pointer<Void> userData, Pointer<ffi.RsProgress> p) {
  final block = Pointer<Int32>.fromAddress(userData.address);
  block[1] = p.ref.stage;
  block[2] = p.ref.tilesDone;
  block[3] = p.ref.tilesTotal;
  block[4] = p.ref.attempt;
  block[5] = p.ref.tileSize;
  block[6] = (p.ref.fraction.clamp(0.0, 1.0) * 1000).round();
}

Pointer<NativeFunction<ffi.RsProgressCB>> _getCallback() =>
    _callbackPtr ??= Pointer.fromFunction(_progressTrampoline);

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

/// Shared native control block for one run.
class ControlBlock {
  ControlBlock() : ptr = calloc<Int32>(7);

  final Pointer<Int32> ptr;

  void requestCancel() => ptr[0] = 1;
  bool get cancelRequested => ptr[0] != 0;

  NativeProgress read() => NativeProgress(
        stage: ptr[1],
        tilesDone: ptr[2],
        tilesTotal: ptr[3],
        attempt: ptr[4],
        tileSize: ptr[5],
        fraction: ptr[6] / 1000.0,
      );

  void dispose() => calloc.free(ptr);
}

// ------------------------------------------------------------------ //
// High-level engine facade.
// ------------------------------------------------------------------ //

class NativeEngine implements EngineRunner {
  NativeEngine._(this._ffi, this._handle, this._deviceInfo);

  final ffi.RealscaleFfi _ffi;
  final Pointer<ffi.RsEngine> _handle;
  final NativeDeviceInfo _deviceInfo;

  /// Creates a primary engine.
  /// [compute]: 0 AUTO (CPU — the verified default), 1 CPU, 2 GPU
  /// (experimental; requires a Vulkan-capable build + device, falls
  /// back to CPU automatically).
  static NativeEngine create({int threads = 0, int useFp16 = -1, int compute = 1}) {
    final f = ffi.RealscaleFfi.open();
    if (f.abiVersion() != ffi.RealscaleFfi.expectedAbiVersion) {
      throw NativeEngineException(
          ffi.RsStatus.engineInit,
          'ABI version mismatch: app expects '
          '${ffi.RealscaleFfi.expectedAbiVersion}, library has '
          '${f.abiVersion()}');
    }
    final opts = calloc<ffi.RsEngineOptions>()
      ..ref.abiVersion = ffi.RealscaleFfi.expectedAbiVersion
      ..ref.compute = compute
      ..ref.threadCount = threads
      ..ref.useFp16 = useFp16;
    final handle = f.engineCreate(opts);
    calloc.free(opts);
    if (handle == nullptr) {
      throw NativeEngineException(ffi.RsStatus.engineInit, f.errorMessage());
    }
    final di = calloc<ffi.RsDeviceInfo>();
    f.engineDeviceInfo(handle, di);
    final info = NativeDeviceInfo(
      gpuAvailable: di.ref.gpuAvailable,
      usingGpu: di.ref.usingGpu,
      threadCount: di.ref.threadCount,
      fp16Storage: di.ref.fp16Storage,
    );
    calloc.free(di);
    return NativeEngine._(f, handle, info);
  }

  NativeDeviceInfo get deviceInfo => _deviceInfo;

  /// Loads a model into the primary engine (capability checks only;
  /// the worker loads its own copy for runs).
  ModelLoadReport loadModel(String paramPath, String binPath) {
    final p = paramPath.toNativeUtf8();
    final b = binPath.toNativeUtf8();
    final info = calloc<ffi.RsModelInfo>();
    final st = _ffi.engineLoadModel(_handle, p, b, info);
    final report = ModelLoadReport(
      statusCode: st,
      message: st == ffi.RsStatus.ok ? '' : _ffi.errorMessage(),
      paramBytes: info.ref.paramBytes,
      binBytes: info.ref.binBytes,
      tileHint: info.ref.inputTileHint,
    );
    calloc.free(info);
    calloc.free(p);
    calloc.free(b);
    return report;
  }

  /// Peak-memory estimate (bytes) matching the native formula.
  int estimatePeakMemory(int w, int h, int scale, int tileSize) =>
      _ffi.estimatePeakMemory(w, h, scale, tileSize);

  /// Runs a job in a background isolate. [onProgress] is called from
  /// the main isolate with real native progress values (polled at
  /// [pollInterval]). The returned future completes with the final
  /// image or an error; it never resolves on a partial image.
  @override
  Future<NativeRunResult> run(
    NativeJobRequest job, {
    ControlBlock? control,
    void Function(NativeProgress)? onProgress,
    Duration pollInterval = const Duration(milliseconds: 120),
  }) async {
    // Caller-owned control block enables cancellation from the main
    // isolate while the worker is blocked inside rs_run.
    final block = control ?? ControlBlock();
    final ownsBlock = control == null;
    final response = ReceivePort();
    final errorPort = ReceivePort();
    await Isolate.spawn(
      _workerEntry,
      _WorkerArgs(
        sendPort: response.sendPort,
        job: NativeJobRequest(
          paramPath: job.paramPath,
          binPath: job.binPath,
          rgba: job.rgba,
          width: job.width,
          height: job.height,
          scale: job.scale,
          tileSize: job.tileSize,
          tileOverlap: job.tileOverlap,
          contextMargin: job.contextMargin,
          threads: job.threads,
          controlBlockAddress: block.ptr.address,
          compute: job.compute,
        ),
      ),
      onError: errorPort.sendPort,
      errorsAreFatal: true,
    );

    final timer = Timer.periodic(pollInterval, (_) {
      onProgress?.call(block.read());
    });

    try {
      final msg = await response.first;
      timer.cancel();
      final map = msg as Map<String, Object?>;
      if (map['ok'] == true) {
        final data = map['rgba'] as TransferableTypedData;
        return NativeRunResult(
          rgba: data.materialize().asUint8List(),
          width: map['width']! as int,
          height: map['height']! as int,
          attempt: map['attempt']! as int,
        );
      } else {
        throw NativeEngineException(
          map['status']! as int,
          map['message']! as String,
        );
      }
    } finally {
      timer.cancel();
      response.close();
      errorPort.close();
      if (ownsBlock) {
        block.dispose();
      }
    }
  }

  void dispose() {
    _ffi.engineDestroy(_handle);
  }
}

class _WorkerArgs {
  const _WorkerArgs({required this.sendPort, required this.job});
  final SendPort sendPort;
  final NativeJobRequest job;
}

/// Worker isolate entry: opens its own engine, runs rs_run with the
/// shared control block for progress/cancel, returns the result.
Future<void> _workerEntry(_WorkerArgs args) async {
  final job = args.job;
  try {
    final f = ffi.RealscaleFfi.open();
    final opts = calloc<ffi.RsEngineOptions>()
      ..ref.abiVersion = ffi.RealscaleFfi.expectedAbiVersion
      ..ref.compute = job.compute
      ..ref.threadCount = job.threads
      ..ref.useFp16 = -1;
    final engine = f.engineCreate(opts);
    calloc.free(opts);
    if (engine == nullptr) {
      args.sendPort.send({
        'ok': false,
        'status': ffi.RsStatus.engineInit,
        'message': f.errorMessage(),
      });
      return;
    }
    final p = job.paramPath.toNativeUtf8();
    final b = job.binPath.toNativeUtf8();
    final st = f.engineLoadModel(engine, p, b, nullptr);
    calloc.free(p);
    calloc.free(b);
    if (st != ffi.RsStatus.ok) {
      final message = f.errorMessage();
      f.engineDestroy(engine);
      args.sendPort
          .send({'ok': false, 'status': st, 'message': message});
      return;
    }

    // Copy the pixel buffer into native memory owned by this run.
    final inPixels = calloc<Uint8>(job.rgba.length);
    inPixels.asTypedList(job.rgba.length).setAll(0, job.rgba);
    final inImage = calloc<ffi.RsImage>()
      ..ref.pixels = inPixels
      ..ref.width = job.width
      ..ref.height = job.height
      ..ref.stride = job.width * 4
      ..ref.channels = 4;

    final desc = calloc<ffi.RsJobDesc>()
      ..ref.scale = job.scale
      ..ref.tileSize = job.tileSize
      ..ref.tileOverlap = job.tileOverlap
      ..ref.contextMargin = job.contextMargin
      ..ref.maxRetries = 3;

    final outPtr = calloc<Pointer<ffi.RsImage>>();
    final block = Pointer<Int32>.fromAddress(job.controlBlockAddress);

    final runStatus = f.run(
      engine,
      inImage,
      desc,
      outPtr,
      _getCallback(),
      block.cast<Void>(),
      block, // cancel flag == control block start
    );

    if (runStatus == ffi.RsStatus.ok && outPtr.value != nullptr) {
      final out = outPtr.value.ref;
      final n = out.width * out.height * 4;
      // Copy out of native memory before freeing.
      final bytes = Uint8List.fromList(out.pixels.asTypedList(n));
      final outW = out.width;
      final outH = out.height;
      final attempt = block[4] == 0 ? 1 : block[4];
      f.imageFree(outPtr.value);
      calloc.free(outPtr);
      calloc.free(desc);
      calloc.free(inImage);
      calloc.free(inPixels);
      f.engineDestroy(engine);
      args.sendPort.send({
        'ok': true,
        'rgba': TransferableTypedData.fromList([bytes]),
        'width': outW,
        'height': outH,
        'attempt': attempt,
      });
    } else {
      final message = f.errorMessage();
      if (outPtr.value != nullptr) {
        f.imageFree(outPtr.value);
      }
      calloc.free(outPtr);
      calloc.free(desc);
      calloc.free(inImage);
      calloc.free(inPixels);
      f.engineDestroy(engine);
      args.sendPort
          .send({'ok': false, 'status': runStatus, 'message': message});
    }
  } catch (e) {
    args.sendPort.send({
      'ok': false,
      'status': ffi.RsStatus.engineInit,
      'message': 'worker exception: $e',
    });
  }
}
