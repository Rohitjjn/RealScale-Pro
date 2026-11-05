import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart' show PixelRect;
import '../../../ui/theme/tokens.dart';
import '../../../ui/widgets/cards.dart' show TabularFigures;

/// Full-screen preview-area picker (Phase 3 "Preview crop"): the photo is
/// fit-contained, the user drags/moves a rounded rect; the chosen region is
/// returned as a pixel-space [PixelRect].
class CropPickerScreen extends StatefulWidget {
  const CropPickerScreen({
    super.key,
    required this.bytes,
    required this.imageWidth,
    required this.imageHeight,
    this.initial,
  });

  final Uint8List bytes;
  final int imageWidth;
  final int imageHeight;
  final PixelRect? initial;

  /// Opens the picker; returns null when the user backed out.
  static Future<PixelRect?> open(
    BuildContext context, {
    required Uint8List bytes,
    required int imageWidth,
    required int imageHeight,
    PixelRect? initial,
  }) => Navigator.of(context).push<PixelRect>(
    MaterialPageRoute(
      fullscreenDialog: true,
      builder: (_) => CropPickerScreen(
        bytes: bytes,
        imageWidth: imageWidth,
        imageHeight: imageHeight,
        initial: initial,
      ),
    ),
  );

  @override
  State<CropPickerScreen> createState() => _CropPickerScreenState();
}

class _CropPickerScreenState extends State<CropPickerScreen> {
  Rect _screenRect = Rect.zero;
  Size _fittedSize = Size.zero;
  Offset _fittedTopLeft = Offset.zero;
  double _scale = 1;

  static const _minSize = 64.0;
  static const _handleRadius = 26.0;

