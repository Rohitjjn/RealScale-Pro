import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/services/model_manager/file_model_store.dart';
import 'package:realscale_pro/services/model_manager/model_catalog.dart';
import 'package:realscale_pro/services/model_manager/model_manager.dart';

void main() {
  late Directory temporary;
  late Directory downloads;
  late FileModelStore store;
  late ModelDefinition model;

  setUp(() async {
    temporary = await Directory.systemTemp.createTemp('model-store-test');
    downloads = Directory('${temporary.path}/downloads')..createSync();
    store = FileModelStore(
      Directory('${temporary.path}/installed'),
      freeBytesReader: () async => 1000,
    );
    model = ModelCatalog.parse(jsonEncode(_catalogJson())).models.single;
  });

  tearDown(() async {
    if (await temporary.exists()) await temporary.delete(recursive: true);
  });

  test('installs verified files and records exact version', () async {
    final File downloaded = File('${downloads.path}/model.bin');
    await downloaded.writeAsBytes(<int>[1, 2, 3]);

    await store.install(model, <File>[downloaded]);

    expect(await store.installedVersion(model.id), '1.0.0');
    final ModelStorageSummary summary = await store.summary();
    expect(summary.usedBytes, greaterThanOrEqualTo(8));
    expect(summary.freeBytes, 1000);
  });

  test('rejects a corrupt file without replacing installed model', () async {
    final File valid = File('${downloads.path}/valid.bin');
    await valid.writeAsBytes(<int>[1, 2, 3]);
    await store.install(model, <File>[valid]);
    final File corrupt = File('${downloads.path}/corrupt.bin');
    await corrupt.writeAsBytes(<int>[3, 2, 1]);

    expect(
      () => store.install(model, <File>[corrupt]),
      throwsA(isA<FileSystemException>()),
    );
    expect(await store.installedVersion(model.id), '1.0.0');
  });

  test('deletes installed model files', () async {
    final File downloaded = File('${downloads.path}/model.bin');
    await downloaded.writeAsBytes(<int>[1, 2, 3]);
    await store.install(model, <File>[downloaded]);

    await store.delete(model.id);

    expect(await store.installedVersion(model.id), isNull);
  });
}

Map<String, dynamic> _catalogJson() => <String, dynamic>{
  'version': 1,
  'models': <Map<String, dynamic>>[
    <String, dynamic>{
      'id': 'test-model',
      'version': '1.0.0',
      'name': 'Test model',
      'engine': 'ncnn',
      'pack': 'starter',
      'minTier': 0,
      'nativeScales': <int>[2],
      'purposes': <String>['photo'],
      'modes': <String>['upscale'],
      'speed': 'fast',
      'sizeBytes': 3,
      'license': 'MIT',
      'sourceUrl': 'https://github.com/example/models',
      'availability': 'onDemand',
      'restricted': false,
      'files': <Map<String, dynamic>>[
        <String, dynamic>{
          'role': 'weights',
          'filename': 'model.bin',
          'url': 'https://github.com/example/models/model.bin',
          'sizeBytes': 3,
          'sha256':
              '039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81',
        },
      ],
    },
  ],
};
