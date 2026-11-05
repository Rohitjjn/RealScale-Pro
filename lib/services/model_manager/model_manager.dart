import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:background_downloader/background_downloader.dart';
import 'package:crypto/crypto.dart' as crypto;

import '../../core/app_exception.dart';
import '../../core/app_logger.dart';
import '../model_catalog/model_catalog.dart';

/// Lifecycle of one model's installation.
enum InstallStatus { notInstalled, downloading, installed, error, unavailable }

/// Observable per-model state (Model Manager rows, Manual rows, setup).
class ModelInstallInfo {
  const ModelInstallInfo({
    required this.modelId,
    required this.status,
    this.progress = 0,
    this.speedBytesPerSec = 0,
    this.etaSeconds,
    this.error,
    this.paused = false,
  });

  final String modelId;
  final InstallStatus status;
  final double progress; // 0..1 across all files of the model
  final int speedBytesPerSec;
  final int? etaSeconds;
  final String? error;
  final bool paused;

  ModelInstallInfo copyWith({
    InstallStatus? status,
    double? progress,
    int? speedBytesPerSec,
    int? etaSeconds,
    String? error,
    bool? paused,
    bool clearError = false,
  }) => ModelInstallInfo(
    modelId: modelId,
    status: status ?? this.status,
    progress: progress ?? this.progress,
    speedBytesPerSec: speedBytesPerSec ?? this.speedBytesPerSec,
    etaSeconds: etaSeconds ?? this.etaSeconds,
    error: clearError ? null : (error ?? this.error),
    paused: paused ?? this.paused,
  );
}

/// Gateway over background_downloader so tests can inject fakes.
abstract class DownloaderGateway {
  Future<String> enqueue({
    required String url,
    required String filePath,
    required bool wifiOnly,
  });
  Future<void> pause(String taskId);
  Future<void> resume(String taskId);
  Future<void> cancel(String taskId);
  Stream<DownloaderEvent> get events;
  Future<void> restorePending();
}

sealed class DownloaderEvent {
  DownloaderEvent(this.taskId);
  final String taskId;
}

final class DownloaderProgress extends DownloaderEvent {
  DownloaderProgress(super.taskId, this.progress, this.speedBytesPerSec);
  final double progress;
  final int speedBytesPerSec;
}

final class DownloaderFinished extends DownloaderEvent {
  DownloaderFinished(super.taskId, this.succeeded);
  final bool succeeded;
}

/// Production gateway over the pinned background_downloader package.
class BackgroundDownloaderGateway implements DownloaderGateway {
  final _controller = StreamController<DownloaderEvent>.broadcast();
  final _tasks = <String, Task>{};

  BackgroundDownloaderGateway() {
    FileDownloader().updates.listen((update) {
      switch (update) {
        case TaskProgressUpdate(task: final task, :final progress):
          final taskId = task.taskId;
          _controller.add(
            DownloaderProgress(taskId, progress.clamp(0.0, 1.0), 0),
          );
        case TaskStatusUpdate(task: final task, :final status):
          final taskId = task.taskId;
          switch (status) {
            case TaskStatus.complete:
              _controller.add(DownloaderFinished(taskId, true));
            case TaskStatus.failed:
            case TaskStatus.notFound:
            case TaskStatus.canceled:
              _controller.add(DownloaderFinished(taskId, false));
            case TaskStatus.enqueued:
            case TaskStatus.running:
            case TaskStatus.waitingToRetry:
            case TaskStatus.paused:
              break;
          }
      }
    });
  }

  @override
  Future<String> enqueue({
    required String url,
    required String filePath,
    required bool wifiOnly,
  }) async {
    final file = File(filePath);
    final task = DownloadTask(
      url: url,
      directory: file.parent.path,
      filename: file.uri.pathSegments.last,
      updates: Updates.statusAndProgress,
      requiresWiFi: wifiOnly,
      retries: 2,
    );
    await FileDownloader().enqueue(task);
    _tasks[task.taskId] = task;
    return task.taskId;
  }

