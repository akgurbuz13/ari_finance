import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../core/services/biometric_service.dart';
import '../../../core/services/notification_service.dart';
import '../../auth/providers/auth_provider.dart';
import '../../../shared/widgets/ova_button.dart';
import '../../../shared/widgets/ova_text_field.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final _firstNameController = TextEditingController();
  final _lastNameController = TextEditingController();
  bool _saving = false;
  String? _message;
  Map<String, dynamic>? _user;

  bool _biometricAvailable = false;
  bool _biometricEnabled = false;
  bool _pushEnabled = false;

  @override
  void initState() {
    super.initState();
    _loadUser();
    _loadPreferences();
  }

  @override
  void dispose() {
    _firstNameController.dispose();
    _lastNameController.dispose();
    super.dispose();
  }

  Future<void> _loadUser() async {
    try {
      final res = await ApiClient.instance.dio.get(ApiEndpoints.me);
      setState(() {
        _user = res.data;
        _firstNameController.text = _user?['firstName'] ?? '';
        _lastNameController.text = _user?['lastName'] ?? '';
      });
    } catch (_) {}
  }

  Future<void> _loadPreferences() async {
    final biometricService = ref.read(biometricServiceProvider);
    final notificationService = ref.read(notificationServiceProvider);

    final available = await biometricService.isAvailable();
    final bioEnabled = await biometricService.isBiometricEnabled();
    final pushEnabled = await notificationService.isPushEnabled();

    if (mounted) {
      setState(() {
        _biometricAvailable = available;
        _biometricEnabled = bioEnabled;
        _pushEnabled = pushEnabled;
      });
    }
  }

  Future<void> _toggleBiometric(bool value) async {
    final biometricService = ref.read(biometricServiceProvider);

    if (value) {
      // Verify the user can authenticate before enabling
      final authenticated = await biometricService.authenticate(
        reason: 'Verify your identity to enable biometric login',
      );
      if (!authenticated) return;
    }

    await biometricService.setBiometricEnabled(value);
    setState(() => _biometricEnabled = value);
  }

  Future<void> _togglePush(bool value) async {
    final notificationService = ref.read(notificationServiceProvider);
    await notificationService.setPushEnabled(value);
    setState(() => _pushEnabled = value);
  }

  Future<void> _saveProfile() async {
    setState(() { _saving = true; _message = null; });
    try {
      await ApiClient.instance.dio.patch(ApiEndpoints.me, data: {
        'firstName': _firstNameController.text,
        'lastName': _lastNameController.text,
      });
      setState(() => _message = 'Profile updated');
    } catch (_) {
      setState(() => _message = 'Failed to update profile');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _setup2FA() async {
    try {
      final res = await ApiClient.instance.dio.post(ApiEndpoints.setup2fa);
      final data = res.data;
      if (mounted) {
        showDialog(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('Set up 2FA'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Scan this URI in your authenticator app:'),
                const SizedBox(height: 8),
                SelectableText(data['uri'] ?? '', style: const TextStyle(fontSize: 12, fontFamily: 'monospace')),
                const SizedBox(height: 12),
                const Text('Secret:'),
                SelectableText(data['secret'] ?? '', style: const TextStyle(fontWeight: FontWeight.bold)),
              ],
            ),
            actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Done'))],
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to set up 2FA')),
        );
      }
    }
  }

  Future<void> _logout() async {
    await ref.read(authProvider.notifier).logout();
    if (mounted) context.go('/login');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const Text('Settings', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
            const SizedBox(height: 24),

            // Profile Section
            _SectionHeader(title: 'Profile'),
            const SizedBox(height: 12),
            if (_message != null) ...[
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.grey[50],
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.grey[200]!),
                ),
                child: Text(_message!, style: const TextStyle(fontSize: 13)),
              ),
              const SizedBox(height: 12),
            ],
            OvaTextField(controller: _firstNameController, label: 'First Name'),
            const SizedBox(height: 12),
            OvaTextField(controller: _lastNameController, label: 'Last Name'),
            const SizedBox(height: 12),
            OvaTextField(
              label: 'Email',
              controller: TextEditingController(text: _user?['email'] ?? ''),
              enabled: false,
            ),
            const SizedBox(height: 12),
            OvaTextField(
              label: 'Phone',
              controller: TextEditingController(text: _user?['phone'] ?? ''),
              enabled: false,
            ),
            const SizedBox(height: 16),
            OvaButton(
              label: _saving ? 'Saving...' : 'Save Changes',
              onPressed: _saving ? null : _saveProfile,
            ),
            const SizedBox(height: 32),

            // Security Section
            _SectionHeader(title: 'Security'),
            const SizedBox(height: 12),

            // Biometric Auth
            if (_biometricAvailable)
              _ToggleRow(
                icon: Icons.fingerprint,
                title: 'Biometric Login',
                subtitle: _biometricEnabled ? 'Enabled' : 'Tap to enable Face ID / fingerprint',
                value: _biometricEnabled,
                onChanged: _toggleBiometric,
              ),
            if (_biometricAvailable) const SizedBox(height: 12),

            // 2FA
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey[200]!),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Two-Factor Authentication', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                      const SizedBox(height: 4),
                      Text(
                        _user?['totpEnabled'] == true ? 'Enabled' : 'Not enabled',
                        style: TextStyle(color: Colors.grey[500], fontSize: 12),
                      ),
                    ],
                  ),
                  if (_user?['totpEnabled'] != true)
                    TextButton(
                      onPressed: _setup2FA,
                      child: const Text('Set up', style: TextStyle(color: Colors.black, fontWeight: FontWeight.w600)),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // KYC
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey[200]!),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('KYC Status', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                      const SizedBox(height: 4),
                      Text(
                        _user?['status'] ?? 'Unknown',
                        style: TextStyle(color: Colors.grey[500], fontSize: 12),
                      ),
                    ],
                  ),
                  if (_user?['status'] == 'pending_kyc')
                    TextButton(
                      onPressed: () => context.go('/kyc'),
                      child: const Text('Verify', style: TextStyle(color: Colors.black, fontWeight: FontWeight.w600)),
                    ),
                ],
              ),
            ),
            const SizedBox(height: 32),

            // Notifications Section
            _SectionHeader(title: 'Notifications'),
            const SizedBox(height: 12),
            _ToggleRow(
              icon: Icons.notifications_outlined,
              title: 'Push Notifications',
              subtitle: _pushEnabled ? 'Enabled' : 'Receive transfer and security alerts',
              value: _pushEnabled,
              onChanged: _togglePush,
            ),
            const SizedBox(height: 32),

            // Logout
            OvaButton(label: 'Log Out', onPressed: _logout, secondary: true),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader({required this.title});

  @override
  Widget build(BuildContext context) {
    return Text(title, style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.grey[700]));
  }
}

class _ToggleRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  const _ToggleRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey[200]!),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(icon, size: 22, color: Colors.grey[600]),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                const SizedBox(height: 4),
                Text(subtitle, style: TextStyle(color: Colors.grey[500], fontSize: 12)),
              ],
            ),
          ),
          Switch.adaptive(
            value: value,
            onChanged: onChanged,
            activeColor: Colors.black,
          ),
        ],
      ),
    );
  }
}
