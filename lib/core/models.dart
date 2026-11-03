import 'dart:io';

/// The user-facing processing mode.
enum UpscaleMode { upscale, superResolution }

/// Progressive disclosure level selected on the configuration screen.
enum SettingLevel { defaultLevel, manual, advanced }

/// Best-effort image classification.
enum ImageKind { photo, anime }

/// Stable device classes used by the pipeline planner.
enum DeviceTier { t0, t1, t2, t3 }

/// Stages emitted by an active image job.
enum JobStage { preparing, enhancing, saving, complete }

/// A safely usable device profile.
class DeviceProfile {
  const DeviceProfile({
    required this.tier,
    required this.label,
    required this.totalRamMb,
    required this.cpuCores,
    required this.gpuName,
    required this.vulkanSupported,
    required this.benchmarkMs,
    required this.freeStorageMb,
    required this.osVersion,
    required this.measuredAt,
  });

  final DeviceTier tier;
  final String label;
  final int totalRamMb;
  final int cpuCores;
  final String gpuName;
  final bool vulkanSupported;
  final int benchmarkMs;
  final int freeStorageMb;
  final String osVersion;
  final DateTime measuredAt;
}

/// Metadata for an image selected by the user.
class ImageSelection {
  const ImageSelection({
    required this.path,
    required this.name,
    required this.width,
    required this.height,
    required this.bytes,
    required this.hasAlpha,
    this.kind = ImageKind.photo,
  });

  final String path;
  final String name;
  final int width;
  final int height;
  final int bytes;
  final bool hasAlpha;
  final ImageKind kind;

  int get outputWidth => width * 2;
  int get outputHeight => height * 2;
  File get file => File(path);
}

/// The only request accepted by an upscale engine.
class JobSpec {
  const JobSpec({
    required this.id,
    required this.input,
    required this.mode,
    required this.level,
    required this.scale,
    required this.modelId,
    required this.tileSize,
    required this.overlap,
    required this.useGpu,
    required this.estimatedSeconds,
    this.preparedInputPath,
  });

  final String id;
  final ImageSelection input;
  final UpscaleMode mode;
  final SettingLevel level;
  final int scale;
  final String modelId;
  final int tileSize;
  final int overlap;
  final bool useGpu;
  final int estimatedSeconds;
  final String? preparedInputPath;
}

/// Honest progress derived from completed work units.
class ProgressEvent {
  const ProgressEvent({
    required this.jobId,
    required this.stage,
    required this.completedTiles,
    required this.totalTiles,
    required this.elapsed,
    this.eta,
  });

  final String jobId;
  final JobStage stage;
  final int completedTiles;
  final int totalTiles;
  final Duration elapsed;
  final Duration? eta;

  double get fraction => totalTiles == 0 ? 0 : completedTiles / totalTiles;
}

/// A saved result shown in My Upscales.
class UpscaleRecord {
  const UpscaleRecord({
    required this.id,
    required this.originalPath,
    required this.outputPath,
    required this.width,
    required this.height,
    required this.bytes,
    required this.modelId,
    required this.scale,
    required this.elapsed,
    required this.createdAt,
  });

  final String id;
  final String originalPath;
  final String outputPath;
  final int width;
  final int height;
  final int bytes;
  final String modelId;
  final int scale;
  final Duration elapsed;
  final DateTime createdAt;
}

/// Non-technical errors suitable for mapping to localized UI messages.
enum AppFailure { unsupportedImage, permissionDenied, storageLow, engineUnavailable, cancelled, unknown }
