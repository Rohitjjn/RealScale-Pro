import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/core/feature_gates.dart';
import 'package:realscale_pro/engine/engine_types.dart';
import 'package:realscale_pro/engine/onnx/onnx_engine.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/pipeline_planner/pipeline_planner.dart';

const _onnxRow = '''
{
  "version": 3,
  "models": [
    {"id": "swinir-real-world", "version": "1.0.0", "name": "SwinIR", "engine": "onnx",
     "pack": "ultra", "minTier": "T3", "nativeScales": [4], "modes": ["upscale", "superResolution"],
     "bestFor": ["photo"], "speed": "slow", "sizeBytes": 64000000,
     "license": "Apache-2.0", "verified": true, "heavy": true,
     "files": [{"kind": "bin", "sha256": "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "sizeBytes": 64000000}]}
  ]
}
''';

DeviceProfile _device({
  required bool vulkan,
  DeviceTier tier = DeviceTier.t3,
}) => DeviceProfile(
  tier: tier,
  totalRamMb: 12288,
  freeRamMb: 6144,
  cpuCores: 8,
  gpuName: vulkan ? 'test-gpu' : '',
  vulkanSupported: vulkan,
  benchmarkMsPerTile: 100,
  freeStorageMb: 64000,
  osVersion: 'test',
  thermalState: 'nominal',
  measuredAt: DateTime(2026),
);

ModelCatalog _catalogOf(String json) => ModelCatalog.parse(json);

JobSpec _spec(String modelId) => JobSpec(
  jobId: 't1',
  inputPath: 'x.jpg',
  inputBytes: Uint8ListEmpty.bytes,
  mode: UpscaleMode.superResolution,
  settingLevel: SettingLevel.manual,
  imageType: ImageType.photo,
  scale: 4,
  modelId: modelId,
);

abstract final class Uint8ListEmpty {
  static final bytes = Uint8List(0);
}

void main() {
  test('gates: Phase 3 flips all three Pro gates on', () {
    expect(FeatureGates.advancedLevel, isTrue);
    expect(FeatureGates.superResolutionMode, isTrue);
    expect(FeatureGates.onnxEngine, isTrue);
  });

  test('probe: typed result when the runtime library is absent/present', () {
    // This sandbox (and any device without the shared library) must get a
    // typed result — never a crash, never a raw exception.
    final status = OnnxEngine.probeStatus();
    if (!status.available) {
      expect(status.reason, isNotEmpty);
    } else {
      expect(status.version, isNotEmpty);
    }
  });

  test('engine: tryCreate is null when the runtime is absent', () {
    final engine = OnnxEngine.tryCreate();
    if (OnnxEngine.probeStatus().available) {
      expect(engine, isNotNull);
      expect(engine!.engineId, 'onnx');
    } else {
      expect(engine, isNull);
    }
  });

  test('catalog: bundled Ultra rows are honestly marked coming soon', () {
    final catalog = ModelCatalog.parse(
      File('assets/data/model_catalog.json').readAsStringSync(),
    );
    for (final id in ['swinir-real-world', 'hat', 'dat']) {
      final m = catalog.byId(id);
      expect(m, isNotNull, reason: '$id missing from bundled catalog');
      expect(m!.engine, 'onnx');
      expect(
        m.unavailableReason,
        UnavailableReason.comingSoon,
        reason: '$id must stay Coming soon until audited weights ship',
      );
      expect(m.available, isFalse);
    }
  });

  test('planner: ONNX model on a non-Vulkan device throws typed', () {
    final catalog = _catalogOf(_onnxRow);
    final planner = _planner();
    final model = catalog.byId('swinir-real-world')!;
    expect(
      () => planner.plan(
        spec: _spec(model.id),
        device: _device(vulkan: false),
        catalog: catalog,
        inputWidth: 500,
        inputHeight: 400,
      ),
      throwsA(isA<PlannerException>()),
    );
  });

  test('planner: ONNX model plans cleanly on a capable device', () {
    final catalog = _catalogOf(_onnxRow);
    final planner = _planner();
    final model = catalog.byId('swinir-real-world')!;
    final plan = planner.plan(
      spec: _spec(model.id),
      device: _device(vulkan: true),
      catalog: catalog,
      inputWidth: 500,
      inputHeight: 400,
    );
    expect(plan.nativeScale, 4);
    expect(plan.model.engine, 'onnx');
    expect(plan.outputWidth, 2000);
    expect(plan.outputHeight, 1600);
  });

  test('engine: supports() honors catalog availability (Ultra stays off)', () {
    final engine = OnnxEngine.tryCreate();
    if (engine == null) return; // runtime absent — probe test covers it
    final catalog = _catalogOf(_onnxRow);
    final m = catalog.byId('swinir-real-world')!;
    // A verified Ultra row on a capable device IS supported once it ships.
    expect(engine.supports(m, _device(vulkan: true)), isTrue);
    // But below the minimum tier it is refused.
    expect(
      engine.supports(m, _device(vulkan: true, tier: DeviceTier.t1)),
      isFalse,
    );
  });

  test('engine: bundled Ultra rows are refused while comingSoon', () {
    final engine = OnnxEngine.tryCreate();
    if (engine == null) return;
    final catalog = ModelCatalog.parse(
      File('assets/data/model_catalog.json').readAsStringSync(),
    );
    final m = catalog.byId('swinir-real-world')!;
    expect(engine.supports(m, _device(vulkan: true)), isFalse);
  });
}

PipelinePlanner _planner() => PipelinePlanner({
  'autoSelection': {
    'upscale': {
      'photo': {
        'T0': 'swinir-real-world',
        'T1': 'swinir-real-world',
        'T2': 'swinir-real-world',
        'T3': 'swinir-real-world',
      },
      'anime': {
        'T0': 'swinir-real-world',
        'T1': 'swinir-real-world',
        'T2': 'swinir-real-world',
        'T3': 'swinir-real-world',
      },
    },
    'superResolution': {
      'photo': {
        'T0': 'swinir-real-world',
        'T1': 'swinir-real-world',
        'T2': 'swinir-real-world',
        'T3': 'swinir-real-world',
      },
      'anime': {
        'T0': 'swinir-real-world',
        'T1': 'swinir-real-world',
        'T2': 'swinir-real-world',
        'T3': 'swinir-real-world',
      },
    },
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
    'swinir-real-world': {'cpu': 2000, 'gpu': 600},
  },
  'scaleChain': {
    'photo': {'2': '4x:50', '3': '4x:75', '4': '4x:100', '5': '4x:125'},
    'anime': {'2': '2x:100', '3': '3x:100', '4': '4x:100', '5': '4x:125'},
  },
});
