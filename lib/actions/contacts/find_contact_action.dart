import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter_contacts/flutter_contacts.dart';

class FindContactAction extends BaseAction {
  @override
  String get id => 'find_contact';

  @override
  String get description => 'find_contact(name) — найти контакт по имени';

  @override
  List<String> get requiredPermissions => ['contacts'];

  @override
  Future<bool> canExecute() async {
    return await Permission.contacts.isGranted;
  }

  @override
  Future<ActionResult> execute(Map<String, dynamic> params) async {
    final name = params['name'] as String?;
    if (name == null || name.isEmpty) {
      return ActionResult.failed('Не указано имя контакта.');
    }

    try {
      final contacts = await FlutterContacts.getContacts(
        withProperties: true,
        withPhoto: false,
      );

      final query = name.toLowerCase();
      final matches = contacts.where((c) =>
          c.displayName.toLowerCase().contains(query)).toList();

      if (matches.isEmpty) {
        return ActionResult.failed('Контакт "$name" не найден.');
      }

      final found = matches.first;
      final phone = found.phones.isNotEmpty ? found.phones.first.number : null;

      return ActionResult.success(
        'Найден: ${found.displayName}${phone != null ? ", тел: $phone" : ""}',
        data: {
          'displayName': found.displayName,
          'phone': phone,
          'id': found.id,
        },
      );
    } catch (e) {
      return ActionResult.failed('Ошибка при поиске контакта.');
    }
  }
}
