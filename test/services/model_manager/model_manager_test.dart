import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/services/model_manager/model_catalog.dart';
import 'package:realscale_pro/services/model_manager/model_manager.dart';

void main() {
  late Directory temporary;
  late ModelCatalog catalog;

  setUp(() async {
    temporary = await Directory.systemTemp.createTemp('model-manager-test');
    catalog = ModelCatalog.parse(jsonEncode(_catalogJson()));
  });

  tearDown(() async {
    if (await temporary.exists()) await temporary.delete(recursive: true);
  });

  test('initialization maps catalog and installed versions honestly', () async {
    final _FakeStore store = _FakeStore()..versions['remote-model'] = '0.9.0';
    final ModelManager manager = ModelManager(
      catalog: catalog,
      transfers: _FakeGateway(temporary),
      store: store,
    );

    await manager.initialize();

    expect(
      manager.stateFor('bundled-model').status,
      ModelInstallStatus.bundled,
    );
    expect(
      manager.stateFor('remote-model').status,
      ModelInstallStatus.updateAvailable,
    );
    expect(manager.stateFor('held-model').status, ModelInstallStatus.held);
    expect(
      manager.stateFor('blocked-model').status,
      ModelInstallStatus.blocked,
    );
    expect(
      manager.stateFor('future-model').status,
      ModelInstallStatus.comingSoon,
    );
    await manager.dispose();
  });

  test('verified download installs and reports completion', () async {
    final _FakeStore store = _FakeStore();
    final ModelManager manager = ModelManager(
      catalog: catalog,
      transfers: _FakeGateway(temporary),
      store: store,
    );
    await manager.initialize();

    await manager.install('remote-model');

    final ModelRuntimeState state = manager.stateFor('remote-model');
    expect(state.status, ModelInstallStatus.installed);
    expect(state.progress, 1);
    expect(state.installedVersion, '1.0.0');
    expect(store.installedIds, contains('remote-model'));
    await manager.dispose();
  });

  test('download failure becomes a friendly failed state', () async {
    final ModelManager manager = ModelManager(
      catalog: catalog,
      transfers: _FakeGateway(temporary)..failure = true,
      store: _FakeStore(),
    );
    await manager.initialize();

    await manager.install('remote-model');

    expect(manager.stateFor('remote-model').status, ModelInstallStatus.failed);
    expect(
      manager.stateFor('remote-model').failure,
      ModelManagerFailure.downloadFailed,
    );
    await manager.dispose();
  });

  test('deleting selected model falls back to automatic choice', () async {
    bool fellBack = false;
    final _FakeStore store = _FakeStore()..versions['remote-model'] = '1.0.0';
    final ModelManager manager = ModelManager(
      catalog: catalog,
      transfers: _FakeGateway(temporary),
      store: store,
      selectedModelId: 'remote-model',
      onSelectedModelDeleted: () => fellBack = true,
    );
    await manager.initialize();

    await manager.delete('remote-model');

    expect(
      manager.stateFor('remote-model').status,
      ModelInstallStatus.notInstalled,
    );
    expect(manager.selectedModelId, isNull);
    expect(fellBack, isTrue);
    await manager.dispose();
  });

  test('held model cannot enter download queue', () async {
    final ModelManager manager = ModelManager(
      catalog: catalog,
      transfers: _FakeGateway(temporary),
      store: _FakeStore(),
    );
    await manager.initialize();

    await manager.install('held-model');

    expect(manager.stateFor('held-model').status, ModelInstallStatus.failed);
    expect(
      manager.stateFor('held-model').failure,
      ModelManagerFailure.unavailable,
    );
    await manager.dispose();
  });
}

Map<String, dynamic> _catalogJson() => <String, dynamic>{
  'version': 1,
  'models': <Map<String, dynamic>>[
    _model('bundled-model', 'bundled', asset: true),
    _model('remote-model', 'onDemand'),
    _model('held-model', 'held', files: false),
    _model('blocked-model', 'blocked', files: false),
    _model('future-model', 'comingSoon', files: false),
  ],
};

Map<String, dynamic> _model(
  String id,
  String availability, {
  bool asset = false,
  bool files = true,
}) => <String, dynamic>{
  'id': id,
  'version': '1.0.0',
  'name': id,
  'engine': 'ncnn',
  'pack': 'starter',
  'minTier': 0,
  'nativeScales': <int>[2],
  'purposes': <String>['photo'],
  'modes': <String>['upscale'],
  'speed': 'fast',
  'sizeBytes': files ? 3 : 0,
  'license': 'MIT',
  'sourceUrl': 'https://github.com/example/models',
  'availability': availability,
  'restricted': false,
  'files': files
      ? <Map<String, dynamic>>[
          <String, dynamic>{
            'role': 'weights',
            'filename': '$id.bin',
            if (asset)
              'assetPath': 'assets/$id.bin'
            else
              'url': 'https://github.com/example/models/$id.bin',
            'sizeBytes': 3,
            'sha256':
                '039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81',
          },
        ]
      : <Map<String, dynamic>>[],
};

class _FakeGateway implements ModelTransferGateway {
  _FakeGateway(this.root);

  final Directory root;
  bool failure = false;

  @override
  Future<File> downloadFile(
    ModelDefinition model,
    ModelFileDefinition file, {
    required void Function(double progress) onProgress,
  }) async {
    if (failure) throw const SocketException('offline');
    onProgress(0.5);
    final File result = File('${root.path}/${file.filename}');
    await result.writeAsBytes(<int>[1, 2, 3]);
    onProgress(1);
    return result;
  }

  @override
  Future<void> cancelModel(String modelId) async {}

  @override
  Future<void> pauseModel(String modelId) async {}

  @override
  Future<void> resumeModel(String modelId) async {}
}

class _FakeStore implements ModelInstallStore {
  final Map<String, String> versions = <String, String>{};
  final Set<String> installedIds = <String>{};

  @override
  Future<void> delete(String modelId) async {
    versions.remove(modelId);
    installedIds.remove(modelId);
  }

  @override
  Future<void> install(
    ModelDefinition model,
    List<File> downloadedFiles,
  ) async {
    installedIds.add(model.id);
    versions[model.id] = model.version;
  }

  @override
  Future<String?> installedVersion(String modelId) async => versions[modelId];

  @override
  Future<ModelStorageSummary> summary() async =>
      const ModelStorageSummary(usedBytes: 3, freeBytes: 100);
}
