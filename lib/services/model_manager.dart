/// RealScale-Pro — model management.
///
/// The registry ships as an asset (metadata only). Model weights are
/// downloaded on explicit user consent from the official Real-ESRGAN
/// release package, extracted, and SHA-256-verified against the values
/// recorded at integration time. A model that fails verification is
/// reported as corrupted — never used.
library;

import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive_io.dart';
import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter/services.dart' show rootBundle;
import 'package:http/http.dart' as http;

import '../domain/models.dart';
import 'storage_service.dart';

class ModelManager {
  ModelManager({required this.storage})
      : _status = <String, ModelInstallStatus>{},
        _downloads = <String, double>{};

  final StorageService storage;
  final Map<String, ModelInstallStatus> _status;
  final Map<String, double> _downloads;

  List<ModelInfo>? _registry;
  Directory? _cachedModelDir;

  /// Loads the registry asset.
  Future<List<ModelInfo>> registry() async {
    final cached = _registry;
    if (cached != null) {
      return cached;
    }
    final raw = await rootBundle.loadString('assets/models/registry.json');
    final json = jsonDecode(raw) as Map<String, dynamic>;
    _registry = (json['models'] as List)
        .map((e) => ModelInfo.fromJson(e as Map<String, Object?>))
        .toList();
    return _registry!;
  }

  /// Must be called once at app start (resolves the model directory).
  Future<void> init() async {
    _cachedModelDir = await storage.modelDirectory();
  }

  ModelInstallStatus statusOf(ModelInfo model) =>
      _status[model.id] ?? _installedStatus(model);

  ModelInstallStatus _installedStatus(ModelInfo model) {
    final dir = _cachedModelDir;
    if (dir == null) {
      return ModelInstallStatus.notInstalled;
    }
    if (_paramFile(dir, model).existsSync() &&
        _binFile(dir, model).existsSync()) {
      return ModelInstallStatus.installed;
    }
    return ModelInstallStatus.notInstalled;
  }

  double? downloadProgressOf(ModelInfo model) => _downloads[model.id];

  File _paramFile(Directory dir, ModelInfo m) =>
      File('${dir.path}/${m.paramFile}');
  File _binFile(Directory dir, ModelInfo m) =>
      File('${dir.path}/${m.binFile}');

  bool isInstalled(ModelInfo model) =>
      _installedStatus(model) == ModelInstallStatus.installed;

  /// Paths for the engine.
  ({String param, String bin}) modelPathsCached(ModelInfo m) {
    final dir = _cachedModelDir!;
    return (param: _paramFile(dir, m).path, bin: _binFile(dir, m).path);
  }

  Future<({String param, String bin})> modelPaths(ModelInfo m) async {
    final dir = await storage.modelDirectory();
    return (param: _paramFile(dir, m).path, bin: _binFile(dir, m).path);
  }

  /// Downloads, extracts and verifies a model.
  ///
  /// The package is streamed to a partial file on disk (never fully
  /// held in RAM) and interrupted downloads RESUME via HTTP Range
  /// requests when the server supports them. [onProgress] receives
  /// 0..1 fractions of the whole package. Throws
  /// [ModelVerifyException] when the SHA-256 of the extracted files
  /// does not match the registry.
  Future<void> download(
    ModelInfo model, {
    void Function(double fraction)? onProgress,
    http.Client? client,
  }) async {
    _status[model.id] = ModelInstallStatus.downloading;
    _downloads[model.id] = 0.0;
    try {
      final dir = await storage.modelDirectory();
      _cachedModelDir = dir;
      final partial = File('${dir.path}/.${model.id}.part');
      final zipClient = client ?? http.Client();
      try {
        await _downloadToFile(zipClient, Uri.parse(model.sourceUrl),
            model.id, partial, onProgress);

        // Decode from the file (streamed; only the two model files are
        // ever held in memory).
        final archive =
            ZipDecoder().decodeStream(InputFileStream(partial.path));
        final paramData = _extract(archive, model.paramFile);
        final binData = _extract(archive, model.binFile);
        _verify(model, paramData, binData);

        // Atomic install: write temp files, then rename into place.
        await _atomicWrite(_paramFile(dir, model), paramData);
        await _atomicWrite(_binFile(dir, model), binData);
        _status[model.id] = ModelInstallStatus.installed;
      } finally {
        if (partial.existsSync()) {
          try {
            await partial.delete();
          } catch (_) {
            // Best effort cleanup.
          }
        }
        if (client == null) {
          zipClient.close();
        }
      }
    } catch (e) {
      _status[model.id] = e is ModelVerifyException
          ? ModelInstallStatus.corrupted
          : ModelInstallStatus.notInstalled;
      rethrow;
    } finally {
      _downloads.remove(model.id);
    }
  }

