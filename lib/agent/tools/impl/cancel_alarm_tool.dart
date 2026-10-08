import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Cancel alarm tool
class CancelAlarmTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/alarm');

  @override
  String get name => 'cancel_alarm';

  @override
  String get description => 'Отменить установленный будильник';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'alarm_id',
          type: 'integer',
          description: 'ID будильника для отмены (если известен)',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    try {
      await _channel.invokeMethod('cancelAlarm', {
        'alarm_id': params['alarm_id'] as int? ?? 0,
      });
      return ToolResult.success('Будильник отменён');
    } catch (e) {
      return ToolResult.failure('Не удалось отменить будильник: $e');
    }
  }
}
