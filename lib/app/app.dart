import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../l10n/app_localizations.dart';
import '../services/model_manager/model_manager.dart';
import '../services/permission_service/permission_service.dart';
import '../services/settings_service.dart';
import '../state/app_controller.dart';
import '../state/providers.dart';
import '../ui/theme/app_theme.dart';
import 'router.dart';

class RealScaleApp extends ConsumerStatefulWidget {
  const RealScaleApp({super.key});

  @override
  ConsumerState<RealScaleApp> createState() => _RealScaleAppState();
}

class _RealScaleAppState extends ConsumerState<RealScaleApp> {
  late final SettingsService _settings;
  late final AppController _controller;
  late final ModelManager _models;
  late final GoRouter _router;

  @override
  void initState() {
    super.initState();
    _settings = ref.read(settingsProvider);
    _controller = ref.read(appControllerProvider);
    _models = ref.read(modelManagerProvider);
    _router = buildRouter(
      settings: _settings,
      controller: _controller,
      models: _models,
      permissions: const PermissionService(),
    );
  }

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _settings,
    builder: (BuildContext context, Widget? child) => MaterialApp.router(
      debugShowCheckedModeBanner: false,
      onGenerateTitle: (BuildContext context) =>
          AppLocalizations.of(context).appName,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: _settings.themeMode,
      routerConfig: _router,
      localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
    ),
  );
}
