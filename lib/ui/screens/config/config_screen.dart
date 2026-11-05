import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app.dart';
import '../../../core/feature_gates.dart';
import '../../../engine/upscale_engine.dart'
    show
        ComputeDevice,
        DeviceProfile,
        ImageType,
        JobSpec,
        SettingLevel,
        UpscaleMode;
import '../../../l10n/app_localizations.dart';
import '../../../services/feasibility/feasibility_checker.dart';
import '../../../services/image_service/image_service.dart' show ImageKind;
import '../../../services/model_catalog/model_catalog.dart' show ModelDef;
import '../../../services/model_manager/model_manager.dart'
    show InstallStatus, ModelInstallInfo;
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/cards.dart' show TabularFigures;
import '../../../ui/widgets/common.dart';
import '../../../ui/widgets/controls.dart';
import 'advanced_section.dart';
import 'sr_section.dart';

/// Config screen (tasks 1.13 / 2.5 / 2.6): preview, level control, detection
/// summary card, scale chips (Manual), inline model download, face toggle,
/// sticky Upscale button with output size + ETA.
class ConfigScreen extends ConsumerStatefulWidget {
  const ConfigScreen({super.key});

  @override
  ConsumerState<ConfigScreen> createState() => _ConfigScreenState();
}

class _ConfigScreenState extends ConsumerState<ConfigScreen> {
  @override
  void initState() {
    super.initState();
    unawaited(
      Future.microtask(() async {
        await ref.read(configProvider.notifier).ensureInputRule();
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final cfg = ref.watch(configProvider);
    final deviceAsync = ref.watch(deviceProfileProvider);

    if (cfg.path == null) {
      return Scaffold(
        appBar: AppBar(),
        body: ErrorState(onRetry: () => context.pop()),
      );
    }

    final isManual = cfg.level == SettingLevel.manual;
    final isAdvanced = cfg.level == SettingLevel.advanced;
    final device = deviceAsync.value;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Text(cfg.isSrMode ? l.configSrBadge : l.configTitle),
            if (cfg.isSrMode) ...[
              const SizedBox(width: RsSpacing.s),
              InfoChip(label: l.proBadge, tone: InfoChipTone.brand),
            ],
          ],
        ),
      ),
      bottomNavigationBar: _StickyBar(device: device),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _PreviewWithMeta(),
            const SizedBox(height: RsSpacing.l),
            LevelSegmentedControl(
              selected: switch (cfg.level) {
                SettingLevel.manual => SettingLevelView.manual,
                SettingLevel.advanced => SettingLevelView.advanced,
                _ => SettingLevelView.defaultLevel,
              },
              showAdvanced: FeatureGates.advancedLevel,
              onChanged: (v) {
                Haptics.selection(ref);
                ref.read(configProvider.notifier).setLevel(switch (v) {
                  SettingLevelView.manual => SettingLevel.manual,
                  SettingLevelView.advanced => SettingLevel.advanced,
                  _ => SettingLevel.defaultLevel,
                });
              },
            ),
            const SizedBox(height: RsSpacing.l),
            _DetectionCard(cfg: cfg),
            if (cfg.isSrMode) ...[
              const SizedBox(height: RsSpacing.l),
              SrSection(feasibility: _feasibilityFor(ref, cfg, device)),
            ],
            if (isManual || isAdvanced) ...[
              const SizedBox(height: RsSpacing.xl),
              SectionHeader(label: l.configScaleSection),
              ScaleChips(
                selected: cfg.scale,
                onChanged: (s) {
                  Haptics.selection(ref);
                  ref.read(configProvider.notifier).setScale(s);
                },
              ),
              if (cfg.scale == 5) ...[
                const SizedBox(height: RsSpacing.s),
                InfoChip(
                  label: l.configScaleExtraStep,
                  icon: Icons.info_outline_rounded,
                ),
              ],
              const SizedBox(height: RsSpacing.l),
              const _ManualModelSection(),
            ],
            if (isAdvanced) ...[
              const SizedBox(height: RsSpacing.xl),
              AdvancedSection(),
            ],
            if (FeatureGates.faceEnhancement) ...[
              const SizedBox(height: RsSpacing.xl),
              _FaceSection(cfg: cfg),
            ],
            const SizedBox(height: RsSpacing.xxl),
          ],
        ),
      ),
    );
  }

  /// Computes the live SR feasibility (green/amber/red) from the cached
  /// dimensions, the selected/automatic model and the device profile.
  FeasibilityResult? _feasibilityFor(
    WidgetRef ref,
    ConfigState cfg,
    DeviceProfile? device,
  ) {
    if (!cfg.isSrMode || device == null) return null;
    final dims = configDimsProviderKey(cfg);
    if (dims == null) return null;
    final services = ref.read(servicesProvider);
    try {
      final modelId = _resolveModelId(ref, cfg, device);
      final model = services.catalog.byId(modelId ?? '');
      if (model == null) return null;
      final spec = buildPlannerSpec(ref, cfg, model.id, dims.$1, dims.$2);
      final plan = services.planner.plan(
        spec: spec,
        device: device,
        catalog: services.catalog,
        inputWidth: dims.$1,
        inputHeight: dims.$2,
      );
      final eta = services.planner.estimateSeconds(plan, dims.$1, dims.$2);
      return const FeasibilityChecker().check(
        mode: cfg.mode,
        inputWidth: dims.$1,
        inputHeight: dims.$2,
        targetScale: cfg.scale,
        outputCapMegapixels: plan.outputCapMegapixels,
        etaSeconds: eta,
        device: device,
        previewOnly: cfg.previewCrop != null,
      );
    } catch (_) {
      return null;
    }
  }

  String? _resolveModelId(
    WidgetRef ref,
    ConfigState cfg,
    DeviceProfile device,
  ) {
    final services = ref.read(servicesProvider);
    if (cfg.level != SettingLevel.defaultLevel && cfg.selectedModelId != null) {
      return cfg.selectedModelId;
    }
    return services.planner.autoModelId(
          mode: cfg.mode,
          imageType: cfg.typeName,
          tier: device.tier,
          catalog: services.catalog,
        ) ??
        cfg.selectedModelId;
  }
}

