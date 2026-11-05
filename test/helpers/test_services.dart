import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:riverpod/misc.dart' show Override;
import 'package:realscale_pro/engine/engine_types.dart';
import 'package:realscale_pro/engine/lanczos_engine.dart';
import 'package:realscale_pro/engine/recovery_chain.dart';
import 'package:realscale_pro/services/device_profiler/device_profiler.dart';
import 'package:realscale_pro/services/image_service/image_service.dart';
import 'package:realscale_pro/services/job_manager/job_manager.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/model_manager/model_manager.dart';
import 'package:realscale_pro/services/permission_service/permission_service.dart';
import 'package:realscale_pro/services/pipeline_planner/pipeline_planner.dart';
import 'package:realscale_pro/services/settings_service/settings_service.dart';
import 'package:realscale_pro/services/storage_service/storage_service.dart';
import 'package:realscale_pro/state/providers.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Fake download gateway: never touches the network or platform channels.
class FakeDownloaderGateway implements DownloaderGateway {
  final _events = StreamController<DownloaderEvent>.broadcast();

  @override
  Future<String> enqueue({
    required String url,
    required String filePath,
    required bool wifiOnly,
  }) async => 'fake-task';

  @override
  Future<void> pause(String taskId) async {}

  @override
  Future<void> resume(String taskId) async {}

  @override
  Future<void> cancel(String taskId) async {}

  @override
  Stream<DownloaderEvent> get events => _events.stream;

  @override
  Future<void> restorePending() async {}
}

/// Builds a full Services graph for widget tests: real catalog/planner from
/// the bundled assets, real file IO in a temp root, fake downloader only.
Future<Services> buildTestServices(String tempRoot) async {
  final modelDir = '$tempRoot/models';
  final tempDir = '$tempRoot/tmp';
  Directory(modelDir).createSync(recursive: true);
  Directory(tempDir).createSync(recursive: true);

  final prefs = await SharedPreferences.getInstance();
  final settings = SettingsService(prefs);
  // Read the bundled data files directly from disk: rootBundle depends on
  // the platform channel, which does not answer reliably inside the
  // fake-async zone of widget tests.
  final catalog = ModelCatalog.parse(
    await File('assets/data/model_catalog.json').readAsString(),
  );
  final planner = PipelinePlanner(
    jsonDecode(await File('assets/data/planner_rules.json').readAsString())
        as Map<String, Object?>,
  );
  final modelManager = ModelManager(
    catalog: catalog,
    modelDir: modelDir,
    downloader: FakeDownloaderGateway(),
    wifiOnlyDefault: settings.wifiOnly,
  );
  final image = ImageService(tempDir: tempDir);
  final engines = <UpscaleEngine>[LanczosEngine()];
  final jobManager = JobManager(
    engine: engines.first,
    recoveryChainFactory: (plan) => RecoveryChain(
      engine: engines.first,
      basePlan: plan,
      replanForRetry: replanStandard,
    ),
    storage: StorageService(),
    haptics: NoopHaptics(),
  );

  return Services(
    settings: settings,
    permissions: const PermissionService(),
    profiler: DeviceProfiler(prefs: prefs),
    image: image,
    storage: StorageService(),
    planner: planner,
    catalog: catalog,
    modelManager: modelManager,
    jobManager: jobManager,
    engineRegistry: EngineRegistry(engines: engines),
    modelDir: modelDir,
    tempDir: tempDir,
  );
}

/// Fixed device profile for widget tests (no platform channels).
DeviceProfile testDevice() => DeviceProfile(
  tier: DeviceTier.t2,
  totalRamMb: 8192,
  freeRamMb: 4096,
  cpuCores: 8,
  gpuName: 'test-gpu',
  vulkanSupported: false,
  benchmarkMsPerTile: 100,
  freeStorageMb: 32000,
  osVersion: 'widget-test',
  thermalState: 'nominal',
  measuredAt: DateTime(2026),
);

/// Standard overrides used by every screen widget test.
List<Override> screenOverrides(Services services) => [
  servicesProvider.overrideWithValue(services),
  deviceProfileProvider.overrideWith((ref) async => testDevice()),
];

/// Fresh mock-backed SharedPreferences for service tests.
Future<SharedPreferences> buildPrefs() async {
  SharedPreferences.setMockInitialValues({});
  return SharedPreferences.getInstance();
}

/// The real bundled catalog parsed from disk (no rootBundle in tests).
Future<ModelCatalog> buildCatalog() async => ModelCatalog.parse(
  await File('assets/data/model_catalog.json').readAsString(),
);
