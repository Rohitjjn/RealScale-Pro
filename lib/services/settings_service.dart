/// RealScale-Pro — user settings persistence.
library;

import 'package:shared_preferences/shared_preferences.dart';

class SettingsService {
  SettingsService(this._prefs);
  final SharedPreferences _prefs;

  static const _kJpegQuality = 'output.jpegQuality';
  static const _kDefaultMode = 'processing.defaultMode';
  static const _kOnboardingDone = 'onboarding.done';
  static const _kThemeMode = 'appearance.themeMode';
  static const _kModelConsent = 'models.downloadConsent';
  static const _kPreferGpu = 'processing.preferGpu';

  int get jpegQuality => _prefs.getInt(_kJpegQuality) ?? 92;

  /// Quality is constrained to the honest 90–96 band.
  set jpegQuality(int q) => _prefs.setInt(_kJpegQuality, q.clamp(90, 96));

  String get defaultMode => _prefs.getString(_kDefaultMode) ?? 'upscale';
  set defaultMode(String m) => _prefs.setString(_kDefaultMode, m);

  bool get onboardingDone => _prefs.getBool(_kOnboardingDone) ?? false;
  set onboardingDone(bool v) => _prefs.setBool(_kOnboardingDone, v);

  String get themeMode => _prefs.getString(_kThemeMode) ?? 'system';
  set themeMode(String m) => _prefs.setString(_kThemeMode, m);

  /// Explicit user consent for on-demand model downloads.
  bool get modelDownloadConsent => _prefs.getBool(_kModelConsent) ?? false;
  set modelDownloadConsent(bool v) => _prefs.setBool(_kModelConsent, v);

  /// Experimental GPU (Vulkan) preference. OFF by default: the CPU path
  /// is the verified backend; GPU runs only on devices that expose a
  /// usable Vulkan driver and fall back to CPU on any error.
  bool get preferGpu => _prefs.getBool(_kPreferGpu) ?? false;
  set preferGpu(bool v) => _prefs.setBool(_kPreferGpu, v);
}
