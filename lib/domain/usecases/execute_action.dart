import 'package:ai_voice_agent/domain/repositories/i_action_registry.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';

class ExecuteAction {
  final IActionRegistry _actionRegistry;

  ExecuteAction(this._actionRegistry);

  Future<ActionResult> call(String actionId, Map<String, dynamic> params) async {
    return await _actionRegistry.execute(actionId, params);
  }

  bool canExecute(String actionId) => _actionRegistry.canExecute(actionId);

  List<String> getAvailableActions() => _actionRegistry.getAvailableActions();
}
