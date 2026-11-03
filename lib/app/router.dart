import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../services/permission_service/permission_service.dart';
import '../services/settings_service.dart';
import '../state/app_controller.dart';
import '../ui/screens/config/config_screen.dart';
import '../ui/screens/home/home_screen.dart';
import '../ui/screens/home/viewer_screen.dart';
import '../ui/screens/onboarding/onboarding_screens.dart';
import '../ui/screens/processing/processing_screen.dart';
import '../ui/screens/result/result_screens.dart';
import '../ui/screens/settings/settings_screens.dart';

GoRouter buildRouter({
  required SettingsService settings,
  required AppController controller,
  required PermissionService permissions,
}) =>
    GoRouter(
      initialLocation: '/splash',
      errorBuilder: (BuildContext context, GoRouterState state) => Scaffold(
        body: Center(
          child: FilledButton(
            onPressed: () => context.go('/home'),
            child: const Icon(Icons.home_rounded),
          ),
        ),
      ),
      routes: <RouteBase>[
        GoRoute(
          path: '/splash',
          builder: (BuildContext context, GoRouterState state) => BrandSplashScreen(
            onFinished: () => context.go(settings.onboardingComplete ? '/home' : '/guide'),
          ),
        ),
        GoRoute(
          path: '/guide',
          builder: (BuildContext context, GoRouterState state) =>
              GuideScreen(replay: state.uri.queryParameters['replay'] == 'true'),
        ),
        GoRoute(
          path: '/permissions/photos',
          builder: (_, __) => PermissionPrimerScreen(isPhotos: true, permissions: permissions),
        ),
        GoRoute(
          path: '/permissions/notifications',
          builder: (_, __) => PermissionPrimerScreen(isPhotos: false, permissions: permissions),
        ),
        GoRoute(path: '/device-check', builder: (_, __) => DeviceCheckScreen(controller: controller)),
        GoRoute(path: '/setup', builder: (_, __) => SetupScreen(settings: settings)),
        GoRoute(
          path: '/home',
          builder: (_, __) => HomeScreen(
            controller: controller,
            settings: settings,
            permissions: permissions,
          ),
        ),
        GoRoute(path: '/viewer', builder: (_, __) => ViewerScreen(controller: controller)),
        GoRoute(path: '/config', builder: (_, __) => ConfigScreen(controller: controller, settings: settings)),
        GoRoute(path: '/processing', builder: (_, __) => ProcessingScreen(controller: controller, settings: settings)),
        GoRoute(path: '/result', builder: (_, __) => ResultScreen(controller: controller)),
        GoRoute(path: '/upscales', builder: (_, __) => MyUpscalesScreen(controller: controller)),
        GoRoute(path: '/settings', builder: (_, __) => SettingsScreen(settings: settings)),
        GoRoute(path: '/about', builder: (_, __) => const AboutScreen()),
        GoRoute(path: '/models', builder: (_, __) => const SimpleInfoScreen(kind: 'models')),
        GoRoute(path: '/help', builder: (_, __) => const SimpleInfoScreen(kind: 'help')),
      ],
    );
