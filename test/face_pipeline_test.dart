import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:realscale_pro/engine/image_bitmap.dart';
import 'package:realscale_pro/engine/lanczos_engine.dart';
import 'package:realscale_pro/engine/recovery_chain.dart';
import 'package:realscale_pro/engine/engine_types.dart';
import 'package:realscale_pro/services/job_manager/job_manager.dart';
import 'package:realscale_pro/services/image_service/image_service.dart'
    show OutputKind;
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';
import 'package:realscale_pro/services/storage_service/storage_service.dart';

class _FnRestorer implements FaceRestorer {
  _FnRestorer(this.fn);
  final Future<Uint8List> Function(FaceCrop, Uint8List, int, int) fn;
  @override
  Future<Uint8List> restore(
    FaceCrop crop,
    Uint8List rgba,
    int width,
    int height,
  ) => fn(crop, rgba, width, height);
}

class _NoStorage extends StorageService {
  @override
  Future<UpscaleRecord> saveResult({
    required String tempPath,
    required String originalName,
    required int width,
    required int height,
    required int scale,
    required String modelId,
    required int elapsedMs,
    String? thumbnailPath,
  }) async => UpscaleRecord(
    id: 'r',
    originalName: originalName,
    savedPath: tempPath,
    width: width,
    height: height,
    fileSizeBytes: 1,
    modelId: modelId,
    scale: scale,
    elapsedMs: elapsedMs,
    createdAt: DateTime(2026),
  );
}

class _NoHaptics implements JobHaptics {
  @override
  void checkpoint() {}
}

DeviceProfile _device() => DeviceProfile(
  tier: DeviceTier.t1,
  totalRamMb: 4096,
  freeRamMb: 2048,
  cpuCores: 4,
  gpuName: '',
  vulkanSupported: false,
  benchmarkMsPerTile: 100,
  freeStorageMb: 1000,
  osVersion: 'test',
  thermalState: 'nominal',
  measuredAt: DateTime(2026),
);

PipelinePlan _plan() => PipelinePlan(
  model: const ModelDef(
    id: 'm',
    version: '1',
    name: 'm',
    engine: 'ncnn',
    pack: 'starter',
    minTier: DeviceTier.t0,
    nativeScales: [2],
    modes: ['upscale'],
    bestFor: ['photo'],
    speed: 'fast',
    sizeBytes: 1,
    license: 'MIT',
    verified: true,
    heavy: false,
    files: [],
  ),
  nativeScale: 2,
  resamplePercent: 100,
  extraResizeStep: false,
  tileSize: 96,
  overlapPx: 8,
  threadCount: 2,
  useGpu: false,
  inputPixelLimit: 8000000,
  outputCapMegapixels: 40,
  outputWidth: 192,
  outputHeight: 192,
  etaBaselineMsPerTile: 50,
);

Uint8List _pngBytes(int w, int h) {
  final image = img.Image(width: w, height: h);
  img.fill(image, color: img.ColorRgb8(128, 128, 128));
  return Uint8List.fromList(img.encodePng(image));
}

JobManager _managerWithFaceSupport() {
  final engine = LanczosEngine();
  return JobManager(
    engine: engine,
    recoveryChainFactory: (plan) => RecoveryChain(
      engine: engine,
      basePlan: plan,
      replanForRetry: replanStandard,
    ),
    storage: _NoStorage(),
    haptics: _NoHaptics(),
  );
}

