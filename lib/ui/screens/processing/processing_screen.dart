import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../../../app/app.dart';
import '../../../core/app_exception.dart';
import '../../../core/app_logger.dart';
import '../../../engine/upscale_engine.dart' show JobStage;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/cards.dart' show TabularFigures;
import '../../../ui/widgets/common.dart';

/// Processing screen (task 1.14): real tile-fill animation, giant percent,
/// tile counter, honest ETA, stage label, cancel with confirm, rotating tips.
class ProcessingScreen extends ConsumerStatefulWidget {
  const ProcessingScreen({super.key});

  @override
  ConsumerState<ProcessingScreen> createState() => _ProcessingScreenState();
}

class _ProcessingScreenState extends ConsumerState<ProcessingScreen> {
  bool _navigatedAway = false;

  @override
  void initState() {
    super.initState();
    unawaited(Future.microtask(_start));
  }

  bool _wakelockEnabled = false;

  Future<void> _start() async {
    final settings = ref.read(settingsProvider);
    if (settings.keepScreenAwake) {
      await WakelockPlus.enable();
      _wakelockEnabled = true;
    }
    String? failure;
    try {
      await ref.read(jobProvider.notifier).run();
    } on AppException catch (e) {
      failure = e.message;
    } catch (e, st) {
      AppLogger.e('ProcessingScreen', 'job run threw', e, st);
      failure = null;
    } finally {
      // Always release the wakelock: a throwing job must never leave the
      // screen on and the user trapped behind PopScope(canPop: false).
      await _releaseWakelock();
    }
    if (!mounted) return;
    final state = ref.read(jobProvider);
    // Any thrown error (missing model, infeasible SR run …) is surfaced the
    // same way as a failed job instead of freezing the screen.
    if (failure != null && state.stage != JobStage.done) {
      unawaited(_showFailure(failure));
      return;
    }
    if (state.stage == JobStage.done) {
      if (!mounted || _navigatedAway) return;
      _navigatedAway = true;
      context.go(Routes.result);
    } else if (state.stage == JobStage.failed) {
      if (!mounted) return;
      unawaited(_showFailure(state.message));
    }
  }

  Future<void> _releaseWakelock() async {
    // Only touch the wakelock plugin when we actually enabled it above.
    if (!_wakelockEnabled) return;
    _wakelockEnabled = false;
    try {
      await WakelockPlus.disable();
    } catch (e, st) {
      AppLogger.w('ProcessingScreen', 'wakelock disable failed: $e', st);
    }
  }

