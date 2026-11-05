import '../../core/app_exception.dart';
import '../../engine/engine_types.dart';

/// Feasibility verdict for a Super Resolution run (phases.md Phase 3:
/// "feasibility check green/amber/red").
enum FeasibilityLevel { green, amber, red }

/// Typed reasons — the UI maps these to friendly strings; nothing raw
/// reaches a widget (rules.md §3).
enum FeasibilityReason {
  none,
  outputOverCap, // red: output megapixels exceed the device cap
  memoryTight, // amber: peak estimate above the comfortable share
  memoryExhausted, // red: peak estimate exceeds what fits
  verySlow, // amber: heavy model on this tier takes very long
  largeSource, // amber: input at the tier pixel limit
}

/// The result of a feasibility check.
class FeasibilityResult {
  const FeasibilityResult({
    required this.level,
    required this.reasons,
    required this.estimatedPeakMb,
    required this.availableMb,
    required this.outputMegapixels,
    required this.estimatedSeconds,
  });

  final FeasibilityLevel level;
  final List<FeasibilityReason> reasons;
  final int estimatedPeakMb;
  final int availableMb;
  final int outputMegapixels;
  final int estimatedSeconds;

  bool get blocked => level == FeasibilityLevel.red;
}

/// Estimates whether a planned Super Resolution run fits the device:
/// peak memory (input bitmap + output bitmap + tiles in flight) against
/// free RAM, and output megapixels against the tier cap. Pure function —
/// fully unit-testable, no device calls (rules.md §3).
class FeasibilityChecker {
  const FeasibilityChecker({this.decodeOverhead = 1.6});

  /// Codec + intermediate-copy multiplier over the raw pixel buffers.
  final double decodeOverhead;

  /// Shares of [DeviceProfile.freeRamMb] we allow the job to touch.
  /// Above [amberShare] the check degrades; above [redShare] it blocks.
  static const amberShare = 0.35;
  static const redShare = 0.60;

  /// Tile size assumed for the in-flight tile pair (worst common case).
  static const estimateTileSize = 512;

  FeasibilityResult check({
    required UpscaleMode mode,
    required int inputWidth,
    required int inputHeight,
    required int targetScale,
    required int outputCapMegapixels,
    required int etaSeconds,
    required DeviceProfile device,
    bool previewOnly = false,
  }) {
    final outW = inputWidth * targetScale;
    final outH = inputHeight * targetScale;
    final outMp = (outW * outH / 1000000).round();

    // Peak bytes: input RGBA + output RGBA + one tile pair in flight
    // (tile input + upscaled tile), times the decode overhead.
    final tile = estimateTileSize;
    final tileBytes = tile * tile * 4 * (1 + targetScale * targetScale);
    final peakBytes =
        (inputWidth * inputHeight * 4 + outW * outH * 4 + tileBytes) *
        decodeOverhead;
    final peakMb = (peakBytes / (1024 * 1024)).round();
    // A preview crop only processes the crop region — a small fraction.
    final effectivePeakMb = previewOnly ? (peakMb * 0.15).round() : peakMb;

    final reasons = <FeasibilityReason>[];
    var level = FeasibilityLevel.green;

    if (outputCapMegapixels > 0 && outMp > outputCapMegapixels) {
      reasons.add(FeasibilityReason.outputOverCap);
      level = FeasibilityLevel.red;
    }
    if (effectivePeakMb > device.freeRamMb * redShare) {
      reasons.add(FeasibilityReason.memoryExhausted);
      level = FeasibilityLevel.red;
    } else if (effectivePeakMb > device.freeRamMb * amberShare) {
      reasons.add(FeasibilityReason.memoryTight);
      if (level == FeasibilityLevel.green) level = FeasibilityLevel.amber;
    }
    if (!previewOnly) {
      if (mode == UpscaleMode.superResolution &&
          etaSeconds > 15 * 60 &&
          device.tier.index <= DeviceTier.t1.index) {
        reasons.add(FeasibilityReason.verySlow);
        if (level == FeasibilityLevel.green) level = FeasibilityLevel.amber;
      }
      if (inputWidth * inputHeight >= 16000000) {
        reasons.add(FeasibilityReason.largeSource);
        if (level == FeasibilityLevel.green) level = FeasibilityLevel.amber;
      }
    }

    return FeasibilityResult(
      level: level,
      reasons: reasons,
      estimatedPeakMb: effectivePeakMb,
      availableMb: device.freeRamMb,
      outputMegapixels: outMp,
      estimatedSeconds: etaSeconds,
    );
  }
}

/// Thrown when a Super Resolution run is blocked (red) — carries the typed
/// result so the UI can explain exactly why.
base class FeasibilityException extends AppException {
  const FeasibilityException(this.result) : super('Super Resolution blocked');
  final FeasibilityResult result;
}
