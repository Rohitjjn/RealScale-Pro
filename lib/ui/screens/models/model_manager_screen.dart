import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../l10n/app_localizations.dart';
import '../../../services/model_catalog/model_catalog.dart'
    show ModelDef, UnavailableReason;
import '../../../services/model_manager/model_manager.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/common.dart';
import '../../../ui/widgets/controls.dart';

/// Model Manager (task 2.4): grouped packs, status + size rows, storage bar,
/// Photo/Anime/Face filters, download-all, delete, update actions.
class ModelManagerScreen extends ConsumerStatefulWidget {
  const ModelManagerScreen({super.key});

  @override
  ConsumerState<ModelManagerScreen> createState() => _ModelManagerScreenState();
}

enum _PackFilter { all, photo, anime, face }

/// Re-computed whenever any install state changes, so the storage bar stays
/// honest after downloads and deletes.
final usedModelBytesProvider = FutureProvider<int>((ref) async {
  ref.watch(installStatesProvider);
  return ref.watch(servicesProvider).modelManager.installedBytes();
});

class _ModelManagerScreenState extends ConsumerState<ModelManagerScreen> {
  _PackFilter _filter = _PackFilter.all;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final services = ref.watch(servicesProvider);
    final states = ref.watch(installStatesSnapshotProvider);
    final cs = Theme.of(context).colorScheme;
    final device = ref.watch(deviceProfileProvider).value;
    final used = ref.watch(usedModelBytesProvider).value ?? 0;

    final packs = ['starter', 'pro', 'face', 'ultra'];
    final packTitles = {
      'starter': l.modelManagerPackStarter,
      'pro': l.modelManagerPackPro,
      'face': l.modelManagerPackFace,
      'ultra': l.modelManagerPackUltra,
      'community': l.modelManagerPackCommunity,
    };

    return Scaffold(
      appBar: AppBar(title: Text(l.modelManagerTitle)),
      body: Builder(
        builder: (context) {
          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
            children: [
              // Honest storage bar: used vs remaining free space on the
              // device (freeStorageMb from the real DeviceProfiler run).
              Container(
                padding: const EdgeInsets.all(RsSpacing.l),
                decoration: BoxDecoration(
                  color: cs.surface,
                  borderRadius: RsRadius.cardBorder,
                  border: Border.all(color: cs.outline),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            l.modelManagerStorage(
                              formatBytes(used),
                              formatBytes(
                                (device?.freeStorageMb ?? 0) * 1024 * 1024,
                              ),
                            ),
                            style: RsType.bodyMedium,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    ClipRRect(
                      borderRadius: RsRadius.pill,
                      child: Builder(
                        builder: (context) {
                          final freeBytes =
                              (device?.freeStorageMb ?? 0) * 1024 * 1024;
                          final total = used + freeBytes;
                          final fill = total > 0
                              ? (used / total).clamp(0.0, 1.0)
                              : 0.0;
                          return LinearProgressIndicator(
                            value: fill,
                            minHeight: 6,
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: RsSpacing.m),

              // Filters (task 2.4: Photo/Anime/Face/mode filters).
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    for (final f in _PackFilter.values)
                      Padding(
                        padding: const EdgeInsets.only(right: RsSpacing.s),
                        child: ChoiceChip(
                          label: Text(switch (f) {
                            _PackFilter.all => l.modelManagerFilterAll,
                            _PackFilter.photo => l.modelManagerFilterPhoto,
                            _PackFilter.anime => l.modelManagerFilterAnime,
                            _PackFilter.face => l.modelManagerFilterFace,
                          }),
                          selected: _filter == f,
                          onSelected: (_) => setState(() => _filter = f),
                        ),
                      ),
                  ],
                ),
              ),

              for (final pack in packs)
                _PackSection(
                  title: packTitles[pack] ?? pack,
                  models: services.catalog
                      .byPack(pack)
                      .where((m) => _matchesFilter(m))
                      .toList(),
                  states: states,
                ),

              const SizedBox(height: RsSpacing.xxl),
            ],
          );
        },
      ),
    );
  }

  bool _matchesFilter(dynamic model) {
    if (_filter == _PackFilter.all) return true;
    final type = switch (_filter) {
      _PackFilter.photo => 'photo',
      _PackFilter.anime => 'anime',
      _PackFilter.face => 'faces',
      _PackFilter.all => null,
    };
    return model.bestFor.contains(type);
  }
}

class _PackSection extends ConsumerWidget {
  const _PackSection({
    required this.title,
    required this.models,
    required this.states,
  });

  final String title;
  final List<ModelDef> models;
  final Map<String, ModelInstallInfo> states;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final services = ref.watch(servicesProvider);
    if (models.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SectionHeader(
          label: title,
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextButton(
                onPressed: () async {
                  for (final m in models) {
                    if (m.available &&
                        states[m.id]?.status != InstallStatus.installed) {
                      await services.modelManager.install(m.id);
                    }
                  }
                },
                child: Text(l.modelManagerDownloadAll),
              ),
            ],
          ),
        ),
        for (final model in models)
          Padding(
            padding: const EdgeInsets.only(bottom: RsSpacing.m),
            child: ModelListRow(
              name: model.name,
              sizeLabel: model.sizeBytes > 0
                  ? formatBytes(model.sizeBytes)
                  : '—',
              status: _status(model, states),
              progress: states[model.id]?.progress ?? 0,
              subtitle: _subtitle(context, model),
              actionLabel: _actionLabel(context, model, states),
              onAction: () async {
                final status = states[model.id]?.status;
                if (status == InstallStatus.installed) {
                  final l = AppLocalizations.of(context)!;
                  final confirmed = await showDialog<bool>(
                    context: context,
                    builder: (context) => AlertDialog(
                      title: Text(l.modelManagerDeleteConfirmTitle),
                      content: Text(l.modelManagerDeleteConfirmBody),
                      actions: [
                        TextButton(
                          onPressed: () => context.pop(false),
                          child: Text(l.cancel),
                        ),
                        FilledButton(
                          onPressed: () => context.pop(true),
                          child: Text(l.delete),
                        ),
                      ],
                    ),
                  );
                  if (confirmed == true) {
                    await services.modelManager.delete(model.id);
                    // Fallback-to-Auto note (task 2.3).
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text(l.modelManagerDeleteFallback)),
                      );
                    }
                  }
                } else if (model.available) {
                  await services.modelManager.install(model.id);
                }
              },
            ),
          ),
      ],
    );
  }

  String? _subtitle(BuildContext context, ModelDef model) {
    final l = AppLocalizations.of(context)!;
    if (!model.available) {
      return switch (model.unavailableReason) {
        UnavailableReason.licenseBlocked => l.modelManagerUnavailableLicense,
        UnavailableReason.weightsChecksumPending =>
          l.modelManagerUnavailableChecksum,
        UnavailableReason.comingSoon => l.modelManagerUnavailableSoon,
        UnavailableReason.none => null,
      };
    }
    return '${l.modelManagerLicense}: ${model.license}';
  }

  String? _actionLabel(
    BuildContext context,
    ModelDef model,
    Map<String, ModelInstallInfo> states,
  ) {
    final l = AppLocalizations.of(context)!;
    final status = states[model.id]?.status ?? InstallStatus.notInstalled;
    if (!model.available) return null;
    return switch (status) {
      InstallStatus.notInstalled => l.setupDownload,
      InstallStatus.installed => l.delete,
      InstallStatus.error => l.retry,
      _ => null,
    };
  }

  ModelRowStatus _status(ModelDef model, Map<String, ModelInstallInfo> states) {
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
