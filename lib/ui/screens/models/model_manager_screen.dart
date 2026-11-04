import 'package:flutter/material.dart';

import '../../../services/model_manager/model_catalog.dart';
import '../../../services/model_manager/model_manager.dart';
import '../../widgets/core_widgets.dart';

/// Download, update and storage controls for the audited model catalog.
class ModelManagerScreen extends StatefulWidget {
  const ModelManagerScreen({required this.manager, super.key});

  final ModelManager manager;

  @override
  State<ModelManagerScreen> createState() => _ModelManagerScreenState();
}

class _ModelManagerScreenState extends State<ModelManagerScreen> {
  ModelPurpose? _purpose;
  ModelMode? _mode;
  late Future<ModelStorageSummary> _storage;

  @override
  void initState() {
    super.initState();
    _storage = widget.manager.storageSummary();
  }

  void _refreshStorage() {
    if (mounted) {
      setState(() => _storage = widget.manager.storageSummary());
    }
  }

  List<ModelRuntimeState> _filtered(List<ModelRuntimeState> states) => states
      .where(
        (ModelRuntimeState state) =>
            (_purpose == null || state.model.purposes.contains(_purpose)) &&
            (_mode == null || state.model.modes.contains(_mode)),
      )
      .toList(growable: false);

  @override
  Widget build(BuildContext context) => StreamBuilder<List<ModelRuntimeState>>(
    stream: widget.manager.changes,
    initialData: widget.manager.states,
    builder: (
      BuildContext context,
      AsyncSnapshot<List<ModelRuntimeState>> snapshot,
    ) {
      final List<ModelRuntimeState> states = _filtered(
        snapshot.data ?? const <ModelRuntimeState>[],
      );
      return Scaffold(
        appBar: AppBar(title: Text(context.l10n.modelManager)),
        body: RefreshIndicator(
          onRefresh: () async => _refreshStorage(),
          child: ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            children: <Widget>[
              _StorageCard(storage: _storage),
              const SizedBox(height: 12),
              _Filters(
                purpose: _purpose,
                mode: _mode,
                onPurpose: (ModelPurpose? value) {
                  setState(() => _purpose = value);
                },
                onMode: (ModelMode? value) {
                  setState(() => _mode = value);
                },
              ),
              const SizedBox(height: 16),
              if (states.isEmpty)
                _EmptyFilter(onClear: () {
                  setState(() {
                    _purpose = null;
                    _mode = null;
                  });
                })
              else
                for (final ModelPack pack in ModelPack.values)
                  if (states.any(
                    (ModelRuntimeState state) => state.model.pack == pack,
                  ))
                    _PackSection(
                      pack: pack,
                      states: states
                          .where(
                            (ModelRuntimeState state) =>
                                state.model.pack == pack,
                          )
                          .toList(growable: false),
                      manager: widget.manager,
                      onChanged: _refreshStorage,
                    ),
            ],
          ),
        ),
      );
    },
  );
}

class _StorageCard extends StatelessWidget {
  const _StorageCard({required this.storage});

  final Future<ModelStorageSummary> storage;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: FutureBuilder<ModelStorageSummary>(
        future: storage,
        builder: (
          BuildContext context,
          AsyncSnapshot<ModelStorageSummary> snapshot,
        ) {
          if (snapshot.hasError) {
            return Text(context.l10n.somethingWentWrong);
          }
          if (!snapshot.hasData) {
            return const LinearProgressIndicator();
          }
          final ModelStorageSummary value = snapshot.data!;
          final int? free = value.freeBytes;
          final double? fraction = free == null || value.usedBytes + free == 0
              ? null
              : value.usedBytes / (value.usedBytes + free);
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Row(
                children: <Widget>[
                  const Icon(Icons.storage_rounded),
                  const SizedBox(width: 10),
                  Text(
                    context.l10n.modelStorage,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (fraction != null)
                LinearProgressIndicator(value: fraction.clamp(0.0, 1.0).toDouble()),
              const SizedBox(height: 8),
              Text(
                free == null
                    ? context.l10n.modelUsed(_megabytes(value.usedBytes))
                    : context.l10n.modelStorageSummary(
                        _megabytes(value.usedBytes),
                        _megabytes(free),
                      ),
              ),
            ],
          );
        },
      ),
    ),
  );
}

class _Filters extends StatelessWidget {
  const _Filters({
    required this.purpose,
    required this.mode,
    required this.onPurpose,
    required this.onMode,
  });

