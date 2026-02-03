import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiClient {
  static const String baseUrl = 'http://localhost:8080/api/v1';
  static final storage = FlutterSecureStorage();

  static final Dio _dio = Dio(BaseOptions(
    baseUrl: baseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
    headers: {'Content-Type': 'application/json'},
  ))
    ..interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await storage.read(key: 'accessToken');
        if (token != null) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        if (error.response?.statusCode == 401) {
          try {
            final refreshToken = await storage.read(key: 'refreshToken');
            if (refreshToken == null) return handler.next(error);

            final response = await Dio().post('$baseUrl/auth/refresh', data: {
              'refreshToken': refreshToken,
            });

            await storage.write(key: 'accessToken', value: response.data['accessToken']);
            await storage.write(key: 'refreshToken', value: response.data['refreshToken']);

            error.requestOptions.headers['Authorization'] = 'Bearer ${response.data['accessToken']}';
            final retryResponse = await _dio.fetch(error.requestOptions);
            return handler.resolve(retryResponse);
          } catch (_) {
            await storage.deleteAll();
            return handler.next(error);
          }
        }
        handler.next(error);
      },
    ));

  static Dio get instance => _dio;
}
