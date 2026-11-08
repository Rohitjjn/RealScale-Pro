/// RealScale-Pro — device capability profiling.
///
/// Android: a tiny MethodChannel (`realscale/device`) reads
/// ActivityManager.MemoryInfo and Build fields. Other platforms report
/// what is honestly knowable (cores, platform); memory stays null
/// rather than being invented.
library;

import 'dart:io';

import 'package:flutter/services.dart';

import '../domain/models.dart';

class DeviceProfiler {
  static const MethodChannel _channel = MethodChannel('realscale/device');

  Future<DeviceProfile> profile() async {
    int? totalMemoryMb;
    int? availableMemoryMb;
    String? androidVersion;
    String? cpuAbi;
    bool gpuAvailable = false;

    if (Platform.isAndroid) {
      try {
        final info = await _channel
            .invokeMethod<Map<dynamic, dynamic>>('getDeviceInfo');
        if (info != null) {
          totalMemoryMb = (info['totalMemoryMb'] as num?)?.toInt();
          availableMemoryMb = (info['availableMemoryMb'] as num?)?.toInt();
          androidVersion = info['androidVersion'] as String?;
          cpuAbi = info['cpuAbi'] as String?;
          gpuAvailable = info['supportsVulkan'] == true;
        }
      } on PlatformException {
        // Channel unavailable (e.g. old embedding) — leave values null.
      } on MissingPluginException {
        // Running in a test harness without the channel.
      }
    }

    final cores = Platform.numberOfProcessors;
    final platform = Platform.operatingSystem;

    final tier = classifyTier(
      totalMemoryMb: totalMemoryMb,
      cores: cores,
      gpuAvailable: gpuAvailable,
    );

    final engineThreads = switch (tier) {
      DeviceTier.lowResource => cores.clamp(1, 2),
      DeviceTier.balanced => cores.clamp(2, 4),
      DeviceTier.highPerformance => cores.clamp(2, 6),
    };

    return DeviceProfile(
      tier: tier,
      cpuCores: cores,
      platform: platform,
      totalMemoryMb: totalMemoryMb,
      availableMemoryMb: availableMemoryMb,
      freeStorageMb: null, // see StorageService.freeSpaceBytes
      androidVersion: androidVersion,
      cpuAbi: cpuAbi ?? Platform.version,
      gpuAvailable: gpuAvailable,
      engineThreads: engineThreads,
    );
  }


  /// Tier classification. Unknown RAM degrades to `balanced` with the
  /// conservative inference budget documented in models.dart.
  static DeviceTier classifyTier({
    int? totalMemoryMb,
    required int cores,
    bool gpuAvailable = false,
  }) {
    final ram = totalMemoryMb;
    if (ram == null) {
      return DeviceTier.balanced;
    }
    if (ram >= 6000 && cores >= 6 || gpuAvailable && ram >= 4000) {
      return DeviceTier.highPerformance;
    }
    if (ram >= 3000) {
      return DeviceTier.balanced;
    }
    return DeviceTier.lowResource;
  }
}
