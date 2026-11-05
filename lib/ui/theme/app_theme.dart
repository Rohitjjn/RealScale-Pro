import 'package:flutter/material.dart';

import 'tokens.dart';

/// Builds the light and dark Material 3 themes from the design tokens
/// (design-system.md §2). Dynamic color (Material You) can override the
/// seed when the user enables it.
class AppTheme {
  AppTheme._();

  static ThemeData light() => _theme(_scheme(Brightness.light));
  static ThemeData dark() => _theme(_scheme(Brightness.dark));

  static ThemeData _theme(_Scheme c) {
    final colorScheme = ColorScheme(
      brightness: c.brightness,
      primary: c.primary,
      onPrimary: Colors.white,
      primaryContainer: c.primary.withValues(alpha: 0.14),
      onPrimaryContainer: c.primaryDarkText,
      secondary: c.gradientEnd,
      onSecondary: Colors.white,
      secondaryContainer: c.gradientEnd.withValues(alpha: 0.14),
      onSecondaryContainer: c.gradientEnd,
      tertiary: c.accentWarm,
      onTertiary: Colors.black,
      tertiaryContainer: c.accentWarm.withValues(alpha: 0.16),
      onTertiaryContainer: c.accentWarmDeep,
      error: c.error,
      onError: Colors.white,
      errorContainer: c.error.withValues(alpha: 0.12),
      onErrorContainer: c.error,
      surface: c.surface,
      onSurface: c.onSurface,
      surfaceContainerHighest: c.surfaceVariant,
      surfaceContainerHigh: c.surfaceVariant,
      surfaceContainer: c.surface,
      onSurfaceVariant: c.onSurfaceVariant,
      outline: c.outline,
      outlineVariant: c.outline,
      inverseSurface: c.onSurface,
      onInverseSurface: c.surface,
    );

    final base = ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: c.background,
      splashFactory: InkSparkle.splashFactory,
    );

    return base.copyWith(
      textTheme: _textTheme(colorScheme),
      appBarTheme: AppBarTheme(
        backgroundColor: c.background,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: RsType.title.copyWith(color: c.onSurface),
        iconTheme: IconThemeData(color: c.onSurface),
      ),
      cardTheme: CardThemeData(
        color: c.surface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        clipBehavior: Clip.antiAlias,
        shape: const RoundedRectangleBorder(borderRadius: RsRadius.cardBorder),
      ),
      dividerTheme: DividerThemeData(color: c.outline, thickness: 1, space: 1),
      chipTheme: base.chipTheme.copyWith(
        backgroundColor: c.surfaceVariant,
        side: BorderSide(color: c.outline),
        labelStyle: RsType.label.copyWith(color: c.onSurface),
        shape: const RoundedRectangleBorder(borderRadius: RsRadius.pill),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: c.primary,
          foregroundColor: Colors.white,
          minimumSize: const Size(0, 52),
          padding: const EdgeInsets.symmetric(horizontal: 24),
          shape: const RoundedRectangleBorder(borderRadius: RsRadius.pill),
          textStyle: RsType.bodyMedium,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: c.primary,
          minimumSize: const Size(0, 48),
          side: BorderSide(color: c.outline),
          shape: const RoundedRectangleBorder(borderRadius: RsRadius.pill),
          textStyle: RsType.bodyMedium,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: c.primary,
          textStyle: RsType.bodyMedium,
        ),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
          (states) => states.contains(WidgetState.selected)
              ? Colors.white
              : c.onSurfaceVariant,
        ),
        trackColor: WidgetStateProperty.resolveWith(
          (states) =>
              states.contains(WidgetState.selected) ? c.primary : c.outline,
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: c.surface,
        indicatorColor: c.primary.withValues(alpha: 0.14),
        labelTextStyle: WidgetStatePropertyAll(
          RsType.label.copyWith(color: c.onSurface),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: c.onSurface,
        contentTextStyle: RsType.body.copyWith(color: c.surface),
        behavior: SnackBarBehavior.floating,
        shape: const RoundedRectangleBorder(borderRadius: RsRadius.cardBorder),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: c.surface,
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(borderRadius: RsRadius.sheetBorder),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: c.primary,
        linearTrackColor: c.outline,
      ),
    );
  }

  static TextTheme _textTheme(ColorScheme cs) => TextTheme(
    displayLarge: RsType.display.copyWith(color: cs.onSurface),
    displayMedium: RsType.display.copyWith(color: cs.onSurface),
    headlineLarge: RsType.headline.copyWith(color: cs.onSurface),
    headlineMedium: RsType.headline.copyWith(color: cs.onSurface),
    headlineSmall: RsType.headline.copyWith(color: cs.onSurface),
    titleLarge: RsType.title.copyWith(color: cs.onSurface),
    titleMedium: RsType.title.copyWith(color: cs.onSurface),
    titleSmall: RsType.bodyMedium.copyWith(color: cs.onSurface),
    bodyLarge: RsType.body.copyWith(color: cs.onSurface),
    bodyMedium: RsType.body.copyWith(color: cs.onSurface),
    bodySmall: RsType.label.copyWith(color: cs.onSurfaceVariant),
    labelLarge: RsType.bodyMedium.copyWith(color: cs.onSurface),
    labelMedium: RsType.label.copyWith(color: cs.onSurface),
    labelSmall: RsType.label.copyWith(color: cs.onSurfaceVariant),
  );
}

/// Internal palette holder.
typedef _Scheme = ({
  Brightness brightness,
  Color primary,
  Color gradientEnd,
  Color accentWarm,
  Color accentWarmDeep,
  Color primaryDarkText,
  Color surface,
  Color surfaceVariant,
  Color background,
  Color onSurface,
  Color onSurfaceVariant,
  Color outline,
  Color error,
});

_Scheme _scheme(Brightness brightness) {
  final dark = brightness == Brightness.dark;
  return (
    brightness: brightness,
    primary: dark ? RsColors.primaryDark : RsColors.primaryLight,
    gradientEnd: dark ? RsColors.gradientEndDark : RsColors.gradientEndLight,
    accentWarm: dark ? RsColors.accentWarmDark : RsColors.accentWarmLight,
    accentWarmDeep: dark ? const Color(0xFF78350F) : const Color(0xFF92400E),
    primaryDarkText: dark ? const Color(0xFFE0E7FF) : const Color(0xFF312E81),
    surface: dark ? RsColors.surfaceDark : RsColors.surfaceLight,
    surfaceVariant: dark
        ? RsColors.surfaceVariantDark
        : RsColors.surfaceVariantLight,
    background: dark ? RsColors.backgroundDark : RsColors.backgroundLight,
    onSurface: dark ? RsColors.onSurfaceDark : RsColors.onSurfaceLight,
    onSurfaceVariant: dark
        ? RsColors.onSurfaceVariantDark
        : RsColors.onSurfaceVariantLight,
    outline: dark ? RsColors.outlineDark : RsColors.outlineLight,
    error: dark ? RsColors.errorDark : RsColors.errorLight,
  );
}
