class User {
  final String id;
  final String email;
  final String phone;
  final String? firstName;
  final String? lastName;
  final String status;
  final String region;
  final bool totpEnabled;
  final String createdAt;

  User({
    required this.id,
    required this.email,
    required this.phone,
    this.firstName,
    this.lastName,
    required this.status,
    required this.region,
    required this.totpEnabled,
    required this.createdAt,
  });

  factory User.fromJson(Map<String, dynamic> json) => User(
        id: json['id'],
        email: json['email'],
        phone: json['phone'],
        firstName: json['firstName'],
        lastName: json['lastName'],
        status: json['status'],
        region: json['region'],
        totpEnabled: json['totpEnabled'] ?? false,
        createdAt: json['createdAt'],
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'email': email,
        'phone': phone,
        'firstName': firstName,
        'lastName': lastName,
        'status': status,
        'region': region,
        'totpEnabled': totpEnabled,
        'createdAt': createdAt,
      };
}