/// Sticky gradient Upscale button with output size + ETA second line
/// (design-system §8). One tap starts the job in Default (task 1.13).
class _StickyBar extends ConsumerWidget {
  const _StickyBar({required this.device});
  final DeviceProfile? device;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final cfg = ref.watch(configProvider);
    final dev = device ?? ref.watch(deviceProfileProvider).value;
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;

    String outputLine = '';
    if (dev != null && cfg.path != null) {
      final services = ref.read(servicesProvider);
      final bytes = cfg.effectiveBytes ?? cfg.originalBytes;
      if (bytes != null) {
        try {
          final d = ref.read(configDimsProvider);
          if (d != null) {
            final dims = d;
            final modelId =
                cfg.level != SettingLevel.defaultLevel &&
                    cfg.selectedModelId != null
                ? cfg.selectedModelId!
                : services.planner.autoModelId(
                        mode: cfg.mode,
                        imageType: cfg.typeName,
                        tier: dev.tier,
                        catalog: services.catalog,
                      ) ??
                      cfg.selectedModelId;
            final model = services.catalog.byId(modelId ?? '');
            if (model != null) {
              final spec = buildPlannerSpec(
                ref,
                cfg,
                model.id,
                dims.$1,
                dims.$2,
              );
              final plan = services.planner.plan(
                spec: spec,
                device: dev,
                catalog: services.catalog,
                inputWidth: dims.$1,
                inputHeight: dims.$2,
              );
              final eta = services.planner.estimateSeconds(
                plan,
                dims.$1,
                dims.$2,
              );
              outputLine =
                  '${l.configOutputSize(dims.$1 * cfg.scale, dims.$2 * cfg.scale)} · ${l.configEta(formatDuration(l, eta))}';
            }
          }
        } catch (_) {
          outputLine = '';
        }
      }
    }

