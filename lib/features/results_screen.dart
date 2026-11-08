/// Results: before/after comparison slider with honest, measured
/// outcome facts. Large images use resolution-capped thumbnails to
/// avoid unnecessary memory duplication.
library;

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/providers.dart';
import '../core/theme.dart';
import '../domain/models.dart';

class ResultsScreen extends ConsumerStatefulWidget {
  const ResultsScreen({super.key});
  @override
  ConsumerState<ResultsScreen> createState() => _ResultsScreenState();
}

class _ResultsScreenState extends ConsumerState<ResultsScreen> {
  double _split = 0.5;

  @override
  Widget build(BuildContext context) {
    final recent = ref.watch(recentResultsProvider);
    final session = ref.watch(importSessionProvider);
    final result = recent.isNotEmpty ? recent.first : null;
    final scheme = Theme.of(context).colorScheme;

    if (result == null || session == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Results')),
        body: const Center(child: Text('No completed results in this session.')),
      );
    }

    final output = File(result.outputPath);
    final source = session.file;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Result'),
        actions: [
          IconButton(
            icon: const Icon(Icons.home_outlined),
            tooltip: 'Home',
            onPressed: () => context.go('/home'),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(RsSpacing.md),
        children: [
          AspectRatio(
            aspectRatio: (result.outputWidth / result.outputHeight)
                .clamp(0.5, 2.0),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(RsRadius.card),
              child: LayoutBuilder(builder: (context, constraints) {
                final width = constraints.maxWidth;
                return Stack(
                  fit: StackFit.expand,
                  children: [
                    // After (full)
                    Image.file(output,
                        fit: BoxFit.contain,
                        cacheWidth: 1400,
                        errorBuilder: (_, _, _) => ColoredBox(
                            color: scheme.surfaceContainerHighest,
                            child: const Center(
                                child: Text('Output file not found')))),
                    // Before (clipped to the left of the divider)
                    ClipRect(
                      clipper: _LeftClip(width * _split),
                      child: Image.file(source,
                          fit: BoxFit.contain,
                          cacheWidth: 1400,
                          errorBuilder: (_, _, _) => const SizedBox
                              .shrink()),
                    ),
                    // Divider handle
                    Positioned(
                      left: (width * _split - 1).clamp(0, width - 2),
                      top: 0,
                      bottom: 0,
                      child: Container(width: 2, color: Colors.white),
                    ),
                    Positioned(
                      left: (width * _split - 18).clamp(0, width - 36),
                      top: constraints.maxHeight / 2 - 18,
                      child: GestureDetector(
                        onPanUpdate: (details) {
                          setState(() {
                            _split =
                                (_split + details.delta.dx / width).clamp(
                                    0.02, 0.98);
                          });
                        },
                        child: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: Colors.white,
                            shape: BoxShape.circle,
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.25),
                                blurRadius: 8,
                              ),
                            ],
                          ),
                          child: const Icon(Icons.compare_arrows,
                              size: 20, color: Colors.black87),
                        ),
                      ),
                    ),
                    // Labels
                    Positioned(
                      left: 10,
                      top: 8,
                      child: _tag('BEFORE · ${result.inputWidth}×${result.inputHeight}'),
                    ),
                    Positioned(
                      right: 10,
                      top: 8,
                      child: _tag('AFTER · ${result.outputWidth}×${result.outputHeight}'),
                    ),
                  ],
                );
              }),
            ),
          ),
          const SizedBox(height: RsSpacing.md),
          PaperCard(
            child: Column(
              children: [
                _fact('Model', _modelName(result.modelId)),
                _fact('Mode', result.mode == ProcessingMode.upscale
                    ? 'Upscale'
                    : 'Super Resolution'),
                _fact('Backend', result.backend),
                _fact('Dimensions',
                    '${result.inputWidth}×${result.inputHeight} → '
                    '${result.outputWidth}×${result.outputHeight} '
                    '(${(result.outputWidth / result.inputWidth).toStringAsFixed(1)}×)'),
                _fact('File size',
                    '${(result.inputFileBytes / 1024).toStringAsFixed(0)} KB → '
                    '${(result.outputFileBytes / 1024 / 1024).toStringAsFixed(1)} MB'),
                _fact('Format',
                    result.outputFormat == OutputFormat.png
                        ? 'PNG (lossless)'
                        : 'JPEG'),
                _fact('Processing time',
                    '${(result.duration.inMilliseconds / 1000.0).toStringAsFixed(1)} s'),
                if (result.attempt > 1)
                  _fact('Recovery',
                      'completed after ${result.attempt} attempts '
                      '(tile size reduced)'),
              ],
            ),
          ),
          const SizedBox(height: RsSpacing.md),
          Row(
            children: [
              Expanded(
                child: PaperButton(
                  label: 'Done',
                  icon: Icons.check_outlined,
                  onPressed: () {
                    ref.read(importSessionProvider.notifier).state = null;
                    context.go('/home');
                  },
                ),
              ),
              const SizedBox(width: RsSpacing.md),
              Expanded(
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                        borderRadius:
                            BorderRadius.circular(RsRadius.button)),
                  ),
                  onPressed: () => _share(result),
                  icon: const Icon(Icons.share_outlined),
                  label: const Text('Share'),
                ),
              ),
            ],
          ),
          const SizedBox(height: RsSpacing.sm),
          Text(
            result.savedToGallery
                ? 'Saved to app storage and Pictures/RealScale-Pro'
                : result.galleryNote.isNotEmpty
                    ? 'Saved to: ${result.outputPath}\n'
                        'Gallery export failed: ${result.galleryNote}'
                    : 'Saved to: ${result.outputPath}',
            textAlign: TextAlign.center,
            style: RsType.caption.copyWith(color: scheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }

  Future<void> _share(JobResult result) async {
    final storage = ref.read(storageProvider);
    final ok = await storage.shareResult(
      mimeType: result.outputFormat == OutputFormat.png
          ? 'image/png'
          : 'image/jpeg',
    );
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text(
            'Sharing is unavailable for this result (no gallery export).'),
      ));
    }
  }

  Widget _tag(String text) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(6),
        ),
        child: Text(text,
            style: RsType.caption.copyWith(color: Colors.white)),
      );

  Widget _fact(String label, String value) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(label,
                style: RsType.caption
                    .copyWith(color: scheme.onSurfaceVariant)),
          ),
          Expanded(child: Text(value, style: RsType.body)),
        ],
      ),
    );
  }

  String _modelName(String id) => switch (id) {
        'realesr-animevideov3-x4' => 'RealESR AnimeVideo v3 ×4',
        'realesrgan-x4plus' => 'RealESRGAN ×4+',
        'realesrgan-x4plus-anime' => 'RealESRGAN ×4+ Anime 6B',
        _ => id,
      };
}

class _LeftClip extends CustomClipper<Rect> {
  _LeftClip(this.width);
  final double width;
  @override
  Rect getClip(Size size) => Rect.fromLTWH(0, 0, width, size.height);
  @override
  bool shouldReclip(_LeftClip oldClip) => oldClip.width != width;
}
