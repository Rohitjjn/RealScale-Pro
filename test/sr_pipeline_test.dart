import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/engine/engine_types.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/pipeline_planner/pipeline_planner.dart';

/// Phase 3 Super Resolution planner contract: SR mode resolves models from
/// the SR table, the SR-capable catalog rows accept the mode, and the
/// lossless promise holds at the plan level (native run, no resample).
void main() {
  final catalog = ModelCatalog.parse(
    File('assets/data/model_catalog.json').readAsStringSync(),
  );
  final planner = PipelinePlanner(
    (jsonDecode(File('assets/data/planner_rules.json').readAsStringSync())
            as Map)
        .cast<String, Object?>(),
  );

  test('SR table: photo picks x4plus across tiers', () {
    for (final tier in DeviceTier.values) {
      final id = planner.autoModelId(
        mode: UpscaleMode.superResolution,
        imageType: 'photo',
        tier: tier,
        catalog: catalog,
      );
      expect(id, 'realesrgan-x4plus', reason: 'tier ${tier.name}');
    }
  });

  test('SR table: anime picks the x4plus-anime row on T1', () {
    final id = planner.autoModelId(
      mode: UpscaleMode.superResolution,
      imageType: 'anime',
      tier: DeviceTier.t1,
      catalog: catalog,
    );
    expect(id, 'realesrgan-x4plus-anime');
  });

  test('SR-capable rows support the superResolution mode', () {
    for (final id in [
      'realesrgan-x4plus',
      'realesrgan-x4plus-anime',
      'realcugan-pro-2x-conservative',
    ]) {
      final m = catalog.byId(id);
      expect(m, isNotNull);
      expect(
        m!.supportsMode('superResolution'),
        isTrue,
        reason: '$id must accept Super Resolution mode',
      );
    }
  });

  test('SR plan: x4plus on 500x400 at 4x is a pure native run', () {
    final model = catalog.byId('realesrgan-x4plus')!;
    final spec = JobSpec(
      jobId: 'sr1',
      inputPath: 'p.jpg',
      inputBytes: Uint8List(0),
      mode: UpscaleMode.superResolution,
      settingLevel: SettingLevel.defaultLevel,
      imageType: ImageType.photo,
      scale: 4,
      modelId: model.id,
    );
    final plan = planner.plan(
      spec: spec,
      device: _device(),
      catalog: catalog,
      inputWidth: 500,
      inputHeight: 400,
    );
    expect(plan.nativeScale, 4);
    expect(plan.resamplePercent, 100); // native run only — no resample
    expect(plan.outputWidth, 2000);
    expect(plan.outputHeight, 1600);
  });

  test('models without the SR mode are rejected by the contract', () {
    final plain = ModelDef(
      id: 'up-only',
      version: '1.0.0',
      name: 'Up only',
      engine: 'ncnn',
      pack: 'pro',
      minTier: DeviceTier.t0,
      nativeScales: const [4],
      modes: const ['upscale'],
      bestFor: const ['photo'],
      speed: 'medium',
      sizeBytes: 10,
      license: 'MIT',
      verified: true,
      heavy: false,
      files: const [],
    );
    expect(plain.supportsMode('superResolution'), isFalse);
  });
}

DeviceProfile _device({DeviceTier tier = DeviceTier.t2}) => DeviceProfile(
  tier: tier,
  totalRamMb: 8192,
  freeRamMb: 4096,
  cpuCores: 8,
  gpuName: '',
  vulkanSupported: false,
  benchmarkMsPerTile: 100,
  freeStorageMb: 32000,
  osVersion: 'test',
  thermalState: 'nominal',
  measuredAt: DateTime(2026),
);
