/// RealScale-Pro — domain model types.
///
/// These types are the app's shared vocabulary between services,
/// processing and UI. They contain no I/O and no Flutter dependencies
/// beyond foundation, so they are unit-testable everywhere.
library;

import 'dart:typed_data';

/// The two user-facing processing modes.
enum ProcessingMode {
  /// Practical enhancement with controlled output optimization.
  upscale,

  /// Quality-first super resolution: no lossy steps, no silent
  /// downsampling, tiling when required.
  superResolution,
}

enum DeviceTier {
  lowResource,
  balanced,
  highPerformance;

  String get label => switch (this) {
        lowResource => 'Low resource',
        balanced => 'Balanced',
        highPerformance => 'High performance',
      };
}

enum OutputFormat { png, jpeg }

/// Image inspection result (before any processing).
class ImageMetadata {
  const ImageMetadata({
    required this.width,
    required this.height,
    required this.format,
    required this.fileSizeBytes,
    required this.hasAlpha,
    required this.exifOrientation,
    this.formatDetails = '',
  });

  final int width;
  final int height;
  final String format; // 'JPEG', 'PNG', 'WEBP', 'unknown', ...
  final int fileSizeBytes;
  final bool hasAlpha;
  /// EXIF orientation 1..8 (1 = normal). 0 = absent.
  final int exifOrientation;
  final String formatDetails;

  double get aspectRatio => width / height;
  double get megapixels => (width * height) / 1000000;

  /// True when the stored bytes must be rotated/flipped to obtain the
  /// display orientation (orientation != 1 and != 0).
  bool get needsOrientationFix =>
      exifOrientation >= 2 && exifOrientation <= 8;

  /// Dimensions after applying EXIF orientation.
  ({int width, int height}) get orientedDimensions {
    if (needsOrientationFix && (exifOrientation == 5 ||
        exifOrientation == 6 || exifOrientation == 7 || exifOrientation == 8)) {
      return (width: height, height: width);
    }
    return (width: width, height: height);
  }

  @override
  String toString() =>
      'ImageMetadata(${width}x$height $format ${megapixels}MP '
      'alpha=$hasAlpha exif=$exifOrientation)';
}

/// Decoded RGBA image ready for the engine.
class RgbaImage {
  const RgbaImage({
    required this.pixels,
    required this.width,
    required this.height,
  });
  final Uint8List pixels;
  final int width;
  final int height;
  int get stride => width * 4;
}

/// Device capability snapshot. Only fields that could actually be
/// read are populated; unknowns stay null (never invented).
class DeviceProfile {
  const DeviceProfile({
    required this.tier,
    required this.cpuCores,
    required this.platform,
    this.totalMemoryMb,
    this.availableMemoryMb,
    this.freeStorageMb,
    this.androidVersion,
    this.cpuAbi,
    this.gpuAvailable = false,
    this.engineThreads = 2,
  });

  final DeviceTier tier;
  final int cpuCores;
  final String platform; // android / linux / web ...
  final int? totalMemoryMb;
  final int? availableMemoryMb;
  final int? freeStorageMb;
  final String? androidVersion;
  final String? cpuAbi;
  final bool gpuAvailable;
  final int engineThreads;

  /// Conservative RAM budget for inference planning (70% of the
  /// smaller of total/available, or a tier default when unknown).
  int get inferenceBudgetMb {
    final avail = availableMemoryMb;
    final total = totalMemoryMb;
    final base = (avail != null && total != null)
        ? (avail < total ? avail : total)
        : switch (tier) {
            DeviceTier.lowResource => 1400,
            DeviceTier.balanced => 2800,
            DeviceTier.highPerformance => 5200,
          };
    return (base * 0.7).round();
  }

  factory DeviceProfile.unknown() => const DeviceProfile(
        tier: DeviceTier.balanced,
        cpuCores: 2,
        platform: 'unknown',
      );
}

/// A model registry entry (metadata only; weights are downloaded).
class ModelInfo {
  const ModelInfo({
    required this.id,
    required this.displayName,
    required this.family,
    required this.scale,
    required this.license,
    required this.licenseUrl,
    required this.sourceUrl,
    required this.paramFile,
    required this.binFile,
    required this.approxSizeMb,
    required this.minRamMb,
    required this.description,
    required this.tags,
    this.paramSha256,
    this.binSha256,
    this.checksumStatus = 'unverified',
  });

  final String id;
  final String displayName;
  final String family; // 'SRVGGNet compact' / 'RRDBNet' ...
  final int scale;
  final String license;
  final String licenseUrl;
  final String sourceUrl;
  final String paramFile;
  final String binFile;
  final double approxSizeMb;
  final int minRamMb;
  final String description;
  final List<String> tags;

