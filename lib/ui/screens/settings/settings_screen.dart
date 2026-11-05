import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/common.dart';

/// Settings (tasks 1.17 + 2.10): theme, dynamic color, haptics, keep awake,
/// language, defaults, downloads, storage, privacy, support, about.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context)!;
    final settings = ref.watch(settingsProvider);
    final cs = Theme.of(context).colorScheme;
    final deviceAsync = ref.watch(deviceProfileProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l.settingsTitle)),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
        children: [
          SectionHeader(label: l.settingsAppearance),
          _Card(
            children: [
              ListTile(
                leading: const Icon(Icons.brightness_6_outlined),
                title: Text(l.settingsTheme),
                trailing: SegmentedButton<String>(
                  segments: [
                    ButtonSegment(
                      value: 'system',
                      icon: const Icon(Icons.auto_awesome, size: 18),
                    ),
                    ButtonSegment(
                      value: 'light',
                      icon: const Icon(Icons.light_mode_outlined, size: 18),
                    ),
                    ButtonSegment(
                      value: 'dark',
                      icon: const Icon(Icons.dark_mode_outlined, size: 18),
                    ),
                  ],
                  selected: {settings.themeMode},
                  onSelectionChanged: (s) async {
                    Haptics.selection(ref);
                    await ref.read(themeModeNotifier.notifier).set(
                      switch (s.first) {
                        'light' => ThemeMode.light,
                        'dark' => ThemeMode.dark,
                        _ => ThemeMode.system,
                      },
                    );
                  },
                  showSelectedIcon: false,
                  style: const ButtonStyle(
                    visualDensity: VisualDensity.compact,
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                ),
              ),
              SwitchListTile(
                secondary: const Icon(Icons.palette_outlined),
                title: Text(l.settingsDynamicColor),
                subtitle: Text(l.settingsDynamicColorBody, style: RsType.label),
                value: settings.dynamicColor,
                onChanged: (v) => ref.read(settingsProvider).setDynamicColor(v),
              ),
            ],
          ),

          SectionHeader(label: l.settingsFeel),
          _Card(
            children: [
              SwitchListTile(
                secondary: const Icon(Icons.vibration_outlined),
                title: Text(l.settingsHaptics),
                value: settings.hapticsEnabled,
                onChanged: (v) =>
                    ref.read(settingsProvider).setHapticsEnabled(v),
              ),
              SwitchListTile(
                secondary: const Icon(Icons.local_fire_department_outlined),
                title: Text(l.settingsKeepAwake),
                subtitle: Text(l.settingsKeepAwakeBody, style: RsType.label),
                value: settings.keepScreenAwake,
                onChanged: (v) =>
                    ref.read(settingsProvider).setKeepScreenAwake(v),
              ),
              ListTile(
                leading: const Icon(Icons.translate_rounded),
                title: Text(l.settingsLanguage),
                subtitle: Text(l.settingsLanguageMore, style: RsType.label),
                trailing: Text(
                  _languageLabel(context, settings.language),
                  style: RsType.body,
                ),
                onTap: () => _pickLanguage(context, ref, settings.language),
              ),
            ],
          ),

          SectionHeader(label: l.settingsDefaults),
          _Card(
            children: [
              ListTile(
                leading: const Icon(Icons.auto_awesome_outlined),
                title: Text(l.settingsDefaultMode),
                trailing: Text(l.homeModeUpscaleTitle, style: RsType.body),
              ),
              ListTile(
                leading: const Icon(Icons.speed_rounded),
                title: Text(l.settingsCompute),
                trailing: Text(l.settingsComputeAuto, style: RsType.body),
              ),
              SwitchListTile(
                secondary: const Icon(Icons.battery_saver_outlined),
                title: Text(l.settingsThrottle),
                value: settings.throttleOnBattery,
                onChanged: (v) =>
                    ref.read(settingsProvider).setThrottleOnBattery(v),
              ),
              ListTile(
                leading: const Icon(Icons.image_outlined),
                title: Text(l.settingsOutputFormat),
                trailing: Text(l.settingsOutputFormatSame, style: RsType.body),
              ),
              SwitchListTile(
                secondary: const Icon(Icons.description_outlined),
                title: Text(l.settingsOutputMetadata),
                value: settings.keepMetadata,
                onChanged: (v) => ref.read(settingsProvider).setKeepMetadata(v),
              ),
            ],
          ),

          SectionHeader(label: l.settingsDownloads),
          _Card(
            children: [
              SwitchListTile(
                secondary: const Icon(Icons.wifi_outlined),
                title: Text(l.settingsWifiOnly),
                value: settings.wifiOnly,
                onChanged: (v) => ref.read(settingsProvider).setWifiOnly(v),
              ),
            ],
          ),

          SectionHeader(label: l.settingsStorage),
          _Card(
            children: [
              ListTile(
                leading: const Icon(Icons.cleaning_services_outlined),
                title: Text(l.settingsClearCache),
                subtitle: Text(l.settingsClearCacheBody, style: RsType.label),
                onTap: () async {
                  await ref.read(servicesProvider).storage.clearCaches();
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(l.settingsClearCacheDone)),
                    );
                  }
                },
              ),
              ListTile(
                leading: const Icon(Icons.phone_android_rounded),
                title: Text(l.settingsRerunDeviceCheck),
                subtitle: deviceAsync.value != null
                    ? Text(
                        '${l.settingsDeviceTier}: ${deviceAsync.value!.tier.uiLabel} · ${deviceAsync.value!.totalRamMb} MB',
                        style: RsType.label,
                      )
                    : null,
                onTap: () => context.push(Routes.deviceCheck),
              ),
            ],
          ),

          SectionHeader(label: l.settingsPrivacy),
          _Card(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                child: Text(
                  l.settingsPrivacyBody,
                  style: RsType.label.copyWith(color: cs.onSurfaceVariant),
                ),
              ),
              SwitchListTile(
                secondary: const Icon(Icons.bar_chart_outlined),
                title: Text(l.settingsAnonymousStats),
                value: settings.anonymousStats,
                onChanged: (v) =>
                    ref.read(settingsProvider).setAnonymousStats(v),
              ),
              SwitchListTile(
                secondary: const Icon(Icons.bug_report_outlined),
                title: Text(l.settingsCrashReports),
                value: settings.crashReports,
                onChanged: (v) => ref.read(settingsProvider).setCrashReports(v),
              ),
            ],
          ),

          SectionHeader(label: l.settingsSupport),
          _Card(
            children: [
              ListTile(
                leading: const Icon(Icons.school_outlined),
                title: Text(l.settingsReplayGuide),
                onTap: () => context.push(Routes.guide),
              ),
              ListTile(
                leading: const Icon(Icons.quiz_outlined),
                title: Text(l.settingsFaq),
                onTap: () => _showFaq(context, l),
              ),
              ListTile(
                leading: const Icon(Icons.lock_outline_rounded),
                title: Text(l.settingsPrivacyStatement),
                onTap: () => context.push(Routes.privacy),
              ),
              ListTile(
                leading: const Icon(Icons.memory_outlined),
                title: Text(l.settingsModelCredits),
                onTap: () => _showCredits(context, l),
              ),
              ListTile(
                leading: const Icon(Icons.info_outline_rounded),
                title: Text(l.settingsAbout),
                onTap: () => context.push(Routes.about),
              ),
              ListTile(
                leading: const Icon(Icons.article_outlined),
                title: Text(l.settingsLicenses),
                onTap: () => context.push(Routes.licenses),
              ),
            ],
          ),
          const SizedBox(height: RsSpacing.xxl),
        ],
      ),
    );
  }

  void _showFaq(BuildContext context, AppLocalizations l) {
    AppBottomSheet.show(
      context,
      title: l.faqTitle,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          RsSpacing.xl,
          0,
          RsSpacing.xl,
          RsSpacing.xl,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l.faqQ1, style: RsType.bodyMedium),
            Text(
              l.faqA1,
              style: RsType.body.copyWith(color: cs(context).onSurfaceVariant),
            ),
            const SizedBox(height: RsSpacing.l),
            Text(l.faqQ2, style: RsType.bodyMedium),
            Text(
              l.faqA2,
              style: RsType.body.copyWith(color: cs(context).onSurfaceVariant),
            ),
            const SizedBox(height: RsSpacing.l),
            Text(l.faqQ3, style: RsType.bodyMedium),
            Text(
              l.faqA3,
              style: RsType.body.copyWith(color: cs(context).onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }

  void _showCredits(BuildContext context, AppLocalizations l) {
    AppBottomSheet.show(
      context,
      title: l.settingsModelCredits,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          RsSpacing.xl,
          0,
          RsSpacing.xl,
          RsSpacing.xl,
        ),
        child: Text(l.settingsCreditsBody, style: RsType.body),
      ),
    );
  }

  ColorScheme cs(BuildContext context) => Theme.of(context).colorScheme;
}

