import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/app.dart' show Haptics;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/common.dart' show SectionHeader;
import '../../../ui/widgets/cards.dart' show TabularFigures;

/// Advanced level pipeline controls (Phase 3): tile size, overlap, threads,
/// compute device, output format, quality and the output-size cap. Every
/// control is an override — Auto leaves the decision to the planner data.
class AdvancedSection extends ConsumerWidget {
  const AdvancedSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final cfg = ref.watch(configProvider);
    final adv = cfg.advanced;

    void update({
      int? tileSize,
      String? overlap,
      int? threads,
      String? compute,
      String? format,
      int? quality,
      int? outputCapMp,
    }) {
      Haptics.selection(ref);
      ref
          .read(configProvider.notifier)
          .setAdvanced(
            ref
                .read(configProvider)
                .advanced
                .copyWith(
                  tileSize: tileSize,
                  overlap: overlap,
                  threads: threads,
                  compute: compute,
                  format: format,
                  quality: quality,
                  outputCapMp: outputCapMp,
                ),
          );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SectionHeader(label: l.configAdvancedSection),
        _OptionChipsRow(
          label: l.configAdvancedTile,
          hint: l.configAdvancedTileHint,
          labels: [l.configAuto, '128', '192', '256', '384', '512'],
          values: const ['0', '128', '192', '256', '384', '512'],
          selected: '${adv.tileSize}',
          onChanged: (v) => update(tileSize: int.parse(v)),
        ),
        _OptionChipsRow(
          label: l.configAdvancedOverlap,
          hint: l.configAdvancedOverlapHint,
          labels: [l.configAuto, '0', '8', '16', '32'],
          values: const ['auto', 'none', 'small', 'medium', 'large'],
          selected: adv.overlap,
          onChanged: (v) => update(overlap: v),
        ),
        _OptionChipsRow(
          label: l.configAdvancedThreads,
          labels: [l.configAuto, '2', '4', '6', '8'],
          values: const ['0', '2', '4', '6', '8'],
          selected: '${adv.threads}',
          onChanged: (v) => update(threads: int.parse(v)),
        ),
        _OptionChipsRow(
          label: l.configAdvancedCompute,
          labels: [
            l.configAuto,
            l.configAdvancedComputeGpu,
            l.configAdvancedComputeCpu,
          ],
          values: const ['auto', 'gpu', 'cpu'],
          selected: adv.compute,
          onChanged: (v) => update(compute: v),
        ),
        _OptionChipsRow(
          label: l.configAdvancedFormat,
          labels: [l.configFormatSameAsSource, 'JPEG', 'PNG'],
          values: const ['sameAsSource', 'jpeg', 'png'],
          selected: adv.format,
          onChanged: (v) => update(format: v),
        ),
        _OptionChipsRow(
          label: l.configAdvancedCap,
          hint: l.configAdvancedCapHint,
          labels: [
            l.configAuto,
            '12 ${l.configMpUnit}',
            '24 ${l.configMpUnit}',
            '48 ${l.configMpUnit}',
            '96 ${l.configMpUnit}',
          ],
          values: const ['0', '12', '24', '48', '96'],
          selected: '${adv.outputCapMp}',
          onChanged: (v) => update(outputCapMp: int.parse(v)),
        ),
        // JPEG quality — only relevant when the output is JPEG.
        if (adv.format == 'jpeg')
          Padding(
            padding: const EdgeInsets.only(top: RsSpacing.s),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        l.configAdvancedQuality,
                        style: RsType.bodyMedium,
                      ),
                    ),
                    Text(
                      '${adv.quality}',
                      style: RsType.bodyMedium.copyWith(
                        fontFeatures: const [TabularFigures()],
                        color: Theme.of(context).colorScheme.primary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
                Slider(
                  value: adv.quality.toDouble(),
                  min: 70,
                  max: 100,
                  divisions: 6,
                  label: '${adv.quality}',
                  onChanged: (v) {
                    // Slider drags persist without haptic spam.
                    ref
                        .read(configProvider.notifier)
                        .setAdvanced(
                          ref
                              .read(configProvider)
                              .advanced
                              .copyWith(quality: v.round()),
                        );
                  },
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// One labeled row of pill chips (Auto first where applicable).
class _OptionChipsRow extends StatelessWidget {
  const _OptionChipsRow({
    required this.label,
    required this.labels,
    required this.values,
    required this.selected,
    required this.onChanged,
    this.hint,
  });

  final String label;
  final String? hint;
  final List<String> labels;
  final List<String> values;
  final String selected;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: RsSpacing.l),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: RsType.bodyMedium),
          if (hint != null) ...[
            const SizedBox(height: 2),
            Text(
              hint!,
              style: RsType.label.copyWith(color: cs.onSurfaceVariant),
            ),
          ],
          const SizedBox(height: RsSpacing.s),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            clipBehavior: Clip.none,
            child: Row(
              children: [
                for (var i = 0; i < values.length; i++)
                  Padding(
                    padding: EdgeInsets.only(
                      right: i == values.length - 1 ? 0 : RsSpacing.s,
                    ),
                    child: _Chip(
                      label: labels[i],
                      selected: selected == values[i],
                      onTap: () => onChanged(values[i]),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: RsMotion.micro,
        curve: RsMotion.spring,
        height: 38,
        padding: const EdgeInsets.symmetric(horizontal: RsSpacing.l),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          gradient: selected ? RsColors.brandGradient(dark: dark) : null,
          color: selected ? null : cs.surface,
          borderRadius: RsRadius.pill,
          border: Border.all(color: selected ? Colors.transparent : cs.outline),
        ),
        child: Text(
          label,
          style: RsType.label.copyWith(
            color: selected ? Colors.white : cs.onSurface,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
          ),
        ),
      ),
    );
  }
}
