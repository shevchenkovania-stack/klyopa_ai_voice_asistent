import 'package:ai_voice_agent/domain/repositories/i_speech_repository.dart';
import 'package:ai_voice_agent/domain/repositories/i_ai_repository.dart';
import 'package:ai_voice_agent/domain/entities/conversation.dart';

class ProcessVoiceInput {
  final ISpeechRepository _speechRepository;
  final IAIRepository _aiRepository;
  final Conversation _conversation;

  ProcessVoiceInput(
    this._speechRepository,
    this._aiRepository,
    this._conversation,
  );

  Future<String> transcribe(String audioPath, {String? language}) async {
    return await _speechRepository.transcribeAudio(audioPath, language: language);
  }

  Future<AiResponse> analyze(String text) async {
    _conversation.addUserMessage(text);
    final response = await _aiRepository.processCommand(text, _conversation);
    _conversation.addAssistantMessage(response.response);
    return response;
  }

  void clearContext() => _conversation.clear();
}
