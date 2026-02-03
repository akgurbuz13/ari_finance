class PaymentOrder {
  final String id;
  final String type;
  final String status;
  final String senderAccountId;
  final String receiverAccountId;
  final String amount;
  final String currency;
  final String createdAt;

  PaymentOrder({
    required this.id,
    required this.type,
    required this.status,
    required this.senderAccountId,
    required this.receiverAccountId,
    required this.amount,
    required this.currency,
    required this.createdAt,
  });

  factory PaymentOrder.fromJson(Map<String, dynamic> json) => PaymentOrder(
        id: json['id'],
        type: json['type'],
        status: json['status'],
        senderAccountId: json['senderAccountId'],
        receiverAccountId: json['receiverAccountId'],
        amount: json['amount'],
        currency: json['currency'],
        createdAt: json['createdAt'],
      );
}
