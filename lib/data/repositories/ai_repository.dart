import 'dart:async';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/config/ai_prompts.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/data/datasources/grok_api.dart';
import 'package:ai_voice_agent/data/datasources/openai_api.dart';
import 'package:ai_voice_agent/domain/entities/conversation.dart';
import 'package:ai_voice_agent/domain/repositories/i_ai_repository.dart';
import 'package:ai_voice_agent/domain/repositories/i_action_registry.dart';
import 'package:ai_voice_agent/core/network/api_client.dart' show TimeoutException;

class AIRepository implements IAIRepository {
  final GrokApi _grokApi;
  final OpenAIApi _openaiApi;
  final IActionRegistry _actionRegistry;

  AIRepository(this._grokApi, this._openaiApi, this._actionRegistry);

  @override
  Future<AiResponse> processCommand(String text, Conversation conversation) async {
    final config = sl<AppConfig>();
    final actions = _actionRegistry.getAvailableActions();
    final systemPrompt = AiPrompts.systemPrompt(config.agentName, actions);

    final messages = [
      {'role': 'system', 'content': systemPrompt},
      ...conversation.toApiFormat(),
    ];

    Map<String, dynamic> result;

    try {
      // Primary: Groq with timeout
      result = await _grokApi
          .chat(config.groqApiKey, messages,
              timeout: Duration(seconds: config.aiTimeoutSeconds))
          .timeout(Duration(seconds: config.aiTimeoutSeconds));
    } on TimeoutException {
      // Fallback: OpenAI
      result = await _openaiApi.chat(config.openaiApiKey, messages);
    } on Exception {
      try {
        // Fallback: OpenAI
        result = await _openaiApi.chat(config.openaiApiKey, messages);
      } catch (_) {
        return const AiResponse(
          response: 'Извини, сейчас не могу обработать. Попробуй позже.',
        );
      }
    }

    return AiResponse(
      response: result['response'] as String? ?? '',
      actionId: result['action'] as String?,
      params: result['params'] as Map<String, dynamic>?,
      needsConfirmation: result['needs_confirmation'] as bool? ?? false,
    );
  }
}
