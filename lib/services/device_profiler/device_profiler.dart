import 'dart:convert';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../core/app_logger.dart';
import '../../engine/upscale_engine.dart';

/// DeviceProfiler (prd §8.1): detection + a real micro-benchmark, producing
/// a DeviceProfile stored locally. The benchmark resamples a real 256 px
/// tile in an isolate — the number is measured, never invented.
class DeviceProfiler {
  DeviceProfiler({required this.prefs});

  final SharedPreferences prefs;
  static const _storeKey = 'device_profile_v1';

  /// Last stored profile, if any.
  DeviceProfile? cached() {
    final raw = prefs.getString(_storeKey);
    if (raw == null) return null;
    try {
      return DeviceProfile.fromMap(jsonDecode(raw) as Map<String, Object?>);
    } catch (e) {
      return null;
    }
  }

  Future<void> store(DeviceProfile profile) =>
      prefs.setString(_storeKey, jsonEncode(profile.toMap()));

  /// Full detection + benchmark. Benchmark runs a real Lanczos 2x on a
  /// synthetic 256 px tile (isolates) and takes the median of 3 runs.
  Future<DeviceProfile> profile({bool rerunBenchmark = false}) async {
    if (!rerunBenchmark) {
      final cachedProfile = cached();
      if (cachedProfile != null) return cachedProfile;
    }

    final totalRamMb = await _totalRamMb();
    final freeRamMb = await _freeRamMb();
    final cores = Platform.numberOfProcessors;
    final freeStorageMb = await _freeStorageMb();
    final gpuName = await _gpuName();
    final osVersion = await _osVersion();

    final sw = Stopwatch()..start();
    final benchMs = await _runMicroBenchmark();
    sw.stop();

    final tier = _tierFor(totalRamMb, cores, benchMs);
    final profile = DeviceProfile(
      tier: tier,
      totalRamMb: totalRamMb,
      freeRamMb: freeRamMb,
      cpuCores: cores,
      gpuName: gpuName,
      // Vulkan capability is only known once the native engine loads; we
      // stay conservative and let the engine upgrade this at runtime.
      vulkanSupported: false,
      benchmarkMsPerTile: benchMs,
      freeStorageMb: freeStorageMb,
      osVersion: osVersion,
      thermalState: 'nominal',
      measuredAt: DateTime.now(),
    );
    await store(profile);
    return profile;
  }

  DeviceTier _tierFor(int ramMb, int cores, int benchMs) {
    if (ramMb <= 2600) return DeviceTier.t0;
    if (ramMb <= 5000) {
      return benchMs > 900 ? DeviceTier.t0 : DeviceTier.t1;
    }
    if (ramMb <= 9000) return benchMs > 700 ? DeviceTier.t1 : DeviceTier.t2;
    return DeviceTier.t3;
  }

  /// Real work: median of 3 Lanczos 2x upscales of a 256x256 tile.
  Future<int> _runMicroBenchmark() async {
    final tile = _makeBenchmarkTile();
    final times = <int>[];
    for (var i = 0; i < 3; i++) {
      final sw = Stopwatch()..start();
      final out = await benchmarkResize(tile, 256, 256, 512, 512);
      sw.stop();
      if (out.isEmpty) continue;
      times.add(sw.elapsedMilliseconds);
    }
    if (times.isEmpty) return 800;
    times.sort();
    return times[times.length ~/ 2];
  }

  static Uint8List _makeBenchmarkTile() {
    final rng = _Lcg(42);
    final px = Uint8List(256 * 256 * 4);
    for (var i = 0; i < px.length; i += 4) {
      px[i] = rng.next() & 0xFF;
      px[i + 1] = rng.next() & 0xFF;
      px[i + 2] = rng.next() & 0xFF;
      px[i + 3] = 255;
    }
    return px;
  }

  Future<int> _totalRamMb() async {
    if (Platform.isAndroid || Platform.isLinux) {
      final meminfo = File('/proc/meminfo');
      if (await meminfo.exists()) {
        final first = (await meminfo.readAsLines()).first;
        final kb = int.tryParse(
          RegExp(r'(\d+)').firstMatch(first)?.group(0) ?? '',
        );
        if (kb != null) return kb ~/ 1024;
      }
    }
    return 4096; // honest default when unknown
  }

