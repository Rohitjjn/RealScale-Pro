import 'dart:ffi';
import 'dart:io';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';

import '../../core/app_logger.dart';
import '../engine_types.dart' show EngineException;

/// FFI bindings for Microsoft ONNX Runtime (C API, ORT_API_VERSION 20,
/// verified against onnxruntime_c_api.h v1.20.1 — OrtApi has 285 members).
///
/// OrtApi is a struct of function pointers. We bind `OrtGetApiBase` (stable
/// two-field struct) and then index members by their documented ordinal —
/// the thin-binding approach; every ordinal below was extracted mechanically
/// from the official header and cross-checked (see reports/phase-3.md).
///
/// All buffers are owned by Dart; native never keeps pointers across calls.
/// When the shared library is absent the loader returns null and the engine
/// reports unsupported — never a crash (rules.md golden rule 5).
class OnnxRuntimeLibrary {
  OnnxRuntimeLibrary._(this._lib, this._api);

  final DynamicLibrary _lib;

  /// OrtApi* viewed as an array of function pointers.
  final Pointer<Pointer<Void>> _api;

  static OnnxRuntimeLibrary? _instance;
  static bool _loadFailed = false;

  /// ORT_API_VERSION the ordinals below are pinned to.
  static const int apiVersion = 20;

  /// OrtApi function ordinals (onnxruntime_c_api.h v1.20.1).
  static const _kGetErrorCode = 1;
  static const _kGetErrorMessage = 2;
  static const _kCreateEnv = 3;
  static const _kDisableTelemetryEvents = 6;
  static const _kCreateSession = 7;
  static const _kRun = 9;
  static const _kCreateSessionOptions = 10;
  static const _kSetSessionExecutionMode = 13;
  static const _kDisableMemPattern = 17;
  static const _kDisableCpuMemArena = 19;
  static const _kSetSessionGraphOptimizationLevel = 23;
  static const _kSetIntraOpNumThreads = 24;
  static const _kSessionGetInputCount = 30;
  static const _kSessionGetOutputCount = 31;
  static const _kSessionGetInputName = 36;
  static const _kSessionGetOutputName = 37;
  static const _kCreateTensorWithDataAsOrtValue = 49;
  static const _kGetTensorMutableData = 51;
  static const _kGetDimensionsCount = 61;
  static const _kGetDimensions = 62;
  static const _kGetTensorTypeAndShape = 65;
  static const _kCreateCpuMemoryInfo = 69;
  static const _kReleaseStatus = 93;
  static const _kReleaseEnv = 92;
  static const _kReleaseMemoryInfo = 94;
  static const _kReleaseSession = 95;
  static const _kReleaseValue = 96;
  static const _kReleaseSessionOptions = 100;
  static const _kReleaseTensorTypeAndShapeInfo = 99;

  /// OrtLoggingLevel: ORT_LOGGING_LEVEL_ERROR = 4 (quiet — we surface our
  /// own typed errors).
  static const int loggingLevelError = 4;

  /// GraphOptimizationLevel: ORT_ENABLE_ALL = 99.
  static const int graphOptEnableAll = 99;

  /// ONNXTensorElementDataType: ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT = 1.
  static const int tensorDataTypeFloat = 1;

  /// Env handle created lazily per process.
  Pointer<Void>? _env;

  /// Loads the ONNX Runtime shared library. Returns null when absent.
  /// [overridePath] (dev/tests): explicit library path, e.g. a pip-installed
  /// `libonnxruntime.so` on a Linux machine — keeps verification possible
  /// without touching production loading.
  static OnnxRuntimeLibrary? tryLoad({String? overridePath}) {
    if (_instance != null) return _instance;
    if (_loadFailed) return null;
    try {
      final DynamicLibrary lib;
      if (overridePath != null) {
        lib = DynamicLibrary.open(overridePath);
      } else if (Platform.isAndroid || Platform.isLinux) {
        lib = DynamicLibrary.open('libonnxruntime.so');
      } else if (Platform.isWindows) {
        lib = DynamicLibrary.open('onnxruntime.dll');
      } else {
        // iOS/macOS arrive in Phase 4 (static linking + frameworks).
        _loadFailed = true;
        return null;
      }
      final base = lib
          .lookupFunction<
            Pointer<Pointer<Void>> Function(),
            Pointer<Pointer<Void>> Function()
          >('OrtGetApiBase')();
      if (base == nullptr) {
        _loadFailed = true;
        return null;
      }
      // OrtApiBase = { GetApi(uint32) -> const OrtApi*; GetVersionString }.
      final getApiPtr = base
          .cast<Pointer<NativeFunction<Pointer<Void> Function(Uint32)>>>()
          .value;
      final getApi = getApiPtr.asFunction<Pointer<Void> Function(int)>();
      final api = getApi(apiVersion).cast<Pointer<Void>>();
      if (api == nullptr) {
        _loadFailed = true;
        return null;
      }
      _instance = OnnxRuntimeLibrary._(lib, api);
      AppLogger.d('OnnxRuntime', 'runtime ${_instance!.versionString()} ready');
      return _instance;
    } catch (e) {
      AppLogger.d('OnnxRuntime', 'runtime not available: $e');
      _loadFailed = true;
      return null;
    }
  }

