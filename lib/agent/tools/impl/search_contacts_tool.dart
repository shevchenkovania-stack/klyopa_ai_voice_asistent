import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import '../base_tool.dart';

/// Search contacts by name tool
class SearchContactsTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/contacts');

  @override
  String get name => 'search_contacts';

  @override
  String get description => 'Найти контакт в телефонной книге по имени. Возвращает имя и номер телефона.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'query',
          type: 'string',
          description: 'Имя для поиска (например "Мама", "Иван", "Ева Директор")',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final hasPermission = await Permission.contacts.isGranted;
    if (!hasPermission) {
      return ToolResult.failure('Нет разрешения на доступ к контактам');
    }

    final query = params['query'] as String?;
    if (query == null || query.isEmpty) {
      return ToolResult.failure('Не указано имя для поиска');
    }

    try {
      final result = await _channel.invokeMethod('searchContacts', {'query': query});
      
      if (result == null || (result is List && result.isEmpty)) {
        return ToolResult.failure('Контакт "$query" не найден');
      }
      
      final contacts = List<Map<dynamic, dynamic>>.from(result as List);
      if (contacts.isEmpty) {
        return ToolResult.failure('Контакт "$query" не найден');
      }
      
      // Format results
      final formatted = contacts.map((c) {
        final name = c['name'] as String? ?? 'Неизвестно';
        final phone = c['phone'] as String? ?? 'Нет номера';
        return '$name: $phone';
      }).join('\n');
      
      return ToolResult.success(
        'Найдено контактов: ${contacts.length}\n$formatted',
        {
          'count': contacts.length,
          'contacts': contacts.map((c) => {
            'name': c['name'],
            'phone': c['phone'],
          }).toList(),
        },
      );
    } catch (e) {
      return ToolResult.failure('Ошибка поиска контактов: $e');
    }
  }
}
