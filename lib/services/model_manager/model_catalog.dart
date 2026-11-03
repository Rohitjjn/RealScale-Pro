import 'dart:convert';

/// Runtime used to execute a model.
enum ModelEngine { ncnn, onnx }

/// Download grouping shown in Model Manager.
enum ModelPack { starter, pro, face, ultra, community }

/// Content a model is designed to process.
enum ModelPurpose { photo, anime, face, helper }

/// Product modes that can offer a model.
enum ModelMode { upscale, superResolution }

/// Honest catalog state before device-specific install state is applied.
enum ModelAvailability { bundled, onDemand, held, blocked, comingSoon }

/// Relative speed shown without promising a device-specific duration.
enum ModelSpeed { veryFast, fast, medium, slow, verySlow }

/// One exact file needed to install a model.
class ModelFileDefinition {
  const ModelFileDefinition({
    required this.role,
    required this.filename,
    required this.sizeBytes,
    required this.sha256,
    this.assetPath,
    this.url,
  });

  factory ModelFileDefinition.fromJson(Map<String, dynamic> json) {
    final ModelFileDefinition file = ModelFileDefinition(
      role: _requiredString(json, 'role'),
      filename: _requiredString(json, 'filename'),
      sizeBytes: _requiredInt(json, 'sizeBytes'),
      sha256: _requiredString(json, 'sha256'),
      assetPath: _optionalString(json, 'assetPath'),
      url: _optionalString(json, 'url'),
    );
    file.validate();
    return file;
  }

  final String role;
  final String filename;
  final int sizeBytes;
  final String sha256;
  final String? assetPath;
  final String? url;

  void validate() {
    if (sizeBytes <= 0) {
      throw FormatException('$filename has a non-positive size');
    }
    if (!RegExp(r'^[a-f0-9]{64}$').hasMatch(sha256)) {
      throw FormatException('$filename has an invalid SHA-256 checksum');
    }
    final bool hasAsset = assetPath?.isNotEmpty ?? false;
    final bool hasUrl = url?.isNotEmpty ?? false;
    if (hasAsset == hasUrl) {
      throw FormatException('$filename must have one source');
    }
    if (hasUrl && Uri.tryParse(url!)?.scheme != 'https') {
      throw FormatException('$filename must use HTTPS');
    }
  }
}

/// One audited entry shared by Manual settings, the planner and Model Manager.
class ModelDefinition {
  const ModelDefinition({
    required this.id,
    required this.version,
    required this.name,
    required this.engine,
    required this.pack,
    required this.minTier,
    required this.nativeScales,
    required this.purposes,
    required this.modes,
    required this.speed,
    required this.sizeBytes,
    required this.license,
    required this.sourceUrl,
    required this.availability,
    required this.files,
    required this.restricted,
    this.restrictionNote,
  });

  factory ModelDefinition.fromJson(Map<String, dynamic> json) {
    final ModelDefinition model = ModelDefinition(
      id: _requiredString(json, 'id'),
      version: _requiredString(json, 'version'),
      name: _requiredString(json, 'name'),
      engine: _enumValue(ModelEngine.values, json, 'engine'),
      pack: _enumValue(ModelPack.values, json, 'pack'),
      minTier: _requiredInt(json, 'minTier'),
      nativeScales: _intList(json, 'nativeScales'),
      purposes: _enumList(ModelPurpose.values, json, 'purposes'),
      modes: _enumList(ModelMode.values, json, 'modes'),
      speed: _enumValue(ModelSpeed.values, json, 'speed'),
      sizeBytes: _requiredInt(json, 'sizeBytes'),
      license: _requiredString(json, 'license'),
      sourceUrl: _requiredString(json, 'sourceUrl'),
      availability: _enumValue(ModelAvailability.values, json, 'availability'),
      files: _objectList(
        json,
        'files',
      ).map(ModelFileDefinition.fromJson).toList(growable: false),
      restricted: json['restricted'] as bool? ?? false,
      restrictionNote: _optionalString(json, 'restrictionNote'),
    );
    model.validate();
    return model;
  }

  final String id;
  final String version;
  final String name;
  final ModelEngine engine;
  final ModelPack pack;
  final int minTier;
  final List<int> nativeScales;
  final List<ModelPurpose> purposes;
  final List<ModelMode> modes;
  final ModelSpeed speed;
  final int sizeBytes;
  final String license;
  final String sourceUrl;
  final ModelAvailability availability;
  final List<ModelFileDefinition> files;
  final bool restricted;
  final String? restrictionNote;

  bool get isEnabled =>
      availability == ModelAvailability.bundled ||
      availability == ModelAvailability.onDemand;

  bool get isFaceModel => purposes.contains(ModelPurpose.face);

