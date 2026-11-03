import 'dart:async';
import 'dart:ffi';
import 'dart:io';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as image_lib;
import 'package:path_provider/path_provider.dart';

import '../../core/models.dart';
import '../safe_cpu_engine.dart';
import '../upscale_engine.dart';

typedef _RunNative = Int32 Function(
  Pointer<Utf8>,
  Pointer<Utf8>,
  Pointer<Utf8>,
  Pointer<Utf8>,
  Int32,
  Int32,
);
typedef _RunDart = int Function(
  Pointer<Utf8>,
  Pointer<Utf8>,
  Pointer<Utf8>,
  Pointer<Utf8>,
  int,
  int,
);
typedef _ValueNative = Int32 Function();
typedef _ValueDart = int Function();
typedef _CancelNative = Void Function();
typedef _CancelDart = void Function();

/// ncnn FFI adapter with a safe portable fallback.
class NcnnEngine implements UpscaleEngine {
  NcnnEngine({SafeCpuEngine? fallback}) : _fallback = fallback ?? SafeCpuEngine();

  final SafeCpuEngine _fallback;
  final Map<String, _CancelDart> _cancellers = <String, _CancelDart>{};

  @override
  String get engineId => 'ncnn';

  @override
  Stream<EngineEvent> run(JobSpec job) {
    final StreamController<EngineEvent> controller = StreamController<EngineEvent>();
    () async {
      DynamicLibrary library;
      try {
        library = _openLibrary();
      } on Object {
        await controller.addStream(_fallback.run(job));
        await controller.close();
        return;
      }
      final _ValueDart progress = library.lookupFunction<_ValueNative, _ValueDart>('realscale_progress');
      final _ValueDart total = library.lookupFunction<_ValueNative, _ValueDart>('realscale_total');
      final _CancelDart cancel = library.lookupFunction<_CancelNative, _CancelDart>('realscale_cancel');
      _cancellers[job.id] = cancel;
      final Stopwatch elapsed = Stopwatch()..start();
      Timer? poll;
      try {
        final _NativeFiles files = await _prepareFiles(job);
        poll = Timer.periodic(const Duration(milliseconds: 200), (_) {
          final int count = progress();
          final int all = total();
          if (all > 0) {
            final double average = count == 0 ? 0 : elapsed.elapsedMilliseconds / count;
            controller.add(
              EngineProgress(
                ProgressEvent(
                  jobId: job.id,
                  stage: JobStage.enhancing,
                  completedTiles: count,
                  totalTiles: all,
                  elapsed: elapsed.elapsed,
                  eta: count == 0
                      ? null
                      : Duration(milliseconds: ((all - count) * average).round()),
                ),
              ),
            );
          }
        });
        final int code = await Isolate.run(() => _invokeNative(files, job.tileSize));
        poll.cancel();
        if (code == 0) {
          final String png = await Isolate.run(() => _ppmToPng(files.outputPpm));
          elapsed.stop();
          controller.add(EngineCompleted(png, elapsed.elapsed));
        } else if (code == 1) {
          controller.add(const EngineFailed(AppFailure.cancelled));
        } else {
          controller.add(const EngineFailed(AppFailure.engineUnavailable));
        }
      } on Object {
        poll?.cancel();
        await controller.addStream(_fallback.run(job));
      } finally {
        _cancellers.remove(job.id);
        await controller.close();
      }
    }();
    return controller.stream;
  }

  @override
  Future<void> cancel(String jobId) async {
    _cancellers[jobId]?.call();
    await _fallback.cancel(jobId);
  }

  @override
  Future<void> dispose() => _fallback.dispose();