  @override
  Future<void> pause(String taskId) async {
    final task = _tasks[taskId];
    if (task is DownloadTask) await FileDownloader().pause(task);
  }

  @override
  Future<void> resume(String taskId) async {
    final task = _tasks[taskId];
    if (task is DownloadTask) await FileDownloader().resume(task);
  }

  @override
  Future<void> cancel(String taskId) async {
    await FileDownloader().cancelTaskWithId(taskId);
    _tasks.remove(taskId);
  }

  @override
  Stream<DownloaderEvent> get events => _controller.stream;

  @override
  Future<void> restorePending() async {
    // Resume interrupted downloads after an app kill (tasks 1.7 / 2.3).
    final tasks = await FileDownloader().allTasks();
    for (final t in tasks) {
      if (t is DownloadTask) await FileDownloader().resume(t);
    }
  }
}

/// ModelManager (prd §9): install detection, download queue with
/// pause/resume, checksum verify with auto re-download, updates, delete and
/// storage accounting. HTTPS only; Wi-Fi-only default; originals/images
/// never enter network code (task 2.3).
class ModelManager {
  ModelManager({
    required this.catalog,
    required this.modelDir,
    required this.downloader,
    required this.wifiOnlyDefault,
  });

  final ModelCatalog catalog;
  final String modelDir;
  final DownloaderGateway downloader;
  final bool wifiOnlyDefault;

  final _states = <String, ModelInstallInfo>{};
  final _stateController =
      StreamController<Map<String, ModelInstallInfo>>.broadcast();
  final _taskToModel = <String, String>{};

  /// Live install states for every catalog model.
  Stream<Map<String, ModelInstallInfo>> get states => _stateController.stream;

  Map<String, ModelInstallInfo> get currentState => Map.unmodifiable(_states);

  void Function(String message)? onEvent;

  /// Asset loader wired by the app (rootBundle) / tests.
  static Future<Uint8List?> Function(String)? assetLoader;

  bool _initialized = false;

