import 'package:ai_voice_agent/core/network/api_client.dart';

class WhisperApi {
  final ApiClient _client;
  static const _baseUrl = 'https://api.openai.com/v1/audio/transcriptions';

  WhisperApi(this._client);

  Future<String> transcribe(String filePath, String apiKey, {String? language}) async {
    final result = await _client.postMultipart(
      _baseUrl,
      filePath: filePath,
      headers: {
        'Authorization': 'Bearer $apiKey',
      },
      fields: {
        'model': 'whisper-1',
        if (language != null) 'language': language,
      },
    );
    return result;
  }
}
