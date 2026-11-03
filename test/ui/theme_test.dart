import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/ui/theme/app_theme.dart';

void main() {
  test('owner-approved palette is stable', () {
    expect(AppColors.canvas, const Color(0xFFF5F5F7));
    expect(AppColors.ink, const Color(0xFF1D1D1F));
    expect(AppColors.blue, const Color(0xFF007AFF));
    expect(AppColors.coral, const Color(0xFFE0745E));
    expect(AppColors.amber, const Color(0xFFF4B13B));
  });

  test('both Material 3 themes are available', () {
    expect(AppTheme.light().useMaterial3, isTrue);
    expect(AppTheme.dark().useMaterial3, isTrue);
    expect(AppTheme.light().brightness, Brightness.light);
    expect(AppTheme.dark().brightness, Brightness.dark);
  });
}