  final ModelPurpose? purpose;
  final ModelMode? mode;
  final ValueChanged<ModelPurpose?> onPurpose;
  final ValueChanged<ModelMode?> onMode;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: <Widget>[
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: <Widget>[
          ChoiceChip(
            label: Text(context.l10n.modelAll),
            selected: purpose == null,
            onSelected: (_) => onPurpose(null),
          ),
          ChoiceChip(
            label: Text(context.l10n.modelPhoto),
            selected: purpose == ModelPurpose.photo,
            onSelected: (_) => onPurpose(ModelPurpose.photo),
          ),
          ChoiceChip(
            label: Text(context.l10n.modelAnime),
            selected: purpose == ModelPurpose.anime,
            onSelected: (_) => onPurpose(ModelPurpose.anime),
          ),
          ChoiceChip(
            label: Text(context.l10n.modelFace),
            selected: purpose == ModelPurpose.face,
            onSelected: (_) => onPurpose(ModelPurpose.face),
          ),
        ],
      ),
      const SizedBox(height: 8),
      Wrap(
        spacing: 8,
        runSpacing: 8,
        children: <Widget>[
          FilterChip(
            label: Text(context.l10n.modelAllModes),
            selected: mode == null,
            onSelected: (_) => onMode(null),
          ),
          FilterChip(
            label: Text(context.l10n.upscaleMode),
            selected: mode == ModelMode.upscale,
            onSelected: (_) => onMode(ModelMode.upscale),
          ),
          FilterChip(
            label: Text(context.l10n.superResolution),
            selected: mode == ModelMode.superResolution,
            onSelected: (_) => onMode(ModelMode.superResolution),
          ),
        ],
      ),
    ],
  );
}

class _PackSection extends StatelessWidget {
  const _PackSection({
    required this.pack,
    required this.states,
    required this.manager,
    required this.onChanged,
  });

  final ModelPack pack;
  final List<ModelRuntimeState> states;
  final ModelManager manager;
  final VoidCallback onChanged;

  Future<void> _downloadAll() async {
    for (final ModelRuntimeState state in states) {
      if (state.status == ModelInstallStatus.notInstalled ||
          state.status == ModelInstallStatus.updateAvailable ||
          state.status == ModelInstallStatus.failed) {
        await manager.install(state.model.id);
      }
    }
    onChanged();
  }

  @override
  Widget build(BuildContext context) {
    final bool canDownload = states.any(
      (ModelRuntimeState state) =>
          state.model.availability == ModelAvailability.onDemand &&
          state.status != ModelInstallStatus.installed &&
          state.status != ModelInstallStatus.downloading,
    );
    return Padding(
      padding: const EdgeInsets.only(bottom: 18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: Text(
                  _packName(context, pack),
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              if (canDownload)
                TextButton.icon(
                  onPressed: _downloadAll,
                  icon: const Icon(Icons.download_rounded),
                  label: Text(context.l10n.downloadAll),
                ),
            ],
          ),
          for (final ModelRuntimeState state in states)
            _ModelRow(
              state: state,
              manager: manager,
              onChanged: onChanged,
            ),
        ],
      ),
    );
  }
}

class _ModelRow extends StatelessWidget {
  const _ModelRow({
    required this.state,
    required this.manager,
    required this.onChanged,
  });

  final ModelRuntimeState state;
  final ModelManager manager;
  final VoidCallback onChanged;

  Future<void> _install() async {
    await manager.install(state.model.id);
    onChanged();
  }

  Future<void> _delete(BuildContext context) async {
    final bool confirmed =
        await showDialog<bool>(
          context: context,
          builder: (BuildContext context) => AlertDialog(
            title: Text(context.l10n.deleteModelTitle),
            content: Text(context.l10n.deleteModelBody(state.model.name)),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: Text(context.l10n.cancel),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: Text(context.l10n.delete),
              ),
            ],
          ),
        ) ??
        false;
    if (!confirmed) return;
    await manager.delete(state.model.id);
    onChanged();
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              _StatusIcon(status: state.status),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      state.model.name,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '${_megabytes(state.model.sizeBytes)} · '
                      '${state.model.license}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: <Widget>[
                        InfoChip(
                          icon: Icons.speed_rounded,
                          label: _speedName(context, state.model.speed),
                        ),
                        InfoChip(
                          icon: Icons.memory_rounded,
                          label: context.l10n.modelTier(state.model.minTier),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: context.l10n.modelSource,
                onPressed: () => showAppSheet(
                  context,
                  SelectableText(
                    '${state.model.license}\n${state.model.sourceUrl}',
                  ),
                ),
                icon: const Icon(Icons.info_outline_rounded),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (state.status == ModelInstallStatus.downloading ||
              state.status == ModelInstallStatus.queued) ...<Widget>[
            LinearProgressIndicator(
              value: state.progress > 0 ? state.progress : null,
            ),
            const SizedBox(height: 8),
          ],
          Row(
            children: <Widget>[
              Expanded(child: Text(_statusName(context, state))),
              ..._actions(context),
            ],
          ),
        ],
      ),
    ),
  );

  List<Widget> _actions(BuildContext context) {
    switch (state.status) {
      case ModelInstallStatus.notInstalled:
      case ModelInstallStatus.updateAvailable:
      case ModelInstallStatus.failed:
        return <Widget>[
          FilledButton.tonalIcon(
            onPressed: _install,
            icon: const Icon(Icons.download_rounded),
            label: Text(
              state.status == ModelInstallStatus.failed
                  ? context.l10n.retry
                  : context.l10n.download,
            ),
          ),
        ];
      case ModelInstallStatus.downloading:
      case ModelInstallStatus.queued:
        return <Widget>[
          IconButton(
            tooltip: context.l10n.pause,
            onPressed: () => manager.pause(state.model.id),
            icon: const Icon(Icons.pause_rounded),
          ),
          IconButton(
            tooltip: context.l10n.cancel,
            onPressed: () => manager.cancel(state.model.id),
            icon: const Icon(Icons.close_rounded),
          ),
        ];
      case ModelInstallStatus.paused:
        return <Widget>[
          FilledButton.tonalIcon(
            onPressed: () => manager.resume(state.model.id),
            icon: const Icon(Icons.play_arrow_rounded),
            label: Text(context.l10n.resume),
          ),
        ];
      case ModelInstallStatus.installed:
        return <Widget>[
          IconButton(
            tooltip: context.l10n.delete,
            onPressed: () => _delete(context),
            icon: const Icon(Icons.delete_outline_rounded),
          ),
        ];
      case ModelInstallStatus.bundled:
      case ModelInstallStatus.held:
      case ModelInstallStatus.blocked:
      case ModelInstallStatus.comingSoon:
        return const <Widget>[];
    }
  }
}

