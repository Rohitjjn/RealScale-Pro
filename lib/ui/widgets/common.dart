import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../theme/tokens.dart';

/// Premium gradient primary button (design-system §8: gradient background,
/// white label, optional second line).
class PrimaryActionBar extends StatelessWidget {
  const PrimaryActionBar({
    super.key,
    required this.label,
    this.subtitle,
    required this.onPressed,
    this.enabled = true,
    this.icon,
  });

  final String label;
  final String? subtitle;
  final VoidCallback? onPressed;
  final bool enabled;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Opacity(
      opacity: enabled ? 1 : 0.5,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: RsColors.brandGradient(dark: dark),
          borderRadius: RsRadius.pill,
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            borderRadius: RsRadius.pill,
            onTap: enabled ? onPressed : null,
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: RsSpacing.xl,
                vertical: 12,
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (icon != null) ...[
                    Icon(icon, color: Colors.white, size: 20),
                    const SizedBox(width: RsSpacing.s),
                  ],
                  Text(
                    label,
                    style: RsType.bodyMedium.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Small info chip (InfoChip component).
class InfoChip extends StatelessWidget {
  const InfoChip({
    super.key,
    required this.label,
    this.icon,
    this.tone = InfoChipTone.neutral,
  });

  final String label;
  final IconData? icon;
  final InfoChipTone tone;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final color = switch (tone) {
      InfoChipTone.neutral => cs.onSurfaceVariant,
      InfoChipTone.brand => cs.primary,
      InfoChipTone.success =>
        cs.brightness == Brightness.dark
            ? RsColors.successDark
            : RsColors.successLight,
      InfoChipTone.warning =>
        cs.brightness == Brightness.dark
            ? RsColors.warningDark
            : RsColors.warningLight,
      InfoChipTone.error =>
        cs.brightness == Brightness.dark
            ? RsColors.errorDark
            : RsColors.errorLight,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: RsRadius.pill,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) Icon(icon, size: 14, color: color),
          if (icon != null) const SizedBox(width: 4),
          Text(
            label,
            style: RsType.label.copyWith(
              color: color,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

enum InfoChipTone { neutral, brand, success, warning, error }

/// Bottom sheet with 28 dp top radius, drag handle, safe-area padding.
class AppBottomSheet extends StatelessWidget {
  const AppBottomSheet({super.key, required this.child, this.title});

  final String? title;
  final Widget child;

  static Future<T?> show<T>(
    BuildContext context, {
    String? title,
    required Widget child,
  }) {
    return showModalBottomSheet<T>(
      context: context,
      useSafeArea: true,
      isScrollControlled: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
      shape: const RoundedRectangleBorder(borderRadius: RsRadius.sheetBorder),
      builder: (_) => AppBottomSheet(title: title, child: child),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.viewPaddingOf(context).bottom,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              margin: const EdgeInsets.only(top: 12, bottom: 8),
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.outline,
                borderRadius: RsRadius.pill,
              ),
            ),
          ),
          if (title != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(
                RsSpacing.xl,
                RsSpacing.s,
                RsSpacing.xl,
                RsSpacing.s,
              ),
              child: Text(title!, style: RsType.title),
            ),
          Flexible(child: child),
        ],
      ),
    );
  }
}

/// Skeleton shimmer loader (design-system §6: shimmer, no spinners).
class ShimmerBox extends StatefulWidget {
  const ShimmerBox({
    super.key,
    this.width = double.infinity,
    this.height = 100,
    this.radius = 20,
  });

  final double width;
  final double height;
  final double radius;

  @override
  State<ShimmerBox> createState() => _ShimmerBoxState();
}

class _ShimmerBoxState extends State<ShimmerBox>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1400),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        final t = _controller.value;
        return Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(widget.radius),
            gradient: LinearGradient(
              begin: Alignment(-1 + 2 * t, 0),
              end: Alignment(0 + 2 * t, 0),
              colors: [
                cs.surfaceContainerHighest,
                cs.outline.withValues(alpha: 0.5),
                cs.surfaceContainerHighest,
              ],
            ),
          ),
        );
      },
    );
  }
}

