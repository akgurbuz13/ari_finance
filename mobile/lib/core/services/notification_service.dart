import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

final notificationServiceProvider = Provider<NotificationService>((ref) {
  return NotificationService();
});

/// Push notification service.
///
/// In production this would integrate with Firebase Cloud Messaging (FCM)
/// and flutter_local_notifications for foreground display.
/// The current implementation stores notification preferences and provides
/// the integration surface that the backend pushes to.
class NotificationService {
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  static const String _pushEnabledKey = 'push_notifications_enabled';
  static const String _fcmTokenKey = 'fcm_token';

  /// Initialize the notification service.
  /// Call this at app startup after Firebase initialization.
  Future<void> initialize() async {
    // In production:
    // 1. Request notification permissions
    // 2. Get FCM token
    // 3. Listen for token refresh
    // 4. Configure foreground notification handler
    // 5. Configure background message handler
    debugPrint('[NotificationService] initialized');
  }

  /// Check if push notifications are enabled by the user.
  Future<bool> isPushEnabled() async {
    final value = await _storage.read(key: _pushEnabledKey);
    return value == 'true';
  }

  /// Store the user's push notification preference.
  Future<void> setPushEnabled(bool enabled) async {
    await _storage.write(key: _pushEnabledKey, value: enabled.toString());
    if (enabled) {
      await _registerForPush();
    } else {
      await _unregisterFromPush();
    }
  }

  /// Get the stored FCM token if available.
  Future<String?> getFcmToken() async {
    return await _storage.read(key: _fcmTokenKey);
  }

  /// Register this device for push notifications.
  Future<void> _registerForPush() async {
    // In production:
    // final token = await FirebaseMessaging.instance.getToken();
    // await _storage.write(key: _fcmTokenKey, value: token);
    // Send token to backend: POST /api/v1/notifications/register
    debugPrint('[NotificationService] registered for push notifications');
  }

  /// Unregister this device from push notifications.
  Future<void> _unregisterFromPush() async {
    // In production:
    // await FirebaseMessaging.instance.deleteToken();
    // await _storage.delete(key: _fcmTokenKey);
    // Notify backend: DELETE /api/v1/notifications/unregister
    debugPrint('[NotificationService] unregistered from push notifications');
  }

  /// Handle an incoming push notification payload.
  void handleNotification(Map<String, dynamic> data) {
    final type = data['type'] as String?;
    final body = data['body'] as String?;
    debugPrint('[NotificationService] received: type=$type body=$body');

    // In production, show a local notification via flutter_local_notifications
    // and optionally navigate to the relevant screen.
  }
}
