import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Cancel timer tool
class CancelTimerTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/system');

  @override
  String get name => 'cancel_timer';

  @override
  String get description => 'Отменить установленный таймер';

  @override
  List<ToolParam> get parameters => [];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    try {
      await _channel.invokeMethod('cancelTimer');
      return ToolResult.success('Таймер отменён');
    } catch (e) {
      return ToolResult.failure('Не удалось отменить таймер: $e');
    }
  }
}
