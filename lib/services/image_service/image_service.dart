import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui'
    show
        ImmutableBuffer,
        ImageByteFormat,
        ImageDescriptor,
        instantiateImageCodec;
import 'package:image/image.dart' as img;

import '../../core/app_exception.dart';
import '../../core/app_logger.dart';
import '../../engine/image_bitmap.dart';

/// ImageService — decode, EXIF orientation, the 2 MB / quality-90 input rule
/// (prd §5.2), transparency protection, photo-vs-anime classification and
/// output encoding. Plain Dart + Flutter codecs; no UI imports.
class ImageService {
  ImageService({required this.tempDir});

  final String tempDir;

  static const maxInlineBytes = 2 * 1024 * 1024; // 2 MB rule

  /// Decoded pixels handed to the engine through JobSpec.options.
  Future<DecodedImage> decodeForEngine(
    Uint8List bytes, {
    required String sourceName,
  }) async {
    // 1) Read EXIF orientation from the original bytes (JPEG only).
    final orientation = _exifOrientation(bytes);

    // 2) Decode via the platform codec (fast, wide format support).
    final buffer = await _decodeWithFlutter(bytes);
    var width = buffer.width;
    var height = buffer.height;

    // 3) EXIF orientation applied (never rely on the viewer to fix it).
    Uint8List pixels = buffer.pixels;
    if (orientation >= 5) {
      pixels = _rotate90(pixels, width, height);
      final t = width;
      width = height;
      height = t;
    } else if (orientation == 2) {
      pixels = _flipH(pixels, width, height);
    } else if (orientation == 4) {
      pixels = _flipH(_rotate180(pixels, width, height), width, height);
    } else if (orientation == 3) {
      pixels = _rotate180(pixels, width, height);
    } else if (orientation == 7) {
      pixels = _rotate90(_flipH(pixels, width, height), width, height);
      final t = width;
      width = height;
      height = t;
    } else if (orientation == 6) {
      pixels = _rotate90(pixels, width, height);
      final t = width;
      width = height;
      height = t;
    } else if (orientation == 8) {
      pixels = _rotate90(_rotate180(pixels, width, height), width, height);
      final t = width;
      width = height;
      height = t;
    }

    // 4) Transparency / CMYK / 16-bit conversions handled by codecs; PNG
    // alpha is preserved end-to-end (never compressed away).
    final hasAlpha = _hasAlpha(pixels);

    return DecodedImage(
      width: width,
      height: height,
      pixels: pixels,
      hasAlpha: hasAlpha,
      sourceName: sourceName,
    );
  }

  /// The 2 MB rule (prd §5.2): inputs above 2 MB are processed via a
  /// quality-90 temporary copy which is deleted after the job. Returns the
  /// effective input bytes and whether a temp copy was used.
  Future<({Uint8List bytes, bool usedTemp, String? tempPath})> applyInputRule(
    Uint8List original,
  ) async {
    if (original.lengthInBytes <= maxInlineBytes) {
      return (bytes: original, usedTemp: false, tempPath: null);
    }
    final decoded = img.decodeImage(original);
    if (decoded == null) {
      throw const UnsupportedImageException('Could not read this image');
    }
    final reencoded = img.encodeJpg(decoded, quality: 90);
    final tempPath =
        '$tempDir/rs_input_${DateTime.now().microsecondsSinceEpoch}.jpg';
    final file = File(tempPath);
    await file.writeAsBytes(reencoded, flush: true);
    AppLogger.d(
      'ImageService',
      'input ${original.lengthInBytes} B → temp q90 copy',
    );
    return (
      bytes: Uint8List.fromList(reencoded),
      usedTemp: true,
      tempPath: tempPath,
    );
  }

  /// Deletes the temp input copy (called after every job, on cancel, at app
  /// start — originals are never modified).
  Future<void> cleanupTempInput(String? tempPath) async {
    if (tempPath == null) return;
    try {
      final f = File(tempPath);
      if (await f.exists()) await f.delete();
    } catch (e) {
      AppLogger.e('ImageService', 'temp cleanup failed', e);
    }
  }

