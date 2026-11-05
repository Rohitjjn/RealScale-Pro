import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/services/model_catalog/model_catalog.dart';

/// Supply-chain check: every bundled starter model's asset files exist and
/// their real SHA-256 matches the catalog (rules.md: verify before shipping).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'bundled catalog parses and starter assets match their checksums',
    () async {
      final raw = await File('assets/data/model_catalog.json').readAsString();
      final catalog = ModelCatalog.parse(raw);

      // Starter pack: at least the compact pair + the two bundled fallbacks.
      final starter = catalog.byPack('starter').where((m) => m.available);
      expect(
        starter.map((m) => m.id),
        containsAll(<String>[
          'realesr-animevideov3',
          'realcugan-se-2x-denoise1x',
          'waifu2x-upconv-photo-2x',
          'waifu2x-upconv-anime-2x',
        ]),
      );

      var checked = 0;
      for (final model in starter) {
        for (final file in model.files) {
          final ap = file.assetPath;
          if (ap == null) continue;
          final f = File(ap);
          expect(f.existsSync(), isTrue, reason: '$ap missing from bundle');
          expect(f.lengthSync(), file.sizeBytes, reason: '$ap size drift');
          final bytes = await f.readAsBytes();
          expect(
            crypto.sha256.convert(bytes).toString(),
            file.sha256,
            reason: '$ap checksum drift — re-verify the source before shipping',
          );
          checked++;
        }
      }
      expect(checked, greaterThanOrEqualTo(8)); // 4 models × param+bin

      // The starter manifest documents the same provenance.
      final manifest =
          jsonDecode(
                await File('assets/data/starter_manifest.json').readAsString(),
              )
              as Map<String, Object?>;
      expect(manifest['license'], contains('MIT'));
      expect((manifest['files'] as List), isNotEmpty);
    },
  );
}
