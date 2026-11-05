import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app/app.dart';
import 'engine/engine_types.dart';
import 'engine/lanczos_engine.dart';
import 'engine/ncnn/ncnn_engine.dart';
import 'engine/onnx/onnx_engine.dart';
import 'engine/recovery_chain.dart';
import 'services/device_profiler/device_profiler.dart';
import 'services/image_service/image_service.dart';
import 'services/job_manager/job_manager.dart';
import 'services/model_catalog/model_catalog.dart';
import 'services/model_manager/model_manager.dart';
import 'services/permission_service/permission_service.dart';
import 'services/pipeline_planner/pipeline_planner.dart';
import 'services/settings_service/settings_service.dart';
import 'services/storage_service/storage_service.dart';
import 'state/providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);

  final prefs = await SharedPreferences.getInstance();

  // Directories.
  final docs = await getApplicationDocumentsDirectory();
  final tempDir = '${docs.path}/rs_temp';
  final modelDir = '${docs.path}/models';
  await Directory(tempDir).create(recursive: true);
  await Directory(modelDir).create(recursive: true);

  // Services.
  final settings = SettingsService(prefs);
  final permissions = const PermissionService();
  final profiler = DeviceProfiler(prefs: prefs);
  final image = ImageService(tempDir: tempDir);
  final storage = StorageService();
  final catalog = await ModelCatalog.loadBundled();
  final planner = await PipelinePlanner.load();
  final modelManager = ModelManager(
    catalog: catalog,
    modelDir: modelDir,
    downloader: BackgroundDownloaderGateway(),
    wifiOnlyDefault: settings.wifiOnly,
  );
  ModelManager.assetLoader = (assetPath) async {
    final data = await rootBundle.load(assetPath);
    return data.buffer.asUint8List();
  };

  // Engine registry: native engines first (ncnn for CNN models, ONNX
  // Runtime for the Ultra pack), pure-Dart fallback always there.
  final engines = <UpscaleEngine>[
    if (NcnnEngine.tryCreate() != null) NcnnEngine.tryCreate()!,
    if (OnnxEngine.tryCreate() != null) OnnxEngine.tryCreate()!,
    LanczosEngine(),
  ];

  final jobManager = JobManager(
    engine: engines.first,
    recoveryChainFactory: (plan) => RecoveryChain(
      engine: engines.first,
      basePlan: plan,
      replanForRetry: replanStandard,
    ),
    storage: storage,
    haptics: NoopHaptics(),
  );

  // Static hooks: temp-output decode + final encode.
  JobManager.registerBitmapReader(image.decodeToBitmap);
  JobManager.registerEncodedSaver(
    (path, bmp, kind, quality, metadata) => image.writeEncoded(
      path: path,
      bmp: bmp,
      kind: kind,
      quality: quality,
      metadataSource: metadata,
    ),
  );

  final services = Services(
    settings: settings,
    permissions: permissions,
    profiler: profiler,
    image: image,
    storage: storage,
    planner: planner,
    catalog: catalog,
    modelManager: modelManager,
    jobManager: jobManager,
    engineRegistry: EngineRegistry(engines: engines),
    modelDir: modelDir,
    tempDir: tempDir,
  );

  // Temp hygiene at app start (rules.md golden rule 8).
  unawaited(storage.clearCaches());

  runApp(
    ProviderScope(
      overrides: [servicesProvider.overrideWithValue(services)],
      child: const RealScaleApp(),
    ),
  );
}
