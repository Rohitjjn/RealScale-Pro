import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:image/image.dart' as image_lib;
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';

import '../../core/models.dart';

/// Image selection, safe decoding and the Upscale Mode 2 MB rule.
class ImageService {
  ImageService({ImagePicker? picker}) : _picker = picker ?? ImagePicker();

  static const int compressionThreshold = 2 * 1024 * 1024;
  final ImagePicker _picker;

  Future<ImageSelection?> pickFile() async {
    final FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const <String>['jpg', 'jpeg', 'png', 'webp', 'heic', 'bmp'],
    );
    final String? path = result?.files.single.path;
    return path == null ? null : inspect(path);
  }

  Future<ImageSelection?> pickGallery() async {
    final XFile? result = await _picker.pickImage(source: ImageSource.gallery);
    return result == null ? null : inspect(result.path);
  }

  Future<ImageSelection?> takePhoto() async {
    final XFile? result = await _picker.pickImage(source: ImageSource.camera);
    return result == null ? null : inspect(result.path);
  }

  Future<ImageSelection> inspect(String path) async {
    final File file = File(path);
    final Uint8List bytes = await file.readAsBytes();
    final _ImageFacts facts = await Isolate.run(() => _decodeFacts(bytes));
    return ImageSelection(
      path: path,
      name: file.uri.pathSegments.last,
      width: facts.width,
      height: facts.height,
      bytes: bytes.length,
      hasAlpha: facts.hasAlpha,
      kind: facts.kind,
    );
  }

  Future<String> prepareForUpscale(ImageSelection selection) async {
    if (selection.bytes <= compressionThreshold || selection.hasAlpha) {
      return selection.path;
    }
    final Uint8List source = await selection.file.readAsBytes();
    final Uint8List encoded = await Isolate.run(() {
      final image_lib.Image? decoded = image_lib.decodeImage(source);
      if (decoded == null) {
        throw const FormatException('unsupported_image');
      }
      final image_lib.Image oriented = image_lib.bakeOrientation(decoded);
      return Uint8List.fromList(image_lib.encodeJpg(oriented, quality: 90));
    });
    final Directory temporary = await getTemporaryDirectory();
    final File copy = File(
      '${temporary.path}/realscale_${DateTime.now().microsecondsSinceEpoch}.jpg',
    );
    await copy.writeAsBytes(encoded, flush: true);
    return copy.path;
  }

  Future<void> removeTemporaryCopy(String path, String originalPath) async {
    if (path == originalPath) {
      return;
    }
    final File file = File(path);
    if (await file.exists()) {
      await file.delete();
    }
  }

  static _ImageFacts _decodeFacts(Uint8List bytes) {
    final image_lib.Image? decoded = image_lib.decodeImage(bytes);
    if (decoded == null || decoded.width < 64 || decoded.height < 64) {
      throw const FormatException('unsupported_image');
    }
    final image_lib.Image oriented = image_lib.bakeOrientation(decoded);
    final bool hasAlpha = oriented.numChannels == 4;
    int saturated = 0;
    int sampled = 0;
    final int stride = (oriented.width * oriented.height / 1500).ceil().clamp(1, 999999);
    for (int index = 0; index < oriented.width * oriented.height; index += stride) {
      final int x = index % oriented.width;
      final int y = index ~/ oriented.width;
      if (y >= oriented.height) {
        break;
      }
      final image_lib.Pixel pixel = oriented.getPixel(x, y);
      final num maximum = <num>[pixel.r, pixel.g, pixel.b].reduce((num a, num b) => a > b ? a : b);
      final num minimum = <num>[pixel.r, pixel.g, pixel.b].reduce((num a, num b) => a < b ? a : b);
      if (maximum - minimum > 95) {
        saturated += 1;
      }
      sampled += 1;
    }
    final ImageKind kind = sampled > 0 && saturated / sampled > 0.42
        ? ImageKind.anime
        : ImageKind.photo;
    return _ImageFacts(oriented.width, oriented.height, hasAlpha, kind);
  }
}

class _ImageFacts {
  const _ImageFacts(this.width, this.height, this.hasAlpha, this.kind);

  final int width;
  final int height;
  final bool hasAlpha;
  final ImageKind kind;
}
