import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../core/api/api_client.dart';
import '../../../core/api/api_endpoints.dart';
import '../../../core/models/user.dart';

final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier();
});

class AuthState {
  final User? user;
  final bool isAuthenticated;
  final bool isLoading;
  final String? error;

  const AuthState({this.user, this.isAuthenticated = false, this.isLoading = false, this.error});

  AuthState copyWith({User? user, bool? isAuthenticated, bool? isLoading, String? error}) {
    return AuthState(
      user: user ?? this.user,
      isAuthenticated: isAuthenticated ?? this.isAuthenticated,
      isLoading: isLoading ?? this.isLoading,
      error: error,
    );
  }
}

class AuthNotifier extends StateNotifier<AuthState> {
  final _storage = const FlutterSecureStorage();

  AuthNotifier() : super(const AuthState()) {
    _checkAuth();
  }

  Future<void> _checkAuth() async {
    final token = await _storage.read(key: 'accessToken');
    if (token != null) {
      await fetchUser();
    }
  }

  Future<void> login(String email, String password, {String? totpCode}) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      final response = await ApiClient.instance.post(ApiEndpoints.login, data: {
        'email': email,
        'password': password,
        if (totpCode != null) 'totpCode': totpCode,
      });

      await _storage.write(key: 'accessToken', value: response.data['accessToken']);
      await _storage.write(key: 'refreshToken', value: response.data['refreshToken']);
      await fetchUser();
    } catch (e) {
      state = state.copyWith(isLoading: false, error: 'Login failed');
      rethrow;
    }
  }

  Future<void> signup(String email, String phone, String password, String region) async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      final response = await ApiClient.instance.post(ApiEndpoints.signup, data: {
        'email': email,
        'phone': phone,
        'password': password,
        'region': region,
      });

      await _storage.write(key: 'accessToken', value: response.data['accessToken']);
      await _storage.write(key: 'refreshToken', value: response.data['refreshToken']);
      await fetchUser();
    } catch (e) {
      state = state.copyWith(isLoading: false, error: 'Signup failed');
      rethrow;
    }
  }

  Future<void> fetchUser() async {
    try {
      final response = await ApiClient.instance.get(ApiEndpoints.userProfile);
      final user = User.fromJson(response.data);
      state = AuthState(user: user, isAuthenticated: true);
    } catch (_) {
      state = const AuthState();
    }
  }

  Future<void> logout() async {
    try {
      await ApiClient.instance.post(ApiEndpoints.logout);
    } finally {
      await _storage.deleteAll();
      state = const AuthState();
    }
  }
}