/// Designed empty state: friendly line + one sentence + one action
/// (prd §10.2, task 1.19).
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.body,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String body;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(RsSpacing.xxl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: cs.primary.withValues(alpha: 0.08),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 32, color: cs.primary),
            ),
            const SizedBox(height: RsSpacing.l),
            Text(title, style: RsType.title, textAlign: TextAlign.center),
            const SizedBox(height: RsSpacing.s),
            Text(
              body,
              style: RsType.body.copyWith(color: cs.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: RsSpacing.xl),
              FilledButton(onPressed: onAction, child: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Section header for grouped lists.
class SectionHeader extends StatelessWidget {
  const SectionHeader({super.key, required this.label, this.trailing});

  final String label;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(
        left: 4,
        right: 4,
        top: RsSpacing.xl,
        bottom: RsSpacing.m,
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label.toUpperCase(),
              style: RsType.label.copyWith(
                color: cs.onSurfaceVariant,
                letterSpacing: 1.1,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}

/// Designed error state with retry (task 1.19).
class ErrorState extends StatelessWidget {
  const ErrorState({super.key, required this.onRetry, this.body});
  final VoidCallback onRetry;
  final String? body;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return EmptyState(
      icon: Icons.error_outline_rounded,
      title: l.errorGenericTitle,
      body: body ?? l.errorGenericBody,
      actionLabel: l.retry,
      onAction: onRetry,
    );
  }
}

/// Formats seconds as "42 s" / "3 min" / "1 min 20 s".
String formatDuration(AppLocalizations l, int seconds) {
  if (seconds < 60) return l.durationSeconds(seconds);
  final m = seconds ~/ 60;
  final s = seconds % 60;
  return s == 0 ? l.durationMinutes(m) : l.durationMinutesSeconds(m, s);
}

/// Formats bytes as KB/MB/GB.
String formatBytes(int bytes) {
  if (bytes >= 1024 * 1024 * 1024) {
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }
  if (bytes >= 1024 * 1024) {
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
  return '${(bytes / 1024).toStringAsFixed(0)} KB';
}

/// Formats a download speed (bytes/s).
String formatSpeed(int bytesPerSec) => '${formatBytes(bytesPerSec)}/s';

/// Subtle grid overlay used on the processing screen (design-system §8).
class TileGridOverlay extends StatelessWidget {
  const TileGridOverlay({super.key, required this.fill, this.columns = 4});

  /// 0..1 — how much of the grid is filled (from real tile progress).
  final double fill;
  final int columns;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return LayoutBuilder(
      builder: (context, constraints) {
        const gap = 6.0;
        // Fit BOTH dimensions: on wide screens (desktop minimum 800x600) a
        // width-only cell size made the 4x4 grid taller than the viewport.
        final gaps = gap * (columns - 1);
        var cell = (constraints.maxWidth - gaps) / columns;
        final maxH = constraints.maxHeight;
        if (maxH.isFinite) {
          cell = math.min(cell, (maxH - gaps) / columns);
        }
        final size = cell.clamp(8.0, 240.0).toDouble();
        final total = columns * columns;
        final filled = (fill * total).clamp(0, total);
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: List.generate(total, (i) {
            final isFilled = i < filled;
            return Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                color: isFilled
                    ? cs.primary.withValues(alpha: 0.85)
                    : cs.outline.withValues(alpha: 0.4),
                borderRadius: BorderRadius.circular(10),
              ),
            );
          }),
        );
      },
    );
  }
}

/// Giant percent display for the processing screen.
class BigPercent extends StatelessWidget {
  const BigPercent({super.key, required this.percent});
  final double percent; // 0..1

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final value = (percent * 100).round().clamp(0, 100);
    return RichText(
      text: TextSpan(
        children: [
          TextSpan(
            text: '$value',
            style: RsType.display.copyWith(
              fontSize: 72,
              height: 1.0,
              color: cs.onSurface,
            ),
          ),
          TextSpan(
            text: '%',
            style: RsType.headline.copyWith(color: cs.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

/// Tiny helper used by a few list rows.
double clamp01(double v) => math.min(1, math.max(0, v));