    final isSr = cfg.isSrMode;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          RsSpacing.xl,
          RsSpacing.m,
          RsSpacing.xl,
          RsSpacing.l,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (outputLine.isNotEmpty) ...[
              Text(
                outputLine,
                style: RsType.label.copyWith(
                  color: cs.onSurfaceVariant,
                  fontFeatures: const [TabularFigures()],
                ),
              ),
              const SizedBox(height: RsSpacing.m),
            ],
            Row(
              children: [
                // Super Resolution: preview the crop first (Phase 3).
                if (isSr && cfg.previewCrop != null) ...[
                  SizedBox(
                    height: 56,
                    child: OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        shape: const RoundedRectangleBorder(
                          borderRadius: RsRadius.pill,
                        ),
                        side: BorderSide(color: cs.primary),
                      ),
                      onPressed: () => _runPreview(context, ref),
                      icon: const Icon(Icons.crop_free_rounded, size: 20),
                      label: Text(l.configPreviewRun),
                    ),
                  ),
                  const SizedBox(width: RsSpacing.m),
                ],
                Expanded(
                  child: SizedBox(
                    height: 56,
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
                        onPressed: () => _startUpscale(context, ref),
                        icon: const Icon(Icons.auto_awesome_rounded),
                        label: Text(
                          l.configUpscaleButton,
                          style: RsType.bodyMedium.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// Preview run (Phase 3): the pipeline on the crop region only.
  Future<void> _runPreview(BuildContext context, WidgetRef ref) async {
    Haptics.medium(ref);
    final notifier = ref.read(previewProvider.notifier);
    unawaited(notifier.run());
    if (!context.mounted) return;
    final l = AppLocalizations.of(context)!;
    await AppBottomSheet.show(
      context,
      title: l.configPreviewTitle,
      child: PreviewResultSheet(
        onUpscaleFull: () {
          Navigator.of(context).pop();
          unawaited(ref.read(previewProvider.notifier).close());
          _startUpscale(context, ref);
        },
      ),
    );
    // Sheet dismissed — clean the temp preview file.
    await notifier.close();
  }

  Future<void> _startUpscale(BuildContext context, WidgetRef ref) async {
    final cfg = ref.read(configProvider);
    final dev = device ?? ref.read(deviceProfileProvider).value;
    if (dev == null || cfg.path == null) return;
    final l = AppLocalizations.of(context)!;

    // Super Resolution feasibility gate (Phase 3): red blocks; amber asks.
    if (cfg.isSrMode) {
      final check = _currentFeasibility(ref, cfg, dev);
      if (check != null && check.blocked) {
        Haptics.warning(ref);
        if (!context.mounted) return;
        final switchMode = await AppBottomSheet.show<bool>(
          context,
          title: l.configSrBlockedTitle,
          child: _SrBlockedSheet(body: l.configSrBlockedBody),
        );
        if (switchMode == true && context.mounted) {
          unawaited(
            ref
                .read(configProvider.notifier)
                .loadFrom(
                  path: cfg.path!,
                  bytes: cfg.originalBytes!,
                  level: cfg.level,
                  mode: UpscaleMode.upscale,
                ),
          );
        }
        return;
      }
      if (check != null && check.level == FeasibilityLevel.amber) {
        Haptics.warning(ref);
        if (!context.mounted) return;
        final go = await AppBottomSheet.show<bool>(
          context,
          title: l.configSrConfirmAmberTitle,
          child: _HeavyConfirmSheet(body: l.configSrConfirmAmberBody),
        );
        if (go != true || !context.mounted) return;
      }
    }

    final services = ref.read(servicesProvider);
    final modelId =
        cfg.level != SettingLevel.defaultLevel && cfg.selectedModelId != null
        ? cfg.selectedModelId
        : services.planner.autoModelId(
                mode: cfg.mode,
                imageType: cfg.typeName,
                tier: dev.tier,
                catalog: services.catalog,
              ) ??
              cfg.selectedModelId;
    final model = services.catalog.byId(modelId ?? '');
    if (model != null && model.heavy && dev.tier.index <= 1) {
      if (!context.mounted) return;
      final go = await AppBottomSheet.show<bool>(
        context,
        title: l.configHeavyTitle,
        child: _HeavyConfirmSheet(body: l.configHeavyBody(model.name)),
      );
      if (go != true || !context.mounted) return;
    }

    Haptics.medium(ref);
    if (!context.mounted) return;
    unawaited(context.push(Routes.processing));
  }

  FeasibilityResult? _currentFeasibility(
    WidgetRef ref,
    ConfigState cfg,
    DeviceProfile dev,
  ) {
    if (!cfg.isSrMode) return null;
    final dims = configDimsProviderKey(cfg);
    if (dims == null) return null;
    final services = ref.read(servicesProvider);
    try {
      final modelId =
          cfg.level != SettingLevel.defaultLevel && cfg.selectedModelId != null
          ? cfg.selectedModelId!
          : services.planner.autoModelId(
                  mode: cfg.mode,
                  imageType: cfg.typeName,
                  tier: dev.tier,
                  catalog: services.catalog,
                ) ??
                cfg.selectedModelId!;
      final model = services.catalog.byId(modelId);
      if (model == null) return null;
      final spec = buildPlannerSpec(ref, cfg, model.id, dims.$1, dims.$2);
      final plan = services.planner.plan(
        spec: spec,
        device: dev,
        catalog: services.catalog,
        inputWidth: dims.$1,
        inputHeight: dims.$2,
      );
      final eta = services.planner.estimateSeconds(plan, dims.$1, dims.$2);
      return const FeasibilityChecker().check(
        mode: cfg.mode,
        inputWidth: dims.$1,
        inputHeight: dims.$2,
        targetScale: cfg.scale,
        outputCapMegapixels: plan.outputCapMegapixels,
        etaSeconds: eta,
        device: dev,
      );
    } catch (_) {
      return null;
    }
  }
}

/// Cached decoded dimensions of the current config photo.
final configDimsProvider = Provider<(int, int)?>((ref) {
  final cfg = ref.watch(configProvider);
  final bytes = cfg.effectiveBytes ?? cfg.originalBytes;
  if (bytes == null) return null;
  return dimsCache[cfg.path];
});

/// Builds a REAL JobSpec (the only object the UI sends toward the engine)
/// used both for planning/estimates and for the actual run.
JobSpec buildPlannerSpec(
  WidgetRef ref,
  ConfigState cfg,
  String modelId,
  int w,
  int h,
) {
  final adv = cfg.advanced;
  return JobSpec(
    jobId: 'preview-${cfg.path ?? ''}',
    inputPath: cfg.path ?? '',
    inputBytes: cfg.effectiveBytes ?? cfg.originalBytes ?? Uint8ListEmpty.bytes,
    mode: cfg.mode,
    settingLevel: cfg.level,
    imageType: cfg.imageType == ImageKind.anime
        ? ImageType.anime
        : ImageType.photo,
    scale: cfg.scale,
    modelId: modelId,
    faceEnhancement: cfg.faceEnabled,
    tileSize: cfg.level == SettingLevel.advanced ? adv.tileSize : 0,
    overlap: adv.overlapEnum,
    overlapExplicit: cfg.level == SettingLevel.advanced && !adv.overlapIsAuto,
    computeDevice: switch ((cfg.level == SettingLevel.advanced)
        ? adv.compute
        : 'auto') {
      'gpu' => ComputeDevice.gpu,
      'cpu' => ComputeDevice.cpu,
      _ => ComputeDevice.auto,
    },
    threadCount: cfg.level == SettingLevel.advanced ? adv.threads : 0,
    outputCapMegapixels: cfg.level == SettingLevel.advanced
        ? adv.outputCapMp
        : 0,
    options: {'decodedWidth': w, 'decodedHeight': h},
  );
}

abstract final class Uint8ListEmpty {
  static final bytes = Uint8List(0);
}

class _HeavyConfirmSheet extends StatelessWidget {
  const _HeavyConfirmSheet({required this.body});
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
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => context.pop(false),
                  child: Text(l.configHeavyCancel),
                ),
              ),
              const SizedBox(width: RsSpacing.m),
              Expanded(
                child: FilledButton(
                  onPressed: () => context.pop(true),
                  child: Text(l.configHeavyContinue),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Shown when Super Resolution is blocked (red feasibility) — offers the
/// one-tap switch back to Upscale mode.
class _SrBlockedSheet extends StatelessWidget {
  const _SrBlockedSheet({required this.body});
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
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => context.pop(false),
                  child: Text(l.close),
                ),
              ),
              const SizedBox(width: RsSpacing.m),
              Expanded(
                child: FilledButton(
                  onPressed: () => context.pop(true),
                  child: Text(l.configSrBlockedUseUpscale),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PreviewWithMeta extends ConsumerWidget {
  const _PreviewWithMeta();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cfg = ref.watch(configProvider);
    final bytes = cfg.effectiveBytes ?? cfg.originalBytes;
    return ClipRRect(
      borderRadius: RsRadius.cardBorder,
      child: Stack(
        children: [
          AspectRatio(
            aspectRatio: 4 / 3,
            child: bytes != null
                ? Image.memory(bytes, fit: BoxFit.cover, gaplessPlayback: true)
                : Container(
                    color: Theme.of(
                      context,
                    ).colorScheme.surfaceContainerHighest,
                  ),
          ),
          Positioned(
            bottom: 10,
            left: 10,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.55),
                borderRadius: RsRadius.pill,
              ),
              child: Text(
                cfg.path?.split('/').last ?? '',
                style: RsType.label.copyWith(color: Colors.white, fontSize: 11),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DetectionCard extends ConsumerWidget {
  const _DetectionCard({required this.cfg});
  final ConfigState cfg;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final typeLabel = cfg.imageType == ImageKind.anime
        ? l.configDetectedAnime
        : l.configDetectedPhoto;
    final lowConfidence = cfg.confidence < 0.62;

    return Container(
      padding: const EdgeInsets.all(RsSpacing.l),
      decoration: BoxDecoration(
        color: cs.primary.withValues(alpha: 0.06),
        borderRadius: RsRadius.cardBorder,
      ),
      child: Row(
        children: [
          Icon(Icons.auto_fix_high_rounded, color: cs.primary, size: 22),
          const SizedBox(width: RsSpacing.m),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${l.configDetectedPrefix} $typeLabel · ${cfg.scale}× · Auto',
                  style: RsType.bodyMedium,
                ),
                if (lowConfidence) ...[
                  const SizedBox(height: 2),
                  Text(
                    l.configNotSure,
                    style: RsType.label.copyWith(color: cs.onSurfaceVariant),
                  ),
                ],
              ],
            ),
          ),
          TextButton(
            onPressed: () {
              Haptics.selection(ref);
              ref
                  .read(configProvider.notifier)
                  .setImageType(
                    cfg.imageType == ImageKind.photo
                        ? ImageType.anime
                        : ImageType.photo,
                  );
            },
            child: Text(l.configChangeLink),
          ),
        ],
      ),
    );
  }
}

class _ManualModelSection extends ConsumerWidget {
  const _ManualModelSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final services = ref.watch(servicesProvider);
    final states = ref.watch(installStatesSnapshotProvider);
    final device = ref.watch(deviceProfileProvider).value;
    final cfg = ref.watch(configProvider);
    final models = services.catalog.forImageType(cfg.typeName);
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SectionHeader(label: l.configManualSection),
        if (models.isEmpty)
          Text(
            l.configManualComingSoon,
            style: RsType.body.copyWith(color: cs.onSurfaceVariant),
          ),
        for (final model in models)
          Padding(
            padding: const EdgeInsets.only(bottom: RsSpacing.m),
            child: ModelListRow(
              name: model.name,
              sizeLabel: formatBytes(model.sizeBytes),
              badge: model.id == _recommended(cfg, services, device)
                  ? l.configModelRecommended
                  : (model.heavy ? l.configModelHeavy : null),
              selected: cfg.selectedModelId == model.id,
              status: _statusFor(model, states),
              progress: states[model.id]?.progress ?? 0,
              actionLabel:
                  states[model.id]?.status == InstallStatus.notInstalled
                  ? l.configModelDownload(formatBytes(model.sizeBytes))
                  : null,
              onAction: () => services.modelManager.install(model.id),
              onTap: () {
                if (states[model.id]?.status == InstallStatus.installed) {
                  Haptics.selection(ref);
                  ref.read(configProvider.notifier).selectModel(model.id);
                }
              },
            ),
          ),
      ],
    );
  }

  String? _recommended(ConfigState cfg, dynamic services, dynamic device) {
    if (device == null) return null;
    return services.planner.autoModelId(
      mode: UpscaleMode.upscale,
      imageType: cfg.typeName,
      tier: device.tier,
      catalog: services.catalog,
    );
  }

  ModelRowStatus _statusFor(
    ModelDef model,
    Map<String, ModelInstallInfo> states,
  ) {
    final info = states[model.id];
    return switch (info?.status ?? InstallStatus.notInstalled) {
      InstallStatus.installed => ModelRowStatus.installed,
      InstallStatus.downloading =>
        (info?.paused ?? false)
            ? ModelRowStatus.paused
            : ModelRowStatus.downloading,
      InstallStatus.error => ModelRowStatus.error,
      InstallStatus.unavailable => ModelRowStatus.unavailable,
      InstallStatus.notInstalled => ModelRowStatus.notInstalled,
    };
  }
}

class _FaceSection extends ConsumerWidget {
  const _FaceSection({required this.cfg});
  final ConfigState cfg;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final services = ref.watch(servicesProvider);
    final faceModel = services.catalog.byId('gfpgan-v1.4');
    final faceAvailable = faceModel?.available == true;
    final faceInstalled = services.modelManager.isInstalled('gfpgan-v1.4');
    // Typed detector hint (task 2.7): "N face(s) detected" when the local
    // helper found face-like regions; otherwise the neutral toggle hint.
    final hint = cfg.faceHint != null
        ? l.configFaceDetectorHint(cfg.faceHint!.count)
        : (!faceAvailable || !faceInstalled
              ? l.configFaceUnavailable
              : l.configFaceHint);

    return FaceToggleRow(
      value: cfg.faceEnabled,
      enabled: faceAvailable && faceInstalled,
      hint: hint,
      onChanged: (v) {
        Haptics.light(ref);
        ref.read(configProvider.notifier).setFaceEnabled(v);
      },
      downloadLabel: faceAvailable && !faceInstalled
          ? l.configFaceDownload(formatBytes(faceModel?.sizeBytes ?? 0))
          : null,
      onDownload: () => services.modelManager.install('gfpgan-v1.4'),
    );
  }
}
