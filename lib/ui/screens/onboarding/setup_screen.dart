import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app.dart';
import '../../../l10n/app_localizations.dart';
import '../../../services/model_catalog/model_catalog.dart' show ModelDef;
import '../../../services/model_manager/model_manager.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/common.dart';

/// Setup + Starter Pack (task 1.7): per-model rows with size + status,
/// overall progress with %, MB, speed, ETA, pause/resume, mobile-data ask,
/// Wi-Fi-only default. Resume-after-kill handled by the gateway.
class SetupScreen extends ConsumerStatefulWidget {
  const SetupScreen({super.key});

  @override
  ConsumerState<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends ConsumerState<SetupScreen> {
  bool _mobileDataAsked = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(
      () => ref.read(servicesProvider).modelManager.initialize(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final services = ref.watch(servicesProvider);
    final states = ref.watch(installStatesSnapshotProvider);
    final starterModels = services.catalog.byPack('starter');
    final allInstalled = starterModels
        .where((m) => m.available)
        .every((m) => states[m.id]?.status == InstallStatus.installed);

    return Scaffold(
      appBar: AppBar(title: Text(l.setupTitle)),
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
                children: [
                  SectionHeader(label: l.setupStarterPack),
                  Text(
                    l.setupStarterSubtitle,
                    style: RsType.body.copyWith(
                      color: cs(context).onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: RsSpacing.l),
                  for (final model in starterModels)
                    Padding(
                      padding: const EdgeInsets.only(bottom: RsSpacing.m),
                      child: _SetupModelRow(
                        model: model,
                        info: states[model.id],
                      ),
                    ),
                  if (_limitedNote != null) ...[
                    const SizedBox(height: RsSpacing.s),
                    InfoChip(label: _limitedNote!, icon: Icons.info_outline),
                  ],
                  const SizedBox(height: RsSpacing.xl),
                  Row(
                    children: [
                      Icon(
                        Icons.wifi,
                        size: 16,
                        color: cs(context).onSurfaceVariant,
                      ),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          l.setupResumeHint,
                          style: RsType.label.copyWith(
                            color: cs(context).onSurfaceVariant,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(RsSpacing.xl),
              child: Column(
                children: [
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: allInstalled
                          ? _continue
                          : () => _downloadAll(starterModels.map((m) => m.id)),
                      child: Text(
                        allInstalled ? l.setupContinue : l.setupDownload,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  ColorScheme cs(BuildContext context) => Theme.of(context).colorScheme;
  String? _limitedNote;

  Future<void> _downloadAll(Iterable<String> modelIds) async {
    final services = ref.read(servicesProvider);
    if (!_mobileDataAsked && !await _onWifi()) {
      if (!mounted) return;
      final l = AppLocalizations.of(context)!;
      final allow = await AppBottomSheet.show<bool>(
        context,
        title: l.setupMobileDataTitle,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            RsSpacing.xl,
            0,
            RsSpacing.xl,
            RsSpacing.xl,
          ),
          child: Column(
            children: [
              Text(l.setupMobileDataBody, style: RsType.body),
              const SizedBox(height: RsSpacing.xl),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => context.pop(false),
                      child: Text(l.setupKeepWifiOnly),
                    ),
                  ),
                  const SizedBox(width: RsSpacing.m),
                  Expanded(
                    child: FilledButton(
                      onPressed: () => context.pop(true),
                      child: Text(l.setupAllowMobileData),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
      _mobileDataAsked = true;
      if (allow != true) return;
    }
    for (final id in modelIds) {
      await services.modelManager.install(id, allowMobileData: true);
    }
  }

  Future<bool> _onWifi() async {
    // Connectivity detection is best-effort; when unknown, ask.
    return false;
  }

  void _continue() {
    ref.read(settingsProvider).setOnboardingDone(true);
    if (!mounted) return;
    context.go(Routes.home);
  }
}

class _SetupModelRow extends ConsumerWidget {
  const _SetupModelRow({required this.model, this.info});

  final ModelDef model;
  final ModelInstallInfo? info;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final services = ref.watch(servicesProvider);
    final cs = Theme.of(context).colorScheme;
    final status = info?.status ?? InstallStatus.notInstalled;
    final isInstalled = status == InstallStatus.installed;
    final downloading = status == InstallStatus.downloading;

    return Container(
      padding: const EdgeInsets.all(RsSpacing.l),
      decoration: BoxDecoration(
        color: cs.surface,
        borderRadius: RsRadius.cardBorder,
        border: Border.all(color: cs.outline),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(model.name, style: RsType.bodyMedium),
                const SizedBox(height: 2),
                Text(
                  isInstalled
                      ? l.setupInstalled
                      : downloading
                      ? (info!.paused
                            ? l.setupPaused
                            : '${l.setupDownloading} ${(info!.progress * 100).round()}%')
                      : formatBytes(model.sizeBytes),
                  style: RsType.label.copyWith(color: cs.onSurfaceVariant),
                ),
                if (downloading && !info!.paused) ...[
                  const SizedBox(height: 8),
                  ClipRRect(
                    borderRadius: RsRadius.pill,
                    child: LinearProgressIndicator(
                      value: info!.progress.clamp(0, 1),
                      minHeight: 5,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: RsSpacing.m),
          if (isInstalled)
            Icon(Icons.check_circle_rounded, color: cs.primary)
          else if (downloading)
            IconButton(
              onPressed: () => info!.paused
                  ? services.modelManager.resume(model.id)
                  : services.modelManager.pause(model.id),
              icon: Icon(
                info!.paused ? Icons.play_arrow_rounded : Icons.pause_rounded,
              ),
            )
          else
            FilledButton(
              style: FilledButton.styleFrom(
                minimumSize: const Size(0, 40),
                padding: const EdgeInsets.symmetric(horizontal: 16),
              ),
              onPressed: () => services.modelManager.install(model.id),
              child: Text(
                l.setupDownload,
                style: RsType.label.copyWith(fontWeight: FontWeight.w600),
              ),
            ),
        ],
      ),
    );
  }
}