  // --- OrtApi member access ---------------------------------------------------

  Pointer<T> _fn<T extends NativeType>(int ordinal) => _api[ordinal].cast<T>();

  late final _createEnvFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(
                Int32,
                Pointer<Utf8>,
                Pointer<Pointer<Void>>,
              )
            >
          >(_kCreateEnv)
          .asFunction<
            Pointer<Void> Function(int, Pointer<Utf8>, Pointer<Pointer<Void>>)
          >();

  late final _createSessionOptionsFn =
      _fn<NativeFunction<Pointer<Void> Function(Pointer<Pointer<Void>>)>>(
        _kCreateSessionOptions,
      ).asFunction<Pointer<Void> Function(Pointer<Pointer<Void>>)>();

  late final _setIntraOpNumThreadsFn =
      _fn<NativeFunction<Pointer<Void> Function(Pointer<Void>, Int32)>>(
        _kSetIntraOpNumThreads,
      ).asFunction<Pointer<Void> Function(Pointer<Void>, int)>();

  late final _setGraphOptimizationFn =
      _fn<NativeFunction<Pointer<Void> Function(Pointer<Void>, Int32)>>(
        _kSetSessionGraphOptimizationLevel,
      ).asFunction<Pointer<Void> Function(Pointer<Void>, int)>();

  late final _setExecutionModeFn =
      _fn<NativeFunction<Pointer<Void> Function(Pointer<Void>, Int32)>>(
        _kSetSessionExecutionMode,
      ).asFunction<Pointer<Void> Function(Pointer<Void>, int)>();

  late final _disableMemPatternFn =
      _fn<NativeFunction<Pointer<Void> Function(Pointer<Void>)>>(
        _kDisableMemPattern,
      ).asFunction<Pointer<Void> Function(Pointer<Void>)>();

  late final _disableCpuMemArenaFn =
      _fn<NativeFunction<Pointer<Void> Function(Pointer<Void>)>>(
        _kDisableCpuMemArena,
      ).asFunction<Pointer<Void> Function(Pointer<Void>)>();

  late final _disableTelemetryFn =
      _fn<NativeFunction<Pointer<Void> Function(Pointer<Void>)>>(
        _kDisableTelemetryEvents,
      ).asFunction<Pointer<Void> Function(Pointer<Void>)>();

  /// CreateSession(env, ORTCHAR_T* modelPath, options, OrtSession**).
  late final _createSessionUtf8Fn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(
                Pointer<Void>,
                Pointer<Utf8>,
                Pointer<Void>,
                Pointer<Pointer<Void>>,
              )
            >
          >(_kCreateSession)
          .asFunction<
            Pointer<Void> Function(
              Pointer<Void>,
              Pointer<Utf8>,
              Pointer<Void>,
              Pointer<Pointer<Void>>,
            )
          >();

  late final _createSessionUtf16Fn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(
                Pointer<Void>,
                Pointer<Uint16>,
                Pointer<Void>,
                Pointer<Pointer<Void>>,
              )
            >
          >(_kCreateSession)
          .asFunction<
            Pointer<Void> Function(
              Pointer<Void>,
              Pointer<Uint16>,
              Pointer<Void>,
              Pointer<Pointer<Void>>,
            )
          >();

  late final _runFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(
                Pointer<Void>, // session
                Pointer<Void>, // run options (null)
                Pointer<Pointer<Utf8>>, // input names
                Pointer<Pointer<Void>>, // input values
                IntPtr, // input count
                Pointer<Pointer<Utf8>>, // output names
                Pointer<Pointer<Void>>, // output values
                IntPtr, // output count
              )
            >
          >(_kRun)
          .asFunction<
            Pointer<Void> Function(
              Pointer<Void>,
              Pointer<Void>,
              Pointer<Pointer<Utf8>>,
              Pointer<Pointer<Void>>,
              int,
              Pointer<Pointer<Utf8>>,
              Pointer<Pointer<Void>>,
              int,
            )
          >();

