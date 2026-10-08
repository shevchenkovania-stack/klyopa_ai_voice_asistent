import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:ai_voice_agent/domain/repositories/i_action_registry.dart';

class ActionRegistry implements IActionRegistry {
  final Map<String, BaseAction> _actions = {};

  @override
  void register(BaseAction action) {
    _actions[action.id] = action;
  }

  @override
  Future<ActionResult> execute(String actionId, Map<String, dynamic> params) async {
    final action = _actions[actionId];
    if (action == null) {
      return ActionResult.unsupported('Я пока не умею это делать.');
    }
    if (!await action.canExecute()) {
      return ActionResult.permissionDenied('Нужно разрешение для этого действия.');
    }
    return await action.execute(params);
  }

  @override
  bool canExecute(String actionId) => _actions.containsKey(actionId);

  @override
  List<String> getAvailableActions() => _actions.keys.toList();
}
