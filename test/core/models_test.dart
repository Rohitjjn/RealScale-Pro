import 'package:flutter_test/flutter_test.dart';
import 'package:realscale_pro/core/models.dart';

void main() {
  test('image output size is exactly 2x in Default mode', () {
    const ImageSelection selection = ImageSelection(
      path: 'photo.jpg',
      name: 'photo.jpg',
      width: 1200,
      height: 800,
      bytes: 1500000,
      hasAlpha: false,
    );
    expect(selection.outputWidth, 2400);
    expect(selection.outputHeight, 1600);
  });

  test('progress derives only from completed tiles', () {
    const ProgressEvent event = ProgressEvent(
      jobId: 'job',
      stage: JobStage.enhancing,
      completedTiles: 3,
      totalTiles: 12,
      elapsed: Duration(seconds: 1),
    );
    expect(event.fraction, 0.25);
  });
}
