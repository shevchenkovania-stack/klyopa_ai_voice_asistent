import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import '../base_tool.dart';

/// Read recent SMS messages from device
class ReadSmsTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/sms');

  @override
  String get name => 'read_sms';

  @override
  String get description => 'Прочитать последние SMS-сообщения с устройства. '
      'ЧТО ДЕЛАЕТ: Возвращает список последних входящих SMS с номером отправителя, текстом и датой. '
      'КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "прочитай смс", "какие сообщения", "есть ли новые смс", "кто писал". '
      'ПАРАМЕТРЫ: count (number, опц.) — количество сообщений (по умолчанию 10). '
      'ВОЗВРАЩАЕТ: Список SMS с номерами и текстом.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'count',
          type: 'number',
          description: 'Количество последних SMS для чтения (по умолчанию 10)',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final hasPermission = await Permission.sms.isGranted;
    if (!hasPermission) {
      return ToolResult.failure(
        'Нет разрешения на чтение SMS. '
        'Откройте Настройки телефона → Приложения → AI Voice Agent → Разрешения → SMS → Разрешить.'
      );
    }

    final count = params['count'] as int? ?? 10;

    try {
      final result = await _channel.invokeMethod('readSms', {
        'count': count,
      });

      if (result == null || (result as List).isEmpty) {
        return ToolResult.success(
          'SMS-сообщений не найдено. '
          'Если сообщения есть, но не читаются — возможно, Android ограничивает доступ. '
          'Попробуйте сделать AI Voice Agent SMS-приложением по умолчанию в настройках.'
        );
      }

      final messages = result as List;
      final buffer = StringBuffer();
      buffer.writeln('Найдено ${messages.length} SMS:');

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
      return ToolResult.failure('Ошибка чтения SMS: $e');
    }
  }
}
