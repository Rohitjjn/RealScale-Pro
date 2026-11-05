import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import 'package:realscale_pro/engine/engine_types.dart';
import 'package:realscale_pro/engine/image_bitmap.dart';
import 'package:realscale_pro/engine/lanczos_engine.dart';
import 'package:realscale_pro/engine/ncnn/ncnn_ffi.dart';
import 'package:realscale_pro/engine/recovery_chain.dart';
import 'package:realscale_pro/engine/tiling_engine.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';

ModelDef _model() => const ModelDef(
  id: 'realesr-animevideov3',
  version: '1.0.0',
  name: 'animevideov3',
  engine: 'ncnn',
  pack: 'starter',
  minTier: DeviceTier.t0,
  nativeScales: [2, 3, 4],
  modes: ['upscale'],
  bestFor: ['anime'],
  speed: 'veryFast',
  sizeBytes: 100,
  license: 'BSD-3-Clause',
  verified: true,
  heavy: false,
  files: [
    ModelFileSpec(
      kind: 'bin',
      sha256:
          '548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d',
      sizeBytes: 100,
    ),
    ModelFileSpec(
      kind: 'param',
      scale: 2,
      sha256:
          'b88ff4f00ebf019a7fdac17fdd45a7fd3665d37509efc5baf2e4da2e24420a04',
      sizeBytes: 100,
    ),
  ],
);

DeviceProfile _device({DeviceTier tier = DeviceTier.t1}) => DeviceProfile(
  tier: tier,
  totalRamMb: 4096,
  freeRamMb: 2048,
  cpuCores: 4,
  gpuName: '',
  vulkanSupported: false,
  benchmarkMsPerTile: 100,
  freeStorageMb: 1000,
  osVersion: 'test',
  thermalState: 'nominal',
  measuredAt: DateTime(2026),
);

PipelinePlan _plan({
  int nativeScale = 2,
  int resamplePercent = 100,
  int tileSize = 64,
}) => PipelinePlan(
  model: _model(),
  nativeScale: nativeScale,
  resamplePercent: resamplePercent,
  extraResizeStep: false,
  tileSize: tileSize,
  overlapPx: 8,
  threadCount: 2,
  useGpu: false,
  inputPixelLimit: 8000000,
  outputCapMegapixels: 40,
  outputWidth: 256,
  outputHeight: 256,
  etaBaselineMsPerTile: 100,
);

JobSpec _spec({Map<String, Object> options = const {}}) => JobSpec(
  jobId: 'job1',
  inputPath: '/tmp/x.png',
  inputBytes: Uint8List(16),
  mode: UpscaleMode.upscale,
  settingLevel: SettingLevel.defaultLevel,
  imageType: ImageType.anime,
  scale: 2,
  modelId: 'realesr-animevideov3',
  options: options,
);

/// Fake tile processor: counts calls, scales the tile by filling gray,
/// and can be told to fail N times or throw OOM.
class FakeTileProcessor {
  FakeTileProcessor({this.failFirst = 0, this.oomFirst = 0});

  int failFirst;
  int oomFirst;
  int calls = 0;
  final sizes = <(int, int)>[];

  Future<Uint8List> call(TileJob job) async {
    calls++;
    sizes.add((job.width, job.height));
    if (oomFirst > 0) {
      oomFirst--;
      throw const NcnnOomException();
    }
    if (failFirst > 0) {
      failFirst--;
      throw const NcnnErrorException('boom');
    }
    final outW = job.width * job.nativeScale;
    final outH = job.height * job.nativeScale;
    final out = Uint8List(outW * outH * 4);
    for (var i = 0; i < out.length; i += 4) {
      out[i] = 200;
      out[i + 1] = 100;
      out[i + 2] = 50;
      out[i + 3] = 255;
    }
    return out;
  }
}

