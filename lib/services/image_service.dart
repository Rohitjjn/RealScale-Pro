/// RealScale-Pro — image inspection, decode, EXIF orientation and
/// encode.
///
/// * Inspection reads magic bytes + JPEG APP1 EXIF orientation without
///   fully decoding (cheap, works on huge files).
/// * Decode uses dart:ui (platform codecs, fast on Android) and then
///   applies EXIF orientation on the RGBA buffer with a pure-Dart
///   transform — orientation correctness must not depend on codec
///   quirks.
/// * Encode uses the pure-Dart `image` package: PNG (lossless) or JPEG
///   with an explicit quality in the 90–96 range.
library;

import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:image/image.dart' as img;

import '../domain/models.dart';

class ImageService {
  /// Inspects a file: format, dimensions, alpha presence, EXIF
  /// orientation. Decodes headers only where possible; dimensions of
  /// JPEG/PNG are parsed without a full decode.
  Future<ImageMetadata> inspect(File file) async {
    final bytes = await file.readAsBytes();
    if (bytes.length < 16) {
      throw const ImageInspectException('file too small to be an image');
    }
    final format = detectFormat(bytes);
    if (format == null) {
      throw ImageInspectException(
          'unsupported or unrecognized format (file starts with '
          '${bytes.sublist(0, 4).map((b) => b.toRadixString(16)).join(' ')})');
    }
    if (format == 'HEIC' || format == 'HEIF') {
      throw const ImageInspectException(
          'HEIC/HEIF is not supported yet — convert to JPEG or PNG first');
    }

    final dims = switch (format) {
      'JPEG' => _jpegDimensions(bytes),
      'PNG' => _pngDimensions(bytes),
      _ => null,
    };

    int exif = 0;
    bool hasAlpha = false;
    if (dims == null) {
      // Full decode fallback (WebP etc.).
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      hasAlpha = !(await _frameIsOpaque(frame.image));
      final oriented = _oriented(dimsFrom(frame.image), exif);
      return ImageMetadata(
        width: oriented.$1,
        height: oriented.$2,
        format: format,
        fileSizeBytes: bytes.length,
        hasAlpha: hasAlpha,
        exifOrientation: exif,
        formatDetails: 'decoded for inspection',
      );
    }

    if (format == 'JPEG') {
      exif = parseJpegExifOrientation(bytes) ?? 0;
    } else if (format == 'PNG') {
      hasAlpha = _pngHasAlpha(bytes);
    }

    final oriented = _oriented(dims, exif);
    return ImageMetadata(
      width: oriented.$1,
      height: oriented.$2,
      format: format,
      fileSizeBytes: bytes.length,
      hasAlpha: hasAlpha,
      exifOrientation: exif,
    );
  }

  static (int, int) dimsFrom(ui.Image image) => (image.width, image.height);
  static (int, int) _oriented((int, int) dims, int exif) {
    if (exif >= 5 && exif <= 8) {
      return (dims.$2, dims.$1);
    }
    return dims;
  }

  /// Decodes to RGBA (orientation applied), ready for the engine.
  Future<RgbaImage> decodeToRgba(File file) async {
    final bytes = await file.readAsBytes();
    final exif = parseJpegExifOrientation(bytes) ?? 0;
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    final data =
        await frame.image.toByteData(format: ui.ImageByteFormat.rawRgba);
    if (data == null) {
      throw const ImageDecodeException('platform decoder returned no data');
    }
    codec.dispose();
    frame.image.dispose();
    var pixels = data.buffer.asUint8List();
    final needsTransform = exif >= 2 && exif <= 8;
    if (needsTransform) {
      pixels = applyExifOrientation(
          pixels, frame.image.width, frame.image.height, exif);
      final swapped = exif >= 5 && exif <= 8;
      return RgbaImage(
        pixels: pixels,
        width: swapped ? frame.image.height : frame.image.width,
        height: swapped ? frame.image.width : frame.image.height,
      );
    }
    return RgbaImage(
      pixels: pixels,
      width: frame.image.width,
      height: frame.image.height,
    );
  }