  /// Photo-vs-anime classifier (prd §5.4): lightweight heuristics on the
  /// decoded pixels — flat-color share, edge saturation, palette entropy.
  /// Returns (type, confidence 0..1).
  ({ImageKind kind, double confidence}) classify(DecodedImage image) {
    final step = math.max(1, image.width * image.height ~/ 40000);
    var flat = 0;
    var saturated = 0;
    var counted = 0;
    final histogram = List<int>.filled(4 * 4 * 4, 0);
    for (var i = 0; i < image.pixels.lengthInBytes; i += 4 * step) {
      final r = image.pixels[i],
          g = image.pixels[i + 1],
          b = image.pixels[i + 2];
      counted++;
      final maxC = math.max(r, math.max(g, b));
      final minC = math.min(r, math.min(g, b));
      if (maxC - minC < 12) flat++; // low local variance → flat color
      if (maxC - minC > 60) saturated++;
      histogram[(r >> 6) * 16 + (g >> 6) * 4 + (b >> 6)]++;
    }
    var entropy = 0.0;
    for (final c in histogram) {
      if (c == 0) continue;
      final p = c / counted;
      entropy -= p * (math.log(p) / math.ln2);
    }
    final flatShare = flat / math.max(1, counted);
    final satShare = saturated / math.max(1, counted);

    // Anime art: many flat regions, fewer distinct colors, higher saturation.
    final animeScore =
        flatShare * 1.2 + (entropy < 5.5 ? 0.25 : 0) + satShare * 0.4;
    final kind = animeScore > 0.62 ? ImageKind.anime : ImageKind.photo;
    final confidence = (0.5 + (animeScore - 0.62).abs())
        .clamp(0.5, 0.95)
        .toDouble();
    return (kind: kind, confidence: confidence);
  }

  /// Encodes the result to the requested format; PNG keeps transparency
  /// (prd §7.3), metadata is copied from the source when JPEG→JPEG.
  Future<Uint8List> encodeOutput({
    required Uint8List rgba,
    required int width,
    required int height,
    required bool hasAlpha,
    required OutputKind kind,
    required int quality,
    Uint8List? originalJpegForMetadata,
  }) async {
    final image = img.Image.fromBytes(
      width: width,
      height: height,
      bytes: rgba.buffer,
      numChannels: 4,
      order: img.ChannelOrder.rgba,
    );
    switch (kind) {
      case OutputKind.png:
        return Uint8List.fromList(img.encodePng(image));
      case OutputKind.webp:
        return Uint8List.fromList(img.encodeWebP(image, quality: quality));
      case OutputKind.jpeg:
        final out = img.encodeJpg(image, quality: quality);
        if (originalJpegForMetadata != null) {
          return _copyExif(originalJpegForMetadata, Uint8List.fromList(out));
        }
        return Uint8List.fromList(out);
      case OutputKind.sameAsSource:
        throw const UnsupportedImageException('Format must be resolved first');
    }
  }

