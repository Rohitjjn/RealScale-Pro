import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/app.dart' show Haptics;
import '../../../l10n/app_localizations.dart';
import '../../../services/feasibility/feasibility_checker.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/cards.dart' show TabularFigures;
import '../../../ui/widgets/common.dart' show SectionHeader;
import 'crop_picker_screen.dart';

/// Super Resolution Mode sections (Phase 3): the lossless-mode explainer,
/// the green/amber/red feasibility banner and the preview-crop card.
class SrSection extends ConsumerWidget {
  const SrSection({super.key, required this.feasibility});

  final FeasibilityResult? feasibility;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Lossless promise.
        Container(
          padding: const EdgeInsets.all(RsSpacing.l),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                cs.primary.withValues(alpha: 0.10),
                cs.tertiary.withValues(alpha: 0.08),
              ],
            ),
            borderRadius: RsRadius.cardBorder,
          ),
          child: Row(
            children: [
              Icon(Icons.verified_rounded, color: cs.primary, size: 22),
              const SizedBox(width: RsSpacing.m),
              Expanded(child: Text(l.configSrLosslessInfo, style: RsType.body)),
            ],
          ),
        ),
        if (feasibility != null) ...[
          const SizedBox(height: RsSpacing.m),
          _FeasibilityBanner(result: feasibility!),
        ],
        const SizedBox(height: RsSpacing.xl),
        SectionHeader(label: l.configPreviewSection),
        Text(
          l.configPreviewHint,
          style: RsType.body.copyWith(color: cs.onSurfaceVariant),
        ),
        const SizedBox(height: RsSpacing.m),
        const _PreviewCard(),
      ],
    );
  }
}

class _FeasibilityBanner extends StatelessWidget {
  const _FeasibilityBanner({required this.result});

  final FeasibilityResult result;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;

    final (color, icon, label) = switch (result.level) {
      FeasibilityLevel.green => (
        dark ? RsColors.successDark : RsColors.successLight,
        Icons.check_circle_rounded,
        l.configSrFeasibilityGreen,
      ),
      FeasibilityLevel.amber => (
        dark ? RsColors.warningDark : RsColors.warningLight,
        Icons.warning_amber_rounded,
        l.configSrFeasibilityAmber,
      ),
      FeasibilityLevel.red => (
        dark ? RsColors.errorDark : RsColors.errorLight,
        Icons.error_rounded,
        l.configSrFeasibilityRed,
      ),
    };

    final lines = <String>[];
    for (final r in result.reasons) {
      switch (r) {
        case FeasibilityReason.outputOverCap:
        case FeasibilityReason.memoryExhausted:
        case FeasibilityReason.memoryTight:
          lines.add(
            l.configSrFeasibilityMemory(
              result.estimatedPeakMb,
              result.availableMb,
            ),
          );
        case FeasibilityReason.verySlow:
          lines.add(l.configSrFeasibilitySlow);
        case FeasibilityReason.largeSource:
          lines.add(l.configSrFeasibilityLargeSource);
        case FeasibilityReason.none:
          break;
      }
    }
    // Deduplicate while keeping order.
    final uniqueLines = lines.toSet().toList();