  /// SHA-256 of the model files. Values are RECORDED at download time
  /// from the official release (upstream publishes no checksum files);
  /// integrity is re-verified against these values on every install.
  final String? paramSha256;
  final String? binSha256;
  final String checksumStatus;

  ModelInfo copyWith({
    String? paramSha256,
    String? binSha256,
    String? checksumStatus,
  }) =>
      ModelInfo(
        id: id,
        displayName: displayName,
        family: family,
        scale: scale,
        license: license,
        licenseUrl: licenseUrl,
        sourceUrl: sourceUrl,
        paramFile: paramFile,
        binFile: binFile,
        approxSizeMb: approxSizeMb,
        minRamMb: minRamMb,
        description: description,
        tags: tags,
        paramSha256: paramSha256 ?? this.paramSha256,
        binSha256: binSha256 ?? this.binSha256,
        checksumStatus: checksumStatus ?? this.checksumStatus,
      );

  factory ModelInfo.fromJson(Map<String, Object?> j) => ModelInfo(
        id: j['id']! as String,
        displayName: j['displayName']! as String,
        family: j['family']! as String,
        scale: j['scale']! as int,
        license: j['license']! as String,
        licenseUrl: j['licenseUrl']! as String,
        sourceUrl: j['sourceUrl']! as String,
        paramFile: j['paramFile']! as String,
        binFile: j['binFile']! as String,
        approxSizeMb: (j['approxSizeMb']! as num).toDouble(),
        minRamMb: j['minRamMb']! as int,
        description: j['description']! as String,
        tags: (j['tags']! as List).cast<String>(),
        paramSha256: j['paramSha256'] as String?,
        binSha256: j['binSha256'] as String?,
        checksumStatus: j['checksumStatus'] as String? ?? 'unverified',
      );
}

/// Installation status of a model on this device.
enum ModelInstallStatus { notInstalled, downloading, installed, corrupted }

/// The computed plan for one processing job.
class PipelinePlan {
  const PipelinePlan({
    required this.mode,
    required this.modelId,
    required this.scale,
    required this.tileSize,
    required this.tileOverlap,
    required this.contextMargin,
    required this.outputFormat,
    this.jpegQuality,
    required this.estimatedPeakRamMb,
    required this.estimatedOutputWidth,
    required this.estimatedOutputHeight,
    required this.warnings,
    this.saferAlternative,
  });

  final ProcessingMode mode;
  final String modelId;
  final int scale;
  final int tileSize;
  final int tileOverlap;
  final int contextMargin;
  final OutputFormat outputFormat;
  final int? jpegQuality; // 90..96 when JPEG
  final int estimatedPeakRamMb;
  final int estimatedOutputWidth;
  final int estimatedOutputHeight;
  final List<String> warnings;
  final PipelinePlan? saferAlternative;

  bool get isSafe => estimatedPeakRamMb >= 0 && warnings.isEmpty;
}

/// Job lifecycle states.
enum JobPhase {
  queued,
  inspecting,
  planning,
  acquiringModel,
  decoding,
  inferring,
  encoding,
  saving,
  done,
  failed,
  cancelled,
}

/// Real progress update from the native engine (inference phase).
class JobProgress {
  const JobProgress({
    required this.phase,
    required this.fraction,
    this.tilesDone = 0,
    this.tilesTotal = 0,
    this.attempt = 1,
    this.tileSize = 0,
    this.message,
  });
  final JobPhase phase;
  final double fraction;
  final int tilesDone;
  final int tilesTotal;
  final int attempt;
  final int tileSize;
  final String? message;
}

/// Completed job outcome with honest, measured facts.
class JobResult {
  const JobResult({
    required this.outputPath,
    required this.inputWidth,
    required this.inputHeight,
    required this.outputWidth,
    required this.outputHeight,
    required this.inputFileBytes,
    required this.outputFileBytes,
    required this.duration,
    required this.modelId,
    required this.mode,
    required this.outputFormat,
    required this.attempt,
    this.backend = 'ncnn-cpu',
    this.savedToGallery = false,
    this.galleryNote = '',
  });

  final String outputPath;
  final int inputWidth;
  final int inputHeight;
  final int outputWidth;
  final int outputHeight;
  final int inputFileBytes;
  final int outputFileBytes;
  final Duration duration;
  final String modelId;
  final ProcessingMode mode;
  final OutputFormat outputFormat;
  final int attempt; // recovery attempts used (1 = none)

  /// Inference backend actually requested for this run (the engine
  /// reports the final backend via device info; GPU implies CPU
  /// fallback on failure).
  final String backend;

  /// Android: whether the result was also exported to the shared
  /// gallery (Pictures/RealScale-Pro). The app-storage copy always
  /// exists regardless.
  final bool savedToGallery;

  /// Gallery path on success, or the honest failure reason.
  final String galleryNote;
}
