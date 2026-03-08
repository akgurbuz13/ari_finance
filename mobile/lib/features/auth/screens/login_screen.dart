import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../../../shared/widgets/ari_button.dart';
import '../../../shared/widgets/ari_text_field.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _totpController = TextEditingController();
  bool _loading = false;
  String? _error;

  Future<void> _login() async {
    setState(() { _loading = true; _error = null; });
    try {
      await ref.read(authProvider.notifier).login(
        _emailController.text,
        _passwordController.text,
        totpCode: _totpController.text.isEmpty ? null : _totpController.text,
      );
      if (mounted) context.go('/home');
    } catch (e) {
      setState(() => _error = 'Invalid credentials');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Text('ARI', style: TextStyle(fontSize: 40, fontWeight: FontWeight.bold, color: Colors.black)),
                const SizedBox(height: 8),
                Text('Sign in to your account', style: TextStyle(color: Colors.grey[500])),
                const SizedBox(height: 40),
                if (_error != null) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(color: Colors.red[50], borderRadius: BorderRadius.circular(8)),
                    child: Text(_error!, style: TextStyle(color: Colors.red[700], fontSize: 13)),
                  ),
                  const SizedBox(height: 16),
                ],
                AriTextField(controller: _emailController, label: 'Email', keyboardType: TextInputType.emailAddress),
                const SizedBox(height: 16),
                AriTextField(controller: _passwordController, label: 'Password', obscureText: true),
                const SizedBox(height: 16),
                AriTextField(controller: _totpController, label: '2FA Code (optional)', keyboardType: TextInputType.number),
                const SizedBox(height: 24),
                AriButton(label: _loading ? 'Signing in...' : 'Sign in', onPressed: _loading ? null : _login),
                const SizedBox(height: 24),
                TextButton(
                  onPressed: () => context.go('/signup'),
                  child: const Text("Don't have an account? Sign up", style: TextStyle(color: Colors.black)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
