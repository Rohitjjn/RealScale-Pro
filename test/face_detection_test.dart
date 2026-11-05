import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/services/face_detection/face_detection.dart';

/// Builds a width×height RGBA image: a skin-toned oval on a dark background
/// plus optional extra blobs, to exercise the detector like a portrait.
Uint8List _portraitPixels(int w, int h) {
  final px = Uint8List(w * h * 4);
  void put(int x, int y, int r, int g, int b) {
    final i = (y * w + x) * 4;
    px[i] = r;
    px[i + 1] = g;
    px[i + 2] = b;
    px[i + 3] = 255;
  }

  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      put(x, y, 30, 34, 40); // dark studio background
    }
  }
  // One large skin-toned blob in the upper half (a face).
  final cx = w ~/ 2, cy = h ~/ 3, rx = w ~/ 6, ry = h ~/ 5;
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      final dx = (x - cx) / rx, dy = (y - cy) / ry;
      if (dx * dx + dy * dy <= 1) put(x, y, 224, 172, 140); // skin tone
    }
  }
  return px;
}

void main() {
  const helper = FaceDetectionHelper();

  test('skin-region portrait yields a detected hint with one region', () async {
    final w = 320, h = 480;
    final result = await helper.detect(_portraitPixels(w, h), w, h);
    expect(result.status, FaceDetectionStatus.detected);
    expect(result.faces, isNotEmpty);
    expect(result.faces.first.confidence, greaterThan(0.2));
    // Crop stays inside the image.
    final f = result.faces.first;
    expect(f.left, greaterThanOrEqualTo(0));
    expect(f.top, greaterThanOrEqualTo(0));
    expect(f.left + f.width, lessThanOrEqualTo(w));
    expect(f.top + f.height, lessThanOrEqualTo(h));
  });

  test('flat non-skin image reports none (no false hint)', () async {
    final w = 200, h = 200;
    final px = Uint8List(w * h * 4);
    for (var i = 0; i < px.length; i += 4) {
      px[i] = 20;
      px[i + 1] = 60;
      px[i + 2] = 30; // greenery
      px[i + 3] = 255;
    }
    final result = await helper.detect(px, w, h);
    expect(result.status, FaceDetectionStatus.none);
    expect(result.count, 0);
  });

  test('degenerate input never throws', () async {
    final r1 = await helper.detect(Uint8List(0), 0, 0);
    expect(r1.status, FaceDetectionStatus.none);
    final r2 = await helper.detect(Uint8List(16), 2, 2);
    expect(r2.status, FaceDetectionStatus.none);
  });

  test(
    'detection keeps UI responsive: many runs stay fast (hint only)',
    () async {
      final w = 240, h = 320;
      final sw = Stopwatch()..start();
      for (var i = 0; i < 5; i++) {
        await helper.detect(_portraitPixels(w, h), w, h);
      }
      sw.stop();
      // Five runs of a small grid must finish quickly; it runs in an isolate.
      expect(sw.elapsedMilliseconds, lessThan(10000));
    },
  );
}
