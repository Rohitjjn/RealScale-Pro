import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../theme/tokens.dart';
import 'cards.dart' show TabularFigures;
import 'common.dart';

/// LevelSegmentedControl — Default / Manual / Advanced with spring motion
/// (design-system §6). Advanced shows locked state until Phase 3.
class LevelSegmentedControl extends StatelessWidget {
  const LevelSegmentedControl({
    super.key,
    required this.selected,
    required this.onChanged,
    this.showAdvanced = false,
    this.showManual = true,
  });

  final SettingLevelView selected;
  final ValueChanged<SettingLevelView> onChanged;
  final bool showAdvanced;
  final bool showManual;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final entries = <(SettingLevelView, String)>[
      (SettingLevelView.defaultLevel, l.configLevelDefault),
      if (showManual) (SettingLevelView.manual, l.configLevelManual),
      if (showAdvanced) (SettingLevelView.advanced, l.configLevelAdvanced),
    ];
    return Container(
      height: 48,
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest,
        borderRadius: RsRadius.pill,
      ),
      child: Row(
        children: entries
            .map(
              (e) => _Segment(
                label: e.$2,
                selected: selected == e.$1,
                onTap: () => onChanged(e.$1),
              ),
            )
            .toList(),
      ),
    );
  }
}

enum SettingLevelView { defaultLevel, manual, advanced }

class _Segment extends StatelessWidget {
  const _Segment({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final cs = Theme.of(context).colorScheme;
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: RsMotion.micro,
          curve: RsMotion.spring,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            gradient: selected ? RsColors.brandGradient(dark: dark) : null,
            color: selected ? null : Colors.transparent,
            borderRadius: RsRadius.pill,
          ),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: RsType.label.copyWith(
              color: selected ? Colors.white : cs.onSurfaceVariant,
              fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }
}

/// ScaleChips — 2x…5x pill chips with live output info beneath (task 2.5).
class ScaleChips extends StatelessWidget {
  const ScaleChips({
    super.key,
    required this.selected,
    required this.onChanged,
    this.enabled = const [true, true, true, true],
  });

  final int selected; // 2..5
  final ValueChanged<int> onChanged;
  final List<bool> enabled;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(4, (i) {
        final scale = i + 2;
        final isSel = scale == selected;
        final isEnabled = enabled[i];
        final cs = Theme.of(context).colorScheme;
        return Expanded(
          child: Padding(
            padding: EdgeInsets.only(right: i == 3 ? 0 : RsSpacing.s),
            child: GestureDetector(
              onTap: isEnabled ? () => onChanged(scale) : null,
              child: AnimatedContainer(
                duration: RsMotion.micro,
                curve: RsMotion.spring,
                height: 44,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: isSel
                      ? cs.primary
                      : isEnabled
                      ? cs.surface
                      : cs.surfaceContainerHighest.withValues(alpha: 0.5),
                  borderRadius: RsRadius.pill,
                  border: Border.all(
                    color: isSel ? cs.primary : cs.outline,
                    width: isSel ? 0 : 1,
                  ),
                ),
                child: Text(
                  '$scale×',
                  style: RsType.bodyMedium.copyWith(
                    color: isSel
                        ? Colors.white
                        : isEnabled
                        ? cs.onSurface
                        : cs.onSurfaceVariant.withValues(alpha: 0.5),
                  ),
                ),
              ),
            ),
          ),
        );
      }),
    );
  }
}

/// ModelListRow — installed / downloading / not-installed / unavailable
/// states (design-system §5, tasks 2.4/2.5).
class ModelListRow extends StatelessWidget {
  const ModelListRow({
    super.key,
    required this.name,
    required this.sizeLabel,
    required this.status,
    this.progress = 0,
    this.speedLabel,
    this.badge,
    this.selected = false,
    this.onTap,
    this.onAction,
    this.actionLabel,
    this.subtitle,
  });

