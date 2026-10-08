import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:ai_voice_agent/agent/tools/tool_registry.dart';
import 'package:ai_voice_agent/agent/tools/base_tool.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/core/network/api_client.dart';
import 'package:ai_voice_agent/core/debug/debug_log_service.dart';

/// Agent response
class AgentResponse {
  final String message;
  final bool hasToolCalls;
  final List<ToolCall> toolCalls;

  AgentResponse({
    required this.message,
    this.hasToolCalls = false,
    this.toolCalls = const [],
  });
}

/// Tool call from AI
class ToolCall {
  final String id;
  final String name;
  final Map<String, dynamic> arguments;

  ToolCall({
    required this.id,
    required this.name,
    required this.arguments,
  });
}

/// Core agent with function calling
class VoiceAgent {
  final ToolRegistry _tools;
  static const _groqUrl = 'https://api.groq.com/openai/v1/chat/completions';
  static const _openaiUrl = 'https://api.openai.com/v1/chat/completions';
  
  /// Conversation history for multi-turn dialogs
  final List<Map<String, dynamic>> _conversationHistory = [];
  DateTime _lastInteractionTime = DateTime.now();
  static const _conversationTimeout = Duration(seconds: 60);

  VoiceAgent(this._tools);

  /// Process user message and execute tools if needed
  Future<String> process(String userMessage) async {
    final log = DebugLogService();
    log.voice('Agent', 'Команда: $userMessage');
    
    // Clear conversation if timed out
    if (_conversationHistory.isNotEmpty && 
        DateTime.now().difference(_lastInteractionTime) > _conversationTimeout) {
      log.info('Agent', 'Контекст устарел, очищаю');
      _conversationHistory.clear();
    }
    _lastInteractionTime = DateTime.now();
    
    // Build messages: system + history + new user message
    final messages = <Map<String, dynamic>>[
      {'role': 'system', 'content': _systemPrompt},
      ..._conversationHistory,
      {'role': 'user', 'content': userMessage},
    ];
    
    // Add user message to history
    _conversationHistory.add({'role': 'user', 'content': userMessage});

    // Agent loop - continue until no more tool calls
    int iterations = 0;
    const maxIterations = 5;

    while (iterations < maxIterations) {
      iterations++;
      log.info('Agent', 'Итерация $iterations');

      final response = await _callAI(messages);
      
      if (response.toolCalls.isEmpty) {
        // No tool calls - return final message
        log.success('Agent', 'Ответ: ${response.message}');
        // Save assistant response to history
        _conversationHistory.add({'role': 'assistant', 'content': response.message});
        return response.message;
      }

      // Add assistant message with tool calls
      final assistantMsg = {
        'role': 'assistant',
        'content': response.message.isEmpty ? null : response.message,
        'tool_calls': response.toolCalls.map((tc) => {
          'id': tc.id,
          'type': 'function',
          'function': {
            'name': tc.name,
            'arguments': jsonEncode(tc.arguments),
          },
        }).toList(),
      };
      messages.add(assistantMsg);
      _conversationHistory.add(assistantMsg);

      // Execute each tool and add results
      for (final toolCall in response.toolCalls) {
        log.tool('Tool', 'Вызов: ${toolCall.name}', details: jsonEncode(toolCall.arguments));
        final result = await _tools.execute(toolCall.name, toolCall.arguments);
        log.info('Tool', 'Результат: ${toolCall.name}', details: result.toJsonString());
        
        final toolMsg = {
          'role': 'tool',
          'tool_call_id': toolCall.id,
          'content': result.toJsonString(),
        };
        messages.add(toolMsg);
        _conversationHistory.add(toolMsg);
      }
    }

    log.warning('Agent', 'Превышен лимит итераций');
    final errorMsg = 'Слишком много шагов. Попробуй проще сформулировать.';
    _conversationHistory.add({'role': 'assistant', 'content': errorMsg});
    return errorMsg;
  }

  /// Clear conversation history (e.g., on explicit reset)
  void clearContext() {
    _conversationHistory.clear();
  }

