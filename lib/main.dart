/// RealScale-Pro — entry point.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';
import 'core/providers.dart';
import 'services/storage_service.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  // Crash-recovery hygiene: remove leftover temp files from interrupted
  // saves. Best effort — a failure here never blocks startup.
  try {
    await StorageService().cleanTempFiles();
  } catch (_) {}
  runApp(ProviderScope(
    overrides: [
      sharedPreferencesProvider.overrideWithValue(prefs),
    ],
    child: const RealScaleApp(),
  ));
}