  _Edge? _activeEdge;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _initRect());
  }

  void _initRect() {
    final size = MediaQuery.sizeOf(context);
    _scale = math.min(
      size.width / widget.imageWidth,
      (size.height * 0.72) / widget.imageHeight,
    );
    _fittedSize = Size(widget.imageWidth * _scale, widget.imageHeight * _scale);
    _fittedTopLeft = Offset(
      (size.width - _fittedSize.width) / 2,
      (size.height - _fittedSize.height) / 2,
    );
    final init = widget.initial;
    if (init != null) {
      final (l, t, w, h) = init;
      _screenRect = Rect.fromLTWH(
        _fittedTopLeft.dx + l * _scale,
        _fittedTopLeft.dy + t * _scale,
        w * _scale,
        h * _scale,
      );
    } else {
      // Start with a centered rect over ~40% of the image.
      final w = _fittedSize.width * 0.4;
      final h = _fittedSize.height * 0.4;
      _screenRect = Rect.fromCenter(
        center: _fittedTopLeft + _fittedSize.center(Offset.zero),
        width: w,
        height: h,
      );
    }
    _clampRect();
    setState(() {});
  }

  void _clampRect() {
    final bounds = Rect.fromLTWH(
      _fittedTopLeft.dx,
      _fittedTopLeft.dy,
      _fittedSize.width,
      _fittedSize.height,
    );
    var rect = _screenRect;
    if (rect.width < _minSize) {
      rect = Rect.fromLTWH(rect.left, rect.top, _minSize, rect.height);
    }
    if (rect.height < _minSize) {
      rect = Rect.fromLTWH(rect.left, rect.top, rect.width, _minSize);
    }
    rect = Rect.fromLTWH(
      rect.left.clamp(bounds.left, bounds.right - _minSize).toDouble(),
      rect.top.clamp(bounds.top, bounds.bottom - _minSize).toDouble(),
      math.min(rect.width, bounds.width),
      math.min(rect.height, bounds.height),
    );
    // Keep inside the fitted image.
    if (rect.right > bounds.right) {
      rect = rect.translate(bounds.right - rect.right, 0);
    }
    if (rect.bottom > bounds.bottom) {
      rect = rect.translate(0, bounds.bottom - rect.bottom);
    }
    _screenRect = rect;
  }

  void _onDrag(DragUpdateDetails d) {
    setState(() {
      final edge = _activeEdge;
      if (edge == null) {
        _screenRect = _screenRect.shift(d.delta);
      } else {
        switch (edge) {
          case _Edge.topLeft:
            _screenRect = Rect.fromLTRB(
              _screenRect.left + d.delta.dx,
              _screenRect.top + d.delta.dy,
              _screenRect.right,
              _screenRect.bottom,
            );
          case _Edge.topRight:
            _screenRect = Rect.fromLTRB(
              _screenRect.left,
              _screenRect.top + d.delta.dy,
              _screenRect.right + d.delta.dx,
              _screenRect.bottom,
            );
          case _Edge.bottomLeft:
            _screenRect = Rect.fromLTRB(
              _screenRect.left + d.delta.dx,
              _screenRect.top,
              _screenRect.right,
              _screenRect.bottom + d.delta.dy,
            );
          case _Edge.bottomRight:
            _screenRect = Rect.fromLTRB(
              _screenRect.left,
              _screenRect.top,
              _screenRect.right + d.delta.dx,
              _screenRect.bottom + d.delta.dy,
            );
        }
      }
      _clampRect();
    });
  }

  _Edge? _edgeAt(Offset pos) {
    final r = _screenRect;
    final corners = {
      _Edge.topLeft: r.topLeft,
      _Edge.topRight: r.topRight,
      _Edge.bottomLeft: r.bottomLeft,
      _Edge.bottomRight: r.bottomRight,
    };
    for (final e in corners.entries) {
      if ((e.value - pos).distance <= _handleRadius) return e.key;
    }
    return null;
  }

  PixelRect? _pixelRect() {
    if (_fittedSize == Size.zero) return null;
    final l = ((_screenRect.left - _fittedTopLeft.dx) / _scale).round();
    final t = ((_screenRect.top - _fittedTopLeft.dy) / _scale).round();
    final w = (_screenRect.width / _scale).round();
    final h = (_screenRect.height / _scale).round();
    if (w < 8 || h < 8) return null;
    return (
      l.clamp(0, widget.imageWidth - 8),
      t.clamp(0, widget.imageHeight - 8),
      w.clamp(8, widget.imageWidth - l),
      h.clamp(8, widget.imageHeight - t),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final size = MediaQuery.sizeOf(context);
    final r = _pixelRect();
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: Colors.white,
        title: Text(l.cropTitle),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: RsSpacing.xl),
            child: Text(
              l.cropHint,
              style: RsType.body.copyWith(color: Colors.white70),
              textAlign: TextAlign.center,
            ),
          ),
          Expanded(
            child: Stack(
              children: [
                Positioned.fromRect(
                  rect: Rect.fromLTWH(
                    _fittedTopLeft.dx,
                    _fittedTopLeft.dy,
                    _fittedSize.width,
                    _fittedSize.height,
                  ),
                  child: Image.memory(
                    widget.bytes,
                    fit: BoxFit.fill,
                    gaplessPlayback: true,
                  ),
                ),
                // Dimmed outside the crop.
                Positioned.fromRect(
                  rect: Rect.fromLTWH(0, 0, size.width, size.height),
                  child: ClipPath(
                    clipper: _RectHole(_screenRect),
                    child: Container(
                      color: Colors.black.withValues(alpha: 0.6),
                    ),
                  ),
                ),
                // Crop frame.
                Positioned.fromRect(
                  rect: _screenRect,
                  child: IgnorePointer(
                    child: Container(
                      decoration: BoxDecoration(
                        border: Border.all(color: Colors.white, width: 2),
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                ),
                // Gesture surface: corners resize, inside moves.
                Positioned.fromRect(
                  rect: Rect.fromLTWH(0, 0, size.width, size.height),
                  child: GestureDetector(
                    behavior: HitTestBehavior.translucent,
                    onPanStart: (d) => _activeEdge = _edgeAt(d.localPosition),
                    onPanUpdate: _onDrag,
                    onPanEnd: (_) => _activeEdge = null,
                  ),
                ),
                // Corner handles.
                for (final corner in [
                  _screenRect.topLeft,
                  _screenRect.topRight,
                  _screenRect.bottomLeft,
                  _screenRect.bottomRight,
                ])
                  Positioned(
                    left: corner.dx - 14,
                    top: corner.dy - 14,
                    child: IgnorePointer(
                      child: Container(
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(
                          color: cs.primary.withValues(alpha: 0.9),
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 2),
                        ),
                      ),
                    ),
                  ),
                // Pixel readout.
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 16,
                  child: Center(
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.55),
                        borderRadius: RsRadius.pill,
                      ),
                      child: Text(
                        r == null ? '' : '${r.$3} × ${r.$4} px',
                        style: RsType.label.copyWith(
                          color: Colors.white,
                          fontFeatures: const [TabularFigures()],
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(RsSpacing.xl),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.white,
                        side: const BorderSide(color: Colors.white38),
                      ),
                      onPressed: () {
                        setState(() {
                          final w = _fittedSize.width * 0.4;
                          final h = _fittedSize.height * 0.4;
                          _screenRect = Rect.fromCenter(
                            center:
                                _fittedTopLeft +
                                _fittedSize.center(Offset.zero),
                            width: w,
                            height: h,
                          );
                          _clampRect();
                        });
                      },
                      child: Text(l.cropReset),
                    ),
                  ),
                  const SizedBox(width: RsSpacing.m),
                  Expanded(
                    flex: 2,
                    child: FilledButton(
                      onPressed: () => Navigator.of(context).pop(_pixelRect()),
                      child: Text(l.cropApply),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

enum _Edge { topLeft, topRight, bottomLeft, bottomRight }

class _RectHole extends CustomClipper<Path> {
  _RectHole(this.rect);
  final Rect rect;

  @override
  Path getClip(Size size) => Path()
    ..addRect(Rect.fromLTWH(0, 0, size.width, size.height))
    ..addRRect(RRect.fromRectAndRadius(rect, const Radius.circular(12)))
    ..fillType = PathFillType.evenOdd;

  @override
  bool shouldReclip(_RectHole old) => old.rect != rect;
}