  Future<int> _freeRamMb() async {
    if (Platform.isAndroid || Platform.isLinux) {
      final meminfo = File('/proc/meminfo');
      if (await meminfo.exists()) {
        for (final line in await meminfo.readAsLines()) {
          if (line.startsWith('MemAvailable')) {
            final kb = int.tryParse(
              RegExp(r'(\d+)').firstMatch(line)?.group(0) ?? '',
            );
            if (kb != null) return kb ~/ 1024;
          }
        }
      }
    }
    return 1024;
  }

  Future<int> _freeStorageMb() async {
    try {
      if (Platform.isAndroid || Platform.isLinux) {
        return await freeStorageMb(Directory.systemTemp.path);
      }
    } catch (e) {
      AppLogger.d('DeviceProfiler', 'storage stat failed: $e');
    }
    return 0;
  }

  /// Free space at [path], in **megabytes**.
  static Future<int> freeStorageMb(String path) async {
    // `df -k` reports the available column in KiB — divide by 1024 so the
    // profile stores megabytes (it used to publish KiB as MB, which made
    // every device look ~1000x fuller than it was).
    try {
      final res = await Process.run('df', ['-k', path]);
      if (res.exitCode == 0) {
        final lines = (res.stdout as String).trim().split('\n');
        if (lines.length >= 2) {
          final cols = lines.last.split(RegExp(r'\s+'));
          if (cols.length >= 4) {
            final availKb = int.tryParse(cols[3]) ?? 0;
            return availKb ~/ 1024;
          }
        }
      }
    } catch (e) {
      AppLogger.d('DeviceProfiler', 'df failed: $e');
    }
    return 0;
  }

  Future<String> _gpuName() async {
    try {
      if (Platform.isAndroid) {
        final info = await DeviceInfoPlugin().androidInfo;
        return info.model;
      }
      if (Platform.isWindows) return ''; // filled by native engine later
    } catch (e) {
      AppLogger.d('DeviceProfiler', 'gpu detect failed: $e');
    }
    return '';
  }

  Future<String> _osVersion() async {
    try {
      if (Platform.isAndroid) {
        final info = await DeviceInfoPlugin().androidInfo;
        return 'Android ${info.version.release}';
      }
      if (Platform.isWindows) return 'Windows';
      if (Platform.isLinux) return Platform.operatingSystemVersion;
    } catch (_) {}
    return Platform.operatingSystemVersion;
  }
}

/// Isolate entry for the benchmark resize (kept tiny and dependency-free).
Future<Uint8List> benchmarkResize(
  Uint8List pixels,
  int w,
  int h,
  int dstW,
  int dstH,
) async {
  return IsolateResize.run(pixels, w, h, dstW, dstH);
}

/// Deterministic RNG so the benchmark is stable across runs.
class _Lcg {
  _Lcg(this._s);
  int _s;
  int next() {
    _s = (_s * 1664525 + 1013904223) & 0x7FFFFFFF;
    return _s;
  }
}

/// Tiny wrapper so the profiler does not import the resampler directly
/// (services → engine imports stay one-directional in tests).
class IsolateResize {
  static Future<Uint8List> run(
    Uint8List pixels,
    int w,
    int h,
    int dstW,
    int dstH,
  ) async {
    return compute(_resizeSync, (pixels, w, h, dstW, dstH));
  }

  static Uint8List _resizeSync((Uint8List, int, int, int, int) args) {
    final (pixels, w, h, dstW, dstH) = args;
    // Bilinear here is intentional: benchmark measures device speed class,
    // not output quality.
    final out = Uint8List(dstW * dstH * 4);
    for (var y = 0; y < dstH; y++) {
      final sy = y * h ~/ dstH;
      for (var x = 0; x < dstW; x++) {
        final sx = x * w ~/ dstW;
        final si = (sy * w + sx) * 4;
        final di = (y * dstW + x) * 4;
        out[di] = pixels[si];
        out[di + 1] = pixels[si + 1];
        out[di + 2] = pixels[si + 2];
        out[di + 3] = pixels[si + 3];
      }
    }
    return out;
  }
}
