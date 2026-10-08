import 'package:ai_voice_agent/domain/entities/conversation.dart';
import 'package:ai_voice_agent/domain/repositories/i_history_repository.dart';
import 'package:ai_voice_agent/domain/entities/voice_command.dart';
import 'package:uuid/uuid.dart';
import 'package:ai_voice_agent/domain/repositories/i_ai_repository.dart';

class ManageConversation {
  final IHistoryRepository _historyRepository;
  final Conversation conversation = Conversation();
  static final _uuid = Uuid();

  ManageConversation(this._historyRepository);

  Future<void> saveCommand({
    required String rawText,
    required AiResponse aiResponse,
  }) async {
    final command = VoiceCommand(
      id: _uuid.v4(),
      rawText: rawText,
      actionId: aiResponse.actionId,
      params: aiResponse.params,
      needsConfirmation: aiResponse.needsConfirmation,
      response: aiResponse.response,
    );
    await _historyRepository.addCommand(command);
  }

  void clear() => conversation.clear();
}
