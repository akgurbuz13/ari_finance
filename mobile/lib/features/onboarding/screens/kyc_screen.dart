import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../shared/widgets/ari_button.dart';

class KycScreen extends ConsumerStatefulWidget {
  const KycScreen({super.key});

  @override
  ConsumerState<KycScreen> createState() => _KycScreenState();
}

class _KycScreenState extends ConsumerState<KycScreen> {
  String _status = 'unknown';
  bool _loading = true;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    try {
      final res = await ApiClient.instance.dio.get(ApiEndpoints.kycStatus);
      setState(() => _status = res.data['status'] ?? 'not_started');
    } catch (_) {
      setState(() => _status = 'not_started');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _initiateKyc() async {
    setState(() => _submitting = true);
    try {
      await ApiClient.instance.dio.post(ApiEndpoints.kycInitiate, data: {
        'provider': 'veriff',
        'level': 'basic',
      });
      await _checkStatus();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to start KYC verification')),
        );
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => context.go('/home'),
        ),
        title: const Text('Identity Verification', style: TextStyle(color: Colors.black)),
      ),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: Colors.black))
            : Padding(
                padding: const EdgeInsets.all(24),
                child: _buildContent(),
              ),
      ),
    );
  }

  Widget _buildContent() {
    switch (_status) {
      case 'approved':
        return _StatusView(
          icon: Icons.check_circle,
          iconColor: Colors.green,
          title: 'Verified',
          subtitle: 'Your identity has been verified. You have full access to all features.',
          action: AriButton(label: 'Continue', onPressed: () => context.go('/home')),
        );
      case 'pending':
        return _StatusView(
          icon: Icons.hourglass_top,
          iconColor: Colors.orange,
          title: 'Verification in Progress',
          subtitle: 'We are reviewing your documents. This usually takes a few minutes.',
          action: AriButton(label: 'Check Again', onPressed: _checkStatus, secondary: true),
        );
      case 'rejected':
        return _StatusView(
          icon: Icons.cancel,
          iconColor: Colors.red,
          title: 'Verification Failed',
          subtitle: 'Your verification was rejected. Please try again with valid documents.',
          action: AriButton(
            label: _submitting ? 'Starting...' : 'Try Again',
            onPressed: _submitting ? null : _initiateKyc,
          ),
        );
      default:
        return Column(
          children: [
            const Spacer(),
            Icon(Icons.verified_user_outlined, size: 64, color: Colors.grey[300]),
            const SizedBox(height: 24),
            const Text(
              'Verify Your Identity',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            Text(
              'To comply with regulations and protect your account, we need to verify your identity.',
              style: TextStyle(color: Colors.grey[500], height: 1.5),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 32),
            _StepRow(number: '1', text: 'Take a photo of your ID document'),
            const SizedBox(height: 12),
            _StepRow(number: '2', text: 'Take a selfie for face verification'),
            const SizedBox(height: 12),
            _StepRow(number: '3', text: 'Wait for automatic review'),
            const Spacer(),
            AriButton(
              label: _submitting ? 'Starting...' : 'Start Verification',
              onPressed: _submitting ? null : _initiateKyc,
            ),
            const SizedBox(height: 16),
          ],
        );
    }
  }
}

class _StatusView extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;
  final String subtitle;
  final Widget? action;

  const _StatusView({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.subtitle,
    this.action,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const Spacer(),
        Icon(icon, size: 64, color: iconColor),
        const SizedBox(height: 24),
        Text(title, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        Text(subtitle, style: TextStyle(color: Colors.grey[500], height: 1.5), textAlign: TextAlign.center),
        const Spacer(),
        if (action != null) action!,
        const SizedBox(height: 16),
      ],
    );
  }
}

class _StepRow extends StatelessWidget {
  final String number;
  final String text;

  const _StepRow({required this.number, required this.text});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(color: Colors.black, borderRadius: BorderRadius.circular(8)),
          child: Center(child: Text(number, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13))),
        ),
        const SizedBox(width: 12),
        Expanded(child: Text(text, style: const TextStyle(fontSize: 14))),
      ],
    );
  }
}
