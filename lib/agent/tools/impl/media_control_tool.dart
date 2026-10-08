import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Media control tool (play/pause/next/prev/volume)
class MediaControlTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/system');

  @override
  String get name => 'media_control';

  @override
  String get description => 'Управлять воспроизведением музыки: play, pause, next, previous, volume_up, volume_down.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'action',
          type: 'string',
          description: 'Действие: play, pause, next, previous, volume_up, volume_down',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final action = params['action'] as String?;
    if (action == null) {
      return ToolResult.failure('Не указано действие');
    }

    try {
      await _channel.invokeMethod('mediaControl', {'action': action});
      
      final actionNames = {
        'play': 'Воспроизведение',
        'pause': 'Пауза',
        'next': 'Следующий трек',
        'previous': 'Предыдущий трек',
        'volume_up': 'Громкость увеличена',
        'volume_down': 'Громкость уменьшена',
      };
      
      return ToolResult.success(actionNames[action] ?? 'Действие "$action" выполнено');
    } catch (e) {
      return ToolResult.failure('Ошибка управления медиа: $e');
    }
  }
}
