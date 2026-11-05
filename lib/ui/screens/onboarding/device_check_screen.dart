import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../app/app.dart';
import '../../../engine/upscale_engine.dart' show DeviceTier;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';

/// Device check (task 1.6): animated 2–3 s, real micro-benchmark,
/// friendly tier label Light / Standard / Powerful.
class DeviceCheckScreen extends ConsumerStatefulWidget {
  const DeviceCheckScreen({super.key});

  @override
  ConsumerState<DeviceCheckScreen> createState() => _DeviceCheckScreenState();
}

class _DeviceCheckScreenState extends ConsumerState<DeviceCheckScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1200),
  )..repeat(reverse: true);

  bool _done = false;
  DeviceTier? _tier;

  @override
  void initState() {
    super.initState();
    _runCheck();
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  Future<void> _runCheck() async {
    final started = DateTime.now();
    final services = ref.read(servicesProvider);
    final profile = await services.profiler.profile(rerunBenchmark: true);
    // Keep the animated screen between 2–3 s (task 1.6 test).
    final elapsed = DateTime.now().difference(started).inMilliseconds;
    if (elapsed < 2000) {
      await Future<void>.delayed(Duration(milliseconds: 2000 - elapsed));
    }
    if (!mounted) return;
    setState(() {
      _done = true;
      _tier = profile.tier;
    });
    await Future<void>.delayed(const Duration(milliseconds: 900));
    if (!mounted) return;
    context.go(Routes.setup);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final tierLabel = switch (_tier) {
      DeviceTier.t0 => l.deviceCheckTierLight,
      DeviceTier.t1 => l.deviceCheckTierStandard,
      DeviceTier.t2 || DeviceTier.t3 => l.deviceCheckTierPowerful,
      null => '',
    };
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            FadeTransition(
              opacity: Tween(begin: 0.55, end: 1.0).animate(
                CurvedAnimation(parent: _pulse, curve: Curves.easeInOut),
              ),
              child: ScaleTransition(
                scale: Tween(begin: 0.96, end: 1.04).animate(_pulse),
                child: Container(
                  width: 108,
                  height: 108,
                  decoration: BoxDecoration(
                    gradient: RsColors.brandGradient(dark: dark),
                    shape: BoxShape.circle,
                  ),
                  child: _done
                      ? const Icon(
                          Icons.check_rounded,
                          color: Colors.white,
                          size: 52,
                        )
                      : const Icon(
                          Icons.memory_rounded,
                          color: Colors.white,
                          size: 48,
                        ),
                ),
              ),
            ),
            const SizedBox(height: RsSpacing.xl),
            AnimatedSwitcher(
              duration: RsMotion.micro,
              child: _done
                  ? Column(
                      key: const ValueKey('done'),
                      children: [
                        Text(
                          l.deviceCheckDone,
                          style: RsType.title.copyWith(color: cs.onSurface),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          tierLabel,
                          style: RsType.headline.copyWith(color: cs.primary),
                        ),
                      ],
                    )
                  : Column(
                      key: const ValueKey('working'),
                      children: [
                        Text(l.deviceCheckTitle, style: RsType.title),
                        const SizedBox(height: 8),
                        Text(
                          l.deviceCheckBody,
                          style: RsType.body.copyWith(
                            color: cs.onSurfaceVariant,
                          ),
                          textAlign: TextAlign.center,
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
