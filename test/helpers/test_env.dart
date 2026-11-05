import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:realscale_pro/services/image_service/image_service.dart';
import 'package:realscale_pro/services/settings_service/settings_service.dart';
import 'package:realscale_pro/services/storage_service/storage_service.dart';

/// Fake path_provider so services run in plain `flutter test`.
class FakePathProviderPlatform extends Fake
    with MockPlatformInterfaceMixin
    implements PathProviderPlatform {
  @override
  Future<String?> getApplicationDocumentsPath() async =>
      Directory.systemTemp.createTempSync('rs_test_docs').path;

  @override
  Future<String?> getTemporaryPath() async =>
      Directory.systemTemp.createTempSync('rs_test_tmp').path;

  @override
  Future<String?> getExternalStoragePath() async =>
      Directory.systemTemp.createTempSync('rs_test_ext').path;
}

/// Shared test environment: fake plugins + fresh services.
class TestEnv {
  static Future<SettingsService> settings() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    return SettingsService(prefs);
  }

  static Future<StorageService> storage() async {
    sqfliteFfiInit();
    final service = StorageService();
    await service.db; // force open with ffi factory
    return service;
  }

  static ImageService imageService() {
    final tmp = Directory.systemTemp.createTempSync('rs_img_tmp');
    return ImageService(tempDir: tmp.path);
  }
}

void registerFakePathProvider() {
  PathProviderPlatform.instance = FakePathProviderPlatform();
}
