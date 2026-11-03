import 'package:flutter/material.dart';

/// Owner-approved Apple-like brand colors and shared visual tokens.
abstract final class AppColors {
  static const Color canvas = Color(0xFFF5F5F7);
  static const Color ink = Color(0xFF1D1D1F);
  static const Color muted = Color(0xFFAAAAAA);
  static const Color blue = Color(0xFF007AFF);
  static const Color coral = Color(0xFFE0745E);
  static const Color amber = Color(0xFFF4B13B);
}

/// Shared shape and spacing values.
abstract final class AppTokens {
  static const double radiusCard = 20;
  static const double radiusSheet = 28;
  static const double spacing = 16;
  static const Duration microMotion = Duration(milliseconds: 180);
  static const Duration screenMotion = Duration(milliseconds: 340);
}

/// Material 3 themes used on every platform.
abstract final class AppTheme {
  static ThemeData light() => _build(Brightness.light);
  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final bool dark = brightness == Brightness.dark;
    final ColorScheme scheme = ColorScheme.fromSeed(
      seedColor: AppColors.blue,
      brightness: brightness,
      primary: AppColors.blue,
      secondary: AppColors.coral,
      surface: dark ? AppColors.ink : Colors.white,
    );
    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: dark
          ? const Color(0xFF111113)
          : AppColors.canvas,
      cardTheme: CardThemeData(
        clipBehavior: Clip.antiAlias,
        elevation: 0,
        color: dark ? const Color(0xFF252528) : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppTokens.radiusCard),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 52),
          shape: const StadiumBorder(),
          textStyle: const TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
      ),
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: <TargetPlatform, PageTransitionsBuilder>{
          TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
          TargetPlatform.windows: FadeForwardsPageTransitionsBuilder(),
        },
      ),
    );
  }
}
