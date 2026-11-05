import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:uuid/uuid.dart';

import '../../core/app_exception.dart';
import '../../core/app_logger.dart';

/// One row in the My Upscales history.
class UpscaleRecord {
  UpscaleRecord({
    required this.id,
    required this.originalName,
    required this.savedPath,
    required this.width,
    required this.height,
    required this.fileSizeBytes,
    required this.modelId,
    required this.scale,
    required this.elapsedMs,
    required this.createdAt,
    this.thumbnailPath,
  });

  final String id;
  final String originalName;
  final String savedPath;
  final int width;
  final int height;
  final int fileSizeBytes;
  final String modelId;
  final int scale;
  final int elapsedMs;
  final DateTime createdAt;
  final String? thumbnailPath;

  Map<String, Object?> toMap() => {
    'id': id,
    'originalName': originalName,
    'savedPath': savedPath,
    'width': width,
    'height': height,
    'fileSizeBytes': fileSizeBytes,
    'modelId': modelId,
    'scale': scale,
    'elapsedMs': elapsedMs,
    'createdAt': createdAt.millisecondsSinceEpoch,
    'thumbnailPath': thumbnailPath,
  };

  static UpscaleRecord fromMap(Map<String, Object?> m) => UpscaleRecord(
    id: m['id'] as String,
    originalName: m['originalName'] as String,
    savedPath: m['savedPath'] as String,
    width: m['width'] as int,
    height: m['height'] as int,
    fileSizeBytes: m['fileSizeBytes'] as int,
    modelId: m['modelId'] as String,
    scale: m['scale'] as int,
    elapsedMs: m['elapsedMs'] as int,
    createdAt: DateTime.fromMillisecondsSinceEpoch(m['createdAt'] as int),
    thumbnailPath: m['thumbnailPath'] as String?,
  );
}

/// StorageService — saves to the platform RealScale-Pro folder (prd §7.3),
/// keeps the history DB (SQLite via sqflite), checks free space, and
/// registers saved images with the system gallery (MediaStore on Android).
class StorageService {
  StorageService();

  Database? _db;
  String? _saveDirPath;

  static const _dbName = 'realscale_history.db';
  static const _table = 'upscales';

