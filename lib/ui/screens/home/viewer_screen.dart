import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';

import '../../../state/app_controller.dart';
import '../../widgets/core_widgets.dart';

class ViewerScreen extends StatefulWidget {
  const ViewerScreen({required this.controller, super.key});
  final AppController controller;

  @override
  State<ViewerScreen> createState() => _ViewerScreenState();
}

class _ViewerScreenState extends State<ViewerScreen> {
  bool _controls = true;

  @override
  Widget build(BuildContext context) {
    final selection = widget.controller.selectedImage;
    if (selection == null) {
      return Scaffold(body: Center(child: Text(context.l10n.pickAnother)));
    }
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: _controls
          ? AppBar(
              backgroundColor: Colors.black,
              foregroundColor: Colors.white,
              title: Text(
                '${selection.width}×${selection.height} · ${(selection.bytes / 1048576).toStringAsFixed(1)} MB',
              ),
            )
          : null,
      body: GestureDetector(
        onTap: () => setState(() => _controls = !_controls),
        onVerticalDragEnd: (DragEndDetails details) {
          if ((details.primaryVelocity ?? 0) > 500) context.pop();
        },
        child: Center(
          child: Hero(
            tag: selection.path,
            child: InteractiveViewer(
              minScale: 0.8,
              maxScale: 6,
              child: Image.file(File(selection.path), fit: BoxFit.contain),
            ),
          ),
        ),
      ),
      bottomNavigationBar: _controls
          ? SafeArea(
              child: ColoredBox(
                color: Colors.black,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: <Widget>[
                      Expanded(
                        child: FilledButton.icon(
                          onPressed: () => context.go('/config'),
                          icon: const Icon(Icons.auto_awesome_rounded),
                          label: Text(context.l10n.upscale),
                        ),
                      ),
                      IconButton(
                        color: Colors.white,
                        tooltip: context.l10n.details,
                        onPressed: () => showAppSheet(
                          context,
                          Text(
                            '${selection.name}\n${selection.width}×${selection.height}',
                          ),
                        ),
                        icon: const Icon(Icons.info_outline_rounded),
                      ),
                      IconButton(
                        color: Colors.white,
                        tooltip: context.l10n.share,
                        onPressed: () => SharePlus.instance.share(
                          ShareParams(files: <XFile>[XFile(selection.path)]),
                        ),
                        icon: const Icon(Icons.share_rounded),
                      ),
                      const InfoChip(
                        icon: Icons.auto_awesome_rounded,
                        label: '2×',
                      ),
                    ],
                  ),
                ),
              ),
            )
          : null,
    );
  }
}