  /// Whether the last response expects user input (is a question)
  bool get expectsResponse {
    if (_conversationHistory.isEmpty) return false;
    final last = _conversationHistory.last;
    if (last['role'] != 'assistant') return false;
    final content = last['content'] as String? ?? '';
    return content.contains('?');
  }

  /// Call AI API with function calling
  Future<AgentResponse> _callAI(List<Map<String, dynamic>> messages) async {
    final config = sl<AppConfig>();
    final log = DebugLogService();
    final tools = _tools.toFunctionSchemas();

    final body = <String, dynamic>{
      'temperature': 0.7,
      'max_tokens': 1024,
      'messages': messages,
    };

    if (tools.isNotEmpty) {
      body['tools'] = tools;
      body['tool_choice'] = 'auto';
    }

    // Determine primary and fallback provider based on active setting
    final isPrimaryGroq = config.activeProvider == 'groq';
    final primaryProvider = isPrimaryGroq ? 'Groq' : 'OpenAI';
    final fallbackProvider = isPrimaryGroq ? 'OpenAI' : 'Groq';

    try {
      final response = await _callProvider(
        primaryProvider, body, config, log,
      );
      return response;
    } catch (e) {
      log.error(primaryProvider, 'Ошибка API', details: e.toString());
      // Fallback to other provider
      try {
        log.warning(fallbackProvider, 'Fallback на $fallbackProvider API');
        final response = await _callProvider(
          fallbackProvider, body, config, log,
        );
        return response;
      } catch (e2) {
        log.error(fallbackProvider, 'Ошибка API', details: e2.toString());
        return AgentResponse(message: 'Ошибка соединения. Попробуй позже.');
      }
    }
  }

  /// Call specific provider
  Future<AgentResponse> _callProvider(
    String provider,
    Map<String, dynamic> body,
    AppConfig config,
    DebugLogService log,
  ) async {
    final client = sl<ApiClient>();
    
    if (provider == 'Groq') {
      body['model'] = 'llama-3.3-70b-versatile';
      log.api('Groq', 'Вызов Groq API', details: 'Ключ: ${config.groqApiKey.substring(0, 10)}...');
      final response = await client.post(
        _groqUrl,
        body: body,
        headers: {
          'Authorization': 'Bearer ${config.groqApiKey}',
          'Content-Type': 'application/json',
        },
      );
      log.success('Groq', 'Ответ получен');
      return _parseResponse(response);
    } else {
      body['model'] = 'gpt-4o-mini';
      log.api('OpenAI', 'Вызов OpenAI API', details: 'Ключ: ${config.openaiApiKey.substring(0, 10)}...');
      final response = await client.post(
        _openaiUrl,
        body: body,
        headers: {
          'Authorization': 'Bearer ${config.openaiApiKey}',
          'Content-Type': 'application/json',
        },
      );
      log.success('OpenAI', 'Ответ получен');
      return _parseResponse(response);
    }
  }

  /// Parse AI response
  AgentResponse _parseResponse(Map<String, dynamic> response) {
    final choice = response['choices'][0]['message'];
    final content = choice['content'] as String? ?? '';
    final toolCallsRaw = choice['tool_calls'] as List?;

    if (toolCallsRaw == null || toolCallsRaw.isEmpty) {
      return AgentResponse(message: content);
    }

    final toolCalls = <ToolCall>[];
    for (final tc in toolCallsRaw) {
      final func = tc['function'] as Map<String, dynamic>;
      final args = func['arguments'] as String? ?? '{}';
      
      toolCalls.add(ToolCall(
        id: tc['id'] as String,
        name: func['name'] as String,
        arguments: jsonDecode(args) as Map<String, dynamic>,
      ));
    }

    return AgentResponse(
      message: content,
      hasToolCalls: true,
      toolCalls: toolCalls,
    );
  }

