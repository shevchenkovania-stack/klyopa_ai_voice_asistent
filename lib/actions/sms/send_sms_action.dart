import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class SendSmsAction extends BaseAction {
  static const _channel = MethodChannel('sms');

  @override
  String get id => 'send_sms';

  @override
  String get description => 'Отправить SMS-сообщение контакту';

  @override
  List<String> get requiredPermissions => ['SEND_SMS'];

  @override
  Future<bool> canExecute() async {
    return await Permission.sms.isGranted;
  }

  @override
  Future<ActionResult> execute(Map<String, dynamic> params) async {
    try {
      final phone = params['phone'] as String?;
      final message = params['message'] as String?;

      if (phone == null || phone.isEmpty) {
        return ActionResult.failed('Не указан номер телефона.');
      }
      if (message == null || message.isEmpty) {
        return ActionResult.failed('Не указан текст сообщения.');
      }

      await _channel.invokeMethod('sendSms', {
        'phone': phone,
        'message': message,
      });
      return ActionResult.success('Сообщение отправлено');
    } on PlatformException catch (e) {
      return ActionResult.failed(e.message ?? 'Не удалось отправить сообщение');
    } on Exception {
      return ActionResult.failed('Не удалось отправить SMS. Попробуй позже.');
    }
  }
}
