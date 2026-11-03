import 'dart:async';

import 'package:flutter/foundation.dart';

import '../core/models.dart';
import '../engine/upscale_engine.dart';
import '../services/device_profiler/device_profiler.dart';
import '../services/image_service/image_service.dart';
import '../services/pipeline_planner/pipeline_planner.dart';
import '../services/storage_service/storage_service.dart';

/// Coordinates screens while keeping business logic out of widgets.
class AppController extends ChangeNotifier {
  AppController({
    required DeviceProfiler profiler,
    required PipelinePlanner planner,
    required ImageService images,
    required StorageService storage,
    required UpscaleEngine engine,
  })  : _profiler = profiler,
        _planner = planner,
        _images = images,
        _storage = storage,
        _engine = engine;

  final DeviceProfiler _profiler;
  final PipelinePlanner _planner;
  final ImageService _images;
  final StorageService _storage;
  final UpscaleEngine _engine;

  DeviceProfile? device;
  ImageSelection? selectedImage;
  JobSpec? activeJob;
  ProgressEvent? progress;
  UpscaleRecord? latestResult;
  List<UpscaleRecord> history = <UpscaleRecord>[];
  AppFailure? failure;
  bool busy = false;
  StreamSubscription<EngineEvent>? _jobSubscription;

  Future<void> checkDevice() async {
    busy = true;
    failure = null;
    notifyListeners();
    try {
      device = await _profiler.profile();
    } on Object {
      failure = AppFailure.unknown;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<bool> chooseFile() async => _setSelection(await _images.pickFile());
  Future<bool> chooseGallery() async => _setSelection(await _images.pickGallery());
  Future<bool> takePhoto() async => _setSelection(await _images.takePhoto());

  Future<bool> selectPath(String path) async {
    try {
      return _setSelection(await _images.inspect(path));
    } on Object {
      failure = AppFailure.unsupportedImage;
      notifyListeners();
      return false;
    }
  }

  Future<bool> _setSelection(ImageSelection? value) async {
    if (value == null) {
      return false;
    }
    selectedImage = value;
    failure = null;
    notifyListeners();
    return true;
  }

  Future<JobSpec?> prepareJob() async {
    final ImageSelection? image = selectedImage;
    if (image == null) {
      return null;
    }
    device ??= await _profiler.profile();
    final JobSpec planned = await _planner.plan(image, device!);
    final String prepared = await _images.prepareForUpscale(image);
    activeJob = JobSpec(
      id: planned.id,
      input: planned.input,
      mode: planned.mode,
      level: planned.level,
      scale: planned.scale,
      modelId: planned.modelId,
      tileSize: planned.tileSize,
      overlap: planned.overlap,
      useGpu: planned.useGpu,
      estimatedSeconds: planned.estimatedSeconds,
      preparedInputPath: prepared,
    );
    notifyListeners();
    return activeJob;
  }

  Future<void> startJob({required VoidCallback onComplete}) async {
    final JobSpec? job = activeJob ?? await prepareJob();
    if (job == null) {
      return;
    }
    busy = true;
    failure = null;
    progress = ProgressEvent(
      jobId: job.id,
      stage: JobStage.preparing,
      completedTiles: 0,
      totalTiles: 1,
      elapsed: Duration.zero,
    );
    notifyListeners();
    await _jobSubscription?.cancel();
    _jobSubscription = _engine.run(job).listen((EngineEvent event) async {
      if (event is EngineProgress) {
        progress = event.value;
        notifyListeners();
      } else if (event is EngineCompleted) {
        try {
          latestResult = await _storage.save(
            job: job,
            temporaryOutput: event.outputPath,
            elapsed: event.elapsed,
          );
          history = await _storage.loadHistory();
          busy = false;
          await _images.removeTemporaryCopy(
            job.preparedInputPath ?? job.input.path,
            job.input.path,
          );
          notifyListeners();
          onComplete();
        } on Object {
          busy = false;
          failure = AppFailure.storageLow;
          notifyListeners();
        }
      } else if (event is EngineFailed) {
        busy = false;
        failure = event.failure;
        notifyListeners();
      }
    });
  }

  Future<void> cancelJob() async {
    final JobSpec? job = activeJob;
    if (job != null) {
      await _engine.cancel(job.id);
      await _images.removeTemporaryCopy(
        job.preparedInputPath ?? job.input.path,
        job.input.path,
      );
    }
    busy = false;
    progress = null;
    notifyListeners();
  }

  Future<void> loadHistory() async {
    history = await _storage.loadHistory();
    notifyListeners();
  }

  Future<void> deleteRecord(UpscaleRecord record) async {
    await _storage.delete(record);
    await loadHistory();
  }

  void openRecord(UpscaleRecord record) {
    latestResult = record;
    notifyListeners();
  }

  @override
  void dispose() {
    _jobSubscription?.cancel();
    _engine.dispose();
    super.dispose();
  }
}
