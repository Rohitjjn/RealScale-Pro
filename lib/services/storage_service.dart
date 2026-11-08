/// RealScale-Pro — output storage.
///
/// Results are written to `<app documents>/RealScale-Pro/` with a
/// predictable, duplicate-safe naming scheme. The original image is
/// never touched (the app only reads it).
///
/// Android additionally exports the saved file to the shared gallery
/// (Pictures/RealScale-Pro) via a MethodChannel (`realscale/media`)
/// so it is visible in photo apps, and exposes a share sheet using the
/// MediaStore content URI. Non-Android platforms keep the app-documents
/// copy only — nothing is silently duplicated.
library;

import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

class StorageService {
  StorageService({this.enableGalleryExport = true});
  static const outputFolderName = 'RealScale-Pro';
  static const _mediaChannel = MethodChannel('realscale/media');
  static const _deviceChannel = MethodChannel('realscale/device');

  final bool enableGalleryExport;

  /// Test seam for the Android platform check (production always uses
  /// Platform.isAndroid; tests inject true to exercise channel paths).
  @visibleForTesting
  bool? isAndroidOverride;

  bool get _isAndroid => isAndroidOverride ?? Platform.isAndroid;

  /// Content URI of the most recent gallery export, when available
  /// (used by the share sheet; re-populated on each save).
  String? lastGalleryContentUri;

  /// Base output directory (created on demand).
  Future<Directory> outputDirectory() async {
    final docs = await getApplicationDocumentsDirectory();
    final dir = Directory('${docs.path}/$outputFolderName');
    if (!dir.existsSync()) {
      dir.createSync(recursive: true);
    }
    return dir;
  }

  /// Model storage directory (app support, hidden from galleries).
  Future<Directory> modelDirectory() async {
    final support = await getApplicationSupportDirectory();
    final dir = Directory('${support.path}/models');
    if (!dir.existsSync()) {
      dir.createSync(recursive: true);
    }
    return dir;
  }

  /// Returns a unique, non-destructive output path. Existing files are
  /// never overwritten: "name.png" becomes "name (2).png" etc.
  Future<File> uniqueOutputPath(String baseName, String extension) async {
    final dir = await outputDirectory();
    var candidate = File(
        '${dir.path}/$baseName${extension.startsWith('.') ? '' : '.'}$extension');
    var counter = 2;
    while (candidate.existsSync()) {
      candidate = File('${dir.path}/$baseName ($counter)'
          '${extension.startsWith('.') ? '' : '.'}$extension');
      counter += 1;
    }
    return candidate;
  }

  /// Free space on the output volume, or -1 when unknown.
  ///
  /// Android: StatFs via the device channel (exact). Other platforms:
  /// `df` on POSIX hosts; -1 where unavailable.
  Future<int> freeSpaceBytes() async {
    if (_isAndroid) {
      try {
        final info = await _deviceChannel
            .invokeMethod<Map<dynamic, dynamic>>('getDeviceInfo');
        final bytes = (info?['freeStorageBytes'] as num?)?.toInt();
        if (bytes != null && bytes >= 0) {
          return bytes;
        }
      } on PlatformException {
        // fall through to df
      } on MissingPluginException {
        // test harness — fall through
      }
    }
    final dir = await outputDirectory();
    try {
      final result = Process.runSync('df', ['-B1', dir.path]);
      final lines = (result.stdout as String).split('\n');
      if (lines.length >= 2) {
        final parts = lines[1].split(RegExp(r'\s+'));
        if (parts.length >= 4) {
          return int.tryParse(parts[3]) ?? -1;
        }
      }
    } catch (_) {
      // df unavailable (web/Windows) — report unknown.
    }
    return -1;
  }

  /// Checks that [bytes] can safely be written; throws with an honest
  /// message when storage is insufficient or unwritable.
  Future<void> ensureCanWrite(int bytes, {int headroom = 64 * 1024 * 1024}) async {
    final free = await freeSpaceBytes();
    if (free >= 0 && free < bytes + headroom) {
      throw StorageException(
          'not enough free storage (need ~${(bytes + headroom) ~/ 1024 ~/ 1024} MB, '
          'have ~${free ~/ 1024 ~/ 1024} MB)');
    }
  }

  /// Writes encoded bytes atomically (temp file + rename).
  Future<File> writeOutput(String baseName, String extension,
      List<int> bytes) async {
    final target = await uniqueOutputPath(baseName, extension);
    final tmp = File('${target.path}.tmp');
    await ensureCanWrite(bytes.length);
    await tmp.writeAsBytes(bytes, flush: true);
    await tmp.rename(target.path);
    return target;
  }

  /// Exports a saved output into the Android shared gallery
  /// (Pictures/RealScale-Pro). Returns the gallery file path, or null
  /// when the platform does not support it / the export failed (the
  /// app-documents copy always remains).
  Future<String?> exportToGallery(File file, String mimeType) async {
    if (!enableGalleryExport || !_isAndroid) {
      return null;
    }
    try {
      final result = await _mediaChannel
          .invokeMethod<Map<dynamic, dynamic>>('saveToGallery', {
        'bytes': file.readAsBytesSync(),
        'displayName': file.uri.pathSegments.last,
        'mimeType': mimeType,
      });
      lastGalleryContentUri = result?['contentUri'] as String?;
      return result?['path'] as String? ?? lastGalleryContentUri;
    } on PlatformException catch (e) {
      throw StorageException('gallery export failed: ${e.message}');
    } on MissingPluginException {
      return null; // test harness / unsupported embedding
    }
  }

  /// Opens the system share sheet for a previously exported file.
  /// Requires a content URI (see [exportToGallery]); returns false when
  /// sharing is not available on this platform.
  Future<bool> shareResult({required String mimeType, String? title}) async {
    final uri = lastGalleryContentUri;
    if (!_isAndroid || uri == null) {
      return false;
    }
    try {
      await _mediaChannel.invokeMethod('shareFile', {
        'uri': uri,
        'mimeType': mimeType,
        'title': title ?? 'RealScale-Pro result',
      });
      return true;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  /// Deletes leftover temporary files (crash recovery hygiene).
  Future<void> cleanTempFiles() async {
    final dir = await outputDirectory();
    for (final entity in dir.listSync()) {
      if (entity is File && entity.path.endsWith('.tmp')) {
        try {
          entity.deleteSync();
        } catch (_) {
          // Best effort — a locked temp file is retried next launch.
        }
      }
    }
  }

  Future<List<FileSystemEntity>> recentOutputs({int limit = 50}) async {
    final dir = await outputDirectory();
    final entities = dir.listSync()
      ..sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));
    return entities.take(limit).toList();
  }
}

class StorageException implements Exception {
  StorageException(this.message);
  final String message;
  @override
  String toString() => 'StorageException: $message';
}
