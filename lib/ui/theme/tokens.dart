import 'package:flutter/material.dart';

/// Design tokens from design-system.md §2 (colors), §3 (type), §4 (shape),
/// §6 (motion). The single source of truth for the premium visual language:
/// deep indigo → violet gradient, warm amber highlight, tonal surfaces.
abstract final class RsColors {
  // Brand
  static const primaryLight = Color(0xFF4F46E5); // indigo-600
  static const primaryDark = Color(0xFF818CF8); // indigo-300
  static const gradientStartLight = Color(0xFF4F46E5);
  static const gradientEndLight = Color(0xFF7C3AED); // violet-600
  static const gradientStartDark = Color(0xFF6366F1);
  static const gradientEndDark = Color(0xFFA78BFA);
  static const accentWarmLight = Color(0xFFF59E0B); // amber-500
  static const accentWarmDark = Color(0xFFFBBF24);

  // Surfaces (light)
  static const surfaceLight = Color(0xFFFFFFFF);
  static const surfaceVariantLight = Color(0xFFF3F4F8);
  static const backgroundLight = Color(0xFFFAFAFC);
  static const onSurfaceLight = Color(0xFF181A20);
  static const onSurfaceVariantLight = Color(0xFF5B5F6B);
  static const outlineLight = Color(0xFFE2E4EB);

  // Surfaces (dark)
  static const surfaceDark = Color(0xFF121318);
  static const surfaceVariantDark = Color(0xFF1D1F27);
  static const backgroundDark = Color(0xFF0E0F13);
  static const onSurfaceDark = Color(0xFFE7E8EE);
  static const onSurfaceVariantDark = Color(0xFFA6AAB8);
  static const outlineDark = Color(0xFF2C2F3A);

  // Status
  static const successLight = Color(0xFF16A34A);
  static const successDark = Color(0xFF4ADE80);
  static const warningLight = Color(0xFFD97706);
  static const warningDark = Color(0xFFFBBF24);
  static const errorLight = Color(0xFFDC2626);
  static const errorDark = Color(0xFFF87171);

  static LinearGradient brandGradient({bool dark = false}) => LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: dark
        ? const [gradientStartDark, gradientEndDark]
        : const [gradientStartLight, gradientEndLight],
  );
}

/// Shape tokens (design-system §4).
abstract final class RsRadius {
  static const card = Radius.circular(20);
  static const cardBorder = BorderRadius.all(Radius.circular(20));
  static const sheetBorder = BorderRadius.vertical(top: Radius.circular(28));
  static const inputBorder = BorderRadius.all(Radius.circular(16));
  static const pill = BorderRadius.all(Radius.circular(999));
}

/// Spacing scale: 4, 8, 12, 16, 24, 32.
abstract final class RsSpacing {
  static const xs = 4.0;
  static const s = 8.0;
  static const m = 12.0;
  static const l = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;
}

/// Motion tokens (design-system §6).
abstract final class RsMotion {
  static const micro = Duration(milliseconds: 180); // selection
  static const screen = Duration(milliseconds: 340); // transitions
  static const emphasized = Curves.easeOutCubic; // entrances
  static const standard = Curves.easeInOut; // exits
  static const spring = Curves.easeOutBack; // chips, cards
}

/// Content max width on wide screens (design-system §4).
const double kContentMaxWidth = 560;

/// Type scale helper (design-system §3): Inter everywhere, tabular figures
/// for numerics. Sizes: display 32, headline 24, title 18, body 15, label 13.
abstract final class RsType {
  static const display = TextStyle(
    fontFamily: 'Inter',
    fontSize: 32,
    height: 40 / 32,
    fontWeight: FontWeight.w700,
    letterSpacing: -0.5,
  );
  static const headline = TextStyle(
    fontFamily: 'Inter',
    fontSize: 24,
    height: 32 / 24,
    fontWeight: FontWeight.w600,
    letterSpacing: -0.3,
  );
  static const title = TextStyle(
    fontFamily: 'Inter',
    fontSize: 18,
    height: 26 / 18,
    fontWeight: FontWeight.w600,
  );
  static const body = TextStyle(
    fontFamily: 'Inter',
    fontSize: 15,
    height: 22 / 15,
    fontWeight: FontWeight.w400,
  );
  static const bodyMedium = TextStyle(
    fontFamily: 'Inter',
    fontSize: 15,
    height: 22 / 15,
    fontWeight: FontWeight.w500,
  );
  static const label = TextStyle(
    fontFamily: 'Inter',
    fontSize: 13,
    height: 18 / 13,
    fontWeight: FontWeight.w500,
  );
  static const numeric = TextStyle(
    fontFamily: 'Inter',
    fontSize: 15,
    height: 22 / 15,
    fontWeight: FontWeight.w600,
    fontFeatures: [FontFeature.tabularFigures()],
  );
}
