class ApiEndpoints {
  // Auth
  static const signup = '/auth/signup';
  static const login = '/auth/login';
  static const refresh = '/auth/refresh';
  static const logout = '/auth/logout';
  static const setup2fa = '/auth/2fa/setup';
  static const enable2fa = '/auth/2fa/enable';

  // Users
  static const userProfile = '/users/me';
  static const me = '/users/me';

  // Accounts
  static const accounts = '/accounts';
  static String accountBalance(String id) => '/accounts/$id/balance';

  // Transactions
  static String transactionsByAccount(String id) => '/transactions/account/$id';
  static String accountTransactions(String id) => '/transactions/account/$id';
  static String transactionDetail(String id) => '/transactions/$id';

  // Payments
  static const domesticTransfer = '/payments/domestic';
  static const domesticPayment = '/payments/domestic';
  static const crossBorderPayment = '/payments/cross-border';
  static String paymentDetail(String id) => '/payments/$id';

  // FX
  static const fxRates = '/fx/rates';
  static const fxQuotes = '/fx/quotes';
  static const fxConvert = '/fx/convert';

  // KYC
  static const kycInitiate = '/kyc/initiate';
  static const kycStatus = '/kyc/status';
  static const kycHistory = '/kyc/history';
}
