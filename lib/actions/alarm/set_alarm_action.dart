import 'package:ai_voice_agent/actions/base_action.dart';
import 'package:ai_voice_agent/domain/entities/action_result.dart';
import 'package:android_alarm_manager_plus/android_alarm_manager_plus.dart';

class SetAlarmAction extends BaseAction {
  @override
  String get id => 'set_alarm';

  @override
  String get description => 'set_alarm(time) — установить будильник';

  @override
  List<String> get requiredPermissions => [];

  @override
  Future<bool> canExecute() async => true;

  @override
  Future<ActionResult> execute(Map<String, dynamic> params) async {
    final timeParam = params['time'];
    String? timeStr;
    
    if (timeParam is int) {
      // AI returned minutes from now
      final alarmTime = DateTime.now().add(Duration(minutes: timeParam));
      final hour = alarmTime.hour;
      final minute = alarmTime.minute;
      
      final alarmId = alarmTime.millisecondsSinceEpoch ~/ 1000;
      await AndroidAlarmManager.oneShotAt(
        alarmTime,
        alarmId,
        _alarmCallback,
        exact: true,
        wakeup: true,
      );
      
      final formattedTime = '${hour.toString().padLeft(2, '0')}:${minute.toString().padLeft(2, '0')}';
      return ActionResult.success('Будильник установлен на $formattedTime.');
    } else if (timeParam is String) {
      timeStr = timeParam;
    }
    
    if (timeStr == null || timeStr.isEmpty) {
      return ActionResult.failed('Не указано время будильника.');
    }

    try {
      final parts = timeStr.split(':');
      final hour = int.parse(parts[0]);
      final minute = parts.length > 1 ? int.parse(parts[1]) : 0;

      final now = DateTime.now();
      var alarmTime = DateTime(now.year, now.month, now.day, hour, minute);
      if (alarmTime.isBefore(now)) {
        alarmTime = alarmTime.add(const Duration(days: 1));
      }

      final alarmId = alarmTime.millisecondsSinceEpoch ~/ 1000;
      await AndroidAlarmManager.oneShotAt(
        alarmTime,
        alarmId,
        _alarmCallback,
        exact: true,
        wakeup: true,
      );

      final formattedTime =
          '${hour.toString().padLeft(2, '0')}:${minute.toString().padLeft(2, '0')}';
      return ActionResult.success('Будильник установлен на $formattedTime.');
    } catch (e) {
      return ActionResult.failed('Не удалось установить будильник.');
    }
  }

  @pragma('vm:entry-point')
  static void _alarmCallback() {
    // Will be handled by notification system
  }
}
