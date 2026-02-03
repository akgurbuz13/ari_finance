import 'package:flutter/material.dart';
import '../../core/models/account.dart';

class BalanceCard extends StatelessWidget {
  final Account account;
  final VoidCallback? onTap;

  const BalanceCard({super.key, required this.account, this.onTap});

  String get _currencySymbol => account.currency == 'TRY' ? '₺' : '€';

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: Colors.black,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${account.currency} Account',
                  style: TextStyle(color: Colors.grey[400], fontSize: 13),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: account.status == 'active'
                        ? Colors.green.withValues(alpha: 0.2)
                        : Colors.red.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    account.status,
                    style: TextStyle(
                      color: account.status == 'active' ? Colors.green[300] : Colors.red[300],
                      fontSize: 11,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              '$_currencySymbol${_formatBalance(account.balance)}',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 28,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              account.accountType.replaceAll('_', ' '),
              style: TextStyle(color: Colors.grey[500], fontSize: 12),
            ),
          ],
        ),
      ),
    );
  }

  String _formatBalance(String balance) {
    final value = double.tryParse(balance) ?? 0;
    return value.toStringAsFixed(2).replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
      (match) => '${match[1]},',
    );
  }
}
