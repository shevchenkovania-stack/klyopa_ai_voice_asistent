import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/voice/audio_recorder.dart';
import 'package:ai_voice_agent/voice/wake_word_detector.dart';
import 'package:ai_voice_agent/voice/tts_engine.dart';
import 'package:ai_voice_agent/agent/voice_agent.dart';
import 'package:ai_voice_agent/domain/repositories/i_speech_repository.dart';
import 'package:ai_voice_agent/domain/repositories/i_history_repository.dart';
import 'package:ai_voice_agent/domain/entities/voice_command.dart';
import 'package:uuid/uuid.dart';

enum ServiceState { idle, listening, processing, executing, speaking }

/// Background service that listens for wake word.
/// Uses Android SpeechRecognizer for continuous local wake word detection,
/// then Whisper for command transcription after wake word is triggered.
class AgentBackgroundService {
  static bool _isRunning = false;
  static bool _isProcessing = false;
  static final _channel = const MethodChannel('com.aiagent.ai_voice_agent/system');
  static StreamController<String>? _onResponse;
  static Function(ServiceState)? _onStateChanged;

  static ServiceState _state = ServiceState.idle;
  static ServiceState get state => _state;
  static set state(ServiceState value) {
    _state = value;
    _onStateChanged?.call(value);
  }

  static bool get isRunning => _isRunning;

  static Stream<String> get onResponse {
    _onResponse ??= StreamController<String>.broadcast();
    return _onResponse!.stream;
  }

  static void setOnStateChanged(Function(ServiceState) callback) {
    _onStateChanged = callback;
  }

  /// Start background listening
  static Future<void> start() async {
    if (_isRunning) return;

    _isRunning = true;
    state = ServiceState.idle;

    // Start foreground notification
    try {
      await _channel.invokeMethod('startForegroundService', {
        'title': sl<AppConfig>().agentName,
        'content': 'Слушаю "${sl<AppConfig>().agentName}"...',
        'wakeWord': sl<AppConfig>().agentName.toLowerCase(),
      });
    } catch (e) {
      debugPrint('Failed to start foreground service: $e');
    }

    // Start wake word detection
    _startWakeWordListening();

    debugPrint('Background service started');
  }

  /// Stop background listening
  static Future<void> stop() async {
    _isRunning = false;

    _stopWakeWordListening();

    try {
      await _channel.invokeMethod('stopForegroundService');
    } catch (e) {
      debugPrint('Failed to stop foreground service: $e');
    }

    debugPrint('Background service stopped');
  }

  /// Start wake word detection via Android SpeechRecognizer
  static void _startWakeWordListening() {
    final wakeWord = sl<WakeWordDetector>();
    wakeWord.onWakeWordDetected = _onWakeWordTriggered;
    wakeWord.startListening();
    state = ServiceState.listening;
  }

  /// Stop wake word detection
  static void _stopWakeWordListening() {
    sl<WakeWordDetector>().stopListening();
    state = ServiceState.idle;
  }

  /// Called when wake word is detected by SpeechRecognizer
  static Future<void> _onWakeWordTriggered() async {
    if (_isProcessing) return;
    _isProcessing = true;

    // Stop wake word listener while processing command
    sl<WakeWordDetector>().stopListening();

    debugPrint('Wake word detected, bringing UI to front...');
    state = ServiceState.listening;

    // Bring UI to front immediately so user sees what's happening
    try {
      await _channel.invokeMethod('bringToFront');
    } catch (e) {
      debugPrint('Failed to bring to front: $e');
    }

    // Small delay for UI to appear
    await Future.delayed(const Duration(milliseconds: 300));

    // Record command audio
    final command = await _recordCommand();

    if (command != null && command.isNotEmpty) {
      await _processCommand(command);
    } else {
      // No command detected – restart wake word
      debugPrint('No command detected, restarting wake word');
      await sl<TtsEngine>().speak('Не расслышала. Скажите команду.');
    }

    // Restart wake word listening
    _isProcessing = false;
    if (_isRunning) {
      _startWakeWordListening();
      // Tell Kotlin foreground service to restart SpeechRecognizer
      try {
        await _channel.invokeMethod('restartWakeWord');
      } catch (e) {
        debugPrint('Failed to restart wake word: $e');
      }
    }
  }

  /// Record audio after wake word triggered
  static Future<String?> _recordCommand() async {
    try {
      final recorder = sl<AgentAudioRecorder>();

      await recorder.startRecording();

      // Record for 5 seconds
      await Future.delayed(const Duration(seconds: 5));

      final audioPath = await recorder.stopRecording();

      if (audioPath == null) return null;

      // Transcribe with Whisper
      state = ServiceState.processing;
      final text = await sl<ISpeechRepository>().transcribeAudio(
        audioPath,
        language: sl<AppConfig>().language,
      );

      if (text.isEmpty) return null;

      debugPrint('Command transcribed: "$text"');
      return text;
    } catch (e) {
      debugPrint('Record command error: $e');
      return null;
    }
  }

  /// Process command through VoiceAgent
  static Future<void> _processCommand(String command) async {
    state = ServiceState.executing;
    debugPrint('Processing command: $command');

    try {
      final response = await sl<VoiceAgent>().process(command);

      // Save to history
      await _saveToHistory(command, response);

      if (response.isNotEmpty) {
        state = ServiceState.speaking;
        await sl<TtsEngine>().speak(response);
        _onResponse?.add(response);
      }
    } catch (e) {
      debugPrint('Command processing error: $e');
    }
  }

  /// Save command to history
  static Future<void> _saveToHistory(String text, String response) async {
    try {
      final history = sl<IHistoryRepository>();
      final command = VoiceCommand(
        id: const Uuid().v4(),
        rawText: text,
        response: response,
      );
      await history.addCommand(command);
    } catch (e) {
      debugPrint('Failed to save to history: $e');
    }
  }
}
