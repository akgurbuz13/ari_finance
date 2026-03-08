import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../core/models/account.dart';
import '../../../core/models/fx_quote.dart';
import '../../../shared/widgets/ari_button.dart';
import '../../../shared/widgets/ari_text_field.dart';

class TransferScreen extends ConsumerStatefulWidget {
  const TransferScreen({super.key});

  @override
  ConsumerState<TransferScreen> createState() => _TransferScreenState();
}

class _TransferScreenState extends ConsumerState<TransferScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Domestic
  final _receiverController = TextEditingController();
  final _domesticAmountController = TextEditingController();
  String? _domesticSourceAccountId;
  String _domesticCurrency = 'TRY';
  bool _domesticLoading = false;
  String? _domesticSuccess;
  String? _domesticError;

  // Cross-border
  final _crossBorderAmountController = TextEditingController();
  final _crossBorderReceiverController = TextEditingController();
  String? _crossBorderSourceAccountId;
  FxQuote? _quote;
  int _countdown = 0;
  Timer? _countdownTimer;
  bool _crossBorderLoading = false;
  String? _crossBorderSuccess;
  String? _crossBorderError;
  // Steps: form, quote, success
  String _crossBorderStep = 'form';

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
    _receiverController.dispose();
    _domesticAmountController.dispose();
    _crossBorderAmountController.dispose();
    _crossBorderReceiverController.dispose();
    _countdownTimer?.cancel();
    super.dispose();
  }

  Future<void> _loadAccounts() async {
    try {
      final res = await ApiClient.instance.dio.get(ApiEndpoints.accounts);
      setState(() {
        _accounts = (res.data as List).map((e) => Account.fromJson(e)).toList();
        if (_accounts.isNotEmpty) {
          _domesticSourceAccountId = _accounts.first.id;
          _crossBorderSourceAccountId = _accounts.first.id;
        }
      });
    } catch (_) {}
  }

  String _getTargetCurrency(String? sourceAccountId) {
    if (sourceAccountId == null) return 'EUR';
    final account = _accounts.where((a) => a.id == sourceAccountId).firstOrNull;
    if (account == null) return 'EUR';
    return account.currency == 'TRY' ? 'EUR' : 'TRY';
  }

  Account? _getAccount(String? id) {
    if (id == null) return null;
    return _accounts.where((a) => a.id == id).firstOrNull;
  }

  // --- Domestic Transfer ---

  Future<void> _sendDomestic() async {
    if (_domesticSourceAccountId == null) return;
    setState(() { _domesticLoading = true; _domesticError = null; _domesticSuccess = null; });
    try {
      await ApiClient.instance.dio.post(
        ApiEndpoints.domesticTransfer,
        data: {
          'senderAccountId': _domesticSourceAccountId,
          'receiverAccountId': _receiverController.text,
          'amount': double.parse(_domesticAmountController.text),
          'currency': _domesticCurrency,
        },
        options: Options(headers: {'Idempotency-Key': const Uuid().v4()}),
      );
      setState(() {
        _domesticSuccess = 'Transfer completed successfully';
        _domesticAmountController.clear();
        _receiverController.clear();
      });
    } catch (e) {
      setState(() => _domesticError = 'Transfer failed. Please try again.');
    } finally {
      if (mounted) setState(() => _domesticLoading = false);
    }
  }

  // --- Cross-Border Transfer ---

  Future<void> _getQuote() async {
    if (_crossBorderSourceAccountId == null) return;
    final amountText = _crossBorderAmountController.text;
    if (amountText.isEmpty) return;

    final sourceAccount = _getAccount(_crossBorderSourceAccountId);
    if (sourceAccount == null) return;

    final targetCurrency = _getTargetCurrency(_crossBorderSourceAccountId);

    setState(() { _crossBorderLoading = true; _crossBorderError = null; });
    try {
      final res = await ApiClient.instance.dio.post(
        ApiEndpoints.fxQuotes,
        data: {
          'sourceCurrency': sourceAccount.currency,
          'targetCurrency': targetCurrency,
          'sourceAmount': double.parse(amountText),
        },
      );
      final quote = FxQuote.fromJson(res.data);
      _startCountdown(quote.expiresAt);
      setState(() {
        _quote = quote;
        _crossBorderStep = 'quote';
        _crossBorderLoading = false;
      });
    } catch (e) {
      setState(() {
        _crossBorderError = 'Failed to get FX quote. Please try again.';
        _crossBorderLoading = false;
      });
    }
  }

  void _startCountdown(DateTime expiresAt) {
    _countdownTimer?.cancel();
    _countdown = expiresAt.difference(DateTime.now()).inSeconds;
    if (_countdown <= 0) _countdown = 0;

    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) { timer.cancel(); return; }
      setState(() {
        _countdown--;
        if (_countdown <= 0) {
          timer.cancel();
          _countdown = 0;
          _crossBorderError = 'Quote expired. Please request a new one.';
          _crossBorderStep = 'form';
          _quote = null;
        }
      });
    });
  }

  Future<void> _confirmCrossBorder() async {
    if (_quote == null || _crossBorderSourceAccountId == null) return;
    if (_crossBorderReceiverController.text.isEmpty) {
      setState(() => _crossBorderError = 'Please enter a recipient account ID.');
      return;
    }

    setState(() { _crossBorderLoading = true; _crossBorderError = null; });
    try {
      await ApiClient.instance.dio.post(
        ApiEndpoints.crossBorderPayment,
        data: {
          'senderAccountId': _crossBorderSourceAccountId,
          'receiverAccountId': _crossBorderReceiverController.text,
          'quoteId': _quote!.quoteId,
          'sourceAmount': _quote!.sourceAmount,
          'sourceCurrency': _quote!.sourceCurrency,
          'targetCurrency': _quote!.targetCurrency,
        },
        options: Options(headers: {'Idempotency-Key': const Uuid().v4()}),
      );
      _countdownTimer?.cancel();
      setState(() {
        _crossBorderStep = 'success';
        _crossBorderSuccess = 'Cross-border transfer submitted successfully';
        _crossBorderLoading = false;
      });
    } catch (e) {
      setState(() {
        _crossBorderError = 'Transfer failed. Please try again.';
        _crossBorderLoading = false;
      });
    }
  }

  void _resetCrossBorder() {
    _countdownTimer?.cancel();
    setState(() {
      _crossBorderStep = 'form';
      _quote = null;
      _countdown = 0;
      _crossBorderSuccess = null;
      _crossBorderError = null;
      _crossBorderAmountController.clear();
      _crossBorderReceiverController.clear();
    });
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
          if (_domesticSuccess != null) ...[
            _StatusBanner(message: _domesticSuccess!, isError: false),
            const SizedBox(height: 16),
          ],
          if (_domesticError != null) ...[
            _StatusBanner(message: _domesticError!, isError: true),
            const SizedBox(height: 16),
          ],
          if (_accounts.isNotEmpty) ...[
            DropdownButtonFormField<String>(
              value: _domesticSourceAccountId,
              decoration: InputDecoration(
                labelText: 'From Account',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
              items: _accounts.map((a) => DropdownMenuItem(
                value: a.id,
                child: Text('${a.currency} - ${a.id.substring(0, 8)}...'),
              )).toList(),
              onChanged: (v) => setState(() => _domesticSourceAccountId = v),
            ),
            const SizedBox(height: 16),
          ],
          AriTextField(
            controller: _receiverController,
            label: 'Recipient Account ID',
            hint: 'Enter recipient account ID',
          ),
          const SizedBox(height: 16),
          AriTextField(
            controller: _domesticAmountController,
            label: 'Amount',
            keyboardType: TextInputType.number,
            hint: '0.00',
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            value: _domesticCurrency,
            decoration: InputDecoration(
              labelText: 'Currency',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            ),
            items: const [
              DropdownMenuItem(value: 'TRY', child: Text('TRY (₺)')),
              DropdownMenuItem(value: 'EUR', child: Text('EUR (€)')),
            ],
            onChanged: (v) => setState(() => _domesticCurrency = v!),
          ),
          const SizedBox(height: 24),
          AriButton(
            label: _domesticLoading ? 'Processing...' : 'Send Money',
            onPressed: _domesticLoading ? null : _sendDomestic,
          ),
        ],
      ),
    );
  }

  Widget _buildCrossBorderTab() {
    switch (_crossBorderStep) {
      case 'quote':
        return _buildQuoteStep();
      case 'success':
        return _buildSuccessStep();
      default:
        return _buildCrossBorderForm();
    }
  }

  Widget _buildCrossBorderForm() {
    final targetCurrency = _getTargetCurrency(_crossBorderSourceAccountId);
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_crossBorderError != null) ...[
            _StatusBanner(message: _crossBorderError!, isError: true),
            const SizedBox(height: 16),
          ],
          Text(
            'Send money across borders with real-time FX rates.',
            style: TextStyle(color: Colors.grey[500], fontSize: 13),
          ),
          const SizedBox(height: 20),
          if (_accounts.isNotEmpty) ...[
            DropdownButtonFormField<String>(
              value: _crossBorderSourceAccountId,
              decoration: InputDecoration(
                labelText: 'From Account',
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              ),
              items: _accounts.map((a) => DropdownMenuItem(
                value: a.id,
                child: Text('${a.currency} - ${a.id.substring(0, 8)}...'),
              )).toList(),
              onChanged: (v) => setState(() => _crossBorderSourceAccountId = v),
            ),
            const SizedBox(height: 16),
          ],
          AriTextField(
            controller: _crossBorderReceiverController,
            label: 'Recipient Account ID',
            hint: 'Enter recipient account ID',
          ),
          const SizedBox(height: 16),
          AriTextField(
            controller: _crossBorderAmountController,
            label: 'Amount (${_getAccount(_crossBorderSourceAccountId)?.currency ?? 'TRY'})',
            keyboardType: TextInputType.number,
            hint: '0.00',
          ),
          const SizedBox(height: 8),
          Text(
            'Recipient will receive $targetCurrency at the current FX rate.',
            style: TextStyle(color: Colors.grey[400], fontSize: 12),
          ),
          const SizedBox(height: 24),
          AriButton(
            label: _crossBorderLoading ? 'Getting Quote...' : 'Get FX Quote',
            onPressed: _crossBorderLoading ? null : _getQuote,
          ),
        ],
      ),
    );
  }

  Widget _buildQuoteStep() {
    if (_quote == null) return const SizedBox();

    final minutes = _countdown ~/ 60;
    final seconds = _countdown % 60;
    final timerColor = _countdown <= 15 ? Colors.red : Colors.black;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          if (_crossBorderError != null) ...[
            _StatusBanner(message: _crossBorderError!, isError: true),
            const SizedBox(height: 16),
          ],

          // Timer
          Container(
            padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
            decoration: BoxDecoration(
              color: _countdown <= 15 ? Colors.red[50] : Colors.grey[100],
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              'Expires in ${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}',
              style: TextStyle(fontWeight: FontWeight.w600, color: timerColor, fontSize: 13),
            ),
          ),
          const SizedBox(height: 24),

          // Quote details card
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey[200]!),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              children: [
                const Text('FX Quote', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 16)),
                const SizedBox(height: 16),
                _QuoteRow(label: 'You send', value: '${_quote!.sourceAmount.toStringAsFixed(2)} ${_quote!.sourceCurrency}'),
                _QuoteRow(label: 'They receive', value: '${_quote!.targetAmount.toStringAsFixed(2)} ${_quote!.targetCurrency}'),
                _QuoteRow(label: 'Exchange rate', value: '1 ${_quote!.sourceCurrency} = ${_quote!.customerRate.toStringAsFixed(6)} ${_quote!.targetCurrency}'),
                _QuoteRow(label: 'Spread', value: _quote!.spread),
              ],
            ),
          ),
          const SizedBox(height: 24),
          AriButton(
            label: _crossBorderLoading ? 'Submitting...' : 'Confirm Transfer',
            onPressed: (_crossBorderLoading || _countdown <= 0) ? null : _confirmCrossBorder,
          ),
          const SizedBox(height: 12),
          TextButton(
            onPressed: _resetCrossBorder,
            child: const Text('Cancel', style: TextStyle(color: Colors.grey)),
          ),
        ],
      ),
    );
  }

  Widget _buildSuccessStep() {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: BoxDecoration(
              color: Colors.green[50],
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.check, color: Colors.green[600], size: 36),
          ),
          const SizedBox(height: 24),
          const Text(
            'Transfer Submitted',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text(
            _crossBorderSuccess ?? 'Your cross-border transfer is being processed.',
            style: TextStyle(color: Colors.grey[500], fontSize: 14),
            textAlign: TextAlign.center,
          ),
          if (_quote != null) ...[
            const SizedBox(height: 16),
            Text(
              '${_quote!.sourceAmount.toStringAsFixed(2)} ${_quote!.sourceCurrency} → ${_quote!.targetAmount.toStringAsFixed(2)} ${_quote!.targetCurrency}',
              style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
            ),
          ],
          const SizedBox(height: 32),
          AriButton(label: 'New Transfer', onPressed: _resetCrossBorder),
        ],
      ),
    );
  }
}

class _StatusBanner extends StatelessWidget {
  final String message;
  final bool isError;
  const _StatusBanner({required this.message, required this.isError});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isError ? Colors.red[50] : Colors.green[50],
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        message,
        style: TextStyle(color: isError ? Colors.red[700] : Colors.green[700], fontSize: 13),
      ),
    );
  }
}

class _QuoteRow extends StatelessWidget {
  final String label;
  final String value;
  const _QuoteRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: Colors.grey[500], fontSize: 13)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
        ],
      ),
    );
  }
}
