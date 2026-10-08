import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Set timer tool
class SetTimerTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/system');

  @override
  String get name => 'set_timer';

  @override
  String get description => 'Установить таймер обратного отсчёта (например "на 5 минут", "на 30 секунд"). Таймер сработает в системном приложении Часы.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'seconds',
          type: 'integer',
          description: 'Длительность таймера в секундах',
          required: true,
        ),
        ToolParam(
          name: 'label',
          type: 'string',
          description: 'Название таймера (необязательно)',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final seconds = params['seconds'] as int?;
    if (seconds == null || seconds <= 0) {
      return ToolResult.failure('Не указана длительность таймера');
    }

    final label = params['label'] as String? ?? '';

    try {
      await _channel.invokeMethod('setTimer', {'seconds': seconds, 'label': label});
      final minutes = seconds ~/ 60;
      final secs = seconds % 60;
      final timeStr = minutes > 0 ? '$minutes мин $secs сек' : '$secs сек';
      return ToolResult.success('Таймер установлен на $timeStr');
    } catch (e) {
      return ToolResult.failure('Не удалось установить таймер: $e');
    }
  }
}
