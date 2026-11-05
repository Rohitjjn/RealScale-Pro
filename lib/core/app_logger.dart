import 'dart:developer' as dev;

/// Minimal logger: debug prints in debug mode, developer log always.
/// Never logs image content or file paths of user photos beyond file names.
class AppLogger {
  static void d(String tag, String message) {
    assert(() {
      dev.log('[$tag] $message', name: 'RealScale');
      return true;
    }());
  }

  static void e(
    String tag,
    String message, [
    Object? error,
    StackTrace? stack,
  ]) {
    dev.log(
      '[$tag] $message',
      name: 'RealScale',
      error: error,
      stackTrace: stack,
    );
  }
}