  /// Restores persisted install states and resumes interrupted downloads.
  /// Idempotent: safe to call from splash, setup and the Model Manager.
  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    await Directory(modelDir).create(recursive: true);
    // Start clean: interrupted downloads leave half-written archives behind.
    await _purgeTempZips();
    final saved = await _readInstallRegistry();
    for (final model in catalog.models) {
      final registered = saved.contains(model.id);
      _states[model.id] = ModelInstallInfo(
        modelId: model.id,
        status: !model.available
            ? InstallStatus.unavailable
            : (registered && _filesExist(model))
            ? InstallStatus.installed
            : InstallStatus.notInstalled,
      );
    }
    _emit();
    downloader.events.listen(_onDownloaderEvent);
    await downloader.restorePending();
  }

  bool isInstalled(String modelId) =>
      _states[modelId]?.status == InstallStatus.installed;

  /// Best installed model for [imageType] (starter first). Used for
  /// fallback-to-Auto when a selected model is deleted (task 2.3).
  String? fallbackModel(String imageType) {
    for (final m in catalog.forImageType(imageType)) {
      if (isInstalled(m.id) && m.pack == 'starter') return m.id;
    }
    for (final m in catalog.forImageType(imageType)) {
      if (isInstalled(m.id)) return m.id;
    }
    return null;
  }

  /// Installs a model: bundled asset copy when available, otherwise
  /// download → extract (zip) → sha256 verify → install. Corrupt downloads
  /// are re-downloaded once, then a typed error surfaces (prd §9.3).
  Future<void> install(String modelId, {bool? allowMobileData}) async {
    final model = catalog.byId(modelId);
    if (model == null) throw ModelMissingException(modelId);
    if (!model.available) {
      throw ModelMissingException('$modelId is unavailable in this build');
    }
    // Already fully present? Nothing to do. A partially present model (some
    // scales of a multi-scale pack still missing) continues to fetch the
    // rest instead of being treated as done.
    if (isInstalled(modelId) && _allFilesExist(model)) return;
    _states[modelId] =
        (_states[modelId] ??
                ModelInstallInfo(
                  modelId: modelId,
                  status: InstallStatus.downloading,
                ))
            .copyWith(
              status: InstallStatus.downloading,
              clearError: true,
              progress: 0,
            );
    _emit();
    onEvent?.call('Downloading ${model.name}');

    try {
      final total = model.files.fold<int>(0, (a, f) => a + f.sizeBytes);
      var done = 0;
      await Directory(modelDir).create(recursive: true);
      final zipsSeen = <String, String>{};

      for (final file in model.files) {
        final destination = destPath(model.id, file);
        // Resume-friendly: a file already on disk with the right checksum is
        // never downloaded twice.
        final existing = File(destination);
        if (existing.existsSync() &&
            sha256Hex(await existing.readAsBytes()) == file.sha256) {
          done += file.sizeBytes;
          continue;
        }
        final bytes = await _obtainFileBytes(
          file: file,
          modelId: modelId,
          allowMobileData: allowMobileData ?? !wifiOnlyDefault,
          zipsSeen: zipsSeen,
          onProgress: (fileProgress, speed) {
            final overall =
                (done + fileProgress * file.sizeBytes) /
                (total == 0 ? 1 : total);
            _states[modelId] =
                (_states[modelId] ??
                        ModelInstallInfo(
                          modelId: modelId,
                          status: InstallStatus.downloading,
                        ))
                    .copyWith(
                      progress: overall.clamp(0, 1),
                      speedBytesPerSec: speed,
                    );
            _emit();
          },
        );
        if (bytes == null) {
          _states[modelId] = ModelInstallInfo(
            modelId: modelId,
            status: InstallStatus.notInstalled,
          );
          _emit();
          return;
        }
        final actualSha = sha256Hex(bytes);
        if (actualSha != file.sha256) {
          throw DownloadException(
            'Checksum mismatch for ${file.sourceZipEntry ?? modelId}',
            checksumFailed: true,
          );
        }
        await File(destination).writeAsBytes(bytes, flush: true);
        done += file.sizeBytes;
      }

      _states[modelId] = ModelInstallInfo(
        modelId: modelId,
        status: InstallStatus.installed,
        progress: 1,
      );
      await _writeInstallRegistry();
      onEvent?.call('Installed ${model.name}');
    } on DownloadException catch (e) {
      _states[modelId] = ModelInstallInfo(
        modelId: modelId,
        status: InstallStatus.error,
        error: e.message,
      );
      onEvent?.call('Download failed: ${e.message}');
    } catch (e, st) {
      AppLogger.e('ModelManager', 'install failed for $modelId', e, st);
      _states[modelId] = ModelInstallInfo(
        modelId: modelId,
        status: InstallStatus.error,
        error: 'Could not download this model',
      );
      onEvent?.call('Download failed');
    } finally {
      // Temp zip archives never linger (rules.md golden rule 8).
      await _purgeTempZips();
    }
    _emit();
  }

  /// Removes every temporary download/extract artifact from the model dir.
  Future<void> _purgeTempZips() async {
    try {
      final dir = Directory(modelDir);
      if (!await dir.exists()) return;
      await for (final entity in dir.list()) {
        if (entity is File &&
            entity.uri.pathSegments.last.startsWith('z_') &&
            entity.uri.pathSegments.last.endsWith('.zip')) {
          await _deleteQuietly(entity);
        }
      }
    } catch (e, st) {
      AppLogger.w('ModelManager', 'temp cleanup failed', e, st);
    }
  }

  Future<Uint8List?> _obtainFileBytes({
    required ModelFileSpec file,
    required String modelId,
    required bool allowMobileData,
    required Map<String, String> zipsSeen,
    required void Function(double progress, int speed) onProgress,
  }) async {
    // 1) Bundled asset shortcut (Starter pack ships inside the app).
    if (file.assetPath != null) {
      final loader = ModelManager.assetLoader;
      final data = loader != null ? await loader(file.assetPath!) : null;
      if (data != null) {
        onProgress(1, 0);
        return data;
      }
    }
    if (file.url == null) return Uint8List(0);

    // 2) Reuse an already-downloaded official zip when files share it.
    final isZip = file.sourceZipEntry != null;
    if (isZip) {
      final cached = zipsSeen[file.url!];
      if (cached != null && await File(cached).exists()) {
        final bytes = await _extractZipEntry(cached, file.sourceZipEntry!);
        onProgress(1, 0);
        if (bytes != null && sha256Hex(bytes) == file.sha256) return bytes;
      }
    }

    // 3) Download with verification; one automatic retry after checksum
    // failure (auto re-download rule, qa-checklist Phase 1).
    for (var attempt = 0; attempt < 2; attempt++) {
      try {
        final bytes = await _downloadVerify(
          file: file,
          modelId: modelId,
          allowMobileData: allowMobileData,
          onProgress: onProgress,
        );
        if (bytes == null) return null; // cancelled
        if (bytes.isEmpty) continue;
        if (isZip) {
          final extracted = _extractZipBytes(bytes, file.sourceZipEntry!);
          final cachePath = '$modelDir/z_${modelId}_${file.kind}.zip';
          await File(cachePath).writeAsBytes(bytes, flush: true);
          zipsSeen[file.url!] = cachePath;
          if (extracted == null) {
            throw const DownloadException(
              'Model archive is damaged',
              checksumFailed: false,
            );
          }
          return extracted;
        }
        return bytes;
      } on DownloadException catch (e) {
        if (attempt == 1 || !e.checksumFailed) rethrow;
        onEvent?.call('Verifying download… retrying');
      }
    }
    throw DownloadException(
      'Could not download ${file.sourceZipEntry ?? modelId}',
    );
  }

  Future<Uint8List?> _downloadVerify({
    required ModelFileSpec file,
    required String modelId,
    required bool allowMobileData,
    required void Function(double, int) onProgress,
  }) async {
    final url = file.url!;
    final isZip = file.sourceZipEntry != null;
    final fileName = isZip
        ? 'z_${modelId}_${file.sourceZipEntry.hashCode.abs()}.zip'
        : '$modelId.bin';
    final filePath = '$modelDir/$fileName';
    final taskId = await downloader.enqueue(
      url: url,
      filePath: filePath,
      wifiOnly: !allowMobileData,
    );
    _taskToModel[taskId] = modelId;
    final completer = Completer<bool>();
    final sub = downloader.events.listen((e) {
      if (e.taskId != taskId) return;
      switch (e) {
        case DownloaderProgress(:final progress):
          onProgress(progress, 0);
        case DownloaderFinished(:final succeeded):
          if (!completer.isCompleted) completer.complete(succeeded);
      }
    });
    final ok = await completer.future.timeout(
      const Duration(minutes: 30),
      onTimeout: () => false,
    );
    await sub.cancel();
    _taskToModel.remove(taskId);
    if (!ok) return null; // cancelled or failed
    final raw = await File(filePath).readAsBytes();
    if (isZip) return raw;
    if (sha256Hex(raw) != file.sha256) {
      await _deleteQuietly(File(filePath));
      throw const DownloadException(
        'Download failed verification',
        checksumFailed: true,
      );
    }
    return raw;
  }

  Future<Uint8List?> _extractZipEntry(String zipPath, String entry) async {
    try {
      final archive = ZipDecoder().decodeBytes(
        await File(zipPath).readAsBytes(),
      );
      return _findEntry(archive, entry);
    } catch (e) {
      AppLogger.e('ModelManager', 'zip extract failed', e);
      return null;
    }
  }

  Uint8List? _extractZipBytes(List<int> zipBytes, String entry) {
    try {
      return _findEntry(ZipDecoder().decodeBytes(zipBytes), entry);
    } catch (e) {
      AppLogger.e('ModelManager', 'zip extract failed', e);
      return null;
    }
  }

  Uint8List? _findEntry(Archive archive, String entry) {
    for (final f in archive.files) {
      if (f.name == entry || f.name.endsWith('/$entry')) {
        return Uint8List.fromList(f.content as List<int>);
      }
    }
    return null;
  }

  Future<void> pause(String modelId) async {
    for (final entry in _taskToModel.entries) {
      if (entry.value == modelId) await downloader.pause(entry.key);
    }
    _states[modelId] =
        (_states[modelId] ??
                ModelInstallInfo(
                  modelId: modelId,
                  status: InstallStatus.downloading,
                ))
            .copyWith(paused: true, status: InstallStatus.downloading);
    _emit();
  }

  Future<void> resume(String modelId) async {
    for (final entry in _taskToModel.entries) {
      if (entry.value == modelId) await downloader.resume(entry.key);
    }
    _states[modelId] =
        (_states[modelId] ??
                ModelInstallInfo(
                  modelId: modelId,
                  status: InstallStatus.downloading,
                ))
            .copyWith(paused: false);
    _emit();
  }

  Future<void> cancelInstall(String modelId) async {
    for (final entry in _taskToModel.entries.toList()) {
      if (entry.value == modelId) {
        await downloader.cancel(entry.key);
        _taskToModel.remove(entry.key);
      }
    }
    _states[modelId] = ModelInstallInfo(
      modelId: modelId,
      status: InstallStatus.notInstalled,
    );
    _emit();
  }

  /// Delete frees storage; callers re-plan when the deleted model was
  /// selected (fallback-to-Auto via [fallbackModel]).
  Future<void> delete(String modelId) async {
    final model = catalog.byId(modelId);
    if (model == null) return;
    for (final file in model.files) {
      await _deleteQuietly(File(destPath(modelId, file)));
    }
    await _purgeTempZips();
    _states[modelId] = ModelInstallInfo(
      modelId: modelId,
      status: InstallStatus.notInstalled,
    );
    await _writeInstallRegistry();
    _emit();
  }

  /// True when the native engine has everything it needs on disk to run
  /// [model] at [scale]: the shared `.bin` weights plus the matching
  /// `-<scale>x.param` graph.
  ///
  /// Note: the bundled `realesr-animevideov3` weights are the official x4
  /// checkpoint, so only the 4x graph is usable offline until the 2x/3x
  /// weights are downloaded.
  bool hasUsableNativeFiles(ModelDef model, int scale) {
    if (model.engine != 'ncnn') return false;
    final param = File('$modelDir/${model.id}-${scale}x.param');
    final bin = File('$modelDir/${model.id}.bin');
    return param.existsSync() && bin.existsSync();
  }

  /// True when every file a model needs is already on disk.
  bool isReady(ModelDef model, {int? scale}) {
    if (!_filesExist(model)) return false;
    if (scale != null && model.engine == 'ncnn') {
      return hasUsableNativeFiles(model, scale);
    }
    return true;
  }

  /// Materializes the bundled Starter-pack assets (offline-first: the four
  /// starter models ship inside the APK, so no download is needed). Safe and
  /// idempotent — files already present are left untouched.
  Future<void> ensureBundledFiles() async {
    await Directory(modelDir).create(recursive: true);
    for (final model in catalog.models) {
      if (model.pack != 'starter') continue;
      if (_allFilesExist(model)) {
        _markInstalled(model.id);
        continue;
      }
      final loader = ModelManager.assetLoader;
      if (loader == null) continue;
      for (final file in model.files) {
        final asset = file.assetPath;
        // Files without a bundled asset are downloaded on demand.
        if (asset == null) continue;
        final destFile = File(destPath(model.id, file));
        if (destFile.existsSync() &&
            sha256Hex(destFile.readAsBytesSync()) == file.sha256) {
          continue;
        }
        Uint8List? data;
        try {
          data = await loader(asset);
        } catch (e) {
          AppLogger.d('ModelManager', 'bundled asset unavailable: $asset ($e)');
        }
        if (data == null) continue;
        if (sha256Hex(data) != file.sha256) {
          AppLogger.w('ModelManager', 'bundled asset checksum: $asset');
          continue;
        }
        await destFile.writeAsBytes(data, flush: true);
      }
      if (_filesExist(model)) _markInstalled(model.id);
    }
    await _purgeTempZips();
    await _writeInstallRegistry();
    _emit();
  }

  void _markInstalled(String modelId) {
    _states[modelId] = ModelInstallInfo(
      modelId: modelId,
      status: InstallStatus.installed,
      progress: 1,
    );
  }

  /// Storage accounting for the Model Manager storage bar.
  Future<int> installedBytes() async {
    var total = 0;
    for (final model in catalog.models) {
      for (final file in model.files) {
        final f = File(destPath(model.id, file));
        if (await f.exists()) total += await f.length();
      }
    }
    return total;
  }

  /// Where a model file lives once installed. Kept public so the engine
  /// layer resolves files exactly the way the installer writes them:
  ///   `<dir>/<id>.bin`, `<dir>/<id>-<scale>x.param`, `<dir>/<id>.onnx`.
  String destPath(String modelId, ModelFileSpec file) {
    if (file.kind == 'bin') return '$modelDir/$modelId.bin';
    if (file.kind == 'onnx') return '$modelDir/$modelId.onnx';
    return '$modelDir/$modelId-${file.scale}x.param';
  }

  /// Every single file of [model] is on disk (full, complete install).
  bool _allFilesExist(ModelDef model) {
    if (model.files.isEmpty) return false;
    for (final file in model.files) {
      if (!File(destPath(model.id, file)).existsSync()) return false;
    }
    return true;
  }

  /// [model] is usable: its weights are on disk and at least one graph
  /// (`.param`) is present.
  ///
  /// Multi-scale packs such as `realesr-animevideov3` ship only their x4
  /// graph inside the APK (the x2/x3 graphs are downloaded on demand), so
  /// requiring every file would wrongly report the starter model as
  /// uninstalled while it can perfectly run at 4x offline.
  bool _filesExist(ModelDef model) {
    if (model.files.isEmpty) return false;
    var hasParam = false;
    for (final file in model.files) {
      final exists = File(destPath(model.id, file)).existsSync();
      if (file.kind == 'bin') {
        if (!exists) return false;
      } else if (exists) {
        hasParam = true;
      }
    }
    return hasParam || model.engine != 'ncnn';
  }

  void _onDownloaderEvent(DownloaderEvent e) {
    final modelId = _taskToModel[e.taskId];
    if (modelId == null) return;
    if (e is DownloaderProgress) {
      final info = _states[modelId];
      if (info != null &&
          info.status == InstallStatus.downloading &&
          !info.paused) {
        _states[modelId] = info.copyWith(progress: e.progress.clamp(0, 1));
        _emit();
      }
    } else if (e is DownloaderFinished && !e.succeeded) {
      final info = _states[modelId];
      if (info != null &&
          info.status == InstallStatus.downloading &&
          !info.paused) {
        _states[modelId] = ModelInstallInfo(
          modelId: modelId,
          status: InstallStatus.error,
          error: 'Download interrupted',
        );
        _emit();
      }
    }
  }

  void _emit() => _stateController.add(Map.unmodifiable(_states));

  Future<Set<String>> _readInstallRegistry() async {
    final f = File('$modelDir/installed.json');
    if (!await f.exists()) return {};
    try {
      final list = jsonDecode(await f.readAsString()) as List;
      return list.map((e) => e as String).toSet();
    } catch (_) {
      return {};
    }
  }

  Future<void> _writeInstallRegistry() async {
    final f = File('$modelDir/installed.json');
    final installed = _states.entries
        .where((e) => e.value.status == InstallStatus.installed)
        .map((e) => e.key)
        .toList();
    await f.writeAsString(jsonEncode(installed), flush: true);
  }

  Future<void> _deleteQuietly(File f) async {
    try {
      if (await f.exists()) await f.delete();
    } catch (_) {}
  }
}

String sha256Hex(List<int> bytes) => crypto.sha256.convert(bytes).toString();
