import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../services/permission_service/permission_service.dart';
import '../../../services/settings_service.dart';
import '../../../state/app_controller.dart';
import '../../theme/app_theme.dart';
import '../../widgets/core_widgets.dart';

class BrandSplashScreen extends StatefulWidget {
  const BrandSplashScreen({required this.onFinished, super.key});

  final VoidCallback onFinished;

  @override
  State<BrandSplashScreen> createState() => _BrandSplashScreenState();
}

class _BrandSplashScreenState extends State<BrandSplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _animation;

  @override
  void initState() {
    super.initState();
    _animation = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..forward();
    Future<void>.delayed(const Duration(milliseconds: 1200), widget.onFinished);
  }

  @override
  void dispose() {
    _animation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Center(
          child: FadeTransition(
            opacity: CurvedAnimation(parent: _animation, curve: Curves.easeOut),
            child: ScaleTransition(
              scale: Tween<double>(begin: 0.92, end: 1).animate(_animation),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  const Icon(Icons.auto_awesome_rounded, color: AppColors.blue, size: 58),
                  const SizedBox(height: 16),
                  Text(
                    context.l10n.appName,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
}

class GuideScreen extends StatefulWidget {
  const GuideScreen({required this.replay, super.key});
  final bool replay;

  @override
  State<GuideScreen> createState() => _GuideScreenState();
}

class _GuideScreenState extends State<GuideScreen> {
  final PageController _controller = PageController();
  int _page = 0;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _finish() {
    context.go(widget.replay ? '/home' : '/permissions/photos');
  }

  @override
  Widget build(BuildContext context) {
    final List<(IconData, String, String)> slides = <(IconData, String, String)>[
      (Icons.hd_rounded, context.l10n.guideTitle1, context.l10n.guideBody1),
      (Icons.devices_rounded, context.l10n.guideTitle2, context.l10n.guideBody2),
      (Icons.tune_rounded, context.l10n.guideTitle3, context.l10n.guideBody3),
      (Icons.lock_rounded, context.l10n.guideTitle4, context.l10n.guideBody4),
    ];
    return Scaffold(
      appBar: AppBar(
        actions: <Widget>[
          TextButton(onPressed: _finish, child: Text(context.l10n.skip)),
        ],
      ),
      body: Column(
        children: <Widget>[
          Expanded(
            child: PageView.builder(
              controller: _controller,
              itemCount: slides.length,
              onPageChanged: (int value) => setState(() => _page = value),
              itemBuilder: (BuildContext context, int index) {
                final (IconData icon, String title, String body) = slides[index];
                return Padding(
                  padding: const EdgeInsets.all(32),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: <Widget>[
                      Icon(icon, size: 96, color: AppColors.blue),
                      const SizedBox(height: 32),
                      Text(title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineMedium),
                      const SizedBox(height: 12),
                      Text(body, textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodyLarge),
                    ],
                  ),
                );
              },
            ),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: List<Widget>.generate(
              slides.length,
              (int index) => AnimatedContainer(
                duration: AppTokens.microMotion,
                margin: const EdgeInsets.all(4),
                width: index == _page ? 24 : 8,
                height: 8,
                decoration: BoxDecoration(
                  color: index == _page ? AppColors.blue : AppColors.muted,
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ),
          PrimaryActionBar(
            label: _page == slides.length - 1 ? context.l10n.getStarted : context.l10n.next,
            onPressed: () {
              if (_page == slides.length - 1) {
                _finish();
              } else {
                _controller.nextPage(duration: AppTokens.screenMotion, curve: Curves.easeOutCubic);
              }
            },
          ),
        ],
      ),
    );
  }
}

class PermissionPrimerScreen extends StatelessWidget {
  const PermissionPrimerScreen({
    required this.isPhotos,
    required this.permissions,
    super.key,
  });

  final bool isPhotos;
  final PermissionService permissions;

  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                Icon(
                  isPhotos ? Icons.photo_library_rounded : Icons.notifications_active_rounded,
                  color: AppColors.blue,
                  size: 88,
                ),
                const SizedBox(height: 28),
                Text(
                  isPhotos
                      ? context.l10n.photoPermissionTitle
                      : context.l10n.notificationPermissionTitle,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 12),
                Text(
                  isPhotos
                      ? context.l10n.photoPermissionBody
                      : context.l10n.notificationPermissionBody,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 32),
                FilledButton(
                  onPressed: () async {
                    if (isPhotos) {
                      await permissions.photoState();
                    } else {
                      await permissions.notificationState();
                    }
                    if (context.mounted) {
                      context.go(isPhotos ? '/permissions/notifications' : '/device-check');
                    }
                  },
                  child: Text(context.l10n.allow),
                ),
                TextButton(
                  onPressed: () => context.go(
                    isPhotos ? '/permissions/notifications' : '/device-check',
                  ),
                  child: Text(context.l10n.notNow),
                ),
              ],
            ),
          ),
        ),
      );
}

class DeviceCheckScreen extends StatefulWidget {
  const DeviceCheckScreen({required this.controller, super.key});
  final AppController controller;

  @override
  State<DeviceCheckScreen> createState() => _DeviceCheckScreenState();
}

class _DeviceCheckScreenState extends State<DeviceCheckScreen> {
  @override
  void initState() {
    super.initState();
    widget.controller.checkDevice().then((_) {
      if (mounted) {
        Future<void>.delayed(const Duration(milliseconds: 500), () {
          if (mounted) context.go('/setup');
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                const Icon(Icons.memory_rounded, color: AppColors.blue, size: 80),
                const SizedBox(height: 24),
                Text(context.l10n.checkingDevice, style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 8),
                Text(context.l10n.deviceCheckBody, textAlign: TextAlign.center),
                const SizedBox(height: 24),
                const LinearProgressIndicator(borderRadius: BorderRadius.all(Radius.circular(8))),
              ],
            ),
          ),
        ),
      );
}

class SetupScreen extends StatelessWidget {
  const SetupScreen({required this.settings, super.key});
  final SettingsService settings;

  @override
  Widget build(BuildContext context) {
    final List<(String, String)> rows = <(String, String)>[
      (context.l10n.starterPhoto, '1.1 MB'),
      (context.l10n.starterAnime, '2.6 MB'),
      (context.l10n.starterClassifier, context.l10n.queued),
      (context.l10n.starterDetector, context.l10n.queued),
    ];
    return Scaffold(
      appBar: AppBar(title: Text(context.l10n.setupTitle)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: <Widget>[
          Text(context.l10n.setupBody),
          const SizedBox(height: 20),
          ...rows.indexed.map(((int, (String, String)) entry) {
            final (int index, (String, String) row) = entry;
            return Card(
              child: ListTile(
                leading: Icon(index < 2 ? Icons.check_circle_rounded : Icons.cloud_download_outlined),
                title: Text(row.$1),
                subtitle: Text(row.$2),
                trailing: Text(index < 2 ? context.l10n.done : context.l10n.queued),
              ),
            );
          }),
          const SizedBox(height: 18),
          const LinearProgressIndicator(value: 1),
        ],
      ),
      bottomNavigationBar: PrimaryActionBar(
        label: context.l10n.startUpscaling,
        onPressed: () async {
          await settings.completeOnboarding();
          if (context.mounted) context.go('/home');
        },
      ),
    );
  }
}
