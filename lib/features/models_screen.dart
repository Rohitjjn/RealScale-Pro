/// Model Manager screen: registry, real download progress, SHA-256
/// verification status, deletion, license info.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/providers.dart';
import '../core/theme.dart';
import '../domain/models.dart';

class ModelsScreen extends ConsumerStatefulWidget {
  const ModelsScreen({super.key});
  @override
  ConsumerState<ModelsScreen> createState() => _ModelsScreenState();
}

class _ModelsScreenState extends ConsumerState<ModelsScreen> {
  final Map<String, String?> _errors = {};

  Future<void> _download(ModelInfo model) async {
    final settings = ref.read(settingsProvider);
    if (!settings.modelDownloadConsent) {
      final granted = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text('Allow model downloads?', style: RsType.headline),
          content: Text(
            '${model.displayName} (${model.approxSizeMb.toStringAsFixed(1)} MB) '
            'will be downloaded from the official Real-ESRGAN release on '
            'GitHub, checksum-verified, and stored on this device for '
            'offline use.',
            style: RsType.body,
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Not now')),
            FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Allow')),
          ],
        ),
      );
      if (granted == true) {
        settings.modelDownloadConsent = true;
      } else {
        return;
      }
    }
    setState(() => _errors[model.id] = null);
    try {
      await ref.read(modelManagerProvider).download(model);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content:
                Text('${model.displayName} downloaded and verified.')));
      }
    } catch (e) {
      setState(() => _errors[model.id] = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final registryAsync = ref.watch(modelRegistryProvider);
    final manager = ref.watch(modelManagerProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Models')),
      body: registryAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
            child: Padding(
          padding: const EdgeInsets.all(RsSpacing.lg),
          child: Text('Could not load the model registry: $e',
              style: RsType.body.copyWith(color: scheme.error)),
        )),
        data: (models) {
          // Refresh install status each rebuild.
          return ListView(
            padding: const EdgeInsets.all(RsSpacing.md),
            children: [
              Text(
                'Models download on demand, are SHA-256 verified, and work '
                'fully offline afterwards. Weights are never bundled into '
                'the app.',
                style: RsType.caption
                    .copyWith(color: scheme.onSurfaceVariant),
              ),
              const SizedBox(height: RsSpacing.md),
              for (final model in models)
                Padding(
                  padding: const EdgeInsets.only(bottom: RsSpacing.sm),
                  child: _ModelCard(
                    model: model,
                    status: manager.statusOf(model),
                    progress: manager.downloadProgressOf(model),
                    error: _errors[model.id],
                    onDownload: () => _download(model),
                    onDelete: () async {
                      await manager.delete(model);
                      setState(() {});
                    },
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

class _ModelCard extends StatelessWidget {
  const _ModelCard({
    required this.model,
    required this.status,
    required this.progress,
    required this.error,
    required this.onDownload,
    required this.onDelete,
  });

  final ModelInfo model;
  final ModelInstallStatus status;
  final double? progress;
  final String? error;
  final VoidCallback onDownload;
  final Future<void> Function() onDelete;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return PaperCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(model.displayName, style: RsType.title),
              ),
              switch (status) {
                ModelInstallStatus.installed => PaperChip(
                    label: 'Installed', icon: Icons.check, active: true),
                ModelInstallStatus.downloading => PaperChip(
                    label:
                        '${((progress ?? 0) * 100).round()}%',
                    icon: Icons.downloading),
                ModelInstallStatus.corrupted => PaperChip(
                    label: 'Corrupted',
                    icon: Icons.error_outline),
                _ => PaperChip(
                    label: 'Not installed',
                    icon: Icons.cloud_download_outlined),
              },
            ],
          ),
          const SizedBox(height: 4),
          Text(model.description,
              style: RsType.caption
                  .copyWith(color: scheme.onSurfaceVariant)),
          const SizedBox(height: 8),
          Text(
            '${model.approxSizeMb.toStringAsFixed(1)} MB · '
            '${model.scale}× · ${model.family} · '
            'min RAM ~${model.minRamMb} MB · ${model.license}',
            style: RsType.mono.copyWith(color: scheme.onSurfaceVariant),
          ),
          if (error != null) ...[
            const SizedBox(height: 8),
            Text(error!,
                style: RsType.caption.copyWith(color: scheme.error)),
          ],
          if (status == ModelInstallStatus.downloading &&
              progress != null) ...[
            const SizedBox(height: 8),
            LinearProgressIndicator(value: progress),
          ],
          const SizedBox(height: 10),
          Row(
            children: [
              if (status == ModelInstallStatus.notInstalled ||
                  status == ModelInstallStatus.corrupted)
                Expanded(
                  child: PaperButton(
                    label: 'Download',
                    icon: Icons.download_outlined,
                    onPressed: onDownload,
                  ),
                )
              else if (status == ModelInstallStatus.installed) ...[
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      shape: RoundedRectangleBorder(
                          borderRadius:
                              BorderRadius.circular(RsRadius.button)),
                    ),
                    onPressed: () => onDelete(),
                    child: const Text('Delete'),
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}
