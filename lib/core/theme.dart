/// RealScale-Pro — Paper-Crafted design system.
///
/// Light: warm ivory / parchment surfaces, ink text, a restrained
/// terracotta accent. Dark: deep graphite surfaces with the same
/// editorial typography. Motion is intentional and subtle, and the
/// components honor `MediaQuery.disableAnimations`.
library;

import 'package:flutter/material.dart';

abstract final class RsColors {
  // Light (paper)
  static const paperBackground = Color(0xFFF7F3EC);
  static const paperSurface = Color(0xFFFCFAF5);
  static const paperSurfaceAlt = Color(0xFFEFE9DE);
  static const paperBorder = Color(0xFFDCD3C3);
  static const inkPrimary = Color(0xFF2B2723);
  static const inkSecondary = Color(0xFF6B6257);
  static const accent = Color(0xFFB4552D); // terracotta
  static const accentSoft = Color(0xFFE8D9CE);
  static const success = Color(0xFF3E6B4F);
  static const warning = Color(0xFF9A6B2F);
  static const error = Color(0xFF9C3D2E);

  // Dark (graphite)
  static const graphiteBackground = Color(0xFF211F1D);
  static const graphiteSurface = Color(0xFF2B2825);
  static const graphiteSurfaceAlt = Color(0xFF35312D);
  static const graphiteBorder = Color(0xFF45403A);
  static const graphiteInk = Color(0xFFEDE7DD);
  static const graphiteInkSecondary = Color(0xFFA69C8E);
  static const accentDark = Color(0xFFD07A50);
}

abstract final class RsType {
  static const display = TextStyle(
    fontFamily: 'SerifTools',
    fontSize: 30,
    height: 1.15,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.5,
  );
  static const headline = TextStyle(
    fontFamily: 'SerifTools',
    fontSize: 22,
    height: 1.25,
    fontWeight: FontWeight.w600,
  );
  static const title = TextStyle(
    fontSize: 16.5,
    height: 1.3,
    fontWeight: FontWeight.w600,
    letterSpacing: 0.1,
  );
  static const body = TextStyle(
    fontSize: 14.5,
    height: 1.5,
    fontWeight: FontWeight.w400,
  );
  static const caption = TextStyle(
    fontSize: 12.5,
    height: 1.4,
    fontWeight: FontWeight.w400,
    letterSpacing: 0.2,
  );
  static const mono = TextStyle(
    fontFamily: 'MonoTools',
    fontSize: 12.5,
    height: 1.4,
    fontFeatures: [FontFeature.tabularFigures()],
  );
}

abstract final class RsSpacing {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 16.0;
  static const lg = 24.0;
  static const xl = 32.0;
  static const xxl = 48.0;
}

abstract final class RsRadius {
  static const card = 14.0;
  static const chip = 999.0;
  static const button = 12.0;
}

/// Paper surface with a hairline border and soft elevation.
class PaperCard extends StatelessWidget {
  const PaperCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(RsSpacing.md),
    this.onTap,
    this.borderColor,
  });

  final Widget child;
  final EdgeInsets padding;
  final VoidCallback? onTap;
  final Color? borderColor;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.surface,
      borderRadius: BorderRadius.circular(RsRadius.card),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(RsRadius.card),
        child: Container(
          padding: padding,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(RsRadius.card),
            border: Border.all(
                color: borderColor ?? scheme.outlineVariant, width: 1),
          ),
          child: child,
        ),
      ),
    );
  }
}

/// Small editorial label chip.
class PaperChip extends StatelessWidget {
  const PaperChip({
    super.key,
    required this.label,
    this.icon,
    this.active = false,
  });
  final String label;
  final IconData? icon;
  final bool active;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: active ? scheme.primary : scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(RsRadius.chip),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon,
                size: 13,
                color: active ? scheme.onPrimary : scheme.onSurfaceVariant),
            const SizedBox(width: 5),
          ],
          Text(label,
              style: RsType.caption.copyWith(
                  color: active
                      ? scheme.onPrimary
                      : scheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}

/// Primary button in the paper language.
class PaperButton extends StatelessWidget {
  const PaperButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.filled = true,
  });
  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    final shape =
        RoundedRectangleBorder(borderRadius: BorderRadius.circular(RsRadius.button));
    final child = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 17),
          const SizedBox(width: 8),
        ],
        Text(label),
      ],
    );
    return filled
        ? FilledButton(
            style: FilledButton.styleFrom(shape: shape, elevation: 0),
            onPressed: onPressed,
            child: child)
        : OutlinedButton(
            style: OutlinedButton.styleFrom(shape: shape),
            onPressed: onPressed,
            child: child);
  }
}

/// Builds the app ThemeData in the paper-crafted language.
ThemeData buildRsTheme(Brightness brightness) {
  final isLight = brightness == Brightness.light;
  final scheme = ColorScheme.fromSeed(
    seedColor: isLight ? RsColors.accent : RsColors.accentDark,
    brightness: brightness,
    surface: isLight ? RsColors.paperSurface : RsColors.graphiteSurface,
    onSurface: isLight ? RsColors.inkPrimary : RsColors.graphiteInk,
    surfaceContainerHighest:
        isLight ? RsColors.paperSurfaceAlt : RsColors.graphiteSurfaceAlt,
    outlineVariant: isLight ? RsColors.paperBorder : RsColors.graphiteBorder,
  );

  final base = ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor:
        isLight ? RsColors.paperBackground : RsColors.graphiteBackground,
    splashFactory: InkSparkle.splashFactory,
  );

  return base.copyWith(
    textTheme: base.textTheme
        .apply(
          bodyColor: scheme.onSurface,
          displayColor: scheme.onSurface,
          fontFamily: 'SansTools',
        )
        .merge(isLight ? RsTypeLight.text : RsTypeDark.text),
    appBarTheme: AppBarTheme(
      backgroundColor: isLight
          ? RsColors.paperBackground
          : RsColors.graphiteBackground,
      foregroundColor: scheme.onSurface,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: RsType.headline.copyWith(color: scheme.onSurface),
    ),
    dividerColor: scheme.outlineVariant,
    listTileTheme: ListTileThemeData(
      shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(RsRadius.button)),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        textStyle: RsType.title,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: scheme.onSurface,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        textStyle: RsType.title,
      ),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: scheme.surfaceContainerHighest,
    ),
  );
}

abstract final class RsTypeLight {
  static const text = TextTheme(
    displayLarge: RsType.display,
    displayMedium: RsType.headline,
    headlineMedium: RsType.headline,
    titleMedium: RsType.title,
    bodyMedium: RsType.body,
    bodySmall: RsType.caption,
    labelSmall: RsType.caption,
  );
}

abstract final class RsTypeDark {
  static const text = RsTypeLight.text;
}
