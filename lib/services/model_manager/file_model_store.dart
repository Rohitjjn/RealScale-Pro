import 'dart:io';

import 'package:crypto/crypto.dart';

import 'model_catalog.dart';
import 'model_manager.dart';

/// Transactional file store for downloaded model pairs.
class FileModelStore implements ModelInstallStore {
  FileModelStore(this.root, {this.freeBytesReader});

  final Directory root;
  final Future<int?> Function()? freeBytesReader;

  @override
  Future<String?> installedVersion(String modelId) async {
    final File marker = File(_path(modelId, '.version'));
    if (!await marker.exists()) return null;
    final String version = (await marker.readAsString()).trim();
    return version.isEmpty ? null : version;
  }

  @override
  Future<void> install(
    ModelDefinition model,
    List<File> downloadedFiles,
  ) async {
    if (downloadedFiles.length != model.files.length) {
      throw const FileSystemException('model_install_incomplete');
    }
    await root.create(recursive: true);
    final Directory staging = Directory(_path('${model.id}.installing', ''));
    if (await staging.exists()) await staging.delete(recursive: true);
    await staging.create(recursive: true);

    try {
      for (int index = 0; index < model.files.length; index += 1) {
        final ModelFileDefinition expected = model.files[index];
        final File source = downloadedFiles[index];
        if (!await _matches(source, expected)) {
          throw const FileSystemException('model_checksum_failed');
        }
        await source.copy(
          '${staging.path}${Platform.pathSeparator}${expected.filename}',
        );
      }
      await File(
        '${staging.path}${Platform.pathSeparator}.version',
      ).writeAsString(model.version, flush: true);

      final Directory installed = Directory(_path(model.id, ''));
      final Directory backup = Directory(_path('${model.id}.previous', ''));
      if (await backup.exists()) await backup.delete(recursive: true);
      if (await installed.exists()) await installed.rename(backup.path);
      try {
        await staging.rename(installed.path);
        if (await backup.exists()) await backup.delete(recursive: true);
      } on Object {
        if (await backup.exists() && !await installed.exists()) {
          await backup.rename(installed.path);
        }
        rethrow;
      }
    } finally {
      if (await staging.exists()) await staging.delete(recursive: true);
    }
  }

  @override
  Future<void> delete(String modelId) async {
    final Directory installed = Directory(_path(modelId, ''));
    if (await installed.exists()) await installed.delete(recursive: true);
  }

  @override
  Future<ModelStorageSummary> summary() async {
    int used = 0;
    if (await root.exists()) {
      await for (final FileSystemEntity entity in root.list(recursive: true)) {
        if (entity is File) used += await entity.length();
      }
    }
    return ModelStorageSummary(
      usedBytes: used,
      freeBytes: await freeBytesReader?.call(),
    );
  }

  String _path(String modelId, String filename) =>
      '${root.path}${Platform.pathSeparator}$modelId'
      '${filename.isEmpty ? '' : '${Platform.pathSeparator}$filename'}';

  Future<bool> _matches(File file, ModelFileDefinition expected) async {
    if (!await file.exists() || await file.length() != expected.sizeBytes) {
      return false;
    }
    final String checksum = (await sha256.bind(file.openRead()).first)
        .toString();
    return checksum == expected.sha256;
  }
}
