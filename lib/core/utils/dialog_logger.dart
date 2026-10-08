import 'dart:io';
import 'package:path_provider/path_provider.dart';

class DialogLogger {
  static Future<void> log(String userText, String aiResponse) async {
    try {
      final dir = await getApplicationDocumentsDirectory();
      final logFile = File('${dir.path}/dialog_log.txt');
      final timestamp = DateTime.now().toIso8601String();
      
      final entry = '''
=== $timestamp ===
USER: $userText
AI: $aiResponse
---

''';
      
      await logFile.writeAsString(entry, mode: FileMode.append);
    } catch (e) {
      // Silent fail - logging shouldn't break the app
    }
  }

  static Future<String> getLogPath() async {
    final dir = await getApplicationDocumentsDirectory();
    return '${dir.path}/dialog_log.txt';
  }

  static Future<void> clearLog() async {
    try {
      final dir = await getApplicationDocumentsDirectory();
      final logFile = File('${dir.path}/dialog_log.txt');
      if (await logFile.exists()) {
        await logFile.delete();
      }
    } catch (e) {
      // Silent fail
    }
  }
}