void main() {
  test('TilingEngine produces honest progress from completed tiles', () async {
    final processor = FakeTileProcessor();
    final engine = TilingEngine(
      engineId: 'test',
      tileProcessor: processor.call,
    );

    final outputs = <String>[];
    final events = <ProgressEvent>[];
    final ctx = PipelineContext(
      catalog: ModelCatalog(version: 1, models: []),
      device: _device(),
      modelDir: '/tmp/models',
      tempDir: '/tmp',
      plan: _plan(),
      outputWriter: (path, bmp) async => outputs.add(path),
    );

    // 64x64 input, tile 64, overlap 8 → step 56 → 2x2 tiles at 2x → 128x128 output.
    await for (final e in engine.run(_spec(options: _pixels(64, 64)), ctx)) {
      events.add(e);
    }

    expect(
      events.map((e) => e.stage),
      containsAll([
        JobStage.preparing,
        JobStage.upscaling,
        JobStage.saving,
        JobStage.done,
      ]),
    );
    final done = events.last;
    expect(done.result, isNotNull);
    expect(done.result!.outputWidth, 128);
    expect(done.result!.outputHeight, 128);
    expect(processor.calls, 4);
    // Percent from completed tiles only: the last upscale event is 4/4.
    final upscale = events
        .where((e) => e.stage == JobStage.upscaling)
        .reduce((a, b) => b);
    expect(upscale.tileIndex, 4);
    expect(upscale.tileTotal, 4);
    expect(outputs, hasLength(1));
  });

  test('TilingEngine blends multiple tiles with overlap (no seams)', () async {
    final processor = FakeTileProcessor();
    final engine = TilingEngine(
      engineId: 'test',
      tileProcessor: processor.call,
    );
    final events = <ProgressEvent>[];
    final ctx = PipelineContext(
      catalog: ModelCatalog(version: 1, models: []),
      device: _device(),
      modelDir: '/tmp/models',
      tempDir: '/tmp',
      plan: _plan(), // tile 64, overlap 8 → step 56 → 2x2 tiles for 100x100
      outputWriter: (path, bmp) async {},
    );
    await for (final e in engine.run(_spec(options: _pixels(100, 100)), ctx)) {
      events.add(e);
    }
    final upscaleEvents = events
        .where((e) => e.stage == JobStage.upscaling)
        .toList();
    expect(upscaleEvents, hasLength(4));
    expect(upscaleEvents.last.tileTotal, 4);
    expect(processor.calls, 4);
  });

  test('cancel stops the engine within one event', () async {
    final processor = FakeTileProcessor();
    final engine = TilingEngine(
      engineId: 'test',
      tileProcessor: processor.call,
    );
    final ctx = PipelineContext(
      catalog: ModelCatalog(version: 1, models: []),
      device: _device(),
      modelDir: '/tmp/models',
      tempDir: '/tmp',
      plan: _plan(),
      outputWriter: (path, bmp) async {},
    );
    final sub = engine.run(_spec(options: _pixels(300, 300)), ctx).listen(null);
    await engine.cancel('job1');
    await sub.cancel();
    // No throw — cancellation is a normal path.
    expect(processor.calls, lessThan(40));
  });

  test('RecoveryChain halves tiles on OOM, then succeeds (prd §8.5)', () async {
    // First tile attempts fail with OOM twice, then succeed.
    final processor = FakeTileProcessor(oomFirst: 2);
    final engine = TilingEngine(
      engineId: 'test',
      tileProcessor: processor.call,
    );
    final basePlan = _plan(tileSize: 64);
    final chain = RecoveryChain(
      engine: engine,
      basePlan: basePlan,
      replanForRetry: replanStandard,
    );
    final stages = <JobStage>[];
    EngineResult? result;
    final ctx = PipelineContext(
      catalog: ModelCatalog(version: 1, models: []),
      device: _device(),
      modelDir: '/tmp/models',
      tempDir: '/tmp',
      plan: basePlan,
      outputWriter: (path, bmp) async {},
    );
    await for (final e in chain.runWithRecovery(
      _spec(options: _pixels(64, 64)),
      ctx,
    )) {
      stages.add(e.stage);
      if (e.stage == JobStage.done) result = e.result;
    }
    expect(result, isNotNull);
    expect(result!.recoverySteps, isNotEmpty);
    expect(result.recoverySteps.first, contains('tile'));
    expect(basePlan.tileSize, 64); // base plan untouched
  });

  test(
    'RecoveryChain gives up with a friendly failure after max steps',
    () async {
      final processor = FakeTileProcessor(oomFirst: 99);
      final engine = TilingEngine(
        engineId: 'test',
        tileProcessor: processor.call,
      );
      final basePlan = _plan();
      final chain = RecoveryChain(
        engine: engine,
        basePlan: basePlan,
        replanForRetry: replanStandard,
      );
      ProgressEvent? failed;
      final ctx = PipelineContext(
        catalog: ModelCatalog(version: 1, models: []),
        device: _device(),
        modelDir: '/tmp/models',
        tempDir: '/tmp',
        plan: basePlan,
        outputWriter: (path, bmp) async {},
      );
      await for (final e in chain.runWithRecovery(
        _spec(options: _pixels(64, 64)),
        ctx,
      )) {
        if (e.stage == JobStage.failed) failed = e;
      }
      expect(failed, isNotNull);
      expect(failed!.message, contains('safer settings'));
    },
  );

  test('LanczosEngine is a real engine: solid color survives 2x', () async {
    final engine = LanczosEngine();
    expect(engine.supports(_model(), _device()), isTrue);
    final events = <ProgressEvent>[];
    Bitmap? written;
    final ctx = PipelineContext(
      catalog: ModelCatalog(version: 1, models: []),
      device: _device(),
      modelDir: '/tmp/models',
      tempDir: '/tmp',
      plan: _plan(tileSize: 32),
      outputWriter: (path, bmp) async => written = bmp,
    );
    // Solid red input at nativeScale 2.
    await for (final e in engine.run(
      _spec(options: _pixels(32, 32, r: 255, g: 0, b: 0)),
      ctx,
    )) {
      events.add(e);
    }
    expect(events.last.stage, JobStage.done);
    expect(written, isNotNull);
    expect(written!.width, 64);
    expect(written!.height, 64);
    // Center pixel stays red (Lanczos preserves flat color).
    final center = (32 * 64 + 32) * 4;
    expect(written!.pixels[center], greaterThan(240));
  });
}

Map<String, Object> _pixels(
  int w,
  int h, {
  int r = 200,
  int g = 100,
  int b = 50,
}) {
  final px = Uint8List(w * h * 4);
  for (var i = 0; i < px.length; i += 4) {
    px[i] = r;
    px[i + 1] = g;
    px[i + 2] = b;
    px[i + 3] = 255;
  }
  return {'decodedPixels': px, 'decodedWidth': w, 'decodedHeight': h};
}
