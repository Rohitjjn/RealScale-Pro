import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persisted user choices with privacy-safe defaults.
class SettingsService extends ChangeNotifier {
  SettingsService(this._preferences);

  static const String _onboardingKey = 'onboarding_complete';
  static const String _themeKey = 'theme_mode';
  static const String _hapticsKey = 'haptics';
  static const String _keepAwakeKey = 'keep_awake';
  static const String _dynamicColorKey = 'dynamic_color';
  static const String _superResolutionSeenKey = 'sr_notice_seen';

  final SharedPreferences _preferences;

  bool get onboardingComplete => _preferences.getBool(_onboardingKey) ?? false;
  bool get haptics => _preferences.getBool(_hapticsKey) ?? true;
  bool get keepAwake => _preferences.getBool(_keepAwakeKey) ?? true;
  bool get dynamicColor => _preferences.getBool(_dynamicColorKey) ?? true;
  bool get superResolutionNoticeSeen =>
      _preferences.getBool(_superResolutionSeenKey) ?? false;

  ThemeMode get themeMode {
    final String value = _preferences.getString(_themeKey) ?? 'system';
    return ThemeMode.values.firstWhere(
      (ThemeMode mode) => mode.name == value,
      orElse: () => ThemeMode.system,
    );
  }

  Future<void> completeOnboarding() async {
    await _preferences.setBool(_onboardingKey, true);
    notifyListeners();
  }

  Future<void> setThemeMode(ThemeMode value) async {
    await _preferences.setString(_themeKey, value.name);
    notifyListeners();
  }

  Future<void> setHaptics(bool value) async {
    await _preferences.setBool(_hapticsKey, value);
    notifyListeners();
  }

  Future<void> setKeepAwake(bool value) async {
    await _preferences.setBool(_keepAwakeKey, value);
    notifyListeners();
  }

  Future<void> setDynamicColor(bool value) async {
    await _preferences.setBool(_dynamicColorKey, value);
    notifyListeners();
  }

  Future<void> markSuperResolutionNoticeSeen() async {
    await _preferences.setBool(_superResolutionSeenKey, true);
    notifyListeners();
  }

  Future<void> resetGuide() async {
    await _preferences.setBool(_onboardingKey, false);
    notifyListeners();
  }
}
