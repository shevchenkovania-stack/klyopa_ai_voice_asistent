import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Control system settings tool (wifi, bluetooth, flashlight, volume, brightness)
class SystemControlTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/system');

  @override
  String get name => 'system_control';

  @override
  String get description => 'Управлять настройками телефона: WiFi, Bluetooth, фонарик, громкость, яркость. Поддерживаемые действия: toggle_wifi, toggle_bluetooth, toggle_flashlight, set_volume, set_brightness.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'action',
          type: 'string',
          description: 'Действие: toggle_wifi, toggle_bluetooth, toggle_flashlight, set_volume, set_brightness',
          required: true,
        ),
        ToolParam(
          name: 'value',
          type: 'integer',
          description: 'Значение для set_volume (0-100) или set_brightness (0-255). Не нужно для toggle.',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final action = params['action'] as String?;
    if (action == null) {
      return ToolResult.failure('Не указано действие');
    }

    try {
      final args = <String, dynamic>{'action': action};
      if (params.containsKey('value')) {
        args['value'] = params['value'];
      }

      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('systemControl', args);
      
      if (result == null) {
        return ToolResult.failure('Нет ответа от системы');
      }
      
      final success = result['success'] as bool? ?? false;
      final message = result['message'] as String? ?? '';
      
      if (success) {
        return ToolResult.success(message);
      } else {
        return ToolResult.failure(message);
      }
    } catch (e) {
      return ToolResult.failure('Ошибка управления системой: $e');
    }
  }
}
