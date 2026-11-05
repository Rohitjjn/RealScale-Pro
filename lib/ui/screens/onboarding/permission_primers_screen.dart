import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/app.dart';
import '../../../services/permission_service/permission_service.dart'
    show PermissionOutcome;
import '../../../l10n/app_localizations.dart';
import '../../../state/providers.dart';
import '../../../ui/widgets/controls.dart';

/// Permission primers (task 1.5): photos first, notifications second.
/// "Not now" is always respected; denial flows leave the app usable
/// (prd §3.6) — import still works via Files / system picker.
class PermissionPrimersScreen extends ConsumerStatefulWidget {
  const PermissionPrimersScreen({super.key});

  @override
  ConsumerState<PermissionPrimersScreen> createState() =>
      _PermissionPrimersScreenState();
}

class _PermissionPrimersScreenState
    extends ConsumerState<PermissionPrimersScreen> {
  int _step = 0;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      body: _step == 0
          ? PermissionPrimer(
              icon: Icons.photo_library_outlined,
              title: l.permissionPhotosTitle,
              body: l.permissionPhotosBody,
              allowLabel: l.permissionPhotosAllow,
              notNowLabel: l.permissionPhotosNotNow,
              onAllow: _requestPhotos,
              onNotNow: () => setState(() => _step = 1),
            )
          : PermissionPrimer(
              icon: Icons.notifications_none_rounded,
              title: l.permissionNotificationsTitle,
              body: l.permissionNotificationsBody,
              allowLabel: l.permissionNotificationsAllow,
              notNowLabel: l.permissionNotificationsNotNow,
              onAllow: _requestNotifications,
              onNotNow: _finish,
            ),
    );
  }

  Future<void> _requestPhotos() async {
    final services = ref.read(servicesProvider);
    final outcome = await services.permissions.requestPhotos();
    if (!mounted) return;
    if (outcome == PermissionOutcome.limited && mounted) {
      final l = AppLocalizations.of(context)!;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l.permissionSelectedPhotos)));
    }
    setState(() => _step = 1);
  }

  Future<void> _requestNotifications() async {
    final services = ref.read(servicesProvider);
    await services.permissions.requestNotifications();
    if (!mounted) return;
    await _finish();
  }

  Future<void> _finish() async {
    if (!mounted) return;
    context.go(Routes.deviceCheck);
  }
}
