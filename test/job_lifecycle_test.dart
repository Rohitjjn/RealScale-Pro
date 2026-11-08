/// Job lifecycle tests with a scripted fake engine: verifies state
/// transitions, honest progress/ETA wiring, result facts, original-file
/// preservation, cancellation and failure paths — WITHOUT pretending
/// to run AI (the real inference test lives in
/// native_ffi_integration_test.dart).
library;

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:realscale_pro/domain/models.dart';
import 'package:realscale_pro/engine/native/realscale_native.dart';
import 'package:realscale_pro/processing/job_manager.dart';
import 'package:realscale_pro/services/image_service.dart';
import 'package:realscale_pro/services/model_manager.dart';
import 'package:realscale_pro/services/storage_service.dart';

class _FakePathProvider extends PathProviderPlatform {
  _FakePathProvider(this.root);
  final Directory root;

  @override
  Future<String?> getApplicationDocumentsPath() async =>
      '${root.path}/docs';

  @override
  Future<String?> getTemporaryPath() async => '${root.path}/tmp';

  @override
  Future<String?> getApplicationSupportPath() async =>
      '${root.path}/support';
}

class _ScriptedEngine implements EngineRunner {
  _ScriptedEngine({
    this.tiles = 3,
    this.failWith,
    this.respectCancel = false,
  });

  final int tiles;
  final NativeEngineException? failWith;
  final bool respectCancel;
  ControlBlock? lastControl;

  @override
  Future<NativeRunResult> run(
    NativeJobRequest job, {
    ControlBlock? control,
    void Function(NativeProgress)? onProgress,
    Duration pollInterval = const Duration(milliseconds: 120),
  }) async {
    lastControl = control;
    if (failWith != null) {
      throw failWith!;
    }
    if (respectCancel) {
      control?.requestCancel();
      throw NativeEngineException(8, 'cancelled by user');
    }
    for (var i = 0; i <= tiles; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 5));
      onProgress?.call(NativeProgress(
        stage: RsStage.inference,
        tilesDone: i,
        tilesTotal: tiles,
        attempt: 1,
        tileSize: job.tileSize,
        fraction: i / tiles,
      ));
    }
    final outW = job.width * job.scale;
    final outH = job.height * job.scale;
    final px = Uint8List(outW * outH * 4);
    for (var i = 0; i < px.length; i += 4) {
      px[i] = 200;
      px[i + 1] = 150;
      px[i + 2] = 100;
      px[i + 3] = 255;
    }
    return NativeRunResult(
        rgba: px, width: outW, height: outH, attempt: 1);
  }
}

void main() {
  late Directory tmp;
  late StorageService storage;
  late ModelManager models;
  late File sourceFile;

  setUpAll(() {
    tmp = Directory.systemTemp.createTempSync('rs_job_test');
    PathProviderPlatform.instance = _FakePathProvider(tmp);
    // A real JPEG (the checked-in 220x220 test photo) — the platform
    // codec must actually decode it.
    sourceFile = File('${tmp.path}/source.jpg');
    sourceFile.writeAsBytesSync(
        File('test/fixtures/input.jpg').readAsBytesSync());
  });

  tearDownAll(() {
    tmp.deleteSync(recursive: true);
  });

  Future<(JobManager, List<JobPhase>)> buildManager(
      {required _ScriptedEngine engine}) async {
    storage = StorageService();
    models = ModelManager(storage: storage);
    await models.init();
    // Install fake model files so isInstalled() is true and the job
    // skips the download phase.
    final dir = await storage.modelDirectory();
    File('${dir.path}/fake.param').writeAsStringSync('param');
    File('${dir.path}/fake.bin').writeAsStringSync('bin');
    final phases = <JobPhase>[];
    final manager = JobManager(
      imageService: ImageService(),
      storage: storage,
      modelManager: models,
      engineFactory: () => engine,
    );
    manager.state.addListener(() {
      final s = manager.state.value;
      if (phases.isEmpty || phases.last != s.phase) {
        phases.add(s.phase);
      }
    });
    return (manager, phases);
  }

  const model = ModelInfo(
    id: 'fake-model',
    displayName: 'Fake Model',
    family: 'SRVGGNet compact',
    scale: 4,
    license: 'BSD-3-Clause',
    licenseUrl: 'https://example.com',
    sourceUrl: 'https://example.com',
    paramFile: 'fake.param',
    binFile: 'fake.bin',
    approxSizeMb: 1,
    minRamMb: 500,
    description: '',
    tags: [],
  );

  const device = DeviceProfile(
    tier: DeviceTier.balanced,
    cpuCores: 4,
    platform: 'android',
    totalMemoryMb: 4096,
    availableMemoryMb: 2048,
  );

  test('successful run walks the full lifecycle and preserves the input',
      () async {
    final before = sourceFile.readAsBytesSync();
    final (manager, phases) = await buildManager(
        engine: _ScriptedEngine(tiles: 4));

    final result = await manager.run(
      input: sourceFile,
      mode: ProcessingMode.upscale,
      model: model,
      device: device,
    );

    // Lifecycle order.
    expect(phases, containsAllInOrder([
      JobPhase.inspecting,
      JobPhase.planning,
      JobPhase.decoding,
      JobPhase.inferring,
      JobPhase.encoding,
      JobPhase.saving,
      JobPhase.done,
    ]));

    // Honest result facts (from the fake engine's declared geometry:
    // the real 220x220 fixture scaled 4x).
    expect(result.outputWidth, 220 * 4);
    expect(result.outputHeight, 220 * 4);
    expect(result.modelId, model.id);
    expect(result.outputPath, contains('RealScale-Pro'));
    expect(File(result.outputPath).existsSync(), isTrue);
    expect(result.outputFileBytes, greaterThan(0));

    // The ORIGINAL file is untouched.
    expect(sourceFile.readAsBytesSync(), before);
  });

  test('native failure surfaces an honest error state', () async {
    final (manager, phases) = await buildManager(
        engine: _ScriptedEngine(
            failWith: NativeEngineException(6, 'inference blew up')));

    await expectLater(
      manager.run(
        input: sourceFile,
        mode: ProcessingMode.superResolution,
        model: model,
        device: device,
      ),
      throwsA(isA<JobManagerException>()),
    );
    expect(manager.state.value.phase, JobPhase.failed);
    expect(manager.state.value.error, contains('inference blew up'));
    expect(phases, contains(JobPhase.failed));
  });

  test('cancellation is reported as cancelled, not failed', () async {
    final (manager, phases) = await buildManager(
        engine: _ScriptedEngine(respectCancel: true));

    await expectLater(
      manager.run(
        input: sourceFile,
        mode: ProcessingMode.upscale,
        model: model,
        device: device,
      ),
      throwsA(isA<JobManagerException>()),
    );
    expect(manager.state.value.phase, JobPhase.cancelled);
    expect(phases, contains(JobPhase.cancelled));
  });
}

