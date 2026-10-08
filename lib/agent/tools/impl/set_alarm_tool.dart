import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Set alarm tool - creates system alarm
class SetAlarmTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/alarm');

  @override
  String get name => 'set_alarm';

  @override
  String get description => 'Установить будильник на указанное время';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'hour',
          type: 'integer',
          description: 'Часы (0-23)',
          required: true,
        ),
        ToolParam(
          name: 'minute',
          type: 'integer',
          description: 'Минуты (0-59)',
          required: true,
        ),
        ToolParam(
          name: 'label',
          type: 'string',
          description: 'Название будильника',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final hour = params['hour'];
    final minute = params['minute'];
    final label = params['label'] as String? ?? 'Будильник';

    if (hour == null || minute == null) {
      return ToolResult.failure('Не указано время будильника');
    }

    final h = hour is int ? hour : int.tryParse(hour.toString()) ?? -1;
    final m = minute is int ? minute : int.tryParse(minute.toString()) ?? -1;

    if (h < 0 || h > 23 || m < 0 || m > 59) {
      return ToolResult.failure('Неверное время. Часы: 0-23, минуты: 0-59');
    }

    try {
      await _channel.invokeMethod('setAlarm', {
        'hour': h,
        'minute': m,
        'label': label,
      });
      
      final timeStr = '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}';
      return ToolResult.success('Будильник установлен на $timeStr');
    } catch (e) {
      return ToolResult.failure('Не удалось установить будильник: $e');
    }
  }
}
