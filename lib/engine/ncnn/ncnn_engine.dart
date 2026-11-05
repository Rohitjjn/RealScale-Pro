import 'dart:async';
import 'dart:ffi';
import 'dart:isolate';
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
/// (architecture.md §5). One model loaded at a time on a persistent worker
/// isolate so the UI thread never blocks (rules.md §3); tiles processed via
/// the native Real-ESRGAN / Real-CUGAN / waifu2x graphs; CPU fallback when
/// Vulkan is unavailable. When the native library is missing the engine
/// reports unsupported and the registry picks the pure-Dart fallback.
class NcnnEngine extends TilingEngine {
  NcnnEngine._(this._worker, NcnnLibrary lib)
    : _lib = lib,
      super(engineId: 'ncnn', tileProcessor: _worker.process);

  final _NcnnIsolateWorker _worker;
  final NcnnLibrary _lib;

  static NcnnEngine? tryCreate() {
    final lib = NcnnLibrary.tryLoad();
    if (lib == null) return null;
    final worker = _NcnnIsolateWorker();
    return NcnnEngine._(worker, lib);
  }

  @override
  bool supports(ModelDef m, DeviceProfile d) {
    if (!super.supports(m, d)) return false;
    return m.engine == 'ncnn';
  }

  @override
  Future<void> cancel(String jobId) async {
    await super.cancel(jobId);
    final addr = _worker.sessionAddress;
    if (addr != 0) {
      _lib.cancel(Pointer<Void>.fromAddress(addr));
    }
  }

  @override
  Future<void> dispose() async {
    await super.dispose();
    await _worker.dispose();
  }
}

/// Persistent background isolate keeping a single native RsSession warm across
/// all tiles of a job so weights are loaded once and UI stays at 60 fps.
final class _NcnnIsolateWorker {
  Isolate? _isolate;
  SendPort? _commandPort;
  ReceivePort? _handshakePort;
  Future<void>? _starting;
  int sessionAddress = 0;

  Future<void> _ensureStarted() async {
    if (_commandPort != null) return;
    if (_starting != null) {
      await _starting;
      return;
    }
    final completer = Completer<void>();
    _starting = completer.future;
    try {
      final handshake = ReceivePort();
      _handshakePort = handshake;
      _isolate = await Isolate.spawn(
        _ncnnWorkerEntry,
        handshake.sendPort,
        debugName: 'realscale-ncnn-worker',
      );
      final first = await handshake.first as List<Object?>;
      _commandPort = first[0] as SendPort;
      sessionAddress = first[1] as int;
      handshake.close();
      _handshakePort = null;
      completer.complete();
    } catch (e, st) {
      _starting = null;
      completer.completeError(e, st);
      rethrow;
    }
  }

  Future<Uint8List> process(TileJob job) async {
    await _ensureStarted();
    final port = _commandPort;
    if (port == null) {
      throw const EngineException('Native worker isolate unavailable');
    }
    final reply = ReceivePort();
    port.send(<Object?>[
      reply.sendPort,
      job.modelId,
      job.nativeScale,
      job.threadCount,
      job.useGpu,
      job.width,
      job.height,
      TransferableTypedData.fromList(<Uint8List>[job.pixels]),
      job.modelDir,
    ]);
    final msg = await reply.first as List<Object?>;
    reply.close();
    final kind = msg[0] as String;
    switch (kind) {
      case 'ok':
        final ttd = msg[1] as TransferableTypedData;
        return ttd.materialize().asUint8List();
      case 'cancelled':
        throw const NcnnCancelledException();
      case 'oom':
        throw const NcnnOomException();
      case 'ncnn_err':
        throw NcnnErrorException(msg[1] as String);
      default:
        throw EngineException(msg[1] as String);
    }
  }

  Future<void> dispose() async {
    final port = _commandPort;
    _commandPort = null;
    sessionAddress = 0;
    _starting = null;
    if (port != null) {
      final ack = ReceivePort();
      port.send(<Object?>[ack.sendPort, '__dispose__']);
      await ack.first.timeout(
        const Duration(seconds: 2),
        onTimeout: () => null,
      );
      ack.close();
    }
    _handshakePort?.close();
    _handshakePort = null;
    _isolate?.kill(priority: Isolate.immediate);
    _isolate = null;
  }
}

void _ncnnWorkerEntry(SendPort handshake) {
  final lib = NcnnLibrary.tryLoad();
  final handle = lib?.create() ?? nullptr;
  final commands = ReceivePort();
  handshake.send(<Object?>[commands.sendPort, handle.address]);

  String? loadedKey;

  commands.listen((dynamic raw) {
    final req = raw as List<Object?>;
    final reply = req[0] as SendPort;
    if (req[1] == '__dispose__') {
      if (lib != null && handle != nullptr) {
        if (loadedKey != null) lib.unloadModel(handle);
        lib.destroy(handle);
      }
      reply.send(true);
      commands.close();
      return;
    }

    if (lib == null || handle == nullptr) {
      reply.send(<Object?>['err', 'Native ncnn library failed to initialize']);
      return;
    }

    final modelId = req[1] as String;
    final nativeScale = req[2] as int;
    final threadCount = req[3] as int;
    final useGpu = req[4] as bool;
    final width = req[5] as int;
    final height = req[6] as int;
    final pixels = (req[7] as TransferableTypedData)
        .materialize()
        .asUint8List();
    final modelDir = req[8] as String;

    final paramPath = ncnnParamPath(modelDir, modelId, nativeScale);
    final binPath = ncnnBinPath(modelDir, modelId);
    final key = '$paramPath#$binPath#$threadCount#$useGpu';

    try {
      if (loadedKey != key) {
        if (loadedKey != null) {
          lib.unloadModel(handle);
          loadedKey = null;
        }
        final code = lib.loadModel(
          handle,
          paramPath,
          binPath,
          threadCount,
          useGpu,
        );
        if (code == NcnnLibrary.rsOutOfMemory) {
          reply.send(<Object?>['oom']);
          return;
        }
        if (code != NcnnLibrary.rsOk) {
          reply.send(<Object?>['ncnn_err', lib.lastError()]);
          return;
        }
        loadedKey = key;
      }

      final job = TileJob(
        modelId: modelId,
        nativeScale: nativeScale,
        threadCount: threadCount,
        useGpu: useGpu,
        width: width,
        height: height,
        pixels: pixels,
        modelDir: modelDir,
      );
      final out = lib.processTile(job, handle);
      reply.send(<Object?>[
        'ok',
        TransferableTypedData.fromList(<Uint8List>[out]),
      ]);
    } on NcnnCancelledException {
      reply.send(<Object?>['cancelled']);
    } on NcnnOomException {
      reply.send(<Object?>['oom']);
    } on NcnnErrorException catch (e) {
      reply.send(<Object?>['ncnn_err', e.detail]);
    } catch (e) {
      reply.send(<Object?>['err', e.toString()]);
    }
  });
}
