import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../../core/models.dart';
import '../../../services/job_manager/processing_platform_service.dart';
import '../../../services/settings_service.dart';
import '../../../state/app_controller.dart';
import '../../widgets/core_widgets.dart';

class ProcessingScreen extends StatefulWidget {
  const ProcessingScreen({required this.controller, required this.settings, super.key});
  final AppController controller;
  final SettingsService settings;

  @override
  State<ProcessingScreen> createState() => _ProcessingScreenState();
}

class _ProcessingScreenState extends State<ProcessingScreen> {
  final Set<int> _hapticMarks = <int>{};
  final ProcessingPlatformService _platform = const ProcessingPlatformService();
  int _lastNotificationPercent = -1;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _platform.start(keepAwake: widget.settings.keepAwake);
      await widget.controller.startJob(onComplete: () async {
        await _platform.stop();
        if (mounted) context.go('/result');
      });
    });
  }

  Future<void> _confirmCancel() async {
    final bool? cancel = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: Text(context.l10n.cancelQuestion),
        content: Text(context.l10n.cancelBody),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(context.l10n.keepWorking)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(context.l10n.cancel)),
        ],
      ),
    );
    if (cancel ?? false) {
      await _platform.stop();
      await widget.controller.cancelJob();
      if (mounted) context.go('/config');
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: widget.controller,
        builder: (BuildContext context, Widget? child) {
          final ProgressEvent? progress = widget.controller.progress;
          final double fraction = progress?.fraction.clamp(0, 1) ?? 0;
          final int percent = (fraction * 100).round();
          if (percent != _lastNotificationPercent) {
            _lastNotificationPercent = percent;
            unawaited(_platform.update(fraction));
          }
          if (widget.settings.haptics) {
            for (final int mark in <int>[25, 50, 75]) {
              if (percent >= mark && _hapticMarks.add(mark)) HapticFeedback.selectionClick();
            }
          }
          final ImageSelection? image = widget.controller.selectedImage;
          return Scaffold(
            appBar: AppBar(title: Text(context.l10n.enhancingDetails)),
            body: SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  children: <Widget>[
                    if (image != null)
                      Expanded(
                        child: Stack(
                          fit: StackFit.expand,
                          children: <Widget>[
                            Opacity(opacity: 0.25, child: ImagePreview(path: image.path)),
                            ClipRect(
                              clipper: _ProgressClipper(fraction),
                              child: ImagePreview(path: image.path),
                            ),
                          ],
                        ),
                      ),
                    const SizedBox(height: 24),
                    Text('$percent%', style: Theme.of(context).textTheme.displayMedium?.copyWith(fontWeight: FontWeight.w700)),
                    Text(
                      progress == null
                          ? context.l10n.estimating
                          : context.l10n.tileCounter(progress.completedTiles, progress.totalTiles),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      progress?.eta == null
                          ? context.l10n.estimating
                          : context.l10n.etaSeconds(progress!.eta!.inSeconds),
                    ),
                    const SizedBox(height: 12),
                    LinearProgressIndicator(value: fraction, borderRadius: BorderRadius.circular(8)),
                    const SizedBox(height: 16),
                    Text(context.l10n.tip),
                    if (widget.controller.failure != null) ...<Widget>[
                      const SizedBox(height: 12),
                      Text(context.l10n.processingError),
                      FilledButton(onPressed: () => setState(() {}), child: Text(context.l10n.safeRetry)),
                    ],
                    const SizedBox(height: 16),
                    Row(
                      children: <Widget>[
                        Expanded(child: OutlinedButton(onPressed: _confirmCancel, child: Text(context.l10n.cancel))),
                        const SizedBox(width: 12),
                        Expanded(child: FilledButton(onPressed: () => context.go('/home'), child: Text(context.l10n.continueBackground))),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      );
}

class _ProgressClipper extends CustomClipper<Rect> {
  const _ProgressClipper(this.fraction);
  final double fraction;

  @override
  Rect getClip(Size size) => Rect.fromLTWH(0, 0, size.width, size.height * fraction);

  @override
  bool shouldReclip(_ProgressClipper oldClipper) => oldClipper.fraction != fraction;
}
