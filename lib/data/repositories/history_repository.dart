import 'package:ai_voice_agent/data/datasources/local_db.dart';
import 'package:ai_voice_agent/domain/entities/voice_command.dart';
import 'package:ai_voice_agent/domain/repositories/i_history_repository.dart';

class HistoryRepository implements IHistoryRepository {
  final LocalDb _localDb;

  HistoryRepository(this._localDb);

  @override
  Future<List<VoiceCommand>> getHistory({int limit = 50}) async {
    final raw = _localDb.getHistory(limit: limit);
    return raw.map((e) => VoiceCommand.fromJson(e)).toList();
  }

  @override
  Future<void> addCommand(VoiceCommand command) async {
    await _localDb.addToHistory(command.toJson());
  }

  @override
  Future<void> clearHistory() async {
    await _localDb.clearHistory();
  }
}
