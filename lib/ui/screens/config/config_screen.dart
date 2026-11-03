import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../../core/models.dart';
import '../../../services/settings_service.dart';
import '../../../state/app_controller.dart';
import '../../widgets/core_widgets.dart';

class ConfigScreen extends StatefulWidget {
  const ConfigScreen({
    required this.controller,
    required this.settings,
    super.key,
  });
  final AppController controller;
  final SettingsService settings;

  @override
  State<ConfigScreen> createState() => _ConfigScreenState();
}

class _ConfigScreenState extends State<ConfigScreen> {
  JobSpec? _plan;

  @override
  void initState() {
    super.initState();
    widget.controller.prepareJob().then((JobSpec? value) {
      if (mounted) setState(() => _plan = value);
    });
  }

  Future<void> _swap() async {
    if (await widget.controller.chooseFile()) {
      final JobSpec? plan = await widget.controller.prepareJob();
      if (mounted) setState(() => _plan = plan);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ImageSelection? image = widget.controller.selectedImage;
    if (image == null) {
      return Scaffold(
        appBar: AppBar(title: Text(context.l10n.configTitle)),
        body: Center(
          child: FilledButton(
            onPressed: _swap,
            child: Text(context.l10n.pickAnother),
          ),
        ),
      );
    }
    final String detection = image.kind == ImageKind.anime
        ? context.l10n.detectedAnime
        : context.l10n.detectedPhoto;
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.configTitle),
        actions: <Widget>[
          IconButton(
            tooltip: context.l10n.swap,
            onPressed: _swap,
            icon: const Icon(Icons.swap_horiz_rounded),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 120),
        children: <Widget>[
          AspectRatio(
            aspectRatio: 16 / 10,
            child: Hero(
              tag: image.path,
              child: ImagePreview(path: image.path),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '${image.width}×${image.height} · ${(image.bytes / 1048576).toStringAsFixed(1)} MB',
          ),
          const SizedBox(height: 20),
          SegmentedButton<int>(
            segments: <ButtonSegment<int>>[
              ButtonSegment<int>(
                value: 0,
                label: Text(context.l10n.defaultLevel),
              ),
              ButtonSegment<int>(
                value: 1,
                label: Text(context.l10n.manualLevel),
                enabled: false,
              ),
              ButtonSegment<int>(
                value: 2,
                label: Text(context.l10n.advancedLevel),
                enabled: false,
              ),
            ],
            selected: const <int>{0},
          ),
          const SizedBox(height: 14),
          Card(
            child: ListTile(
              leading: const Icon(Icons.auto_awesome_rounded),
              title: Text(detection),
              trailing: TextButton(
                onPressed: () =>
                    showAppSheet(context, Text(context.l10n.phaseTwoNotice)),
                child: Text(context.l10n.change),
              ),
            ),
          ),
          Card(
            child: SwitchListTile(
              value: false,
              onChanged: null,
              secondary: const Icon(Icons.face_retouching_natural_rounded),
              title: Text(context.l10n.facesLater),
            ),
          ),
        ],
      ),
      bottomNavigationBar: PrimaryActionBar(
        label: context.l10n.upscale,
        subtitle: context.l10n.outputEstimate(
          image.outputWidth,
          image.outputHeight,
          ((_plan?.estimatedSeconds ?? 60) / 60).ceil(),
        ),
        onPressed: _plan == null
            ? null
            : () {
                if (widget.settings.haptics) HapticFeedback.mediumImpact();
                context.go('/processing');
              },
      ),
    );
  }
}
