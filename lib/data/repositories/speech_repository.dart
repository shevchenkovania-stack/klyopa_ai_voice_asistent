import 'package:ai_voice_agent/data/datasources/whisper_api.dart';
import 'package:ai_voice_agent/domain/repositories/i_speech_repository.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/di/injection.dart';

class SpeechRepository implements ISpeechRepository {
  final WhisperApi _whisperApi;

  SpeechRepository(this._whisperApi);

  @override
  Future<String> transcribeAudio(String filePath, {String? language}) async {
    final config = sl<AppConfig>();
    return await _whisperApi.transcribe(
      filePath,
      config.openaiApiKey,
      language: language ?? config.language,
    );
  }
}
