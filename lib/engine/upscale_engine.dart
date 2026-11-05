import 'dart:typed_data';

/// Device performance tier (prd §8.1).
enum DeviceTier {
  t0, // ~2 GB RAM, budget CPU — "Light"
  t1, // ~4 GB RAM — "Standard"
  t2, // 6–8 GB RAM — "Powerful"
  t3; // 12 GB+, flagship / desktop — "Powerful"

  static DeviceTier fromName(String? name) => DeviceTier.values.firstWhere(
    (t) => t.name == name?.toLowerCase(), // catalog uses "T0".."T3"
    orElse: () => DeviceTier.t1,
  );

  String get uiLabel => switch (this) {
    DeviceTier.t0 => 'Light',
    DeviceTier.t1 => 'Standard',
    DeviceTier.t2 => 'Powerful',
    DeviceTier.t3 => 'Powerful',
  };

  bool atLeast(DeviceTier other) => index >= other.index;
}

/// Measured device profile (architecture.md §3.2). Produced by DeviceProfiler,
/// stored locally, refreshed after app updates or on demand.
class DeviceProfile {
  const DeviceProfile({
    required this.tier,
    required this.totalRamMb,
    required this.freeRamMb,
    required this.cpuCores,
    required this.gpuName,
    required this.vulkanSupported,
    this.vramMb,
    required this.benchmarkMsPerTile,
    required this.freeStorageMb,
    required this.osVersion,
    this.batteryLevel,
    required this.thermalState,
    required this.measuredAt,
  });

  final DeviceTier tier;
  final int totalRamMb;
  final int freeRamMb;
  final int cpuCores;
  final String gpuName; // '' when unknown
  final bool vulkanSupported;
  final int? vramMb;
  final int benchmarkMsPerTile; // measured, honest
  final int freeStorageMb;
  final String osVersion;
  final int? batteryLevel;
  final String thermalState; // nominal | fair | serious | critical
  final DateTime measuredAt;

  Map<String, Object?> toMap() => {
    'tier': tier.name,
    'totalRamMb': totalRamMb,
    'freeRamMb': freeRamMb,
    'cpuCores': cpuCores,
    'gpuName': gpuName,
    'vulkanSupported': vulkanSupported,
    'vramMb': vramMb,
    'benchmarkMsPerTile': benchmarkMsPerTile,
    'freeStorageMb': freeStorageMb,
    'osVersion': osVersion,
    'batteryLevel': batteryLevel,
    'thermalState': thermalState,
    'measuredAt': measuredAt.millisecondsSinceEpoch,
  };

  factory DeviceProfile.fromMap(Map<String, Object?> m) => DeviceProfile(
    tier: DeviceTier.fromName(m['tier'] as String?),
    totalRamMb: (m['totalRamMb'] as num?)?.toInt() ?? 2048,
    freeRamMb: (m['freeRamMb'] as num?)?.toInt() ?? 1024,
    cpuCores: (m['cpuCores'] as num?)?.toInt() ?? 4,
    gpuName: m['gpuName'] as String? ?? '',
    vulkanSupported: m['vulkanSupported'] as bool? ?? false,
    vramMb: (m['vramMb'] as num?)?.toInt(),
    benchmarkMsPerTile: (m['benchmarkMsPerTile'] as num?)?.toInt() ?? 400,
    freeStorageMb: (m['freeStorageMb'] as num?)?.toInt() ?? 0,
    osVersion: m['osVersion'] as String? ?? '',
    batteryLevel: (m['batteryLevel'] as num?)?.toInt(),
    thermalState: m['thermalState'] as String? ?? 'nominal',
    measuredAt: DateTime.fromMillisecondsSinceEpoch(
      (m['measuredAt'] as num?)?.toInt() ?? 0,
    ),
  );
}

/// What the user picked (architecture.md §3.1 JobSpec input fields).
enum UpscaleMode { upscale, superResolution }

enum SettingLevel { defaultLevel, manual, advanced }

enum ImageType { auto, photo, anime }

enum ComputeDevice { auto, gpu, cpu }

enum TileOverlap { none, small, medium, large }

enum OutputFormat { sameAsSource, jpeg, png, webp }

