/// Processing screen: shows the REAL job state — phase, tile progress
/// from native events, elapsed time, ETA (only when meaningful),
/// recovery status and cancellation.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/providers.dart';
import '../core/theme.dart';
import '../domain/models.dart';
import '../processing/job_manager.dart';

class ProcessingScreen extends ConsumerStatefulWidget {
  const ProcessingScreen({super.key});
  @override
  ConsumerState<ProcessingScreen> createState() => _ProcessingScreenState();
}

class _ProcessingScreenState extends ConsumerState<ProcessingScreen> {
  JobState? _lastState;
  bool _started = false;

  @override
  void initState() {
    super.initState();
    ref.read(jobManagerProvider).state.addListener(_onJobState);
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  void _onJobState() {
    final jobManager = ref.read(jobManagerProvider);
    final state = jobManager.state.value;
    if (state.isTerminal && mounted) {
      if (state.phase == JobPhase.done) {
        final results =
            List<JobResult>.of(ref.read(recentResultsProvider));
        if (state.result != null &&
            !results.any((r) => r.outputPath == state.result!.outputPath)) {
          results.insert(0, state.result!);
          ref.read(recentResultsProvider.notifier).state = results;
        }
        context.go('/results');
      } else if (state.phase == JobPhase.failed) {
        setState(() => _lastState = state);
      }
    } else {
      setState(() => _lastState = state);
    }
  }

  @override
  void dispose() {
    ref.read(jobManagerProvider).state.removeListener(_onJobState);
    super.dispose();
  }

  Future<void> _start() async {
    if (_started) {
      return;
    }
    _started = true;
    final session = ref.read(importSessionProvider);
    if (session == null) {
      if (mounted) {
        context.go('/home');
      }
      return;
    }
    final device = await ref.read(deviceProfileProvider.future);
    final registry = await ref.read(modelRegistryProvider.future);
    final settings = ref.read(settingsProvider);
    final planner = ref.read(jobManagerProvider);
    // JobManager.run owns the rest of the pipeline.
    unawaited(planner.run(
      input: session.file,
      mode: ref.read(processingModeProvider),
      model: registry.firstWhere(
        (m) => m.id == _defaultModelFor(device),
        orElse: () => registry.first,
      ),
      device: device,
      jpegQuality: settings.jpegQuality,
      preferGpu: settings.preferGpu && device.gpuAvailable,
    ).catchError((Object e) {
      // State machine already recorded the failure.
      return JobResult(
        outputPath: '',
        inputWidth: 0,
        inputHeight: 0,
        outputWidth: 0,
        outputHeight: 0,
        inputFileBytes: 0,
        outputFileBytes: 0,
        duration: Duration.zero,
        modelId: '',
        mode: ProcessingMode.upscale,
        outputFormat: OutputFormat.png,
        attempt: 1,
      );
    }));
  }

  String _defaultModelFor(DeviceProfile device) => switch (device.tier) {
        DeviceTier.lowResource || DeviceTier.balanced =>
          'realesr-animevideov3-x4',
        DeviceTier.highPerformance => 'realesrgan-x4plus',
      };

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final state = _lastState ??
        const JobState(phase: JobPhase.queued);
    final session = ref.watch(importSessionProvider);

    // Guard: leaving mid-run would restart the job on re-entry; users
    // must cancel explicitly instead of navigating away.
    return PopScope(
      canPop: state.isTerminal,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Processing'),
          leading: state.isTerminal ? null : const SizedBox.shrink(),
        ),
        body: Center(
        child: Padding(
          padding: const EdgeInsets.all(RsSpacing.xl),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (session != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.file(session.file,
                      height: 170,
                      fit: BoxFit.contain,
                      errorBuilder: (_, _, _) => const SizedBox(
                          height: 170,
                          child: Icon(Icons.image_outlined, size: 48))),
                ),
              const SizedBox(height: RsSpacing.lg),
              Text(
                _phaseLabel(state),
                textAlign: TextAlign.center,
                style: RsType.headline,
              ),
              const SizedBox(height: RsSpacing.sm),
              Text(
                state.message ??
                    _detailText(state, scheme),
                textAlign: TextAlign.center,
                style: RsType.body
                    .copyWith(color: scheme.onSurfaceVariant),
              ),
              const SizedBox(height: RsSpacing.lg),
              LinearProgressIndicator(
                value: state.progress.fraction <= 0
                    ? null
                    : state.progress.fraction.clamp(0.0, 1.0),
                minHeight: 6,
                borderRadius: BorderRadius.circular(3),
              ),
              const SizedBox(height: RsSpacing.sm),
              Text(
                '${(state.progress.fraction * 100).clamp(0, 100).round()}%'
                '${state.progress.tilesTotal > 0 ? ' · tile ${state.progress.tilesDone}/${state.progress.tilesTotal}' : ''}',
                textAlign: TextAlign.center,
                style: RsType.mono,
              ),
              if (state.eta != null) ...[
                const SizedBox(height: 4),
                Text(
                  'about ${_formatDuration(state.eta!)} remaining',
                  textAlign: TextAlign.center,
                  style: RsType.caption
                      .copyWith(color: scheme.onSurfaceVariant),
                ),
              ],
              if (state.phase == JobPhase.failed) ...[
                const SizedBox(height: RsSpacing.md),
                PaperCard(
                  borderColor: scheme.error,
                  child: Text(
                    state.error ?? 'Processing failed.',
                    style: RsType.body.copyWith(color: scheme.error),
                  ),
                ),
                const SizedBox(height: RsSpacing.md),
                PaperButton(
                  label: 'Back to Home',
                  onPressed: () => context.go('/home'),
                ),
              ] else if (state.phase == JobPhase.cancelled) ...[
                const SizedBox(height: RsSpacing.md),
                PaperButton(
                  label: 'Back to Home',
                  onPressed: () => context.go('/home'),
                ),
              ] else ...[
                const SizedBox(height: RsSpacing.lg),
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(
                        borderRadius:
                            BorderRadius.circular(RsRadius.button)),
                  ),
                  onPressed: () =>
                      ref.read(jobManagerProvider).cancel(),
                  child: const Text('Cancel'),
                ),
              ],
            ],
          ),
        ),
      ),
      ),
    );
  }

  String _phaseLabel(JobState state) => switch (state.phase) {
        JobPhase.queued => 'Queued',
        JobPhase.inspecting => 'Inspecting image',
        JobPhase.planning => 'Planning pipeline',
        JobPhase.acquiringModel => 'Downloading model',
        JobPhase.decoding => 'Decoding',
        JobPhase.inferring => 'AI upscaling',
        JobPhase.encoding => 'Encoding output',
        JobPhase.saving => 'Saving',
        JobPhase.done => 'Done',
        JobPhase.failed => 'Failed',
        JobPhase.cancelled => 'Cancelled',
      };

  String _detailText(JobState state, ColorScheme scheme) {
    final plan = state.plan;
    if (state.phase == JobPhase.inferring && plan != null) {
      return '${plan.scale}× · ${plan.tileSize}px tiles · '
          'attempt ${state.progress.attempt}';
    }
    return '';
  }

  String _formatDuration(Duration d) {
    if (d.inMinutes >= 1) {
      return '${d.inMinutes} min';
    }
    return '${d.inSeconds.clamp(1, 59)} s';
  }
}
