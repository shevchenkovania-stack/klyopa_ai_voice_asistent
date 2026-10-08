import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import '../base_tool.dart';

/// Make phone call tool
class MakeCallTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/phone');

  @override
  String get name => 'make_call';

  @override
  String get description => 'Позвонить по номеру телефона';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'phone',
          type: 'string',
          description: 'Номер телефона для звонка',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final hasPermission = await Permission.phone.isGranted;
    if (!hasPermission) {
      return ToolResult.failure('Нет разрешения на звонки');
    }

    final phone = params['phone'] as String?;
    if (phone == null || phone.isEmpty) {
      return ToolResult.failure('Не указан номер телефона');
    }

    try {
      await _channel.invokeMethod('makeCall', {'phone': phone});
      return ToolResult.success('Звоню на $phone');
    } catch (e) {
      return ToolResult.failure('Не удалось позвонить: $e');
    }
  }
}
