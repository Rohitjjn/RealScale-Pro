import 'dart:typed_data';

import 'engine_types.dart';
import 'image_bitmap.dart';
import 'lanczos_resampler.dart';
import 'tiling_engine.dart';
import '../services/model_catalog/model_catalog.dart' show ModelDef;

/// Pure-Dart fallback engine: real upscaling via Lanczos-3 at the model's
/// native scale (plus unsharp for 5x), executed in background isolates.
///
/// It is an honest engine — pixels are really resampled, progress is real —
/// used when the ncnn native library is absent (dev machines, unit tests).
/// It never claims to be the neural engine.
class LanczosEngine extends TilingEngine {
  LanczosEngine()
    : super(engineId: 'lanczos', tileProcessor: _lanczosTileProcessor);

  @override
  bool supports(ModelDef m, DeviceProfile d) => m.available;
}

/// Converts a TileJob into a Bitmap (zero-copy view over the same pixels).
Bitmap _toBitmap(TileJob job) =>
    Bitmap(job.width, job.height, Uint8List.fromList(job.pixels));

/// Processes one tile with a real Lanczos resample in an isolate.
Future<Uint8List> _lanczosTileProcessor(TileJob job) async {
  final tile = _toBitmap(job);
  final outW = job.width * job.nativeScale;
  final outH = job.height * job.nativeScale;
  final up = await LanczosResampler().resample(tile, outW, outH);
  return up.pixels;
}
