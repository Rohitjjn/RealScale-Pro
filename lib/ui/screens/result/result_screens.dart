import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';

import '../../../core/models.dart';
import '../../../state/app_controller.dart';
import '../../widgets/core_widgets.dart';

class ResultScreen extends StatefulWidget {
  const ResultScreen({required this.controller, super.key});
  final AppController controller;

  @override
  State<ResultScreen> createState() => _ResultScreenState();
}

class _ResultScreenState extends State<ResultScreen> {
  double _position = 0.5;
  bool _holding = false;

  @override
  Widget build(BuildContext context) {
    final UpscaleRecord? result = widget.controller.latestResult;
    if (result == null) {
      return Scaffold(body: Center(child: Text(context.l10n.emptyUpscales)));
    }
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.result),
        actions: <Widget>[
          PopupMenuButton<String>(
            onSelected: (String value) async {
              if (value == 'delete') {
                await widget.controller.deleteRecord(result);
                if (context.mounted) context.go('/home');
              }
            },
            itemBuilder: (BuildContext context) => <PopupMenuEntry<String>>[
              PopupMenuItem<String>(value: 'delete', child: Text(context.l10n.deleteResult)),
            ],
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          AspectRatio(
            aspectRatio: result.width / result.height,
            child: GestureDetector(
              onLongPressStart: (_) => setState(() => _holding = true),
              onLongPressEnd: (_) => setState(() => _holding = false),
              child: InteractiveViewer(
                maxScale: 6,
                child: LayoutBuilder(
                  builder: (BuildContext context, BoxConstraints constraints) => Stack(
                    fit: StackFit.expand,
                    children: <Widget>[
                      Image.file(File(result.outputPath), fit: BoxFit.contain),
                      ClipRect(
                        clipper: _HorizontalClipper(_holding ? 1 : _position),
                        child: Image.file(File(result.originalPath), fit: BoxFit.contain),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          Slider(
            value: _position,
            onChanged: (double value) => setState(() => _position = value),
            semanticFormatterCallback: (double value) => '${(value * 100).round()}%',
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: <Widget>[Text(context.l10n.before), Text(context.l10n.after)],
          ),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: <Widget>[
              InfoChip(icon: Icons.aspect_ratio_rounded, label: '${result.width}×${result.height}'),
              InfoChip(icon: Icons.zoom_out_map_rounded, label: '${result.scale}×'),
              InfoChip(icon: Icons.timer_outlined, label: '${result.elapsed.inSeconds}s'),
              InfoChip(icon: Icons.folder_rounded, label: context.l10n.savedTo),
            ],
          ),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: () => SharePlus.instance.share(ShareParams(files: <XFile>[XFile(result.outputPath)])),
            icon: const Icon(Icons.share_rounded),
            label: Text(context.l10n.share),
          ),
          OutlinedButton(onPressed: () => context.go('/config'), child: Text(context.l10n.upscaleAgain)),
          TextButton(onPressed: () => context.go('/home'), child: Text(context.l10n.newImage)),
        ],
      ),
    );
  }
}

class MyUpscalesScreen extends StatelessWidget {
  const MyUpscalesScreen({required this.controller, super.key});
  final AppController controller;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: controller,
        builder: (BuildContext context, Widget? child) => Scaffold(
          appBar: AppBar(title: Text(context.l10n.myUpscales)),
          body: controller.history.isEmpty
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: <Widget>[
                        const Icon(Icons.auto_awesome_motion_rounded, size: 72),
                        const SizedBox(height: 16),
                        Text(context.l10n.emptyUpscales, textAlign: TextAlign.center),
                        Text(context.l10n.emptyUpscalesBody, textAlign: TextAlign.center),
                      ],
                    ),
                  ),
                )
              : GridView.builder(
                  padding: const EdgeInsets.all(12),
                  gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                    maxCrossAxisExtent: 260,
                    mainAxisSpacing: 10,
                    crossAxisSpacing: 10,
                  ),
                  itemCount: controller.history.length,
                  itemBuilder: (BuildContext context, int index) {
                    final UpscaleRecord record = controller.history[index];
                    return Card(
                      child: InkWell(
                        onTap: () {
                          controller.openRecord(record);
                          context.go('/result');
                        },
                        child: Image.file(File(record.outputPath), fit: BoxFit.cover),
                      ),
                    );
                  },
                ),
        ),
      );
}

class _HorizontalClipper extends CustomClipper<Rect> {
  const _HorizontalClipper(this.fraction);
  final double fraction;

  @override
  Rect getClip(Size size) => Rect.fromLTWH(0, 0, size.width * fraction, size.height);

  @override
  bool shouldReclip(_HorizontalClipper oldClipper) => oldClipper.fraction != fraction;
}
