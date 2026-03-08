import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../core/models/account.dart';
import '../../../shared/widgets/balance_card.dart';
import '../../../shared/widgets/ari_button.dart';

class AccountsScreen extends ConsumerStatefulWidget {
  const AccountsScreen({super.key});

  @override
  ConsumerState<AccountsScreen> createState() => _AccountsScreenState();
}

class _AccountsScreenState extends ConsumerState<AccountsScreen> {
  List<Account> _accounts = [];
  bool _loading = true;
  bool _creating = false;

  @override
  void initState() {
    super.initState();
    _loadAccounts();
  }

  Future<void> _loadAccounts() async {
    try {
      final res = await ApiClient.instance.dio.get(ApiEndpoints.accounts);
      _accounts = (res.data as List).map((e) => Account.fromJson(e)).toList();
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _createAccount(String currency) async {
    setState(() => _creating = true);
    try {
      await ApiClient.instance.dio.post(ApiEndpoints.accounts, data: {'currency': currency});
      setState(() => _loading = true);
      await _loadAccounts();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to create account')),
        );
      }
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: Colors.black))
            : RefreshIndicator(
                color: Colors.black,
                onRefresh: () async {
                  setState(() => _loading = true);
                  await _loadAccounts();
                },
                child: ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text('Accounts', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
                        PopupMenuButton<String>(
                          icon: Container(
                            padding: const EdgeInsets.all(8),
                            decoration: BoxDecoration(
                              border: Border.all(color: Colors.grey[300]!),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: const Icon(Icons.add, size: 20),
                          ),
                          onSelected: _creating ? null : _createAccount,
                          itemBuilder: (_) => [
                            const PopupMenuItem(value: 'TRY', child: Text('TRY Account')),
                            const PopupMenuItem(value: 'EUR', child: Text('EUR Account')),
                          ],
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    if (_accounts.isEmpty)
                      Container(
                        padding: const EdgeInsets.all(32),
                        decoration: BoxDecoration(
                          color: Colors.grey[50],
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Column(
                          children: [
                            Icon(Icons.account_balance_wallet_outlined, size: 48, color: Colors.grey[300]),
                            const SizedBox(height: 12),
                            Text('No accounts yet', style: TextStyle(color: Colors.grey[500])),
                            const SizedBox(height: 16),
                            Row(
                              children: [
                                Expanded(
                                  child: AriButton(
                                    label: '+ TRY',
                                    onPressed: _creating ? null : () => _createAccount('TRY'),
                                    secondary: true,
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: AriButton(
                                    label: '+ EUR',
                                    onPressed: _creating ? null : () => _createAccount('EUR'),
                                    secondary: true,
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      )
                    else
                      ..._accounts.map((account) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: BalanceCard(account: account),
                      )),
                  ],
                ),
              ),
      ),
    );
  }
}
