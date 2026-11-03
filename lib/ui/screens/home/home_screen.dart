import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:photo_manager/photo_manager.dart';

import '../../../services/permission_service/permission_service.dart';
import '../../../services/settings_service.dart';
import '../../../state/app_controller.dart';
import '../../widgets/core_widgets.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({
    required this.controller,
    required this.settings,
    required this.permissions,
    super.key,
  });

  final AppController controller;
  final SettingsService settings;
  final PermissionService permissions;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  List<AssetEntity> _recents = <AssetEntity>[];
  PermissionState _permission = PermissionState.notDetermined;
  bool _loading = true;
  bool _superResolution = false;

  @override
  void initState() {
    super.initState();
    _loadRecents();
    widget.controller.loadHistory();
  }

  Future<void> _loadRecents({bool request = false}) async {
    setState(() => _loading = true);
    try {
      _permission = request
          ? await widget.permissions.photoState()
          : await PhotoManager.getPermissionState(
              requestOption: const PermissionRequestOption(),
            );
      if (_permission.hasAccess) {
        final List<AssetPathEntity> paths = await PhotoManager.getAssetPathList(
          type: RequestType.image,
          onlyAll: true,
          filterOption: FilterOptionGroup(
            orders: const <OrderOption>[
              OrderOption(type: OrderOptionType.createDate, asc: false),
            ],
          ),
        );
        _recents = paths.isEmpty
            ? <AssetEntity>[]
            : await paths.first.getAssetListRange(start: 0, end: 30);
      }
    } on Object {
      _recents = <AssetEntity>[];
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openSelection(Future<bool> Function() picker) async {
    if (await picker() && mounted) {
      context.go('/config');
    }
  }

  Future<void> _openAsset(AssetEntity asset, {required bool viewer}) async {
    final File? file = await asset.file;
    if (file != null &&
        await widget.controller.selectPath(file.path) &&
        mounted) {
      context.go(viewer ? '/viewer' : '/config');
    }
  }

  Future<void> _selectSuperResolution() async {
    if (!widget.settings.superResolutionNoticeSeen) {
      await showAppSheet(
        context,
        Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(
              context.l10n.superResolution,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 8),
            Text(context.l10n.superResolutionNotice),
            const SizedBox(height: 8),
            InfoChip(
              icon: Icons.speed_rounded,
              label: widget.controller.device?.label ?? context.l10n.standard,
            ),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () {
                Navigator.pop(context);
                showAppSheet(context, Text(context.l10n.comingLater));
              },
              child: Text(context.l10n.useSuperResolution),
            ),
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(context.l10n.stayUpscale),
            ),
          ],
        ),
      );
      await widget.settings.markSuperResolutionNoticeSeen();
    } else if (mounted) {
      await showAppSheet(context, Text(context.l10n.comingLater));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: CustomScrollView(
      slivers: <Widget>[
        SliverAppBar.large(
          title: Text(context.l10n.appName),
          actions: <Widget>[
            IconButton(
              tooltip: context.l10n.settings,
              onPressed: () => context.go('/settings'),
              icon: const Icon(Icons.settings_rounded),
            ),
          ],
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                _SectionTitle(
                  title: context.l10n.recentPhotos,
                  action: context.l10n.seeAll,
                ),
                const SizedBox(height: 12),
                _recentContent(),
                const SizedBox(height: 28),
                Text(
                  context.l10n.chooseImage,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 12),
                GridView.count(
                  crossAxisCount: MediaQuery.sizeOf(context).width >= 800
                      ? 4
                      : 2,
                  childAspectRatio: 1.25,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  children: <Widget>[
                    ImportTile(
                      icon: Icons.photo_library_rounded,
                      title: context.l10n.gallery,
                      subtitle: context.l10n.gallerySubtitle,
                      onTap: () =>
                          _openSelection(widget.controller.chooseGallery),
                    ),
                    ImportTile(
                      icon: Icons.folder_rounded,
                      title: context.l10n.files,
                      subtitle: context.l10n.filesSubtitle,
                      onTap: () => _openSelection(widget.controller.chooseFile),
                    ),
                    ImportTile(
                      icon: Icons.camera_alt_rounded,
                      title: context.l10n.camera,
                      subtitle: context.l10n.cameraSubtitle,
                      onTap: () async {
                        await widget.permissions.cameraState();
                        await _openSelection(widget.controller.takePhoto);
                      },
                    ),
                    ImportTile(
                      icon: Icons.add_circle_rounded,
                      title: context.l10n.more,
                      subtitle: context.l10n.moreSubtitle,
                      onTap: () => showAppSheet(
                        context,
                        ListTile(
                          leading: const Icon(Icons.cloud_outlined),
                          title: Text(context.l10n.selectFromDevice),
                          onTap: () {
                            Navigator.pop(context);
                            _openSelection(widget.controller.chooseFile);
                          },
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 28),
                Text(
                  context.l10n.mode,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                ModeCard(
                  title: context.l10n.upscaleMode,
                  subtitle: context.l10n.upscaleModeSubtitle,
                  selected: !_superResolution,
                  onTap: () => setState(() => _superResolution = false),
                ),
                ModeCard(
                  title: context.l10n.superResolution,
                  subtitle: context.l10n.superResolutionSubtitle,
                  selected: _superResolution,
                  badge: context.l10n.pro,
                  onTap: _selectSuperResolution,
                ),
                const SizedBox(height: 24),
                _OptionsList(controller: widget.controller),
              ],
            ),
          ),
        ),
      ],
    ),
  );

  Widget _recentContent() {
    if (Platform.isWindows) {
      return Card(
        child: ListTile(
          leading: const Icon(Icons.history_rounded),
          title: Text(context.l10n.desktopRecent),
          subtitle: Text(context.l10n.filesSubtitle),
          trailing: const Icon(Icons.chevron_right_rounded),
          onTap: () => _openSelection(widget.controller.chooseFile),
        ),
      );
    }
    if (_loading) {
      return const SizedBox(height: 170, child: LinearProgressIndicator());
    }
    if (!_permission.hasAccess) {
      return Card(
        child: ListTile(
          leading: const Icon(Icons.photo_library_outlined),
          title: Text(context.l10n.recentPermissionMessage),
          trailing: FilledButton(
            onPressed: () => _loadRecents(request: true),
            child: Text(context.l10n.allowPhotoAccess),
          ),
        ),
      );
    }
    if (_recents.isEmpty) {
      return Card(
        child: ListTile(
          leading: const Icon(Icons.photo_outlined),
          title: Text(context.l10n.noRecentPhotos),
        ),
      );
    }
    return SizedBox(
      height: 180,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _recents.length,
        separatorBuilder: (_, _) => const SizedBox(width: 10),
        itemBuilder: (BuildContext context, int index) => _RecentCard(
          asset: _recents[index],
          onImage: () => _openAsset(_recents[index], viewer: true),
          onUpscale: () => _openAsset(_recents[index], viewer: false),
        ),
      ),
    );
  }
}

