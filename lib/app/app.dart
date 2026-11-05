import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../l10n/app_localizations.dart';
import '../state/providers.dart';
import '../ui/screens/onboarding/splash_screen.dart';
import '../ui/screens/config/config_screen.dart';
import '../ui/screens/home/gallery_screen.dart';
import '../ui/screens/home/home_screen.dart';
import '../ui/screens/home/more_sheet_screen.dart';
import '../ui/screens/home/viewer_screen.dart';
import '../ui/screens/models/model_manager_screen.dart';
import '../ui/screens/onboarding/device_check_screen.dart';
import '../ui/screens/onboarding/guide_screen.dart';
import '../ui/screens/onboarding/permission_primers_screen.dart';
import '../ui/screens/onboarding/setup_screen.dart';
import '../ui/screens/processing/processing_screen.dart';
import '../ui/screens/result/my_upscales_screen.dart';
import '../ui/screens/result/result_screen.dart';
import '../ui/screens/settings/screens_extra.dart';
import '../ui/screens/settings/settings_screen.dart';
import '../ui/theme/app_theme.dart';
import '../ui/theme/tokens.dart' show RsMotion;

/// Root widget: theme wiring, localization, dynamic color (Material You).
class RealScaleApp extends ConsumerWidget {
  const RealScaleApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeNotifier);
    final settings = ref.watch(settingsProvider);
    return MaterialYouColors(
      enabled: settings.dynamicColor,
      builder: (context, lightDynamic, darkDynamic) {
        return MaterialApp.router(
          title: 'RealScale-Pro',
          debugShowCheckedModeBanner: false,
          routerConfig: ref.watch(routerProvider),
          theme: lightDynamic != null
              ? mergeBrand(lightDynamic)
              : AppTheme.light(),
          darkTheme: darkDynamic != null
              ? mergeBrand(darkDynamic)
              : AppTheme.dark(),
          themeMode: themeMode,
          localizationsDelegates: const [
            AppLocalizations.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en'), Locale('hi')],
          locale: switch (settings.language) {
            'en' => const Locale('en'),
            'hi' => const Locale('hi'),
            _ => null, // follow the system
          },
        );
      },
    );
  }

  /// Keeps the brand gradient + accent warm even under Material You.
  ThemeData mergeBrand(ColorScheme dynamicScheme) {
    final base = dynamicScheme.brightness == Brightness.dark
        ? AppTheme.dark()
        : AppTheme.light();
    return base.copyWith(
      colorScheme: base.colorScheme.copyWith(
        primary: dynamicScheme.primary,
        secondary: dynamicScheme.secondary,
      ),
    );
  }
}

/// Reads Material You dynamic colors with COMPLETE error handling.
///
/// The stock `DynamicColorBuilder` only catches `PlatformException`; on
/// platforms without the channel (old Android, desktop, tests) the call
/// throws `MissingPluginException` which would escape as an unhandled error
/// (rules.md golden rule 5: never crash). This widget degrades to the brand
/// themes instead.
class MaterialYouColors extends StatefulWidget {
  const MaterialYouColors({
    super.key,
    required this.enabled,
    required this.builder,
  });

  final bool enabled;
  final Widget Function(BuildContext, ColorScheme?, ColorScheme?) builder;

  @override
  State<MaterialYouColors> createState() => _MaterialYouColorsState();
}

class _MaterialYouColorsState extends State<MaterialYouColors> {
  ColorScheme? _light;
  ColorScheme? _dark;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (!widget.enabled) return;
    try {
      final palette = await DynamicColorPlugin.getCorePalette();
      if (!mounted || palette == null) return;
      setState(() {
        _light = palette.toColorScheme();
        _dark = palette.toColorScheme(brightness: Brightness.dark);
      });
    } catch (_) {
      // Any failure (missing plugin, unsupported OS) → brand themes stay.
    }
  }

  @override
  Widget build(BuildContext context) => widget.builder(context, _light, _dark);
}

