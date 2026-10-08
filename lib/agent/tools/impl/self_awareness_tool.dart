import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Self-awareness tool - controls agent visibility
class SelfAwarenessTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/system');

  @override
  String get name => 'self_awareness';

  @override
  String get description => 'Управление видимостью агента. Используй когда нужно появиться (показаться) или скрыться.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'action',
          type: 'string',
          description: 'Действие: "show" — появиться, "hide" — скрыться',
          required: true,
          enumValues: ['show', 'hide'],
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final action = params['action'] as String?;
    if (action == null || action.isEmpty) {
      return ToolResult.failure('Не указано действие');
    }

    try {
      if (action == 'show') {
        await _channel.invokeMethod('bringToFront');
        return ToolResult.success('✓ Агент появился на экране');
      } else if (action == 'hide') {
        await _channel.invokeMethod('minimizeApp');
        return ToolResult.success('✓ Агент скрылся в фон');
      } else {
        return ToolResult.failure('Неизвестное действие: $action');
      }
    } catch (e) {
      return ToolResult.failure('Не удалось выполнить действие: $e');
    }
  }
}
