/// Home: mode selection, real image import + inspection, plan summary,
/// recent results and honest device/engine status.
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../core/providers.dart';
import '../core/theme.dart';
import '../domain/models.dart';
import '../processing/pipeline_planner.dart';
import '../services/image_service.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});
  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  bool _importing = false;

  Future<void> _pickImage(ImageSource source) async {
    if (_importing) {
      return;
    }
    setState(() => _importing = true);
    try {
      final picker = ImagePicker();
      final picked = await picker.pickImage(
          source: source, imageQuality: 100);
      if (picked == null) {
        return;
      }
      final file = File(picked.path);

      final imageService = ref.read(imageServiceProvider);
      final metadata = await imageService.inspect(file);

      if (!mounted) {
        return;
      }
      ref.read(importSessionProvider.notifier).state =
          ImportSession(file: file, metadata: metadata);

      await _confirmAndPlan();
    } on ImageInspectException catch (e) {
      _showSnack(e.message, error: true);
    } catch (e) {
      _showSnack('Could not import the image: $e', error: true);
    } finally {
      if (mounted) {
        setState(() => _importing = false);
      }
    }
  }

  Future<void> _confirmAndPlan() async {
    final session = ref.read(importSessionProvider);
    if (session == null) {
      return;
    }
    final device = await ref.read(deviceProfileProvider.future);
    final mode = ref.read(processingModeProvider);
    final registry = await ref.read(modelRegistryProvider.future);
    final planner = const PipelinePlanner();
    final model = planner.chooseModel(registry, device, mode);
    final plan = planner.plan(
      image: session.metadata,
      device: device,
      mode: mode,
      model: model,
      jpegQuality: ref.read(settingsProvider).jpegQuality,
    );

    if (!mounted) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _PlanDialog(plan: plan, model: model),
    );
    if (confirmed == true && mounted) {
      context.go('/processing');
    }
  }

  void _showSnack(String message, {bool error = false}) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(message),
      behavior: SnackBarBehavior.floating,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final deviceAsync = ref.watch(deviceProfileProvider);
    final engineAsync = ref.watch(engineInfoProvider);
    final session = ref.watch(importSessionProvider);
    final recent = ref.watch(recentResultsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('RealScale Pro'),
        actions: [
          IconButton(
              icon: const Icon(Icons.grid_view_outlined),
              tooltip: 'Models',
              onPressed: () => context.push('/models')),
          IconButton(
              icon: const Icon(Icons.settings_outlined),
              tooltip: 'Settings',
              onPressed: () => context.push('/settings')),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(RsSpacing.md),
        children: [
          // ---- status strip (honest device + engine info) ----
          PaperCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                deviceAsync.when(
                  data: (d) => Row(
                    children: [
                      PaperChip(
                          label: d.tier.label,
                          icon: Icons.memory,
                          active: true),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '${d.cpuCores} cores · '
                          '${d.totalMemoryMb != null ? '${d.totalMemoryMb} MB RAM' : 'RAM not readable'} · '
                          '${d.platform}',
                          style: RsType.caption
                              .copyWith(color: scheme.onSurfaceVariant),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                  loading: () => const Text('Profiling device…',
                      style: RsType.caption),
                  error: (e, _) => Text('Device profile unavailable',
                      style: RsType.caption
                          .copyWith(color: scheme.error)),
                ),
                const SizedBox(height: 8),
                engineAsync.when(
                  data: (info) => info == null
                      ? Row(children: [
                          Icon(Icons.warning_amber_rounded,
                              size: 15, color: scheme.error),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              'Native engine unavailable — AI upscaling '
                              'requires the Android app.',
                              style: RsType.caption
                                  .copyWith(color: scheme.error),
                            ),
                          ),
                        ])
                      : Text(
                          'Engine ready: ${info.threadCount} threads, '
                          'fp16=${info.fp16Storage == 1}, '
                          'GPU=${info.gpuAvailable == 1 ? 'yes' : 'no (CPU)'}',
                          style: RsType.caption
                              .copyWith(color: scheme.onSurfaceVariant)),
                  loading: () => const Text('Probing engine…',
                      style: RsType.caption),
                  error: (e, _) => Text('Engine probe failed: $e',
                      style: RsType.caption.copyWith(color: scheme.error)),
                ),
              ],
            ),
          ),
          const SizedBox(height: RsSpacing.md),

          // ---- mode selector ----
          Text('Choose a mode', style: RsType.title),
          const SizedBox(height: RsSpacing.sm),
          _ModeCard(
            selected:
                ref.watch(processingModeProvider) == ProcessingMode.upscale,
            icon: Icons.photo_size_select_large_outlined,
            title: 'Upscale Mode',
            subtitle: 'Practical results. JPEG quality 90–96, controlled '
                'output optimization, original preserved.',
            onTap: () => ref
                .read(processingModeProvider.notifier)
                .state = ProcessingMode.upscale,
          ),
          const SizedBox(height: RsSpacing.sm),
          _ModeCard(
            selected: ref.watch(processingModeProvider) ==
                ProcessingMode.superResolution,
            icon: Icons.auto_awesome_outlined,
            title: 'Super Resolution Mode',
            subtitle: 'Quality first. Lossless processing, tiling for large '
                'images, never a silent downsample.',
            onTap: () => ref
                .read(processingModeProvider.notifier)
                .state = ProcessingMode.superResolution,
          ),
          const SizedBox(height: RsSpacing.lg),

          // ---- import actions ----
          PaperButton(
            label: _importing
                ? 'Importing…'
                : (session != null ? 'Choose another image' : 'Import an image'),
            icon: Icons.add_photo_alternate_outlined,
            onPressed: _importing ? null : () => _pickImage(ImageSource.gallery),
          ),
          const SizedBox(height: RsSpacing.sm),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(RsRadius.button)),
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
            ),
            onPressed: _importing ? null : () => _pickImage(ImageSource.camera),
            icon: const Icon(Icons.photo_camera_outlined, size: 17),
            label: const Text('Take a photo'),
          ),

          if (session != null) ...[
            const SizedBox(height: RsSpacing.lg),
            Text('Ready to process', style: RsType.title),
            const SizedBox(height: RsSpacing.sm),
            PaperCard(
              child: Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.file(session.file,
                        width: 64,
                        height: 64,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => Container(
                            width: 64,
                            height: 64,
                            color: scheme.surfaceContainerHighest,
                            child: const Icon(Icons.image_outlined))),
                  ),
                  const SizedBox(width: RsSpacing.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${session.metadata.width} × '
                          '${session.metadata.height} · '
                          '${session.metadata.format}',
                          style: RsType.title,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${(session.metadata.fileSizeBytes / 1024).toStringAsFixed(0)} KB · '
                          '${session.metadata.megapixels} MP'
                          '${session.metadata.needsOrientationFix ? ' · EXIF orientation will be applied' : ''}',
                          style: RsType.caption
                              .copyWith(color: scheme.onSurfaceVariant),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],

          if (recent.isNotEmpty) ...[
            const SizedBox(height: RsSpacing.lg),
            Text('Recent results', style: RsType.title),
            const SizedBox(height: RsSpacing.sm),
            for (final r in recent.take(5))
              PaperCard(
                onTap: () => context.push('/results'),
                child: Row(
                  children: [
                    Icon(Icons.check_circle_outline,
                        color: scheme.primary, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        '${r.inputWidth}×${r.inputHeight} → '
                        '${r.outputWidth}×${r.outputHeight} · '
                        '${r.duration.inSeconds}s · '
                        '${(r.outputFileBytes / 1024 / 1024).toStringAsFixed(1)} MB',
                        style: RsType.body,
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class _ModeCard extends StatelessWidget {
  const _ModeCard({
    required this.selected,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });
  final bool selected;
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return PaperCard(
      onTap: onTap,
      borderColor: selected ? scheme.primary : null,
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: selected
                  ? scheme.primary
                  : scheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon,
                size: 21,
                color: selected ? scheme.onPrimary : scheme.onSurfaceVariant),
          ),
          const SizedBox(width: RsSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: RsType.title),
                const SizedBox(height: 2),
                Text(subtitle,
                    style: RsType.caption
                        .copyWith(color: scheme.onSurfaceVariant)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _PlanDialog extends StatelessWidget {
  const _PlanDialog({required this.plan, required this.model});
  final PipelinePlan plan;
  final ModelInfo model;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AlertDialog(
      title: Text('Processing plan', style: RsType.headline),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _row(context, 'Model', model.displayName),
            _row(context, 'Scale', '${plan.scale}x'),
            _row(context, 'Output',
                '${plan.estimatedOutputWidth} x ${plan.estimatedOutputHeight}'),
            _row(
                context,
                'Format',
                plan.outputFormat == OutputFormat.png
                    ? 'PNG (lossless)'
                    : 'JPEG q${plan.jpegQuality}'),
            _row(context, 'Tiles', '${plan.tileSize}px core, '
                '${plan.tileOverlap}px overlap'),
            _row(context, 'Est. peak RAM', '~${plan.estimatedPeakRamMb} MB'),
            for (final w in plan.warnings)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.warning_amber_rounded,
                        size: 16, color: scheme.error),
                    const SizedBox(width: 6),
                    Expanded(
                        child: Text(w,
                            style: RsType.caption
                                .copyWith(color: scheme.error))),
                  ],
                ),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel')),
        FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Start processing')),
      ],
    );
  }

  Widget _row(BuildContext context, String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 110,
            child: Text(label,
                style: RsType.caption
                    .copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ),
          Expanded(child: Text(value, style: RsType.body)),
        ],
      ),
    );
  }
}
