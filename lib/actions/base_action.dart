import 'package:ai_voice_agent/domain/entities/action_result.dart';

abstract class BaseAction {
  String get id;
  String get description;
  List<String> get requiredPermissions;

  Future<ActionResult> execute(Map<String, dynamic> params);
  Future<bool> canExecute();
}
