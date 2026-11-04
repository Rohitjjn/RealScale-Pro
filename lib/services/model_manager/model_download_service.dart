import 'dart:io';

import 'package:background_downloader/background_downloader.dart';
import 'package:crypto/crypto.dart';

import 'model_catalog.dart';
import 'model_manager.dart';

/// Resumable background downloads with mandatory checksum verification.
class ModelDownloadService implements ModelTransferGateway {
  final Map<String, Transfer> _active = <String, Transfer>{};

  Future<void> initialize() async {
    await FileDownloader().start(autoCleanDatabase: true);
    final Iterable<TaskRecord> records = await FileDownloader().database
        .allRecords();
    for (final TaskRecord record in records) {
      if (record.status.isNotFinalState) {
        _active[record.task.taskId] = await FileDownloader().transfers
            .getOrStart(record.task);
      }
    }
  }

  Transfer? transferFor(String taskId) => _active[taskId];

  @override
  Future<File> downloadFile(
    ModelDefinition model,
    ModelFileDefinition file, {
    required void Function(double progress) onProgress,
  }) async {
    final String? url = file.url;
    if (url == null || Uri.tryParse(url)?.scheme != 'https') {
      throw const FileSystemException('model_url_unavailable');
    }
    final String taskId = '${model.id}-${model.version}-${file.role}';
    final DownloadTask task = DownloadTask(
      taskId: taskId,
      url: url,
      filename: file.filename,
      directory: 'models/downloads/${model.id}/${model.version}',
      baseDirectory: BaseDirectory.applicationSupport,
      updates: Updates.statusAndProgress,
      retries: 3,
      allowPause: true,
      requiresWiFi: true,
      transferHints: const <TransferHint>{
        TransferHint.userInitiated,
        TransferHint.largeFile,
      },
    );
    onProgress(0);
    final Transfer transfer = await FileDownloader().transfers.getOrStart(task);
    _active[taskId] = transfer;
    final File downloaded = await transfer.file;
    if (!await _matches(downloaded, file)) {
      await downloaded.delete();
      _active.remove(taskId);
      throw const FileSystemException('model_checksum_failed');
    }
    onProgress(1);
    return downloaded;
  }

  @override
  Future<void> pauseModel(String modelId) async {
    for (final MapEntry<String, Transfer> entry in _forModel(modelId)) {
      await entry.value.pause();
    }
  }

  @override
  Future<void> resumeModel(String modelId) async {
    for (final MapEntry<String, Transfer> entry in _forModel(modelId)) {
      await entry.value.resume();
    }
  }

  @override
  Future<void> cancelModel(String modelId) async {
    final List<MapEntry<String, Transfer>> transfers = _forModel(modelId);
    for (final MapEntry<String, Transfer> entry in transfers) {
      await entry.value.cancel();
      _active.remove(entry.key);
    }
  }

  List<MapEntry<String, Transfer>> _forModel(String modelId) => _active.entries
      .where(
        (MapEntry<String, Transfer> entry) => entry.key.startsWith('$modelId-'),
      )
      .toList(growable: false);

  Future<bool> _matches(File file, ModelFileDefinition expected) async {
    if (!await file.exists() || await file.length() != expected.sizeBytes) {
      return false;
    }
    return (await sha256.bind(file.openRead()).first).toString() ==
        expected.sha256;
  }
}
