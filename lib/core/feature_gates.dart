/// Build-time feature gates (architecture.md §6). Phase-gated features are
/// compile-time constants so gated code is fully tree-shaken from the app.
class FeatureGates {
  const FeatureGates._();

  /// Face enhancement pipeline — unlocked in Phase 2.
  static const bool faceEnhancement = true;

  /// Manual level — unlocked in Phase 2 (Advanced stays Phase 3).
  static const bool manualLevel = true;

  /// Advanced level — unlocked in Phase 3.
  static const bool advancedLevel = true;

  /// Super Resolution Mode pipeline — unlocked in Phase 3 (lossless
  /// pipeline, feasibility check, preview crop).
  static const bool superResolutionMode = true;

  /// ONNX engine + Ultra models — Phase 3. The engine adapter ships now;
  /// Ultra catalog rows stay `comingSoon` until audited weights land
  /// (phases.md allows the "Coming soon" path when integration is not
  /// verifiable on real hardware).
  static const bool onnxEngine = true;

  /// Video upscaling — never. Zero video code; locked card + sheet only.
  static const bool video = false;
}
