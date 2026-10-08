import 'package:ai_voice_agent/domain/entities/voice_command.dart';

abstract class IHistoryRepository {
  Future<List<VoiceCommand>> getHistory({int limit = 50});
  Future<void> addCommand(VoiceCommand command);
  Future<void> clearHistory();
}
