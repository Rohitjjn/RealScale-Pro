import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';

import '../../../l10n/app_localizations.dart';
import '../../../services/storage_service/storage_service.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/common.dart';

/// My Upscales grid (task 1.16): open/share/delete with multi-select.
class MyUpscalesScreen extends ConsumerStatefulWidget {
  const MyUpscalesScreen({super.key});

  @override
  ConsumerState<MyUpscalesScreen> createState() => _MyUpscalesScreenState();
}

class _MyUpscalesScreenState extends ConsumerState<MyUpscalesScreen> {
  final Set<String> _selected = {};
  List<UpscaleRecord>? _records;

  @override
  void initState() {
    super.initState();
    Future.microtask(_load);
  }

  Future<void> _load() async {
    final records = await ref.read(servicesProvider).storage.history();
    if (!mounted) return;
    setState(() => _records = records);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final records = _records;

    return Scaffold(
      appBar: AppBar(
        title: Text(l.myUpscalesTitle),
        actions: [
          if (_selected.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.delete_outline_rounded),
              onPressed: _deleteSelected,
            ),
        ],
      ),
      body: records == null
          ? const Center(child: ShimmerBox(height: 200))
          : records.isEmpty
          ? EmptyState(
              icon: Icons.auto_awesome_outlined,
              title: l.myUpscalesEmptyTitle,
              body: l.myUpscalesEmptyBody,
            )
          : GridView.builder(
              padding: const EdgeInsets.all(RsSpacing.m),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                mainAxisSpacing: RsSpacing.m,
                crossAxisSpacing: RsSpacing.m,
                childAspectRatio: 0.82,
              ),
              itemCount: records.length,
              itemBuilder: (context, i) {
                final record = records[i];
                final isSelected = _selected.contains(record.id);
                return _UpscaleTile(
                  record: record,
                  isSelected: isSelected,
                  onLongPress: () => setState(() {
                    isSelected
                        ? _selected.remove(record.id)
                        : _selected.add(record.id);
                  }),
                  onTap: () => setState(() {
                    if (_selected.isEmpty) {
                      _open(record);
                    } else {
                      isSelected
                          ? _selected.remove(record.id)
                          : _selected.add(record.id);
                    }
                  }),
                );
              },
            ),
    );
  }

  void _open(UpscaleRecord record) {
    showDialog<void>(
      context: context,
      builder: (_) => Dialog(
        backgroundColor: Colors.transparent,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ClipRRect(
              borderRadius: RsRadius.cardBorder,
              child: InteractiveViewer(
                maxScale: 4,
                child: Image.file(File(record.savedPath)),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                IconButton.filled(
                  onPressed: () {
                    SharePlus.instance.share(
                      ShareParams(files: [XFile(record.savedPath)]),
                    );
                  },
                  icon: const Icon(Icons.ios_share_rounded),
                ),
                const SizedBox(width: 12),
                IconButton.filledTonal(
                  onPressed: () => context.pop(),
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _deleteSelected() async {
    final l = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.myUpscalesDeleteConfirmTitle),
        content: Text(l.myUpscalesDeleteConfirm(_selected.length)),
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
    if (confirmed != true) return;
    final storage = ref.read(servicesProvider).storage;
    for (final id in _selected) {
      await storage.deleteRecord(id);
    }
    _selected.clear();
    await _load();
  }
}

class _UpscaleTile extends StatelessWidget {
  const _UpscaleTile({
    required this.record,
    required this.isSelected,
    required this.onTap,
    required this.onLongPress,
  });

  final UpscaleRecord record;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: ClipRRect(
        borderRadius: RsRadius.cardBorder,
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.file(File(record.savedPath), fit: BoxFit.cover),
            Positioned(
              bottom: 8,
              left: 8,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: Colors.black.withValues(alpha: 0.55),
                  borderRadius: RsRadius.pill,
                ),
                child: Text(
                  '${record.width}×${record.height}',
                  style: RsType.label.copyWith(
                    color: Colors.white,
                    fontSize: 10,
                  ),
                ),
              ),
            ),
            if (isSelected)
              Container(
                color: cs.primary.withValues(alpha: 0.35),
                child: Center(
                  child: Icon(
                    Icons.check_circle_rounded,
                    color: cs.primary,
                    size: 40,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
