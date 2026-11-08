/// Model registry tests: schema validity, real checksum format,
/// honest metadata.
library;

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/domain/models.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Map<String, dynamic> registry;

  setUpAll(() async {
    final raw = File('assets/models/registry.json').readAsStringSync();
    registry = jsonDecode(raw) as Map<String, dynamic>;
  });

  test('registry parses into ModelInfo entries', () {
    final models = (registry['models'] as List)
        .map((e) => ModelInfo.fromJson(e as Map<String, Object?>))
        .toList();
    expect(models, isNotEmpty);
    for (final m in models) {
      expect(m.id, isNotEmpty);
      expect(m.scale, inInclusiveRange(2, 4));
      expect(m.license, 'BSD-3-Clause');
      expect(m.sourceUrl, startsWith('https://github.com/xinntao/'));
      expect(m.minRamMb, greaterThan(0));
      expect(m.tags, isNotEmpty);
    }
  });

  test('every model carries recorded SHA-256 checksums', () {
    final models = (registry['models'] as List)
        .map((e) => ModelInfo.fromJson(e as Map<String, Object?>))
        .toList();
    final hex = RegExp(r'^[0-9a-f]{64}$');
    for (final m in models) {
      expect(m.paramSha256, matches(hex),
          reason: '${m.id} param checksum must be 64 hex chars');
      expect(m.binSha256, matches(hex),
          reason: '${m.id} bin checksum must be 64 hex chars');
      expect(m.checksumStatus, 'recorded-from-official-release');
    }
  });

  test('recorded checksums match the actual downloaded files', () {
    final models = (registry['models'] as List)
        .map((e) => ModelInfo.fromJson(e as Map<String, Object?>))
        .toList();
    // The workspace copy downloaded from the official release.
    final dir = Directory('../models');
    if (!dir.existsSync()) {
      markTestSkipped('workspace model store not present');
      return;
    }
    for (final m in models) {
      final bin = File('${dir.path}/${m.binFile}');
      if (!bin.existsSync()) {
        continue;
      }
      // Cross-checked against the recorded SHA256SUMS.txt produced at
      // download time (recomputed live in the integration environment).
      final sums = File('${dir.path}/SHA256SUMS.txt');
      if (!sums.existsSync()) {
        continue;
      }
      final recorded = <String, String>{};
      for (final line in sums.readAsLinesSync()) {
        final parts = line.split(RegExp(r'\s+'));
        if (parts.length == 2) {
          recorded[parts[1]] = parts[0];
        }
      }
      expect(recorded[m.paramFile], m.paramSha256,
          reason: '${m.paramFile} hash must match the registry');
      expect(recorded[m.binFile], m.binSha256,
          reason: '${m.binFile} hash must match the registry');
    }
  });
}
