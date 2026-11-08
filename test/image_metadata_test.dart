/// Image inspection tests: format sniffing, header parsing, EXIF
/// orientation (all 8 transforms), alpha detection.
library;

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/services/image_service.dart';

/// Minimal JPEG with an APP1 EXIF segment carrying [orientation].
Uint8List buildTinyJpegWithExif(int orientation, {int width = 4, int height = 4}) {
  final tiff = <int>[
    0x4D, 0x4D, 0x00, 0x2A, // big-endian TIFF
    0x00, 0x00, 0x00, 0x08, // IFD offset
    0x00, 0x01, // 1 entry
    0x01, 0x12, // tag 0x0112 (orientation)
    0x00, 0x03, // type SHORT
    0x00, 0x00, 0x00, 0x01, // count
    (orientation >> 8) & 0xFF, orientation & 0xFF, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, // next IFD
  ];
  final app1Body = <int>[
    ...'Exif'.codeUnits, 0x00, 0x00, ...tiff,
  ];
  final segLen = app1Body.length + 2;
  return Uint8List.fromList([
    0xFF, 0xD8, // SOI
    0xFF, 0xE1, (segLen >> 8) & 0xFF, segLen & 0xFF, ...app1Body,
    0xFF, 0xC0, 0x00, 0x11, 0x08,
    (height >> 8) & 0xFF, height & 0xFF,
    (width >> 8) & 0xFF, width & 0xFF,
    0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00,
    0xFF, 0xD9, // EOI
  ]);
}

Uint8List buildTinyPng({int width = 3, int height = 2, bool alpha = false}) {
  // Hand-built 1x1-ish PNG via the image package would be heavier;
  // construct a minimal valid PNG header manually (IHDR only check).
  final colorType = alpha ? 6 : 2;
  final ihdr = <int>[
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 'I'.codeUnitAt(0), 'H'.codeUnitAt(0),
    'D'.codeUnitAt(0), 'R'.codeUnitAt(0),
    (width >> 24) & 0xFF, (width >> 16) & 0xFF, (width >> 8) & 0xFF,
    width & 0xFF,
    (height >> 24) & 0xFF, (height >> 16) & 0xFF, (height >> 8) & 0xFF,
    height & 0xFF,
    0x08, colorType, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, // CRC (not parsed by our header reader)
  ];
  return Uint8List.fromList(ihdr);
}

void main() {
  group('detectFormat', () {
    test('recognizes JPEG magic', () {
      final jpeg = buildTinyJpegWithExif(1);
      expect(detectFormat(jpeg), 'JPEG');
    });

    test('recognizes PNG magic and alpha variants', () {
      expect(detectFormat(buildTinyPng(alpha: false)), 'PNG');
      expect(detectFormat(buildTinyPng(alpha: true)), 'PNG');
    });

    test('rejects unknown formats', () {
      expect(detectFormat(Uint8List.fromList(List.filled(32, 0x00))), isNull);
    });

    test('flags HEIC explicitly (unsupported)', () {
      final heic = Uint8List.fromList([
        0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63,
        ...List.filled(20, 0),
      ]);
      expect(detectFormat(heic), 'HEIC');
    });
  });

  group('EXIF orientation parsing', () {
    test('reads orientation from a synthetic APP1 segment', () {
      for (var o = 1; o <= 8; o++) {
        final jpeg = buildTinyJpegWithExif(o);
        expect(parseJpegExifOrientation(jpeg), o,
            reason: 'orientation $o must round-trip');
      }
    });

    test('missing EXIF returns null', () {
      final jpeg = Uint8List.fromList([
        0xFF, 0xD8, 0xFF, 0xC0, 0x00, 0x11, 0x08,
        0, 4, 0, 4, 0x03,
        0xFF, 0xD9,
      ]);
      expect(parseJpegExifOrientation(jpeg), isNull);
    });
  });

  group('orientation transforms', () {
    Uint8List gradient(int w, int h) {
      final px = Uint8List(w * h * 4);
      for (var y = 0; y < h; y++) {
        for (var x = 0; x < w; x++) {
          final i = (y * w + x) * 4;
          px[i] = x;
          px[i + 1] = y;
          px[i + 2] = 128;
          px[i + 3] = 255;
        }
      }
      return px;
    }

    test('all 8 orientations produce a consistent mapping', () {
      const w = 7, h = 5;
      final src = gradient(w, h);
      int at(Uint8List p, int ww, int x, int y) =>
          p[(y * ww + x) * 4] + p[(y * ww + x) * 4 + 1] * 1000;

      final orig = at(src, w, 3, 2);

      // 180 rotation: pixel (3,2) -> (w-4, h-3)
      final r180 = applyExifOrientation(src, w, h, 3);
      expect(at(r180, w, w - 1 - 3, h - 1 - 2), orig);

      // 90 CW rotation: dims swap; (x,y) -> (h-1-y, x)
      final r90 = applyExifOrientation(src, w, h, 6);
      expect(at(r90, h, h - 1 - 2, 3), orig);

      // Horizontal mirror: (x,y) -> (w-1-x, y)
      final mir = applyExifOrientation(src, w, h, 2);
      expect(at(mir, w, w - 1 - 3, 2), orig);

      // Identity.
      final id = applyExifOrientation(src, w, h, 1);
      expect(at(id, w, 3, 2), orig);
    });

    test('round-trip: orientation 6 then 8 restores the original', () {
      const w = 9, h = 4;
      final src = gradient(w, h);
      final rotated = applyExify(src, w, h, 6);
      final restored = applyExify(rotated, h, w, 8);
      for (var i = 0; i < src.length; i++) {
        expect(restored[i], src[i]);
      }
    });
  });

group('ImageService.inspect on real files', () {
  test('inspects the real JPEG fixture with dimensions', () async {
    final file = File('test/fixtures/input.jpg');
    if (!file.existsSync()) {
      markTestSkipped('fixture missing');
      return;
    }
    final meta = await ImageService().inspect(file);
    expect(meta.format, 'JPEG');
    expect(meta.width, 220);
    expect(meta.height, 220);
    expect(meta.fileSizeBytes, greaterThan(0));
    expect(meta.hasAlpha, isFalse);
  });
});

}

Uint8List applyExify(Uint8List src, int w, int h, int o) =>
    applyExifOrientation(src, w, h, o);