class _Card extends StatelessWidget {
  const _Card({required this.children});
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: cs.surface,
        borderRadius: RsRadius.cardBorder,
        border: Border.all(color: cs.outline),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(children: children),
    );
  }
}

/// Language picker (Phase 4): System / English / हिन्दी — persisted via
/// SettingsService and applied by MaterialApp.router immediately.
String _languageLabel(BuildContext context, String value) {
  final l = AppLocalizations.of(context)!;
  return switch (value) {
    'en' => l.settingsLanguageEnglish,
    'hi' => l.settingsLanguageHindi,
    _ => l.settingsLanguageSystem,
  };
}

Future<void> _pickLanguage(
  BuildContext context,
  WidgetRef ref,
  String current,
) async {
  final l = AppLocalizations.of(context)!;
  final options = <(String, String)>[
    ('system', l.settingsLanguageSystem),
    ('en', l.settingsLanguageEnglish),
    ('hi', l.settingsLanguageHindi),
  ];
  final picked = await showModalBottomSheet<String>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: RsSpacing.s),
            child: Text(l.settingsLanguagePick, style: RsType.bodyMedium),
          ),
          for (final (value, label) in options)
            ListTile(
              title: Text(label),
              trailing: value == current
                  ? const Icon(Icons.check_rounded)
                  : null,
              onTap: () => Navigator.of(sheetContext).pop(value),
            ),
        ],
      ),
    ),
  );
  if (picked != null) {
    await ref.read(settingsProvider).setLanguage(picked);
  }
}