  final String name;
  final String sizeLabel;
  final ModelRowStatus status;
  final double progress;
  final String? speedLabel;
  final String? badge; // Recommended / Fits device / Heavy
  final bool selected;
  final VoidCallback? onTap;
  final VoidCallback? onAction;
  final String? actionLabel;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l = AppLocalizations.of(context)!;
    final statusLabel = switch (status) {
      ModelRowStatus.installed => l.modelManagerStatusInstalled,
      ModelRowStatus.notInstalled => l.modelManagerStatusNotInstalled,
      ModelRowStatus.downloading => l.modelManagerStatusDownloading,
      ModelRowStatus.paused => l.modelManagerStatusPaused,
      ModelRowStatus.error => l.modelManagerStatusError,
      ModelRowStatus.unavailable => l.modelManagerStatusUnavailable,
    };
    return Material(
      color: selected ? cs.primary.withValues(alpha: 0.06) : cs.surface,
      borderRadius: RsRadius.cardBorder,
      child: InkWell(
        onTap: onTap,
        borderRadius: RsRadius.cardBorder,
        child: Padding(
          padding: const EdgeInsets.all(RsSpacing.l),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      name,
                      style: RsType.bodyMedium,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (badge != null) ...[
                    const SizedBox(width: RsSpacing.s),
                    InfoChip(label: badge!, tone: InfoChipTone.brand),
                  ],
                  const SizedBox(width: RsSpacing.s),
                  Text(
                    sizeLabel,
                    style: RsType.label.copyWith(color: cs.onSurfaceVariant),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  _statusIcon(cs, status),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      subtitle ?? statusLabel,
                      style: RsType.label.copyWith(
                        color: status == ModelRowStatus.error
                            ? cs.error
                            : cs.onSurfaceVariant,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (actionLabel != null && onAction != null)
                    TextButton(
                      onPressed: onAction,
                      child: Text(
                        actionLabel!,
                        style: RsType.label.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                ],
              ),
              if (status == ModelRowStatus.downloading ||
                  status == ModelRowStatus.paused) ...[
                const SizedBox(height: RsSpacing.s),
                ClipRRect(
                  borderRadius: RsRadius.pill,
                  child: LinearProgressIndicator(
                    value: clamp01(progress),
                    minHeight: 5,
                  ),
                ),
                if (speedLabel != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    speedLabel!,
                    style: RsType.label.copyWith(
                      color: cs.onSurfaceVariant,
                      fontFeatures: const [TabularFigures()],
                    ),
                  ),
                ],
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _statusIcon(ColorScheme cs, ModelRowStatus status) {
    final dark = cs.brightness == Brightness.dark;
    switch (status) {
      case ModelRowStatus.installed:
        return Icon(
          Icons.check_circle_rounded,
          size: 16,
          color: dark ? RsColors.successDark : RsColors.successLight,
        );
      case ModelRowStatus.error:
        return Icon(
          Icons.error_rounded,
          size: 16,
          color: dark ? RsColors.errorDark : RsColors.errorLight,
        );
      case ModelRowStatus.unavailable:
        return Icon(Icons.block_rounded, size: 16, color: cs.onSurfaceVariant);
      default:
        return SizedBox(
          width: 14,
          height: 14,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            value: status == ModelRowStatus.paused ? 0 : null,
          ),
        );
    }
  }
}

enum ModelRowStatus {
  installed,
  notInstalled,
  downloading,
  paused,
  error,
  unavailable,
}

/// FaceToggleRow — toggle + detector hint + Face pack download flow
/// (tasks 2.7/2.8).
class FaceToggleRow extends StatelessWidget {
  const FaceToggleRow({
    super.key,
    required this.value,
    required this.onChanged,
    required this.hint,
    this.enabled = true,
    this.downloadLabel,
    this.onDownload,
  });

  final bool value;
  final ValueChanged<bool> onChanged;
  final String hint;
  final bool enabled;
  final String? downloadLabel;
  final VoidCallback? onDownload;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(RsSpacing.l),
      decoration: BoxDecoration(
        color: cs.surface,
        borderRadius: RsRadius.cardBorder,
        border: Border.all(color: cs.outline),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      AppLocalizations.of(context)!.configFaceToggle,
                      style: RsType.bodyMedium,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      hint,
                      style: RsType.label.copyWith(color: cs.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
              Switch(value: value, onChanged: enabled ? onChanged : null),
            ],
          ),
          if (downloadLabel != null && onDownload != null) ...[
            const SizedBox(height: RsSpacing.m),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                onPressed: onDownload,
                child: Text(downloadLabel!),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// PermissionPrimer — full-screen pre-dialog page (prd §3.6, task 1.5).
class PermissionPrimer extends StatelessWidget {
  const PermissionPrimer({
    super.key,
    required this.icon,
    required this.title,
    required this.body,
    required this.allowLabel,
    required this.notNowLabel,
    required this.onAllow,
    required this.onNotNow,
  });

  final IconData icon;
  final String title;
  final String body;
  final String allowLabel;
  final String notNowLabel;
  final VoidCallback onAllow;
  final VoidCallback onNotNow;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final dark = Theme.of(context).brightness == Brightness.dark;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(RsSpacing.xl),
        child: Column(
          children: [
            const Spacer(),
            Container(
              width: 96,
              height: 96,
              decoration: BoxDecoration(
                gradient: RsColors.brandGradient(dark: dark),
                borderRadius: BorderRadius.circular(28),
              ),
              child: Icon(icon, size: 42, color: Colors.white),
            ),
            const SizedBox(height: RsSpacing.xl),
            Text(title, style: RsType.headline, textAlign: TextAlign.center),
            const SizedBox(height: RsSpacing.m),
            Text(
              body,
              style: RsType.body.copyWith(color: cs.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            const Spacer(),
            SizedBox(
              width: double.infinity,
              child: FilledButton(onPressed: onAllow, child: Text(allowLabel)),
            ),
            const SizedBox(height: RsSpacing.s),
            SizedBox(
              width: double.infinity,
              child: TextButton(onPressed: onNotNow, child: Text(notNowLabel)),
            ),
          ],
        ),
      ),
    );
  }
}

/// BeforeAfterSlider — drag divider + hold-to-compare (task 1.15, prd §7).
class BeforeAfterSlider extends StatefulWidget {
  const BeforeAfterSlider({
    super.key,
    required this.before,
    required this.after,
    this.height = 320,
  });

  final Widget before;
  final Widget after;
  final double height;

  @override
  State<BeforeAfterSlider> createState() => _BeforeAfterSliderState();
}

class _BeforeAfterSliderState extends State<BeforeAfterSlider> {
  double _position = 0.5;
  bool _holdCompare = false;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l = AppLocalizations.of(context)!;
    // LayoutBuilder, not context.size: `context.size` is only valid during
    // paint, so reading it inside build() throws (and would crash the result
    // screen on every render).
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final safeWidth = width.isFinite && width > 0 ? width : 1.0;
        return GestureDetector(
          onHorizontalDragUpdate: (d) => setState(() {
            _position = (d.localPosition.dx / safeWidth).clamp(0.05, 0.95);
          }),
          onLongPressStart: (_) => setState(() => _holdCompare = true),
          onLongPressEnd: (_) => setState(() => _holdCompare = false),
          child: ClipRRect(
            borderRadius: RsRadius.cardBorder,
            child: SizedBox(
              height: widget.height,
              width: double.infinity,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  widget.before,
                  if (!_holdCompare)
                    ClipRect(
                      clipper: _RightClip(_position),
                      child: widget.after,
                    ),
                  if (!_holdCompare)
                    Positioned(
                      left: (_position * safeWidth - 1).clamp(
                        0.0,
                        safeWidth - 2,
                      ),
                      top: 0,
                      bottom: 0,
                      width: 2,
                      child: Container(color: Colors.white),
                    ),
                  Positioned(
                    bottom: 12,
                    left: 12,
                    child: _ComparePill(label: l.resultBefore),
                  ),
                  Positioned(
                    bottom: 12,
                    right: 12,
                    child: _ComparePill(label: l.resultAfter),
                  ),
                  Positioned(
                    top: 0,
                    bottom: 0,
                    left: (_position * safeWidth - 16).clamp(
                      0.0,
                      safeWidth - 32,
                    ),
                    width: 32,
                    child: Center(
                      child: Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.9),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.drag_indicator_rounded,
                          size: 16,
                          color: cs.onSurface,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _RightClip extends CustomClipper<Rect> {
  _RightClip(this.position);
  final double position;

  @override
  Rect getClip(Size size) => Rect.fromLTWH(
    size.width * position,
    0,
    size.width * (1 - position),
    size.height,
  );

  @override
  bool shouldReclip(_RightClip old) => old.position != position;
}

class _ComparePill extends StatelessWidget {
  const _ComparePill({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.55),
        borderRadius: RsRadius.pill,
      ),
      child: Text(
        label,
        style: RsType.label.copyWith(color: Colors.white, fontSize: 11),
      ),
    );
  }
}
