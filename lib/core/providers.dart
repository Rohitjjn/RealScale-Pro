/// RealScale-Pro — Riverpod providers.
library;

import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models.dart';
import '../engine/native/realscale_native.dart';
import '../processing/job_manager.dart';
import '../services/device_profiler.dart';
import '../services/image_service.dart';
import '../services/model_manager.dart';
import '../services/settings_service.dart';
import '../services/storage_service.dart';

final sharedPreferencesProvider = Provider<SharedPreferences>((ref) {
  throw UnimplementedError('overridden in main()');
});

final settingsProvider = Provider<SettingsService>(
    (ref) => SettingsService(ref.watch(sharedPreferencesProvider)));

final storageProvider =
    Provider<StorageService>((ref) => StorageService());

final imageServiceProvider =
    Provider<ImageService>((ref) => ImageService());

final deviceProfilerProvider =
    Provider<DeviceProfiler>((ref) => DeviceProfiler());

/// Profiled once at startup; refreshed on demand.
final deviceProfileProvider =
    FutureProvider<DeviceProfile>((ref) async {
  return ref.watch(deviceProfilerProvider).profile();
});

final modelManagerProvider = Provider<ModelManager>((ref) {
  final manager = ModelManager(storage: ref.watch(storageProvider));
  return manager;
});

/// Registry loaded from the asset.
final modelRegistryProvider = FutureProvider<List<ModelInfo>>((ref) async {
  final manager = ref.watch(modelManagerProvider);
  await manager.init();
  return manager.registry();
});

/// Native engine availability + info. Null when unavailable (web or
/// missing library) — UI must communicate this honestly.
final engineInfoProvider = FutureProvider<NativeDeviceInfo?>((ref) async {
  try {
    final engine = NativeEngine.create();
    ref.onDispose(engine.dispose);
    return engine.deviceInfo;
  } on NativeEngineException {
    return null;
  } on UnsupportedError {
    return null;
  }
});

/// Whether a real native engine is loadable on this platform.
final engineAvailableProvider = FutureProvider<bool>(
    (ref) async => await ref.watch(engineInfoProvider.future) != null);

final jobManagerProvider = Provider<JobManager>((ref) {
  return JobManager(
    imageService: ref.watch(imageServiceProvider),
    storage: ref.watch(storageProvider),
    modelManager: ref.watch(modelManagerProvider),
    engineFactory: () {
      // Engine instances are cheap; the worker isolate loads its own
      // copy of the model per run.
      try {
        return NativeEngine.create();
      } catch (_) {
        rethrow;
      }
    },
  );
});

/// The image the user imported for the current session.
class ImportSession {
  const ImportSession({required this.file, required this.metadata});
  final File file;
  final ImageMetadata metadata;
}

final importSessionProvider =
    StateProvider<ImportSession?>((ref) => null);

/// Chosen mode for the next job.
final processingModeProvider =
    StateProvider<ProcessingMode>((ref) => ProcessingMode.upscale);

/// Recently completed jobs (session-scoped; disk listing in Prompt 3).
final recentResultsProvider = StateProvider<List<JobResult>>((ref) => []);
