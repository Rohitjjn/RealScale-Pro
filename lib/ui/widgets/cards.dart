import 'dart:typed_data';
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../theme/tokens.dart';

/// ModeCard — the two big mode cards on Home (design-system §5, prd §4.5):
/// Upscale (selected: violet gradient outline) and Super Resolution (PRO pill).
class ModeCard extends StatefulWidget {
  const ModeCard({
    super.key,
    required this.title,
    required this.body,
    required this.selected,
    this.proBadge = false,
    required this.onTap,
  });

  final String title;
  final String body;
  final bool selected;
  final bool proBadge;
  final VoidCallback onTap;

  @override
  State<ModeCard> createState() => _ModeCardState();
}

class _ModeCardState extends State<ModeCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final cs = Theme.of(context).colorScheme;
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapCancel: () => setState(() => _pressed = false),
      onTapUp: (_) {
        setState(() => _pressed = false);
        widget.onTap();
      },
      child: AnimatedScale(
        scale: _pressed ? 0.98 : 1,
        duration: RsMotion.micro,
        curve: RsMotion.spring,
        child: AnimatedContainer(
          duration: RsMotion.micro,
          curve: RsMotion.spring,
          padding: const EdgeInsets.all(RsSpacing.l),
          decoration: BoxDecoration(
            color: widget.selected
                ? cs.primary.withValues(alpha: 0.06)
                : cs.surface,
            borderRadius: RsRadius.cardBorder,
            border: Border.all(
              color: widget.selected ? cs.primary : Colors.transparent,
              width: 2,
            ),
          ),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  gradient: widget.selected
                      ? RsColors.brandGradient(dark: dark)
                      : null,
                  color: widget.selected ? null : cs.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Icon(
                  widget.selected
                      ? Icons.auto_awesome_rounded
                      : Icons.auto_awesome_outlined,
                  color: widget.selected ? Colors.white : cs.onSurfaceVariant,
                  size: 22,
                ),
              ),
              const SizedBox(width: RsSpacing.l),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            widget.title,
                            style: RsType.title,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (widget.proBadge) ...[
                          const SizedBox(width: RsSpacing.s),
                          const ProPill(),
                        ],
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      widget.body,
                      style: RsType.body.copyWith(color: cs.onSurfaceVariant),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              AnimatedContainer(
                duration: RsMotion.micro,
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: widget.selected ? cs.primary : Colors.transparent,
                  border: Border.all(
                    color: widget.selected ? cs.primary : cs.outline,
                    width: 2,
                  ),
                ),
                child: widget.selected
                    ? const Icon(
                        Icons.check_rounded,
                        size: 14,
                        color: Colors.white,
                      )
                    : null,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// PRO pill badge (locked feature signal).
class ProPill extends StatelessWidget {
  const ProPill({super.key, this.label});
  final String? label;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        gradient: RsColors.brandGradient(dark: dark),
        borderRadius: RsRadius.pill,
      ),
      child: Text(
        label ?? l.proBadge,
        style: RsType.label.copyWith(
          color: Colors.white,
          fontSize: 10,
          letterSpacing: 0.6,
        ),
      ),
    );
  }
}

/// RecentPhotoCard — thumbnail + Upscale button + resolution badge
/// (prd §4.7: one tap to config).
class RecentPhotoCard extends StatelessWidget {
  const RecentPhotoCard({
    super.key,
    required this.thumbnail,
    required this.resolutionLabel,
    required this.onOpen,
    required this.onUpscale,
  });

  final Uint8List? thumbnail;
  final String resolutionLabel;
  final VoidCallback onOpen;
  final VoidCallback onUpscale;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: onOpen,
      child: ClipRRect(
        borderRadius: RsRadius.cardBorder,
        child: SizedBox(
          width: 132,
          height: 176,
          child: Stack(
            fit: StackFit.expand,
            children: [
              thumbnail != null
                  ? Image.memory(
                      thumbnail!,
                      fit: BoxFit.cover,
                      gaplessPlayback: true,
                    )
                  : Container(color: cs.surfaceContainerHighest),
              Positioned(
                top: 8,
                right: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 7,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.55),
                    borderRadius: RsRadius.pill,
                  ),
                  child: Text(
                    resolutionLabel,
                    style: RsType.label.copyWith(
                      color: Colors.white,
                      fontSize: 10,
                      fontFeatures: const [TabularFigures()],
                    ),
                  ),
                ),
              ),
              Positioned(
                bottom: 8,
                left: 8,
                right: 8,
                child: _UpscaleMiniButton(onPressed: onUpscale),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// Tabular figures font feature (kept local to avoid exporting fonts).
class TabularFigures extends FontFeature {
  const TabularFigures() : super('tnum', 1);
}

class _UpscaleMiniButton extends StatelessWidget {
  const _UpscaleMiniButton({required this.onPressed});
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onPressed,
        borderRadius: RsRadius.pill,
        child: Ink(
          height: 34,
          decoration: BoxDecoration(
            gradient: RsColors.brandGradient(dark: dark),
            borderRadius: RsRadius.pill,
          ),
          child: Center(
            child: Text(
              l.viewerUpscale,
              style: RsType.label.copyWith(
                color: Colors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// ImportTile — the 2x2 grid (Gallery / Files / Camera / More).
class ImportTile extends StatelessWidget {
  const ImportTile({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Material(
      color: cs.surface,
      borderRadius: RsRadius.cardBorder,
      child: InkWell(
        onTap: onTap,
        borderRadius: RsRadius.cardBorder,
        child: Padding(
          padding: const EdgeInsets.all(RsSpacing.l),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 26, color: cs.primary),
              const SizedBox(height: RsSpacing.s),
              Text(
                label,
                style: RsType.label.copyWith(fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
