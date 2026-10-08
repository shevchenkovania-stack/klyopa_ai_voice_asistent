import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';

class ApiClient {
  final Dio _dio;

  ApiClient(this._dio) {
    // Ensure Dio parses JSON automatically
    _dio.options.responseType = ResponseType.json;
  }

  Future<Map<String, dynamic>> get(
    String url, {
    Map<String, String>? headers,
    Duration? timeout,
  }) async {
    try {
      final response = await _dio.get(
        url,
        options: Options(
          headers: headers,
          responseType: ResponseType.json,
          sendTimeout: timeout ?? const Duration(seconds: 15),
          receiveTimeout: timeout ?? const Duration(seconds: 15),
        ),
      );
      
      // Handle case where response.data is a String (not parsed)
      if (response.data is String) {
        return jsonDecode(response.data as String) as Map<String, dynamic>;
      }
      return response.data as Map<String, dynamic>;
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout) {
        throw TimeoutException('Request timed out');
      }
      if (e.type == DioExceptionType.connectionError) {
        throw NetworkException('No network connection');
      }
      throw ApiException(
        e.message ?? 'API error',
        statusCode: e.response?.statusCode,
        responseData: e.response?.data,
      );
    }
  }

  Future<Map<String, dynamic>> post(
    String url, {
    required Map<String, dynamic> body,
    required Map<String, String> headers,
    Duration? timeout,
  }) async {
    try {
      final response = await _dio.post(
        url,
        data: body,
        options: Options(
          headers: headers,
          sendTimeout: timeout ?? const Duration(seconds: 30),
          receiveTimeout: timeout ?? const Duration(seconds: 30),
        ),
      );
      return response.data as Map<String, dynamic>;
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout) {
        throw TimeoutException('Request timed out');
      }
      if (e.type == DioExceptionType.connectionError) {
        throw NetworkException('No network connection');
      }
      throw ApiException(
        e.message ?? 'API error',
        statusCode: e.response?.statusCode,
        responseData: e.response?.data,
      );
    }
  }

  Future<Uint8List> postBytes(
    String url, {
    required Map<String, dynamic> body,
    required Map<String, String> headers,
    Duration? timeout,
  }) async {
    try {
      final response = await _dio.post(
        url,
        data: body,
        options: Options(
          headers: headers,
          responseType: ResponseType.bytes,
          sendTimeout: timeout ?? const Duration(seconds: 30),
          receiveTimeout: timeout ?? const Duration(seconds: 30),
        ),
      );
      return Uint8List.fromList(response.data as List<int>);
    } on DioException catch (e) {
      if (e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout) {
        throw TimeoutException('Request timed out');
      }
      if (e.type == DioExceptionType.connectionError) {
        throw NetworkException('No network connection');
      }
      throw ApiException(
        e.message ?? 'API error',
        statusCode: e.response?.statusCode,
        responseData: e.response?.data,
      );
    }
  }

  Future<String> postMultipart(
    String url, {
    required String filePath,
    required Map<String, String> headers,
    Map<String, String>? fields,
  }) async {
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(filePath),
      if (fields != null) ...fields,
    });

    final response = await _dio.post(
      url,
      data: formData,
      options: Options(headers: headers),
    );
    return (response.data as Map<String, dynamic>)['text'] ?? '';
  }
}

class TimeoutException implements Exception {
  final String message;
  TimeoutException(this.message);
}

class NetworkException implements Exception {
  final String message;
  NetworkException(this.message);
}

class ApiException implements Exception {
  final String message;
  final int? statusCode;
  final dynamic responseData;
  
  ApiException(this.message, {this.statusCode, this.responseData});

  @override
  String toString() {
    final buf = StringBuffer('ApiException: $message');
    if (statusCode != null) buf.write(' (status: $statusCode)');
    if (responseData != null) buf.write(' response: $responseData');
    return buf.toString();
  }
}
