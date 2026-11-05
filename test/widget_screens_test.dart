import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:photo_manager/photo_manager.dart';
import 'package:file_picker/file_picker.dart';
import 'package:file_picker_platform_interface/file_picker_platform_interface.dart';
import 'package:go_router/go_router.dart';
import 'package:realscale_pro/app/app.dart';
import 'package:realscale_pro/engine/upscale_engine.dart'
    show JobStage, SettingLevel, UpscaleMode;
import 'package:realscale_pro/l10n/app_localizations.dart';
import 'package:realscale_pro/state/providers.dart';
import 'package:realscale_pro/ui/theme/app_theme.dart';
import 'package:realscale_pro/ui/screens/config/config_screen.dart';
import 'package:realscale_pro/ui/screens/home/home_screen.dart';
import 'package:realscale_pro/ui/screens/models/model_manager_screen.dart';
import 'package:realscale_pro/ui/screens/processing/processing_screen.dart';
import 'package:riverpod/misc.dart' show Override;
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/test_services.dart';

const _l10nDelegates = [
  AppLocalizations.delegate,
  GlobalMaterialLocalizations.delegate,
  GlobalWidgetsLocalizations.delegate,
  GlobalCupertinoLocalizations.delegate,
];

Widget _wrap(Widget child, List<Override> overrides) => ProviderScope(
  overrides: overrides,
  child: MaterialApp(
    localizationsDelegates: _l10nDelegates,
    supportedLocales: const [Locale('en')],
    theme: AppTheme.light(),
    darkTheme: AppTheme.dark(),
    home: child,
  ),
);

Future<Services> _services() async {
  final root = Directory.systemTemp.createTempSync('rs_widget');
  return buildTestServices(root.path);
}

