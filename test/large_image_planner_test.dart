import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/engine/image_bitmap.dart';
import 'package:realscale_pro/engine/lanczos_resampler.dart';
import 'package:realscale_pro/engine/upscale_engine.dart';
import 'package:realscale_pro/services/feasibility/feasibility_checker.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/pipeline_planner/pipeline_planner.dart';

/// Phase 3 exit check (24 MP without crash): the planner, feasibility gate
/// and tiling budget must handle a 24 MP photo on a T3 device, and one real
/// 512-px tile must run through the engine pipeline. A full on-device run
/// stays a hardware gap (see reports/phase-3.md) — this proves the software
/// path does not blow up at the pixel budgets involved.
void main() {
  final catalog = ModelCatalog.parse(
    File('assets/data/model_catalog.json').readAsStringSync(),
  );
  final planner = PipelinePlanner(
    (jsonDecode(File('assets/data/planner_rules.json').readAsStringSync())
            as Map)
        .cast<String, Object?>(),
  );

  const w = 6000;
  const h = 4000; // exactly 24 MP

  test('24 MP is accepted on T3 (input limit 34 MP) and planned cleanly', () {
    final model = catalog.byId('realesr-animevideov3')!;
    final spec = JobSpec(
      jobId: 'mp24',
      inputPath: 'big.jpg',
      inputBytes: Uint8List(0),
      mode: UpscaleMode.upscale,
      settingLevel: SettingLevel.defaultLevel,
      imageType: ImageType.photo,
      scale: 2,
      modelId: model.id,
    );
    final device = _deviceT3();
    final plan = planner.plan(
      spec: spec,
      device: device,
      catalog: catalog,
      inputWidth: w,
      inputHeight: h,
    );
    expect(w * h, 24000000);
    expect(w * h, lessThanOrEqualTo(plan.inputPixelLimit));
    // 2x target on the anime-video model → native 2x run, no resample.
    expect(plan.nativeScale, 2);
    expect(plan.resamplePercent, 100);
    // Output clamp: 96 MP cap allows 12000x8000 = 96 MP exactly.
    expect(plan.outputWidth, 12000);
    expect(plan.outputHeight, 8000);
    // Tiling budget is sane: finite tile count, positive step.
    final step = plan.tileSize - plan.overlapPx;
    final cols = (w + step - 1) ~/ step;
    final rows = (h + step - 1) ~/ step;
    expect(cols * rows, inInclusiveRange(1, 10000));
    // Honest ETA baseline present.
    expect(plan.etaBaselineMsPerTile, greaterThan(0));
  });

  test(
    '24 MP Super Resolution feasibility is green or amber on T3, never red',
    () {
      final result = const FeasibilityChecker().check(
        mode: UpscaleMode.superResolution,
        inputWidth: w,
        inputHeight: h,
        targetScale: 2,
        outputCapMegapixels: 96,
        etaSeconds: 300,
        device: _deviceT3(freeRamMb: 8192),
      );
      expect(
        result.level,
        isNot(FeasibilityLevel.red),
        reason: '24 MP at 2x (96 MP out) must fit a T3 device with 8 GB free',
      );
    },
  );

  test(
    'one real 512-px tile runs through the resampler without crashing',
    () async {
      // Deterministic gradient tile — real pixels, real resample.
      final tile = Bitmap(512, 512, _gradient(512, 512));
      final up = await LanczosResampler().resample(tile, 2048, 2048);
      expect(up.width, 2048);
      expect(up.height, 2048);
      expect(up.pixels.lengthInBytes, 2048 * 2048 * 4);
      // Gradient sanity: brighter at the bottom than the top.
      final topLum = _lum(up.pixels, 2048, 8);
      final bottomLum = _lum(up.pixels, 2048, 2040);
      expect(bottomLum, greaterThan(topLum));
    },
  );

  test('planner ETA stays within the honest-estimate envelope', () {
    final model = catalog.byId('realesr-animevideov3')!;
    final spec = JobSpec(
      jobId: 'mp24b',
      inputPath: 'big.jpg',
      inputBytes: Uint8List(0),
      mode: UpscaleMode.upscale,
      settingLevel: SettingLevel.defaultLevel,
      imageType: ImageType.photo,
      scale: 2,
      modelId: model.id,
    );
    final plan = planner.plan(
      spec: spec,
      device: _deviceT3(),
      catalog: catalog,
      inputWidth: w,
      inputHeight: h,
    );
    final seconds = planner.estimateSeconds(plan, w, h);
    final step = plan.tileSize - plan.overlapPx;
    final tiles = ((w + step - 1) ~/ step) * ((h + step - 1) ~/ step);
    // ETA formula consistency (within 5%).
    final expected = tiles * plan.etaBaselineMsPerTile / 1000;
    expect(seconds, closeTo(expected, expected * 0.05));
  });
}

Uint8List _gradient(int w, int h) {
  final px = Uint8List(w * h * 4);
  for (var y = 0; y < h; y++) {
    for (var x = 0; x < w; x++) {
      final i = (y * w + x) * 4;
      px[i] = (x * 255 ~/ w);
      px[i + 1] = (y * 255 ~/ h);
      px[i + 2] = ((x + y) * 255 ~/ (w + h));
      px[i + 3] = 255;
    }
  }
  return px;
}

double _lum(Uint8List px, int w, int y) {
  var sum = 0;
  for (var x = 0; x < w; x += 16) {
    final i = (y * w + x) * 4;
    sum += (px[i] + px[i + 1] + px[i + 2]) ~/ 3;
  }
  return sum / math.max(1, w ~/ 16);
}

DeviceProfile _deviceT3({int freeRamMb = 8192}) => DeviceProfile(
  tier: DeviceTier.t3,
  totalRamMb: 16384,
  freeRamMb: freeRamMb,
  cpuCores: 8,
  gpuName: 'test',
  vulkanSupported: true,
  benchmarkMsPerTile: 120,
  freeStorageMb: 64000,
  osVersion: 'test',
  thermalState: 'nominal',
  measuredAt: DateTime(2026),
);
