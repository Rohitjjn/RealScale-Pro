// RealScale-Pro — Dart-side encode benchmark (host).
//
// Measures the app's real output-encoding stage (package:image, pure
// Dart — the same encoder JobManager uses via compute()) for large RGBA
// buffers. Run from the project root:
//
//   dart run tools/bench_encode.dart
//
// The numbers feed docs/PERFORMANCE-REPORT.md; they are host figures.
library;

import 'dart:io';
import 'dart:typed_data';

import 'package:image/image.dart';

void main() {
  const sizes = [
    (1024, 1024),
    (2048, 2048),
    (4096, 4096),
  ];
  for (final (w, h) in sizes) {
    final rgba = _gradient(w, h);
    final pngBytes = _timed(() => encodePng(Image.fromBytes(
        width: w, height: h, bytes: rgba.buffer, numChannels: 4)));
    final jpgBytes = _timed(() => encodeJpg(
        Image.fromBytes(
            width: w, height: h, bytes: rgba.buffer, numChannels: 4),
        quality: 92));
    stdout.writeln('${w}x$h RGBA '
        '(${(w * h / 1e6).toStringAsFixed(1)} MP): '
        'PNG ${pngBytes.$1} ms (${(pngBytes.$2 / 1024 / 1024).toStringAsFixed(1)} MB) · '
        'JPEG q92 ${jpgBytes.$1} ms (${(jpgBytes.$2 / 1024 / 1024).toStringAsFixed(1)} MB)');
  }
}

Uint8List _gradient(int w, int h) {
  final data = Uint8List(w * h * 4);
  var i = 0;
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      data[i++] = (x * 255) ~/ w;
      data[i++] = (y * 255) ~/ h;
      data[i++] = ((x + y) * 255) ~/ (w + h);
      data[i++] = 255;
    }
  }
  return data;
}

/// Returns (elapsed ms, encoded length). The encoded length is written
/// to stdout so neither call can be optimized away.
(int, int) _timed(List<int> Function() f) {
  final sw = Stopwatch()..start();
  final bytes = f();
  sw.stop();
  return (sw.elapsedMilliseconds, bytes.length);
}
