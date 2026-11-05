import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';

import '../../../app/app.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/common.dart';
import '../../../ui/widgets/controls.dart' show BeforeAfterSlider;

/// Result screen (task 1.15): zoomable result, before/after slider,
/// info row, Share, Upscale again (settings kept), New image.
class ResultScreen extends ConsumerStatefulWidget {
  const ResultScreen({super.key});

  @override
  ConsumerState<ResultScreen> createState() => _ResultScreenState();
}

class _ResultScreenState extends ConsumerState<ResultScreen> {
  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final job = ref.watch(jobProvider);
    final cs = Theme.of(context).colorScheme;
    final result = job.result;

    if (result == null || job.message == null) {
      return Scaffold(
        appBar: AppBar(),
        body: ErrorState(onRetry: () => context.go(Routes.home)),
      );
    }

    final originalPath = ref.read(configProvider).path;
    final dark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: Text(l.resultTitle),
        actions: [
          IconButton(
            icon: const Icon(Icons.more_vert_rounded),
            onPressed: () {},
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: RsSpacing.l),
              child: ClipRRect(
                borderRadius: RsRadius.cardBorder,
                child: InteractiveViewer(
                  maxScale: 4,
                  child: BeforeAfterSlider(
                    before: originalPath != null
                        ? Image.file(File(originalPath), fit: BoxFit.contain)
                        : const SizedBox(),
                    after: Image.file(File(job.message!), fit: BoxFit.contain),
                    height: 320,
                  ),
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(RsSpacing.xl),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Info row (resolution, size, scale, model, time).
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    InfoChip(
                      label: l.resultInfoResolution(
                        result.outputWidth,
                        result.outputHeight,
                      ),
                      icon: Icons.aspect_ratio_rounded,
                    ),
                    const SizedBox(width: RsSpacing.s),
                    InfoChip(
                      label: l.resultInfoScale(
                        result.usedScale,
                        result.modelId,
                      ),
                      tone: InfoChipTone.brand,
                    ),
                    const SizedBox(width: RsSpacing.s),
                    InfoChip(
                      label: l.resultInfoTime(result.elapsedMs ~/ 1000),
                      tone: InfoChipTone.neutral,
                    ),
                  ],
                ),
                if (result.facesRestored > 0) ...[
                  const SizedBox(height: RsSpacing.s),
                  Center(
                    child: InfoChip(
                      label: l.resultFacesRestored(result.facesRestored),
                      tone: InfoChipTone.success,
                      icon: Icons.face_retouching_natural_rounded,
                    ),
                  ),
                ],
                if (result.recoverySteps.isNotEmpty) ...[
                  const SizedBox(height: RsSpacing.s),
                  Center(
                    child: InfoChip(
                      label: l.resultRecoveredNote,
                      tone: InfoChipTone.warning,
                      icon: Icons.build_circle_outlined,
                    ),
                  ),
                ],
                const SizedBox(height: RsSpacing.s),
                Center(
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.check_circle_rounded,
                        size: 15,
                        color: dark
                            ? RsColors.successDark
                            : RsColors.successLight,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        l.resultSavedTo,
                        style: RsType.label.copyWith(
                          color: cs.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: RsSpacing.l),
                Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: SizedBox(
                        height: 54,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: RsColors.brandGradient(dark: dark),
                            borderRadius: RsRadius.pill,
                          ),
                          child: ElevatedButton.icon(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.transparent,
                              shadowColor: Colors.transparent,
                              foregroundColor: Colors.white,
                              shape: const RoundedRectangleBorder(
                                borderRadius: RsRadius.pill,
                              ),
                            ),
                            onPressed: _share,
                            icon: const Icon(Icons.ios_share_rounded),
                            label: Text(
                              l.resultShare,
                              style: RsType.bodyMedium.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: RsSpacing.m),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => context.push(Routes.config),
                        child: Text(
                          l.resultUpscaleAgain,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                    const SizedBox(width: RsSpacing.m),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _newImage,
                        child: Text(
                          l.resultNewImage,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _share() async {
    final path = ref.read(jobProvider).message;
    if (path == null) return;
    await SharePlus.instance.share(ShareParams(files: [XFile(path)]));
  }

  Future<void> _newImage() async {
    await ref.read(configProvider.notifier).reset();
    ref.read(jobProvider.notifier).resetView();
    if (!mounted) return;
    context.go(Routes.home);
  }
}
