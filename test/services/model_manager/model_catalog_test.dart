import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/services/model_manager/model_catalog.dart';

void main() {
  late String source;

  setUpAll(() async {
    source = await File('assets/data/model_catalog.json').readAsString();
  });

  test('project catalog parses every declared model', () {
    final ModelCatalog catalog = ModelCatalog.parse(source);

    expect(catalog.version, 1);
    expect(catalog.models, hasLength(17));
    expect(
      catalog.models.map((ModelDefinition model) => model.id),
      containsAll(<String>[
        'waifu2x-photo-fallback',
        'waifu2x-anime-fallback',
        'realesrgan-general-x4v3',
        'realcugan-pro',
        'realsr-df2k',
        'gfpgan-v1-4',
        'codeformer',
      ]),
    );
  });

  test('only models with exact files are enabled', () {
    final ModelCatalog catalog = ModelCatalog.parse(source);
    final List<ModelDefinition> enabled = catalog.models
        .where((ModelDefinition model) => model.isEnabled)
        .toList();

    expect(enabled.map((ModelDefinition model) => model.id), <String>[
      'waifu2x-photo-fallback',
      'waifu2x-anime-fallback',
      'realesrgan-animevideov3',
      'realesrgan-x2plus',
    ]);
    expect(
      enabled.every((ModelDefinition model) => model.files.length == 2),
      isTrue,
    );
  });

  test('face catalog keeps restricted models unavailable', () {
    final ModelCatalog catalog = ModelCatalog.parse(source);
    final List<ModelDefinition> faceModels = catalog.forPurpose(
      ModelPurpose.face,
    );

    expect(faceModels, hasLength(2));
    expect(
      faceModels.every((ModelDefinition model) => model.restricted),
      isTrue,
    );
    expect(
      faceModels.every((ModelDefinition model) => !model.isEnabled),
      isTrue,
    );
    expect(
      catalog.byId('gfpgan-v1-4').restrictionNote,
      contains('Owner-approved'),
    );
  });

  test('duplicate model ids are rejected', () {
    final Map<String, dynamic> json =
        jsonDecode(source) as Map<String, dynamic>;
    final List<dynamic> models = json['models'] as List<dynamic>;
    models.add(models.first);

    expect(
      () => ModelCatalog.parse(jsonEncode(json)),
      throwsA(isA<FormatException>()),
    );
  });

  test('enabled remote files require HTTPS and a valid checksum', () {
    final Map<String, dynamic> json =
        jsonDecode(source) as Map<String, dynamic>;
    final List<dynamic> models = json['models'] as List<dynamic>;
    final Map<String, dynamic> first = models.first as Map<String, dynamic>;
    first['availability'] = 'onDemand';
    final List<dynamic> files = first['files'] as List<dynamic>;
    final Map<String, dynamic> file = files.first as Map<String, dynamic>;
    file.remove('assetPath');
    file['url'] = 'http://example.com/model.bin';
    file['sha256'] = 'not-a-checksum';

    expect(
      () => ModelCatalog.parse(jsonEncode(json)),
      throwsA(isA<FormatException>()),
    );
  });

  test('restricted models cannot accidentally become enabled', () {
    final Map<String, dynamic> json =
        jsonDecode(source) as Map<String, dynamic>;
    final List<dynamic> models = json['models'] as List<dynamic>;
    final Map<String, dynamic> gfpgan =
        models.firstWhere(
              (dynamic model) =>
                  (model as Map<String, dynamic>)['id'] == 'gfpgan-v1-4',
            )
            as Map<String, dynamic>;
    gfpgan['availability'] = 'onDemand';

    expect(
      () => ModelCatalog.parse(jsonEncode(json)),
      throwsA(isA<FormatException>()),
    );
  });
}
