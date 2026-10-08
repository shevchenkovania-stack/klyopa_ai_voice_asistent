import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/debug/debug_log_service.dart';

/// Flutter-side wrapper for the native Kotlin AI Engine.
/// Calls the Kotlin engine via MethodChannel.
///
/// Pipeline: Audio → STT → Agent → TTS (all in Kotlin native)
class KotlinEngineService {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/engine');

  // Callback for continuous dialogue results
  void Function(String text, String response)? onContinuousResult;

  // Callback for pipeline status updates (настройка, слушаю, распознаю, думаю, отвечаю)
  void Function(String status)? onStatusUpdate;

  // Callback: агент попрощался (сессия завершена)
  void Function()? onAgentFarewell;

  // Callback: barge-in — пользователь прервал TTS голосом
  void Function()? onBargeIn;

  // Callback: состояние pipeline (IDLE, LISTENING, STT_RUNNING, etc.)
  void Function(String state)? onPipelineStateChanged;

  // Callback for music playback updates (сейчас играет, стоп, пауза)
  void Function(String trackName, String trackPath)? onMusicUpdate;

  // Callback: wake word detected (via onPipelineStateChanged)
  void Function(String detectedText)? onWakeWordDetected;

  KotlinEngineService() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onContinuousResult':
        final args = call.arguments as Map<dynamic, dynamic>;
        final text = args['text'] as String? ?? '';
        final response = args['response'] as String? ?? '';
        onContinuousResult?.call(text, response);
        break;
      case 'onStatusUpdate':
        final status = call.arguments as String? ?? '';
        onStatusUpdate?.call(status);
        break;
      case 'onAgentFarewell':
        onAgentFarewell?.call();
        break;
      case 'onBargeIn':
        onBargeIn?.call();
        break;
      case 'onPipelineStateChanged':
        final state = call.arguments as String? ?? 'IDLE';
        onPipelineStateChanged?.call(state);
        break;
      case 'onDebugLog':
        // Логи из Kotlin → Flutter debug panel
        final args = call.arguments as Map<dynamic, dynamic>;
        final tag = args['tag'] as String? ?? 'Kotlin';
        final level = args['level'] as String? ?? 'info';
        final message = args['message'] as String? ?? '';
        final details = args['details'] as String?;
        // MusicPlay: уведомляем UI о текущем треке
        if (tag == 'MusicPlay') {
          if (message.isEmpty) {
            onMusicUpdate?.call('', '');
          } else {
            final trackName = message.replaceFirst('Играет: ', '');
            onMusicUpdate?.call(trackName, details ?? '');
          }
        }
        _routeKotlinLog(tag, level, message, details);
        break;
    }
  }

  /// Map Kotlin log level to DebugLogService method
  void _routeKotlinLog(
    String tag,
    String level,
    String message,
    String? details,
  ) {
    final log = DebugLogService();
    // TTS-tagged logs always use TTS type for filtering
    if (tag == 'TTS') {
      switch (level) {
        case 'success':
          log.tts(tag, message, details: details);
          return;
        case 'warning':
          log.tts(tag, message, details: details);
          return;
        case 'error':
          log.tts(tag, message, details: details);
          return;
        default:
          log.tts(tag, message, details: details);
          return;
      }
    }
    switch (level) {
      case 'success':
        log.success(tag, message, details: details);
        break;
      case 'warning':
        log.warning(tag, message, details: details);
        break;
      case 'error':
        log.error(tag, message, details: details);
        break;
      case 'voice':
        log.voice(tag, message, details: details);
        break;
      default:
        log.info(tag, message, details: details);
    }
  }

  /// Process a voice command through the full Kotlin pipeline:
  /// Audio file → STT (Whisper) → Agent (Groq/OpenAI) → TTS (Edge)
  /// Returns the agent's text response.
  Future<String> processCommand(String audioPath) async {
    try {
      final response = await _channel.invokeMethod<String>('processCommand', {
        'audioPath': audioPath,
      });
      return response ?? '';
    } on PlatformException catch (e) {
      debugPrint('KotlinEngine.processCommand error: ${e.message}');
      return 'Ошибка движка: ${e.message ?? "неизвестная"}';
    } catch (e) {
      debugPrint('KotlinEngine.processCommand error: $e');
      return 'Ошибка соединения с движком';
    }
  }

  /// Execute a tool by name with params.
  Future<Map<String, dynamic>> executeTool(
    String toolName,
    Map<String, dynamic> params,
  ) async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'executeTool',
        {'toolName': toolName, 'params': params},
      );
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return {'success': false, 'message': 'Нет ответа'};
    } catch (e) {
      debugPrint('KotlinEngine.executeTool error: $e');
      return {'success': false, 'message': 'Ошибка: $e'};
    }
  }

  /// Transcribe audio file to text (STT only, no agent/TTS)
  Future<String> transcribe(String audioPath) async {
    try {
      final text = await _channel.invokeMethod<String>('transcribe', {
        'audioPath': audioPath,
      });
      return text ?? '';
    } on PlatformException catch (e) {
      debugPrint('KotlinEngine.transcribe error: ${e.message}');
      return '';
    } catch (e) {
      debugPrint('KotlinEngine.transcribe error: $e');
      return '';
    }
  }

  /// Process text directly (skip STT) — Agent → TTS
  Future<String> processText(String text) async {
    try {
      final response = await _channel.invokeMethod<String>('processText', {
        'text': text,
      });
      return response ?? '';
    } on PlatformException catch (e) {
      debugPrint('KotlinEngine.processText error: ${e.message}');
      return 'Ошибка движка: ${e.message ?? "неизвестная"}';
    } catch (e) {
      debugPrint('KotlinEngine.processText error: $e');
      return 'Ошибка соединения с движком';
    }
  }

  /// Full voice command: record (Kotlin) → STT → Agent → TTS
  /// Returns map with 'text' and 'response'.
  Future<Map<String, String>> processVoiceCommand() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'processVoiceCommand',
      );
      if (result != null) {
        return {
          'text': result['text'] as String? ?? '',
          'response': result['response'] as String? ?? '',
        };
      }
      return {'text': '', 'response': 'Ошибка движка'};
    } on PlatformException catch (e) {
      debugPrint('KotlinEngine.processVoiceCommand error: ${e.message}');
      return {'text': '', 'response': 'Ошибка: ${e.message ?? "неизвестная"}'};
    } catch (e) {
      debugPrint('KotlinEngine.processVoiceCommand error: $e');
      return {'text': '', 'response': 'Ошибка соединения'};
    }
  }

  /// Reload config from SharedPreferences
  Future<void> reloadConfig() async {
    try {
      await _channel.invokeMethod('reloadConfig');
    } catch (e) {
      debugPrint('KotlinEngine.reloadConfig error: $e');
    }
  }

  /// Sync config from Flutter (SecureStorage) to Kotlin (SharedPreferences)
  Future<void> syncConfig(AppConfig config) async {
    try {
      await _channel.invokeMethod('syncConfig', {
        'openaiApiKey': config.openaiApiKey,
        'groqApiKey': config.groqApiKey,
        'geminiApiKey': config.geminiApiKey,
        'activeProvider': config.activeProvider,
        'language': config.language,
        'ttsVoice': config.ttsVoice,
        'agentName': config.agentName,
      });
      debugPrint('Config synced to Kotlin');
    } catch (e) {
      debugPrint('KotlinEngine.syncConfig error: $e');
    }
  }

  /// Apply a preset (fast/current) — switches provider and TTS settings instantly.
  Future<Map<String, dynamic>?> applyPreset(String presetId) async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'applyPreset',
        {'presetId': presetId},
      );
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return null;
    } on PlatformException catch (e) {
      debugPrint('KotlinEngine.applyPreset error: ${e.message}');
      rethrow;
    } catch (e) {
      debugPrint('KotlinEngine.applyPreset error: $e');
      rethrow;
    }
  }

  /// Get list of registered tool names
  Future<List<String>> getTools() async {
    try {
      final tools = await _channel.invokeMethod<List<dynamic>>('getTools');
      return tools?.cast<String>() ?? [];
    } catch (e) {
      debugPrint('KotlinEngine.getTools error: $e');
      return [];
    }
  }

  /// Get detailed info for all registered tools (name, description, parameters).
  Future<List<Map<String, dynamic>>> getToolDetails() async {
    try {
      final details = await _channel.invokeMethod<List<dynamic>>(
        'getToolDetails',
      );
      if (details == null) return [];
      return details
          .whereType<Map>()
          .map((m) => Map<String, dynamic>.from(m))
          .toList();
    } catch (e) {
      debugPrint('KotlinEngine.getToolDetails error: $e');
      return [];
    }
  }

  /// Run dot-art drawing test (4 scenarios: flower, heart, smiley, abstract).
  /// Returns list of test results with 'scenario', 'passed', 'details' keys.
  Future<List<Map<String, dynamic>>> runDotArtTest() async {
    try {
      final results = await _channel.invokeMethod<List<dynamic>>(
        'runDotArtTest',
      );
      if (results == null) return [];
      return results
          .whereType<Map>()
          .map((m) => Map<String, dynamic>.from(m))
          .toList();
    } catch (e) {
      debugPrint('KotlinEngine.runDotArtTest error: $e');
      return [];
    }
  }

  // ==================== Story Library ====================

  /// Get saved stories from Kotlin engine (StoryTools).
  Future<List<Map<String, dynamic>>> getStories() async {
    try {
      final stories = await _channel.invokeMethod<List<dynamic>>('getStories');
      if (stories == null) return [];
      return stories
          .whereType<Map>()
          .map((m) => Map<String, dynamic>.from(m))
          .toList();
    } catch (e) {
      debugPrint('KotlinEngine.getStories error: $e');
      return [];
    }
  }

  /// Share story at [index] via Android share sheet.
  Future<void> shareStory(int index) async {
    await _channel.invokeMethod('shareStory', {'index': index});
  }

  /// Delete story at [index].
  Future<void> deleteStory(int index) async {
    await _channel.invokeMethod('deleteStory', {'index': index});
  }

  // ==================== Continuous Dialogue ====================

  /// Start continuous dialogue session.
  /// Mic stays active, VAD detects speech, barge-in enabled.
  /// Поднимает foreground service — Клёпа продолжает слушать в фоне.
  Future<void> startContinuousSession() async {
    try {
      // Сначала поднимаем foreground service (легальный микрофон в фоне + живучесть)
      await _channel.invokeMethod('startForegroundService');
      // startSession идемпотентен — повторный старт из сервиса безопасен
      await _channel.invokeMethod('startContinuousSession');
      debugPrint('Continuous session started (foreground)');
    } catch (e) {
      debugPrint('KotlinEngine.startContinuousSession error: $e');
    }
  }

  /// Stop continuous dialogue session.
  /// Останавливает сессию, но НЕ останавливает foreground service.
  /// Foreground service останавливается только явно через stopForegroundService().
  Future<void> stopContinuousSession() async {
    try {
      await _channel.invokeMethod('stopContinuousSession');
      // НЕ вызываем stopForegroundService здесь — сервис продолжает работать в фоне
      debugPrint('Continuous session stopped');
    } catch (e) {
      debugPrint('KotlinEngine.stopContinuousSession error: $e');
    }
  }

  /// Notify Kotlin that microphone permission was granted.
  /// Triggers AudioSessionManager.startWhenReady() to open AudioRecord.
  Future<void> notifyMicPermissionGranted() async {
    try {
      await _channel.invokeMethod('notifyMicPermissionGranted');
      debugPrint('Mic permission notified to Kotlin');
    } catch (e) {
      debugPrint('KotlinEngine.notifyMicPermissionGranted error: $e');
    }
  }

  /// Manual "Слушать" button — triggers listening without wake word.
  /// Sends WAKE_WORD_DETECTED event to SessionManager.
  Future<void> startManualListening() async {
    try {
      await _channel.invokeMethod('startManualListening');
      debugPrint('Manual listening started');
    } catch (e) {
      debugPrint('KotlinEngine.startManualListening error: $e');
    }
  }

  /// Cancel currently running pipeline (STT -> Agent -> TTS).
  /// Called from Flutter when user presses Stop button.
  Future<void> cancelPipeline() async {
    try {
      await _channel.invokeMethod('cancelPipeline');
      debugPrint('Pipeline cancelled');
    } catch (e) {
      debugPrint('KotlinEngine.cancelPipeline error: $e');
    }
  }

  /// Поднять foreground service (держит процесс живым в фоне).
  /// Используется при одиночном «Слушать» и при continuous-сессии.
  Future<void> startForegroundService() async {
    try {
      await _channel.invokeMethod('startForegroundService');
      debugPrint('Foreground service started');
    } catch (e) {
      debugPrint('KotlinEngine.startForegroundService error: $e');
    }
  }

  /// Остановить foreground service и убрать уведомление.
  Future<void> stopForegroundService() async {
    try {
      await _channel.invokeMethod('stopForegroundService');
      debugPrint('Foreground service stopped');
    } catch (e) {
      debugPrint('KotlinEngine.stopForegroundService error: $e');
    }
  }

  /// Stop TTS playback immediately (barge-in / manual stop).
  Future<void> stopTts() async {
    try {
      await _channel.invokeMethod('stopTts');
      debugPrint('TTS stopped');
    } catch (e) {
      debugPrint('KotlinEngine.stopTts error: $e');
    }
  }

  // ==================== Wake Word ====================

  /// Enable wake word — Vosk слушает в фоне.
  Future<void> enableWakeWord() async {
    try {
      await _channel.invokeMethod('enableWakeWord');
      debugPrint('Wake word enabled');
    } catch (e) {
      debugPrint('KotlinEngine.enableWakeWord error: $e');
    }
  }

  /// Disable wake word — остановить Vosk.
  Future<void> disableWakeWord() async {
    try {
      await _channel.invokeMethod('disableWakeWord');
      debugPrint('Wake word disabled');
    } catch (e) {
      debugPrint('KotlinEngine.disableWakeWord error: $e');
    }
  }

  /// Set wake word name (eg "Клёпа").
  Future<void> setWakeWordName(String name) async {
    try {
      await _channel.invokeMethod('setWakeWordName', {'name': name});
      debugPrint('Wake word name set: $name');
    } catch (e) {
      debugPrint('KotlinEngine.setWakeWordName error: $e');
    }
  }

  /// Get wake word state: enabled, name, isListening, isReady.
  Future<Map<String, dynamic>> getWakeWordState() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'getWakeWordState',
      );
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return {'enabled': false, 'name': 'клёпа', 'isListening': false, 'isReady': false};
    } catch (e) {
      debugPrint('KotlinEngine.getWakeWordState error: $e');
      return {'enabled': false, 'name': 'клёпа', 'isListening': false, 'isReady': false};
    }
  }

  /// Run pipeline integration test (WAV → STT → Agent → TTS).
  Future<Map<String, dynamic>> runPipelineTest() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'runPipelineTest',
      );
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return {'error': 'no result'};
    } catch (e) {
      debugPrint('KotlinEngine.runPipelineTest error: $e');
      return {'error': e.toString()};
    }
  }

  /// Run conversation flow test (multi-turn scenario).
  Future<Map<String, dynamic>> runConversationTest() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'runConversationTest',
      );
      if (result != null) {
        return Map<String, dynamic>.from(result);
      }
      return {'error': 'no result'};
    } catch (e) {
      debugPrint('KotlinEngine.runConversationTest error: $e');
      return {'error': e.toString()};
    }
  }
}
