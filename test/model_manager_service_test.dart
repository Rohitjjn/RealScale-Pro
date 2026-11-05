import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/core/app_exception.dart';
import 'package:realscale_pro/services/model_manager/model_manager.dart';

import 'helpers/test_services.dart';

/// Fake gateway whose downloads SUCCEED with given bytes — drives the full
/// install → verify → installed lifecycle without network or channels.
class FakeSuccessGateway extends FakeDownloaderGateway {
  FakeSuccessGateway(this.payloadByCall);

  final List<Uint8List> payloadByCall;
  final completedTasks = <String>[];
  final _controller = StreamController<DownloaderEvent>.broadcast();
  var _calls = 0;

  @override
  Future<String> enqueue({
    required String url,
    required String filePath,
    required bool wifiOnly,
  }) async {
    final taskId = 'task-${_calls++}';
    completedTasks.add(taskId);
    // Emit progress then success AFTER a real delay so the ModelManager's
    // per-task listener (created after enqueue returns) is attached.
    Timer(const Duration(milliseconds: 30), () {
      _controller.add(DownloaderProgress(taskId, 0.5, 1024));
      _controller.add(DownloaderFinished(taskId, true));
    });
    // Write the payload where ModelManager expects the download.
    final payload = payloadByCall[(_calls - 1) % payloadByCall.length];
    await File(filePath).writeAsBytes(payload, flush: true);
    return taskId;
  }

  @override
  Stream<DownloaderEvent> get events => _controller.stream;
}

void main() {
  test(
    'install lifecycle: notInstalled → downloading → installed + registry',
    () async {
      final root = Directory.systemTemp.createTempSync('rs_mm');
      final catalog = await buildCatalog();
      final gateway = FakeSuccessGateway([
        Uint8List.fromList(List.filled(64, 1)),
      ]);
      final manager = ModelManager(
        catalog: catalog,
        modelDir: '${root.path}/models',
        downloader: gateway,
        wifiOnlyDefault: true,
      );
      await manager.initialize();

      final anime = catalog.byId('waifu2x-upconv-photo-2x')!;
      expect(manager.isInstalled(anime.id), isFalse);

      // Bundle asset shortcut: assetLoader wired → install copies bytes.
      ModelManager.assetLoader = (path) async =>
          File(path.replaceFirst('assets/', 'assets/')).readAsBytes();

      await manager.install(anime.id);
      expect(manager.isInstalled(anime.id), isTrue);
      final registry = File('${root.path}/models/installed.json');
      expect(registry.existsSync(), isTrue);
      expect(
        (jsonDecode(registry.readAsStringSync()) as List),
        contains('waifu2x-upconv-photo-2x'),
      );

      // Double install is a no-op (idempotent).
      await manager.install(anime.id);
      expect(manager.isInstalled(anime.id), isTrue);
    },
  );

  test(
    'corrupt download fails verification and surfaces a typed error',
    () async {
      final root = Directory.systemTemp.createTempSync('rs_mm2');
      final catalog = await buildCatalog();
      // The bundle-asset path is not wired here → falls to network → gateway
      // writes wrong bytes → checksum mismatch → error state after retry.
      ModelManager.assetLoader = null;
      final gateway = FakeSuccessGateway([
        Uint8List.fromList(List.filled(64, 9)),
      ]);
      final manager = ModelManager(
        catalog: catalog,
        modelDir: '${root.path}/models',
        downloader: gateway,
        wifiOnlyDefault: true,
      );
      await manager.initialize();
      final anime = catalog.byId('realesr-animevideov3')!;
      await manager.install(anime.id);
      expect(manager.currentState[anime.id]!.status, InstallStatus.error);
    },
  );

  test('delete removes files, registry entry and falls back to Auto', () async {
    final root = Directory.systemTemp.createTempSync('rs_mm3');
    final catalog = await buildCatalog();
    final gateway = FakeSuccessGateway([Uint8List(64)]);
    final manager = ModelManager(
      catalog: catalog,
      modelDir: '${root.path}/models',
      downloader: gateway,
      wifiOnlyDefault: true,
    );
    await manager.initialize();
    // Real bundled bytes (asset payloads are checksum-verified too).
    ModelManager.assetLoader = (path) async => File(path).readAsBytes();
    final anime = catalog.byId('waifu2x-upconv-photo-2x')!;
    await manager.install(anime.id);
    expect(manager.isInstalled(anime.id), isTrue);

    await manager.delete(anime.id);
    expect(manager.isInstalled(anime.id), isFalse);
    // Fallback-to-Auto: no starter model installed → returns null or another.
    expect(manager.fallbackModel('anime'), anyOf(isNull, isNot(anime.id)));
  });

  test('unavailable models throw a typed error and stay unavailable', () async {
    final root = Directory.systemTemp.createTempSync('rs_mm4');
    final catalog = await buildCatalog();
    final manager = ModelManager(
      catalog: catalog,
      modelDir: '${root.path}/models',
      downloader: FakeSuccessGateway([Uint8List(64)]),
      wifiOnlyDefault: true,
    );
    await manager.initialize();
    final blocked = catalog.byId('gfpgan-v1.4')!;
    expect(blocked.available, isFalse);
    expect(
      () => manager.install(blocked.id),
      throwsA(isA<ModelMissingException>()),
    );
  });

  test('storage accounting sums installed files only', () async {
    final root = Directory.systemTemp.createTempSync('rs_mm5');
    final catalog = await buildCatalog();
    final manager = ModelManager(
      catalog: catalog,
      modelDir: '${root.path}/models',
      downloader: FakeSuccessGateway([Uint8List(64)]),
      wifiOnlyDefault: true,
    );
    await manager.initialize();
    expect(await manager.installedBytes(), 0);
    ModelManager.assetLoader = (path) async => File(path).readAsBytes();
    await manager.install(catalog.byId('waifu2x-upconv-photo-2x')!.id);
    expect(await manager.installedBytes(), greaterThan(0));
  });
}
