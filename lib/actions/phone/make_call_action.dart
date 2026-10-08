import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/services.dart';

class MakeCallAction extends BaseAction {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/phone');

  @override
  String get id => 'make_call';

  @override
  String get description => 'make_call(contact) — позвонить контакту';

  @override
  List<String> get requiredPermissions => ['phone'];

  @override
  Future<bool> canExecute() async {
    return await Permission.phone.isGranted;
  }

  @override
  Future<ActionResult> execute(Map<String, dynamic> params) async {
    final phone = params['phone'] as String?;
    if (phone == null || phone.isEmpty) {
      return ActionResult.failed('Не указан номер телефона.');
    }

    try {
      await _channel.invokeMethod('makeCall', {'phone': phone});
      return ActionResult.success('Звоню...');
    } catch (e) {
      return ActionResult.failed('Не удалось совершить вызов.');
    }
  }
}
