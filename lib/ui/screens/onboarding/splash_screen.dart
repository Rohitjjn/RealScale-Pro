import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';

/// Splash (task 1.3): theme-matched, ≤1.5 s, decides the landing route.
class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _decideAndGo();
  }

  Future<void> _decideAndGo() async {
    final services = ref.read(servicesProvider);
    // Restore model install states in the background — routing must not
    // wait on file IO (task 1.3: splash ≤ 1.5 s). Setup/Model Manager call
    // initialize() themselves before showing download states.
    unawaited(services.modelManager.initialize());
    if (!mounted) return;
    final onboardingDone = services.settings.onboardingDone;
    await Future<void>.delayed(const Duration(milliseconds: 900));
    if (!mounted) return;
    context.go(onboardingDone ? Routes.home : Routes.guide);
  }

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 96,
              height: 96,
              decoration: BoxDecoration(
                gradient: RsColors.brandGradient(dark: dark),
                borderRadius: BorderRadius.circular(26),
              ),
              child: const Icon(
                Icons.photo_size_select_large_rounded,
                color: Colors.white,
                size: 44,
              ),
            ),
            const SizedBox(height: 20),
            Text(
              'RealScale-Pro',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ],
        ),
      ),
    );
  }
}
