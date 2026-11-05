import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../../app/app.dart';
import '../../../core/feature_gates.dart';
import '../../../services/permission_service/permission_service.dart'
    show PermissionOutcome;
import '../../../engine/upscale_engine.dart' show SettingLevel, UpscaleMode;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/cards.dart';
import '../../../ui/widgets/common.dart';

/// Home (task 1.8 + 1.10 + 1.11 + 1.12): collapsing header, recents strip,
/// 2x2 import tiles, mode selector, grouped options, locked Video card.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  void initState() {
    super.initState();
    unawaited(
      Future.microtask(() async {
        await ref.read(libraryProvider.notifier).load();
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final library = ref.watch(libraryProvider);

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: CustomScrollView(
          physics: const BouncingScrollPhysics(),
          slivers: [
            SliverAppBar(
              expandedHeight: 118,
              collapsedHeight: 72,
              pinned: true,
              backgroundColor: Theme.of(context).scaffoldBackgroundColor,
              surfaceTintColor: Colors.transparent,
              flexibleSpace: FlexibleSpaceBar(
                titlePadding: const EdgeInsets.only(
                  left: RsSpacing.xl,
                  bottom: 14,
                ),
                title: ShaderMask(
                  shaderCallback: (bounds) =>
                      RsColors.brandGradient(dark: dark).createShader(bounds),
                  child: Text(
                    l.homeGreeting,
                    style: RsType.display.copyWith(
                      fontSize: 26,
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ),
              actions: [
                IconButton(
                  tooltip: l.homeSettingsTooltip,
                  onPressed: () => context.push(Routes.settings),
                  icon: const Icon(Icons.settings_outlined),
                ),
                const SizedBox(width: RsSpacing.s),
              ],
            ),
            SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  switch (library.status) {
                    LibraryStatus.loading => const _RecentsShimmer(),
                    LibraryStatus.denied => _PermissionCard(
                      onTap: () async {
                        final services = ref.read(servicesProvider);
                        await services.permissions.requestPhotos();
                        if (context.mounted) {
                          unawaited(ref.read(libraryProvider.notifier).load());
                        }
                      },
                      title: l.homePermissionCard,
                    ),
                    LibraryStatus.empty => _EmptyRecents(
                      title: l.homeRecentsEmptyTitle,
                      body: l.homeRecentsEmptyBody,
                    ),
                    LibraryStatus.ready => _RecentsStrip(library: library),
                  },
                  SectionHeader(label: l.homeImportSection),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: RsSpacing.xl,
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: AspectRatio(
                            aspectRatio: 1.55,
                            child: ImportTile(
                              icon: Icons.photo_outlined,
                              label: l.homeImportGallery,
                              onTap: _importGallery,
                            ),
                          ),
                        ),
                        const SizedBox(width: RsSpacing.m),
                        Expanded(
                          child: AspectRatio(
                            aspectRatio: 1.55,
                            child: ImportTile(
                              icon: Icons.folder_outlined,
                              label: l.homeImportFiles,
                              onTap: _importFiles,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: RsSpacing.m),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: RsSpacing.xl,
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          child: AspectRatio(
                            aspectRatio: 1.55,
                            child: ImportTile(
                              icon: Icons.photo_camera_outlined,
                              label: l.homeImportCamera,
                              onTap: _importCamera,
                            ),
                          ),
                        ),
                        const SizedBox(width: RsSpacing.m),
                        Expanded(
                          child: AspectRatio(
                            aspectRatio: 1.55,
                            child: ImportTile(
                              icon: Icons.more_horiz_rounded,
                              label: l.homeImportMore,
                              onTap: () => context.push(Routes.more),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  SectionHeader(label: l.appName),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: RsSpacing.xl,
                    ),
                    child: Column(
                      children: [
                        ModeCard(
                          title: l.homeModeUpscaleTitle,
                          body: l.homeModeUpscaleBody,
                          selected: true,
                          onTap: () => Haptics.selection(ref),
                        ),
                        const SizedBox(height: RsSpacing.m),
                        ModeCard(
                          title: l.homeModeSrTitle,
                          body: l.homeModeSrBody,
                          selected: false,
                          proBadge: true,
                          onTap: _onSuperResolutionTapped,
                        ),
                      ],
                    ),
                  ),
                  SectionHeader(label: l.homeOptionsSection),
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: RsSpacing.xl,
                    ),
                    child: Column(
                      children: [
                        _OptionRow(
                          icon: Icons.history_rounded,
                          label: l.homeOptionMyUpscales,
                          onTap: () => context.push(Routes.myUpscales),
                        ),
                        _OptionRow(
                          icon: Icons.memory_rounded,
                          label: l.homeOptionModelManager,
                          onTap: () => context.push(Routes.modelManager),
                        ),
                        _OptionRow(
                          icon: Icons.settings_outlined,
                          label: l.homeOptionSettings,
                          onTap: () => context.push(Routes.settings),
                        ),
                        _OptionRow(
                          icon: Icons.videocam_outlined,
                          label: null,
                          onTap: _onVideoTapped,
                          locked: true,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: RsSpacing.xxl),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // --- import paths (task 1.10) --------------------------------------------

  Future<void> _importGallery() async {
    unawaited(context.push(Routes.gallery));
  }

  Future<void> _importFiles() async {
    final files = await FilePicker.pickFiles(type: FileType.image);
    final file = files.isEmpty ? null : files.first;
    if (file?.path == null || !mounted) return;
    final bytes = await File(file!.path!).readAsBytes();
    await ref
        .read(configProvider.notifier)
        .loadFrom(
          path: file.path!,
          bytes: bytes,
          level: SettingLevel.defaultLevel,
        );
    if (!mounted) return;
    unawaited(context.push(Routes.config));
  }

  Future<void> _importCamera() async {
    final services = ref.read(servicesProvider);
    final outcome = await services.permissions.requestCamera();
    if (!mounted) return;
    if (outcome == PermissionOutcome.granted) {
      final picked = await ImagePicker().pickImage(source: ImageSource.camera);
      if (picked == null || !mounted) return;
      final bytes = await File(picked.path).readAsBytes();
      await ref
          .read(configProvider.notifier)
          .loadFrom(
            path: picked.path,
            bytes: bytes,
            level: SettingLevel.defaultLevel,
          );
      if (!mounted) return;
      unawaited(context.push(Routes.config));
    } else {
      Haptics.warning(ref);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.errorGenericBody)),
      );
    }
  }

  // --- mode cards ------------------------------------------------------------

  Future<void> _onSuperResolutionTapped() async {
    Haptics.selection(ref);
    final l = AppLocalizations.of(context)!;

    // Phase 3: the SR pipeline is live. First time: the honest warning
    // sheet with the device-fit chip; then straight into the picker.
    if (!FeatureGates.superResolutionMode) {
      unawaited(
        AppBottomSheet.show(
          context,
          title: l.homeSrWarningTitle,
          child: _SrWarningSheet(),
        ),
      );
      return;
    }
    final settings = ref.read(settingsProvider);
    if (!settings.srWarningSeen) {
      final proceed = await AppBottomSheet.show<bool>(
        context,
        title: l.homeSrWarningTitle,
        child: _SrStartSheet(),
      );
      if (proceed != true || !mounted) return;
    }
    await _pickForSuperResolution();
  }

  Future<void> _pickForSuperResolution() async {
    final files = await FilePicker.pickFiles(type: FileType.image);
    final file = files.isEmpty ? null : files.first;
    if (file?.path == null || !mounted) return;
    final bytes = await File(file!.path!).readAsBytes();
    await ref
        .read(configProvider.notifier)
        .loadFrom(
          path: file.path!,
          bytes: bytes,
          level: SettingLevel.defaultLevel,
          mode: UpscaleMode.superResolution,
        );
    if (!mounted) return;
    unawaited(context.push(Routes.config));
  }

  void _onVideoTapped() {
    Haptics.warning(ref);
    final l = AppLocalizations.of(context)!;
    AppBottomSheet.show(
      context,
      title: l.homeVideoComingSoonTitle,
      child: _VideoComingSoonSheet(body: l.homeVideoComingSoonBody),
    );
  }
}

class _RecentsStrip extends ConsumerWidget {
  const _RecentsStrip({required this.library});

  final LibraryState library;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            RsSpacing.xl,
            0,
            RsSpacing.xl,
            RsSpacing.m,
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  l.homeRecents,
                  style: RsType.title.copyWith(fontWeight: FontWeight.w600),
                ),
              ),
              TextButton(
                onPressed: () => context.push(Routes.gallery),
                child: Text(l.homeSeeAll),
              ),
            ],
          ),
        ),
        SizedBox(
          height: 190,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
            scrollDirection: Axis.horizontal,
            itemCount: library.photos.length,
            separatorBuilder: (_, _) => const SizedBox(width: RsSpacing.m),
            itemBuilder: (context, i) {
              final entry = library.photos[i];
              return RecentPhotoCard(
                thumbnail: entry.bytes,
                resolutionLabel: '${entry.asset.width}×${entry.asset.height}',
                onOpen: () => context.push(Routes.viewer, extra: i),
                onUpscale: () async {
                  final file = await entry.asset.originFile;
                  if (file == null) return;
                  final bytes = await File(file.path).readAsBytes();
                  await ref
                      .read(configProvider.notifier)
                      .loadFrom(
                        path: file.path,
                        bytes: bytes,
                        level: SettingLevel.defaultLevel,
                      );
                  if (!context.mounted) return;
                  unawaited(context.push(Routes.config));
                },
              );
            },
          ),
        ),
      ],
    );
  }
}

