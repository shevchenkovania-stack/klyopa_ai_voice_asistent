import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:flutter/services.dart';

/// Find a contact in Telegram by name.
class FindTelegramContactAction extends BaseAction {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/telegram');

  @override
  String get id => 'find_telegram_contact';

  @override
  String get description => 'find_telegram_contact(name) — найти контакт в Telegram';

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
    final name = params['name'] as String?;
    if (name == null || name.isEmpty) {
      return ActionResult.failed('Не указано имя контакта.');
    }

    try {
      final result = await _channel.invokeMethod('findContact', {'name': name});
      if (result != null) {
        return ActionResult.success(
          'Найден в Telegram: ${result['name']}',
          data: Map<String, dynamic>.from(result),
        );
      }
      return ActionResult.failed('Контакт "$name" не найден в Telegram.');
    } catch (e) {
      return ActionResult.failed('Ошибка поиска в Telegram.');
    }
  }
}
