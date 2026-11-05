import 'dart:isolate';
import 'dart:math' as math;
import 'dart:typed_data';

import 'image_bitmap.dart';

/// Real Lanczos-3 resampling running in a background isolate.
/// Used for the scale chain (native scale → target) and by the pure-Dart
/// fallback engine. Never runs on the UI thread.
class LanczosResampler {
  const LanczosResampler();

  Future<Bitmap> resample(Bitmap src, int dstW, int dstH) {
    return Isolate.run(() => resampleSync(src, dstW, dstH));
  }

  /// Synchronous version for isolate-internal use and tests.
  static Bitmap resampleSync(Bitmap src, int dstW, int dstH) {
    if (dstW <= 0 || dstH <= 0) {
      throw ArgumentError('Invalid target size $dstW x $dstH');
    }
    if (src.width == dstW && src.height == dstH) return src.clone();

    // Horizontal pass: width → dstW, stored as float rows.
    final hWeights = _contributions(src.width, dstW);
    final tmp = Float32List(dstW * src.height * 4);
    for (var y = 0; y < src.height; y++) {
      for (var x = 0; x < dstW; x++) {
        final idx = hWeights.$1[x];
        final wts = hWeights.$2[x];
        double r = 0, g = 0, b = 0, a = 0;
        for (var k = 0; k < idx.length; k++) {
          final sx = idx[k];
          final w = wts[k];
          final si = (y * src.width + sx) * 4;
          r += src.pixels[si] * w;
          g += src.pixels[si + 1] * w;
          b += src.pixels[si + 2] * w;
          a += src.pixels[si + 3] * w;
        }
        final di = (y * dstW + x) * 4;
        tmp[di] = r;
        tmp[di + 1] = g;
        tmp[di + 2] = b;
        tmp[di + 3] = a;
      }
    }

    // Vertical pass: height → dstH.
    final vWeights = _contributions(src.height, dstH);
    final out = Uint8List(dstW * dstH * 4);
    for (var y = 0; y < dstH; y++) {
      final idx = vWeights.$1[y];
      final wts = vWeights.$2[y];
      for (var x = 0; x < dstW; x++) {
        double r = 0, g = 0, b = 0, a = 0;
        for (var k = 0; k < idx.length; k++) {
          final sy = idx[k];
          final w = wts[k];
          final si = (sy * dstW + x) * 4;
          r += tmp[si] * w;
          g += tmp[si + 1] * w;
          b += tmp[si + 2] * w;
          a += tmp[si + 3] * w;
        }
        final di = (y * dstW + x) * 4;
        out[di] = _clamp8(r);
        out[di + 1] = _clamp8(g);
        out[di + 2] = _clamp8(b);
        out[di + 3] = _clamp8(a);
      }
    }
    return Bitmap(dstW, dstH, out);
  }

  static int _clamp8(double v) => v < 0 ? 0 : (v > 255 ? 255 : v.round());

  /// Precomputes source indices + weights for every destination sample.
  static (List<Int32List>, List<Float64List>) _contributions(
    int srcLen,
    int dstLen,
  ) {
    final scale = dstLen / srcLen;
    final support = scale < 1 ? 3.0 / scale : 3.0;
    final indices = List<Int32List>.filled(dstLen, Int32List(0));
    final weights = List<Float64List>.filled(dstLen, Float64List(0));
    for (var i = 0; i < dstLen; i++) {
      final center = (i + 0.5) / scale - 0.5;
      final start = math.max(0, (center - support + 0.5).floor());
      final end = math.min(srcLen - 1, (center + support - 0.5).ceil());
      final count = end - start + 1;
      final idx = Int32List(count);
      final wts = Float64List(count);
      var sum = 0.0;
      for (var k = 0; k < count; k++) {
        final srcIdx = start + k;
        idx[k] = srcIdx < 0 ? 0 : (srcIdx > srcLen - 1 ? srcLen - 1 : srcIdx);
        final w = lanczos3((start + k - center) * (scale < 1 ? scale : 1));
        wts[k] = w;
        sum += w;
      }
      if (sum.abs() > 1e-10) {
        for (var k = 0; k < count; k++) {
          wts[k] /= sum;
        }
      } else {
        wts[0] = 1.0;
      }
      indices[i] = idx;
      weights[i] = wts;
    }
    return (indices, weights);
  }
}

/// Light unsharp mask applied after the 5x extra resize step (prd §8.4).
Bitmap unsharpMask(Bitmap src, {double amount = 0.35}) {
  final out = src.clone();
  final w = src.width;
  final h = src.height;
  for (var y = 1; y < h - 1; y++) {
    for (var x = 1; x < w - 1; x++) {
      final i = (y * w + x) * 4;
      for (var c = 0; c < 3; c++) {
        final blur =
            (src.pixels[i - w * 4 + c] +
                src.pixels[i + w * 4 + c] +
                src.pixels[i - 4 + c] +
                src.pixels[i + 4 + c]) /
            4;
        final v = src.pixels[i + c] + amount * (src.pixels[i + c] - blur);
        out.pixels[i + c] = v < 0 ? 0 : (v > 255 ? 255 : v.round());
      }
    }
  }
  return out;
}
