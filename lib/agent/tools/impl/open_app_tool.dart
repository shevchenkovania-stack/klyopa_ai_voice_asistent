import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Open/launch app by name tool
class OpenAppTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/apps');

  @override
  String get name => 'open_app';

  @override
  String get description => 'Запустить приложение на телефоне по названию. Если приложение не найдено, вернёт список похожих приложений для выбора.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'name',
          type: 'string',
          description: 'Название приложения для запуска (например "YouTube", "Telegram", "Роблокс", "заметки")',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final name = params['name'] as String?;
    if (name == null || name.isEmpty) {
      return ToolResult.failure('Не указано название приложения');
    }

    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('openApp', {'name': name});
      
      if (result == null) {
        return ToolResult.failure('Ошибка связи с устройством');
      }
      
      final success = result['success'] as bool? ?? false;
      
      if (success) {
        final appName = result['appName'] as String? ?? name;
        final verified = result['verified'] as bool? ?? false;
        final message = result['message'] as String?;
        
        if (verified) {
          return ToolResult.success('✓ Приложение "$appName" запущено и проверено (работает)');
        } else {
          // App was launched but verification failed
          return ToolResult.success(
            'Приложение "$appName" запущено${message != null ? " ($message)" : ""}. '
            'Проверка не подтвердила запуск — возможно, приложение ещё загружается.'
          );
        }
      } else {
        // App not found, return similar apps
        final similarApps = result['similarApps'] as List<dynamic>? ?? [];
        
        if (similarApps.isEmpty) {
          return ToolResult.failure('Приложение "$name" не найдено и похожих приложений нет');
        }
        
        final appsList = similarApps.map((a) => '- ${a['label']} (${a['package']})').join('\n');
        return ToolResult.failure(
          'Приложение "$name" не найдено. Доступные похожие приложения:\n$appsList\n\nВыбери подходящее и вызови open_app с точным названием.'
        );
      }
    } catch (e) {
      return ToolResult.failure('Не удалось запустить приложение: $e');
    }
  }
}
