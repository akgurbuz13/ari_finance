class FxQuote {
  final String quoteId;
  final String sourceCurrency;
  final String targetCurrency;
  final double sourceAmount;
  final double targetAmount;
  final double customerRate;
  final String spread;
  final DateTime expiresAt;
  final String status;

  FxQuote({
    required this.quoteId,
    required this.sourceCurrency,
    required this.targetCurrency,
    required this.sourceAmount,
    required this.targetAmount,
    required this.customerRate,
    required this.spread,
    required this.expiresAt,
    required this.status,
  });

  factory FxQuote.fromJson(Map<String, dynamic> json) => FxQuote(
        quoteId: json['quoteId'],
        sourceCurrency: json['sourceCurrency'],
        targetCurrency: json['targetCurrency'],
        sourceAmount: double.parse(json['sourceAmount'].toString()),
        targetAmount: double.parse(json['targetAmount'].toString()),
        customerRate: double.parse(json['customerRate'].toString()),
        spread: json['spread'].toString(),
        expiresAt: DateTime.parse(json['expiresAt']),
        status: json['status'],
      );
}
