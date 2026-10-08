import '../base_tool.dart';
import 'package:flutter/services.dart';

/// Tool to open Google Play Store page for an app
class OpenPlayStoreTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/system');

  @override
  String get name => 'open_play_store';

  @override
  String get description => 'Открыть страницу приложения в Google Play Store для установки. Используй после поиска приложения.';

  @override
  List<ToolParam> get parameters => [
    ToolParam(
      name: 'package_name',
      type: 'string',
      description: 'Package name приложения (например, com.google.android.keep)',
      required: true,
    ),
  ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final packageName = params['package_name'] as String?;
    if (packageName == null || packageName.isEmpty) {
      return ToolResult.failure('Не указан package name приложения');
    }

    try {
      await _channel.invokeMethod('openPlayStore', {'packageName': packageName});
      return ToolResult.success('Открыта страница в Google Play для установки: $packageName');
    } catch (e) {
      return ToolResult.failure('Не удалось открыть Play Store: $e');
    }
  }
}
