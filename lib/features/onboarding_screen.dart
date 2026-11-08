/// Onboarding: three focused pages (offline privacy, the two modes,
/// model download consent). Respects reduced-motion.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/providers.dart';
import '../core/theme.dart';

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});
  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  final _controller = PageController();
  int _page = 0;
  bool _consent = false;

  static const _pages = <(IconData, String, String)>[
    (
      Icons.lock_outline,
      'Offline by design',
      'Your images are processed entirely on this device. '
          'Nothing is uploaded, and no account is needed. The only '
          'network use is the one-time model download you approve.'
    ),
    (
      Icons.tune,
      'Two honest modes',
      'Upscale Mode optimizes for practical, shareable results with '
          'controlled JPEG quality (90–96). Super Resolution Mode is '
          'quality-first: lossless steps, tiling for large images, and '
          'never a silent downsample.'
    ),
    (
      Icons.download_for_offline_outlined,
      'Models on your terms',
      'AI models (BSD-3 licensed Real-ESRGAN) download only with your '
          'consent, are checksum-verified, and then work fully offline. '
          'You can delete them at any time.'
    ),
  ];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _finish() async {
    final settings = ref.read(settingsProvider);
    settings.onboardingDone = true;
    settings.modelDownloadConsent = _consent;
    if (mounted) {
      context.go('/home');
    }
  }

  @override
  Widget build(BuildContext context) {
    final reduceMotion =
        MediaQuery.of(context).disableAnimations;
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: PageView(
                controller: _controller,
                physics: reduceMotion
                    ? const NeverScrollableScrollPhysics()
                    : null,
                onPageChanged: (i) => setState(() => _page = i),
                children: [
                  for (final (icon, title, body) in _pages)
                    Padding(
                      padding: const EdgeInsets.all(RsSpacing.xl),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Icon(icon,
                              size: 56, color: scheme.primary),
                          const SizedBox(height: RsSpacing.lg),
                          Text(title,
                              style: RsType.headline
                                  .copyWith(color: scheme.onSurface)),
                          const SizedBox(height: RsSpacing.md),
                          Text(body,
                              style: RsType.body.copyWith(
                                  color: scheme.onSurfaceVariant)),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            if (_page == 2)
              Padding(
                padding: const EdgeInsets.symmetric(
                    horizontal: RsSpacing.xl),
                child: CheckboxListTile(
                  value: _consent,
                  onChanged: (v) => setState(() => _consent = v ?? false),
                  title: Text(
                      'Allow model downloads over the network',
                      style: RsType.body),
                  controlAffinity: ListTileControlAffinity.leading,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            Padding(
              padding: const EdgeInsets.all(RsSpacing.xl),
              child: Row(
                children: [
                  Row(
                    children: [
                      for (var i = 0; i < _pages.length; i++)
                        Container(
                          width: i == _page ? 22 : 8,
                          height: 8,
                          margin: const EdgeInsets.only(right: 6),
                          decoration: BoxDecoration(
                            color: i == _page
                                ? scheme.primary
                                : scheme.surfaceContainerHighest,
                            borderRadius: BorderRadius.circular(4),
                          ),
                        ),
                    ],
                  ),
                  const Spacer(),
                  if (_page > 0)
                    TextButton(
                        onPressed: () => _controller.previousPage(
                            duration: const Duration(milliseconds: 200),
                            curve: Curves.easeOut),
                        child: const Text('Back')),
                  const SizedBox(width: 8),
                  PaperButton(
                    label: _page == _pages.length - 1 ? 'Get started' : 'Next',
                    onPressed: () {
                      if (_page == _pages.length - 1) {
                        _finish();
                      } else {
                        _controller.nextPage(
                            duration: reduceMotion
                                ? Duration.zero
                                : const Duration(milliseconds: 200),
                            curve: Curves.easeOut);
                      }
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
