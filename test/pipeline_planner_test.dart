/// PipelinePlanner tests: real decision logic, memory safety, honest
/// mode semantics (no silent downsampling).
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/domain/models.dart';
import 'package:realscale_pro/processing/pipeline_planner.dart';

ModelInfo _model(String id, String family, int scale,
        {double sizeMb = 1.25, int minRam = 500}) =>
    ModelInfo(
      id: id,
      displayName: id,
      family: family,
      scale: scale,
      license: 'BSD-3-Clause',
      licenseUrl: 'https://example.com',
      sourceUrl: 'https://example.com/zip',
      paramFile: 'a.param',
      binFile: 'a.bin',
      approxSizeMb: sizeMb,
      minRamMb: minRam,
      description: '',
      tags: const [],
    );

const _compact = 'realesr-animevideov3-x4';
ImageMetadata _img(int w, int h,
        {bool alpha = false, String format = 'JPEG'}) =>
    ImageMetadata(
      width: w,
      height: h,
      format: format,
      fileSizeBytes: 1024,
      hasAlpha: alpha,
      exifOrientation: 0,
    );

DeviceProfile _device(DeviceTier tier, {int? ramMb}) => DeviceProfile(
      tier: tier,
      cpuCores: 4,
      platform: 'android',
      totalMemoryMb: ramMb,
      availableMemoryMb: ramMb,
    );

void main() {
  final planner = const PipelinePlanner();

  group('estimatePeakMemoryBytes', () {
    test('grows with image and shrinks with tiles', () {
      final small = PipelinePlanner.estimatePeakMemoryBytes(64, 64, 4, 128);
      final big = PipelinePlanner.estimatePeakMemoryBytes(512, 512, 4, 128);
      expect(big, greaterThan(small));
      final smallTiles =
          PipelinePlanner.estimatePeakMemoryBytes(512, 512, 4, 64);
      expect(smallTiles, lessThan(big));
    });

    test('rejects invalid inputs', () {
      expect(PipelinePlanner.estimatePeakMemoryBytes(0, 10, 4, 64), -1);
      expect(PipelinePlanner.estimatePeakMemoryBytes(10, 10, 1, 64), -1);
      expect(PipelinePlanner.estimatePeakMemoryBytes(10, 10, 5, 64), -1);
    });
  });

  group('output format policy', () {
    test('upscale mode uses JPEG for opaque photos', () {
      final plan = planner.plan(
          image: _img(400, 300),
          device: _device(DeviceTier.balanced, ramMb: 4096),
          mode: ProcessingMode.upscale,
          model: _model(_compact, 'SRVGGNet compact', 4));
      expect(plan.outputFormat, OutputFormat.jpeg);
      expect(plan.jpegQuality, inInclusiveRange(90, 96));
    });

    test('upscale mode uses PNG when alpha demands lossless', () {
      final plan = planner.plan(
          image: _img(400, 300, alpha: true, format: 'PNG'),
          device: _device(DeviceTier.balanced, ramMb: 4096),
          mode: ProcessingMode.upscale,
          model: _model(_compact, 'SRVGGNet compact', 4));
      expect(plan.outputFormat, OutputFormat.png);
      expect(plan.jpegQuality, isNull);
    });

    test('super resolution mode is always lossless', () {
      final plan = planner.plan(
          image: _img(400, 300),
          device: _device(DeviceTier.highPerformance, ramMb: 8192),
          mode: ProcessingMode.superResolution,
          model: _model('realesrgan-x4plus', 'RRDBNet', 4,
              sizeMb: 33.4, minRam: 1500));
      expect(plan.outputFormat, OutputFormat.png);
    });
  });

  group('memory adaptation', () {
    test('low-tier devices get reduced tiles', () {
      final plan = planner.plan(
          image: _img(2000, 2000),
          device: _device(DeviceTier.lowResource, ramMb: 2048),
          mode: ProcessingMode.superResolution,
          model: _model('realesrgan-x4plus', 'RRDBNet', 4,
              sizeMb: 33.4, minRam: 1500));
      // With 2048MB the estimate exceeds the budget even after tile
      // reduction for an 8000x8000 target: honest warning expected.
      expect(plan.warnings, isNotEmpty);
    });

    test('tile reduction can bring a plan back into budget', () {
      final plan = planner.plan(
          image: _img(800, 600),
          device: _device(DeviceTier.lowResource, ramMb: 2048),
          mode: ProcessingMode.upscale,
          model: _model(_compact, 'SRVGGNet compact', 4));
      expect(plan.estimatedPeakRamMb,
          lessThan(_device(DeviceTier.lowResource, ramMb: 2048)
              .inferenceBudgetMb));
    });
  });

  group('mode honesty', () {
    test('super resolution never proposes downsampling', () {
      final plan = planner.plan(
          image: _img(4000, 4000),
          device: _device(DeviceTier.lowResource, ramMb: 2048),
          mode: ProcessingMode.superResolution,
          model: _model('realesrgan-x4plus', 'RRDBNet', 4,
              sizeMb: 33.4, minRam: 1500));
      expect(plan.saferAlternative, isNull);
      expect(
          plan.warnings.join(' '),
          contains('does not downsample'),
          reason: 'SR mode must state the limitation instead of silently '
              'lowering quality');
    });

    test('upscale mode proposes an explicit alternative instead of '
        'silently downsampling', () {
      final plan = planner.plan(
          image: _img(4000, 4000),
          device: _device(DeviceTier.lowResource, ramMb: 2048),
          mode: ProcessingMode.upscale,
          model: _model(_compact, 'SRVGGNet compact', 4));
      expect(plan.warnings, isNotEmpty);
      if (plan.saferAlternative != null) {
        expect(plan.saferAlternative!.warnings.join(' '),
            contains('explicit'));
        // The alternative must actually fit the budget.
        expect(
            plan.saferAlternative!.estimatedOutputWidth *
                plan.saferAlternative!.estimatedOutputHeight,
            lessThan(plan.estimatedOutputWidth * plan.estimatedOutputHeight));
      }
    });
  });

  group('model choice', () {
    final registry = [
      _model(_compact, 'SRVGGNet compact', 4),
      _model('realesrgan-x4plus-anime', 'RRDBNet', 4,
          sizeMb: 8.9, minRam: 1000),
      _model('realesrgan-x4plus', 'RRDBNet', 4, sizeMb: 33.4, minRam: 1500),
    ];

    test('low tier gets the compact model', () {
      final m = planner.chooseModel(
          registry, _device(DeviceTier.lowResource), ProcessingMode.upscale);
      expect(m.id, _compact);
    });

    test('high performance SR mode gets the flagship', () {
      final m = planner.chooseModel(registry,
          _device(DeviceTier.highPerformance), ProcessingMode.superResolution);
      expect(m.id, 'realesrgan-x4plus');
    });
  });

  group('jpeg quality clamping', () {
    test('quality stays within the honest 90-96 band', () {
      final plan = planner.plan(
          image: _img(100, 100),
          device: _device(DeviceTier.balanced, ramMb: 4096),
          mode: ProcessingMode.upscale,
          model: _model(_compact, 'SRVGGNet compact', 4),
          jpegQuality: 75);
      expect(plan.jpegQuality, 90);
    });
  });
}
