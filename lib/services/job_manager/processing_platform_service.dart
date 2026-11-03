import 'dart:ffi';
import 'dart:io';

import 'package:flutter/services.dart';

/// Small Android bridge for foreground processing and screen wake state.
class ProcessingPlatformService {
  const ProcessingPlatformService();

  static const MethodChannel _channel = MethodChannel('realscale/device');

  Future<void> start({required bool keepAwake}) async {
    if (Platform.isWindows) {
      if (keepAwake) _setWindowsAwake(true);
      return;
    }
    if (!Platform.isAndroid) return;
    await _safeInvoke('setKeepAwake', <String, bool>{'enabled': keepAwake});
    await _safeInvoke('startProcessing');
  }

  Future<void> update(double fraction) async {
    if (!Platform.isAndroid) return;
    await _safeInvoke(
      'updateProcessing',
      <String, int>{'progress': (fraction.clamp(0, 1) * 100).round()},
    );
  }

  Future<void> stop() async {
    if (Platform.isWindows) {
      _setWindowsAwake(false);
      return;
    }
    if (!Platform.isAndroid) return;
    await _safeInvoke('stopProcessing');
    await _safeInvoke('setKeepAwake', <String, bool>{'enabled': false});
  }

  void _setWindowsAwake(bool enabled) {
    final DynamicLibrary kernel = DynamicLibrary.open('kernel32.dll');
    final int Function(int) setState = kernel.lookupFunction<
        Uint32 Function(Uint32), int Function(int)>('SetThreadExecutionState');
    const int continuous = 0x80000000;
    const int systemRequired = 0x00000001;
    const int displayRequired = 0x00000002;
    setState(enabled
        ? continuous | systemRequired | displayRequired
        : continuous);
  }

  Future<void> _safeInvoke(String method, [Object? arguments]) async {
    try {
      await _channel.invokeMethod<void>(method, arguments);
    } on PlatformException {
      // In-app progress remains available when a platform service is blocked.
    }
  }
}