  /// Copies the original APP1 EXIF segment into a re-encoded JPEG so photo
  /// metadata (date, camera) survives (prd §7.3 "metadata kept").
  Uint8List _copyExif(Uint8List originalJpeg, Uint8List outputJpeg) {
    Uint8List? exif;
    if (originalJpeg.length > 4 &&
        originalJpeg[0] == 0xFF &&
        originalJpeg[1] == 0xD8) {
      var i = 2;
      while (i + 4 <= originalJpeg.length) {
        if (originalJpeg[i] != 0xFF) break;
        final marker = originalJpeg[i + 1];
        final len = (originalJpeg[i + 2] << 8) | originalJpeg[i + 3];
        if (marker == 0xE1 && // APP1
            i + 10 <= originalJpeg.length &&
            originalJpeg[i + 4] == 0x45 && // 'E'
            originalJpeg[i + 5] == 0x78 && // 'x'
            originalJpeg[i + 6] == 0x69 &&
            originalJpeg[i + 7] == 0x66 &&
            originalJpeg[i + 8] == 0x00 &&
            originalJpeg[i + 9] == 0x00) {
          exif = originalJpeg.sublist(i, i + 2 + len);
          break;
        }
        if (marker == 0xDA) break; // start of scan
        i += 2 + len;
      }
    }
    if (exif == null) return outputJpeg;
    final out = BytesBuilder();
    out.add(outputJpeg.sublist(0, 2)); // SOI
    out.add(exif);
    out.add(outputJpeg.sublist(2));
    return out.toBytes();
  }

  // --- decode helpers --------------------------------------------------------

  Future<_DecodedBuffer> _decodeWithFlutter(Uint8List bytes) async {
    try {
      final buffer = await ImmutableBuffer.fromUint8List(bytes);
      final descriptor = await ImageDescriptor.encoded(buffer);
      final codec = await instantiateImageCodec(
        bytes,
        targetWidth: descriptor.width,
        targetHeight: descriptor.height,
      );
      final frame = await codec.getNextFrame();
      final data = await frame.image.toByteData(
        format: ImageByteFormat.rawRgba,
      );
      frame.image.dispose();
      codec.dispose();
      if (data == null) {
        throw const UnsupportedImageException('Could not read this image');
      }
      return _DecodedBuffer(
        width: descriptor.width,
        height: descriptor.height,
        pixels: data.buffer.asUint8List(),
      );
    } on UnsupportedImageException {
      rethrow;
    } catch (e) {
      // Fallback: pure-Dart decode (handles formats the platform codec may
      // reject, e.g. some CMYK JPEGs / 16-bit PNGs).
      final decoded = img.decodeImage(bytes);
      if (decoded == null) {
        throw const UnsupportedImageException(
          'This image format is not supported',
        );
      }
      final rgba = decoded.getBytes(order: img.ChannelOrder.rgba);
      return _DecodedBuffer(
        width: decoded.width,
        height: decoded.height,
        pixels: Uint8List.fromList(rgba),
      );
    }
  }

  // --- EXIF orientation (minimal, JPEG only) ---------------------------------

  int _exifOrientation(Uint8List bytes) {
    try {
      if (bytes.length < 12 || bytes[0] != 0xFF || bytes[1] != 0xD8) return 1;
      var i = 2;
      while (i + 4 <= bytes.length) {
        if (bytes[i] != 0xFF) return 1;
        final marker = bytes[i + 1];
        final len = (bytes[i + 2] << 8) | bytes[i + 3];
        if (marker == 0xE1 && bytes[i + 4] == 0x45 && bytes[i + 5] == 0x78) {
          // EXIF APP1; orientation is tag 0x0112 in IFD0.
          final tiff = i + 10;
          final little = bytes[tiff] == 0x49;
          int u16(int o) => little
              ? bytes[o] | (bytes[o + 1] << 8)
              : (bytes[o] << 8) | bytes[o + 1];
          int u32(int o) => little
              ? bytes[o] |
                    (bytes[o + 1] << 8) |
                    (bytes[o + 2] << 16) |
                    (bytes[o + 3] << 24)
              : (bytes[o] << 24) |
                    (bytes[o + 1] << 16) |
                    (bytes[o + 2] << 8) |
                    bytes[o + 3];
          final ifdOffset = u32(tiff + 4) + tiff;
          final entries = u16(ifdOffset);
          for (var e = 0; e < entries; e++) {
            final entry = ifdOffset + 2 + e * 12;
            if (entry + 12 > bytes.length) break;
            if (u16(entry) == 0x0112) return u16(entry + 8).clamp(1, 8);
          }
          return 1;
        }
        if (marker == 0xDA) return 1;
        i += 2 + len;
      }
    } catch (e) {
      AppLogger.e('ImageService', 'exif parse failed', e);
    }
    return 1;
  }

