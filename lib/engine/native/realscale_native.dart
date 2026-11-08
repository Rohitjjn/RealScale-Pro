/// RealScale-Pro — native engine facade.
///
/// Conditional export (canonical IO-default form): IO platforms
/// (Android, Linux, macOS, Windows) get the real dart:ffi bindings;
/// web builds get the stub that reports the engine as unavailable.
library;

export 'realscale_ffi_io.dart'
    if (dart.library.js_interop) 'realscale_ffi_stub.dart';
