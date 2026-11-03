import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../engine/ncnn/ncnn_engine.dart';
import '../services/device_profiler/device_profiler.dart';
import '../services/image_service/image_service.dart';
import '../services/model_manager/model_download_service.dart';
import '../services/pipeline_planner/pipeline_planner.dart';
import '../services/settings_service.dart';
import '../services/storage_service/storage_service.dart';
import 'app_controller.dart';

final Provider<SettingsService> settingsProvider = Provider<SettingsService>(
  (Ref ref) =>
      throw StateError('SettingsService must be overridden at startup'),
);

final Provider<ModelDownloadService> modelDownloadProvider =
    Provider<ModelDownloadService>(
      (Ref ref) => throw StateError(
        'ModelDownloadService must be overridden at startup',
      ),
    );

final Provider<AppController> appControllerProvider = Provider<AppController>((
  Ref ref,
) {
  final AppController controller = AppController(
    profiler: const DeviceProfiler(),
    planner: PipelinePlanner(),
    images: ImageService(),
    storage: StorageService(),
    engine: NcnnEngine(),
  );
  ref.onDispose(controller.dispose);
  return controller;
});
