import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/l10n/app_localizations.dart';
import 'package:realscale_pro/services/model_manager/model_catalog.dart';
import 'package:realscale_pro/services/model_manager/model_manager.dart';
import 'package:realscale_pro/ui/screens/models/model_manager_screen.dart';

void main() {
  late ModelManager manager;

  setUp(() async {
    final ModelCatalog catalog = ModelCatalog.parse(
      await File('assets/data/model_catalog.json').readAsString(),
    );
    manager = ModelManager(
      catalog: catalog,
      transfers: _NoopGateway(),
      store: _MemoryStore(),
    );
    await manager.initialize();
  });

  tearDown(() => manager.dispose());

  testWidgets('shows storage, packs and honest install states', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(_testApp(manager));
    await tester.pumpAndSettle();

    expect(find.text('Model storage'), findsOneWidget);
    expect(find.text('Starter pack'), findsOneWidget);
    expect(find.text('Waifu2x compact photo'), findsOneWidget);
    expect(find.text('Included with the app'), findsWidgets);
    expect(find.text('Download all'), findsWidgets);
  });

  testWidgets('face filter hides photo models', (WidgetTester tester) async {
    await tester.pumpWidget(_testApp(manager));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Faces'));
    await tester.pumpAndSettle();

    expect(find.text('Waifu2x compact photo'), findsNothing);
    expect(find.text('GFPGAN v1.4'), findsOneWidget);
    expect(find.text('CodeFormer'), findsOneWidget);
  });
}

Widget _testApp(ModelManager manager) => MaterialApp(
  localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
    AppLocalizations.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: AppLocalizations.supportedLocales,
  home: ModelManagerScreen(manager: manager),
);

class _NoopGateway implements ModelTransferGateway {
  @override
  Future<void> cancelModel(String modelId) async {}

  @override
  Future<File> downloadFile(
    ModelDefinition model,
    ModelFileDefinition file, {
    required void Function(double progress) onProgress,
  }) => throw UnimplementedError();

  @override
  Future<void> pauseModel(String modelId) async {}

  @override
  Future<void> resumeModel(String modelId) async {}
}

class _MemoryStore implements ModelInstallStore {
  @override
  Future<void> delete(String modelId) async {}

  @override
  Future<void> install(
    ModelDefinition model,
    List<File> downloadedFiles,
  ) async {}

  @override
  Future<String?> installedVersion(String modelId) async => null;

  @override
  Future<ModelStorageSummary> summary() async =>
      const ModelStorageSummary(usedBytes: 2214590, freeBytes: 1000000000);
}