  /// Encodes RGBA pixels to encoded bytes.
  Future<Uint8List> encode(
    RgbaImage image, {
    required OutputFormat format,
    int? jpegQuality,
  }) async {
    final picture = img.Image.fromBytes(
      width: image.width,
      height: image.height,
      bytes: image.pixels.buffer,
      numChannels: 4,
      order: img.ChannelOrder.rgba,
    );
    switch (format) {
      case OutputFormat.png:
        return img.encodePng(picture);
      case OutputFormat.jpeg:
        final q = (jpegQuality ?? 92).clamp(90, 96);
        // JPEG has no alpha: composite over white so transparent areas
        // do not turn black (planner normally routes alpha sources to
        // PNG; this is the safety net).
        final rgb = picture.numChannels == 4
            ? flattenOverWhite(picture)
            : picture;
        return img.encodeJpg(rgb, quality: q);
    }
  }

  /// Downsamples an RGBA buffer to fit within [maxEdge] (square fit).
  /// Only used by Upscale Mode as an EXPLICIT, user-confirmed step —
  /// never silently.
  RgbaImage downsampleToFit(RgbaImage source, int maxEdge) {
    final src = img.Image.fromBytes(
      width: source.width,
      height: source.height,
      bytes: source.pixels.buffer,
      numChannels: 4,
      order: img.ChannelOrder.rgba,
    );
    final longest = source.width > source.height
        ? source.width
        : source.height;
    if (longest <= maxEdge) {
      return source;
    }
    final scale = maxEdge / longest;
    final w = (source.width * scale).round();
    final h = (source.height * scale).round();
    final dst = img.copyResize(src, width: w, height: h);
    return RgbaImage(
      pixels: Uint8List.fromList(dst.getBytes(order: img.ChannelOrder.rgba)),
      width: w,
      height: h,
    );
  }
}

// ------------------------------------------------------------------ //
// Format sniffing + header parsing (pure functions, unit-testable).
// ------------------------------------------------------------------ //

String? detectFormat(Uint8List b) {
  if (b.length >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) {
    return 'JPEG';
  }
  if (b.length >= 8 &&
      b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
    return 'PNG';
  }
  if (b.length >= 12 &&
      b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70) {
    final brand = String.fromCharCodes(b.sublist(8, 12));
    if (brand == 'heic' || brand == 'heix' || brand == 'hevc') {
      return 'HEIC';
    }
    if (brand == 'mif1' || brand == 'msf1') {
      return 'HEIF';
    }
    return 'ISOBMFF';
  }
  if (b.length >= 12 && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 &&
      b[3] == 0x46 && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 &&
      b[11] == 0x50) {
    return 'WEBP';
  }
  if (b.length >= 2 && b[0] == 0x42 && b[1] == 0x4D) {
    return 'BMP';
  }
  return null;
}

(int, int)? _jpegDimensions(Uint8List b) {
  var i = 2;
  while (i + 9 < b.length) {
    if (b[i] != 0xFF) {
      i += 1;
      continue;
    }
    final marker = b[i + 1];
    if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2 ||
        marker == 0xC3 || marker == 0xC5 || marker == 0xC6 ||
        marker == 0xC7 || marker == 0xC9 || marker == 0xCA ||
        marker == 0xCB || marker == 0xCD || marker == 0xCE ||
        marker == 0xCF) {
      final h = (b[i + 5] << 8) | b[i + 6];
      final w = (b[i + 7] << 8) | b[i + 8];
      return (w, h);
    }
    if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
      i += 2;
      continue;
    }
    final segLen = (b[i + 2] << 8) | b[i + 3];
    i += 2 + segLen;
  }
  return null;
}