  Future<Database> get db async {
    if (_db != null) return _db!;
    // FFI factory also works on desktop; sqflite handles Android natively.
    if (Platform.isWindows || Platform.isLinux) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }
    final dir = await getApplicationDocumentsDirectory();
    _db = await databaseFactory.openDatabase(
      '${dir.path}/$_dbName',
      options: OpenDatabaseOptions(
        version: 1,
        onCreate: (d, v) async => d.execute('''
          CREATE TABLE $_table (
            id TEXT PRIMARY KEY,
            originalName TEXT NOT NULL,
            savedPath TEXT NOT NULL,
            width INTEGER NOT NULL,
            height INTEGER NOT NULL,
            fileSizeBytes INTEGER NOT NULL,
            modelId TEXT NOT NULL,
            scale INTEGER NOT NULL,
            elapsedMs INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            thumbnailPath TEXT
          )'''),
      ),
    );
    return _db!;
  }

  /// The RealScale-Pro save folder per platform:
  ///   Android 10+ → Pictures/RealScale-Pro (via app external files dir)
  ///   Windows → Pictures\RealScale-Pro
  Future<String> saveDir() async {
    if (_saveDirPath != null) return _saveDirPath!;
    final Directory base;
    if (Platform.isAndroid) {
      base =
          await getExternalStorageDirectory() ??
          await getApplicationDocumentsDirectory();
      // Use the Pictures subfolder of the app's external dir so gallery
      // indexers pick it up without legacy storage permissions.
      _saveDirPath = '${base.path}/Pictures/RealScale-Pro';
    } else {
      final home =
          Platform.environment['USERPROFILE'] ??
          Platform.environment['HOME'] ??
          '/';
      _saveDirPath = '$home/Pictures/RealScale-Pro';
    }
    final dir = Directory(_saveDirPath!);
    if (!await dir.exists()) await dir.create(recursive: true);
    return _saveDirPath!;
  }

  /// Naming rule (prd §7.3): `RealScale_<original>_<scale>x_<yyyymmdd_hhmmss>.<ext>`
  String outputFileName({
    required String originalName,
    required int scale,
    required String ext,
    DateTime? at,
  }) {
    final base = originalName.replaceAll(RegExp(r'\.[^.]+$'), '');
    final safe = base.replaceAll(RegExp(r'[^\w\-]+'), '_');
    final t = at ?? DateTime.now();
    final stamp =
        '${t.year.toString().padLeft(4, '0')}'
        '${t.month.toString().padLeft(2, '0')}'
        '${t.day.toString().padLeft(2, '0')}_'
        '${t.hour.toString().padLeft(2, '0')}'
        '${t.minute.toString().padLeft(2, '0')}'
        '${t.second.toString().padLeft(2, '0')}';
    return 'RealScale_${safe}_${scale}x_$stamp.$ext';
  }

  /// Free-space check before saving (rules.md: never crash on disk-full).
  Future<bool> hasFreeSpaceMb(int neededMb) async {
    try {
      final res = await Process.run('df', ['-k', Directory.systemTemp.path]);
      if (res.exitCode == 0) {
        final lines = (res.stdout as String).trim().split('\n');
        if (lines.length >= 2) {
          final cols = lines.last.split(RegExp(r'\s+'));
          if (cols.length >= 4) {
            final availKb = int.tryParse(cols[3]) ?? 0;
            return availKb >= neededMb * 1024;
          }
        }
      }
    } catch (_) {}
    return true; // cannot check → allow, failures surface on save
  }

  /// Saves the finished file into the RealScale-Pro folder and records it.
  Future<UpscaleRecord> saveResult({
    required String tempPath,
    required String originalName,
    required int width,
    required int height,
    required int scale,
    required String modelId,
    required int elapsedMs,
    String? thumbnailPath,
  }) async {
    final dir = await saveDir();
    final ext = _extensionOf(tempPath);
    final name = outputFileName(
      originalName: originalName,
      scale: scale,
      ext: ext,
    );
    final destPath = '$dir/$name';
    final sourceFile = File(tempPath);
    if (!await sourceFile.exists()) {
      throw const StorageFullException('Output file missing after job');
    }
    await sourceFile.copy(destPath);
    final size = await File(destPath).length();

    final record = UpscaleRecord(
      id: const Uuid().v4(),
      originalName: originalName,
      savedPath: destPath,
      width: width,
      height: height,
      fileSizeBytes: size,
      modelId: modelId,
      scale: scale,
      elapsedMs: elapsedMs,
      createdAt: DateTime.now(),
      thumbnailPath: thumbnailPath,
    );
    final database = await db;
    await database.insert(_table, record.toMap());
    AppLogger.d('StorageService', 'saved $destPath ($size B)');
    await _publishToGallery(destPath, name);
    return record;
  }

  static String _extensionOf(String path) {
    final lower = path.toLowerCase();
    if (lower.endsWith('.png')) return 'png';
    if (lower.endsWith('.webp')) return 'webp';
    if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'jpg';
    return 'png';
  }

  /// Registers the saved image with the system gallery (MediaStore on
  /// Android) so it shows up in Google Photos / the Gallery app. Best
  /// effort only: a denial or an unsupported platform must never fail the
  /// save — the file already lives in Pictures/RealScale-Pro.
  Future<void> _publishToGallery(String path, String title) async {
    if (!Platform.isAndroid && !Platform.isIOS) return;
    try {
      final permitted = await PhotoManager.requestPermissionExtend();
      if (!permitted.hasAccess) {
        AppLogger.d('StorageService', 'gallery permission not granted');
        return;
      }
      await PhotoManager.editor.saveImageWithPath(
        path,
        title: title,
        description: 'RealScale-Pro upscale',
      );
    } catch (e, st) {
      AppLogger.w('StorageService', 'gallery publish skipped: $e', st);
    }
  }

  Future<List<UpscaleRecord>> history({int limit = 200}) async {
    final database = await db;
    final rows = await database.query(
      _table,
      orderBy: 'createdAt DESC',
      limit: limit,
    );
    return rows.map(UpscaleRecord.fromMap).toList();
  }

  Future<void> deleteRecord(String id) async {
    final database = await db;
    final rows = await database.query(
      _table,
      where: 'id = ?',
      whereArgs: [id],
      limit: 1,
    );
    if (rows.isNotEmpty) {
      final path = rows.first['savedPath'] as String?;
      if (path != null) {
        try {
          final f = File(path);
          if (await f.exists()) await f.delete();
        } catch (e) {
          AppLogger.e('StorageService', 'file delete failed', e);
        }
      }
    }
    await database.delete(_table, where: 'id = ?', whereArgs: [id]);
  }

  /// Cache/temp cleanup — never touches installed models (task 2.10).
  ///
  /// Best effort by design: the system temp dir is shared with every other
  /// process on the device, so a locked or already-deleted file must never
  /// make cleanup throw (rules.md golden rule 5).
  Future<void> clearCaches() async {
    await _purgeTempDir(Directory.systemTemp);
    try {
      final docs = await getApplicationDocumentsDirectory();
      await _purgeTempDir(Directory('${docs.path}/rs_temp'));
    } catch (e, st) {
      AppLogger.w('StorageService', 'cache cleanup skipped: $e', st);
    }
  }

  Future<void> _purgeTempDir(Directory dir) async {
    try {
      if (!await dir.exists()) return;
      await for (final e in dir.list()) {
        if (e is! File) continue;
        if (!e.uri.pathSegments.last.startsWith('rs_')) continue;
        try {
          await e.delete();
        } catch (_) {
          // Locked by another process — leave it for the next sweep.
        }
      }
    } catch (e, st) {
      AppLogger.w('StorageService', 'temp dir cleanup skipped: $e', st);
    }
  }
}
