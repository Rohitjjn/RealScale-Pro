import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/engine/ncnn/ncnn_engine.dart'
    show ncnnBinPath, ncnnParamPath;
import 'package:realscale_pro/l10n/app_localizations.dart';
import 'package:realscale_pro/services/device_profiler/device_profiler.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/model_manager/model_manager.dart';
import 'package:realscale_pro/ui/screens/settings/settings_screen.dart';
import 'package:realscale_pro/ui/theme/app_theme.dart';
import 'package:realscale_pro/state/providers.dart';
import 'package:realscale_pro/ui/widgets/controls.dart';
import 'package:riverpod/misc.dart' show Override;
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers/test_services.dart';

const _l10nDelegates = [
  AppLocalizations.delegate,
  GlobalMaterialLocalizations.delegate,
  GlobalWidgetsLocalizations.delegate,
  GlobalCupertinoLocalizations.delegate,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // ---------------------------------------------------------------------------
  // 1. BeforeAfterSlider must not read context.size inside build().
  //    Reading RenderObject.size during build threw and took the whole
  //    result screen down on every frame (debug AND release).
  // ---------------------------------------------------------------------------
  testWidgets('BeforeAfterSlider builds without touching context.size', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: _l10nDelegates,
        supportedLocales: const [Locale('en')],
        theme: AppTheme.light(),
        home: const Scaffold(
          body: SizedBox(
            width: 400,
            height: 320,
            child: BeforeAfterSlider(
              before: ColoredBox(color: Colors.red),
              after: ColoredBox(color: Colors.blue),
            ),
          ),
        ),
      ),
    );

    // No exception → the slider painted.
    expect(tester.takeException(), isNull);
    expect(find.byType(BeforeAfterSlider), findsOneWidget);

    // Dragging the divider works off the layout width, not render size.
    await tester.drag(find.byType(BeforeAfterSlider), const Offset(120, 0));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  // ---------------------------------------------------------------------------
  // 2. Settings toggles must move instantly (settings revision counter).
  // ---------------------------------------------------------------------------
  testWidgets('settings: dynamic-color toggle rebuilds live', (tester) async {
    SharedPreferences.setMockInitialValues({
      'dynamic_color': false,
      'haptics_enabled': false,
    });
    final root = Directory.systemTemp.createTempSync('rs_settings');
    addTearDown(() => root.deleteSync(recursive: true));
    final services =
        await tester.runAsync(() => buildTestServices(root.path)) as Services;

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[servicesProvider.overrideWithValue(services)],
        child: MaterialApp(
          localizationsDelegates: _l10nDelegates,
          supportedLocales: const [Locale('en')],
          theme: AppTheme.light(),
          home: const SettingsScreen(),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    final first = find.byType(SwitchListTile).first;
    expect(tester.widget<SwitchListTile>(first).value, isFalse);

    await tester.tap(first);
    await tester.pump();

    // The revision counter was bumped → the switch flipped in the UI.
    expect(services.settings.dynamicColor, isTrue);
    expect(tester.widget<SwitchListTile>(first).value, isTrue);
  });

  // ---------------------------------------------------------------------------
  // 3. DeviceProfiler must report free storage in MEGABYTES, not KiB.
  // ---------------------------------------------------------------------------
  test('freeStorageMb divides the df KiB column by 1024', () async {
    if (!(Platform.isLinux || Platform.isAndroid)) return; // df -k only
    final mb = await DeviceProfiler.freeStorageMb(Directory.systemTemp.path);
    final res = await Process.run('df', ['-k', Directory.systemTemp.path]);
    if (res.exitCode != 0) return;
    final lines = (res.stdout as String).trim().split('\n');
    if (lines.length < 2) return;
    final cols = lines.last.split(RegExp(r'\s+'));
    if (cols.length < 4) return;
    final availKb = int.tryParse(cols[3]);
    if (availKb == null) return;

    expect(mb, availKb ~/ 1024);
    // Sanity: a device never reports petabytes in MB.
    expect(mb, lessThan(64 * 1024 * 1024));
  });

  // ---------------------------------------------------------------------------
  // 4. ModelManager file layout + offline starter readiness.
  // ---------------------------------------------------------------------------
  test('destPath writes .onnx files with an .onnx extension', () async {
    final root = Directory.systemTemp.createTempSync('rs_dest');
    addTearDown(() => root.deleteSync(recursive: true));
    final catalog = await buildCatalog();
    final manager = ModelManager(
      catalog: catalog,
      modelDir: '${root.path}/models',
      downloader: FakeDownloaderGateway(),
      wifiOnlyDefault: true,
    );

    final model = catalog.byId('waifu2x-upconv-photo-2x')!;
    final bin = model.files.firstWhere((f) => f.kind == 'bin');
    final param = model.files.firstWhere((f) => f.kind == 'param');
    expect(manager.destPath(model.id, bin), endsWith('.bin'));
    expect(manager.destPath(model.id, param), endsWith('-2x.param'));

    // The old code saved ANY non-bin file as `<id>-nullx.param`, which
    // silently destroyed ONNX weights of the Ultra pack.
    final onnx = ModelFileSpec.fromMap({
      'kind': 'onnx',
      'sha256': bin.sha256,
      'sizeBytes': 1,
    });
    expect(manager.destPath('swinir-real-world', onnx), endsWith('.onnx'));
  });

  test('engine paths match what ModelManager installs', () async {
    final catalog = await buildCatalog();
    final model = catalog.byId('realesr-animevideov3')!;
    const dir = '/data/models';
    // Native resolution helpers (ncnn_engine.dart) and the installer must
    // agree on `<dir>/<id>-<scale>x.param` + `<dir>/<id>.bin`.
    expect(ncnnParamPath(dir, model.id, 4), '$dir/${model.id}-4x.param');
    expect(ncnnBinPath(dir, model.id), '$dir/${model.id}.bin');
  });

  test('starter pack is ready offline with partial scales', () async {
    final root = Directory.systemTemp.createTempSync('rs_starter');
    addTearDown(() => root.deleteSync(recursive: true));
    final catalog = await buildCatalog();
    final manager = ModelManager(
      catalog: catalog,
      modelDir: '${root.path}/models',
      downloader: FakeDownloaderGateway(),
      wifiOnlyDefault: true,
    );
    await manager.initialize();

    // Real bundled bytes, read straight from disk (no rootBundle in tests).
    ModelManager.assetLoader = (path) async => File(path).readAsBytes();
    addTearDown(() => ModelManager.assetLoader = null);

    await manager.ensureBundledFiles();

    // realesr-animevideov3 ships ONLY its x4 graph inside the APK, so it is
    // installed (usable) even though the x2/x3 graphs are still missing.
    final realesr = catalog.byId('realesr-animevideov3')!;
    expect(manager.isInstalled(realesr.id), isTrue);
    expect(manager.hasUsableNativeFiles(realesr, 4), isTrue);
    expect(manager.hasUsableNativeFiles(realesr, 2), isFalse);
    expect(manager.hasUsableNativeFiles(realesr, 3), isFalse);

    // Every other starter model is fully usable too.
    for (final id in const [
      'waifu2x-upconv-photo-2x',
      'waifu2x-upconv-anime-2x',
      'realcugan-se-2x-denoise1x',
    ]) {
      final m = catalog.byId(id)!;
      expect(manager.isInstalled(id), isTrue, reason: id);
      expect(manager.isReady(m, scale: m.nativeScales.first), isTrue);
    }

    // A Pro model nobody downloaded is NOT silently ready.
    final pro = catalog.byId('realesrgan-x4plus')!;
    expect(manager.isInstalled(pro.id), isFalse);
    expect(manager.hasUsableNativeFiles(pro, 4), isFalse);
  });

  test('ensureBundledFiles leaves no temporary zip archives behind', () async {
    final root = Directory.systemTemp.createTempSync('rs_zips');
    addTearDown(() => root.deleteSync(recursive: true));
    final catalog = await buildCatalog();
    final modelDir = '${root.path}/models';
    final manager = ModelManager(
      catalog: catalog,
      modelDir: modelDir,
      downloader: FakeDownloaderGateway(),
      wifiOnlyDefault: true,
    );
    await manager.initialize();
    await File(
      '$modelDir/z_realesr-animevideov3_bin.zip',
    ).writeAsBytes(List.filled(32, 7));
    await manager.ensureBundledFiles();
    await manager.delete('waifu2x-upconv-photo-2x');

    final leftovers = Directory(modelDir)
        .listSync()
        .where((e) => e.uri.pathSegments.last.startsWith('z_'))
        .toList();
    expect(leftovers, isEmpty);
  });
}
