/// RealScale-Pro — dart:ffi bindings for the native engine C ABI
/// (native/include/realscale/realscale.h). This file is compiled only
/// on IO platforms (Android, Linux, macOS, Windows); see
/// [realscale_native.dart] for the conditional export.
library;

import 'dart:ffi';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

/// Opaque engine handle.
final class RsEngine extends Opaque {}

/// ABI structures mirrored from realscale.h (fixed layout).
final class RsEngineOptions extends Struct {
  @Int32()
  external int abiVersion;
  @Int32()
  external int compute;
  @Int32()
  external int threadCount;
  @Int32()
  external int useFp16;
  @Array.multi([8])
  external Array<Int32> reserved;
}

final class RsImage extends Struct {
  external Pointer<Uint8> pixels;
  @Int32()
  external int width;
  @Int32()
  external int height;
  @Int32()
  external int stride;
  @Int32()
  external int channels;
}

final class RsProgress extends Struct {
  @Int32()
  external int stage;
  @Int32()
  external int tilesDone;
  @Int32()
  external int tilesTotal;
  @Int32()
  external int attempt;
  @Int32()
  external int tileSize;
  @Double()
  external double fraction;
}

final class RsJobDesc extends Struct {
  @Int32()
  external int scale;
  @Int32()
  external int tileSize;
  @Int32()
  external int tileOverlap;
  @Int32()
  external int contextMargin;
  @Int32()
  external int maxRetries;
  @Array.multi([4])
  external Array<Int32> reserved;
}

final class RsModelInfo extends Struct {
  @Int32()
  external int scaleHint;
  @Int32()
  external int inputTileHint;
  @Int64()
  external int paramBytes;
  @Int64()
  external int binBytes;
  @Int32()
  external int loaded;
}

final class RsDeviceInfo extends Struct {
  @Int32()
  external int gpuAvailable;
  @Int32()
  external int usingGpu;
  @Int32()
  external int threadCount;
  @Int32()
  external int fp16Storage;
}

/// Progress callback C signature.
typedef RsProgressCB = Void Function(
    Pointer<Void> userData, Pointer<RsProgress> progress);

/// Native function signatures.
typedef RsAbiVersionNative = Int32 Function();
typedef RsAbiVersionDart = int Function();
typedef RsEngineCreateNative = Pointer<RsEngine> Function(
    Pointer<RsEngineOptions>);
typedef RsEngineCreateDart = Pointer<RsEngine> Function(
    Pointer<RsEngineOptions>);
typedef RsEngineDestroyNative = Void Function(Pointer<RsEngine>);
typedef RsEngineDestroyDart = void Function(Pointer<RsEngine>);
typedef RsEngineLoadModelNative = Int32 Function(Pointer<RsEngine>,
    Pointer<Utf8>, Pointer<Utf8>, Pointer<RsModelInfo>);
typedef RsEngineLoadModelDart = int Function(
    Pointer<RsEngine>, Pointer<Utf8>, Pointer<Utf8>, Pointer<RsModelInfo>);
typedef RsEngineModelInfoNative = Int32 Function(
    Pointer<RsEngine>, Pointer<RsModelInfo>);
typedef RsEngineModelInfoDart = int Function(
    Pointer<RsEngine>, Pointer<RsModelInfo>);
typedef RsEngineDeviceInfoNative = Int32 Function(
    Pointer<RsEngine>, Pointer<RsDeviceInfo>);
typedef RsEngineDeviceInfoDart = int Function(
    Pointer<RsEngine>, Pointer<RsDeviceInfo>);
typedef RsEstimatePeakMemoryNative = Int64 Function(
    Int32, Int32, Int32, Int32);
typedef RsEstimatePeakMemoryDart = int Function(int, int, int, int);
typedef RsRunNative = Int32 Function(
    Pointer<RsEngine>,
    Pointer<RsImage>,
    Pointer<RsJobDesc>,
    Pointer<Pointer<RsImage>>,
    Pointer<NativeFunction<RsProgressCB>>,
    Pointer<Void>,
    Pointer<Int32>);
typedef RsRunDart = int Function(
    Pointer<RsEngine>,
    Pointer<RsImage>,
    Pointer<RsJobDesc>,
    Pointer<Pointer<RsImage>>,
    Pointer<NativeFunction<RsProgressCB>>,
    Pointer<Void>,
    Pointer<Int32>);
typedef RsImageFreeNative = Void Function(Pointer<RsImage>);
typedef RsImageFreeDart = void Function(Pointer<RsImage>);
typedef RsLastErrorNative = Pointer<Utf8> Function();
typedef RsLastErrorDart = Pointer<Utf8> Function();

