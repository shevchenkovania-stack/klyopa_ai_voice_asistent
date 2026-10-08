import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import '../base_tool.dart';

/// Search SMS messages by query (text, sender, or date)
class SearchSmsTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/sms');

  @override
  String get name => 'search_sms';

  @override
  String get description => 'Поиск SMS-сообщений по тексту, номеру или дате. '
      'ЧТО ДЕЛАЕТ: Ищет SMS содержащие указанную строку в тексте или от определённого номера. '
      'КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "найди смс от Оли", "поищи сообщения с текстом привет", "найди смс за сегодня". '
      'ПАРАМЕТРЫ: query (string, обязательно) — текст поиска (номер, имя, или слово из сообщения). '
      'ВОЗВРАЩАЕТ: Список найденных SMS с номерами и текстом.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'query',
          type: 'string',
          description: 'Текст для поиска (номер телефона, имя, или слово из сообщения)',
          required: true,
        ),
        ToolParam(
          name: 'limit',
          type: 'number',
          description: 'Максимальное количество результатов (по умолчанию 20)',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final hasPermission = await Permission.sms.isGranted;
    if (!hasPermission) {
      return ToolResult.failure('Нет разрешения на чтение SMS. Разрешите в настройках.');
    }

    final query = params['query'] as String?;
    if (query == null || query.isEmpty) {
      return ToolResult.failure('Не указан текст для поиска');
    }

    final limit = params['limit'] as int? ?? 20;

    try {
      final result = await _channel.invokeMethod('searchSms', {
        'query': query,
        'limit': limit,
      });

      if (result == null || (result as List).isEmpty) {
        return ToolResult.success('SMS по запросу "$query" не найдено');
      }

      final messages = result as List;
      final buffer = StringBuffer();
      buffer.writeln('Найдено ${messages.length} SMS по запросу "$query":');

      for (var i = 0; i < messages.length; i++) {
        final msg = messages[i] as Map;
        final address = msg['address'] ?? 'Неизвестный';
        final body = msg['body'] ?? '';
        final date = msg['date'] ?? '';

        buffer.writeln('${i + 1}. $address');
        buffer.writeln('   Текст: ${body.length > 100 ? '${body.substring(0, 100)}...' : body}');
        buffer.writeln('   Время: $date');
        if (i < messages.length - 1) buffer.writeln();
      }

      return ToolResult.success(buffer.toString().trim());
    } catch (e) {
      return ToolResult.failure('Ошибка поиска SMS: $e');
    }
  }
}
