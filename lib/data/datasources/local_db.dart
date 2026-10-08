import 'package:hive_flutter/hive_flutter.dart';

class LocalDb {
  late Box<Map> _historyBox;

  Future<void> init() async {
    await Hive.initFlutter();
    _historyBox = await Hive.openBox<Map>('command_history');
  }

  Future<void> addToHistory(Map<String, dynamic> command) async {
    await _historyBox.add(command);
  }

  List<Map<String, dynamic>> getHistory({int limit = 50}) {
    final values = _historyBox.values.toList();
    final sorted = values.reversed.take(limit).toList();
    return sorted.map((e) => Map<String, dynamic>.from(e)).toList();
  }

  Future<void> clearHistory() async {
    await _historyBox.clear();
  }

  Future<void> close() async {
    await _historyBox.close();
  }
}