/// Pumps a bounded number of frames; shimmer/indicator animations never
/// settle, so tests must not wait for quiescence.
Future<void> settle(WidgetTester tester, {int frames = 30}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 32));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('theme: Material 3 brand palette in light and dark', (
    tester,
  ) async {
    final light = AppTheme.light();
    final dark = AppTheme.dark();
    expect(light.useMaterial3, isTrue);
    expect(dark.useMaterial3, isTrue);
    // design-system.md: primary #4F46E5 light, #818CF8 dark.
    expect(light.colorScheme.primary, const Color(0xFF4F46E5));
    expect(dark.colorScheme.primary, const Color(0xFF818CF8));
    // Dark surfaces are near-black, not pure black.
    expect(dark.scaffoldBackgroundColor, const Color(0xFF0E0F13));
  });

  testWidgets('onboarding: splash lands on guide, Skip reaches Home', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({'dynamic_color': false});
    final services = (await tester.runAsync(_services))!;
    // Real file IO cannot complete in the fake-async zone — runAsync lets it.
    await tester.runAsync(() => services.modelManager.initialize());
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ...screenOverrides(services),
          libraryProvider.overrideWith(_DeniedLibrary.new),
        ],
        child: const RealScaleApp(),
      ),
    );
    await tester.pump();
    // Splash shows the wordmark, then routes to the guide.
    expect(find.text('RealScale-Pro'), findsWidgets);
    await tester.pump(const Duration(milliseconds: 1100));
    await settle(tester);
    // Guide first slide + Skip present (task 1.4): 4 slides, indicator.
    expect(find.text('Skip'), findsOneWidget);
    expect(find.byType(PageView), findsOneWidget);
    // Skip → primers screen renders (task 1.5 primer, not a system dialog).
    await tester.tap(find.text('Skip'));
    await settle(tester);
    expect(find.text('Allow photo access?'), findsOneWidget);
    expect(find.text('Not now'), findsOneWidget);
  });

  testWidgets('home: tiles, mode cards, locked video row, recents strip', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false, // no platform channel in widget tests
      'onboarding_done': true,
      'keep_screen_awake': false,
    });
    final services = (await tester.runAsync(_services))!;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ...screenOverrides(services),
          libraryProvider.overrideWith(_ReadyLibrary.new),
        ],
        child: const RealScaleApp(),
      ),
    );
    await tester.pump(const Duration(milliseconds: 1100));
    await settle(tester);

    // Import tiles (task 1.10).
    expect(find.text('Gallery'), findsOneWidget);
    expect(find.text('Files'), findsOneWidget);
    // Mode cards with PRO badge on Super Resolution (task 1.11).
    expect(find.text('Super Resolution'), findsOneWidget);
    // Locked video row, no video code (task 1.12).
    expect(find.text('Video Upscale'), findsOneWidget);
    // Recents strip shows the fake photo's resolution badge.
    expect(find.text('1920×1080'), findsOneWidget);
    expect(find.text('Recent photos'), findsOneWidget);
  });

  testWidgets('home: permission-denied state shows friendly card', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false, // no platform channel in widget tests
      'onboarding_done': true,
      'keep_screen_awake': false,
    });
    final services = (await tester.runAsync(_services))!;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ...screenOverrides(services),
          libraryProvider.overrideWith(_DeniedLibrary.new),
        ],
        child: const RealScaleApp(),
      ),
    );
    await tester.pump(const Duration(milliseconds: 1100));
    await settle(tester);
    // Deny photos → "Allow photo access" card (prd §3.6).
    expect(
      find.text('Allow photo access to see your recent photos'),
      findsOneWidget,
    );
  });

  testWidgets('model manager: rows, filters and honest storage bar', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({'dynamic_color': false});
    final services = (await tester.runAsync(_services))!;
    // Real file IO cannot complete in the fake-async zone — runAsync lets it.
    await tester.runAsync(() => services.modelManager.initialize());
    await tester.pumpWidget(
      _wrap(ModelManagerScreen(), screenOverrides(services)),
    );
    await settle(tester);

    // Catalog rows render with statuses (task 2.4): blocked rows visible
    // with their honest subtitle.
    expect(find.text('Model Manager'), findsOneWidget);
    expect(find.textContaining('Real-ESRGAN'), findsWidgets);
    // Tall viewport so every pack (incl. face/ultra blocked rows) renders.
    tester.view.physicalSize = const Size(900, 2600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await settle(tester);
    expect(find.textContaining('Source verification pending'), findsWidgets);
    expect(find.textContaining('Coming in a later version'), findsWidgets);

    // Filter chips (task 2.4): tapping Anime hides the photo-only x4plus
    // (exact name — the anime 6B variant legitimately stays).
    await tester.tap(find.text('Anime'));
    await settle(tester);
    expect(find.text('Real-ESRGAN x4plus'), findsNothing);
    expect(find.textContaining('anime-video'), findsWidgets);
  });

  testWidgets('config: detection card, face section, sticky upscale button', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false,
      'keep_screen_awake': false,
    });
    final services = (await tester.runAsync(_services))!;
    final container = ProviderContainer(overrides: screenOverrides(services));
    addTearDown(container.dispose);

    final png = _pngBytes();
    // decode + hint isolate are real async: run them in a real-event window.
    await tester.runAsync(
      () => container
          .read(configProvider.notifier)
          .loadFrom(
            path: 'photo.png',
            bytes: png,
            level: SettingLevel.defaultLevel,
          ),
    );

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: _wrap(ConfigScreen(), const []),
      ),
    );
    await settle(tester);

    // Detection summary card (task 1.13).
    expect(find.textContaining('Detected:'), findsOneWidget);
    // Tall viewport so the face section below the fold renders.
    tester.view.physicalSize = const Size(900, 2200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await settle(tester);
    // The test PNG is skin-toned: the local detector (task 2.7) reports its
    // hint. GFPGAN is license-blocked, so the toggle stays disabled.
    expect(find.textContaining('face(s) detected'), findsOneWidget);
    final faceSwitch = tester.widget<Switch>(
      find.byType(Switch).at(find.byType(Switch).evaluate().length - 1),
    );
    expect(faceSwitch.onChanged, isNull);
    // Sticky gradient Upscale button.
    expect(find.widgetWithText(ElevatedButton, 'Upscale'), findsOneWidget);

    // Switching to Manual shows the model section (task 2.5).
    await tester.tap(find.text('Manual'));
    await settle(tester);
    expect(find.textContaining('Real-ESRGAN'), findsWidgets);
    // 2x-5x scale chips appear.
    expect(find.text('2×'), findsOneWidget);
    expect(find.text('5×'), findsOneWidget);
  });

  testWidgets('processing screen: real progress widgets render', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false,
      'keep_screen_awake': false,
    });
    final services = (await tester.runAsync(_services))!;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ...screenOverrides(services),
          jobProvider.overrideWith(_FakeJobNotifier.new),
        ],
        child: MaterialApp(
          localizationsDelegates: _l10nDelegates,
          supportedLocales: const [Locale('en')],
          theme: AppTheme.light(),
          home: const ProcessingScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    // Stage label + honest tile counter from completed tiles (task 1.14).
    expect(find.text('Upscaling tiles'), findsOneWidget);
    expect(find.text('2 / 8 tiles'), findsOneWidget);
    // Cancel + continue-in-background actions.
    expect(find.textContaining('Cancel'), findsOneWidget);
    expect(find.textContaining('background'), findsOneWidget);
  });

  testWidgets('a11y: config stays usable at 200% text scale (Phase 4)', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false,
      'keep_screen_awake': false,
    });
    final services = (await tester.runAsync(_services))!;
    final container = ProviderContainer(overrides: screenOverrides(services));
    addTearDown(container.dispose);

    final png = _pngBytes();
    await tester.runAsync(
      () => container
          .read(configProvider.notifier)
          .loadFrom(
            path: 'photo.png',
            bytes: png,
            level: SettingLevel.defaultLevel,
          ),
    );

    tester.view.physicalSize = const Size(900, 3200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2.0)),
          child: _wrap(ConfigScreen(), const []),
        ),
      ),
    );
    await settle(tester);

    // Key controls still render and are reachable at 200% scale.
    expect(find.text('Default'), findsOneWidget);
    expect(find.text('Manual'), findsOneWidget);
    expect(find.text('Advanced'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, 'Upscale'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('l10n: Hindi locale renders translated home (Phase 4)', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false,
      'keep_screen_awake': false,
      'language': 'hi',
    });
    final services = (await tester.runAsync(_services))!;
    final container = ProviderContainer(overrides: screenOverrides(services));
    addTearDown(container.dispose);

    tester.view.physicalSize = const Size(900, 2600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: ProviderScope(
          overrides: const [],
          child: MaterialApp(
            locale: const Locale('hi'),
            localizationsDelegates: _l10nDelegates,
            supportedLocales: const [Locale('en'), Locale('hi')],
            theme: AppTheme.light(),
            home: HomeScreen(),
          ),
        ),
      ),
    );
    await settle(tester);

    // Hindi strings from app_hi.arb render on Home.
    expect(find.text('अपस्केल'), findsWidgets); // Upscale mode card
    expect(find.text('सुपर रेज़ोल्यूशन'), findsWidgets); // SR card
    expect(find.text('सेटिंग्स'), findsWidgets); // options row
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'config: Super Resolution mode shows feasibility + preview card',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        'dynamic_color': false,
        'keep_screen_awake': false,
      });
      final services = (await tester.runAsync(_services))!;
      final container = ProviderContainer(overrides: screenOverrides(services));
      addTearDown(container.dispose);

      final png = _pngBytes();
      await tester.runAsync(
        () => container
            .read(configProvider.notifier)
            .loadFrom(
              path: 'photo.png',
              bytes: png,
              level: SettingLevel.defaultLevel,
              mode: UpscaleMode.superResolution,
            ),
      );

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: _wrap(ConfigScreen(), const []),
        ),
      );
      await settle(tester);

      // SR title with PRO chip + lossless explainer (Phase 3).
      expect(find.text('Super Resolution'), findsWidgets);
      expect(find.textContaining('No recompression'), findsOneWidget);
      // The tiny test photo fits: green feasibility banner.
      expect(find.textContaining('Fits this device'), findsOneWidget);
      // Preview-first card with the pick action.
      expect(find.textContaining('Choose area'), findsWidgets);
      // Lossless PNG output promise: SR mode always plans PNG.
      expect(find.widgetWithText(ElevatedButton, 'Upscale'), findsOneWidget);
    },
  );

  testWidgets('config: Advanced level shows pipeline override sections', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false,
      'keep_screen_awake': false,
    });
    final services = (await tester.runAsync(_services))!;
    final container = ProviderContainer(overrides: screenOverrides(services));
    addTearDown(container.dispose);

    final png = _pngBytes();
    await tester.runAsync(
      () => container
          .read(configProvider.notifier)
          .loadFrom(
            path: 'photo.png',
            bytes: png,
            level: SettingLevel.advanced,
          ),
    );

    tester.view.physicalSize = const Size(900, 2600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: _wrap(ConfigScreen(), const []),
      ),
    );
    await settle(tester);

    // Advanced pipeline sections render (Phase 3). SectionHeader
    // uppercases its label.
    expect(
      find.textContaining(RegExp('advanced pipeline', caseSensitive: false)),
      findsOneWidget,
    );
    expect(find.text('Tile size'), findsOneWidget);
    expect(find.text('Tile overlap'), findsOneWidget);
    expect(find.text('CPU threads'), findsOneWidget);
    expect(find.text('Compute'), findsOneWidget);
    expect(find.text('Output format'), findsOneWidget);
    expect(find.text('Output size limit'), findsOneWidget);
    // Manual sections are inherited (scale chips + model list).
    expect(find.text('2×'), findsOneWidget);
    expect(find.text('5×'), findsOneWidget);
    // Auto chips are present for every override row.
    expect(find.text('Auto'), findsWidgets);
  });

  testWidgets('home: Super Resolution card starts the SR flow (gate on)', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false,
      'keep_screen_awake': false,
      'onboarding_done': true,
    });
    final services = (await tester.runAsync(_services))!;
    final container = ProviderContainer(overrides: screenOverrides(services));
    addTearDown(container.dispose);

    // Tall viewport so the Super Resolution mode card is on screen.
    tester.view.physicalSize = const Size(900, 2600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    // The picker opens after Continue; return "cancelled" (no file).
    FilePickerPlatform.instance = _NoopFilePicker();

    // A router-backed wrap: the first-time SR sheet pops via GoRouter.
    final router = GoRouter(
      initialLocation: '/home',
      routes: [
        GoRoute(path: '/home', builder: (context, state) => HomeScreen()),
        GoRoute(
          path: '/config',
          builder: (context, state) => const SizedBox.shrink(),
        ),
      ],
    );
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(
          localizationsDelegates: _l10nDelegates,
          supportedLocales: const [Locale('en')],
          theme: AppTheme.light(),
          routerConfig: router,
        ),
      ),
    );
    await settle(tester);

    // The SR mode card is present; tapping it with no warning seen opens
    // the honest first-time sheet (gated flow), not the locked placeholder.
    expect(find.textContaining('Super Resolution'), findsWidgets);
    await tester.tap(find.textContaining('Super Resolution').first);
    await settle(tester);
    expect(find.textContaining('Before you start'), findsOneWidget);
    // Continue proceeds (marks srWarningSeen) — the picker would open next;
    // in the test env the picker is a no-op, so we only assert the flag.
    await tester.tap(find.text('Continue anyway'));
    await settle(tester);
    expect(services.settings.srWarningSeen, isTrue);
  });
}

