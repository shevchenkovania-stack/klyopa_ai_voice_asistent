import '../base_tool.dart';

/// Get current date and time tool
class GetCurrentTimeTool extends AgentTool {
  @override
  String get name => 'get_current_time';

  @override
  String get description => 'Получить текущую дату и время на устройстве';

  @override
  List<ToolParam> get parameters => [];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final now = DateTime.now();
    
    final weekdays = ['понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота', 'воскресенье'];
    final months = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 
                    'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
    
    final weekday = weekdays[now.weekday - 1];
    final month = months[now.month - 1];
    
    final formatted = '$weekday, ${now.day} $month ${now.year}, ${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
    
    return ToolResult.success(
      'Текущее время: $formatted',
      {
        'timestamp': now.millisecondsSinceEpoch ~/ 1000,
        'hour': now.hour,
        'minute': now.minute,
        'day': now.day,
        'month': now.month,
        'year': now.year,
        'weekday': now.weekday,
      },
    );
  }
}
