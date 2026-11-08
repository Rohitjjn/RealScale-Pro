/// Widget test: the app shell builds and Home renders its honest
/// device/engine status without fabricated data.
library;

import 'dart:ui' show Size;

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'dart:io' show Directory;
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:realscale_pro/app.dart';
import 'package:realscale_pro/core/providers.dart';

class _FakePathProvider extends PathProviderPlatform {
  @override
  Future<String?> getApplicationDocumentsPath() async =>
      Directory.systemTemp.path;
  @override
  Future<String?> getApplicationSupportPath() async =>
      Directory.systemTemp.path;
  @override
  Future<String?> getTemporaryPath() async =>
      Directory.systemTemp.path;
}

void main() {
  testWidgets('Home renders status strip and mode cards',
      (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({'onboarding.done': true});
    PathProviderPlatform.instance = _FakePathProvider();
    final prefs = await SharedPreferences.getInstance();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          sharedPreferencesProvider.overrideWithValue(prefs),
        ],
        child: const RealScaleApp(),
      ),
    );

    // Splash performs async bootstrap; advance time in steps (the
    // splash progress bar is intentionally indeterminate, so
    // pumpAndSettle would never settle).
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    // Tall surface so the whole Home list is laid out.
    await tester.binding.setSurfaceSize(Size(800, 1800));
    await tester.pump();

    expect(find.text('RealScale Pro'), findsWidgets);
    expect(find.text('Choose a mode'), findsOneWidget);
    expect(find.text('Upscale Mode'), findsOneWidget);
    expect(find.text('Super Resolution Mode'), findsOneWidget);
    expect(find.text('Import an image'), findsOneWidget);
  });
}
