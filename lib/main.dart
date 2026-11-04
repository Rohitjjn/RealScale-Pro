import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app/app.dart';
import 'services/device_profiler/device_profiler.dart';
import 'services/model_manager/file_model_store.dart';
import 'services/model_manager/model_catalog.dart';
import 'services/model_manager/model_download_service.dart';
import 'services/model_manager/model_manager.dart';
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
  final ModelCatalog catalog = ModelCatalog.parse(
    await rootBundle.loadString('assets/data/model_catalog.json'),
  );
  final Directory support = await getApplicationSupportDirectory();
  final ModelManager models = ModelManager(
    catalog: catalog,
    transfers: downloads,
    store: FileModelStore(
      Directory(
        '${support.path}${Platform.pathSeparator}models'
        '${Platform.pathSeparator}installed',
      ),
      freeBytesReader: () async {
        final int freeMb = (await const DeviceProfiler().profile()).freeStorageMb;
        return freeMb * 1024 * 1024;
      },
    ),
  );
  await models.initialize();
  runApp(
    ProviderScope(
      overrides: [
        settingsProvider.overrideWithValue(settings),
        modelDownloadProvider.overrideWithValue(downloads),
        modelManagerProvider.overrideWithValue(models),
      ],
      child: const RealScaleApp(),
    ),
  );
}
