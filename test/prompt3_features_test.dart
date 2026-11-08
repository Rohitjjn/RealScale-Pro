/// Tests for Prompt 3 features: resumable/streamed model downloads,
/// gallery export + share plumbing, StatFs-backed free space, GPU
/// settings and the JobResult honesty fields.
library;

import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:realscale_pro/domain/models.dart';
import 'package:realscale_pro/services/model_manager.dart';
import 'package:realscale_pro/services/settings_service.dart';
import 'package:realscale_pro/services/storage_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory tmp;
  late StorageTestProxy storage;

  setUp(() async {
    tmp = await Directory.systemTemp.createTemp('rs_prompt3_test');
    storage = StorageTestProxy(
        appDocuments: tmp, appSupport: Directory('${tmp.path}/support'));
  });

  tearDown(() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('realscale/media'), null);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('realscale/device'), null);
    try {
      await tmp.delete(recursive: true);
    } catch (_) {}
  });

  ModelInfo fakeModel(String id, {String? binSha, String? paramSha}) =>
      ModelInfo(
        id: id,
        displayName: 'Test $id',
        family: 'SRVGGNet compact',
        scale: 4,
        license: 'BSD-3-Clause',
        licenseUrl: 'https://example.com/license',
        sourceUrl: 'https://example.com/models.zip',
        paramFile: 'test.param',
        binFile: 'test.bin',
        approxSizeMb: 1,
        minRamMb: 500,
        description: 'test model',
        tags: const ['test'],
        paramSha256: paramSha,
        binSha256: binSha,
        checksumStatus: 'test-fixture',
      );

  group('streamed + resumable downloads', () {
    test('streams to disk, verifies SHA-256, installs atomically', () async {
      final (zipBytes, paramSha, binSha) =
          buildZip('param-bytes-here', 'bin-bytes-here');
      final manager = ModelManager(storage: storage);
      final model = fakeModel('m1', binSha: binSha, paramSha: paramSha);
      var lastFraction = -1.0;
      await manager.download(model,
          client: _fullZipClient(zipBytes),
          onProgress: (f) => lastFraction = f);
      expect(manager.statusOf(model), ModelInstallStatus.installed);
      expect(
          File('${tmp.path}/support/models/test.param').readAsStringSync(),
          'param-bytes-here');
      expect(File('${tmp.path}/support/models/test.bin').readAsStringSync(),
          'bin-bytes-here');
      expect(lastFraction, 1.0);
      expect(File('${tmp.path}/support/models/.m1.part').existsSync(), isFalse,
          reason: 'partial files are cleaned after a successful install');
    });

    test('a partial file triggers an HTTP Range resume', () async {
      final (zipBytes, paramSha, binSha) = buildZip('p', 'b');
      final manager = ModelManager(storage: storage);
      final model = fakeModel('m2', binSha: binSha, paramSha: paramSha);
      final dir = Directory('${tmp.path}/support/models')
        ..createSync(recursive: true);
      File('${dir.path}/.m2.part').writeAsBytesSync(zipBytes.sublist(0, 5));

      var sawRangeHeader = false;
      final client = MockClient((request) async {
        final range = request.headers['Range'];
        if (range != null) {
          sawRangeHeader = true;
          expect(range, 'bytes=5-');
          return http.Response.bytes(zipBytes.sublist(5), 206,
              headers: {'content-length': '${zipBytes.length - 5}'});
        }
        return http.Response.bytes(zipBytes, 200,
            headers: {'content-length': '${zipBytes.length}'});
      });
      await manager.download(model, client: client);
      expect(sawRangeHeader, isTrue);
      expect(manager.statusOf(model), ModelInstallStatus.installed);
      expect(File('${dir.path}/test.param').readAsStringSync(), 'p');
    });

    test('a server ignoring Range restarts the download cleanly', () async {
      final (zipBytes, paramSha, binSha) = buildZip('p', 'b');
      final manager = ModelManager(storage: storage);
      final model = fakeModel('m3', binSha: binSha, paramSha: paramSha);
      final dir = Directory('${tmp.path}/support/models')
        ..createSync(recursive: true);
      File('${dir.path}/.m3.part').writeAsBytesSync([1, 2, 3, 4, 5]);
      final client = MockClient((request) async =>
          http.Response.bytes(zipBytes, 200,
              headers: {'content-length': '${zipBytes.length}'}));
      await manager.download(model, client: client);
      expect(manager.statusOf(model), ModelInstallStatus.installed);
      expect(File('${dir.path}/test.param').readAsStringSync(), 'p');
    });

    test('checksum mismatch marks the model corrupted, never installed',
        () async {
      final (zipBytes, _, _) = buildZip('tampered', 'tampered');
      final manager = ModelManager(storage: storage);
      final model = fakeModel('m4', binSha: 'dead', paramSha: 'dead');
      await expectLater(
        manager.download(model, client: _fullZipClient(zipBytes)),
        throwsA(isA<ModelVerifyException>()),
      );
      expect(manager.statusOf(model), ModelInstallStatus.corrupted);
      expect(File('${tmp.path}/support/models/test.param').existsSync(), isFalse,
          reason: 'a failed verify must not install files');
    });

    test('HTTP errors surface as ModelDownloadException', () async {
      final manager = ModelManager(storage: storage);
      final model = fakeModel('m5');
      final client = MockClient(
          (request) async => http.Response.bytes(const [], 503));
      await expectLater(
        manager.download(model, client: client),
        throwsA(isA<ModelDownloadException>()),
      );
      expect(manager.statusOf(model), ModelInstallStatus.notInstalled);
    });
  });

  group('gallery export + share (Android channel)', () {
    test('exports via the media channel and shares the content URI',
        () async {
      final file = File('${tmp.path}/out.png')..writeAsBytesSync([1, 2, 3]);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('realscale/media'),
              (call) async {
        if (call.method == 'saveToGallery') {
          return {
            'contentUri': 'content://media/external/images/42',
            'path': '/storage/emulated/0/Pictures/RealScale-Pro/out.png',
          };
        }
        if (call.method == 'shareFile') {
          return null;
        }
        return null;
      });
      final androidStorage = StorageTestProxy(
          appDocuments: tmp, appSupport: Directory('${tmp.path}/support'))
        ..isAndroidOverride = true;
      final galleryPath = await androidStorage.exportToGallery(file, 'image/png');
      expect(galleryPath, contains('Pictures/RealScale-Pro'));
      expect(androidStorage.lastGalleryContentUri,
          startsWith('content://'));
      expect(await androidStorage.shareResult(mimeType: 'image/png'), isTrue);
    });

    test('share without a prior export reports unavailable honestly',
        () async {
      final androidStorage = StorageTestProxy(
          appDocuments: tmp, appSupport: Directory('${tmp.path}/support2'))
        ..isAndroidOverride = true;
      expect(await androidStorage.shareResult(mimeType: 'image/png'), isFalse);
    });

    test('non-Android platforms keep file-only storage', () async {
      final file = File('${tmp.path}/out.png')..writeAsBytesSync([1]);
      expect(await storage.exportToGallery(file, 'image/png'), isNull);
    });

    test('channel failures surface as StorageException, file stays intact',
        () async {
      final file = File('${tmp.path}/out.png')..writeAsBytesSync([1, 2, 3]);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('realscale/media'),
              (call) async {
        throw PlatformException(code: 'save_error', message: 'disk full');
      });
      final androidStorage = StorageTestProxy(
          appDocuments: tmp, appSupport: Directory('${tmp.path}/support3'))
        ..isAndroidOverride = true;
      await expectLater(
        androidStorage.exportToGallery(file, 'image/png'),
        throwsA(isA<StorageException>()),
      );
      expect(file.existsSync(), isTrue,
          reason: 'the app-storage copy is never removed');
    });
  });

  group('device free space', () {
    test('Android reads StatFs-backed bytes from the device channel',
        () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('realscale/device'),
              (call) async {
        return {
          'totalMemoryMb': 3900,
          'availableMemoryMb': 1800,
          'androidVersion': 'Android 13 (API 33)',
          'cpuAbi': 'arm64-v8a',
          'supportsVulkan': true,
          'freeStorageBytes': 12345678,
        };
      });
      final androidStorage = StorageTestProxy(
          appDocuments: tmp, appSupport: Directory('${tmp.path}/support4'))
        ..isAndroidOverride = true;
      expect(await androidStorage.freeSpaceBytes(), 12345678);
    });
  });

  group('temp cleanup + settings', () {
    test('cleanTempFiles removes leftover .tmp outputs only', () async {
      final dir = await storage.outputDirectory();
      File('${dir.path}/good.png').writeAsBytesSync([1]);
      File('${dir.path}/half.png.tmp').writeAsBytesSync([2]);
      await storage.cleanTempFiles();
      expect(File('${dir.path}/good.png').existsSync(), isTrue);
      expect(File('${dir.path}/half.png.tmp').existsSync(), isFalse);
    });

    test('GPU preference defaults off and persists', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final settings = SettingsService(prefs);
      expect(settings.preferGpu, isFalse,
          reason: 'the verified CPU backend must be the default');
      settings.preferGpu = true;
      expect(settings.preferGpu, isTrue);
    });
  });

  test('JobResult honesty fields have honest defaults', () {
    const result = JobResult(
      outputPath: '/x.png',
      inputWidth: 1,
      inputHeight: 1,
      outputWidth: 4,
      outputHeight: 4,
      inputFileBytes: 1,
      outputFileBytes: 1,
      duration: Duration.zero,
      modelId: 'm',
      mode: ProcessingMode.upscale,
      outputFormat: OutputFormat.png,
      attempt: 1,
    );
    expect(result.backend, 'ncnn-cpu');
    expect(result.savedToGallery, isFalse);
    expect(result.galleryNote, '');
  });
}

