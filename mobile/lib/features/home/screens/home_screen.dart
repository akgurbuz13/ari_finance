import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../core/models/account.dart';
import '../../../core/models/transaction.dart';
import '../../../shared/widgets/balance_card.dart';
import '../../../shared/widgets/transaction_tile.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  List<Account> _accounts = [];
  List<Transaction> _transactions = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    try {
      final api = ApiClient.instance;
      final accountsRes = await api.dio.get(ApiEndpoints.accounts);
      _accounts = (accountsRes.data as List).map((e) => Account.fromJson(e)).toList();

      if (_accounts.isNotEmpty) {
        final txRes = await api.dio.get(
          ApiEndpoints.accountTransactions(_accounts.first.id),
          queryParameters: {'limit': 10},
        );
        _transactions = (txRes.data as List).map((e) => Transaction.fromJson(e)).toList();
      }
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        backgroundColor: Colors.white,
        body: Center(child: CircularProgressIndicator(color: Colors.black)),
      );
    }

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: RefreshIndicator(
          color: Colors.black,
          onRefresh: () async {
            setState(() => _loading = true);
            await _loadData();
          },
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              const Text('Home', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
              const SizedBox(height: 20),

              // Accounts
              if (_accounts.isEmpty)
                Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    color: Colors.grey[50],
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Column(
                    children: [
                      Text('No accounts yet', style: TextStyle(color: Colors.grey[500])),
                      const SizedBox(height: 8),
                      TextButton(
                        onPressed: () => context.go('/accounts'),
                        child: const Text('Create one', style: TextStyle(color: Colors.black)),
                      ),
                    ],
                  ),
                )
              else ...[
                SizedBox(
                  height: 160,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: _accounts.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 12),
                    itemBuilder: (_, i) => SizedBox(
                      width: MediaQuery.of(context).size.width * 0.75,
                      child: BalanceCard(account: _accounts[i]),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 28),

              // Quick Actions
              const Text('Quick Actions', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
              const SizedBox(height: 12),
              Row(
                children: [
                  _QuickAction(icon: Icons.send, label: 'Send', onTap: () => context.go('/transfer')),
                  const SizedBox(width: 12),
                  _QuickAction(icon: Icons.account_balance_wallet, label: 'Accounts', onTap: () => context.go('/accounts')),
                  const SizedBox(width: 12),
                  _QuickAction(icon: Icons.history, label: 'History', onTap: () => context.go('/history')),
                ],
              ),
              const SizedBox(height: 28),

              // Recent Transactions
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Recent Activity', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                  if (_transactions.isNotEmpty)
                    GestureDetector(
                      onTap: () => context.go('/history'),
                      child: Text('See all', style: TextStyle(color: Colors.grey[500], fontSize: 13)),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              if (_transactions.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Text('No transactions yet', style: TextStyle(color: Colors.grey[400]), textAlign: TextAlign.center),
                )
              else
                Container(
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.grey[200]!),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: Column(
                      children: _transactions.map((tx) => TransactionTile(transaction: tx)).toList(),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _QuickAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _QuickAction({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 16),
          decoration: BoxDecoration(
            border: Border.all(color: Colors.grey[200]!),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            children: [
              Icon(icon, color: Colors.black),
              const SizedBox(height: 6),
              Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500)),
            ],
          ),
        ),
      ),
    );
  }
}