(int, int)? _pngDimensions(Uint8List b) {
  if (b.length >= 24 && b[12] == 0x49 && b[13] == 0x48 && b[14] == 0x44 &&
      b[15] == 0x52) {
    final w = (b[16] << 24) | (b[17] << 16) | (b[18] << 8) | b[19];
    final h = (b[20] << 24) | (b[21] << 16) | (b[22] << 8) | b[23];
    return (w, h);
  }
  return null;
}

bool _pngHasAlpha(Uint8List b) {
  if (b.length < 26) {
    return false;
  }
  final colorType = b[25];
  return colorType == 4 || colorType == 6;
}

Future<bool> _frameIsOpaque(ui.Image image) async {
  // Cheap heuristic: decode a 1x1 thumbnail and check its alpha.
  final completer = ui.PictureRecorder();
  final canvas = ui.Canvas(completer);
  canvas.scale(1 / image.width, 1 / image.height);
  canvas.drawImage(image, ui.Offset.zero, ui.Paint());
  final picture = completer.endRecording();
  final thumb = picture.toImageSync(1, 1);
  final data = await thumb.toByteData(format: ui.ImageByteFormat.rawRgba);
  picture.dispose();
  thumb.dispose();
  return data == null || data.getUint8(3) == 255;
}

/// Parses the EXIF orientation (tag 0x0112) from a JPEG APP1 segment.
/// Returns 1..8 or null when absent/unparseable.
int? parseJpegExifOrientation(Uint8List b) {
  var i = 2;
  while (i + 4 < b.length) {
    if (b[i] != 0xFF) {
      return null; // not a marker — EXIF would have started by now
    }
    final marker = b[i + 1];
    if (marker == 0xE1) {
      final segLen = (b[i + 2] << 8) | b[i + 3];
      final seg = b.sublist(i + 4, i + 2 + segLen);
      if (seg.length >= 6) {
        const exifHeader = [0x45, 0x78, 0x69, 0x66, 0x00, 0x00]; // "Exif\0\0"
        var match = true;
        for (var k = 0; k < 6; k++) {
          if (seg[k] != exifHeader[k]) {
            match = false;
            break;
          }
        }
        if (match) {
          return _tiffOrientation(seg.sublist(6));
        }
      }
      return null;
    }
    if (marker == 0xDA || marker == 0xD9) {
      return null; // start of scan / EOI — no EXIF present
    }
    final segLen = (b[i + 2] << 8) | b[i + 3];
    i += 2 + segLen;
  }
  return null;
}

int? _tiffOrientation(Uint8List tiff) {
  if (tiff.length < 8) {
    return null;
  }
  final bigEndian = tiff[0] == 0x4D && tiff[1] == 0x4D;
  if (!bigEndian && !(tiff[0] == 0x49 && tiff[1] == 0x49)) {
    return null;
  }
  int u16(int off) => bigEndian
      ? (tiff[off] << 8) | tiff[off + 1]
      : (tiff[off + 1] << 8) | tiff[off];
  int u32(int off) => bigEndian
      ? (tiff[off] << 24) | (tiff[off + 1] << 16) | (tiff[off + 2] << 8) |
            tiff[off + 3]
      : (tiff[off + 3] << 24) |
            (tiff[off + 2] << 16) |
            (tiff[off + 1] << 8) |
            tiff[off];

  final ifdOffset = u32(4);
  if (ifdOffset + 2 > tiff.length) {
    return null;
  }
  final entryCount = u16(ifdOffset);
  for (var e = 0; e < entryCount; e++) {
    final entry = ifdOffset + 2 + e * 12;
    if (entry + 12 > tiff.length) {
      return null;
    }
    if (u16(entry) == 0x0112) {
      final value = u16(entry + 8);
      return value >= 1 && value <= 8 ? value : null;
    }
  }
  return null;
}

