/// RealScale-Pro — device-adaptive pipeline planning.
///
/// Given the inspected image, the device profile, the mode and the
/// model registry, computes a concrete, honest plan:
///   * model choice (compact vs quality by tier and mode),
///   * tile size / overlap / context,
///   * output format + JPEG quality (90–96, only where JPEG applies),
///   * peak-RAM estimate (Dart mirror of the native formula; an
///     integration test asserts the two agree),
///   * warnings and an explicit safer alternative when unsafe.
///
/// Downsampling is NEVER automatic: in Upscale Mode it only appears in
/// the safer-alternative proposal the user must accept explicitly. In
/// Super Resolution Mode it is never proposed.
library;

import '../domain/models.dart';

class PipelinePlanner {
  const PipelinePlanner();

  /// Peak-RAM estimate mirroring rs_estimate_peak_memory in
  /// native/src/abi.cpp. Kept in sync by test
  /// (planner_estimate_matches_native).
  static int estimatePeakMemoryBytes(
      int inW, int inH, int scale, int tileSize) {
    if (inW <= 0 || inH <= 0 || scale < 2 || scale > 4) {
      return -1;
    }
    final tile = tileSize > 0 ? tileSize : 128;
    final outTile = tile * scale;
    final inTileBytes = tile * tile * 3 * 4 * 3;
    final outTileBytes = outTile * outTile * 3 * 4;
    final accBytes = inW * inH * scale * scale * 4 * 4;
    final outRgba = inW * inH * scale * scale * 4;
    final inRgba = inW * inH * 4;
    const weightsHeadroom = 200 * 1024 * 1024;
    return inTileBytes * 2 + outTileBytes * 3 + accBytes + outRgba +
        inRgba + weightsHeadroom;
  }

  PipelinePlan plan({
    required ImageMetadata image,
    required DeviceProfile device,
    required ProcessingMode mode,
    required ModelInfo model,
    int? scaleOverride,
    int jpegQuality = 92,
  }) {
    final scale = scaleOverride ?? model.scale;
    final warnings = <String>[];

    // ---- tile geometry (model family aware) ------------------------
    final isCompact = model.family.contains('compact');
    var tileSize = isCompact ? 128 : 128;
    var overlap = isCompact ? 10 : 16;
    var context = isCompact ? 2 : 8;
    switch (device.tier) {
      case DeviceTier.lowResource:
        tileSize = isCompact ? 96 : 64;
      case DeviceTier.balanced:
        tileSize = isCompact ? 128 : 96;
      case DeviceTier.highPerformance:
        tileSize = isCompact ? 160 : 128;
    }

    // ---- output format ---------------------------------------------
    final outFormat = switch (mode) {
      // Upscale Mode: JPEG for opaque photographic sources (smaller,
      // practical), PNG when transparency demands lossless.
      ProcessingMode.upscale =>
        image.hasAlpha || image.format == 'PNG' ? OutputFormat.png : OutputFormat.jpeg,
      // Super Resolution Mode: lossless by default.
      ProcessingMode.superResolution => OutputFormat.png,
    };
    final quality = outFormat == OutputFormat.jpeg
        ? jpegQuality.clamp(90, 96)
        : null;

    final outW = image.width * scale;
    final outH = image.height * scale;
    var peak = estimatePeakMemoryBytes(image.width, image.height, scale, tileSize);

    // ---- safety analysis + tile reduction ---------------------------
    final budgetBytes = device.inferenceBudgetMb * 1024 * 1024;
    PipelinePlan? safer;

    if (peak > budgetBytes) {
      // First line of defense: reduce tiles (safe for both modes).
      for (final reduced in [tileSize ~/ 2, tileSize ~/ 4, 32]) {
        if (reduced < 32) {
          continue;
        }
        final candidate =
            estimatePeakMemoryBytes(image.width, image.height, scale, reduced);
        if (candidate <= budgetBytes) {
          warnings.add(
              'Large output for this device — tiles reduced to ${reduced}px '
              'to stay within the memory budget.');
          tileSize = reduced;
          overlap = (overlap ~/ 2).clamp(8, overlap);
          context = (context ~/ 2).clamp(2, context);
          peak = candidate;
          break;
        }
      }
    }

    if (peak > budgetBytes) {
      switch (mode) {
        case ProcessingMode.superResolution:
          // Never downsample. Propose the honest alternative: a lighter
          // model with the same scale, or refuse.
          warnings.add(
              'This device cannot safely process ${image.width}×${image.height} '
              'at ${scale}x without risking a crash. Super Resolution Mode '
              'does not downsample; try a smaller image or a device with '
              'more memory.');
        case ProcessingMode.upscale:
          // Propose an EXPLICIT, user-confirmed downsample target.
          final targetEdge = _fitEdge(image, budgetBytes, scale);
          safer = targetEdge == null
              ? null
              : PipelinePlan(
                  mode: mode,
                  modelId: model.id,
                  scale: scale,
                  tileSize: 32,
                  tileOverlap: 8,
                  contextMargin: 2,
                  outputFormat: outFormat,
                  jpegQuality: quality,
                  estimatedPeakRamMb: -1,
                  estimatedOutputWidth:
                      (image.width * targetEdge ~/ _longEdge(image)) * scale,
                  estimatedOutputHeight:
                      (image.height * targetEdge ~/ _longEdge(image)) * scale,
                  warnings: [
                    'Proposed: downsample the source to ~${targetEdge}px '
                    'before upscaling (explicit step, requires your '
                    'confirmation). Output will be smaller but sharper.'
                  ],
                );
          warnings.add(
              'Even with ${tileSize}px tiles the estimated peak memory '
              '(${peak ~/ 1024 ~/ 1024} MB) exceeds the safe budget '
              '(${budgetBytes ~/ 1024 ~/ 1024} MB).');
      }
    }

    return PipelinePlan(
      mode: mode,
      modelId: model.id,
      scale: scale,
      tileSize: tileSize,
      tileOverlap: overlap,
      contextMargin: context,
      outputFormat: outFormat,
      jpegQuality: quality,
      estimatedPeakRamMb: peak ~/ 1024 ~/ 1024,
      estimatedOutputWidth: outW,
      estimatedOutputHeight: outH,
      warnings: warnings,
      saferAlternative: safer,
    );
  }

  /// Chooses the default model for a tier + mode combination from the
  /// registry (compact for low/balanced, flagship for high tier).
  ModelInfo chooseModel(
      List<ModelInfo> registry, DeviceProfile device, ProcessingMode mode) {
    ModelInfo byId(String id) =>
        registry.firstWhere((m) => m.id == id, orElse: () => registry.first);
    if (mode == ProcessingMode.superResolution &&
        device.tier == DeviceTier.highPerformance) {
      return byId('realesrgan-x4plus');
    }
    return byId('realesr-animevideov3-x4');
  }

  static int _longEdge(ImageMetadata image) =>
      image.width > image.height ? image.width : image.height;

  static int? _fitEdge(ImageMetadata image, int budgetBytes, int scale) {
    for (final edge in [4096, 3072, 2560, 2048, 1600, 1280, 1024, 768]) {
      final w = image.width * edge ~/ _longEdge(image);
      final h = image.height * edge ~/ _longEdge(image);
      if (w <= 0 || h <= 0) {
        continue;
      }
      final peak = estimatePeakMemoryBytes(w, h, scale, 32);
      if (peak <= budgetBytes) {
        return edge;
      }
    }
    return null;
  }
}
