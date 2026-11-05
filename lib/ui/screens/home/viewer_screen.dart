import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:photo_view/photo_view.dart';
import 'package:share_plus/share_plus.dart';

import '../../../app/app.dart';
import '../../../engine/upscale_engine.dart' show SettingLevel;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/cards.dart' show TabularFigures;
import '../../../ui/widgets/common.dart';

/// Full-screen viewer (task 1.9): swipe paging, pinch zoom, tap-to-hide
/// controls, swipe-down close, top info bar, bottom actions.
class ViewerScreen extends ConsumerStatefulWidget {
  const ViewerScreen({super.key, required this.initialIndex});

  final int initialIndex;

  @override
  ConsumerState<ViewerScreen> createState() => _ViewerScreenState();
}

class _ViewerScreenState extends ConsumerState<ViewerScreen> {
  late final PageController _pageController = PageController(
    initialPage: widget.initialIndex,
  );
  bool _controlsVisible = true;
  int _index = 0;

  @override
  void initState() {
    super.initState();
    _index = widget.initialIndex;
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final library = ref.watch(libraryProvider);
    if (library.status != LibraryStatus.ready || library.photos.isEmpty) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final photos = library.photos;
    final l = AppLocalizations.of(context)!;
    final entry = photos[_index.clamp(0, photos.length - 1)];

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          GestureDetector(
            onTap: () => setState(() => _controlsVisible = !_controlsVisible),
            child: NotificationListener<OverscrollNotification>(
              onNotification: (n) {
                // Swipe-down close.
                if (n.overscroll < -40) {
                  if (context.canPop()) context.pop();
                }
                return false;
              },
              child: PageView.builder(
                controller: _pageController,
                itemCount: photos.length,
                onPageChanged: (i) => setState(() => _index = i),
                itemBuilder: (context, i) => _ViewerPage(entry: photos[i]),
              ),
            ),
          ),
          AnimatedPositioned(
            duration: RsMotion.micro,
            top: _controlsVisible ? 0 : -100,
            left: 0,
            right: 0,
            child: Container(
              color: Colors.black.withValues(alpha: 0.55),
              padding: EdgeInsets.only(
                top: MediaQuery.paddingOf(context).top + 4,
                left: RsSpacing.l,
                right: RsSpacing.l,
                bottom: 10,
              ),
              child: Row(
                children: [
                  IconButton(
                    icon: const Icon(
                      Icons.arrow_back_rounded,
                      color: Colors.white,
                    ),
                    onPressed: () => context.pop(),
                  ),
                  Expanded(
                    child: Text(
                      '${entry.asset.width} × ${entry.asset.height}',
                      style: RsType.bodyMedium.copyWith(
                        color: Colors.white,
                        fontFeatures: const [TabularFigures()],
                      ),
                    ),
                  ),
                  InfoChip(
                    label: l.viewerModeChipUpscale,
                    tone: InfoChipTone.brand,
                  ),
                ],
              ),
            ),
          ),
          AnimatedPositioned(
            duration: RsMotion.micro,
            bottom: _controlsVisible ? 0 : -120,
            left: 0,
            right: 0,
            child: Container(
              color: Colors.black.withValues(alpha: 0.55),
              padding: EdgeInsets.only(
                bottom: MediaQuery.paddingOf(context).bottom + 12,
                left: RsSpacing.xl,
                right: RsSpacing.xl,
                top: 12,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: _ViewerAction(
                      icon: Icons.auto_awesome_rounded,
                      label: l.viewerUpscale,
                      onTap: _upscaleThis,
                    ),
                  ),
                  Expanded(
                    child: _ViewerAction(
                      icon: Icons.info_outline_rounded,
                      label: l.viewerDetails,
                      onTap: () => AppBottomSheet.show(
                        context,
                        title: l.viewerDetails,
                        child: _DetailsSheet(entry: entry),
                      ),
                    ),
                  ),
                  Expanded(
                    child: _ViewerAction(
                      icon: Icons.ios_share_rounded,
                      label: l.viewerShare,
                      onTap: _share,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _upscaleThis() async {
    final library = ref.read(libraryProvider);
    final entry = library.photos[_index];
    final file = await entry.asset.originFile;
    if (file == null || !mounted) return;
    final bytes = await File(file.path).readAsBytes();
    await ref
        .read(configProvider.notifier)
        .loadFrom(
          path: file.path,
          bytes: bytes,
          level: SettingLevel.defaultLevel,
        );
    if (!mounted) return;
    unawaited(context.push(Routes.config));
  }

  Future<void> _share() async {
    final library = ref.read(libraryProvider);
    final entry = library.photos[_index];
    final file = await entry.asset.originFile;
    if (file != null) {
      await SharePlus.instance.share(ShareParams(files: [XFile(file.path)]));
    }
  }
}

class _ViewerPage extends ConsumerWidget {
  const _ViewerPage({required this.entry});

  final PhotoEntry entry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return FutureBuilder<File?>(
      future: entry.asset.originFile,
      builder: (context, snapshot) {
        final file = snapshot.data;
        if (file == null) {
          return const Center(child: CircularProgressIndicator());
        }
        return PhotoView(
          imageProvider: FileImage(file),
          minScale: PhotoViewComputedScale.contained * 0.95,
          maxScale: PhotoViewComputedScale.covered * 3,
          backgroundDecoration: const BoxDecoration(color: Colors.black),
        );
      },
    );
  }
}

class _ViewerAction extends StatelessWidget {
  const _ViewerAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: RsRadius.pill,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: Colors.white, size: 22),
            const SizedBox(height: 4),
            Text(label, style: RsType.label.copyWith(color: Colors.white)),
          ],
        ),
      ),
    );
  }
}

class _DetailsSheet extends ConsumerWidget {
  const _DetailsSheet({required this.entry});
  final PhotoEntry entry;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        RsSpacing.xl,
        0,
        RsSpacing.xl,
        RsSpacing.xl,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _DetailRow(
            label: l.detailsResolution,
            value: '${entry.asset.width} × ${entry.asset.height}',
          ),
          _DetailRow(
            label: l.detailsSize,
            value: entry.bytes != null
                ? formatBytes(entry.bytes!.lengthInBytes)
                : '—',
          ),
          _DetailRow(
            label: l.detailsModified,
            value: _fmtDate(entry.asset.modifiedDateTime),
          ),
        ],
      ),
    );
  }

  String _fmtDate(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: RsType.body.copyWith(color: cs.onSurfaceVariant),
            ),
          ),
          Text(
            value,
            style: RsType.bodyMedium.copyWith(
              fontFeatures: const [TabularFigures()],
            ),
          ),
        ],
      ),
    );
  }
}
