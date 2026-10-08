import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import '../base_tool.dart';

/// Send SMS tool
class SendSmsTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/sms');

  @override
  String get name => 'send_sms';

  @override
  String get description => 'Отправить SMS-сообщение на номер телефона';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'phone',
          type: 'string',
          description: 'Номер телефона (например +380501234567)',
          required: true,
        ),
        ToolParam(
          name: 'message',
          type: 'string',
          description: 'Текст сообщения',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final hasPermission = await Permission.sms.isGranted;
    if (!hasPermission) {
      return ToolResult.failure('Нет разрешения на отправку SMS');
    }

    final phone = params['phone'] as String?;
    final message = params['message'] as String?;

    if (phone == null || phone.isEmpty) {
      return ToolResult.failure('Не указан номер телефона');
    }
    if (message == null || message.isEmpty) {
      return ToolResult.failure('Не указан текст сообщения');
    }

    try {
      await _channel.invokeMethod('sendSms', {
        'phone': phone,
        'message': message,
      });
      return ToolResult.success('SMS отправлено на $phone');
    } catch (e) {
      return ToolResult.failure('Не удалось отправить SMS: $e');
    }
  }
}