  String get _systemPrompt {
    final agentName = sl<AppConfig>().agentName;
    return '''
Ты — голосовой AI-ассистент "$agentName" в телефоне пользователя.
Общайся кратко, по-дружески. Отвечай на русском.
Пользователь может обращаться к тебе по имени "$agentName".

Используй доступные инструменты для выполнения задач.
Если задача требует нескольких шагов — выполняй последовательно.
Если не знаешь как выполнить — честно скажи.

=== ЗВОНКИ И SMS — СТРОГОЕ ПОДТВЕРЖДЕНИЕ ===
НИКОГДА не звони и не отправляй SMS сразу! ВСЕГДА работай так:

1. ПОИСК: Сначала search_contacts с именем из запроса
2. ПРЕЗЕНТАЦИЯ: Скажи пользователю кого нашёл:
   "Нашёл Олю, номер +380...12...34. Звонить?"
3. ОЖИДАЙ ПОДТВЕРЖДЕНИЯ — не вызывай make_call/send_sms пока пользователь НЕ СКАЖЕТ "да"/"набирай"/"звонок"

4. Если пользователь говорит "НЕТ" / "Не она" / "Не тот":
   - Спроси: "Кого тогда искать?" или "Уточни имя/номер"
   - Пользователь может сказать: "У неё номер заканчивается на 345"
   - Снова search_contacts с уточнённым запросом
   - Снова презентуй и спроси подтверждение

5. Если найдено НЕСКОЛЬКО контактов с одинаковым именем:
   - Перечисли ВСЕХ: "Нашёл несколько Олень: 1) Оля Иванова +380...12  2) Оля Петрова +380...34. Кому звонить?"
   - Жди выбора пользователя

6. Только после явного "да" / "набирай" / "звонок" → вызывай make_call/send_sms

=== ГИБКИЙ ПОИСК КОНТАКТОВ ===
Пользователь может уточнять поиск разными способами:
- "Не та Оля, у неё номер короче" → ищи с другим запросом
- "У неё номер начинается с 050" → фильтруй результаты по началу номера
- "Мне нужна Оля которая работает в офисе" → используй контекст
- "Нет, поищи по фамилии" → search_contacts с фамилией

Будь УМНЫМ в поиске — анализируй что говорит пользователь и адаптируй запрос.

=== УПРАВЛЕНИЕ ВИДИМОСТЬЮ (self_awareness) ===
Ты сам решаешь когда появиться или скрыться. Анализируй НАМЕРЕНИЕ пользователя:

ПОЯВИТЬСЯ (action="show") — когда пользователь хочет тебя ВИДЕТЬ:
- Просит показать себя, появиться, выйти
- Спрашивает где ты, тут ли ты
- Хочет чтобы ты что-то показал на экране
- Просит открыть приложение, написать, создать
- Любое действие требующее визуального взаимодействия

ОСТАВАТЬСЯ В ФОНЕ (НЕ вызывай self_awareness) — когда достаточно ГОЛОСА:
- Приветствие, беседа, вопросы
- Краткие ответы (время, погода, курс)
- Простые действия не требующие экрана

СКРЫТЬСЯ (action="hide") — когда пользователь хочет чтобы ты ушёл:
- Просит скрыться, уйти, исчезнуть
- Говорит "пока", "до свидания"

ПРИВЕТСТВИЕ: Если пользователь просто сказал "$agentName" без команды — ответь приветствием и НЕ появляйся.

=== ПРОВЕРКА ДЕЙСТВИЙ ===
- Инструменты возвращают результат с проверкой (✓ = подтверждено)
- Если проверка не удалась — честно скажи что действие могло не выполниться
- НЕ говори "сделано" если инструмент вернул ошибку

=== ВРЕМЕННЫЕ ЗАДАЧИ ===
Для будильников/таймеров:
1. СНАЧАЛА get_current_time
2. Рассчитай время
3. Потом set_alarm/set_timer

=== ДОСТУПНЫЕ ВОЗМОЖНОСТИ ===
- Коммуникация: SMS, звонки, контакты
- Органайзер: будильники, таймеры, файлы
- Приложения: запуск, поиск, установка
- Система: WiFi, Bluetooth, медиа
- Информация: погода, валюты, поиск
- Сам: появляться/скрываться (self_awareness)

Будь точным. Говори правду о результатах.
''';
  }
}
