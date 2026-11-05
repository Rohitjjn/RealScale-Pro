import 'package:shared_preferences/shared_preferences.dart';

import '../../core/feature_gates.dart';

/// All user settings (tasks 1.17 + 2.10). Every toggle persists via
/// SharedPreferences; defaults follow prd §10 (privacy options off).
class SettingsService {
  SettingsService(this._prefs);

  final SharedPreferences _prefs;

  static const _kThemeMode = 'theme_mode'; // system | light | dark
  static const _kDynamicColor = 'dynamic_color';
  static const _kHaptics = 'haptics_enabled';
  static const _kKeepAwake = 'keep_screen_awake';
  static const _kGuideSeen = 'guide_seen';
  static const _kOnboardingDone = 'onboarding_done';
  static const _kDefaultMode = 'default_mode'; // upscale | superResolution
  static const _kComputePref = 'compute_preference'; // auto | gpu | cpu
  static const _kThrottleOnBattery = 'throttle_on_battery';
  static const _kWifiOnly = 'wifi_only_downloads';
  static const _kOutputFormat = 'output_format'; // sameAsSource | jpeg | png
  static const _kOutputQuality = 'output_quality';
  static const _kKeepMetadata = 'keep_metadata';
  static const _kOutputNaming = 'output_naming'; // realscale | original
  static const _kAnalyticsOptIn = 'privacy_anonymous_stats';
  static const _kCrashOptIn = 'privacy_crash_reports';
  static const _kLanguage = 'language';
  static const _kManualModelPhoto = 'manual_model_photo';
  static const _kManualModelAnime = 'manual_model_anime';
  static const _kManualScale = 'manual_scale';
  static const _kFaceEnabled = 'face_enhancement_enabled';

  static const _kSrWarningSeen = 'sr_warning_seen';

  // --- Advanced level (Phase 3) ---
  static const _kAdvTile = 'advanced_tile_size'; // 0 = auto
  static const _kAdvOverlap = 'advanced_overlap'; // none|small|medium|large
  static const _kAdvThreads = 'advanced_threads'; // 0 = auto
  static const _kAdvCompute = 'advanced_compute'; // auto|gpu|cpu
  static const _kAdvFormat = 'advanced_format'; // sameAsSource|jpeg|png
  static const _kAdvQuality = 'advanced_quality';
  static const _kAdvCap = 'advanced_output_cap'; // 0 = tier default

  // --- Theme & feel ---
  String get themeMode => _prefs.getString(_kThemeMode) ?? 'system';
  Future<void> setThemeMode(String v) => _prefs.setString(_kThemeMode, v);

  bool get dynamicColor => _prefs.getBool(_kDynamicColor) ?? true;
  Future<void> setDynamicColor(bool v) => _prefs.setBool(_kDynamicColor, v);

  bool get hapticsEnabled => _prefs.getBool(_kHaptics) ?? true;
  Future<void> setHapticsEnabled(bool v) => _prefs.setBool(_kHaptics, v);

  bool get keepScreenAwake => _prefs.getBool(_kKeepAwake) ?? true;
  Future<void> setKeepScreenAwake(bool v) => _prefs.setBool(_kKeepAwake, v);

  /// UI language: 'system' | 'en' | 'hi' (Phase 4 Hindi).
  String get language => _prefs.getString(_kLanguage) ?? 'system';
  Future<void> setLanguage(String v) => _prefs.setString(_kLanguage, v);

  // --- Onboarding ---
  bool get guideSeen => _prefs.getBool(_kGuideSeen) ?? false;
  Future<void> setGuideSeen(bool v) => _prefs.setBool(_kGuideSeen, v);

  bool get onboardingDone => _prefs.getBool(_kOnboardingDone) ?? false;
  Future<void> setOnboardingDone(bool v) => _prefs.setBool(_kOnboardingDone, v);

  bool get srWarningSeen => _prefs.getBool(_kSrWarningSeen) ?? false;
  Future<void> setSrWarningSeen(bool v) => _prefs.setBool(_kSrWarningSeen, v);

  // --- Defaults (task 2.10) ---
  String get defaultMode => _prefs.getString(_kDefaultMode) ?? 'upscale';
  Future<void> setDefaultMode(String v) => _prefs.setString(_kDefaultMode, v);

