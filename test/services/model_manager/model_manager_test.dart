import 'dart:async';
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
  'purposes': <String>['photo']