  void validate() {
    if (!RegExp(r'^[a-z0-9]+(?:-[a-z0-9]+)*$').hasMatch(id)) {
      throw FormatException('$id is not a stable model id');
    }
    if (!RegExp(r'^\d+\.\d+\.\d+$').hasMatch(version)) {
      throw FormatException('$id has an invalid version');
    }
    if (minTier < 0 || minTier > 3) {
      throw FormatException('$id has an invalid minimum tier');
    }
    if (purposes.isEmpty || modes.isEmpty || sizeBytes < 0) {
      throw FormatException('$id is missing catalog metadata');
    }
    if (nativeScales.any((int scale) => scale < 2 || scale > 5)) {
      throw FormatException('$id has an unsupported native scale');
    }
    if (!isFaceModel &&
        !purposes.contains(ModelPurpose.helper) &&
        nativeScales.isEmpty) {
      throw FormatException('$id needs at least one native scale');
    }
    final Uri? source = Uri.tryParse(sourceUrl);
    if (source?.scheme != 'https' || source?.host != 'github.com') {
      throw FormatException('$id must link to its GitHub source');
    }
    if (restricted && isEnabled) {
      throw FormatException('$id is restricted and cannot be enabled');
    }
    if (isEnabled && files.isEmpty) {
      throw FormatException('$id is enabled without exact files');
    }
    final int fileBytes = files.fold(
      0,
      (int total, ModelFileDefinition file) => total + file.sizeBytes,
    );
    if (files.isNotEmpty && fileBytes != sizeBytes) {
      throw FormatException('$id file sizes do not match its catalog size');
    }
    if (availability == ModelAvailability.bundled &&
        files.any((ModelFileDefinition file) => file.assetPath == null)) {
      throw FormatException('$id is bundled but has a remote file');
    }
    if (availability == ModelAvailability.onDemand &&
        files.any((ModelFileDefinition file) => file.url == null)) {
      throw FormatException('$id is downloadable but has a bundled file');
    }
  }
}

/// Validated snapshot of every model the product can describe.
class ModelCatalog {
  const ModelCatalog({required this.version, required this.models});

  factory ModelCatalog.parse(String source) {
    final Object? decoded = jsonDecode(source);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Model catalog must be a JSON object');
    }
    final int version = _requiredInt(decoded, 'version');
    if (version <= 0) {
      throw const FormatException('Model catalog version must be positive');
    }
    final List<ModelDefinition> models = _objectList(
      decoded,
      'models',
    ).map(ModelDefinition.fromJson).toList(growable: false);
    if (models.isEmpty) {
      throw const FormatException('Model catalog cannot be empty');
    }
    final Set<String> ids = <String>{};
    for (final ModelDefinition model in models) {
      if (!ids.add(model.id)) {
        throw FormatException('Duplicate model id: ${model.id}');
      }
    }
    return ModelCatalog(version: version, models: models);
  }

  final int version;
  final List<ModelDefinition> models;

  ModelDefinition byId(String id) => models.firstWhere(
    (ModelDefinition model) => model.id == id,
    orElse: () => throw StateError('Unknown model id: $id'),
  );

  List<ModelDefinition> forPurpose(ModelPurpose purpose) => models
      .where((ModelDefinition model) => model.purposes.contains(purpose))
      .toList(growable: false);
}

String _requiredString(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('$key must be a non-empty string');
  }
  return value;
}

String? _optionalString(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value == null) return null;
  if (value is! String || value.trim().isEmpty) {
    throw FormatException('$key must be a non-empty string');
  }
  return value;
}

int _requiredInt(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value is! int) throw FormatException('$key must be an integer');
  return value;
}

List<int> _intList(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value is! List<dynamic> || value.any((Object? item) => item is! int)) {
    throw FormatException('$key must be an integer list');
  }
  return value.cast<int>().toList(growable: false);
}

List<Map<String, dynamic>> _objectList(Map<String, dynamic> json, String key) {
  final Object? value = json[key];
  if (value is! List<dynamic> ||
      value.any((Object? item) => item is! Map<String, dynamic>)) {
    throw FormatException('$key must be an object list');
  }
  return value.cast<Map<String, dynamic>>().toList(growable: false);
}

T _enumValue<T extends Enum>(
  List<T> values,
  Map<String, dynamic> json,
  String key,
) {
  final String raw = _requiredString(json, key);
  return values.firstWhere(
    (T value) => value.name == raw,
    orElse: () => throw FormatException('$key has unknown value: $raw'),
  );
}

List<T> _enumList<T extends Enum>(
  List<T> values,
  Map<String, dynamic> json,
  String key,
) {
  final Object? raw = json[key];
  if (raw is! List<dynamic> || raw.any((Object? item) => item is! String)) {
    throw FormatException('$key must be a string list');
  }
  return raw
      .cast<String>()
      .map(
        (String item) => values.firstWhere(
          (T value) => value.name == item,
          orElse: () => throw FormatException('$key has unknown value: $item'),
        ),
      )
      .toList(growable: false);
}