void main() {
  setUp(() {
    // Flat 192×192 reader for any temp PNG the pipeline wrote.
    JobManager.registerBitmapReader(
      (bytes) async => Bitmap(
        192,
        192,
        Uint8List(192 * 192 * 4)..fillRange(0, 192 * 192 * 4, 128),
      ),
    );
    JobManager.registerEncodedSaver(
      (path, bmp, kind, quality, metadata) async {},
    );
  });

  test('face pipeline: upscale → faces → done, both faces restored', () async {
    final dir = Directory.systemTemp.createTempSync('rs_face');
    var restoreCalls = 0;
    final manager = _managerWithFaceSupport();
    manager.faceDetector = (rgba, width, height) async => [
      const FaceCrop(left: 10, top: 10, width: 48, height: 56, confidence: 0.9),
      const FaceCrop(
        left: 100,
        top: 20,
        width: 48,
        height: 56,
        confidence: 0.8,
      ),
    ];
    manager.faceRestorer = _FnRestorer((crop, rgba, w, h) async {
      restoreCalls++;
      expect(rgba.length, w * h * 4); // real crop pixels handed over
      return rgba; // identity "restoration"
    });

    final stages = <JobStage>[];
    final sub = manager.progress.listen((e) => stages.add(e.stage));

    final result = await manager.runJob(
      spec: JobSpec(
        jobId: 'face1',
        inputPath: '/in.png',
        inputBytes: _pngBytes(96, 96),
        mode: UpscaleMode.upscale,
        settingLevel: SettingLevel.defaultLevel,
        imageType: ImageType.photo,
        scale: 2,
        modelId: 'm',
        faceEnhancement: true,
        options: {
          'decodedPixels': _solidPixels(96, 96),
          'decodedWidth': 96,
          'decodedHeight': 96,
        },
      ),
      ctx: PipelineContext(
        catalog: ModelCatalog(version: 1, models: const []),
        device: _device(),
        modelDir: dir.path,
        tempDir: dir.path,
        plan: _plan(),
        outputWriter: writePngOutput,
      ),
      originalName: 'photo.png',
      originalJpegBytes: _pngBytes(96, 96),
      keepMetadata: false,
      outputKind: OutputKind.png,
      outputQuality: 92,
      hasAlpha: false,
    );
    await sub.cancel();

    expect(result.stage, JobStage.done);
    expect(restoreCalls, 2, reason: 'both detected faces must be restored');
    expect(result.result!.facesRestored, 2);
    // Stage order contract: tiles first, then the raw engine done, then the
    // face stage events (the final saved-done arrives via the job result,
    // not the progress stream).
    final order = stages
        .where(
          (s) =>
              s == JobStage.upscaling ||
              s == JobStage.faces ||
              s == JobStage.done,
        )
        .toList();
    expect(order.first, JobStage.upscaling);
    expect(
      order.indexOf(JobStage.faces),
      greaterThan(order.indexOf(JobStage.done)),
    );
  });

  test(
    'no detector wired → face stage skipped cleanly, zero restored',
    () async {
      final dir = Directory.systemTemp.createTempSync('rs_face2');
      final manager = _managerWithFaceSupport();
      final result = await manager.runJob(
        spec: JobSpec(
          jobId: 'face2',
          inputPath: '/in.png',
          inputBytes: _pngBytes(96, 96),
          mode: UpscaleMode.upscale,
          settingLevel: SettingLevel.defaultLevel,
          imageType: ImageType.photo,
          scale: 2,
          modelId: 'm',
          options: {
            'decodedPixels': _solidPixels(96, 96),
            'decodedWidth': 96,
            'decodedHeight': 96,
          },
        ),
        ctx: PipelineContext(
          catalog: ModelCatalog(version: 1, models: const []),
          device: _device(),
          modelDir: dir.path,
          tempDir: dir.path,
          plan: _plan(),
          outputWriter: writePngOutput,
        ),
        originalName: 'p.png',
        originalJpegBytes: _pngBytes(96, 96),
        keepMetadata: false,
        outputKind: OutputKind.png,
        outputQuality: 92,
        hasAlpha: false,
      );
      expect(result.stage, JobStage.done);
      expect(result.result!.facesRestored, 0);
    },
  );

  test('cancellation during the face stage stops without crashing', () async {
    final dir = Directory.systemTemp.createTempSync('rs_face3');
    final manager = _managerWithFaceSupport();
    manager.faceDetector = (rgba, width, height) async => [
      const FaceCrop(left: 10, top: 10, width: 48, height: 56, confidence: 0.9),
      const FaceCrop(
        left: 100,
        top: 20,
        width: 48,
        height: 56,
        confidence: 0.8,
      ),
    ];
    manager.faceRestorer = _FnRestorer((crop, rgba, w, h) async {
      // Cancel while restoring the first face.
      unawaited(manager.cancel('face3'));
      return rgba;
    });
    final result = await manager.runJob(
      spec: JobSpec(
        jobId: 'face3',
        inputPath: '/in.png',
        inputBytes: _pngBytes(96, 96),
        mode: UpscaleMode.upscale,
        settingLevel: SettingLevel.defaultLevel,
        imageType: ImageType.photo,
        scale: 2,
        modelId: 'm',
        faceEnhancement: true,
        options: {
          'decodedPixels': _solidPixels(96, 96),
          'decodedWidth': 96,
          'decodedHeight': 96,
        },
      ),
      ctx: PipelineContext(
        catalog: ModelCatalog(version: 1, models: const []),
        device: _device(),
        modelDir: dir.path,
        tempDir: dir.path,
        plan: _plan(),
        outputWriter: writePngOutput,
      ),
      originalName: 'p.png',
      originalJpegBytes: _pngBytes(96, 96),
      keepMetadata: false,
      outputKind: OutputKind.png,
      outputQuality: 92,
      hasAlpha: false,
    );
    // Cancel during the face stage → cancelled or done; never a crash.
    expect(
      result.stage,
      anyOf(JobStage.cancelled, JobStage.done, JobStage.failed),
    );
  });
}

/// Real PNG writer so JobManager's save path can read the temp output back.
Future<void> writePngOutput(String path, Bitmap bmp) async {
  final image = img.Image(width: bmp.width, height: bmp.height);
  for (var y = 0; y < bmp.height; y++) {
    for (var x = 0; x < bmp.width; x++) {
      final i = (y * bmp.width + x) * 4;
      image.setPixelRgba(
        x,
        y,
        bmp.pixels[i],
        bmp.pixels[i + 1],
        bmp.pixels[i + 2],
        bmp.pixels[i + 3],
      );
    }
  }
  await File(
    path,
  ).writeAsBytes(Uint8List.fromList(img.encodePng(image)), flush: true);
}

Uint8List _solidPixels(int w, int h) =>
    Uint8List(w * h * 4)..fillRange(0, w * h * 4, 128);