class _StatusIcon extends StatelessWidget {
  const _StatusIcon({required this.status});

  final ModelInstallStatus status;

  @override
  Widget build(BuildContext context) {
    final (IconData, Color) appearance = switch (status) {
      ModelInstallStatus.bundled || ModelInstallStatus.installed => (
        Icons.check_circle_rounded,
        Colors.green,
      ),
      ModelInstallStatus.downloading || ModelInstallStatus.queued => (
        Icons.downloading_rounded,
        Theme.of(context).colorScheme.primary,
      ),
      ModelInstallStatus.failed => (
        Icons.error_outline_rounded,
        Theme.of(context).colorScheme.error,
      ),
      ModelInstallStatus.blocked => (
        Icons.block_rounded,
        Theme.of(context).colorScheme.error,
      ),
      _ => (Icons.cloud_download_outlined, Theme.of(context).colorScheme.primary),
    };
    return Icon(appearance.$1, color: appearance.$2, size: 28);
  }
}

class _EmptyFilter extends StatelessWidget {
  const _EmptyFilter({required this.onClear});

  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 48),
    child: Column(
      children: <Widget>[
        const Icon(Icons.filter_alt_off_rounded, size: 48),
        const SizedBox(height: 12),
        Text(context.l10n.noModelsFilter),
        const SizedBox(height: 12),
        TextButton(onPressed: onClear, child: Text(context.l10n.clearFilters)),
      ],
    ),
  );
}

String _packName(BuildContext context, ModelPack pack) => switch (pack) {
  ModelPack.starter => context.l10n.packStarter,
  ModelPack.pro => context.l10n.packPro,
  ModelPack.face => context.l10n.packFace,
  ModelPack.ultra => context.l10n.packUltra,
  ModelPack.community => context.l10n.packCommunity,
};

String _speedName(BuildContext context, ModelSpeed speed) => switch (speed) {
  ModelSpeed.veryFast => context.l10n.speedVeryFast,
  ModelSpeed.fast => context.l10n.speedFast,
  ModelSpeed.medium => context.l10n.speedMedium,
  ModelSpeed.slow => context.l10n.speedSlow,
  ModelSpeed.verySlow => context.l10n.speedVerySlow,
};

String _statusName(BuildContext context, ModelRuntimeState state) =>
    switch (state.status) {
      ModelInstallStatus.bundled => context.l10n.modelBundled,
      ModelInstallStatus.notInstalled => context.l10n.modelNotInstalled,
      ModelInstallStatus.queued => context.l10n.queued,
      ModelInstallStatus.downloading => context.l10n.downloading,
      ModelInstallStatus.paused => context.l10n.downloadPaused,
      ModelInstallStatus.installed => context.l10n.installed,
      ModelInstallStatus.updateAvailable => context.l10n.updateAvailable,
      ModelInstallStatus.failed => context.l10n.modelDownloadFailed,
      ModelInstallStatus.held => context.l10n.underReview,
      ModelInstallStatus.blocked => context.l10n.unavailable,
      ModelInstallStatus.comingSoon => context.l10n.comingSoon,
    };

String _megabytes(int bytes) => '${(bytes / 1000000).toStringAsFixed(1)} MB';
