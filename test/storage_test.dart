/// Storage tests: unique naming, atomic writes, original preservation.
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:realscale_pro/services/storage_service.dart';

class _FakePathProvider extends PathProviderPlatform {
  _FakePathProvider(this.root);
  final Directory root;

  @override
  Future<String?> getApplicationDocumentsPath() async =>
      '${root.path}/docs';

  @override
  Future<String?> getTemporaryPath() async => '${root.path}/tmp';
}

void main() {
  late Directory tmp;
  late StorageService storage;

  setUp(() {
    tmp = Directory.systemTemp.createTempSync('rs_storage_test');
    PathProviderPlatform.instance = _FakePathProvider(tmp);
    storage = StorageService();
  });

  tearDown(() {
    tmp.deleteSync(recursive: true);
  });

  test('output directory is created under RealScale-Pro', () async {
    final dir = await storage.outputDirectory();
    expect(dir.path, contains('RealScale-Pro'));
    expect(dir.existsSync(), isTrue);
  });

  test('duplicate names get numbered suffixes, never overwritten',
      () async {
    final a = await storage.writeOutput('photo_upscale_4x', 'png',
        List.filled(10, 1));
    final b = await storage.writeOutput('photo_upscale_4x', 'png',
        List.filled(10, 2));
    expect(a.path, isNot(b.path));
    expect(b.path, contains('(2)'));
    // Content preserved on both.
    expect(a.readAsBytesSync()[0], 1);
    expect(b.readAsBytesSync()[0], 2);
    // Third write increments again.
    final c = await storage.writeOutput('photo_upscale_4x', 'png',
        List.filled(10, 3));
    expect(c.path, contains('(3)'));
  });

  test('no .tmp leftovers after successful writes', () async {
    await storage.writeOutput('clean', 'png', [1, 2, 3]);
    final dir = await storage.outputDirectory();
    final tmpFiles =
        dir.listSync().where((e) => e.path.endsWith('.tmp')).toList();
    expect(tmpFiles, isEmpty);
  });

  test('extension is normalized (dot or no dot)', () async {
    final withDot = await storage.uniqueOutputPath('name', '.png');
    final noDot = await storage.uniqueOutputPath('name2', 'png');
    expect(withDot.path, endsWith('name.png'));
    expect(noDot.path, endsWith('name2.png'));
  });
}
