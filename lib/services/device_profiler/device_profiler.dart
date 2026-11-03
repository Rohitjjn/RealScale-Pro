import 'dart:io';
import 'dart:math' as math;

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

import '../../core/models.dart';

/// Reads safe cross-platform signals and runs a short local CPU probe.
class DeviceProfiler {
  const DeviceProfiler();

  static const MethodChannel _channel = MethodChannel('realscale/device');

  Future<DeviceProfile> profile() async {
    final Stopwatch stopwatch = Stopwatch()..start();
    double accumulator = 0;
    while (stopwatch.elapsed < const Duration(seconds: 2)) {
      for (int index = 1; index < 25000; index += 1) {
        accumulator += math.sqrt(index + accumulator.abs() % 3);
      }
    }
    stopwatch.stop();
    if (accumulator.isNaN) {
      throw StateError('Device probe produced an invalid result');
    }

    final int cores = Platform.numberOfProcessors;
    Map<Object?, Object?> native = <Object?, Object?>{};
    if (Platform.isAndroid) {
      try {
        native =
            await _channel.invokeMapMethod<Object?, Object?>('profile') ??
            <Object?, Object?>{};
      } on PlatformException {
        native = <Object?, Object?>{};
      }
    }
    final int totalRam =
        (native['totalRamMb'] as num?)?.round() ??
        (cores <= 2 ? 2048 : (cores <= 4 ? 4096 : 8192));
    final DeviceTier tier = totalRam <= 2048
        ? DeviceTier.t0
        : totalRam <= 4096
        ? DeviceTier.t1
        : totalRam <= 8192
        ? DeviceTier.t2
        : DeviceTier.t3;
    final Directory support = await getApplicationSupportDirectory();
    final int freeStorageMb =
        (native['freeStorageMb'] as num?)?.round() ??
        _freeSpaceFallback(support);
    final BaseDeviceInfo info = await DeviceInfoPlugin().deviceInfo;

    return DeviceProfile(
      tier: tier,
      label: tier.index <= 1
          ? 'Light'
          : (tier == DeviceTier.t2 ? 'Standard' : 'Powerful'),
      totalRamMb: totalRam,
      cpuCores: cores,
      gpuName:
          native['gpuName'] as String? ??
          (Platform.isAndroid ? 'Android graphics' : 'Windows graphics'),
      vulkanSupported: native['vulkanSupported'] as bool? ?? false,
      benchmarkMs: stopwatch.elapsedMilliseconds,
      freeStorageMb: freeStorageMb,
      osVersion:
          info.data['version']?.toString() ?? Platform.operatingSystemVersion,
      measuredAt: DateTime.now(),
    );
  }

  int _freeSpaceFallback(Directory directory) {
    // Dart has no portable free-space API. Native profiling replaces this
    // conservative value when the platform bridge is available.
    return directory.existsSync() ? 1024 : 512;
  }
}
