import 'dart:convert';
import 'package:ai_voice_agent/core/network/api_client.dart';

class GrokApi {
  final ApiClient _client;
  static const _baseUrl = 'https://api.groq.com/openai/v1/chat/completions';

  GrokApi(this._client);

  Future<Map<String, dynamic>> chat(
    String apiKey,
    List<Map<String, String>> messages, {
    Duration? timeout,
  }) async {
    final response = await _client.post(
      _baseUrl,
      body: {
        'model': 'llama-3.3-70b-versatile',
        'messages': messages,
        'temperature': 0.7,
        'max_tokens': 1024,
      },
      headers: {
        'Authorization': 'Bearer $apiKey',
        'Content-Type': 'application/json',
      },
      timeout: timeout,
    );

    final content = response['choices'][0]['message']['content'] as String;
    return _parseJsonResponse(content);
  }

  Map<String, dynamic> _parseJsonResponse(String content) {
    // Try to extract JSON from response
    final jsonStart = content.indexOf('{');
    final jsonEnd = content.lastIndexOf('}');
    if (jsonStart != -1 && jsonEnd != -1) {
      final jsonStr = content.substring(jsonStart, jsonEnd + 1);
      try {
        return Map<String, dynamic>.from(
          _decodeJson(jsonStr),
        );
      } catch (_) {}
    }
    // Fallback: wrap as simple response
    return {
      'response': content,
      'action': null,
      'params': null,
      'needs_confirmation': false,
    };
  }

  dynamic _decodeJson(String str) {
    return jsonDecode(str);
  }
}
