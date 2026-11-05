import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:smooth_page_indicator/smooth_page_indicator.dart';

import '../../../app/app.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';

/// Guide: 4 swipeable slides, indicator, Skip, Get started (task 1.4).
class GuideScreen extends ConsumerStatefulWidget {
  const GuideScreen({super.key});

  @override
  ConsumerState<GuideScreen> createState() => _GuideScreenState();
}

class _GuideScreenState extends ConsumerState<GuideScreen> {
  final _controller = PageController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _finish() async {
    await ref.read(settingsProvider).setGuideSeen(true);
    if (!mounted) return;
    Haptics.light(ref);
    context.go(Routes.primers);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final cs = Theme.of(context).colorScheme;
    final slides = [
      (Icons.photo_outlined, l.guide1Title, l.guide1Body),
      (Icons.lock_outline_rounded, l.guide2Title, l.guide2Body),
      (Icons.touch_app_rounded, l.guide3Title, l.guide3Body),
      (Icons.tune_rounded, l.guide4Title, l.guide4Body),
    ];
    final isLast =
        _controller.hasClients &&
        (_controller.page?.round() ?? 0) == slides.length - 1;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Align(
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.all(RsSpacing.l),
                child: TextButton(onPressed: _finish, child: Text(l.guideSkip)),
              ),
            ),
            Expanded(
              child: PageView.builder(
                controller: _controller,
                itemCount: slides.length,
                itemBuilder: (context, i) {
                  final (icon, title, body) = slides[i];
                  return Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: RsSpacing.xl,
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 140,
                          height: 140,
                          decoration: BoxDecoration(
                            gradient: RsColors.brandGradient(dark: dark),
                            borderRadius: BorderRadius.circular(40),
                          ),
                          child: Icon(icon, size: 60, color: Colors.white),
                        ),
                        const SizedBox(height: RsSpacing.xxl),
                        Text(
                          title,
                          style: RsType.headline,
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: RsSpacing.m),
                        Text(
                          body,
                          style: RsType.body.copyWith(
                            color: cs.onSurfaceVariant,
                            height: 1.6,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
            SmoothPageIndicator(
              controller: _controller,
              count: slides.length,
              effect: WormEffect(
                dotHeight: 8,
                dotWidth: 8,
                activeDotColor: cs.primary,
                dotColor: cs.outline,
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(RsSpacing.xl),
              child: SizedBox(
                width: double.infinity,
                child: AnimatedSwitcher(
                  duration: RsMotion.micro,
                  child: _controller.hasClients && !isLast
                      ? OutlinedButton(
                          key: const ValueKey('next'),
                          onPressed: () {
                            _controller.nextPage(
                              duration: RsMotion.screen,
                              curve: RsMotion.emphasized,
                            );
                            Haptics.selection(ref);
                          },
                          child: Text(l.guideNext),
                        )
                      : FilledButton(
                          key: const ValueKey('start'),
                          onPressed: _finish,
                          child: Text(l.guideGetStarted),
                        ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