  Future<void> _atomicWrite(File target, List<int> bytes) async {
    final tmp = File('${target.path}.tmp');
    await tmp.writeAsBytes(bytes, flush: true);
    await tmp.rename(target.path);
  }

  /// Streams the package to [partial], resuming an interrupted
  /// download when the server honors Range requests. Servers that
  /// ignore Range (plain 200) cause a clean restart.
  Future<void> _downloadToFile(
      http.Client client,
      Uri uri,
      String modelId,
      File partial,
      void Function(double)? onProgress) async {
    var have = partial.existsSync() ? partial.lengthSync() : 0;
    var request = http.Request('GET', uri);
    if (have > 0) {
      request.headers['Range'] = 'bytes=$have-';
    }
    var response = await client.send(request);
    IOSink sink;
    if (response.statusCode == 206) {
      // Resuming from `have` bytes.
      sink = partial.openWrite(mode: FileMode.append);
    } else {
      if (response.statusCode != 200) {
        throw ModelDownloadException(
            'download failed with HTTP ${response.statusCode}');
      }
      // Full response: restart (server ignored Range or fresh start).
      have = 0;
      sink = partial.openWrite(mode: FileMode.write);
    }
    final contentLength = response.contentLength ?? -1;
    final total = contentLength >= 0 ? have + contentLength : -1;
    var received = 0;
    try {
      await for (final chunk in response.stream) {
        sink.add(chunk);
        received += chunk.length;
        final fraction =
            total > 0 ? (have + received) / total : 0.0;
        _downloads[modelId] = fraction;
        onProgress?.call(fraction);
      }
      await sink.flush();
    } finally {
      await sink.close();
    }
  }

  Uint8List _extract(Archive archive, String fileName) {
    final entry =
        archive.findFile(fileName) ?? archive.findFile('models/$fileName');
    if (entry == null) {
      throw ModelDownloadException('$fileName not found in the package');
    }
    if (entry.isFile) {
      return entry.content as Uint8List? ?? Uint8List(0);
    }
    throw ModelDownloadException('$fileName is not a file');
  }

  void _verify(ModelInfo model, Uint8List param, Uint8List bin) {
    final paramHash = crypto.sha256.convert(param).toString();
    final binHash = crypto.sha256.convert(bin).toString();
    if (model.paramSha256 != null && model.paramSha256 != paramHash) {
      throw ModelVerifyException(
          '${model.paramFile}: SHA-256 mismatch (expected '
          '${model.paramSha256}, got $paramHash)');
    }
    if (model.binSha256 != null && model.binSha256 != binHash) {
      throw ModelVerifyException(
          '${model.binFile}: SHA-256 mismatch (expected '
          '${model.binSha256}, got $binHash)');
    }
  }

  Future<void> delete(ModelInfo model) async {
    final dir = await storage.modelDirectory();
    for (final f in [
      _paramFile(dir, model),
      _binFile(dir, model),
      File('${dir.path}/.${model.id}.part'),
    ]) {
      if (f.existsSync()) {
        await f.delete();
      }
    }
    _status[model.id] = ModelInstallStatus.notInstalled;
  }

  /// Verifies an already-installed model against the registry hashes.
  bool verifyInstalledSync(ModelInfo model) {
    final dir = _cachedModelDir;
    if (dir == null) {
      return false;
    }
    final param = _paramFile(dir, model);
    final bin = _binFile(dir, model);
    if (!param.existsSync() || !bin.existsSync()) {
      return false;
    }
    try {
      _verify(model, param.readAsBytesSync(), bin.readAsBytesSync());
      return true;
    } on ModelVerifyException {
      _status[model.id] = ModelInstallStatus.corrupted;
      return false;
    }
  }
}

class ModelDownloadException implements Exception {
  ModelDownloadException(this.message);
  final String message;
  @override
  String toString() => 'ModelDownloadException: $message';
}

class ModelVerifyException implements Exception {
  ModelVerifyException(this.message);
  final String message;
  @override
  String toString() => 'ModelVerifyException: $message';
}