  late final _createMemoryInfoFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(
                Int32, // allocator type
                Int32, // mem type
                Pointer<Pointer<Void>>,
              )
            >
          >(_kCreateCpuMemoryInfo)
          .asFunction<
            Pointer<Void> Function(int, int, Pointer<Pointer<Void>>)
          >();

  late final _createTensorWithDataFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(
                Pointer<Void>, // memory info
                Pointer<Void>, // data
                IntPtr, // data length in bytes
                Pointer<Int64>, // shape
                IntPtr, // shape len
                Int32, // element type
                Pointer<Pointer<Void>>, // out
              )
            >
          >(_kCreateTensorWithDataAsOrtValue)
          .asFunction<
            Pointer<Void> Function(
              Pointer<Void>,
              Pointer<Void>,
              int,
              Pointer<Int64>,
              int,
              int,
              Pointer<Pointer<Void>>,
            )
          >();

  late final _getTensorMutableDataFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(Pointer<Void>, Pointer<Pointer<Void>>)
            >
          >(_kGetTensorMutableData)
          .asFunction<
            Pointer<Void> Function(Pointer<Void>, Pointer<Pointer<Void>>)
          >();

  late final _getTensorTypeAndShapeFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(Pointer<Void>, Pointer<Pointer<Void>>)
            >
          >(_kGetTensorTypeAndShape)
          .asFunction<
            Pointer<Void> Function(Pointer<Void>, Pointer<Pointer<Void>>)
          >();

  late final _getDimensionsCountFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(Pointer<Void>, Pointer<UintPtr>)
            >
          >(_kGetDimensionsCount)
          .asFunction<
            Pointer<Void> Function(Pointer<Void>, Pointer<UintPtr>)
          >();

  late final _getDimensionsFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(Pointer<Void>, Pointer<Int64>, UintPtr)
            >
          >(_kGetDimensions)
          .asFunction<
            Pointer<Void> Function(Pointer<Void>, Pointer<Int64>, int)
          >();

  late final _getSessionInputCountFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(Pointer<Void>, Pointer<UintPtr>)
            >
          >(_kSessionGetInputCount)
          .asFunction<
            Pointer<Void> Function(Pointer<Void>, Pointer<UintPtr>)
          >();

  late final _getSessionOutputCountFn =
      _fn<
            NativeFunction<
              Pointer<Void> Function(Pointer<Void>, Pointer<UintPtr>)
            >
          >(_kSessionGetOutputCount)
          .asFunction<
            Pointer<Void> Function(Pointer<Void>, Pointer<UintPtr>)
          >();

  late final _getSessionInputNameFn =
      _fn<NativeFunction<Pointer<Utf8> Function(Pointer<Void>, UintPtr)>>(
        _kSessionGetInputName,
      ).asFunction<Pointer<Utf8> Function(Pointer<Void>, int)>();

  late final _getSessionOutputNameFn =
      _fn<NativeFunction<Pointer<Utf8> Function(Pointer<Void>, UintPtr)>>(
        _kSessionGetOutputName,
      ).asFunction<Pointer<Utf8> Function(Pointer<Void>, int)>();

  // Release functions.
  late final _releaseStatusFn =
      _fn<NativeFunction<Void Function(Pointer<Void>)>>(
        _kReleaseStatus,
      ).asFunction<void Function(Pointer<Void>)>();
  late final _releaseMemoryInfoFn =
      _fn<NativeFunction<Void Function(Pointer<Void>)>>(
        _kReleaseMemoryInfo,
      ).asFunction<void Function(Pointer<Void>)>();
  late final _releaseSessionFn =
      _fn<NativeFunction<Void Function(Pointer<Void>)>>(
        _kReleaseSession,
      ).asFunction<void Function(Pointer<Void>)>();
  late final _releaseValueFn =
      _fn<NativeFunction<Void Function(Pointer<Void>)>>(
        _kReleaseValue,
      ).asFunction<void Function(Pointer<Void>)>();
  late final _releaseEnvFn = _fn<NativeFunction<Void Function(Pointer<Void>)>>(
    _kReleaseEnv,
  ).asFunction<void Function(Pointer<Void>)>();
  late final _releaseSessionOptionsFn =
      _fn<NativeFunction<Void Function(Pointer<Void>)>>(
        _kReleaseSessionOptions,
      ).asFunction<void Function(Pointer<Void>)>();
  late final _releaseTensorShapeFn =
      _fn<NativeFunction<Void Function(Pointer<Void>)>>(
        _kReleaseTensorTypeAndShapeInfo,
      ).asFunction<void Function(Pointer<Void>)>();

