import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/engine/engine_types.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/pipeline_planner/pipeline_planner.dart';

/// Phase 3 Advanced level: per-job overrides reach the plan (tile size,
/// overlap, thread count, compute device, output cap) and Auto keeps the
/// data-file rules.
void main() {
  final catalog = ModelCatalog.parse(
    File('assets/data/model_catalog.json').readAsStringSync(),
  );
  final planner = PipelinePlanner(
    (jsonDecode(File('assets/data/planner_rules.json').readAsStringSync())
            as Map)
        .cast<String, Object?>(),
  );
  final model = catalog.byId('realesr-animevideov3')!;

  JobSpec spec({
    int tileSize = 0,
    TileOverlap overlap = TileOverlap.medium,
    bool overlapExplicit = false,
    int threads = 0,
    ComputeDevice compute = ComputeDevice.auto,
    int outputCapMp = 0,
  }) => JobSpec(
    jobId: 'adv',
    inputPath: 'a.png',
    inputBytes: Uint8List(0),
    mode: UpscaleMode.upscale,
    settingLevel: SettingLevel.advanced,
    imageType: ImageType.anime,
    scale: 2,
    modelId: model.id,
    tileSize: tileSize,
    overlap: overlap,
    overlapExplicit: overlapExplicit,
    computeDevice: compute,
    threadCount: threads,
    outputCapMegapixels: outputCapMp,
  );

  test('Auto leaves every decision to the planner data (T2 device)', () {
    final plan = planner.plan(
      spec: spec(),
      device: _device(),
      catalog: catalog,
      inputWidth: 800,
      inputHeight: 600,
    );
    expect(plan.tileSize, 384); // T2 row in planner_rules.json
    expect(plan.overlapPx, 8); // 'small' for 384
    expect(plan.threadCount, 6);
    expect(plan.outputCapMegapixels, 64); // T2 cap
  });

  test('tile size override wins and threads are clamped to cores', () {
    final plan = planner.plan(
      spec: spec(tileSize: 512, threads: 99),
      device: _device(cores: 4),
      catalog: catalog,
      inputWidth: 800,
      inputHeight: 600,
    );
    expect(plan.tileSize, 512);
    expect(plan.overlapPx, 8); // table value for 512 (no explicit overlap)
    expect(plan.threadCount, 4); // clamped to cpuCores
  });

  test('explicit overlap override wins over the per-tile table', () {
    final plan = planner.plan(
      spec: spec(
        tileSize: 512,
        overlap: TileOverlap.large,
        overlapExplicit: true,
      ),
      device: _device(),
      catalog: catalog,
      inputWidth: 800,
      inputHeight: 600,
    );
    expect(plan.overlapPx, 32); // 'large'
  });

  test('compute CPU override disables GPU', () {
    final plan = planner.plan(
      spec: spec(compute: ComputeDevice.cpu),
      device: _device(vulkan: true),
      catalog: catalog,
      inputWidth: 800,
      inputHeight: 600,
    );
    expect(plan.useGpu, isFalse);
  });

  test('per-job output cap override clamps output honestly', () {
    final plan = planner.plan(
      spec: spec(outputCapMp: 12),
      device: _device(),
      catalog: catalog,
      inputWidth: 800,
      inputHeight: 600,
    );
    // 1600x1200 = 1.92 MP — under the 12 MP cap, untouched.
    expect(plan.outputWidth, 1600);
    expect(plan.outputHeight, 1200);

    final capped = planner.plan(
      spec: spec(outputCapMp: 1),
      device: _device(),
      catalog: catalog,
      inputWidth: 800,
      inputHeight: 600,
    );
    expect(
      capped.outputWidth * capped.outputHeight,
      lessThanOrEqualTo(1000000),
    );
  });
}

DeviceProfile _device({bool vulkan = false, int cores = 8}) => DeviceProfile(
  tier: DeviceTier.t2,
  totalRamMb: 8192,
  freeRamMb: 4096,
  cpuCores: cores,
  gpuName: '',
  vulkanSupported: vulkan,
  benchmarkMsPerTile: 100,
  freeStorageMb: 32000,
  osVersion: 'test',
  thermalState: 'nominal',
  measuredAt: DateTime(2026),
);
