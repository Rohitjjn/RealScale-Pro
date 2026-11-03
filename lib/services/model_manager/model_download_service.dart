import 'dart:io';

import 'package:background_downloader/background_downloader.dart';
import 'package:crypto/crypto.dart';

/// One remotely hosted model file.
class ModelDownload {
  const ModelDownload({
    required this.id,
    required this.url,
    required this.filename,
    required this.sha256,
  });

  final String id;
  final String url;
  final String filename;
  final String sha256;
}

/// Resumable, background model downloads with mandatory checksum verification.
class ModelDownloadService {
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

  Transfer? transferFor(String id) => _active[id];

  Future<File> download(
    ModelDownload model, {
    bool wifiOnly = true,
    int checksumRetries = 1,
  }) async {
    final DownloadTask task = DownloadTask(
      taskId: model.id,
      url: model.url,
      filename: model.filename,
      directory: 'models',
      baseDirectory: BaseDirectory.applicationSupport,
      updates: Updates.statusAndProgress,
      retries: 3,
      allowPause: true,
      requiresWiFi: wifiOnly,
      transferHints: const <TransferHint>{
        TransferHint.userInitiated,
        TransferHint.largeFile,
      },
    );
    final Transfer transfer = await FileDownloader().transfers.getOrStart(task);
    _active[model.id] = transfer;
    final File file = await transfer.file;
    if (await _checksum(file) != model.sha256.toLowerCase()) {
      await file.delete();
      _active.remove(model.id);
      if (checksumRetries > 0) {
        return download(
          model,
          wifiOnly: wifiOnly,
          checksumRetries: checksumRetries - 1,
        );
      }
      throw const FileSystemException('model_checksum_failed');
    }
    return file;
  }

  Future<bool> pause(String id) async => await _active[id]?.pause() ?? false;
  Future<bool> resume(String id) async => await _active[id]?.resume() ?? false;
  Future<bool> cancel(String id) async => await _active[id]?.cancel() ?? false;

  Future<String> _checksum(File file) async =>
      (await sha256.bind(file.openRead()).first).toString();
}
