/// Settings: theme, JPEG quality (90–96), default mode, storage info.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/providers.dart';
import '../core/theme.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(settingsProvider);
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(RsSpacing.md),
        children: [
          PaperCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Output quality (JPEG)', style: RsType.title),
                const SizedBox(height: 4),
                Text(
                  'Applies when Upscale Mode writes JPEG. Higher values '
                  'keep more detail and produce larger files.',
                  style: RsType.caption
                      .copyWith(color: scheme.onSurfaceVariant),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    const Text('90'),
                    Expanded(
                      child: Slider(
                        value: settings.jpegQuality.toDouble(),
                        min: 90,
                        max: 96,
                        divisions: 6,
                        label: '${settings.jpegQuality}',
                        onChanged: (v) => setState(
                            () => settings.jpegQuality = v.round()),
                      ),
                    ),
                    const Text('96'),
                    const SizedBox(width: 8),
                    Text('${settings.jpegQuality}', style: RsType.mono),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: RsSpacing.sm),
          PaperCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Default mode', style: RsType.title),
                _ModeOption(
                  selected: settings.defaultMode != 'superResolution',
                  title: 'Upscale Mode',
                  subtitle: 'Practical, JPEG 90–96',
                  onTap: () => setState(() => settings.defaultMode = 'upscale'),
                ),
                _ModeOption(
                  selected: settings.defaultMode == 'superResolution',
                  title: 'Super Resolution Mode',
                  subtitle: 'Lossless, quality-first',
                  onTap: () => setState(
                      () => settings.defaultMode = 'superResolution'),
                ),
              ],
            ),
          ),
          const SizedBox(height: RsSpacing.sm),
          PaperCard(
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.dark_mode_outlined,
                  color: scheme.onSurfaceVariant),
              title: const Text('Appearance'),
              subtitle: Text(
                  switch (settings.themeMode) {
                    'light' => 'Light (paper)',
                    'dark' => 'Dark (graphite)',
                    _ => 'Follow system',
                  },
                  style: RsType.caption),
              trailing: const Icon(Icons.chevron_right),
              onTap: () async {
                final next = switch (settings.themeMode) {
                  'system' => 'light',
                  'light' => 'dark',
                  _ => 'system',
                };
                setState(() => settings.themeMode = next);
              },
            ),
          ),
          const SizedBox(height: RsSpacing.sm),
          PaperCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Consumer(builder: (context, ref, _) {
                  final gpuAvailable = ref.watch(deviceProfileProvider).value
                          ?.gpuAvailable ??
                      false;
                  return SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text('GPU acceleration (experimental)',
                        style: RsType.title),
                    subtitle: Text(
                      gpuAvailable
                          ? 'Runs inference on Vulkan when the device '
                              'supports it; falls back to CPU on any error.'
                          : 'No Vulkan support detected on this device — '
                              'the verified CPU backend is used.',
                      style: RsType.caption
                          .copyWith(color: scheme.onSurfaceVariant),
                    ),
                    value: settings.preferGpu && gpuAvailable,
                    onChanged: gpuAvailable
                        ? (v) => setState(() => settings.preferGpu = v)
                        : null,
                  );
                }),
              ],
            ),
          ),
          const SizedBox(height: RsSpacing.sm),
          PaperCard(
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.storage_outlined,
                  color: scheme.onSurfaceVariant),
              title: const Text('Storage'),
              subtitle: FutureBuilder<int>(
                future: ref.read(storageProvider).freeSpaceBytes(),
                builder: (context, snap) => Text(
                  snap.hasData && snap.data! >= 0
                      ? '${(snap.data! / 1024 / 1024 / 1024).toStringAsFixed(1)} GB free'
                      : 'Free space unknown on this platform',
                  style: RsType.caption,
                ),
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => setState(() {}),
            ),
          ),
          const SizedBox(height: RsSpacing.sm),
          PaperCard(
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.info_outline,
                  color: scheme.onSurfaceVariant),
              title: const Text('About & licenses'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/about'),
            ),
          ),
          const SizedBox(height: RsSpacing.md),
          Text(
            'Results are saved to the app’s RealScale-Pro folder and, on '
            'Android, exported to Pictures/RealScale-Pro. The original '
            'image is never modified. All processing happens on this '
            'device; the network is used only for model downloads you '
            'approve.',
            style: RsType.caption.copyWith(color: scheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

class _ModeOption extends StatelessWidget {
  const _ModeOption({
    required this.selected,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });
  final bool selected;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(RsRadius.button),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(RsRadius.button),
            border: Border.all(
                color: selected ? scheme.primary : scheme.outlineVariant),
          ),
          child: Row(
            children: [
              Icon(
                selected
                    ? Icons.radio_button_checked
                    : Icons.radio_button_unchecked,
                size: 20,
                color: selected ? scheme.primary : scheme.onSurfaceVariant,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: RsType.title),
                    Text(subtitle,
                        style: RsType.caption
                            .copyWith(color: scheme.onSurfaceVariant)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
