import 'package:flutter_test/flutter_test.dart';

import 'package:realscale_pro/engine/upscale_engine.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';

const _catalogJson = '''
{
  "version": 2,
  "models": [
    {
      "id": "realesr-animevideov3",
      "version": "1.0.0",
      "name": "Real-ESRGAN anime-video compact",
      "engine": "ncnn",
      "pack": "starter",
      "minTier": "T0",
      "nativeScales": [2, 3, 4],
      "modes": ["upscale"],
      "bestFor": ["anime"],
      "speed": "veryFast",
      "sizeBytes": 1258334,
      "license": "BSD-3-Clause",
      "verified": true,
      "heavy": false,
      "files": [
        {"kind": "bin", "url": "https://example.com/m.zip", "sourceZipEntry": "x.bin", "sha256": "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "sizeBytes": 1247368},
        {"kind": "param", "scale": 4, "assetPath": "assets/x.param", "sha256": "850a248e7c14c27e5bd8cf7265113a9441036a7db63963bb8aa5169d788a435e", "sizeBytes": 3077}
      ]
    },
    {
      "id": "realesrgan-x4plus",
      "version": "1.0.0",
      "name": "Real-ESRGAN x4plus",
      "engine": "ncnn",
      "pack": "pro",
      "minTier": "T1",
      "nativeScales": [4],
      "modes": ["upscale", "superResolution"],
      "bestFor": ["photo"],
      "speed": "medium",
      "sizeBytes": 33540549,
      "license": "BSD-3-Clause",
      "verified": true,
      "heavy": true,
      "files": [
        {"kind": "bin", "url": "https://example.com/p.zip", "sourceZipEntry": "x4plus.bin", "sha256": "713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf", "sizeBytes": 33424520}
      ]
    },
    {
      "id": "codeformer",
      "version": "0.0.0",
      "name": "CodeFormer",
      "engine": "ncnn",
      "pack": "face",
      "minTier": "T2",
      "nativeScales": [1],
      "modes": ["upscale"],
      "bestFor": ["faces"],
      "speed": "medium",
      "sizeBytes": 0,
      "license": "NON-COMMERCIAL",
      "verified": false,
      "heavy": false,
      "unavailableReason": "licenseBlocked",
      "files": []
    }
  ]
}
''';

void main() {
  test('parses every catalog row and exposes typed accessors (task 2.2)', () {
    final catalog = ModelCatalog.parse(_catalogJson);
    expect(catalog.version, 2);
    expect(catalog.models.length, 3);

    final anime = catalog.byId('realesr-animevideov3')!;
    expect(anime.available, isTrue);
    expect(anime.nativeScales, [2, 3, 4]);
    expect(anime.supportsScale(3), isTrue);
    expect(anime.supportsScale(5), isFalse);
    expect(anime.isFaceModel, isFalse);
    expect(anime.minTier, DeviceTier.t0);

    final x4 = catalog.byId('realesrgan-x4plus')!;
    expect(x4.heavy, isTrue);
    expect(x4.supportsMode('superResolution'), isTrue);

    final blocked = catalog.byId('codeformer')!;
    expect(blocked.available, isFalse);
    expect(blocked.unavailableReason, UnavailableReason.licenseBlocked);

    expect(catalog.forImageType('photo').map((m) => m.id), [
      'realesrgan-x4plus',
    ]);
    expect(catalog.byPack('starter'), hasLength(1));
  });

  test('rejects invalid URLs (HTTP is forbidden — HTTPS only)', () {
    const bad = '''
    {"models": [{"id": "m", "verified": true, "nativeScales": [4], "modes": ["upscale"],
      "bestFor": ["photo"], "files": [{"kind": "bin", "url": "http://insecure.example/m.bin",
      "sha256": "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "sizeBytes": 1}]}]}
    ''';
    expect(() => ModelCatalog.parse(bad), throwsA(isA<CatalogException>()));
  });

  test('rejects malformed checksums', () {
    const bad = '''
    {"models": [{"id": "m", "verified": true, "nativeScales": [4], "modes": ["upscale"],
      "bestFor": ["photo"], "files": [{"kind": "bin", "sha256": "deadbeef", "sizeBytes": 1}]}]}
    ''';
    expect(() => ModelCatalog.parse(bad), throwsA(isA<CatalogException>()));
  });

  test('rejects bad file kinds and negative sizes', () {
    const badKind = '''
    {"models": [{"id": "m", "verified": true, "nativeScales": [4], "modes": ["upscale"],
      "bestFor": ["photo"], "files": [{"kind": "weights", "sha256": "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "sizeBytes": 1}]}]}
    ''';
    const badSize = '''
    {"models": [{"id": "m", "verified": true, "nativeScales": [4], "modes": ["upscale"],
      "bestFor": ["photo"], "files": [{"kind": "bin", "sha256": "548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d", "sizeBytes": -5}]}]}
    ''';
    expect(() => ModelCatalog.parse(badKind), throwsA(isA<CatalogException>()));
    expect(() => ModelCatalog.parse(badSize), throwsA(isA<CatalogException>()));
  });

  test('rejects duplicate ids and malformed JSON', () {
    const dup = '''
    {"models": [{"id": "m", "verified": false, "files": []}, {"id": "m", "verified": false, "files": []}]}
    ''';
    expect(() => ModelCatalog.parse(dup), throwsA(isA<CatalogException>()));
    expect(
      () => ModelCatalog.parse('not json'),
      throwsA(isA<CatalogException>()),
    );
  });
}
