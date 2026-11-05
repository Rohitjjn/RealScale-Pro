import 'dart:ffi';
import 'dart:typed_data';

import '../../services/model_catalog/model_catalog.dart' show ModelDef;
import '../engine_types.dart';
import '../tiling_engine.dart';
import 'ncnn_ffi.dart';

/// Installed model file layout inside the model directory:
///   `<modelDir>/<modelId>.bin`              (weights, shared across scales)
///   `<modelDir>/<modelId>-<scale>x.param`   (per native scale)
/// ModelManager installs files under these names so every engine resolves
/// them the same way.
String ncnnParamPath(String modelDir, String modelId, int scale) =>
    '$modelDir/$modelId-${scale}x.param';

String ncnnBinPath(String modelDir, String modelId) => '$modelDir/$modelId.bin';

/// NcnnEngine — wraps the reused native upscaler behind FFI
/// (architecture.md §5). One model loaded at a time; tiles processed via the
/// native Real-ESRGAN / Real-CUGAN graphs; CPU fallback when Vulkan is
/// unavailable. When the native library is missing the engine reports
/// unsupported and the registry picks the pure-Dart fallback — never a crash.
class NcnnEngine extends TilingEngine {
  NcnnEngine._(NcnnLibrary lib)
    : super(engineId: 'ncnn', tileProcessor: _NcnnTileProcessor(lib).call);

  static NcnnEngine? tryCreate() {
    final lib = NcnnLibrary.tryLoad();
    if (lib == null) return null;
    return NcnnEngine._(lib);
  }

  @override
  bool supports(ModelDef m, DeviceProfile d) {
    if (!super.supports(m, d)) return false;
    return m.engine == 'ncnn';
  }
}

/// Processes tiles through native ncnn, loading each model once.
final class _NcnnTileProcessor {
  _NcnnTileProcessor(this._lib);

  final NcnnLibrary _lib;
  Pointer<Void>? _handle;
  String? _loadedKey;

  Future<Uint8List> call(TileJob job) async {
    _handle ??= _lib.create();
    final h = _handle!;

    final paramPath = ncnnParamPath(job.modelDir, job.modelId, job.nativeScale);
    final binPath = ncnnBinPath(job.modelDir, job.modelId);
    final key = '$binPath#${job.threadCount}#${job.useGpu}';
    if (_loadedKey != key) {
      if (_loadedKey != null) {
        _lib.unloadModel(h);
      }
      final code = _lib.loadModel(
        h,
        paramPath,
        binPath,
        job.threadCount,
        job.useGpu,
      );
      if (code != NcnnLibrary.rsOk) {
        throw const EngineException('Model failed to load in native engine');
      }
      _loadedKey = key;
    }
    try {
      return _lib.processTile(job, h);
    } on NcnnOomException {
      rethrow; // RecoveryChain listens for OOM and halves tiles
    }
  }
}
