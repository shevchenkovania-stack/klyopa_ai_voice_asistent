import 'package:flutter/foundation.dart';

/// Log entry types
enum LogType {
  info,
  success,
  warning,
  error,
  api,
  voice,
  tool,
}

/// Single log entry
class LogEntry {
  final DateTime timestamp;
  final LogType type;
  final String tag;
  final String message;
  final String? details;

  LogEntry({
    required this.type,
    required this.tag,
    required this.message,
    this.details,
  }) : timestamp = DateTime.now();

  String get timeStr =>
      '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}:${timestamp.second.toString().padLeft(2, '0')}';

  String get typeIcon {
    switch (type) {
      case LogType.info:
        return 'ℹ️';
      case LogType.success:
        return '✅';
      case LogType.warning:
        return '⚠️';
      case LogType.error:
        return '❌';
      case LogType.api:
        return '🌐';
      case LogType.voice:
        return '🎤';
      case LogType.tool:
        return '🔧';
    }
  }
}

/// Global debug log service
class DebugLogService extends ChangeNotifier {
  static final DebugLogService _instance = DebugLogService._internal();
  factory DebugLogService() => _instance;
  DebugLogService._internal();

  final List<LogEntry> _logs = [];
  static const int _maxLogs = 100;

  List<LogEntry> get logs => List.unmodifiable(_logs);

  void log(
    String tag,
    String message, {
    LogType type = LogType.info,
    String? details,
  }) {
    final entry = LogEntry(
      type: type,
      tag: tag,
      message: message,
      details: details,
    );
    _logs.insert(0, entry);
    if (_logs.length > _maxLogs) {
      _logs.removeLast();
    }
    // Also print to console
    debugPrint('[$tag] $message${details != null ? '\n$details' : ''}');
    notifyListeners();
  }

  void info(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.info, details: details);

  void success(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.success, details: details);

  void warning(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.warning, details: details);

  void error(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.error, details: details);

  void api(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.api, details: details);

  void voice(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.voice, details: details);

  void tool(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.tool, details: details);

  void tts(String tag, String message, {String? details}) =>
      log(tag, message, type: LogType.voice, details: details);

  void clear() {
    _logs.clear();
    notifyListeners();
  }
}
