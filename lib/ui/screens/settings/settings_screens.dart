import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../../../services/settings_service.dart';
import '../../widgets/core_widgets.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({required this.settings, super.key});
  final SettingsService settings;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: settings,
    builder: (BuildContext context, Widget? child) => Scaffold(
      appBar: AppBar(title: Text(context.l10n.settings)),
      body: ListView(
        children: <Widget>[
          ListTile(
            leading: const Icon(Icons.palette_rounded),
            title: Text(context.l10n.theme),
            trailing: DropdownButton<ThemeMode>(
              value: settings.themeMode,
              onChanged: (ThemeMode? value) {
                if (value != null) settings.setThemeMode(value);
              },
              items: <DropdownMenuItem<ThemeMode>>[
                DropdownMenuItem<ThemeMode>(
                  value: ThemeMode.system,
                  child: Text(context.l10n.systemTheme),
                ),
                DropdownMenuItem<ThemeMode>(
                  value: ThemeMode.light,
                  child: Text(context.l10n.lightTheme),
                ),
                DropdownMenuItem<ThemeMode>(
                  value: ThemeMode.dark,
                  child: Text(context.l10n.darkTheme),
                ),
              ],
            ),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.color_lens_outlined),
            title: Text(context.l10n.dynamicColor),
            value: settings.dynamicColor,
            onChanged: settings.setDynamicColor,
          ),
          SwitchListTile(
            secondary: const Icon(Icons.vibration_rounded),
            title: Text(context.l10n.haptics),
            value: settings.haptics,
            onChanged: settings.setHaptics,
          ),
          SwitchListTile(
            secondary: const Icon(Icons.light_mode_outlined),
            title: Text(context.l10n.keepAwake),
            value: settings.keepAwake,
            onChanged: settings.setKeepAwake,
          ),
          ListTile(
            leading: const Icon(Icons.language_rounded),
            title: Text(context.l10n.language),
            trailing: Text(context.l10n.english),
          ),
          ListTile(
            leading: const Icon(Icons.slideshow_rounded),
            title: Text(context.l10n.replayGuide),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => context.go('/guide?replay=true'),
          ),
          ListTile(
            leading: const Icon(Icons.info_outline_rounded),
            title: Text(context.l10n.aboutLicenses),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => context.go('/about'),
          ),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Text(context.l10n.privacyStatement),
          ),
        ],
      ),
    ),
  );
}

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(context.l10n.aboutLicenses)),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: <Widget>[
        const Icon(Icons.auto_awesome_rounded, size: 72),
        const SizedBox(height: 12),
        Text(
          context.l10n.appName,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        FutureBuilder<PackageInfo>(
          future: PackageInfo.fromPlatform(),
          builder:
              (BuildContext context, AsyncSnapshot<PackageInfo> snapshot) =>
                  Text(
                    context.l10n.appVersion(snapshot.data?.version ?? '0.1.0'),
                    textAlign: TextAlign.center,
                  ),
        ),
        const SizedBox(height: 24),
        Text(context.l10n.privacyStatement),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: () => showLicensePage(
            context: context,
            applicationName: context.l10n.appName,
            applicationVersion: '0.1.0',
          ),
          child: Text(context.l10n.openSourceLicenses),
        ),
      ],
    ),
  );
}

class SimpleInfoScreen extends StatelessWidget {
  const SimpleInfoScreen({required this.kind, super.key});
  final String kind;

  @override
  Widget build(BuildContext context) {
    final bool models = kind == 'models';
    return Scaffold(
      appBar: AppBar(
        title: Text(
          models ? context.l10n.modelManager : context.l10n.helpFeedback,
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          if (models) ...<Widget>[
            _ModelRow(name: context.l10n.starterPhoto, size: '1.1 MB'),
            _ModelRow(name: context.l10n.starterAnime, size: '2.6 MB'),
          ] else
            Card(
              child: ListTile(
                leading: const Icon(Icons.lock_rounded),
                title: Text(context.l10n.privacy),
                subtitle: Text(context.l10n.privacyStatement),
              ),
            ),
        ],
      ),
    );
  }
}

class _ModelRow extends StatelessWidget {
  const _ModelRow({required this.name, required this.size});
  final String name;
  final String size;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: const Icon(Icons.check_circle_rounded, color: Colors.green),
      title: Text(name),
      subtitle: Text(size),
      trailing: Text(context.l10n.done),
    ),
  );
}
