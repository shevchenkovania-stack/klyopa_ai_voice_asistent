import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:flutter/services.dart';

class OpenAppAction extends BaseAction {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/apps');

  @override
  String get id => 'open_app';

  @override
  String get description => 'open_app(app_name) — открыть приложение';

  @override
  List<String> get requiredPermissions => [];

  @override
  Future<bool> canExecute() async => true;

  @override
  Future<ActionResult> execute(Map<String, dynamic> params) async {
    final appName = params['app_name'] as String?;
    if (appName == null || appName.isEmpty) {
      return ActionResult.failed('Не указано какое приложение открыть.');
    }

    try {
      final result = await _channel.invokeMethod('openApp', {'name': appName});
      if (result == true) {
        return ActionResult.success('Открываю $appName.');
      }
      return ActionResult.failed('Приложение "$appName" не найдено.');
    } catch (e) {
      return ActionResult.failed('Не удалось открыть приложение.');
    }
  }
}
