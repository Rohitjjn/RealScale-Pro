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
}) => GoRouter(
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
        onFinished: () =>
            context.go(settings.onboardingComplete ? '/home' : '/guide'),
      ),
    ),
    GoRoute(
      path: '/guide',
      builder: (BuildContext context, GoRouterState state) =>
          GuideScreen(replay: state.uri.queryParameters['replay'] == 'true'),
    ),
    GoRoute(
      path: '/permissions/photos',
      builder: (_, _) =>
          PermissionPrimerScreen(isPhotos: true, permissions: permissions),
    ),
    GoRoute(
      path: '/permissions/notifications',
      builder: (_, _) =>
          PermissionPrimerScreen(isPhotos: false, permissions: permissions),
    ),
    GoRoute(
      path: '/device-check',
      builder: (_, _) => DeviceCheckScreen(controller: controller),
    ),
    GoRoute(
      path: '/setup',
      builder: (_, _) => SetupScreen(settings: settings),
    ),
    GoRoute(
      path: '/home',
      builder: (_, _) => HomeScreen(
        controller: controller,
        settings: settings,
        permissions: permissions,
      ),
    ),
    GoRoute(
      path: '/viewer',
      builder: (_, _) => ViewerScreen(controller: controller),
    ),
    GoRoute(
      path: '/config',
      builder: (_, _) =>
          ConfigScreen(controller: controller, settings: settings),
    ),
    GoRoute(
      path: '/processing',
      builder: (_, _) =>
          ProcessingScreen(controller: controller, settings: settings),
    ),
    GoRoute(
      path: '/result',
      builder: (_, _) => ResultScreen(controller: controller),
    ),
    GoRoute(
      path: '/upscales',
      builder: (_, _) => MyUpscalesScreen(controller: controller),
    ),
    GoRoute(
      path: '/settings',
      builder: (_, _) => SettingsScreen(settings: settings),
    ),
    GoRoute(path: '/about', builder: (_, _) => const AboutScreen()),
    GoRoute(
      path: '/models',
      builder: (_, _) => const SimpleInfoScreen(kind: 'models'),
    ),
    GoRoute(
      path: '/help',
      builder: (_, _) => const SimpleInfoScreen(kind: 'help'),
    ),
  ],
);
