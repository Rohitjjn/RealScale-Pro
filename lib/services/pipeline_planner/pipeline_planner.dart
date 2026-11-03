import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/services.dart';

import '../../core/models.dart';

/// Pure, data-driven planning for the Default level.
class PipelinePlanner {
  Map<String, dynamic>? _rules;

  Future<void> load() async {
    final String source = await rootBundle.loadString('assets/data/planner_rules.json');
    _rules = jsonDecode(source) as Map<String, dynamic>;
  }

  Future<JobSpec> plan(ImageSelection image, DeviceProfile device) async {
    if (_rules == null) {
      await load();
    }
    final Map<String, dynamic> tiers = _rules!['tiers'] as Map<String, dynamic>;
    final String tierKey = 'T${device.tier.index}';
    final Map<String, dynamic> tier = tiers[tierKey] as Map<String, dynamic>;
    final int tile = tier['tile'] as int;
    final int tilesAcross = (image.width / tile).ceil();
    final int tilesDown = (image.height / tile).ceil();
    final int totalTiles = math.max(1, tilesAcross * tilesDown);
    final int estimatedSeconds = math.max(
      1,
      (totalTiles * math.max(30, device.benchmarkMs ~/ 30) / 1000).ceil(),
    );
    return JobSpec(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      input: image,
      mode: UpscaleMode.upscale,
      level: SettingLevel.defaultLevel,
      scale: _rules!['defaultScale'] as int,
      modelId: image.kind == ImageKind.anime
          ? _rules!['animeModel'] as String
          : _rules!['photoModel'] as String,
      tileSize: tile,
      overlap: tier['overlap'] as int,
      useGpu: device.vulkanSupported,
      estimatedSeconds: estimatedSeconds,
    );
  }
}
