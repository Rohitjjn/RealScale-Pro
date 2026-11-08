/// About: honest description, licenses, privacy.
library;

import 'package:flutter/material.dart';

import '../core/theme.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('About')),
      body: ListView(
        padding: const EdgeInsets.all(RsSpacing.md),
        children: [
          PaperCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('RealScale Pro', style: RsType.headline),
                const SizedBox(height: 6),
                Text(
                  'Offline AI image enhancement. Images never leave the '
                  'device; the only network traffic is the model download '
                  'you explicitly approve.',
                  style: RsType.body
                      .copyWith(color: scheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
          const SizedBox(height: RsSpacing.sm),
          PaperCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Licenses', style: RsType.title),
                const SizedBox(height: 8),
                _license(
                  context,
                  'Application code',
                  'MIT — RealScale-Pro contributors',
                ),
                _license(
                  context,
                  'ncnn inference framework',
                  'BSD 3-Clause — Tencent (github.com/Tencent/ncnn)',
                ),
                _license(
                  context,
                  'Real-ESRGAN models',
                  'BSD 3-Clause — xinntao (github.com/xinntao/Real-ESRGAN). '
                  'Downloaded on demand, never bundled.',
                ),
                _license(
                  context,
                  'stb_image (host test harness only)',
                  'Public domain — nothings/stb',
                ),
                _license(
                  context,
                  'Flutter, Riverpod, go_router, image, path_provider, '
                  'http, crypto, shared_preferences, image_picker, archive',
                  'BSD-3 / MIT — see the respective pub.dev packages',
                ),
              ],
            ),
          ),
          const SizedBox(height: RsSpacing.sm),
          PaperCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Privacy', style: RsType.title),
                const SizedBox(height: 8),
                Text(
                  '• No accounts, no tracking, no analytics.\n'
                  '• Processing happens fully on-device.\n'
                  '• Model files are fetched once from the official '
                  'GitHub release and verified with SHA-256.\n'
                  '• The original image is never modified; results are '
                  'written to the app’s own folder.',
                  style: RsType.body
                      .copyWith(color: scheme.onSurfaceVariant),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _license(BuildContext context, String what, String terms) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(what, style: RsType.body),
          Text(terms,
              style: RsType.caption.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant)),
          const SizedBox(height: 2),
        ],
      ),
    );
  }
}
