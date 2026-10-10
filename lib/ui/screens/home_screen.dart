import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/debug/debug_log_service.dart';
import 'package:ai_voice_agent/core/engine/kotlin_engine_service.dart';
import 'package:ai_voice_agent/ui/widgets/debug_log_panel.dart';
import 'package:ai_voice_agent/ui/widgets/klyopa_face.dart';
import 'package:ai_voice_agent/domain/repositories/i_history_repository.dart';
import 'package:ai_voice_agent/domain/entities/voice_command.dart';
import 'package:uuid/uuid.dart';

enum AgentState { idle, listening, processing, executing, speaking, announcing, cooldown, farewell }

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  AgentState _state = AgentState.idle;
  String _statusText = 'Готов к работе';
  final List<String> _recentCommands = [];

  final _kotlinEngine = KotlinEngineService();
  String? _lastText;
  bool _showDebug = false;
  int _emptyListenCount = 0; // Count consecutive empty/no-speech results
  bool _runningPipelineTest = false;
  String? _pipelineTestResult;
  bool _runningConversationTest = false;
  String? _conversationTestResult;
  static const int _maxEmptyListens =
      6; // Stop after 6 empty listens (~10 seconds of silence)
  String _activeProvider = 'openai'; // Track active model
  String? _currentTrack;
  String? _currentTrackPath;

  // Live dialogue display — shows on screen immediately
  final List<_DialogueEntry> _dialogueHistory = [];
  static const int _maxDialogueEntries = 8;

  // Continuous conversation mode
  bool _isContinuousMode = true;
  bool get isContinuousMode => _isContinuousMode;

  // Wake word (offline Vosk) — always enabled
  String _wakeWordName = 'Клёпа';

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat(reverse: true);
    _requestPermissions();

    // Load active provider from config
    final config = sl<AppConfig>();
    _activeProvider = config.activeProvider;

    // Setup pipeline status updates from Kotlin (только текст, состояние — через onPipelineStateChanged)
    _kotlinEngine.onStatusUpdate = (status) {
      if (!mounted) return;
      if (status.isNotEmpty) {
        setState(() {
          _statusText = status;
        });
      }
    };

    // Setup music update callback
    _kotlinEngine.onMusicUpdate = (trackName, trackPath) {
      if (!mounted) return;
      setState(() {
        if (trackName.isEmpty) {
          _currentTrack = null;
          _currentTrackPath = null;
        } else {
          _currentTrack = trackName;
          _currentTrackPath = trackPath;
        }
      });
    };

    // Setup continuous dialogue callback
    _kotlinEngine.onContinuousResult = (text, response) {
      if (!mounted) return;

      // Update UI with results
      if (text.isNotEmpty) {
        setState(() {
          _lastText = text;
          _recentCommands.insert(0, text);
          if (_recentCommands.length > 10) _recentCommands.removeLast();

          // Add to dialogue history
          _dialogueHistory.insert(
            0,
            _DialogueEntry(userText: text, agentResponse: response),
          );
          if (_dialogueHistory.length > _maxDialogueEntries) {
            _dialogueHistory.removeLast();
          }
        });
      }

      if (response.isNotEmpty) {
        setState(() {
          _lastAgentResponse = response;
          _statusText = 'Отвечаю...';
        });
      }

      // Save to history
      if (text.isNotEmpty && response.isNotEmpty) {
        _saveToHistory(text, response);
      }
    };

    // Setup farewell callback — агент попрощался
    _kotlinEngine.onAgentFarewell = () {
      if (!mounted) return;
      setState(() {
        _state = AgentState.farewell;
        _statusText = 'Пока!';
      });
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted) {
          _resetToIdle();
          _kotlinEngine.startContinuousSession();
        }
      });
    };

    // Setup barge-in callback — пользователь прервал TTS голосом
    // TTS уже остановлен на Kotlin side, тут только UI
    _kotlinEngine.onBargeIn = () {
      if (!mounted) return;
      // Визуально показываем что TTS прерван
      setState(() {
        _state = AgentState.listening;
        _statusText = 'Прервал...';
      });
    };

    // Setup pipeline state callback — синхронизация состояния с Kotlin
    _kotlinEngine.onPipelineStateChanged = (state) {
      if (!mounted) return;
      
      // CANCELLED/ERROR — сброс без setState внутри setState
      if (state == 'CANCELLED' || state == 'ERROR') {
        _resetToIdle();
        return;
      }
      
      setState(() {
        switch (state) {
          case 'IDLE':
            _state = AgentState.idle;
            _statusText = 'Готов к работе';
            break;
          case 'WAKE_WORD':
            _state = AgentState.idle;
            _statusText = 'Слушаю "$_wakeWordName" в фоне...';
            break;
          case 'LISTENING':
            _state = AgentState.listening;
            _statusText = 'Слушаю...';
            break;
          case 'STT_RUNNING':
            _state = AgentState.listening;
            _statusText = 'Распознаю...';
            break;
          case 'AGENT_RUNNING':
            _state = AgentState.processing;
            _statusText = 'Думаю...';
            break;
          case 'TOOL_EXECUTING':
            _state = AgentState.executing;
            _statusText = 'Выполняю...';
            break;
          case 'TTS_SPEAKING':
            _state = AgentState.speaking;
            _statusText = 'Отвечаю...';
            break;
        }
      });
    };

    // Load wake word name
    _loadWakeWordName();

    // Auto-start continuous session
    Future.delayed(const Duration(milliseconds: 500), () {
      if (mounted) {
        _kotlinEngine.startContinuousSession();
        setState(() {
          _statusText = 'Непрерывный режим: слушаю...';
        });
      }
    });
  }

  Future<void> _requestPermissions() async {
    final statuses = await [
      Permission.microphone,
      Permission.camera,
      Permission.contacts,
      Permission.sms,
      Permission.phone,
      Permission.location,
      Permission.audio,
      Permission.calendar,
      Permission.notification,
    ].request();

    if (statuses[Permission.microphone] != PermissionStatus.granted) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Для работы нужен доступ к микрофону')),
        );
      }
    } else {
      // Микрофон разрешён — уведомляем Kotlin для открытия AudioRecord
      _kotlinEngine.notifyMicPermissionGranted();
    }
  }

  /// Opens Accessibility Service settings with explanation dialog
  void _showAccessibilityDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.accessibility_new, color: Colors.blue, size: 28),
            SizedBox(width: 8),
            Text('Специальные возможности'),
          ],
        ),
        content: const SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '🔍 Что это?',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              SizedBox(height: 8),
              Text(
                'AccessibilityService — это "глаза" Клёпы. Без него агент не может:',
              ),
              SizedBox(height: 4),
              Text('• Читать что на экране'),
              Text('• Делать скриншоты'),
              Text('• Нажимать на кнопки'),
              Text('• Вводить текст'),
              Text('• Прокручивать страницы'),
              SizedBox(height: 12),
              Text(
                '🔐 Безопасность',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              SizedBox(height: 8),
              Text(
                'Это безопасно! Клёпа использует сервис только для помощи тебе. Все данные остаются на устройстве.',
              ),
              SizedBox(height: 12),
              Text(
                '📱 Как включить?',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              SizedBox(height: 8),
              Text(
                '1. Нажми "Открыть настройки"',
              ),
              Text(
                '2. Найди "AI Voice Agent" или "Клёпа"',
              ),
              Text(
                '3. Включи переключатель',
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Позже'),
          ),
          ElevatedButton.icon(
            onPressed: () {
              Navigator.pop(context);
              // Open Android Accessibility Settings
              const platform = MethodChannel('com.aiagent.ai_voice_agent/engine');
              platform.invokeMethod('openAccessibilitySettings');
            },
            icon: const Icon(Icons.settings_accessibility),
            label: const Text('Открыть настройки'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    // Stop continuous session if active
    if (_isContinuousMode) {
      _kotlinEngine.stopContinuousSession();
    }
    // Clear all callbacks to prevent memory leaks
    _kotlinEngine.onStatusUpdate = null;
    _kotlinEngine.onContinuousResult = null;
    _kotlinEngine.onAgentFarewell = null;
    _kotlinEngine.onBargeIn = null;
    _kotlinEngine.onPipelineStateChanged = null;
    _kotlinEngine.onMusicUpdate = null;
    super.dispose();
  }

  String _lastAgentResponse =
      ''; // Track last agent response for echo detection

  /// Simple voice pipeline — matches original Flutter logic:
  /// Record → STT → Agent → TTS → idle
  /// Only auto-relistens if agent asked a question.
  Future<void> _toggleListening({bool autoListen = false}) async {
    if (_state != AgentState.idle) return;
    final log = DebugLogService();

    // Поднимаем foreground service — Клёпа живёт в фоне, пока слушаем
    if (!autoListen) {
      try {
        await _kotlinEngine.startForegroundService();
      } catch (e) {
        log.warning('FgService', 'Не удалось поднять foreground',
            details: e.toString());
      }
    }

    setState(() {
      _state = AgentState.listening;
      _statusText = 'Слушаю...';
    });
    log.voice('Mic', 'Голосовая команда');

    try {
      // Check permissions
      final hasPerm = await Permission.microphone.isGranted;
      if (!hasPerm) {
        log.error('Mic', 'Нет разрешения на микрофон');
        _showError('Дай разрешение на микрофон в настройках');
        _resetToIdle();
        return;
      }

      // Kotlin pipeline: record → STT → Agent → TTS
      setState(() {
        _state = AgentState.processing;
        _statusText = 'Думаю...';
      });

      final result = await _kotlinEngine.processVoiceCommand();
      final text = result['text'] ?? '';
      final response = result['response'] ?? '';

      // Save agent response
      if (response.isNotEmpty) {
        _lastAgentResponse = response.toLowerCase().trim();
      }

      if (text.isNotEmpty) {
        log.success('STT', 'Распознано: $text');
        _lastText = text;
        setState(() {
          _recentCommands.insert(0, text);
          if (_recentCommands.length > 5) _recentCommands.removeLast();

          // Add to dialogue history (visible on screen immediately)
          _dialogueHistory.insert(
            0,
            _DialogueEntry(userText: text, agentResponse: response),
          );
          if (_dialogueHistory.length > _maxDialogueEntries) {
            _dialogueHistory.removeLast();
          }
        });
        await _saveToHistory(text, response);
      } else {
        // No speech detected or STT returned empty
        log.warning('Whisper', 'Не расслышал');
      }

      if (response.isNotEmpty && response != 'Не расслышал. Повтори.') {
        log.success('Agent', 'Ответ: $response');
      } else if (response.isNotEmpty) {
        log.warning('Agent', response);
      }

      // Проверка на прощание — если агент попрощался, не делаем auto-listen
      if (_isFarewell(response)) {
        log.info('Farewell', 'Агент попрощался, завершаем');
        _lastAgentResponse = response;
        setState(() {
          _state = AgentState.farewell;
          _statusText = 'Пока!';
        });
        await Future.delayed(const Duration(seconds: 2));
        if (mounted) _resetToIdle();
        return;
      }

      // Back to idle (like original Flutter)
      _resetToIdle();

      // Auto-listen only if agent asked a question (like original)
      if (text.isNotEmpty && _agentAsksQuestion(response)) {
        log.info('AutoListen', 'Агент ждёт ответ, авто-слушаю...');
        _emptyListenCount = 0;
        await Future.delayed(const Duration(milliseconds: 500));
        if (mounted && _state == AgentState.idle) {
          _toggleListening(autoListen: true);
        }
      } else if (text.isEmpty) {
        _emptyListenCount++;
        log.info('AutoListen', 'Пусто x$_emptyListenCount');
        if (_emptyListenCount < _maxEmptyListens &&
            _agentAsksQuestion(response)) {
          await Future.delayed(const Duration(milliseconds: 500));
          if (mounted && _state == AgentState.idle) {
            _toggleListening(autoListen: true);
          }
        }
      } else {
        _emptyListenCount = 0;
      }
    } catch (e) {
      log.error('Mic', 'Ошибка', details: e.toString());
      _showError('Произошла ошибка. Попробуй ещё раз');
      _resetToIdle();
    }
  }

  /// Check if agent's response contains a question (expects user answer)
  bool _agentAsksQuestion(String response) {
    if (response.isEmpty) return false;
    final questionMarkers = [
      '?',
      'что-нибудь ещё',
      'ещё что',
      'помочь',
      'расскажи',
      'хочешь',
    ];
    return response.contains('?') ||
        questionMarkers.any((m) => response.toLowerCase().contains(m));
  }

  /// Detect if recognized text is echo of agent's own speech (kept for future use)
  bool _isEcho(String recognizedText, String agentResponse) {
    if (agentResponse.isEmpty) return false;

    // Remove emojis for comparison
    final cleanText = recognizedText
        .replaceAll(
          RegExp(
            r'[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F1E0}-\u{1F1FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]',
            unicode: true,
          ),
          '',
        )
        .trim();
    final cleanResponse = agentResponse
        .replaceAll(
          RegExp(
            r'[\u{1F600}-\u{1F64F}\u{1F300}-\u{1F5FF}\u{1F680}-\u{1F6FF}\u{1F1E0}-\u{1F1FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]',
            unicode: true,
          ),
          '',
        )
        .trim();

    if (cleanText.isEmpty || cleanResponse.isEmpty) return false;

    // If recognized text is contained in agent response (or vice versa) — it's echo
    if (cleanResponse.contains(cleanText) ||
        cleanText.contains(cleanResponse)) {
      return true;
    }

    // Check word overlap — if >60% of words match, it's echo
    final textWords = cleanText
        .split(RegExp(r'\s+'))
        .where((w) => w.length > 3)
        .toSet();
    final responseWords = cleanResponse
        .split(RegExp(r'\s+'))
        .where((w) => w.length > 3)
        .toSet();
    if (textWords.isEmpty || responseWords.isEmpty) return false;

    final overlap = textWords.intersection(responseWords).length;
    final overlapRatio = overlap / textWords.length;
    return overlapRatio > 0.6;
  }

  /// Detect farewell/goodbye phrases
  bool _isFarewell(String text) {
    final lower = text.toLowerCase().trim();
    // Фразы — точное вхождение подстроки (безопасно для многословных фраз)
    final farewellPhrases = [
      'покеда',
      'до свидания',
      'досвидания',
      'прощай',
      'всё пока',
      'хватит',
      'стоп',
      'отбой',
      'закончим',
      'до встречи',
      'увидимся',
      'бай-бай',
      'goodbye',
      'выход',
      'отключись',
      'отстань',
    ];
    for (final phrase in farewellPhrases) {
      if (lower.contains(phrase)) return true;
    }
    // Слова, которые могут быть частью других слов — проверяем как целые слова
    final farewellWords = [
      'пока',
      'всё',
      'конец',
      'бай',
      'bye',
      'закрыть',
      'выключить',
    ];
    for (final word in farewellWords) {
      if (RegExp('\\b$word\\b').hasMatch(lower)) return true;
    }
    return false;
  }

  void _runPipelineTest() async {
    setState(() {
      _runningPipelineTest = true;
      _pipelineTestResult = null;
    });

    final result = await _kotlinEngine.runPipelineTest();

    setState(() {
      _runningPipelineTest = false;
      final states = result['states'] ?? '';
      final sttText = result['stt_text'] ?? '';
      final agentResp = result['agent_response'] ?? '';
      final tts = result['tts_triggered'] ?? '';
      final error = result['error'] ?? 'none';
      final finalState = result['final_state'] ?? '';

      _pipelineTestResult =
          'States: $states\n'
          'STT: $sttText\n'
          'Agent: ${agentResp.length > 50 ? '${agentResp.substring(0, 50)}...' : agentResp}\n'
          'TTS: $tts\n'
          'Final: $finalState\n'
          'Error: $error';
    });
  }

  void _runConversationTest() async {
    setState(() {
      _runningConversationTest = true;
      _conversationTestResult = null;
    });

    final result = await _kotlinEngine.runConversationTest();

    setState(() {
      _runningConversationTest = false;
      final status = result['status'] ?? 'ERROR';
      final passed = result['passed'] ?? '0';
      final failed = result['failed'] ?? '0';
      final total = result['total'] ?? '0';
      final latency = result['total_latency_ms'] ?? '0';
      final flowMap = result['flow_map'] ?? '';

      _conversationTestResult =
          '═══ $status ═══\n'
          'Passed: $passed / $total\n'
          'Failed: $failed\n'
          'Latency: ${latency}ms\n'
          '───────────────\n'
          '$flowMap';
    });
  }

  void _resetToIdle() {
    setState(() {
      _state = AgentState.idle;
      _statusText = 'Готов к работе';
    });
  }

  /// Save command to history
  Future<void> _saveToHistory(String text, String response) async {
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

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _loadWakeWordName() async {
    final state = await _kotlinEngine.getWakeWordState();
    if (!mounted) return;
    setState(() {
      _wakeWordName = state['name'] as String? ?? 'Клёпа';
    });
  }

  @override
  Widget build(BuildContext context) {
    final config = sl<AppConfig>();
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(config.agentName),
        actions: [
          IconButton(
            icon: const Icon(Icons.tune),
            tooltip: 'Инструменты',
            onPressed: () => Navigator.pushNamed(context, '/tools-inspector'),
          ),
          IconButton(
            icon: const Icon(Icons.build),
            tooltip: 'Tool Tester',
            onPressed: () => Navigator.pushNamed(context, '/tool-tester'),
          ),
          IconButton(
            icon: Icon(
              _showDebug ? Icons.bug_report : Icons.bug_report_outlined,
            ),
            color: _showDebug ? Colors.orange : null,
            onPressed: () => setState(() => _showDebug = !_showDebug),
          ),
          IconButton(
            icon: const Icon(Icons.auto_stories),
            tooltip: 'Журнал историй и рецептов',
            onPressed: () => Navigator.pushNamed(context, '/journal'),
          ),
          IconButton(
            icon: const Icon(Icons.history),
            onPressed: () => Navigator.pushNamed(context, '/history'),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => Navigator.pushNamed(context, '/settings'),
          ),
        ],
      ),
      body: Stack(
        children: [
          // Main content
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Klyopa animated face - klyopa_config.json canvas is 415x408,
                // the widget contain-scales, so keep the box on that ratio.
                SizedBox(
                  width: 320,
                  height: 314,
                  child: KlyopaFace(state: _getKlyopaState()),
                ),
                const SizedBox(height: 24),
                // Status text
                Text(_statusText, style: theme.textTheme.headlineSmall),
                // Active model indicator
                const SizedBox(height: 4),
                Text(
                  _activeProvider == 'groq'
                      ? '⚡ Groq (Llama 3.3)'
                      : '✨ OpenAI (GPT-4o-mini)',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.primary.withAlpha(180),
                  ),
                ),
                // Manual "Слушать" button — triggers listening without wake word
                if (_state == AgentState.idle)
                  ElevatedButton.icon(
                    onPressed: () {
                      _kotlinEngine.startManualListening();
                      setState(() {
                        _state = AgentState.listening;
                        _statusText = 'Слушаю...';
                      });
                    },
                    icon: const Icon(Icons.mic, size: 20),
                    label: const Text('Слушать'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green.withOpacity(0.2),
                      foregroundColor: Colors.greenAccent,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 24, vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(24),
                      ),
                    ),
                  ),
                if (_state == AgentState.idle)
                  const SizedBox(height: 12),
                // === TEST BUTTONS HIDDEN (infrastructure kept) ===
                if (_lastText != null) ...[
                  const SizedBox(height: 8),
                  Text(
                    '"$_lastText"',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurface.withAlpha(150),
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                // Live dialogue history — shows user + agent text immediately
                if (_dialogueHistory.isNotEmpty) ...[
                  Expanded(
                    child: ListView.builder(
                      reverse: true,
                      itemCount: _dialogueHistory.length,
                      itemBuilder: (context, index) {
                        final entry = _dialogueHistory[index];
                        return _DialogueBubble(entry: entry, theme: theme);
                      },
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
                // Now Playing card
                if (_currentTrack != null) _buildNowPlayingCard(theme),
                const SizedBox(height: 16),
              ],
            ),
          ),
          // Debug panel overlay
          if (_showDebug)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: MediaQuery.of(context).size.height * 0.5,
              child: const DebugLogPanel(),
            ),
        ],
      ),
    );
  }

  Widget _buildNowPlayingCard(ThemeData theme) {
    final fileName = _currentTrack ?? '';
    final displayName = fileName.endsWith('.mp3')
        ? fileName.substring(0, fileName.length - 4)
        : fileName;
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.green.withOpacity(0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.green.withOpacity(0.3), width: 1),
      ),
      child: Row(
        children: [
          const Icon(Icons.music_note, color: Colors.greenAccent, size: 20),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text(
                  'Сейчас играет',
                  style: TextStyle(fontSize: 10, color: Colors.grey),
                ),
                Text(
                  displayName,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          if (_currentTrackPath != null && _currentTrackPath!.isNotEmpty)
            GestureDetector(
              onTap: () {
                _kotlinEngine.executeTool('media_control', {
                  'action': 'seek_forward',
                  'seconds': '15',
                });
              },
              child: Container(
                padding: const EdgeInsets.all(6),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(
                  Icons.fast_forward,
                  size: 18,
                  color: Colors.white70,
                ),
              ),
            ),
          const SizedBox(width: 6),
          GestureDetector(
            onTap: () {
              _kotlinEngine.executeTool('media_control', {'action': 'stop'});
              setState(() => _currentTrack = null);
            },
            child: Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: Colors.red.withOpacity(0.2),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(Icons.stop, size: 18, color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );
  }

  Color _getStateColor(ThemeData theme) {
    switch (_state) {
      case AgentState.idle:
        return theme.colorScheme.primary;
      case AgentState.listening:
        return Colors.green;
      case AgentState.processing:
        return Colors.orange;
      case AgentState.executing:
        return Colors.blue;
      case AgentState.speaking:
        return Colors.purple;
      case AgentState.announcing:
        return Colors.cyan;
      case AgentState.cooldown:
        return Colors.grey;
      case AgentState.farewell:
        return Colors.teal;
    }
  }

  IconData _getStateIcon() {
    switch (_state) {
      case AgentState.idle:
        return Icons.mic_none;
      case AgentState.listening:
        return Icons.mic;
      case AgentState.processing:
        return Icons.psychology;
      case AgentState.executing:
        return Icons.play_arrow;
      case AgentState.speaking:
        return Icons.volume_up;
      case AgentState.announcing:
        return Icons.tips_and_updates;
      case AgentState.cooldown:
        return Icons.timer;
      case AgentState.farewell:
        return Icons.waving_hand;
    }
  }

  /// Map AgentState to KlyopaState for face animation
  KlyopaState _getKlyopaState() {
    switch (_state) {
      case AgentState.idle:
      case AgentState.cooldown:
        return KlyopaState.idle;
      case AgentState.listening:
        return KlyopaState.listening;
      case AgentState.processing:
      case AgentState.executing:
      case AgentState.announcing:
        return KlyopaState.thinking;
      case AgentState.speaking:
        return KlyopaState.talking;
      case AgentState.farewell:
        return KlyopaState.happy;
    }
  }
}

/// Entry in the live dialogue display
class _DialogueEntry {
  final String userText;
  final String agentResponse;
  final DateTime timestamp;

  _DialogueEntry({required this.userText, required this.agentResponse})
    : timestamp = DateTime.now();
}

/// Chat bubble widget — shows user text + agent response
class _DialogueBubble extends StatelessWidget {
  final _DialogueEntry entry;
  final ThemeData theme;

  const _DialogueBubble({required this.entry, required this.theme});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // User message (right-aligned)
          Align(
            alignment: Alignment.centerRight,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: theme.colorScheme.primary.withAlpha(40),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                entry.userText,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurface,
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          // Agent response (left-aligned)
          if (entry.agentResponse.isNotEmpty) ...[
            Align(
              alignment: Alignment.centerLeft,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: theme.colorScheme.secondary.withAlpha(30),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  entry.agentResponse,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurface.withAlpha(200),
                  ),
                ),
              ),
            ),
            // Кнопка копирования ответа в буфер обмена
            Align(
              alignment: Alignment.centerLeft,
              child: Padding(
                padding: const EdgeInsets.only(left: 4, top: 2),
                child: InkWell(
                  borderRadius: BorderRadius.circular(6),
                  onTap: () async {
                    await Clipboard.setData(
                      ClipboardData(text: entry.agentResponse),
                    );
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Скопировано в буфер обмена'),
                          duration: Duration(seconds: 2),
                          behavior: SnackBarBehavior.floating,
                        ),
                      );
                    }
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 2,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          Icons.copy_rounded,
                          size: 15,
                          color: theme.colorScheme.onSurface.withAlpha(150),
                        ),
                        const SizedBox(width: 4),
                        Text(
                          'Копировать',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurface.withAlpha(150),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