class _RecentsShimmer extends StatelessWidget {
  const _RecentsShimmer();

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(
            RsSpacing.xl,
            0,
            RsSpacing.xl,
            RsSpacing.m,
          ),
          child: ShimmerBox(height: 20, width: 160, radius: 8),
        ),
        SizedBox(
          height: 176,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
            scrollDirection: Axis.horizontal,
            itemCount: 4,
            separatorBuilder: (_, _) => const SizedBox(width: RsSpacing.m),
            itemBuilder: (_, _) => const ShimmerBox(width: 132, height: 176),
          ),
        ),
      ],
    );
  }
}

class _EmptyRecents extends StatelessWidget {
  const _EmptyRecents({required this.title, required this.body});
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 176,
      child: EmptyState(icon: Icons.photo_outlined, title: title, body: body),
    );
  }
}

class _PermissionCard extends StatelessWidget {
  const _PermissionCard({required this.onTap, required this.title});
  final VoidCallback onTap;
  final String title;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
      child: Material(
        color: cs.primary.withValues(alpha: 0.07),
        borderRadius: RsRadius.cardBorder,
        child: InkWell(
          onTap: onTap,
          borderRadius: RsRadius.cardBorder,
          child: Padding(
            padding: const EdgeInsets.all(RsSpacing.l),
            child: Row(
              children: [
                Icon(Icons.lock_outline_rounded, color: cs.primary),
                const SizedBox(width: RsSpacing.m),
                Expanded(
                  child: Text(
                    title,
                    style: RsType.bodyMedium.copyWith(color: cs.primary),
                  ),
                ),
                Icon(Icons.arrow_forward_rounded, size: 18, color: cs.primary),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _OptionRow extends ConsumerWidget {
  const _OptionRow({
    required this.icon,
    required this.label,
    required this.onTap,
    this.locked = false,
  });

  final IconData icon;
  final String? label;
  final VoidCallback onTap;
  final bool locked;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final text = label ?? l.homeOptionVideo;
    return Material(
      color: cs.surface,
      borderRadius: RsRadius.cardBorder,
      child: InkWell(
        onTap: onTap,
        borderRadius: RsRadius.cardBorder,
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: RsSpacing.l,
            vertical: 14,
          ),
          child: Row(
            children: [
              Icon(icon, color: locked ? cs.onSurfaceVariant : cs.primary),
              const SizedBox(width: RsSpacing.l),
              Expanded(
                child: Text(
                  text,
                  style: RsType.bodyMedium.copyWith(
                    color: locked ? cs.onSurfaceVariant : cs.onSurface,
                  ),
                ),
              ),
              if (locked)
                Icon(Icons.lock_rounded, size: 18, color: cs.onSurfaceVariant)
              else
                Icon(
                  Icons.chevron_right_rounded,
                  size: 20,
                  color: cs.onSurfaceVariant,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SrWarningSheet extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final deviceAsync = ref.watch(deviceProfileProvider);
    final tierLabel = deviceAsync.value?.tier.uiLabel ?? '';
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
          Text(l.homeSrWarningBody(tierLabel), style: RsType.body),
          const SizedBox(height: RsSpacing.l),
          Wrap(
            spacing: 8,
            children: [
              InfoChip(
                label: l.homeSrWarningChipFit,
                tone: InfoChipTone.success,
                icon: Icons.check_rounded,
              ),
              InfoChip(
                label: l.homeSrWarningChipHeavy,
                tone: InfoChipTone.warning,
                icon: Icons.battery_alert_rounded,
              ),
            ],
          ),
          const SizedBox(height: RsSpacing.xl),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () {
                ref.read(settingsProvider).setSrWarningSeen(true);
                context.pop();
              },
              child: Text(l.homeSrWarningUseUpscale),
            ),
          ),
          const SizedBox(height: RsSpacing.s),
          SizedBox(
            width: double.infinity,
            child: TextButton(
              onPressed: () => context.pop(),
              child: Text(l.homeSrWarningContinue),
            ),
          ),
        ],
      ),
    );
  }
}

/// First-time Super Resolution start sheet (Phase 3): same honest warning
/// as the old locked sheet, but Continue now starts the SR flow.
class _SrStartSheet extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final deviceAsync = ref.watch(deviceProfileProvider);
    final tierLabel = deviceAsync.value?.tier.uiLabel ?? '';
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
          Text(l.homeSrWarningBody(tierLabel), style: RsType.body),
          const SizedBox(height: RsSpacing.l),
          Wrap(
            spacing: 8,
            children: [
              InfoChip(
                label: l.homeSrWarningChipFit,
                tone: InfoChipTone.success,
                icon: Icons.check_rounded,
              ),
              InfoChip(
                label: l.homeSrWarningChipHeavy,
                tone: InfoChipTone.warning,
                icon: Icons.battery_alert_rounded,
              ),
            ],
          ),
          const SizedBox(height: RsSpacing.xl),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () {
                ref.read(settingsProvider).setSrWarningSeen(true);
                context.pop(true);
              },
              child: Text(l.homeSrWarningContinue),
            ),
          ),
          const SizedBox(height: RsSpacing.s),
          SizedBox(
            width: double.infinity,
            child: TextButton(
              onPressed: () {
                ref.read(settingsProvider).setSrWarningSeen(true);
                context.pop(false);
              },
              child: Text(l.homeSrWarningUseUpscale),
            ),
          ),
        ],
      ),
    );
  }
}

class _VideoComingSoonSheet extends StatelessWidget {
  const _VideoComingSoonSheet({required this.body});
  final String body;

  @override
  Widget build(BuildContext context) {
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
          Text(body, style: RsType.body, textAlign: TextAlign.center),
          const SizedBox(height: RsSpacing.xl),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: () => context.pop(),
              child: Text(l.close),
            ),
          ),
        ],
      ),
    );
  }
}
