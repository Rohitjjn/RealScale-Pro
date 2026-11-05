import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app.dart';
import '../../../engine/upscale_engine.dart' show SettingLevel;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';

/// "More" import sheet (task 1.10): clipboard paste + system picker.
class MoreSheetScreen extends ConsumerStatefulWidget {
  const MoreSheetScreen({super.key});

  @override
  ConsumerState<MoreSheetScreen> createState() => _MoreSheetScreenState();
}

class _MoreSheetScreenState extends ConsumerState<MoreSheetScreen> {
  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: RsRadius.sheetBorder,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 20),
          Text(l.moreSheetTitle, style: RsType.title),
          const SizedBox(height: RsSpacing.l),
          ListTile(
            leading: const Icon(Icons.content_paste_rounded),
            title: Text(l.moreClipboard),
            onTap: _pasteFromClipboard,
          ),
          ListTile(
            leading: const Icon(Icons.image_outlined),
            title: Text(l.moreSystemPicker),
            onTap: _systemPicker,
          ),
          ListTile(
            leading: const Icon(Icons.close_rounded),
            title: Text(l.moreCancel),
            onTap: () => context.pop(),
          ),
          SizedBox(height: MediaQuery.paddingOf(context).bottom + 8),
        ],
      ),
    );
  }

  Future<void> _pasteFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text;
    if (!mounted) return;
    final l = AppLocalizations.of(context)!;
    if (text != null && File(text).existsSync()) {
      final bytes = await File(text).readAsBytes();
      await _goWith(bytes, path: text);
    } else {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l.moreClipboardEmpty)));
    }
  }

  Future<void> _systemPicker() async {
    final files = await FilePicker.pickFiles(type: FileType.image);
    final file = files.isEmpty ? null : files.first;
    if (file?.path == null || !mounted) return;
    final bytes = await File(file!.path!).readAsBytes();
    await _goWith(bytes, path: file.path!);
  }

  Future<void> _goWith(Uint8List bytes, {required String path}) async {
    await ref
        .read(configProvider.notifier)
        .loadFrom(path: path, bytes: bytes, level: SettingLevel.defaultLevel);
    if (!mounted) return;
    context.pushReplacement(Routes.config);
  }
}