/// The only object the UI ever sends to the engine (architecture.md §3.1).
class JobSpec {
  const JobSpec({
    required this.jobId,
    required this.inputPath,
    required this.inputBytes,
    required this.mode,
    required this.settingLevel,
    this.imageType = ImageType.auto,
    required this.scale,
    required this.modelId,
    this.faceEnhancement = false,
    this.faceModelId = '',
    this.tileSize = 0, // 0 = planner decides
    this.overlap = TileOverlap.medium,
    this.overlapExplicit = false, // Advanced level: user set overlap
    this.computeDevice = ComputeDevice.auto,
    this.threadCount = 0, // 0 = planner decides
    this.compression = Compression.auto,
    this.outputFormat = OutputFormat.sameAsSource,
    this.outputQuality = 92,
    this.outputCapMegapixels = 0, // 0 = planner decides
    this.options = const {},
  });

  final String jobId;
  final String inputPath;
  final Uint8List inputBytes;
  final UpscaleMode mode;
  final SettingLevel settingLevel;
  final ImageType imageType;
  final int scale; // 2..5
  final String modelId;
  final bool faceEnhancement;
  final String faceModelId;
  final int tileSize;
  final TileOverlap overlap;
  final bool overlapExplicit;
  final ComputeDevice computeDevice;
  final int threadCount;
  final Compression compression;
  final OutputFormat outputFormat;
  final int outputQuality;
  final int outputCapMegapixels;
  final Map<String, Object> options;

  JobSpec copyWith({
    int? tileSize,
    ComputeDevice? computeDevice,
    int? threadCount,
    String? modelId,
    bool? faceEnhancement,
    TileOverlap? overlap,
    bool? overlapExplicit,
    int? outputCapMegapixels,
  }) => JobSpec(
    jobId: jobId,
    inputPath: inputPath,
    inputBytes: inputBytes,
    mode: mode,
    settingLevel: settingLevel,
    imageType: imageType,
    scale: scale,
    modelId: modelId ?? this.modelId,
    faceEnhancement: faceEnhancement ?? this.faceEnhancement,
    faceModelId: faceModelId,
    tileSize: tileSize ?? this.tileSize,
    overlap: overlap ?? this.overlap,
    overlapExplicit: overlapExplicit ?? this.overlapExplicit,
    computeDevice: computeDevice ?? this.computeDevice,
    threadCount: threadCount ?? this.threadCount,
    compression: compression,
    outputFormat: outputFormat,
    outputQuality: outputQuality,
    outputCapMegapixels: outputCapMegapixels ?? this.outputCapMegapixels,
    options: options,
  );
}

/// The 2 MB / quality-90 input rule lives in ImageService, not in JobSpec;
/// this enum mirrors architecture.md §3.1 `compression`.
class Compression {
  const Compression._();
  static const auto = Compression._();
  static const off = Compression._();
  static const custom = Compression._();
}

/// Engine progress stages.
enum JobStage { preparing, upscaling, faces, saving, done, failed, cancelled }

/// Progress event streamed by the engine (architecture.md §3.4).
/// Percentages come from completed tiles only — honest progress (prd §7.1).
class ProgressEvent {
  const ProgressEvent({
    required this.jobId,
    required this.stage,
    required this.percent,
    this.tileIndex = 0,
    this.tileTotal = 0,
    this.etaSeconds,
    this.message,
    this.result,
  });

  final String jobId;
  final JobStage stage;
  final double percent; // 0..1
  final int tileIndex;
  final int tileTotal;
  final int? etaSeconds;
  final String? message;
  final EngineResult? result; // only on the final `done` event
}

/// Final output of a finished job.
class EngineResult {
  const EngineResult({
    required this.jobId,
    required this.outputPath,
    required this.outputWidth,
    required this.outputHeight,
    required this.outputBytes,
    required this.elapsedMs,
    required this.modelId,
    required this.usedScale,
    required this.tileSizeUsed,
    required this.usedGpu,
    this.facesRestored = 0,
    this.recoverySteps = const [],
    this.tempInputUsed = false,
  });

  final String jobId;
  final String outputPath; // temp output; JobManager moves it to storage
  final int outputWidth;
  final int outputHeight;
  final int outputBytes;
  final int elapsedMs;
  final String modelId;
  final int usedScale;
  final int tileSizeUsed;
  final bool usedGpu;
  final int facesRestored;
  final List<String> recoverySteps; // honest record of what was retried
  final bool tempInputUsed; // true when a quality-90 temp copy was used
}
