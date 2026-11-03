import 'dart:io';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../theme/app_theme.dart';

extension LocalizedContext on BuildContext {
  AppLocalizations get l10n => AppLocalizations.of(this);
}

class InfoChip extends StatelessWidget {
  const InfoChip({required this.icon, required this.label, super.key});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) => Semantics(
        label: label,
        child: Chip(
          avatar: Icon(icon, size: 18),
          label: Text(label),
          side: BorderSide.none,
        ),
      );
}

class PrimaryActionBar extends StatelessWidget {
  const PrimaryActionBar({
    required this.label,
    required this.onPressed,
    this.subtitle,
    super.key,
  });

  final String label;
  final String? subtitle;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) => SafeArea(
        top: false,
        minimum: const EdgeInsets.all(AppTokens.spacing),
        child: SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: onPressed,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text(label),
                  if (subtitle != null)
                    Text(
                      subtitle!,
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                            color: Colors.white.withValues(alpha: 0.85),
                          ),
                    ),
                ],
              ),
            ),
          ),
        ),
      );
}

class ImportTile extends StatelessWidget {
  const ImportTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
    super.key,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
        child: InkWell(
          borderRadius: BorderRadius.circular(AppTokens.radiusCard),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Icon(icon, color: Theme.of(context).colorScheme.primary),
                const SizedBox(height: 12),
                Text(title, style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 4),
                Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
          ),
        ),
      );
}

class ModeCard extends StatelessWidget {
  const ModeCard({
    required this.title,
    required this.subtitle,
    required this.selected,
    required this.onTap,
    this.badge,
    super.key,
  });

  final String title;
  final String subtitle;
  final bool selected;
  final String? badge;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Semantics(
        selected: selected,
        button: true,
        child: Card(
          shape: RoundedRectangleBorder(
            side: BorderSide(
              color: selected ? AppColors.blue : Colors.transparent,
              width: 2,
            ),
            borderRadius: BorderRadius.circular(AppTokens.radiusCard),
          ),
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(AppTokens.radiusCard),
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Row(
                children: <Widget>[
                  Icon(selected ? Icons.check_circle_rounded : Icons.circle_outlined),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Row(
                          children: <Widget>[
                            Flexible(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
                            if (badge != null) ...<Widget>[
                              const SizedBox(width: 8),
                              Chip(label: Text(badge!)),
                            ],
                          ],
                        ),
                        Text(subtitle),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
}

class ImagePreview extends StatelessWidget {
  const ImagePreview({required this.path, this.fit = BoxFit.cover, super.key});

  final String path;
  final BoxFit fit;

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(AppTokens.radiusCard),
        child: Image.file(
          File(path),
          fit: fit,
          errorBuilder: (BuildContext context, Object error, StackTrace? stackTrace) =>
              ColoredBox(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: const Center(child: Icon(Icons.broken_image_outlined, size: 48)),
          ),
        ),
      );
}

Future<void> showAppSheet(BuildContext context, Widget child) => showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (BuildContext context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          child: child,
        ),
      ),
    );
