import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../l10n/app_localizations.dart';
import '../../../ui/theme/tokens.dart';

/// About screen.
class AboutScreen extends ConsumerWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      appBar: AppBar(title: Text(l.settingsAbout)),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 84,
              height: 84,
              decoration: BoxDecoration(
                gradient: RsColors.brandGradient(dark: dark),
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Icon(
                Icons.photo_size_select_large_rounded,
                color: Colors.white,
                size: 40,
              ),
            ),
            const SizedBox(height: RsSpacing.l),
            Text('RealScale-Pro', style: RsType.headline),
            const SizedBox(height: 4),
            Text(
              '${l.aboutVersion} 0.2.0',
              style: RsType.body.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: RsSpacing.s),
            Text(
              l.aboutBuiltWith,
              style: RsType.label.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Privacy statement screen (rules.md: private by default).
class PrivacyScreen extends StatelessWidget {
  const PrivacyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: Text(l.privacyTitle)),
      body: ListView(
        padding: const EdgeInsets.all(RsSpacing.xl),
        children: [
          Text(l.privacyBody1, style: RsType.body.copyWith(height: 1.6)),
          const SizedBox(height: RsSpacing.l),
          Text(
            l.privacyBody2,
            style: RsType.body.copyWith(
              height: 1.6,
              color: cs.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: RsSpacing.l),
          Text(
            l.privacyBody3,
            style: RsType.body.copyWith(
              height: 1.6,
              color: cs.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

/// Open-source licenses screen, driven by the reused components list.
class LicensesScreen extends StatelessWidget {
  const LicensesScreen({super.key});

  static const _components = <(String, String, String)>[
    ('Flutter', 'BSD-3-Clause', 'https://github.com/flutter/flutter'),
    ('Riverpod', 'MIT', 'https://pub.dev/packages/flutter_riverpod'),
    ('go_router', 'BSD-3-Clause', 'https://pub.dev/packages/go_router'),
    ('photo_manager', 'MIT', 'https://pub.dev/packages/photo_manager'),
    ('image_picker', 'Apache-2.0', 'https://pub.dev/packages/image_picker'),
    ('file_picker', 'MIT', 'https://pub.dev/packages/file_picker'),
    ('image', 'Apache-2.0', 'https://pub.dev/packages/image'),
    ('share_plus', 'MIT', 'https://pub.dev/packages/share_plus'),
    (
      'background_downloader',
      'Apache-2.0',
      'https://pub.dev/packages/background_downloader',
    ),
    (
      'permission_handler',
      'MIT',
      'https://pub.dev/packages/permission_handler',
    ),
    ('photo_view', 'MIT', 'https://pub.dev/packages/photo_view'),
    (
      'smooth_page_indicator',
      'MIT',
      'https://pub.dev/packages/smooth_page_indicator',
    ),
    (
      'flutter_native_splash',
      'MIT',
      'https://pub.dev/packages/flutter_native_splash',
    ),
    ('dynamic_color', 'Apache-2.0', 'https://pub.dev/packages/dynamic_color'),
    ('wakelock_plus', 'MIT', 'https://pub.dev/packages/wakelock_plus'),
    (
      'device_info_plus',
      'BSD-3-Clause',
      'https://pub.dev/packages/device_info_plus',
    ),
    (
      'shared_preferences',
      'BSD-3-Clause',
      'https://pub.dev/packages/shared_preferences',
    ),
    ('sqflite / sqflite_common_ffi', 'MIT', 'https://pub.dev/packages/sqflite'),
    ('archive', 'MIT', 'https://pub.dev/packages/archive'),
    ('crypto', 'BSD-3-Clause', 'https://pub.dev/packages/crypto'),
    ('http', 'BSD-3-Clause', 'https://pub.dev/packages/http'),
    ('uuid', 'MIT', 'https://pub.dev/packages/uuid'),
    ('ffi', 'BSD-3-Clause', 'https://pub.dev/packages/ffi'),
    ('Inter font', 'SIL OFL 1.1', 'https://github.com/rsms/inter'),
    ('ncnn', 'BSD-3-Clause', 'https://github.com/Tencent/ncnn'),
    (
      'Real-ESRGAN models',
      'BSD-3-Clause',
      'https://github.com/xinntao/Real-ESRGAN',
    ),
    (
      'Real-CUGAN models',
      'MIT',
      'https://github.com/bilibili/ailab/tree/main/Real-CUGAN',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: Text(l.licensesTitle)),
      body: ListView.builder(
        padding: const EdgeInsets.all(RsSpacing.l),
        itemCount: _components.length,
        itemBuilder: (context, i) {
          final (name, license, url) = _components[i];
          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: RsSpacing.m),
            title: Text(name, style: RsType.bodyMedium),
            subtitle: Text(
              url,
              style: RsType.label.copyWith(color: cs.onSurfaceVariant),
            ),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: cs.primary.withValues(alpha: 0.08),
                borderRadius: RsRadius.pill,
              ),
              child: Text(
                license,
                style: RsType.label.copyWith(fontSize: 10, color: cs.primary),
              ),
            ),
          );
        },
      ),
    );
  }
}
