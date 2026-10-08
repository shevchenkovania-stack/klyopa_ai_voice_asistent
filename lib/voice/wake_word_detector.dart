import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/di/injection.dart';

/// Wake word listener via MethodChannel callback from Kotlin ForegroundService.
/// The Kotlin AgentForegroundService runs SpeechRecognizer in background
/// and calls Dart when wake word is detected.
class WakeWordDetector {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/wakeword');

  bool _isListening = false;
  String _wakeWord = '';

  /// Callback when wake word is detected
  void Function()? onWakeWordDetected;

  /// Callback for partial text (for UI/debug)
  void Function(String)? onPartialText;

  void initialize() {
    final config = sl<AppConfig>();
    _wakeWord = config.agentName.toLowerCase();
    
    // Listen for wake word events from Kotlin foreground service
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onWakeWordDetected') {
        debugPrint('WakeWordDetector: received onWakeWordDetected from Kotlin');
        onWakeWordDetected?.call();
      }
    });
  }

  void updateWakeWord(String newWord) {
    _wakeWord = newWord.toLowerCase();
  }

  /// Start wake word listening (Kotlin foreground service handles SpeechRecognizer)
  Future<void> startListening() async {
    _isListening = true;
    debugPrint('WakeWordDetector: listening for "$_wakeWord" via foreground service');
  }

  /// Stop wake word listening (to free mic for app recording)
  Future<void> stopListening() async {
    _isListening = false;
    try {
      await _channel.invokeMethod('stopWakeWord');
      debugPrint('WakeWordDetector: stopped (mic freed)');
    } catch (e) {
      debugPrint('WakeWordDetector: stopListening error: $e');
    }
  }
  
  /// Restart wake word listening after app recording is done
  Future<void> restartListening() async {
    _isListening = true;
    try {
      await _channel.invokeMethod('restartWakeWord');
      debugPrint('WakeWordDetector: restarted');
    } catch (e) {
      debugPrint('WakeWordDetector: restartListening error: $e');
    }
  }

  bool get isListening => _isListening;
  String get wakeWord => _wakeWord;
}