  Future<_NativeFiles> _prepareFiles(JobSpec job) async {
    final Directory support = await getApplicationSupportDirectory();
    final Directory modelDirectory = Directory('${support.path}${Platform.pathSeparator}models');
    final Directory temporary = await getTemporaryDirectory();
    await modelDirectory.create(recursive: true);
    final bool anime = job.input.kind == ImageKind.anime;
    final String stem = anime ? 'up2x-no-denoise' : 'scale2.0x_model';
    final File parameter = File('${modelDirectory.path}${Platform.pathSeparator}$stem.param');
    final File model = File('${modelDirectory.path}${Platform.pathSeparator}$stem.bin');
    if (!await parameter.exists()) {
      await parameter.writeAsBytes(
        (await rootBundle.load('assets/models_starter/$stem.param')).buffer.asUint8List(),
        flush: true,
      );
    }
    if (!await model.exists()) {
      await model.writeAsBytes(
        (await rootBundle.load('assets/models_starter/$stem.bin')).buffer.asUint8List(),
        flush: true,
      );
    }
    final String inputPpm = '${temporary.path}${Platform.pathSeparator}${job.id}_input.ppm';
    final String outputPpm = '${temporary.path}${Platform.pathSeparator}${job.id}_output.ppm';
    await Isolate.run(() => _imageToPpm(job.preparedInputPath ?? job.input.path, inputPpm));
    return _NativeFiles(parameter.path, model.path, inputPpm, outputPpm);
  }
}

DynamicLibrary _openLibrary() {
  if (Platform.isWindows) return DynamicLibrary.open('realscale_ncnn.dll');
  if (Platform.isAndroid) return DynamicLibrary.open('librealscale_ncnn.so');
  throw UnsupportedError('unsupported_platform');
}

int _invokeNative(_NativeFiles files, int tileSize) {
  final DynamicLibrary library = _openLibrary();
  final _RunDart run = library.lookupFunction<_RunNative, _RunDart>('realscale_upscale');
  final Pointer<Utf8> parameter = files.parameter.toNativeUtf8();
  final Pointer<Utf8> model = files.model.toNativeUtf8();
  final Pointer<Utf8> input = files.inputPpm.toNativeUtf8();
  final Pointer<Utf8> output = files.outputPpm.toNativeUtf8();
  try {
    return run(
      parameter,
      model,
      input,
      output,
      tileSize,
      Platform.numberOfProcessors.clamp(1, 4) as int,
    );
  } finally {
    calloc.free(parameter);
    calloc.free(model);
    calloc.free(input);
    calloc.free(output);
  }
}

Future<void> _imageToPpm(String sourcePath, String outputPath) async {
  final image_lib.Image? decoded = image_lib.decodeImage(await File(sourcePath).readAsBytes());
  if (decoded == null) throw const FormatException('unsupported_image');
  final image_lib.Image oriented = image_lib.bakeOrientation(decoded);
  final BytesBuilder bytes = BytesBuilder(copy: false)
    ..add('P6\n${oriented.width} ${oriented.height}\n255\n'.codeUnits);
  for (final image_lib.Pixel pixel in oriented) {
    bytes.add(<int>[pixel.r.toInt(), pixel.g.toInt(), pixel.b.toInt()]);
  }
  await File(outputPath).writeAsBytes(bytes.takeBytes(), flush: true);
}

Future<String> _ppmToPng(String ppmPath) async {
  final Uint8List data = await File(ppmPath).readAsBytes();
  int newline = 0;
  int offset = 0;
  while (offset < data.length && newline < 3) {
    if (data[offset++] == 10) newline += 1;
  }
  final String header = String.fromCharCodes(data.sublist(0, offset));
  final List<String> parts = header.split(RegExp(r'\s+'));
  final int width = int.parse(parts[1]);
  final int height = int.parse(parts[2]);
  final image_lib.Image image = image_lib.Image.fromBytes(
    width: width,
    height: height,
    bytes: data.buffer,
    bytesOffset: offset,
    numChannels: 3,
    order: image_lib.ChannelOrder.rgb,
  );
  final String pngPath = ppmPath.replaceAll(RegExp(r'\.ppm$'), '.png');
  await File(pngPath).writeAsBytes(image_lib.encodePng(image), flush: true);
  await File(ppmPath).delete();
  return pngPath;
}

class _NativeFiles {
  const _NativeFiles(this.parameter, this.model, this.inputPpm, this.outputPpm);
  final String parameter;
  final String model;
  final String inputPpm;
  final String outputPpm;
}
