import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:ai_voice_agent/actions/base_action.dart';

abstract class IActionRegistry {
  void register(BaseAction action);
  Future<ActionResult> execute(String actionId, Map<String, dynamic> params);
  bool canExecute(String actionId);
  List<String> getAvailableActions();
}
