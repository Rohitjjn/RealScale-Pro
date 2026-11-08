/// Device profile + tier classification tests.
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/domain/models.dart';
import 'package:realscale_pro/services/device_profiler.dart';

void main() {
  group('classifyTier', () {
    test('2 GB RAM devices are low resource', () {
      expect(
          DeviceProfiler.classifyTier(totalMemoryMb: 2048, cores: 4),
          DeviceTier.lowResource);
    });

    test('4 GB devices are balanced', () {
      expect(
          DeviceProfiler.classifyTier(totalMemoryMb: 4096, cores: 4),
          DeviceTier.balanced);
    });

    test('8 GB + 8 cores are high performance', () {
      expect(
          DeviceProfiler.classifyTier(totalMemoryMb: 8192, cores: 8),
          DeviceTier.highPerformance);
    });

    test('unknown RAM degrades to balanced (documented default)', () {
      expect(DeviceProfiler.classifyTier(totalMemoryMb: null, cores: 4),
          DeviceTier.balanced);
    });
  });

  group('DeviceProfile.inferenceBudgetMb', () {
    test('uses 70% of the smaller memory figure', () {
      const p = DeviceProfile(
        tier: DeviceTier.balanced,
        cpuCores: 4,
        platform: 'android',
        totalMemoryMb: 6000,
        availableMemoryMb: 3000,
      );
      expect(p.inferenceBudgetMb, 2100);
    });

    test('unknown RAM uses conservative tier defaults', () {
      const low = DeviceProfile(
          tier: DeviceTier.lowResource, cpuCores: 4, platform: 'android');
      expect(low.inferenceBudgetMb, 980); // 1400 * 0.7
    });
  });

  group('engine threads by tier', () {
    test('low resource caps threads at 2', () {
      const low = DeviceProfile(
          tier: DeviceTier.lowResource, cpuCores: 8, platform: 'android');
      expect(low.engineThreads, 2);
    });
  });
}
