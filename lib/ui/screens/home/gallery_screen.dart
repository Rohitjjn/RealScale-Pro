import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app.dart';
import '../../../engine/upscale_engine.dart' show SettingLevel;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/common.dart';

/// In-app gallery browser ("Gallery" tile + "See all").
class GalleryScreen extends ConsumerStatefulWidget {
  const GalleryScreen({super.key});

  @override
  ConsumerState<GalleryScreen> createState() => _GalleryScreenState();
}

class _GalleryScreenState extends ConsumerState<GalleryScreen> {
  @override
  void initState() {
    super.initState();
    unawaited(
      Future.microtask(() async {
        await ref.read(libraryProvider.notifier).load(limit: 300);
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final library = ref.watch(libraryProvider);
    return Scaffold(
      appBar: AppBar(title: Text(l.galleryTitle)),
      body: switch (library.status) {
        LibraryStatus.loading => const Center(child: ShimmerBox(height: 200)),
        LibraryStatus.denied => EmptyState(
          icon: Icons.lock_outline_rounded,
          title: l.galleryPermissionNeeded,
          body: l.permissionPhotosBody,
          actionLabel: l.galleryAllow,
          onAction: () async {
            await ref.read(servicesProvider).permissions.requestPhotos();
            if (context.mounted) {
              unawaited(ref.read(libraryProvider.notifier).load(limit: 300));
            }
          },
        ),
        LibraryStatus.empty => EmptyState(
          icon: Icons.photo_outlined,
          title: l.galleryEmpty,
          body: '',
        ),
        LibraryStatus.ready => GridView.builder(
          padding: const EdgeInsets.all(RsSpacing.m),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 3,
            mainAxisSpacing: RsSpacing.s,
            crossAxisSpacing: RsSpacing.s,
          ),
          itemCount: library.photos.length,
          itemBuilder: (context, i) {
            final entry = library.photos[i];
            return GestureDetector(
              onTap: () => _open(entry),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: entry.bytes != null
                    ? Image.memory(
                        entry.bytes!,
                        fit: BoxFit.cover,
                        gaplessPlayback: true,
                      )
                    : Container(
                        color: Theme.of(
                          context,
                        ).colorScheme.surfaceContainerHighest,
                      ),
              ),
            );
          },
        ),
      },
    );
  }

  Future<void> _open(PhotoEntry entry) async {
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
}
