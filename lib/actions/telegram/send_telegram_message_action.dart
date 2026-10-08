import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:flutter/services.dart';

/// Telegram message sending via TDLib (Method Channel to native).
/// Requires one-time authorization with phone number + code.
class SendTelegramMessageAction extends BaseAction {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/telegram');

  @override
  String get id => 'send_telegram';

  @override
  String get description => 'send_telegram(contact, message) — отправить сообщение в Telegram';

  @override
  List<String> get requiredPermissions => [];

  @override
  Future<bool> canExecute() async {
    try {
      final authorized = await _channel.invokeMethod('isAuthorized');
      return authorized == true;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<ActionResult> execute(Map<String, dynamic> params) async {
    final contact = params['contact'] as String?;
    final message = params['message'] as String?;

    if (contact == null || contact.isEmpty) {
      return ActionResult.failed('Не указан контакт в Telegram.');
    }
    if (message == null || message.isEmpty) {
      return ActionResult.failed('Не указан текст сообщения.');
    }

    try {
      final result = await _channel.invokeMethod('sendMessage', {
        'contact': contact,
        'message': message,
      });

      if (result == true) {
        return ActionResult.success('Сообщение отправлено в Telegram.');
      }
      return ActionResult.failed('Контакт "$contact" не найден в Telegram.');
    } catch (e) {
      return ActionResult.failed('Не удалось отправить в Telegram. Проверь подключение.');
    }
  }
}