class _RecentCard extends StatelessWidget {
  const _RecentCard({
    required this.asset,
    required this.onImage,
    required this.onUpscale,
  });
  final AssetEntity asset;
  final VoidCallback onImage;
  final VoidCallback onUpscale;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 126,
    child: Column(
      children: <Widget>[
        Expanded(
          child: FutureBuilder<Uint8List?>(
            future: asset.thumbnailDataWithSize(const ThumbnailSize(256, 256)),
            builder:
                (BuildContext context, AsyncSnapshot<Uint8List?> snapshot) =>
                    InkWell(
                      onTap: onImage,
                      child: Stack(
                        fit: StackFit.expand,
                        children: <Widget>[
                          ClipRRect(
                            borderRadius: BorderRadius.circular(18),
                            child: snapshot.data == null
                                ? ColoredBox(
                                    color: Theme.of(
                                      context,
                                    ).colorScheme.surfaceContainerHighest,
                                  )
                                : Image.memory(
                                    snapshot.data!,
                                    fit: BoxFit.cover,
                                  ),
                          ),
                          Positioned(
                            right: 6,
                            bottom: 6,
                            child: Chip(
                              label: Text('${asset.width}×${asset.height}'),
                              visualDensity: VisualDensity.compact,
                            ),
                          ),
                        ],
                      ),
                    ),
          ),
        ),
        TextButton(onPressed: onUpscale, child: Text(context.l10n.upscale)),
      ],
    ),
  );
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({required this.title, required this.action});
  final String title;
  final String action;

  @override
  Widget build(BuildContext context) => Row(
    children: <Widget>[
      Expanded(
        child: Text(title, style: Theme.of(context).textTheme.titleLarge),
      ),
      TextButton(onPressed: () {}, child: Text(action)),
    ],
  );
}

class _OptionsList extends StatelessWidget {
  const _OptionsList({required this.controller});
  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final List<(IconData, String, String)> routes =
        <(IconData, String, String)>[
          (
            Icons.auto_awesome_motion_rounded,
            context.l10n.myUpscales,
            '/upscales',
          ),
          (Icons.inventory_2_rounded, context.l10n.modelManager, '/models'),
          (Icons.settings_rounded, context.l10n.settings, '/settings'),
          (Icons.slideshow_rounded, context.l10n.guide, '/guide?replay=true'),
          (Icons.help_rounded, context.l10n.helpFeedback, '/help'),
          (Icons.info_rounded, context.l10n.aboutLicenses, '/about'),
        ];
    return Card(
      child: Column(
        children: <Widget>[
          ...routes.map(
            ((IconData, String, String) row) => ListTile(
              leading: Icon(row.$1),
              title: Text(row.$2),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => context.go(row.$3),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.lock_rounded),
            title: Text(context.l10n.videoUpscale),
            subtitle: Text(context.l10n.comingSoon),
            onTap: () => showAppSheet(
              context,
              Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text(context.l10n.videoNotice),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(context.l10n.close),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
