import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../core/models/account.dart';
import '../../../shared/widgets/ova_button.dart';
import '../../../shared/widgets/ova_text_field.dart';

class TransferScreen extends ConsumerStatefulWidget {
  const TransferScreen({super.key});

  @override
  ConsumerState<TransferScreen> createState() => _TransferScreenState();
}

class _TransferScreenState extends ConsumerState<TransferScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _senderController = TextEditingController();
  final _receiverController = TextEditingController();
  final _amountController = TextEditingController();
  String _currency = 'TRY';
  bool _loading = false;
  String? _success;
  String? _error;
  List<Account> _accounts = [];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadAccounts();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _senderController.dispose();
    _receiverController.dispose();
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _loadAccounts() async {
    try {
      final res = await ApiClient.instance.dio.get(ApiEndpoints.accounts);
      setState(() {
        _accounts = (res.data as List).map((e) => Account.fromJson(e)).toList();
        if (_accounts.isNotEmpty) {
          _senderController.text = _accounts.first.id;
        }
      });
    } catch (_) {}
  }

  Future<void> _sendDomestic() async {
    setState(() { _loading = true; _error = null; _success = null; });
    try {
      await ApiClient.instance.dio.post(
        ApiEndpoints.domesticTransfer,
        data: {
          'senderAccountId': _senderController.text,
          'receiverAccountId': _receiverController.text,
          'amount': double.parse(_amountController.text),
          'currency': _currency,
        },
        options: Options(headers: {'Idempotency-Key': const Uuid().v4()}),
      );
      setState(() {
        _success = 'Transfer completed successfully';
        _amountController.clear();
        _receiverController.clear();
      });
    } catch (e) {
      setState(() => _error = 'Transfer failed');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 20, 20, 0),
              child: Text('Transfer', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 16),
            TabBar(
              controller: _tabController,
              labelColor: Colors.black,
              unselectedLabelColor: Colors.grey,
              indicatorColor: Colors.black,
              tabs: const [
                Tab(text: 'Domestic'),
                Tab(text: 'Cross-Border'),
              ],
            ),
            Expanded(
              child: TabBarView(
                controller: _tabController,
                children: [
                  _buildDomesticTab(),
                  _buildCrossBorderTab(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDomesticTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          if (_success != null) ...[
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: Colors.green[50], borderRadius: BorderRadius.circular(8)),
              child: Text(_success!, style: TextStyle(color: Colors.green[700], fontSize: 13)),
            ),
            const SizedBox(height: 16),
          ],
          if (_error != null) ...[
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: Colors.red[50], borderRadius: BorderRadius.circular(8)),
              child: Text(_error!, style: TextStyle(color: Colors.red[700], fontSize: 13)),
            ),
            const SizedBox(height: 16),
          ],
          if (_accounts.isNotEmpty) ...[
            DropdownButtonFormField<String>(
              value: _senderController.text.isNotEmpty ? _senderController.text : null,
              decoration: InputDecoration(
                labelText: 'From Account',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
              items: _accounts.map((a) => DropdownMenuItem(
                value: a.id,
                child: Text('${a.currency} - ${a.id.substring(0, 8)}...'),
              )).toList(),
              onChanged: (v) => _senderController.text = v ?? '',
            ),
            const SizedBox(height: 16),
          ],
          OvaTextField(
            controller: _receiverController,
            label: 'Recipient Account ID',
            hint: 'Enter recipient account ID',
          ),
          const SizedBox(height: 16),
          OvaTextField(
            controller: _amountController,
            label: 'Amount',
            keyboardType: TextInputType.number,
            hint: '0.00',
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            value: _currency,
            decoration: InputDecoration(
              labelText: 'Currency',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            ),
            items: const [
              DropdownMenuItem(value: 'TRY', child: Text('TRY (₺)')),
              DropdownMenuItem(value: 'EUR', child: Text('EUR (€)')),
            ],
            onChanged: (v) => setState(() => _currency = v!),
          ),
          const SizedBox(height: 24),
          OvaButton(
            label: _loading ? 'Processing...' : 'Send Money',
            onPressed: _loading ? null : _sendDomestic,
          ),
        ],
      ),
    );
  }

  Widget _buildCrossBorderTab() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.language, size: 48, color: Colors.grey[300]),
            const SizedBox(height: 16),
            Text(
              'Cross-border transfers require an FX quote.',
              style: TextStyle(color: Colors.grey[500]),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              'Get a quote from the FX rates page, then use the quote ID to initiate the transfer.',
              style: TextStyle(color: Colors.grey[400], fontSize: 13),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

