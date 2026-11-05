import 'dart:ffi';
import 'dart:io';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

import '../../core/app_logger.dart';
import '../engine_types.dart' show TileJob;

/// FFI bindings for the reused native upscaler (native/realscale_ncnn.cpp).
///
/// C API (kept intentionally tiny):
///   rs_create() -> handle
///   rs_load_model(h, paramPath, binPath, threads, useGpu) -> code
///   rs_process(h, inPixels, w, h, scale, outPixels, outLen) -> code
///   rs_cancel(h)
///   rs_unload_model(h)
///   rs_destroy(h)
///   rs_last_error() -> const char*
///
/// All buffers are owned by Dart; native never keeps pointers across calls.
class NcnnLibrary {
  NcnnLibrary._(this._lib);

  final DynamicLibrary _lib;

  static NcnnLibrary? _instance;
  static bool _loadFailed = false;

  /// Loads `librealscale_ncnn.so` / `realscale_ncnn.dll`. Returns null when
  /// the native library is absent (engine falls back gracefully — never
  /// crashes, rules.md golden rule 5).
  static NcnnLibrary? tryLoad() {
    if (_instance != null) return _instance;
    if (_loadFailed) return null;
    try {
      final DynamicLibrary lib;
      if (Platform.isAndroid || Platform.isLinux) {
        lib = DynamicLibrary.open('librealscale_ncnn.so');
      } else if (Platform.isWindows) {
        lib = DynamicLibrary.open('realscale_ncnn.dll');
      } else {
        // iOS/macOS arrive in Phase 4 with static linking.
        _loadFailed = true;
        return null;
      }
      _instance = NcnnLibrary._(lib);
      return _instance;
    } catch (e) {
      AppLogger.d('NcnnLibrary', 'native engine not available: $e');
      _loadFailed = true;
      return null;
    }
  }

  static const rsOk = 0;
  static const rsError = 1;
  static const rsCancelled = 2;
  static const rsOutOfMemory = 3;

  late final _create = _lib
      .lookupFunction<Pointer<Void> Function(), Pointer<Void> Function()>(
        'rs_create',
      );
  late final _destroy = _lib
      .lookupFunction<
        Void Function(Pointer<Void>),
        void Function(Pointer<Void>)
      >('rs_destroy');
  late final _loadModel = _lib
      .lookupFunction<
        Int32 Function(
          Pointer<Void>,
          Pointer<Utf8>,
          Pointer<Utf8>,
          Int32,
          Int32,
        ),
        int Function(Pointer<Void>, Pointer<Utf8>, Pointer<Utf8>, int, int)
      >('rs_load_model');
  late final _unloadModel = _lib
      .lookupFunction<
        Void Function(Pointer<Void>),
        void Function(Pointer<Void>)
      >('rs_unload_model');
  late final _process = _lib
      .lookupFunction<
        Int32 Function(
          Pointer<Void>,
          Pointer<Uint8>,
          Int32,
          Int32,
          Int32,
          Pointer<Uint8>,
          Int32,
        ),
        int Function(
          Pointer<Void>,
          Pointer<Uint8>,
          int,
          int,
          int,
          Pointer<Uint8>,
          int,
        )
      >('rs_process');
  late final _cancel = _lib
      .lookupFunction<
        Void Function(Pointer<Void>),
        void Function(Pointer<Void>)
      >('rs_cancel');
  late final _lastError = _lib
      .lookupFunction<Pointer<Utf8> Function(), Pointer<Utf8> Function()>(
        'rs_last_error',
      );

  Pointer<Void> create() => _create();

  void destroy(Pointer<Void> h) => _destroy(h);

  int loadModel(
    Pointer<Void> h,
    String paramPath,
    String binPath,
    int threads,
    bool gpu,
  ) => using((arena) {
    final p = paramPath.toNativeUtf8(allocator: arena).cast<Utf8>();
    final b = binPath.toNativeUtf8(allocator: arena).cast<Utf8>();
    return _loadModel(h, p, b, threads, gpu ? 1 : 0);
  });

  void unloadModel(Pointer<Void> h) => _unloadModel(h);

  /// Processes one RGBA tile by [scale]; output written into [out] which must
  /// be (w*scale * h*scale * 4) bytes. Returns an rs* code.
  int process(
    Pointer<Void> h,
    Uint8List pixels,
    int w,
    int height,
    int scale,
    Uint8List out,
  ) {
    final inPtr = malloc<Uint8>(pixels.length);
    final outPtr = malloc<Uint8>(out.length);
    try {
      inPtr.asTypedList(pixels.length).setAll(0, pixels);
      final code = _process(h, inPtr, w, height, scale, outPtr, out.length);
      if (code == rsOk) {
        out.setAll(0, outPtr.asTypedList(out.length));
      }
      return code;
    } finally {
      malloc.free(inPtr);
      malloc.free(outPtr);
    }
  }

  void cancel(Pointer<Void> h) => _cancel(h);

  String lastError() => _lastError().toDartString();

  /// Convenience: full tile job through native. Throws EngineFailureException
  /// on native errors; returns the upscaled RGBA pixels on success.
  Uint8List processTile(TileJob job, Pointer<Void> handle) {
    final out = Uint8List(
      job.width * job.nativeScale * job.height * job.nativeScale * 4,
    );
    final code = process(
      handle,
      job.pixels,
      job.width,
      job.height,
      job.nativeScale,
      out,
    );
    if (code == rsCancelled) {
      throw const NcnnCancelledException();
    }
    if (code == rsOutOfMemory) {
      throw const NcnnOomException();
    }
    if (code != rsOk) {
      throw NcnnErrorException(lastError());
    }
    return out;
  }
}

sealed class NcnnNativeException implements Exception {
  const NcnnNativeException();
}

final class NcnnCancelledException extends NcnnNativeException {
  const NcnnCancelledException();
}

final class NcnnOomException extends NcnnNativeException {
  const NcnnOomException();
}

final class NcnnErrorException extends NcnnNativeException {
  const NcnnErrorException(this.detail);
  final String detail;
}