  Future<void> _showFailure(String? message) async {
    final l = AppLocalizations.of(context)!;
    await AppBottomSheet.show<bool>(
      context,
      title: l.processingFailedTitle,
      child: _FailureSheet(body: message ?? l.processingFailedBody),
    );
    if (!mounted) return;
    context.go(Routes.home);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final state = ref.watch(jobProvider);
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;

    final stageLabel = switch (state.stage) {
      JobStage.preparing => l.processingStagePreparing,
      JobStage.upscaling => l.processingStageUpscaling,
      JobStage.faces => l.processingStageFaces,
      JobStage.saving => l.processingStageSaving,
      JobStage.done => l.processingStageSaving,
      JobStage.failed => l.processingFailedTitle,
      JobStage.cancelled => l.processingCancelled,
    };

    return PopScope(
      canPop: false,
      child: Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: Padding(
                  padding: const EdgeInsets.all(RsSpacing.l),
                  child: Text(
                    stageLabel,
                    style: RsType.title.copyWith(color: cs.primary),
                  ),
                ),
              ),
              Expanded(
                child: Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Flexible: the overlay sees a finite height budget on
                      // desktop/wide windows and shrinks instead of
                      // overflowing (qa: 360x640 + 800x600 layouts).
                      Flexible(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: RsSpacing.xl,
                          ),
                          // The engine already reports 0–0.85 for the
                          // upscale stage (saving takes it to 1), so the
                          // raw value is shown — scaling again here would
                          // cap the bar at 72%.
                          child: TileGridOverlay(fill: state.percent),
                        ),
                      ),
                      const SizedBox(height: RsSpacing.xxl),
                      BigPercent(percent: state.percent),
                      const SizedBox(height: RsSpacing.s),
                      if (state.tileTotal > 0)
                        Text(
                          l.processingTileCount(
                            state.tileIndex,
                            state.tileTotal,
                          ),
                          style: RsType.body.copyWith(
                            color: cs.onSurfaceVariant,
                            fontFeatures: const [TabularFigures()],
                          ),
                        ),
                      const SizedBox(height: 4),
                      Text(
                        state.etaSeconds == null
                            ? l.processingEtaEstimating
                            : (state.etaSeconds! <= 0
                                  ? ''
                                  : l.processingEta(
                                      formatDuration(l, state.etaSeconds!),
                                    )),
                        style: RsType.body.copyWith(
                          color: cs.accentWarmColor(dark),
                        ),
                        key: const ValueKey('eta'),
                      ),
                      const SizedBox(height: RsSpacing.xl),
                      Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: RsSpacing.xxl,
                        ),
                        child: Text(
                          _tip(l, state.tileIndex),
                          style: RsType.label.copyWith(
                            color: cs.onSurfaceVariant,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(RsSpacing.xl),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () async {
                          final l = AppLocalizations.of(context)!;
                          final confirmed = await AppBottomSheet.show<bool>(
                            context,
                            title: l.processingCancelConfirmTitle,
                            child: _CancelConfirmSheet(
                              body: l.processingCancelConfirmBody,
                            ),
                          );
                          if (confirmed == true && context.mounted) {
                            await ref.read(jobProvider.notifier).cancel();
                            await _releaseWakelock();
                            await ref
                                .read(configProvider.notifier)
                                .reset();
                            if (!context.mounted) return;
                            _navigatedAway = true;
                            context.go(Routes.home);
                          }
                        },
                        child: Text(l.processingCancel),
                      ),
                    ),
                    const SizedBox(width: RsSpacing.m),
                    Expanded(
                      child: FilledButton.tonal(
                        onPressed: () {
                          _navigatedAway = true;
                          context.go(Routes.home);
                        },
                        child: Text(
                          l.processingBackground,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _tip(AppLocalizations l, int tileIndex) {
    final tips = [l.processingTip1, l.processingTip2, l.processingTip3];
    return tips[tileIndex % tips.length];
  }
}

extension on ColorScheme {
  Color accentWarmColor(bool dark) =>
      dark ? RsColors.accentWarmDark : RsColors.accentWarmLight;
}

class _CancelConfirmSheet extends StatelessWidget {
  const _CancelConfirmSheet({required this.body});
  final String body;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        RsSpacing.xl,
        0,
        RsSpacing.xl,
        RsSpacing.xl,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(body, style: RsType.body),
          const SizedBox(height: RsSpacing.xl),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () => context.pop(true),
              child: Text(l.processingCancelYes),
            ),
          ),
          const SizedBox(height: RsSpacing.s),
          SizedBox(
            width: double.infinity,
            child: TextButton(
              onPressed: () => context.pop(false),
              child: Text(l.processingCancelNo),
            ),
          ),
        ],
      ),
    );
  }
}

/// Failure path (task 1.14): friendly message + "Try safer settings" retry.
class _FailureSheet extends ConsumerWidget {
  const _FailureSheet({required this.body});
  final String body;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        RsSpacing.xl,
        0,
        RsSpacing.xl,
        RsSpacing.xl,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(body, style: RsType.body, textAlign: TextAlign.center),
          const SizedBox(height: RsSpacing.xl),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () => context.pop(true),
              child: Text(l.processingTrySafer),
            ),
          ),
        ],
      ),
    );
  }
}
