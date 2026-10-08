import 'dart:convert';
import 'package:ai_voice_agent/core/network/api_client.dart';

class OpenAIApi {
  final ApiClient _client;
  static const _baseUrl = 'https://api.openai.com/v1/chat/completions';

  OpenAIApi(this._client);

  Future<Map<String, dynamic>> chat(
    String apiKey,
    List<Map<String, String>> messages,
  ) async {
    final response = await _client.post(
      _baseUrl,
      body: {
        'model': 'gpt-4o-mini',
        'messages': messages,
        'temperature': 0.7,
        'max_tokens': 1024,
      },
      headers: {
        'Authorization': 'Bearer $apiKey',
        'Content-Type': 'application/json',
      },
    );

    final content = response['choices'][0]['message']['content'] as String;
    return _parseJsonResponse(content);
  }

  Map<String, dynamic> _parseJsonResponse(String content) {
    final jsonStart = content.indexOf('{');
    final jsonEnd = content.lastIndexOf('}');
    if (jsonStart != -1 && jsonEnd != -1) {
      final jsonStr = content.substring(jsonStart, jsonEnd + 1);
      try {
        return Map<String, dynamic>.from(jsonDecode(jsonStr));
      } catch (_) {}
    }
    return {
      'response': content,
      'action': null,
      'params': null,
      'needs_confirmation': false,
    };
  }
}
