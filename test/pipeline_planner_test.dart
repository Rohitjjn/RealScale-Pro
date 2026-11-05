import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/engine/upscale_engine.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/pipeline_planner/pipeline_planner.dart';

const _catalogJson = '''
{
  "version": 3,
  "models": [
    {"id": "photo-native-4x", "version": "1.0.0", "name": "Photo 4x", "engine": "ncnn",
     "pack": "pro", "minTier": "T1", "nativeScales": [4], "modes": ["upscale"],
     "bestFor": ["photo"], "speed": "medium", "sizeBytes": 1000,
     "license": "MIT", "verified": true, "heavy": false,
     "files": [{"kind": "bin", "sha256": "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "sizeBytes": 1}]},
    {"id": "anime-native-2x", "version": "1.0.0", "name": "Anime 2x", "engine": "ncnn",
     "pack": "starter", "minTier": "T0", "nativeScales": [2], "modes": ["upscale"],
     "bestFor": ["anime"], "speed": "veryFast", "sizeBytes": 1000,
     "license": "MIT", "verified": true, "heavy": false,
     "files": [{"kind": "bin", "sha256": "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "sizeBytes": 1}]},
    {"id": "blocked-model", "version": "1.0.0", "name": "Blocked", "engine": "ncnn",
     "pack": "pro", "minTier": "T0", "nativeScales": [4], "modes": ["upscale"],
     "bestFor": ["photo"], "speed": "medium", "sizeBytes": 0,
     "license": "NON-COMMERCIAL", "verified": false, "heavy": false,
     "unavailableReason": "licenseBlocked", "files": []}
  ]
}
''';

ModelCatalog _catalog() => ModelCatalog.parse(_catalogJson);

PipelinePlanner _planner() => PipelinePlanner({
  'autoSelection': {
    'upscale': {
      'photo': {
        'T0': 'blocked-model',
        'T1': 'photo-native-4x',
        'T2': 'photo-native-4x',
        'T3': 'photo-native-4x',
      },
      'anime': {
        'T0': 'anime-native-2x',
        'T1': 'anime-native-2x',
        'T2': 'anime-native-2x',
        'T3': 'anime-native-2x',
      },
    },
    'superResolution': {
      'photo': {
        'T0': 'photo-native-4x',
        'T1': 'photo-native-4x',
        'T2': 'photo-native-4x',
        'T3': 'photo-native-4x',
      },
      'anime': {
        'T0': 'anime-native-2x',
        'T1': 'anime-native-2x',
        'T2': 'anime-native-2x',
        'T3': 'anime-native-2x',
      },
    },
  },
  'scaleChain': {
    'photo': {'2': '4x:50', '3': '4x:75', '4': '4x:100', '5': '4x:125'},
    'anime': {'2': '2x:100', '3': '3x:100', '4': '4x:100', '5': '4x:125'},
  },
  'tileSizes': {'T0': 192, 'T1': 256, 'T2': 384, 'T3': 512},
  'tileOverlap': {'none': 0, 'small': 8, 'medium': 16, 'large': 32},
  'overlapByTile': {
    '192': 'medium',
    '256': 'medium',
    '384': 'small',
    '512': 'small',
  },
  'threadCounts': {'T0': 2, 'T1': 4, 'T2': 6, 'T3': 8},
  'inputPixelLimit': {
    'T0': 4000000,
    'T1': 8000000,
    'T2': 16000000,
    'T3': 34000000,
  },
  'outputCapMegapixels': {'T0': 24, 'T1': 40, 'T2': 64, 'T3': 96},
  'etaBaseMsPerTile': {
    'photo-native-4x': {'cpu': 400, 'gpu': 120},
    'anime-native-2x': {'cpu': 320, 'gpu': 90},
  },
});

DeviceProfile _device(DeviceTier tier) => DeviceProfile(
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

JobSpec _spec({String? modelId, int scale = 2}) => JobSpec(
  jobId: 'p1',
  inputPath: '/x.png',
  inputBytes: Uint8List(8),
  mode: UpscaleMode.upscale,
  settingLevel: SettingLevel.manual,
  imageType: ImageType.photo,
  scale: scale,
  modelId: modelId ?? 'photo-native-4x',
);

void main() {
  test('planner covers Photo/Anime across tiers T0–T3 (task 2.6)', () {
    final catalog = _catalog();
    final planner = _planner();
    for (final tier in DeviceTier.values) {
      for (final type in const ['photo', 'anime']) {
        final id = planner.autoModelId(
          mode: UpscaleMode.upscale,
          imageType: type,
          tier: tier,
          catalog: catalog,
        );
        expect(id, isNotNull, reason: '$type/$tier must resolve a model');
      }
    }
  });

  test('unavailable auto model falls back down the tiers (task 2.6)', () {
    // T0 photo points at blocked-model → planner must walk to an available one.
    final id = _planner().autoModelId(
      mode: UpscaleMode.upscale,
      imageType: 'photo',
      tier: DeviceTier.t0,
      catalog: _catalog(),
    );
    expect(id, 'photo-native-4x');
  });

  test('scale chain: 2x on a 4x-native photo model resamples to 50%', () {
    final chain = _planner().resolveScaleChain(
      model: _catalog().byId('photo-native-4x')!,
      targetScale: 2,
      imageType: 'photo',
    );
    expect(chain.nativeScale, 4);
    expect(chain.resamplePercent, 50);
  });

  test('5x adds the extra resize step (resample > 100%)', () {
    final plan = _planner().plan(
      spec: _spec(scale: 5),
      device: _device(DeviceTier.t2),
      catalog: _catalog(),
      inputWidth: 100,
      inputHeight: 100,
    );
    expect(plan.extraResizeStep, isTrue);
    expect(plan.resamplePercent, 125);
    expect(plan.outputWidth, 500);
    expect(plan.outputHeight, 500);
  });

  test('output cap clamps huge outputs honestly', () {
    final plan = _planner().plan(
      spec: _spec(scale: 4),
      device: _device(DeviceTier.t0), // 24 MP cap
      catalog: _catalog(),
      inputWidth: 2000,
      inputHeight: 2000, // 4 MP in → 64 MP out at 4x → clamped to 24 MP
    );
    expect(plan.outputWidth * plan.outputHeight, lessThanOrEqualTo(24000000));
  });

  test('unavailable selected model throws a typed planner error', () {
    expect(
      () => _planner().plan(
        spec: _spec(modelId: 'blocked-model'),
        device: _device(DeviceTier.t1),
        catalog: _catalog(),
        inputWidth: 100,
        inputHeight: 100,
      ),
      throwsA(isA<PlannerException>()),
    );
  });

  test('ETA baseline comes from data and stays positive', () {
    final plan = _planner().plan(
      spec: _spec(),
      device: _device(DeviceTier.t1),
      catalog: _catalog(),
      inputWidth: 200,
      inputHeight: 200,
    );
    final eta = _planner().estimateSeconds(plan, 200, 200);
    expect(eta, greaterThan(0));
  });
}
