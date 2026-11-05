import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/app_exception.dart';
import '../../engine/upscale_engine.dart' show DeviceTier;

/// One downloadable/checksummed file belonging to a model.
class ModelFileSpec {
  const ModelFileSpec({
    required this.kind, // 'param' | 'bin'
    required this.sha256,
    required this.sizeBytes,
    this.scale,
    this.url,
    this.sourceZipEntry,
    this.assetPath,
  });

  final String kind;
  final int? scale; // params are per native scale
  final String? url; // HTTPS download (zip when sourceZipEntry is set)
  final String? sourceZipEntry; // entry extracted from the downloaded zip
  final String? assetPath; // bundled fallback (Starter pack)
  final String sha256;
  final int sizeBytes;

  /// 64 lowercase hex digits. Validated by pattern, not int parsing: a
  /// SHA-256 value is a 256-bit number and never fits Dart's 64-bit int.
  static final RegExp _sha256Pattern = RegExp(r'^[0-9a-f]{64}$');

  static ModelFileSpec fromMap(Map<String, Object?> m) {
    final sha = (m['sha256'] as String?)?.toLowerCase();
    final kind = m['kind'] as String?;
    final size = (m['sizeBytes'] as num?)?.toInt();
    if (sha == null || !_sha256Pattern.hasMatch(sha)) {
      throw const CatalogException('Invalid sha256 in model file entry');
    }
    if (kind != 'param' && kind != 'bin') {
      throw const CatalogException("File kind must be 'param' or 'bin'");
    }
    if (size == null || size < 0) {
      throw const CatalogException('Invalid sizeBytes in model file entry');
    }
    final kindResolved = kind ?? '';
    final url = m['url'] as String?;
    if (url != null && !url.startsWith('https://')) {
      throw const CatalogException('Model URLs must be HTTPS');
    }
    return ModelFileSpec(
      kind: kindResolved,
      scale: (m['scale'] as num?)?.toInt(),
      url: url,
      sourceZipEntry: m['sourceZipEntry'] as String?,
      assetPath: m['assetPath'] as String?,
      sha256: sha.toLowerCase(),
      sizeBytes: size,
    );
  }
}

/// Why a catalog row is unavailable (shown as clearly unavailable, task 2.5).
enum UnavailableReason {
  none,
  weightsChecksumPending,
  licenseBlocked,
  comingSoon,
}

/// One row of the model catalog — feeds Manual, Model Manager and the planner.
class ModelDef {
  const ModelDef({
    required this.id,
    required this.version,
    required this.name,
    required this.engine,
    required this.pack,
    required this.minTier,
    required this.nativeScales,
    required this.modes,
    required this.bestFor,
    required this.speed,
    required this.sizeBytes,
    required this.license,
    required this.verified,
    required this.heavy,
    required this.files,
    this.unavailableReason = UnavailableReason.none,
    this.licenseUrl,
    this.sourceUrl,
  });

  final String id;
  final String version;
  final String name;
  final String engine; // ncnn | onnx
  final String pack; // starter | pro | face | ultra | community
  final DeviceTier minTier;
  final List<int> nativeScales;
  final List<String> modes;
  final List<String> bestFor; // photo | anime | faces
  final String speed;
  final int sizeBytes;
  final String license;
  final bool verified;
  final bool heavy; // confirmation required on low tiers
  final List<ModelFileSpec> files;
  final UnavailableReason unavailableReason;
  final String? licenseUrl;
  final String? sourceUrl;

  bool get available =>
      verified &&
      unavailableReason == UnavailableReason.none &&
      files.isNotEmpty;

  bool get isFaceModel => bestFor.contains('faces');

  bool supportsMode(String mode) => modes.contains(mode);

  bool supportsScale(int scale) => nativeScales.contains(scale);

  static ModelDef fromMap(Map<String, Object?> m) {
    final id = m['id'] as String?;
    if (id == null || id.isEmpty) {
      throw const CatalogException('Model row missing id');
    }
    final verified = m['verified'] as bool? ?? false;
    final nativeScales = ((m['nativeScales'] as List?) ?? const [])
        .map((e) => (e as num).toInt())
        .toList();
    if (verified && nativeScales.isEmpty) {
      throw CatalogException('Verified model $id has no native scales');
    }
    final files = ((m['files'] as List?) ?? const [])
        .whereType<Map<String, Object?>>()
        .map(ModelFileSpec.fromMap)
        .toList();
    return ModelDef(
      id: id,
      version: m['version'] as String? ?? '0.0.0',
      name: m['name'] as String? ?? id,
      engine: m['engine'] as String? ?? 'ncnn',
      pack: m['pack'] as String? ?? 'pro',
      minTier: DeviceTier.fromName(m['minTier'] as String?),
      nativeScales: nativeScales,
      modes: ((m['modes'] as List?) ?? const ['upscale'])
          .map((e) => e as String)
          .toList(),
      bestFor: ((m['bestFor'] as List?) ?? const [])
          .map((e) => e as String)
          .toList(),
      speed: m['speed'] as String? ?? 'medium',
      sizeBytes: (m['sizeBytes'] as num?)?.toInt() ?? 0,
      license: m['license'] as String? ?? 'Unknown',
      verified: verified,
      heavy: m['heavy'] as bool? ?? false,
      unavailableReason: switch (m['unavailableReason'] as String?) {
        'weightsChecksumPending' => UnavailableReason.weightsChecksumPending,
        'licenseBlocked' => UnavailableReason.licenseBlocked,
        'comingSoon' => UnavailableReason.comingSoon,
        _ => UnavailableReason.none,
      },
      files: files,
      licenseUrl: m['licenseUrl'] as String?,
      sourceUrl: m['sourceUrl'] as String?,
    );
  }
}

/// The full catalog: bundled JSON, optionally refreshed from a remote manifest.
class ModelCatalog {
  const ModelCatalog({required this.version, required this.models});

  final int version;
  final List<ModelDef> models;

  ModelDef? byId(String id) {
    for (final m in models) {
      if (m.id == id) return m;
    }
    return null;
  }

  List<ModelDef> byPack(String pack) =>
      models.where((m) => m.pack == pack).toList();

  /// Models usable for the given image type (excluding face models).
  List<ModelDef> forImageType(String type) => models
      .where((m) => m.available && !m.isFaceModel && m.bestFor.contains(type))
      .toList();

  static ModelCatalog fromMap(Map<String, Object?> m) {
    final rows = ((m['models'] as List?) ?? const [])
        .whereType<Map<String, Object?>>()
        .map(ModelDef.fromMap)
        .toList();
    final ids = rows.map((r) => r.id).toSet();
    if (ids.length != rows.length) {
      throw const CatalogException('Duplicate model ids in catalog');
    }
    return ModelCatalog(
      version: (m['version'] as num?)?.toInt() ?? 1,
      models: rows,
    );
  }

  /// Parses the bundled catalog; malformed entries fail safely (task 2.2).
  static Future<ModelCatalog> loadBundled() async {
    final raw = await rootBundle.loadString('assets/data/model_catalog.json');
    return parse(raw);
  }

  static ModelCatalog parse(String json) {
    final Object? decoded;
    try {
      decoded = jsonDecode(json) as Object?;
    } on FormatException catch (e) {
      throw CatalogException('Catalog JSON malformed: ${e.message}');
    }
    if (decoded is! Map<String, Object?>) {
      throw const CatalogException('Catalog root must be an object');
    }
    return fromMap(decoded);
  }
}

base class CatalogException extends AppException {
  const CatalogException(super.message);
}
