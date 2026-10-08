import 'package:ai_voice_agent/domain/entities/conversation.dart';

class AiResponse {
  final String response;
  final String? actionId;
  final Map<String, dynamic>? params;
  final bool needsConfirmation;

  const AiResponse({
    required this.response,
    this.actionId,
    this.params,
    this.needsConfirmation = false,
  });
}

abstract class IAIRepository {
  Future<AiResponse> processCommand(String text, Conversation conversation);
}