  String get computePreference => _prefs.getString(_kComputePref) ?? 'auto';
  Future<void> setComputePreference(String v) =>
      _prefs.setString(_kComputePref, v);

  bool get throttleOnBattery => _prefs.getBool(_kThrottleOnBattery) ?? true;
  Future<void> setThrottleOnBattery(bool v) =>
      _prefs.setBool(_kThrottleOnBattery, v);

  // --- Downloads ---
  bool get wifiOnly => _prefs.getBool(_kWifiOnly) ?? true;
  Future<void> setWifiOnly(bool v) => _prefs.setBool(_kWifiOnly, v);

  // --- Output ---
  String get outputFormat => _prefs.getString(_kOutputFormat) ?? 'sameAsSource';
  Future<void> setOutputFormat(String v) => _prefs.setString(_kOutputFormat, v);

  int get outputQuality => _prefs.getInt(_kOutputQuality) ?? 92;
  Future<void> setOutputQuality(int v) => _prefs.setInt(_kOutputQuality, v);

  bool get keepMetadata => _prefs.getBool(_kKeepMetadata) ?? true;
  Future<void> setKeepMetadata(bool v) => _prefs.setBool(_kKeepMetadata, v);

  String get outputNaming => _prefs.getString(_kOutputNaming) ?? 'realscale';
  Future<void> setOutputNaming(String v) => _prefs.setString(_kOutputNaming, v);

  // --- Privacy (off by default) ---
  bool get anonymousStats => _prefs.getBool(_kAnalyticsOptIn) ?? false;
  Future<void> setAnonymousStats(bool v) => _prefs.setBool(_kAnalyticsOptIn, v);

  bool get crashReports => _prefs.getBool(_kCrashOptIn) ?? false;
  Future<void> setCrashReports(bool v) => _prefs.setBool(_kCrashOptIn, v);

  // --- Manual choices (persisted, task 2.6) ---
  String? manualModelFor(String imageType) => _prefs.getString(
    imageType == 'anime' ? _kManualModelAnime : _kManualModelPhoto,
  );
  Future<void> setManualModel(String imageType, String modelId) =>
      _prefs.setString(
        imageType == 'anime' ? _kManualModelAnime : _kManualModelPhoto,
        modelId,
      );

  int get manualScale => _prefs.getInt(_kManualScale) ?? 2;
  Future<void> setManualScale(int v) => _prefs.setInt(_kManualScale, v);

  // --- Face enhancement (Phase 2) ---
  bool get faceEnabled =>
      FeatureGates.faceEnhancement && (_prefs.getBool(_kFaceEnabled) ?? false);
  Future<void> setFaceEnabled(bool v) => _prefs.setBool(_kFaceEnabled, v);

  // --- Advanced level (Phase 3) — overrides, 0/auto = planner decides ---
  int get advancedTileSize => _prefs.getInt(_kAdvTile) ?? 0;
  Future<void> setAdvancedTileSize(int v) => _prefs.setInt(_kAdvTile, v);

  String get advancedOverlap => _prefs.getString(_kAdvOverlap) ?? 'auto';
  Future<void> setAdvancedOverlap(String v) =>
      _prefs.setString(_kAdvOverlap, v);

  int get advancedThreads => _prefs.getInt(_kAdvThreads) ?? 0;
  Future<void> setAdvancedThreads(int v) => _prefs.setInt(_kAdvThreads, v);

  String get advancedCompute => _prefs.getString(_kAdvCompute) ?? 'auto';
  Future<void> setAdvancedCompute(String v) =>
      _prefs.setString(_kAdvCompute, v);

  String get advancedFormat => _prefs.getString(_kAdvFormat) ?? 'sameAsSource';
  Future<void> setAdvancedFormat(String v) => _prefs.setString(_kAdvFormat, v);

  int get advancedQuality => _prefs.getInt(_kAdvQuality) ?? 92;
  Future<void> setAdvancedQuality(int v) => _prefs.setInt(_kAdvQuality, v);

  int get advancedOutputCap => _prefs.getInt(_kAdvCap) ?? 0;
  Future<void> setAdvancedOutputCap(int v) => _prefs.setInt(_kAdvCap, v);

  /// Clears everything (used by tests).
  Future<void> reset() async {
    for (final k in _prefs.getKeys()) {
      await _prefs.remove(k);
    }
  }
}