    return Container(
      padding: const EdgeInsets.all(RsSpacing.l),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        borderRadius: RsRadius.cardBorder,
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: color, size: 20),
              const SizedBox(width: RsSpacing.s),
              Expanded(
                child: Text(
                  '${l.configSrFeasibilityTitle} · $label',
                  style: RsType.bodyMedium.copyWith(
                    color: color,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            l.configSrFeasibilityOutput(result.outputMegapixels),
            style: RsType.label.copyWith(
              color: cs.onSurfaceVariant,
              fontFeatures: const [TabularFigures()],
            ),
          ),
          for (final line in uniqueLines) ...[
            const SizedBox(height: 2),
            Text(
              line,
              style: RsType.label.copyWith(
                color: cs.onSurfaceVariant,
                fontFeatures: const [TabularFigures()],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _PreviewCard extends ConsumerWidget {
  const _PreviewCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final cfg = ref.watch(configProvider);
    final cs = Theme.of(context).colorScheme;
    final hasCrop = cfg.previewCrop != null;

    return Container(
      padding: const EdgeInsets.all(RsSpacing.l),
      decoration: BoxDecoration(
        color: cs.surface,
        borderRadius: RsRadius.cardBorder,
        border: Border.all(color: cs.outline),
      ),
      child: Row(
        children: [
          Icon(
            hasCrop ? Icons.crop_free_rounded : Icons.center_focus_weak_rounded,
            color: cs.primary,
            size: 22,
          ),
          const SizedBox(width: RsSpacing.m),
          Expanded(
            child: Text(
              hasCrop
                  ? '${cfg.previewCrop!.$3} × ${cfg.previewCrop!.$4} px'
                  : l.configPreviewPick,
              style: RsType.bodyMedium.copyWith(
                fontFeatures: hasCrop ? const [TabularFigures()] : null,
              ),
            ),
          ),
          if (hasCrop)
            TextButton(
              onPressed: () =>
                  ref.read(configProvider.notifier).setPreviewCrop(null),
              child: Text(l.configPreviewClear),
            ),
          TextButton(
            onPressed: () async {
              Haptics.selection(ref);
              final bytes = cfg.effectiveBytes ?? cfg.originalBytes;
              final dims = configDimsProviderKey(cfg);
              if (bytes == null || dims == null) return;
              final rect = await CropPickerScreen.open(
                context,
                bytes: bytes,
                imageWidth: dims.$1,
                imageHeight: dims.$2,
                initial: cfg.previewCrop,
              );
              if (rect != null) {
                ref.read(configProvider.notifier).setPreviewCrop(rect);
              }
            },
            child: Text(hasCrop ? l.configPreviewChange : l.configPreviewPick),
          ),
        ],
      ),
    );
  }
}

/// Convenience: cached dims for the current config photo.
(int, int)? configDimsProviderKey(ConfigState cfg) {
  final bytes = cfg.effectiveBytes ?? cfg.originalBytes;
  if (bytes == null) return null;
  return dimsCache[cfg.path];
}

/// Bottom sheet with the finished preview: before/after compare + info +
/// "Upscale full image" call to action.
class PreviewResultSheet extends ConsumerWidget {
  const PreviewResultSheet({super.key, required this.onUpscaleFull});

  final VoidCallback onUpscaleFull;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final preview = ref.watch(previewProvider);
    final cs = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        RsSpacing.xl,
        0,
        RsSpacing.xl,
        RsSpacing.xl,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l.configPreviewTitle, style: RsType.headline),
          const SizedBox(height: RsSpacing.l),
          if (preview.running) ...[
            const SizedBox(height: RsSpacing.xl),
            Center(
              child: Column(
                children: [
                  const CircularProgressIndicator(),
                  const SizedBox(height: RsSpacing.l),
                  Text(
                    l.configPreviewRunning,
                    style: RsType.body.copyWith(color: cs.onSurfaceVariant),
                  ),
                ],
              ),
            ),
            const SizedBox(height: RsSpacing.xl),
          ] else if (preview.error != null) ...[
            const SizedBox(height: RsSpacing.m),
            Text(preview.error!, style: RsType.body.copyWith(color: cs.error)),
            const SizedBox(height: RsSpacing.xl),
          ] else if (preview.done &&
              preview.resultPath != null &&
              preview.cropBytes != null) ...[
            BeforeAfterPreview(
              resultPath: preview.resultPath!,
              beforeBytes: preview.cropBytes!,
            ),
            const SizedBox(height: RsSpacing.m),
            Row(
              children: [
                Icon(Icons.bolt_rounded, size: 16, color: cs.primary),
                const SizedBox(width: 4),
                Text(
                  '${preview.resultWidth} × ${preview.resultHeight}'
                  '  ·  ${l.configPreviewTime((preview.elapsedMs ?? 0) ~/ 1000)}',
                  style: RsType.label.copyWith(
                    color: cs.onSurfaceVariant,
                    fontFeatures: const [TabularFigures()],
                  ),
                ),
              ],
            ),
            const SizedBox(height: RsSpacing.xl),
            SizedBox(
              width: double.infinity,
              height: 52,
              child: FilledButton.icon(
                onPressed: onUpscaleFull,
                icon: const Icon(Icons.auto_awesome_rounded),
                label: Text(l.configPreviewFull),
              ),
            ),
          ] else ...[
            const SizedBox(height: RsSpacing.xl),
            Center(child: Text(l.configPreviewRunning)),
            const SizedBox(height: RsSpacing.xl),
          ],
        ],
      ),
    );
  }
}

/// Before/after compare for the preview: original crop vs result, both fit
/// the same box (the result is scale-times larger in pixels).
class BeforeAfterPreview extends StatelessWidget {
  const BeforeAfterPreview({
    super.key,
    required this.resultPath,
    required this.beforeBytes,
    this.height = 300,
  });

  final String resultPath;
  final Uint8List beforeBytes;
  final double height;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return ClipRRect(
      borderRadius: RsRadius.cardBorder,
      child: SizedBox(
        height: height,
        width: double.infinity,
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.memory(beforeBytes, fit: BoxFit.cover, gaplessPlayback: true),
            Image.file(File(resultPath), fit: BoxFit.cover),
            Positioned(
              bottom: 10,
              left: 10,
              child: _pill(l.configPreviewBefore),
            ),
            Positioned(
              bottom: 10,
              right: 10,
              child: _pill(l.configPreviewAfter),
            ),
          ],
        ),
      ),
    );
  }

  Widget _pill(String label) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(
      color: Colors.black.withValues(alpha: 0.55),
      borderRadius: RsRadius.pill,
    ),
    child: Text(
      label,
      style: RsType.label.copyWith(color: Colors.white, fontSize: 11),
    ),
  );
}
