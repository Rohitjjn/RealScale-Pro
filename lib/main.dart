import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app/app.dart';
import 'services/model_manager/model_download_service.dart';
import 'services/settings_service.dart';
import 'state/providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  LicenseRegistry.addLicense(() async* {
    final String notices = await rootBundle.loadString(
      'assets/data/native_notices.txt',
    );
    yield LicenseEntryWithLineBreaks(const <String>[
      'ncnn',
      'waifu2x-ncnn-vulkan',
      'Real-ESRGAN-ncnn-vulkan',
    ], notices);
  });
  final SharedPreferences preferences = await SharedPreferences.getInstance();
  final SettingsService settings = SettingsService(preferences);
  final ModelDownloadService downloads = ModelDownloadService();
  await downloads.initialize();
  runApp(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWithValue(settings),
        modelDownloadProvider.overrideWithValue(downloads),
      ],
      child: const RealScaleApp(),
    ),
  );
}
