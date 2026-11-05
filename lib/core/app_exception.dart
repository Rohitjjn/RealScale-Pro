/// Typed exceptions for every failure the app can show honestly.
/// Raw platform errors must never reach the UI; map them to these types.
/// `base` (not `sealed`) so services can define their own subclasses.
abstract base class AppException implements Exception {
  const AppException(this.message);
  final String message;
  @override
  String toString() => '$runtimeType: $message';
}

/// The user cancelled the job (not an error).
final class CancelledException extends AppException {
  const CancelledException() : super('Cancelled');
}

/// Not enough free disk space for the planned output.
final class StorageFullException extends AppException {
  const StorageFullException(super.message);
}

/// A required model is not installed (or failed checksum).
final class ModelMissingException extends AppException {
  const ModelMissingException(this.modelId)
    : super('Model not installed: $modelId');
  final String modelId;
}

/// The engine failed after the full recovery chain was exhausted.
final class EngineFailureException extends AppException {
  const EngineFailureException(super.message);
}

/// Input image could not be decoded / is unsupported.
final class UnsupportedImageException extends AppException {
  const UnsupportedImageException(super.message);
}

/// Download failed (network, checksum, no space, user cancel).
final class DownloadException extends AppException {
  const DownloadException(super.message, {this.checksumFailed = false});
  final bool checksumFailed;
}

/// Permission denied and the feature cannot proceed without it.
final class PermissionDeniedException extends AppException {
  const PermissionDeniedException(super.message);
}