/// Haptics helper honoring the Settings toggle (design-system §7).
class Haptics {
  static bool _enabled(WidgetRef ref) =>
      ref.read(settingsProvider).hapticsEnabled;

  static void selection(WidgetRef ref) {
    if (_enabled(ref)) HapticFeedback.selectionClick();
  }

  static void light(WidgetRef ref) {
    if (_enabled(ref)) HapticFeedback.lightImpact();
  }

  static void medium(WidgetRef ref) {
    if (_enabled(ref)) HapticFeedback.mediumImpact();
  }

  static void success(WidgetRef ref) {
    if (_enabled(ref)) HapticFeedback.heavyImpact();
  }

  static void warning(WidgetRef ref) {
    if (_enabled(ref)) HapticFeedback.vibrate();
  }
}

/// Route names/paths used across screens.
abstract final class Routes {
  static const splash = '/';
  static const guide = '/guide';
  static const primers = '/primers';
  static const deviceCheck = '/device-check';
  static const setup = '/setup';
  static const home = '/home';
  static const gallery = '/gallery';
  static const viewer = '/viewer';
  static const more = '/more';
  static const config = '/config';
  static const processing = '/processing';
  static const result = '/result';
  static const myUpscales = '/my-upscales';
  static const modelManager = '/models';
  static const settings = '/settings';
  static const about = '/about';
  static const licenses = '/licenses';
  static const privacy = '/privacy';
}

/// Extra data passed between screens (asset id or file path).
class PhotoArg {
  const PhotoArg({this.assetId, this.path});
  final String? assetId;
  final String? path;
}

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: Routes.splash,
    routes: [
      GoRoute(
        path: Routes.splash,
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: Routes.guide,
        builder: (context, state) => const GuideScreen(),
      ),
      GoRoute(
        path: Routes.primers,
        builder: (context, state) => const PermissionPrimersScreen(),
      ),
      GoRoute(
        path: Routes.deviceCheck,
        builder: (context, state) => const DeviceCheckScreen(),
      ),
      GoRoute(
        path: Routes.setup,
        builder: (context, state) => const SetupScreen(),
      ),
      GoRoute(
        path: Routes.home,
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: Routes.gallery,
        builder: (context, state) => const GalleryScreen(),
      ),
      GoRoute(
        path: Routes.viewer,
        builder: (context, state) =>
            ViewerScreen(initialIndex: (state.extra as int?) ?? 0),
      ),
      GoRoute(
        path: Routes.more,
        pageBuilder: (context, state) => CustomTransitionPage(
          key: state.pageKey,
          child: const MoreSheetScreen(),
          opaque: false,
          barrierColor: Colors.black38,
          transitionsBuilder: (c, a, s, child) => SlideTransition(
            position: Tween(
              begin: const Offset(0, 1),
              end: Offset.zero,
            ).animate(CurvedAnimation(parent: a, curve: RsMotion.emphasized)),
            child: child,
          ),
        ),
      ),
      GoRoute(
        path: Routes.config,
        builder: (context, state) => const ConfigScreen(),
      ),
      GoRoute(
        path: Routes.processing,
        builder: (context, state) => const ProcessingScreen(),
      ),
      GoRoute(
        path: Routes.result,
        builder: (context, state) => const ResultScreen(),
      ),
      GoRoute(
        path: Routes.myUpscales,
        builder: (context, state) => const MyUpscalesScreen(),
      ),
      GoRoute(
        path: Routes.modelManager,
        builder: (context, state) => const ModelManagerScreen(),
      ),
      GoRoute(
        path: Routes.settings,
        builder: (context, state) => const SettingsScreen(),
      ),
      GoRoute(
        path: Routes.about,
        builder: (context, state) => const AboutScreen(),
      ),
      GoRoute(
        path: Routes.licenses,
        builder: (context, state) => const LicensesScreen(),
      ),
      GoRoute(
        path: Routes.privacy,
        builder: (context, state) => const PrivacyScreen(),
      ),
    ],
  );
});
