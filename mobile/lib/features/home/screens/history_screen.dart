import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../core/models/account.dart';
import '../../../core/models/transaction.dart';
import '../../../shared/widgets/transaction_tile.dart';

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  List<Account> _accounts = [];
  List<Transaction> _transactions = [];
  String? _selectedAccountId;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadAccounts();
  }

  Future<void> _loadAccounts() async {
    try {
      final res = await ApiClient.instance.dio.get(ApiEndpoints.accounts);
      _accounts = (res.data as List).map((e) => Account.fromJson(e)).toList();
      if (_accounts.isNotEmpty) {
        _selectedAccountId = _accounts.first.id;
        await _loadTransactions();
      }
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _loadTransactions() async {
    if (_selectedAccountId == null) return;
    setState(() => _loading = true);
    try {
      final res = await ApiClient.instance.dio.get(
        ApiEndpoints.accountTransactions(_selectedAccountId!),
        queryParameters: {'limit': 50},
      );
      _transactions = (res.data as List).map((e) => Transaction.fromJson(e)).toList();
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: const Text('Transaction History', style: TextStyle(color: Colors.black)),
        actions: [
          if (_accounts.length > 1)
            Padding(
              padding: const EdgeInsets.only(right: 16),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<String>(
                  value: _selectedAccountId,
                  items: _accounts.map((a) => DropdownMenuItem(
                    value: a.id,
                    child: Text('${a.currency}', style: const TextStyle(fontSize: 14)),
                  )).toList(),
                  onChanged: (v) {
                    setState(() => _selectedAccountId = v);
                    _loadTransactions();
                  },
                ),
              ),
            ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: Colors.black))
          : _transactions.isEmpty
              ? Center(
                  child: Text('No transactions found', style: TextStyle(color: Colors.grey[400])),
                )
              : RefreshIndicator(
                  color: Colors.black,
                  onRefresh: _loadTransactions,
                  child: ListView.builder(
                    itemCount: _transactions.length,
                    itemBuilder: (_, i) => TransactionTile(transaction: _transactions[i]),
                  ),
                ),
    );
  }
}