class _ReadyLibrary extends LibraryNotifier {
  @override
  LibraryState build() => LibraryState(
    status: LibraryStatus.ready,
    photos: [
      PhotoEntry(
        asset: AssetEntity(id: '1', typeInt: 1, width: 1920, height: 1080),
      ),
    ],
  );

  @override
  Future<void> load({int limit = 30}) async {}
}

class _DeniedLibrary extends LibraryNotifier {
  @override
  LibraryState build() => LibraryState(status: LibraryStatus.denied);

  @override
  Future<void> load({int limit = 30}) async {}
}

class _FakeJobNotifier extends JobNotifier {
  @override
  JobViewState build() => const JobViewState(
    stage: JobStage.upscaling,
    percent: 0.5,
    tileIndex: 2,
    tileTotal: 8,
    etaSeconds: 30,
  );

  @override
  Future<String?> run() async => null;

  @override
  Future<void> cancel() async {}
}

/// Platform-channel stub: the SR flow's file picker returns "cancelled".
class _NoopFilePicker extends FilePickerPlatform {
  @override
  Future<List<PlatformFile>> pickFiles({
    List<String>? allowedExtensions,
    dynamic androidOptions,
    int compressionQuality = 30,
    dynamic darwinOptions,
    String? dialogTitle,
    String? initialDirectory,
    dynamic linuxOptions,
    dynamic Function(FilePickerStatus)? onFileLoading,
    FileType type = FileType.any,
    dynamic webOptions,
    dynamic windowsOptions,
  }) async => [];
}

/// Small solid skin-toned PNG generated with the pinned `image` package.
Uint8List _pngBytes() {
  final image = img.Image(width: 48, height: 48);
  img.fill(image, color: img.ColorRgb8(224, 172, 140));
  return Uint8List.fromList(img.encodePng(image));
}
