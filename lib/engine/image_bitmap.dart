import 'dart:math' as math;
import 'dart:typed_data';

/// Simple RGBA bitmap used by engines. Row-major, 8 bits per channel.
class Bitmap {
  Bitmap(this.width, this.height, Uint8List pixels)
    : assert(pixels.length == width * height * 4),
      pixels = pixels;

  final int width;
  final int height;
  final Uint8List pixels; // RGBA

  int get byteLength => width * height * 4;

  Bitmap clone() => Bitmap(width, height, Uint8List.fromList(pixels));

  /// Crops a sub-region (clamped to bounds).
  Bitmap crop(int x, int y, int w, int h) {
    final cx = x < 0 ? 0 : (x > width - 1 ? width - 1 : x);
    final cy = y < 0 ? 0 : (y > height - 1 ? height - 1 : y);
    final cw = w < width - cx ? w : width - cx;
    final ch = h < height - cy ? h : height - cy;
    final out = Uint8List(w * h * 4);
    for (var row = 0; row < ch; row++) {
      final srcStart = ((cy + row) * width + cx) * 4;
      out.setRange(row * w * 4, row * w * 4 + cw * 4, pixels, srcStart);
    }
    return Bitmap(w, h, out);
  }

  /// Blends [patch] into this bitmap at (x, y) with a linear feather ramp on
  /// the left/top edges for seamless tile stitching (prd §8.5).
  void blendPatch(Bitmap patch, int x, int y, int feather) {
    for (var row = 0; row < patch.height; row++) {
      final dy = y + row;
      if (dy < 0 || dy >= height) continue;
      final fy = row < feather && y > 0 ? row / feather : 1.0;
      for (var col = 0; col < patch.width; col++) {
        final dx = x + col;
        if (dx < 0 || dx >= width) continue;
        final fx = col < feather && x > 0 ? col / feather : 1.0;
        final a = fx * fy;
        final di = (dy * width + dx) * 4;
        final si = (row * patch.width + col) * 4;
        if (a >= 1) {
          pixels[di] = patch.pixels[si];
          pixels[di + 1] = patch.pixels[si + 1];
          pixels[di + 2] = patch.pixels[si + 2];
          pixels[di + 3] = patch.pixels[si + 3];
        } else {
          pixels[di] = (pixels[di] * (1 - a) + patch.pixels[si] * a).round();
          pixels[di + 1] = (pixels[di + 1] * (1 - a) + patch.pixels[si + 1] * a)
              .round();
          pixels[di + 2] = (pixels[di + 2] * (1 - a) + patch.pixels[si + 2] * a)
              .round();
          pixels[di + 3] = (pixels[di + 3] * (1 - a) + patch.pixels[si + 3] * a)
              .round();
        }
      }
    }
  }
}

/// Lanczos-3 kernel (a = 3).
double lanczos3(double x) {
  const a = 3.0;
  final ax = x.abs();
  if (ax < 1e-8) return 1.0;
  if (ax >= a) return 0.0;
  const pi = math.pi;
  final pix = pi * ax;
  return (a * math.sin(pix) * math.sin(pix / a)) / (pix * pix);
}
