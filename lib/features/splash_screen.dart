/// Splash: performs real startup checks (settings, model manager init,
/// device profile, engine probe) and routes accordingly.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/providers.dart';
import '../core/theme.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});
  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  String _status = 'Starting…';

  @override
  void initState() {
    super.initState();
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    setState(() => _status = 'Reading settings…');
    final settings = ref.read(settingsProvider);
    final onboardingDone = settings.onboardingDone;

    setState(() => _status = 'Preparing model storage…');
    try {
      await ref.read(modelManagerProvider).init();
    } catch (e) {
      setState(() => _status = 'Storage warning: $e');
    }

    setState(() => _status = 'Profiling device…');
    try {
      await ref.read(deviceProfileProvider.future);
    } catch (_) {
      // Profile provider keeps a conservative fallback value.
    }

    setState(() => _status = 'Probing native engine…');
    // Engine availability is surfaced on Home; no fake waiting here.
    await ref.read(engineInfoProvider.future).catchError((_) => null);

    if (!mounted) {
      return;
    }
    context.go(onboardingDone ? '/home' : '/onboarding');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 84,
              height: 84,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primary,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Icon(Icons.auto_awesome_outlined,
                  color: Colors.white, size: 40),
            ),
            const SizedBox(height: RsSpacing.lg),
            Text('RealScale Pro', style: RsType.display),
            const SizedBox(height: RsSpacing.sm),
            Text(_status,
                style: RsType.caption.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant)),
            const SizedBox(height: RsSpacing.xl),
            const SizedBox(
              width: 120,
              child: LinearProgressIndicator(minHeight: 2),
            ),
          ],
        ),
      ),
    );
  }
}
