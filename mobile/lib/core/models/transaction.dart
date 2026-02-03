class Transaction {
  final String id;
  final String type;
  final String status;
  final String? referenceId;
  final String createdAt;
  final String? completedAt;

  Transaction({
    required this.id,
    required this.type,
    required this.status,
    this.referenceId,
    required this.createdAt,
    this.completedAt,
  });

  factory Transaction.fromJson(Map<String, dynamic> json) => Transaction(
        id: json['id'],
        type: json['type'],
        status: json['status'],
        referenceId: json['referenceId'],
        createdAt: json['createdAt'],
        completedAt: json['completedAt'],
      );

  String get displayType => type.replaceAll('_', ' ');
}
