import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/app_exception.dart';
import '../../engine/engine_types.dart';
import '../model_catalog/model_catalog.dart';

/// Loads `assets/data/planner_rules.json` and resolves user choices into a
/// full plan. All decision rules live here or in the data file — never in
/// widgets (rules.md §3).
class PipelinePlanner {
  PipelinePlanner(this._rules);

  final Map<String, Object?> _rules;

  static Future<PipelinePlanner> load() async {
    final raw = await rootBundle.loadString('assets/data/planner_rules.json');
    return PipelinePlanner(jsonDecode(raw) as Map<String, Object?>);
  }

  Map<String, Object?> get _auto =>
      _rules['autoSelection'] as Map<String, Object?>;

  /// Picks the model for Default level (prd §8.3 table, from data).
  String? autoModelId({
    required UpscaleMode mode,
    required String imageType, // 'photo' | 'anime'
    required DeviceTier tier,
    required ModelCatalog catalog,
  }) {
    final table =
        _auto[mode == UpscaleMode.upscale ? 'upscale' : 'superResolution']
            as Map<String, Object?>?;
    final byType = table?[imageType] as Map<String, Object?>?;
    var id = byType?[tier.name.toUpperCase()] as String?;
    // Fall back down the tiers when the chosen model is unavailable.
    if (catalog.byId(id ?? '')?.available != true) {
      final tiers = [
        DeviceTier.t3,
        DeviceTier.t2,
        DeviceTier.t1,
        DeviceTier.t0,
      ];
      for (final t in tiers) {
        final candidate = byType?[t.name.toUpperCase()] as String?;
        if (candidate != null && catalog.byId(candidate)?.available == true) {
          id = candidate;
          break;
        }
      }
    }
    return id;
  }

  /// True when the photo/anime classifier is not confident (shows "Not sure…").
  static bool isLowConfidence(double confidence) => confidence < 0.62;

  /// Resolves the native-scale chain (prd §8.4): smallest native scale ≥
  /// target, then Lanczos resample to target. 5x uses an extra resize step.
  ({int nativeScale, int resamplePercent}) resolveScaleChain({
    required ModelDef model,
    required int targetScale,
    required String imageType,
  }) {
    final table =
        (_rules['scaleChain'] as Map<String, Object?>)[imageType]
            as Map<String, Object?>;
    final entry = table['$targetScale'] as String? ?? table['4'] as String;
    final parts = entry.split(':');
    final wantedNative = int.parse(parts[0].replaceAll('x', ''));
    final percent = int.parse(parts[1]);
    var native = model.nativeScales
        .where((s) => s >= wantedNative)
        .reduce((a, b) => a < b ? a : b);
    if (!model.supportsScale(native)) {
      native = model.nativeScales.first;
    }
    final resample = percent * wantedNative ~/ native;
    return (nativeScale: native, resamplePercent: resample);
  }

