class Account {
  final String id;
  final String currency;
  final String accountType;
  final String status;
  final String balance;
  final String createdAt;

  Account({
    required this.id,
    required this.currency,
    required this.accountType,
    required this.status,
    required this.balance,
    required this.createdAt,
  });

  factory Account.fromJson(Map<String, dynamic> json) => Account(
        id: json['id'],
        currency: json['currency'],
        accountType: json['accountType'],
        status: json['status'],
        balance: json['balance'],
        createdAt: json['createdAt'],
      );

  String get symbol => currency == 'TRY' ? '₺' : '€';
}