  bool _hasAlpha(Uint8List pixels) {
    for (var i = 3; i < pixels.lengthInBytes; i += 4) {
      if (pixels[i] != 255) return true;
    }
    return false;
  }

  Uint8List _rotate180(Uint8List p, int w, int h) {
    final out = Uint8List(p.length);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final si = ((h - 1 - y) * w + (w - 1 - x)) * 4;
        final di = (y * w + x) * 4;
        out.setRange(di, di + 4, p, si);
      }
    }
    return out;
  }

  Uint8List _rotate90(Uint8List p, int w, int h) {
    final out = Uint8List(p.length);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final si = (y * w + x) * 4;
        final di = (x * h + (h - 1 - y)) * 4;
        out.setRange(di, di + 4, p, si);
      }
    }
    return out;
  }

  Uint8List _flipH(Uint8List p, int w, int h) {
    final out = Uint8List(p.length);
    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        final si = (y * w + (w - 1 - x)) * 4;
        final di = (y * w + x) * 4;
        out.setRange(di, di + 4, p, si);
      }
    }
    return out;
  }

  /// Thumbnail bytes for lists (uses the platform codec, cheap and cached).
  Future<Uint8List?> thumbnail(Uint8List bytes, int maxSize) async {
    try {
      final buffer = await ImmutableBuffer.fromUint8List(bytes);
      final descriptor = await ImageDescriptor.encoded(buffer);
      final scale = (maxSize / math.max(descriptor.width, descriptor.height))
          .clamp(0.0, 1.0);
      final codec = await instantiateImageCodec(
        bytes,
        targetWidth: (descriptor.width * scale).round(),
        targetHeight: (descriptor.height * scale).round(),
      );
      final frame = await codec.getNextFrame();
      final data = await frame.image.toByteData(format: ImageByteFormat.png);
      frame.image.dispose();
      codec.dispose();
      return data?.buffer.asUint8List();
    } catch (e) {
      AppLogger.e('ImageService', 'thumbnail failed', e);
      return null;
    }
  }

  /// Decodes PNG/JPEG bytes into an engine Bitmap (used by JobManager to
  /// re-read engine temp output for the face stage and final encode).
  Future<Bitmap> decodeToBitmap(Uint8List bytes) async {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) {
      throw const UnsupportedImageException('Result unreadable');
    }
    final rgba = decoded.getBytes(order: img.ChannelOrder.rgba);
    return Bitmap(decoded.width, decoded.height, Uint8List.fromList(rgba));
  }

  /// Final encode + write used by JobManager.registerEncodedSaver.
  Future<void> writeEncoded({
    required String path,
    required Bitmap bmp,
    required OutputKind kind,
    required int quality,
    Uint8List? metadataSource,
  }) async {
    final outKind = kind == OutputKind.sameAsSource ? OutputKind.png : kind;
    final bytes = await encodeOutput(
      rgba: bmp.pixels,
      width: bmp.width,
      height: bmp.height,
      hasAlpha: false,
      kind: outKind,
      quality: quality,
      originalJpegForMetadata: metadataSource,
    );
    await File(path).writeAsBytes(bytes, flush: true);
  }
}

enum ImageKind { photo, anime }

enum OutputKind { sameAsSource, jpeg, png, webp }

class DecodedImage {
  const DecodedImage({
    required this.width,
    required this.height,
    required this.pixels,
    required this.hasAlpha,
    required this.sourceName,
  });
  final int width;
  final int height;
  final Uint8List pixels;
  final bool hasAlpha;
  final String sourceName;
}

final class _DecodedBuffer {
  _DecodedBuffer({
    required this.width,
    required this.height,
    required this.pixels,
  });
  final int width;
  final int height;
  final Uint8List pixels;
}