  // --- typed helpers -----------------------------------------------------------

  /// OrtApiBase::GetVersionString — a plain C symbol, not an OrtApi member.
  String versionString() {
    final getVer = _lib
        .lookupFunction<Pointer<Utf8> Function(), Pointer<Utf8> Function()>(
          'OrtGetVersionString',
        )();
    return getVer.toDartString();
  }

  /// Runs [body] and checks the OrtStatus* it returns; throws a typed
  /// [EngineException] carrying ORT's own error text when non-null.
  void _checked(String op, Pointer<Void> Function() body) {
    final status = body();
    if (status == nullptr) return;
    final code = _fn<NativeFunction<Int32 Function(Pointer<Void>)>>(
      _kGetErrorCode,
    ).asFunction<int Function(Pointer<Void>)>()(status);
    final msg = _fn<NativeFunction<Pointer<Utf8> Function(Pointer<Void>)>>(
      _kGetErrorMessage,
    ).asFunction<Pointer<Utf8> Function(Pointer<Void>)>()(status);
    final text = msg == nullptr ? '' : msg.toDartString();
    _releaseStatusFn(status);
    throw EngineException('ONNX $op failed (code $code): $text');
  }

  Pointer<Void> _envHandle() {
    if (_env != null) return _env!;
    final logId = 'realscale_pro'.toNativeUtf8();
    try {
      final env = calloc<Pointer<Void>>();
      _checked('CreateEnv', () => _createEnvFn(loggingLevelError, logId, env));
      _env = env.value;
      // Privacy: telemetry stays off (no data ever leaves the device).
      _disableTelemetryFn(_env!);
      return _env!;
    } finally {
      calloc.free(logId);
    }
  }

  /// Creates a session for [modelPath] with the given intra-op thread count.
  /// Windows uses UTF-16 paths, other platforms UTF-8 (ORTCHAR_T rule).
  OnnxSession createSession(String modelPath, {int threads = 4}) {
    final env = _envHandle();
    final options = calloc<Pointer<Void>>();
    final session = calloc<Pointer<Void>>();
    try {
      _checked('CreateSessionOptions', () => _createSessionOptionsFn(options));
      _checked(
        'SetIntraOpNumThreads',
        () => _setIntraOpNumThreadsFn(options.value, threads),
      );
      _checked(
        'SetSessionGraphOptimizationLevel',
        () => _setGraphOptimizationFn(options.value, graphOptEnableAll),
      );
      _checked(
        'SetSessionExecutionMode',
        () => _setExecutionModeFn(options.value, 0), // ORT_SEQUENTIAL
      );
      _checked('DisableMemPattern', () => _disableMemPatternFn(options.value));
      _checked(
        'DisableCpuMemArena',
        () => _disableCpuMemArenaFn(options.value),
      );
      if (Platform.isWindows) {
        final pathUnits = modelPath.codeUnits;
        final p = calloc<Uint16>(pathUnits.length + 1);
        p.asTypedList(pathUnits.length + 1)
          ..setRange(0, pathUnits.length, pathUnits)
          ..[pathUnits.length] = 0;
        try {
          _checked(
            'CreateSession',
            () => _createSessionUtf16Fn(env, p, options.value, session),
          );
        } finally {
          calloc.free(p);
        }
      } else {
        final p = modelPath.toNativeUtf8();
        try {
          _checked(
            'CreateSession',
            () => _createSessionUtf8Fn(env, p, options.value, session),
          );
        } finally {
          calloc.free(p);
        }
      }
      return OnnxSession._(this, session.value);
    } catch (_) {
      if (session.value != nullptr) _releaseSessionFn(session.value);
      if (options.value != nullptr) _releaseSessionOptionsFn(options.value);
      rethrow;
    } finally {
      calloc.free(options);
      calloc.free(session);
    }
  }