/// Builds an in-memory model package zip plus the REAL SHA-256 of both
/// files (the same values the registry records and ModelManager checks).
(List<int>, String, String) buildZip(String param, String bin) {
  final bytes = const ZipWriter().encode({'test.param': param, 'test.bin': bin});
  return (
    bytes,
    crypto.sha256.convert(utf8.encode(param)).toString(),
    crypto.sha256.convert(utf8.encode(bin)).toString(),
  );
}

http.Client _fullZipClient(List<int> zipBytes) => MockClient(
    (request) async => http.Response.bytes(zipBytes, 200,
        headers: {'content-length': '${zipBytes.length}'}));

/// Minimal ZIP writer (store method, CRC-32) so the tests do not depend
/// on the archive package's writer matching the reader byte-for-byte.
class ZipWriter {
  const ZipWriter();
  List<int> encode(Map<String, String> files) {
    final out = <int>[];
    final central = <int>[];
    var offset = 0;
    for (final entry in files.entries) {
      final nameBytes = entry.key.codeUnits;
      final data = entry.value.codeUnits;
      final crc = _crc32(data);
      out.addAll(_le32(0x04034b50));
      out.addAll(_le16(20));
      out.addAll(_le16(0));
      out.addAll(_le16(0));
      out.addAll(_le16(0));
      out.addAll(_le16(0));
      out.addAll(_le32(crc));
      out.addAll(_le32(data.length));
      out.addAll(_le32(data.length));
      out.addAll(_le16(nameBytes.length));
      out.addAll(_le16(0));
      out.addAll(nameBytes);
      out.addAll(data);

      central.addAll(_le32(0x02014b50));
      central.addAll(_le16(20));
      central.addAll(_le16(20));
      central.addAll(_le16(0));
      central.addAll(_le16(0));
      central.addAll(_le16(0));
      central.addAll(_le16(0));
      central.addAll(_le32(crc));
      central.addAll(_le32(data.length));
      central.addAll(_le32(data.length));
      central.addAll(_le16(nameBytes.length));
      central.addAll(_le16(0));
      central.addAll(_le16(0));
      central.addAll(_le16(0));
      central.addAll(_le16(0));
      central.addAll(_le32(0));
      central.addAll(_le32(offset));
      central.addAll(nameBytes);
      offset += 30 + nameBytes.length + data.length;
    }
    final end = <int>[...out, ...central];
    end.addAll(_le32(0x06054b50));
    end.addAll(_le16(0));
    end.addAll(_le16(0));
    end.addAll(_le16(files.length));
    end.addAll(_le16(files.length));
    end.addAll(_le32(central.length));
    end.addAll(_le32(out.length));
    end.addAll(_le16(0));
    return end;
  }
}

List<int> _le16(int v) => [v & 0xff, (v >> 8) & 0xff];
List<int> _le32(int v) =>
    [v & 0xff, (v >> 8) & 0xff, (v >> 16) & 0xff, (v >> 24) & 0xff];

int _crc32(List<int> data) {
  var crc = 0xffffffff;
  for (final b in data) {
    crc ^= b;
    for (var i = 0; i < 8; i++) {
      crc = (crc >> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return crc ^ 0xffffffff;
}

/// StorageService with injectable directories for tests.
class StorageTestProxy extends StorageService {
  StorageTestProxy({required this.appDocuments, required this.appSupport})
      : super(enableGalleryExport: true);
  final Directory appDocuments;
  final Directory appSupport;

  @override
  Future<Directory> outputDirectory() async {
    if (!appDocuments.existsSync()) {
      appDocuments.createSync(recursive: true);
    }
    return appDocuments;
  }

  @override
  Future<Directory> modelDirectory() async {
    final dir = Directory('${appSupport.path}/models');
    if (!dir.existsSync()) {
      dir.createSync(recursive: true);
    }
    return dir;
  }
}
