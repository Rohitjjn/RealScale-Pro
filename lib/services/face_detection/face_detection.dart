import 'dart:async';
import 'dart:isolate';
import 'dart:typed_data';

import '../job_manager/job_manager.dart' show FaceCrop;

/// Status of one detection run (task 2.7: typed result, friendly states).
enum FaceDetectionStatus { detected, none, unavailable }

/// Typed detection result consumed by the UI — never raw native errors.
class FaceDetectionResult {
  const FaceDetectionResult({
    required this.status,
    this.faces = const [],
    this.confidence = 0,
  });

  final FaceDetectionStatus status;
  final List<FaceCrop> faces;

  /// 0..1 rough confidence of the whole run (hint only, not a guarantee).
  final double confidence;

  int get count => faces.length;
}

/// Local face-detection helper (task 2.7, prd §5.4).
///
/// Runs a conservative skin-region heuristic in a background isolate so the
/// UI thread is never blocked, works fully offline, and uploads nothing.
/// It is a *hint* for the face toggle — the actual restoration pipeline only
/// engages when a verified face model is installed.
///
/// Why a heuristic: every audited ML face detector with permissive licensing
/// was unavailable at build time (see licenses.md face audit); this helper
/// stays conservative (prefers "no faces" over a wrong guess) and is behind
/// a typed interface so a native detector can replace it without UI changes.
class FaceDetectionHelper {
  const FaceDetectionHelper();

  /// Detects face-candidate regions on an RGBA image. Never throws.
  Future<FaceDetectionResult> detect(
    Uint8List rgba,
    int width,
    int height,
  ) async {
    if (rgba.isEmpty || width <= 0 || height <= 0) {
      return const FaceDetectionResult(status: FaceDetectionStatus.none);
    }
    try {
      final raw = await Isolate.run(() => _detectSync(rgba, width, height));
      return raw;
    } catch (_) {
      // Detection is optional; a failure degrades to the neutral hint.
      return const FaceDetectionResult(status: FaceDetectionStatus.none);
    }
  }
}

FaceDetectionResult _detectSync(Uint8List rgba, int width, int height) {
  // Work on a small grid (max 96 px on the long side) — enough for a hint.
  const maxSide = 96;
  final scale = maxSide / (width > height ? width : height);
  if (scale >= 1) {
    return const FaceDetectionResult(status: FaceDetectionStatus.none);
  }
  final gw = (width * scale).floor().clamp(8, maxSide);
  final gh = (height * scale).floor().clamp(8, maxSide);

  // 1) Skin mask (classic RGB rule, conservative thresholds).
  final mask = Uint8List(gw * gh);
  var skinTotal = 0;
  for (var y = 0; y < gh; y++) {
    for (var x = 0; x < gw; x++) {
      final sx = (x / gw * width).floor().clamp(0, width - 1);
      final sy = (y / gh * height).floor().clamp(0, height - 1);
      final i = (sy * width + sx) * 4;
      final r = rgba[i], g = rgba[i + 1], b = rgba[i + 2];
      final mx = r > g ? (r > b ? r : b) : (g > b ? g : b);
      final mn = r < g ? (r < b ? r : b) : (g < b ? g : b);
      final isSkin =
          r > 95 &&
          g > 40 &&
          b > 20 &&
          mx - mn > 15 &&
          (r - g).abs() > 15 &&
          r > g &&
          r > b;
      final v = isSkin ? 1 : 0;
      mask[y * gw + x] = v;
      skinTotal += v;
    }
  }
  // Reject obviously non-portrait images: too little skin → no hint.
  if (skinTotal < gw * gh * 0.05) {
    return const FaceDetectionResult(status: FaceDetectionStatus.none);
  }

  // 2) Connected components (4-neighbour flood fill).
  final visited = Uint8List(gw * gh);
  final faces = <FaceCrop>[];
  for (var start = 0; start < mask.length; start++) {
    if (mask[start] == 0 || visited[start] == 1) continue;
    final queue = <int>[start];
    visited[start] = 1;
    var minX = gw, maxX = 0, minY = gh, maxY = 0, area = 0;
    while (queue.isNotEmpty) {
      final p = queue.removeLast();
      final px = p % gw, py = p ~/ gw;
      area++;
      if (px < minX) minX = px;
      if (px > maxX) maxX = px;
      if (py < minY) minY = py;
      if (py > maxY) maxY = py;
      for (final d in const [(1, 0), (-1, 0), (0, 1), (0, -1)]) {
        final nx = px + d.$1, ny = py + d.$2;
        if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue;
        final np = ny * gw + nx;
        if (mask[np] == 1 && visited[np] == 0) {
          visited[np] = 1;
          queue.add(np);
        }
      }
    }

    // 3) Face-like evidence: size fraction and aspect ratio. Faces occupy
    // roughly 1–45% of a portrait frame and are about as tall as wide.
    final w = maxX - minX + 1, h = maxY - minY + 1;
    final areaFrac = area / (gw * gh);
    final aspect = h / w;
    final fill = area / (w * h);
    final faceLike =
        areaFrac > 0.01 &&
        areaFrac < 0.45 &&
        aspect > 0.75 &&
        aspect < 2.2 &&
        fill > 0.45;
    if (!faceLike) continue;

    // Confidence: compact, portrait-proportioned regions score higher.
    final confidence = (fill * 0.4 + (1 - (aspect - 1.25).abs()) * 0.4 + 0.2)
        .clamp(0.0, 0.95);
    // Map back to full resolution.
    final left = (minX / gw * width).floor().clamp(0, width - 8);
    final top = (minY / gh * height).floor().clamp(0, height - 8);
    final cw = ((maxX - minX + 1) / gw * width).ceil().clamp(8, width - left);
    final ch = ((maxY - minY + 1) / gh * height).ceil().clamp(8, height - top);
    faces.add(
      FaceCrop(
        left: left,
        top: top,
        width: cw,
        height: ch,
        confidence: confidence,
      ),
    );
  }

  // Conservative: at most the 12 strongest regions, highest confidence first.
  faces.sort((a, b) => b.confidence.compareTo(a.confidence));
  final kept = faces.take(12).toList();
  if (kept.isEmpty) {
    return const FaceDetectionResult(status: FaceDetectionStatus.none);
  }
  return FaceDetectionResult(
    status: FaceDetectionStatus.detected,
    faces: kept,
    confidence: kept.first.confidence,
  );
}