  /// Runs one float32 NCHW tensor through [session]; returns the output
  /// tensor data. Single input / single output models only (our SR models).
  Float32List run1To1(
    Pointer<Void> session, {
    required Float32List input,
    required List<int> shape,
  }) {
    final info = calloc<Pointer<Void>>();
    final inValue = calloc<Pointer<Void>>();
    final outValue = calloc<Pointer<Void>>();
    final inputNames = calloc<Pointer<Utf8>>();
    final outputNames = calloc<Pointer<Utf8>>();
    final inputValues = calloc<Pointer<Void>>();
    final outputValues = calloc<Pointer<Void>>();
    final shapePtr = calloc<Int64>(shape.length);
    final inCount = calloc<UintPtr>();
    final outCount = calloc<UintPtr>();
    try {
      _checked(
        'CreateCpuMemoryInfo',
        () => _createMemoryInfoFn(1, 0, info), // OrtArenaAllocator, default
      );
      final data = calloc<Float>(input.length);
      data.asTypedList(input.length).setAll(0, input);
      shapePtr.asTypedList(shape.length).setAll(0, shape);
      _checked(
        'CreateTensorWithDataAsOrtValue',
        () => _createTensorWithDataFn(
          info.value,
          data.cast<Void>(),
          input.length * 4,
          shapePtr,
          shape.length,
          tensorDataTypeFloat,
          inValue,
        ),
      );
      _checked(
        'SessionGetInputCount',
        () => _getSessionInputCountFn(session, inCount),
      );
      _checked(
        'SessionGetOutputCount',
        () => _getSessionOutputCountFn(session, outCount),
      );
      final inName = _getSessionInputNameFn(session, 0);
      final outName = _getSessionOutputNameFn(session, 0);
      inputNames[0] = inName;
      outputNames[0] = outName;
      inputValues[0] = inValue.value;
      outputValues[0] = nullptr;
      _checked(
        'Run',
        () => _runFn(
          session,
          nullptr,
          inputNames,
          inputValues,
          inCount.value,
          outputNames,
          outputValues,
          outCount.value,
        ),
      );
      // Read the output dims (models may differ from the nominal scale).
      final shapeInfo = calloc<Pointer<Void>>();
      final dimCount = calloc<UintPtr>();
      Pointer<Int64>? dimsPtr;
      try {
        _checked(
          'GetTensorTypeAndShape',
          () => _getTensorTypeAndShapeFn(outputValues[0], shapeInfo),
        );
        _checked(
          'GetDimensionsCount',
          () => _getDimensionsCountFn(shapeInfo.value, dimCount),
        );
        dimsPtr = calloc<Int64>(dimCount.value);
        _checked(
          'GetDimensions',
          () => _getDimensionsFn(shapeInfo.value, dimsPtr!, dimCount.value),
        );
        var outLen = 1;
        for (var i = 0; i < dimCount.value; i++) {
          outLen *= dimsPtr[i];
        }
        final outData = calloc<Pointer<Void>>();
        _checked(
          'GetTensorMutableData',
          () => _getTensorMutableDataFn(outputValues[0], outData),
        );
        final result = Float32List(outLen)
          ..setAll(0, outData.cast<Float>().asTypedList(outLen));
        calloc.free(outData);
        return result;
      } finally {
        if (shapeInfo.value != nullptr) _releaseTensorShapeFn(shapeInfo.value);
        if (dimsPtr != null) calloc.free(dimsPtr);
        calloc.free(shapeInfo);
        calloc.free(dimCount);
        calloc.free(data);
      }
    } finally {
      if (inValue.value != nullptr) _releaseValueFn(inValue.value);
      if (outValue.value != nullptr) _releaseValueFn(outValue.value);
      if (info.value != nullptr) _releaseMemoryInfoFn(info.value);
      calloc.free(info);
      calloc.free(inValue);
      calloc.free(outValue);
      calloc.free(inputNames);
      calloc.free(outputNames);
      calloc.free(inputValues);
      calloc.free(outputValues);
      calloc.free(shapePtr);
      calloc.free(inCount);
      calloc.free(outCount);
    }
  }

  /// Probe used by OnnxEngine.supports and tests: the runtime is usable when
  /// a session options object can be created and released.
  bool probeUsable() {
    final options = calloc<Pointer<Void>>();
    try {
      _checked('CreateSessionOptions', () => _createSessionOptionsFn(options));
      return options.value != nullptr;
    } catch (_) {
      return false;
    } finally {
      if (options.value != nullptr) _releaseSessionOptionsFn(options.value);
      calloc.free(options);
    }
  }

  void disposeEnv() {
    if (_env != null) {
      _releaseEnvFn(_env!);
      _env = null;
    }
  }
}

/// One loaded ONNX model. Owns the native session; tiles run through it.
class OnnxSession {
  OnnxSession._(this._lib, this.session);

  final OnnxRuntimeLibrary _lib;
  final Pointer<Void> session;

  Float32List run(Float32List input, List<int> shape) =>
      _lib.run1To1(session, input: input, shape: shape);

  void dispose() => _lib._releaseSessionFn(session);
}

/// An EngineException subclass so the recovery chain and JobManager treat
/// ONNX failures like every other engine failure (typed, friendly).
base class OnnxException extends EngineException {
  const OnnxException(super.message);
}
