import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as image_lib;
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../core/models.dart';

/// Saves results without overwriting originals and maintains local history.
class StorageService {
  static const String _historyName = 'upscales.json';
  static const MethodChannel _channel = MethodChannel('realscale/device');

  Future<Directory> outputDirectory() async {
    if (Platform.isWindows) {
      final String? profile = Platform.environment['USERPROFILE'];
      if (profile != null) {
        return Directory(
          '$profile${Platform.pathSeparator}Pictures${Platform.pathSeparator}RealScale-Pro',
        );
      }
    }
    final Directory documents = await getApplicationDocumentsDirectory();
    if (Platform.isAndroid) {
      return Directory(
        '${documents.path}${Platform.pathSeparator}RealScale-Pro',
      );
    }
    return Directory('${documents.path}${Platform.pathSeparator}RealScale-Pro');
  }

  Future<UpscaleRecord> save({
    required JobSpec job,
    required String temporaryOutput,
    required Duration elapsed,
  }) async {
    final Directory directory = await outputDirectory();
    await directory.create(recursive: true);
    final String base = job.input.name.replaceFirst(RegExp(r'\.[^.]+$'), '');
    final String sourceExtension = job.input.name.contains('.')
        ? job.input.name.split('.').last.toLowerCase()
        : 'jpg';
    final bool keepPng = sourceExtension == 'png';
    final String outputExtension = keepPng ? 'png' : 'jpg';
    final DateTime now = DateTime.now();
    final String stamp = now.toIso8601String().replaceAll(RegExp('[:.]'), '-');
    final String destination =
        '${directory.path}${Platform.pathSeparator}${base}_RealScale_${job.scale}x_$stamp.$outputExtension';
    final File saved;
    if (keepPng) {
      saved = await File(temporaryOutput).copy(destination);
    } else {
      final List<int> encoded = await Isolate.run(() {
        final image_lib.Image? result = image_lib.decodeImage(
          File(temporaryOutput).readAsBytesSync(),
        );
        if (result == null) throw const FormatException('unsupported_image');
        return image_lib.encodeJpg(result, quality: 95);
      });
      saved = await File(destination).writeAsBytes(encoded, flush: true);
    }
    if (Platform.isAndroid) {
      try {
        final int sdk = (await DeviceInfoPlugin().androidInfo).version.sdkInt;
        if (sdk <= 28) await Permission.storage.request();
        await _channel.invokeMethod<String>('saveImage', <String, String>{
          'path': saved.path,
          'name': saved.uri.pathSegments.last,
          'mime': keepPng ? 'image/png' : 'image/jpeg',
        });
      } on PlatformException {
        // The private copy remains usable when gallery insertion is unavailable.
      }
    }
    final UpscaleRecord record = UpscaleRecord(
      id: job.id,
      originalPath: job.input.path,
      outputPath: saved.path,
      width: job.input.width * job.scale,
      height: job.input.height * job.scale,
      bytes: await saved.length(),
      modelId: job.modelId,
      scale: job.scale,
      elapsed: elapsed,
      createdAt: now,
    );
    final List<UpscaleRecord> records = await loadHistory();
    await _writeHistory(<UpscaleRecord>[record, ...records]);
    return record;
  }

  Future<List<UpscaleRecord>> loadHistory() async {
    try {
      final File file = await _historyFile();
      if (!await file.exists()) {
        return <UpscaleRecord>[];
      }
      final List<dynamic> values =
          jsonDecode(await file.readAsString()) as List<dynamic>;
      return values
          .map((dynamic value) {
            final Map<String, dynamic> map = value as Map<String, dynamic>;
            return UpscaleRecord(
              id: map['id'] as String,
              originalPath: map['originalPath'] as String,
              outputPath: map['outputPath'] as String,
              width: map['width'] as int,
              height: map['height'] as int,
              bytes: map['bytes'] as int,
              modelId: map['modelId'] as String,
              scale: map['scale'] as int,
              elapsed: Duration(milliseconds: map['elapsedMs'] as int),
              createdAt: DateTime.parse(map['createdAt'] as String),
            );
          })
          .where((UpscaleRecord record) => File(record.outputPath).existsSync())
          .toList();
    } on Object {
      return <UpscaleRecord>[];
    }
  }

  Future<void> delete(UpscaleRecord record) async {
    final File result = File(record.outputPath);
    if (await result.exists()) {
      await result.delete();
    }
    final List<UpscaleRecord> records = await loadHistory();
    await _writeHistory(
      records.where((UpscaleRecord item) => item.id != record.id).toList(),
    );
  }

  Future<File> _historyFile() async {
    final Directory support = await getApplicationSupportDirectory();
    await support.create(recursive: true);
    return File('${support.path}${Platform.pathSeparator}$_historyName');
  }

  Future<void> _writeHistory(List<UpscaleRecord> records) async {
    final File file = await _historyFile();
    final List<Map<String, Object>> data = records
        .map(
          (UpscaleRecord record) => <String, Object>{
            'id': record.id,
            'originalPath': record.originalPath,
            'outputPath': record.outputPath,
            'width': record.width,
            'height': record.height,
            'bytes': record.bytes,
            'modelId': record.modelId,
            'scale': record.scale,
            'elapsedMs': record.elapsed.inMilliseconds,
            'createdAt': record.createdAt.toIso8601String(),
          },
        )
        .toList();
    await file.writeAsString(jsonEncode(data), flush: true);
  }
}