/// Applies EXIF orientation 1..8 to an RGBA buffer (pure transform).
Uint8List applyExifOrientation(
    Uint8List pixels, int width, int height, int orientation) {
  switch (orientation) {
    case 1:
      return pixels;
    case 2:
      return _transform(pixels, width, height, flipX: true);
    case 3:
      return _transform(pixels, width, height, rotate180: true);
    case 4:
      return _transform(pixels, width, height, flipY: true);
    case 5:
      return _transform(pixels, width, height, transpose: true);
    case 6:
      return _transform(pixels, width, height, rotate90: true);
    case 7:
      return _transform(pixels, width, height, transverse: true);
    case 8:
      return _transform(pixels, width, height, rotate270: true);
    default:
      return pixels;
  }
}

Uint8List _transform(
  Uint8List src,
  int w,
  int h, {
  bool flipX = false,
  bool flipY = false,
  bool rotate180 = false,
  bool rotate90 = false,
  bool rotate270 = false,
  bool transpose = false,
  bool transverse = false,
}) {
  // Output dims swap for 90/270/transpose/transverse.
  final swap = rotate90 || rotate270 || transpose || transverse;
  final outW = swap ? h : w;
  final outH = swap ? w : h;
  final out = Uint8List(outW * outH * 4);
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      var dx = x;
      var dy = y;
      if (flipX) {
        dx = w - 1 - x;
      }
      if (flipY) {
        dy = h - 1 - y;
      }
      if (rotate180) {
        dx = w - 1 - x;
        dy = h - 1 - y;
      }
      if (rotate90) {
        dx = h - 1 - y;
        dy = x;
      }
      if (rotate270) {
        dx = y;
        dy = w - 1 - x;
      }
      if (transpose) {
        dx = y;
        dy = x;
      }
      if (transverse) {
        dx = h - 1 - y;
        dy = w - 1 - x;
      }
      final si = (y * w + x) * 4;
      final di = (dy * outW + dx) * 4;
      out[di] = src[si];
      out[di + 1] = src[si + 1];
      out[di + 2] = src[si + 2];
      out[di + 3] = src[si + 3];
    }
  }
  return out;
}

class ImageInspectException implements Exception {
  const ImageInspectException(this.message);
  final String message;
  @override
  String toString() => 'ImageInspectException: $message';
}

class ImageDecodeException implements Exception {
  const ImageDecodeException(this.message);
  final String message;
  @override
  String toString() => 'ImageDecodeException: $message';
}

/// Payload for isolate encoding (compute).
class EncodePayload {
  const EncodePayload({
    required this.pixels,
    required this.width,
    required this.height,
    required this.format,
    required this.jpegQuality,
  });
  final Uint8List pixels;
  final int width;
  final int height;
  final OutputFormat format;
  final int? jpegQuality;
}

/// Top-level encode entry point for compute()/Isolate.run.
/// Uses the pure-Dart image package; safe off the main isolate.
Uint8List encodeRgbaIsolate(EncodePayload p) {
  final picture = img.Image.fromBytes(
    width: p.width,
    height: p.height,
    bytes: p.pixels.buffer,
    numChannels: 4,
    order: img.ChannelOrder.rgba,
  );
  switch (p.format) {
    case OutputFormat.png:
      return img.encodePng(picture);
    case OutputFormat.jpeg:
      final q = (p.jpegQuality ?? 92).clamp(90, 96);
      final rgb = picture.numChannels == 4
          ? flattenOverWhite(picture)
          : picture;
      return img.encodeJpg(rgb, quality: q);
  }
}

/// Composites an RGBA image over opaque white and returns RGB.
img.Image flattenOverWhite(img.Image src) {
  final out = src.convert(numChannels: 4);
  final maxV = src.maxChannelValue;
  for (final p in out) {
    final a = p.a;
    if (a != maxV) {
      final alpha = a / maxV;
      p
        ..r = (p.r * alpha + 255 * (1 - alpha)).round()
        ..g = (p.g * alpha + 255 * (1 - alpha)).round()
        ..b = (p.b * alpha + 255 * (1 - alpha)).round()
        ..a = maxV;
    }
  }
  return out.convert(numChannels: 3);
}