  /// Builds the complete plan or throws a typed PlannerException.
  PipelinePlan plan({
    required JobSpec spec,
    required DeviceProfile device,
    required ModelCatalog catalog,
    required int inputWidth,
    required int inputHeight,
  }) {
    final model = catalog.byId(spec.modelId);
    if (model == null || !model.available) {
      throw PlannerException('Selected model is not available');
    }
    if (model.engine == 'onnx' && !device.vulkanSupported) {
      throw PlannerException(
        'ONNX models need a capable device (GPU/Vulkan required)',
      );
    }

    final imageType = switch (spec.imageType) {
      ImageType.photo => 'photo',
      ImageType.anime => 'anime',
      ImageType.auto => 'photo', // classifier result is passed via spec
    };
    final chain = resolveScaleChain(
      model: model,
      targetScale: spec.scale,
      imageType: model.bestFor.contains('anime') ? 'anime' : imageType,
    );

    final tierKey = device.tier.name.toUpperCase();
    final tileSizes = _rules['tileSizes'] as Map<String, Object?>;
    var tileSize = spec.tileSize > 0
        ? spec.tileSize
        : (tileSizes[tierKey] as num).toInt();
    // Advanced level (Phase 3): an explicit overlap override wins over the
    // per-tile table; otherwise the data-file rule applies (also for the
    // benchmark-clamped and recovery-halved tile sizes below).
    final overlapMap = _rules['tileOverlap'] as Map<String, Object?>;
    final overlapByTile = _rules['overlapByTile'] as Map<String, Object?>;
    String overlapFor(int size) =>
        overlapByTile['$size'] as String? ?? 'medium';
    var overlap = spec.tileSize > 0 && spec.overlapExplicit
        ? (overlapMap[switch (spec.overlap) {
                    TileOverlap.none => 'none',
                    TileOverlap.small => 'small',
                    TileOverlap.medium => 'medium',
                    TileOverlap.large => 'large',
                  }]
                  as num)
              .toInt()
        : (overlapMap[overlapFor(tileSize)] as num).toInt();

    final threadsMap = _rules['threadCounts'] as Map<String, Object?>;
    var threads = spec.threadCount > 0
        ? spec.threadCount
        : (threadsMap[tierKey] as num).toInt();

    final useGpu = switch (spec.computeDevice) {
      ComputeDevice.gpu => device.vulkanSupported,
      ComputeDevice.cpu => false,
      ComputeDevice.auto => device.vulkanSupported,
    };

    final inputLimits = _rules['inputPixelLimit'] as Map<String, Object?>;
    final inputLimit = (inputLimits[tierKey] as num?) ?? 8000000;
    final outputCaps = _rules['outputCapMegapixels'] as Map<String, Object?>;
    // Advanced level (Phase 3): an explicit per-job cap override wins.
    final outputCap = spec.outputCapMegapixels > 0
        ? spec.outputCapMegapixels
        : (outputCaps[tierKey] as num?) ?? 40;

    var outW = inputWidth * spec.scale;
    var outH = inputHeight * spec.scale;
    final capMp = (outputCap * 1000000).toInt();
    if (outW * outH > capMp && capMp > 0) {
      final factor = (capMp / (outW * outH));
      outW = (outW * factor).floor();
      outH = (outH * factor).floor();
    }

    final etaTable = _rules['etaBaseMsPerTile'] as Map<String, Object?>;
    final modelEta = etaTable[model.id] as Map<String, Object?>?;
    int etaOf(String primary, String secondary) =>
        ((modelEta?[primary] ?? modelEta?[secondary] ?? 400) as num).toInt();
    var eta = modelEta == null
        ? 400
        : etaOf(useGpu ? 'gpu' : 'cpu', useGpu ? 'cpu' : 'gpu');

    // Recovery chain pre-pass: clamp tile size to what the device proved it
    // can handle (bench ms per tile above 2x baseline → smaller tiles).
    if (device.benchmarkMsPerTile > eta * 3 && tileSize > 128) {
      tileSize = 128;
      overlap = (overlapMap['medium'] as num).toInt();
      threads = (threads / 2).ceil();
      eta = eta * 2;
    }

    return PipelinePlan(
      model: model,
      nativeScale: chain.nativeScale,
      resamplePercent: chain.resamplePercent,
      extraResizeStep: chain.resamplePercent > 100,
      tileSize: tileSize,
      overlapPx: overlap,
      threadCount: threads.clamp(1, device.cpuCores),
      useGpu: useGpu,
      inputPixelLimit: inputLimit.toInt(),
      outputCapMegapixels: outputCap.toInt(),
      etaBaselineMsPerTile: eta,
      outputWidth: outW,
      outputHeight: outH,
    );
  }

  /// Honest ETA estimate in seconds for the whole job.
  int estimateSeconds(PipelinePlan plan, int inputWidth, int inputHeight) {
    final tile = plan.tileSize;
    final cols = (inputWidth / (tile - plan.overlapPx)).ceil().clamp(1, 10000);
    final rows = (inputHeight / (tile - plan.overlapPx)).ceil().clamp(1, 10000);
    final tiles = cols * rows;
    return (tiles * plan.etaBaselineMsPerTile / 1000).ceil();
  }
}

base class PlannerException extends AppException {
  const PlannerException(super.message);
}
