import 'dart:io';

import 'package:permission_handler/permission_handler.dart';

/// PermissionService — primers first, system dialogs second, denial flows
/// always usable (prd §3.6). The app works with every permission denied.
enum PermissionOutcome { granted, limited, denied, permanentlyDenied }

class PermissionService {
  const PermissionService();

  /// Photos/media access. Android 14 "selected photos" maps to [limited].
  Future<PermissionOutcome> requestPhotos() async {
    if (Platform.isWindows) return PermissionOutcome.granted; // picker only
    final status = await Permission.photos.request();
    if (status.isGranted || status.isProvisional) {
      return PermissionOutcome.granted;
    }
    if (status.isLimited) return PermissionOutcome.limited;
    if (status.isPermanentlyDenied) return PermissionOutcome.permanentlyDenied;
    return PermissionOutcome.denied;
  }

  /// Current photos permission without prompting.
  Future<PermissionOutcome> photosStatus() async {
    if (Platform.isWindows) return PermissionOutcome.granted;
    final status = await Permission.photos.status;
    if (status.isGranted) return PermissionOutcome.granted;
    if (status.isLimited) return PermissionOutcome.limited;
    if (status.isPermanentlyDenied) return PermissionOutcome.permanentlyDenied;
    return PermissionOutcome.denied;
  }

  /// Notifications (Android 13+) — optional; used for background jobs.
  Future<PermissionOutcome> requestNotifications() async {
    if (!Platform.isAndroid) return PermissionOutcome.granted;
    final status = await Permission.notification.request();
    if (status.isGranted) return PermissionOutcome.granted;
    if (status.isPermanentlyDenied) return PermissionOutcome.permanentlyDenied;
    return PermissionOutcome.denied;
  }

  /// Camera (asked on first tap of the Camera tile).
  Future<PermissionOutcome> requestCamera() async {
    if (Platform.isWindows) return PermissionOutcome.granted;
    final status = await Permission.camera.request();
    if (status.isGranted) return PermissionOutcome.granted;
    if (status.isPermanentlyDenied) return PermissionOutcome.permanentlyDenied;
    return PermissionOutcome.denied;
  }

  Future<bool> openSettingsPage() => openAppSettings();
}
