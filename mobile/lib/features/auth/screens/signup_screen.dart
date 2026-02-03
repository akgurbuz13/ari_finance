import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../../../shared/widgets/ova_button.dart';
import '../../../shared/widgets/ova_text_field.dart';

class SignupScreen extends ConsumerStatefulWidget {
  const SignupScreen({super.key});

  @override
  ConsumerState<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends ConsumerState<SignupScreen> {
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();
  String _region = 'TR';
  bool _loading = false;
  String? _error;

  Future<void> _signup() async {
    setState(() { _loading = true; _error = null; });
    try {
      await ref.read(authProvider.notifier).signup(
        _emailController.text, _phoneController.text, _passwordController.text, _region,
      );
      if (mounted) context.go('/home');
    } catch (e) {
      setState(() => _error = 'Signup failed');
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
              children: [
                const Text('Ova', style: TextStyle(fontSize: 40, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Text('Create your account', style: TextStyle(color: Colors.grey[500])),
                const SizedBox(height: 40),
                if (_error != null) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(color: Colors.red[50], borderRadius: BorderRadius.circular(8)),
                    child: Text(_error!, style: TextStyle(color: Colors.red[700], fontSize: 13)),
                  ),
                  const SizedBox(height: 16),
                ],
                OvaTextField(controller: _emailController, label: 'Email', keyboardType: TextInputType.emailAddress),
                const SizedBox(height: 16),
                OvaTextField(controller: _phoneController, label: 'Phone', keyboardType: TextInputType.phone),
                const SizedBox(height: 16),
                OvaTextField(controller: _passwordController, label: 'Password', obscureText: true),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  value: _region,
                  decoration: InputDecoration(
                    labelText: 'Region',
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  items: const [
                    DropdownMenuItem(value: 'TR', child: Text('Turkey')),
                    DropdownMenuItem(value: 'EU', child: Text('European Union')),
                  ],
                  onChanged: (v) => setState(() => _region = v!),
                ),
                const SizedBox(height: 24),
                OvaButton(label: _loading ? 'Creating...' : 'Create account', onPressed: _loading ? null : _signup),
                const SizedBox(height: 24),
                TextButton(
                  onPressed: () => context.go('/login'),
                  child: const Text('Already have an account? Sign in', style: TextStyle(color: Colors.black)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
