import 'dart:io';

import 'package:permission_handler/permission_handler.dart';
import 'package:photo_manager/photo_manager.dart';

/// Requests permissions only after the app has shown its own explanation.
class PermissionService {
  const PermissionService();

  Future<PermissionState> photoState() async {
    if (Platform.isWindows) {
      return PermissionState.authorized;
    }
    return PhotoManager.requestPermissionExtend(
      requestOption: const PermissionRequestOption(
        androidPermission: AndroidPermission(type: RequestType.image),
      ),
    );
  }

  Future<PermissionStatus> notificationState() async {
    if (!Platform.isAndroid) {
      return PermissionStatus.granted;
    }
    return Permission.notification.request();
  }

  Future<PermissionStatus> cameraState() => Permission.camera.request();

  Future<bool> openSystemSettings() => openAppSettings();
}