/// Status codes mirrored from the header.
abstract final class RsStatus {
  static const int ok = 0;
  static const int invalidArg = 1;
  static const int outOfMemory = 2;
  static const int engineInit = 3;
  static const int modelLoad = 4;
  static const int modelInvalid = 5;
  static const int inference = 6;
  static const int unsupported = 7;
  static const int cancelled = 8;
  static const int io = 9;
  static const int deviceLimit = 10;

  static String describe(int code) {
    switch (code) {
      case ok:
        return 'ok';
      case invalidArg:
        return 'invalid argument';
      case outOfMemory:
        return 'out of memory';
      case engineInit:
        return 'engine initialization failed';
      case modelLoad:
        return 'model load failed';
      case modelInvalid:
        return 'no valid model loaded';
      case inference:
        return 'inference failed';
      case unsupported:
        return 'unsupported operation';
      case cancelled:
        return 'cancelled';
      case io:
        return 'I/O error';
      case deviceLimit:
        return 'device limit exceeded';
      default:
        return 'unknown status $code';
    }
  }
}

/// Processing stages mirrored from the header.
abstract final class RsStage {
  static const int queued = 0;
  static const int preprocess = 1;
  static const int inference = 2;
  static const int blend = 3;
  static const int postprocess = 4;
  static const int done = 5;
}

/// Loaded native library bindings.
class RealscaleFfi {
  RealscaleFfi._(this.dylib);

  final DynamicLibrary dylib;

  static RealscaleFfi? _instance;

  /// Opens the shared library. On Android the library is bundled in the
  /// APK (android/app links native/CMakeLists.txt); on Linux it is
  /// built by `tools/build_native.sh` for tests.
  static RealscaleFfi open() {
    final existing = _instance;
    if (existing != null) {
      return existing;
    }
    final lib = switch (Abi.current()) {
      Abi.androidArm64 ||
      Abi.androidArm ||
      Abi.androidX64 =>
        DynamicLibrary.open('librealscale_native.so'),
      Abi.linuxX64 => _openLinux(),
      _ => throw UnsupportedError(
          'RealScale native engine does not support ${Abi.current()}'),
    };
    return _instance = RealscaleFfi._(lib);
  }

  static DynamicLibrary _openLinux() {
    final candidates = [
      // Test runs pass an explicit path via this env var.
      const String.fromEnvironment('RS_NATIVE_LIB'),
      'build/native/librealscale_native.so',
    ];
    Object? lastError;
    for (final path in candidates) {
      if (path.isEmpty) {
        continue;
      }
      try {
        return DynamicLibrary.open(path);
      } catch (e) {
        lastError = e;
      }
    }
    throw StateError(
        'librealscale_native.so not found (tried: $candidates). '
        'Run tools/build_native.sh first. Last error: $lastError');
  }

  late final int Function() abiVersion = dylib
      .lookupFunction<RsAbiVersionNative, RsAbiVersionDart>('rs_abi_version');

  late final Pointer<RsEngine> Function(Pointer<RsEngineOptions>) engineCreate =
      dylib.lookupFunction<RsEngineCreateNative, RsEngineCreateDart>(
          'rs_engine_create');

  late final void Function(Pointer<RsEngine>) engineDestroy = dylib
      .lookupFunction<RsEngineDestroyNative, RsEngineDestroyDart>(
          'rs_engine_destroy');

  late final RsEngineLoadModelDart engineLoadModel = dylib
      .lookupFunction<RsEngineLoadModelNative, RsEngineLoadModelDart>(
          'rs_engine_load_model');

  late final RsEngineModelInfoDart engineModelInfo = dylib
      .lookupFunction<RsEngineModelInfoNative, RsEngineModelInfoDart>(
          'rs_engine_model_info');

  late final RsEngineDeviceInfoDart engineDeviceInfo = dylib
      .lookupFunction<RsEngineDeviceInfoNative, RsEngineDeviceInfoDart>(
          'rs_engine_device_info');

  late final RsEstimatePeakMemoryDart estimatePeakMemory = dylib
      .lookupFunction<RsEstimatePeakMemoryNative, RsEstimatePeakMemoryDart>(
          'rs_estimate_peak_memory');

  late final RsRunDart run =
      dylib.lookupFunction<RsRunNative, RsRunDart>('rs_run');

  late final void Function(Pointer<RsImage>) imageFree = dylib
      .lookupFunction<RsImageFreeNative, RsImageFreeDart>('rs_image_free');

  late final Pointer<Utf8> Function() lastError = dylib
      .lookupFunction<RsLastErrorNative, RsLastErrorDart>('rs_last_error');

  /// Reads the thread-local native error message (empty when none).
  String errorMessage() {
    final p = lastError();
    if (p == nullptr) {
      return '';
    }
    return p.toDartString();
  }

  static const int expectedAbiVersion = 2;
}

// Keep the typed_data import: callers rely on Uint8List buffers.
typedef Uint8ListAlias = Uint8List;
