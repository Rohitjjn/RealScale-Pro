/// RealScale-Pro — app shell.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/providers.dart';
import 'core/router.dart';
import 'core/theme.dart';

final routerProvider = Provider((ref) => buildRouter());

class RealScaleApp extends ConsumerWidget {
  const RealScaleApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider);
    final themeMode = switch (settings.themeMode) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: 'RealScale Pro',
      debugShowCheckedModeBanner: false,
      theme: buildRsTheme(Brightness.light),
      darkTheme: buildRsTheme(Brightness.dark),
      themeMode: themeMode,
      routerConfig: router,
    );
  }
}
