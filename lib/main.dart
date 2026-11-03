import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app/app.dart';
import 'services/settings_service.dart';
import 'state/providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final SharedPreferences preferences = await SharedPreferences.getInstance();
  final SettingsService settings = SettingsService(preferences);
  runApp(
    ProviderScope(
      overrides: <Override>[
        settingsProvider.overrideWithValue(settings),
      ],
      child: const RealScaleApp(),
    ),
  );
}
