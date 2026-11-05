import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';

import '../../core/feature_gates.dart';
import '../../services/model_catalog/model_catalog.dart' show ModelDef;
import '../engine_types.dart';
import '../tiling_engine.dart';
import 'onnx_ffi.dart';

/// Installed ONNX model file convention inside the model directory:
///   `<modelDir>/<modelId>.onnx` (single file; the catalog row has one
///   `bin`-kind file entry). ModelManager installs it under this name.
String onnxModelPath(String modelDir, String modelId) =>
    '$modelDir/$modelId.onnx';

/// OnnxEngine — ONNX Runtime engine for the Ultra pack (SwinIR, HAT, DAT
/// — phases.md Phase 3). Extends the shared TilingEngine: tiling, overlap
/// feathering, honest progress, cancellation and the resample chain are
/// identical to the other engines; only the tile processor differs.
///
/// The runtime is probed with a real session-options creation. When the
/// shared library is missing (or the device is below the model's minimum
/// tier) the engine reports unsupported and the registry falls back —
/// never a crash. Catalog rows for Ultra models stay `comingSoon` until
/// audited weights ship; enabling them is a data-only change.
class OnnxEngine extends TilingEngine {
  OnnxEngine._(OnnxRuntimeLibrary lib)
    : super(engineId: 'onnx', tileProcessor: _OnnxTileProcessor(lib).call);

  static OnnxEngine? tryCreate() {
    if (!FeatureGates.onnxEngine) return null;
    final lib = OnnxRuntimeLibrary.tryLoad();
    if (lib == null) return null;
    return OnnxEngine._(lib);
  }

  /// Typed probe for tests and diagnostics — does not construct the engine.
  static OnnxRuntimeStatus probeStatus() {
    if (!FeatureGates.onnxEngine) {
      return const OnnxRuntimeStatus(
        available: false,
        reason: 'Feature gate off',
      );
    }
    final lib = OnnxRuntimeLibrary.tryLoad();
    if (lib == null) {
      return const OnnxRuntimeStatus(
        available: false,
        reason: 'ONNX Runtime library not present on this device',
      );
    }
    if (!lib.probeUsable()) {
      return const OnnxRuntimeStatus(
        available: false,
        reason: 'ONNX Runtime failed its self-check',
      );
    }
    return OnnxRuntimeStatus(available: true, version: lib.versionString());
  }

  @override
  bool supports(ModelDef m, DeviceProfile d) {
    if (!super.supports(m, d)) return false;
    if (m.engine != 'onnx') return false;
    // Ultra pack: capable devices only (models.md — Ultra rows are T3).
    return d.tier.atLeast(m.minTier) || d.tier == DeviceTier.t3;
  }
}

/// Result of probing the runtime — typed, shown nowhere raw.
class OnnxRuntimeStatus {
  const OnnxRuntimeStatus({required this.available, this.version, this.reason});
  final bool available;
  final String? version;
  final String? reason;
}

/// Processes tiles through ONNX Runtime: RGBA → float32 NCHW → ORT → RGBA.
/// Loads each model once and reuses the session (mirrors NcnnEngine).
final class _OnnxTileProcessor {
  _OnnxTileProcessor(this._lib);

  final OnnxRuntimeLibrary _lib;
  final _sessions = <String, OnnxSession>{};

  Future<Uint8List> call(TileJob job) async {
    final key = '${job.modelId}#${job.threadCount}';
    final session = _sessions.putIfAbsent(key, () {
      final s = _lib.createSession(
        onnxModelPath(job.modelDir, job.modelId),
        threads: job.threadCount.clamp(1, 8),
      );
      return s;
    });

    final w = job.width;
    final h = job.height;
    final inScale = job.nativeScale;

    // Tile padding: SwinIR/HAT class models want input dims divisible by
    // their window size; we pad to a multiple of 8 and crop after.
    const align = 8;
    final padW = (align - w % align) % align;
    final padH = (align - h % align) % align;
    final netW = w + padW;
    final netH = h + padH;

    // RGBA → float32 NCHW (RGB, normalized 0..1).
    final nchw = Float32List(3 * netW * netH);
    final px = job.pixels;
    var idx = 0;
    for (var y = 0; y < netH; y++) {
      final sy = y < h ? y : h - 1; // edge-extend padding
      for (var x = 0; x < netW; x++) {
        final sx = x < w ? x : w - 1;
        final rowStart = (sy * w + sx) * 4;
        final di = y * netW + x;
        nchw[idx++] = px[rowStart] / 255.0;
        nchw[netW * netH + di] = px[rowStart + 1] / 255.0;
        nchw[2 * netW * netH + di] = px[rowStart + 2] / 255.0;
      }
    }

    final out = session.run(nchw, [1, 3, netH, netW]);

    // float32 NCHW → RGBA (clamp). Conversion contract keeps RGB order;
    // per-model remap can ship with the audited weights.
    final outW = netW * inScale;
    final outH = netH * inScale;
    final plane = outW * outH;
    final rgba = Uint8List(w * inScale * h * inScale * 4);
    const maxIdx = 255;
    for (var y = 0; y < h * inScale; y++) {
      for (var x = 0; x < w * inScale; x++) {
        final si = y * outW + x;
        final di = si * 4;
        rgba[di] = (out[si] * 255.0).round().clamp(0, maxIdx);
        rgba[di + 1] = (out[plane + si] * 255.0).round().clamp(0, maxIdx);
        rgba[di + 2] = (out[2 * plane + si] * 255.0).round().clamp(0, maxIdx);
        rgba[di + 3] = 255;
      }
    }
    return rgba;
  }

  void dispose() {
    for (final s in _sessions.values) {
      s.dispose();
    }
    _sessions.clear();
  }
}

/// Reserved for the Ultra model rows: alpha is not super-resolved by SwinIR
/// class models — the engine composites the original alpha over the result
/// at the output scale when the source has transparency.
Uint8List compositeAlpha(
  Uint8List rgba, {
  required Uint8List alphaMask,
  required int width,
  required int height,
}) {
  final out = Uint8List.fromList(rgba);
  for (
    var i = 3, a = 0;
    i < out.lengthInBytes && a < alphaMask.length;
    i += 4, a++
  ) {
    out[i] = math.min(out[i], alphaMask[a]);
  }
  return out;
}
