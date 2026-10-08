# Техническое задание: AI Voice Agent (Archi)

## ⚠️ Известные проблемы (баги)

### 🔴 self_awareness — «Агент на экране» НЕ РАБОТАЕТ
- **Инструмент:** `self_awareness` (show/hide агента)
- **Где тестируется:** Вкладка **Ядро** → переключатель «Агент на экране»
- **Симптом:** При нажатии toggle агент не появляется/не исчезает
- **Что проверить:**
  1. Как `self_awareness` реализован в Kotlin (`EngineManager.kt` или отдельный файл)
  2. Какой `action` отправляется (`show` / `hide`) и что делает Kotlin-код
  3. Есть ли MethodChannel для управления видимостью агента из Kotlin
  4. Как Flutter-сторона реагирует на команду show/hide (Overlay? Visibility?)
- **Приоритет:** Вернуться после прохождения всех вкладок Tool Tester

---

## Статус проекта (что уже сделано)

Проект — голосовой AI-ассистент для Android на Flutter + Kotlin. 
Умеет: распознавать речь (Whisper), отвечать голосом (Edge TTS), выполнять 18 инструментов через AI (OpenAI/Groq function calling), фоновый wake word режим.

### Уже реализовано:
- [x] Push-to-talk (кнопка) + background wake word режим
- [x] 18 инструментов: SMS, звонки, контакты, будильник, таймер, файлы, погода, курсы, web search, Play Store, open app, WiFi, system control, media control, self-awareness
- [x] AI агент с function calling (OpenAI GPT-4o-mini + Groq Llama 3.3 70B)
- [x] Dual-provider routing (primary/fallback)
- [x] История диалога (60 сек контекст)
- [x] Авто-прослушивание после вопросов
- [x] Edge TTS (ru-RU-DmitryNeural) + MediaPlayer
- [x] 8 Kotlin MethodChannels (alarm, tts, contacts, apps, system, sms, phone, wifi)
- [x] Wake word через Android SpeechRecognizer (EventChannel)
- [x] Self-awareness (AI сам решает когда показаться/скрыться)
- [x] UI: Home, Settings, History, Tool Tester, Tools Inspector, API Keys, Permissions
- [x] Debug Log панель
- [x] Material 3 тёмная/светлая тема

---

## Оставшиеся шаги

### ШАГ 7: Intent Detector — локальная классификация команд

**Суть:** Добавить локальный классификатор, который для **простых команд** (включи фонарик, открой Telegram, какой час) отвечает мгновенно, без вызова LLM. Это даст:
- Ответ за 100-200 мс (вместо 1-3 сек через LLM)
- Экономию токенов/денег
- Работу без интернета для базовых команд

**Реализация:**

```
Пользователь → STT → Intent Detector
                            │
                    ┌───────┴───────┐
                    ▼               ▼
              Простая команда    Сложная команда
                    │               │
                    ▼               ▼
               Tool сразу       LLM + Planner
                    │               │
                    └───────┬───────┘
                            ▼
                         Ответ
```

**Файл:** `lib/core/intent/intent_detector.dart`

```
class IntentDetector {
  // Возвращает: intent name + parameters, или null если команда сложная
  
  /// Проверяет, можно ли обработать команду без LLM
  /// Возвращает IntentMatch(name, params) или null
  static IntentMatch? detect(String text) {
    // Серия проверок от простых к сложным:
    if (глагол+"фонарик" в тексте) → IntentMatch("system_control", {action: "toggle_flashlight"})
    if (глагол+"bluetooth" в тексте) → IntentMatch("system_control", {action: "toggle_bluetooth"})
    if (глагол+"wi-fi"|"вайфай" в тексте) → IntentMatch("system_control", {action: "toggle_wifi"})
    if ("громче" в тексте) → IntentMatch("system_control", {action: "volume_up"})
    if ("тише" в тексте) → IntentMatch("system_control", {action: "volume_down"})
    if ("время"|"который час" в тексте) → IntentMatch("get_time")
    if (глагол+"калькулятор"|"кальк" в тексте) → IntentMatch("open_app", {name: "калькулятор"})
    // ... ~30-50 наиболее частых команд
    
    return null // сложная команда → передать LLM
  }
}
```

**Требования:**
- Intent Detector ДО вызова LLM (в `voice_agent.dart` или отдельном usecase)
- Если IntentMatch найден → выполнить Tool напрямую, вернуть результат
- Если null → стандартный цикл LLM + function calling
- Регулярные выражения на русском, украинском, румынском
- Логирование: что распознано, сколько времени заняло

---

### ШАГ 8: Permission Manager — уровни безопасности

**Суть:** Каждый инструмент должен иметь уровень опасности, и AI должен запрашивать подтверждение перед опасными действиями.

**Уровни:**

| Уровень | Цвет | Примеры | Подтверждение |
|---------|------|---------|---------------|
| SAFE | 🟢 | время, погода, открыть приложение, поиск | Без подтверждения |
| NORMAL | 🟡 | позвонить, отправить SMS, включить WiFi | Одно подтверждение ("Подтвердите?") |
| DANGEROUS | 🟠 | удалить файл, отправить сообщение контакту | Подтверждение + озвучивание деталей |
| CRITICAL | 🔴 | сброс телефона, очистка памяти, банковские операции | Двойное подтверждение |

**Файл:** `lib/agent/security/permission_manager.dart`

```
enum ToolSecurityLevel { safe, normal, dangerous, critical }

class PermissionManager {
  /// Проверить: можно ли выполнить инструмент без подтверждения
  Future<PermissionResult> check(ToolCall call, VoiceAgent agent);
  
  /// Пользователь подтвердил действие
  Future<void> confirm(ToolCall call);
  
  /// Пользователь отклонил
  Future<void> deny(ToolCall call);
}
```

**Интеграция:**
1. Добавить `securityLevel` в `AgentTool` (по умолчанию `safe`)
2. В `VoiceAgent.process()` перед выполнением tool → проверить через `PermissionManager`
3. Если нужно подтверждение → AI спрашивает пользователя, ждёт ответ
4. Если "да" → выполнить. Если "нет" → пропустить, сообщить пользователю

**Файл:** `lib/agent/security/permission_manager.dart`
**Изменения:** `lib/agent/tools/base_tool.dart`, `lib/agent/voice_agent.dart`

---

### ШАГ 9: Execution Log — журнал выполнения задач

**Суть:** Детальный лог каждого действия: что запланировал AI, что выполнил, успех/ошибка. 
Отличается от DebugLogService тем, что это структурированный журнал для анализа, а не UI-панель.

**Формат записи:**

```
[12:15:02] Пользователь: "Отправь фото маме"
  ↓ План создан
  ↓ [12:15:03] Tool: search_contacts("мама") → найден: "Мама Иванова +380..."
  ↓ [12:15:04] AI: "Нашёл маму. Отправляю?"
  ↓ [12:15:05] Пользователь: "Да"
  ↓ [12:15:05] Tool: send_sms("+380...", "Фото...") → успех
  ↓ [12:15:06] Успех: SMS отправлено
```

**Файл:** `lib/agent/execution_log.dart`

```
ExecutionLog {
  + startSession()
  + logUserInput(text)
  + logPlanCreated(plan: List<Step>)
  + logToolCall(tool, params, result, durationMs)
  + logAiDecision(decision)
  + logError(error)
  + endSession(success)
  
  + getSessionHistory(): List<LogEntry>
  + exportToJson(): String
  + clear()
}
```

**Интеграция:**
- Вызывается на каждом шаге цикла `VoiceAgent.process()`
- Сохраняется в памяти (последние N сессий) + опционально в Hive
- UI экран для просмотра истории выполнения (не путать с History Screen команд)

---

### ШАГ 10: Memory System — 3 уровня памяти

**Суть:** Умная память, которая помнит пользователя между сессиями.

**Уровни:**

```
Memory
├── Session (до закрытия приложения)
│   - Текущий диалог
│   - Временный контекст ("сейчас мы работаем над приложением")
├── Short (последние N сообщений, до 24ч)
│   - Последние 20-50 команд
│   - Контекст последнего разговора
└── Long (постоянная)
    - Имя пользователя
    - Любимые приложения
    - Контакты
    - Дом/Работа адреса
    - Предпочтения
```

**Файл:** `lib/agent/memory/memory_manager.dart`

```
class MemoryManager {
  // Session память (in-memory, текущий диалог)
  session = SessionMemory()
  
  // Short-term (Hive, последние 50 команд)
  short = ShortTermMemory()
  
  // Long-term (Hive, предпочтения)
  long = LongTermMemory()
  
  // Получить контекст для LLM
  getContext(): String
  
  // Сохранить извлечённую информацию
  learn(key: String, value: String)
  
  // Очистить сессию
  clearSession()
}
```

**Long-term память — извлечение фактов:**
После каждого диалога AI анализирует и извлекает факты:
- "Меня зовут Сергей" → имя пользователя
- "Я живу на Левом берегу" → дом
- "Открой мой любимый Telegram" → любимое приложение

Факты хранятся в Hive и передаются в system prompt как контекст.

---

### ШАГ 11: Реорганизация инструментов в категории

**Суть:** Текущая структура `lib/agent/tools/impl/` с 18 файлами не масштабируется. Нужно разбить на категории.

**Новая структура:**

```
lib/agent/tools/
├── base_tool.dart
├── tool_registry.dart
├── all_tools.dart              # <- единый файл, регистрирующий все категории
└── categories/
    ├── core/                   # Часы, погода, курсы, поиск
    │   ├── all_tools.dart
    │   ├── get_time.dart
    │   ├── get_weather.dart
    │   ├── get_currency.dart
    │   └── web_search.dart
    ├── communication/          # Звонки, SMS, контакты
    │   ├── all_tools.dart
    │   ├── call.dart
    │   ├── sms.dart
    │   ├── contacts.dart
    │   └── telegram.dart       # NEW
    ├── apps/                   # Приложения
    │   ├── all_tools.dart
    │   ├── open.dart
    │   ├── close.dart          # NEW
    │   ├── list.dart           # NEW
    │   ├── install.dart        # NEW
    │   ├── uninstall.dart      # NEW
    │   └── market.dart
    ├── system/                 # Системные
    │   ├── all_tools.dart
    │   ├── wifi.dart
    │   ├── bluetooth.dart
    │   ├── flashlight.dart
    │   ├── volume.dart
    │   ├── brightness.dart
    │   ├── nfc.dart            # NEW
    │   ├── airplane.dart       # NEW
    │   └── vibration.dart      # NEW
    ├── media/                  # Камера, фото, музыка
    │   ├── all_tools.dart
    │   ├── camera.dart         # NEW
    │   ├── gallery.dart        # NEW
    │   ├── music.dart
    │   └── screenshot.dart     # NEW
    ├── files/                  # Файлы
    │   ├── all_tools.dart
    │   ├── create.dart
    │   ├── delete.dart         # NEW
    │   ├── copy.dart           # NEW
    │   ├── move.dart           # NEW
    │   ├── find.dart           # NEW
    │   ├── share.dart          # NEW
    │   ├── zip.dart            # NEW
    │   └── download.dart       # NEW
    ├── notifications/          # Уведомления (NEW)
    │   ├── all_tools.dart
    │   ├── read.dart
    │   ├── clear.dart
    │   └── open.dart
    ├── calendar/               # Календарь (NEW)
    │   ├── all_tools.dart
    │   ├── create_event.dart
    │   ├── show_schedule.dart
    │   └── delete_event.dart
    ├── geolocation/            # Геолокация (NEW)
    │   ├── all_tools.dart
    │   ├── get_location.dart
    │   ├── navigate.dart
    │   └── nearby_places.dart
    └── shell/                  # Shell команды (NEW) — только с root/ADB
        ├── all_tools.dart
        ├── am.dart
        ├── pm.dart
        ├── dumpsys.dart
        └── input.dart
```

**Файл категории** (пример `communication/all_tools.dart`):
```dart
List<AgentTool> getAllCommunicationTools(ApiClient client) => [
  CallTool(),
  SendSmsTool(),
  SearchContactsTool(),
  SendTelegramMessageTool(client),  // если telegram реализован
];

List<AgentToolParam> getCommunicationToolSchemas() =>
  getAllCommunicationTools().map((t) => t.toFunctionSchema()).toList();
```

**Регистрация** (`tool_registry.dart`):
```dart
void registerAll() {
  for (final tool in [
    ...getAllCoreTools(),
    ...getAllCommunicationTools(),
    ...getAllAppTools(),
    ...getAllSystemTools(),
    ...getAllMediaTools(),
    ...getAllFileTools(),
    ...getAllNotificationTools(),
    ...getAllCalendarTools(),
    ...getAllGeolocationTools(),
    ...getAllShellTools(),
  ]) {
    register(tool);
  }
}
```

---

### ШАГ 12-20: Добавление новых инструментов по категориям

Каждый шаг — одна категория из ШАГа 11.

#### Категория 12: Camera & Media
- `camera_take_photo` — сделать фото (Kotlin: Camera API)
- `camera_record_video` — записать видео
- `camera_switch_front_back` — переключить камеру
- `camera_scan_qr` — сканировать QR-код
- `gallery_find_photo` — найти фото по дате/месту
- `gallery_delete_photo` — удалить фото
- `screenshot_take` — сделать скриншот (Kotlin: MediaProjection API)
- `screen_record` — запись экрана

**Kotlin:** Новый `CameraHelper.kt`, `ScreenCaptureHelper.kt`
**Разрешения:** CAMERA, RECORD_AUDIO, WRITE_EXTERNAL_STORAGE

#### Категория 13: Notifications
- `notification_read` — читать уведомления (Kotlin: NotificationListenerService)
- `notification_clear` — удалить уведомление
- `notification_open` — открыть приложение из уведомления
- `notification_respond` — ответить на уведомление (если поддерживается)

**Kotlin:** `NotificationListenerHelper.kt` (сервис)
**AndroidManifest:** BIND_NOTIFICATION_LISTENER_SERVICE

#### Категория 14: Calendar & Alarms
- `calendar_create_event` — создать событие (Kotlin: ContentResolver + CalendarContract)
- `calendar_show_schedule` — показать расписание на сегодня
- `calendar_delete_event` — удалить событие
- `alarm_set` — ✅ уже есть
- `alarm_cancel` — отменить будильник (файл есть, Kotlin нет)
- `alarm_list` — показать все будильники
- `reminder_set` — установить напоминание
- `reminder_show` — показать напоминания

**Kotlin:** `CalendarHelper.kt`

#### Категория 15: Geolocation
- `location_get` — где я? (Kotlin: FusedLocationProviderClient)
- `location_coordinates` — точные координаты
- `location_navigate` — проложить маршрут (открыть Google Maps)
- `location_nearby` — ближайшие кафе/банки/аптеки (Google Places API)
- `location_share` — поделиться местоположением

**Разрешения:** ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION

#### Категория 16: Files (расширение)
- `file_find` — найти файл по имени/типу
- `file_delete` — удалить файл
- `file_copy` — копировать
- `file_move` — переместить
- `file_rename` — переименовать
- `file_share` — поделиться файлом
- `file_compress` — архивировать (zip)
- `file_extract` — распаковать (zip)
- `file_download` — скачать файл по URL

**Kotlin:** `FileHelper.kt`
**Разрешения:** READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE (Android 11+)

#### Категория 17: Internet & Browser
- `browser_open` — открыть сайт
- `browser_search` — поиск в Google
- `browser_youtube` — поиск на YouTube
- `browser_images` — поиск картинок
- `browser_news` — поиск новостей
- `web_download` — скачать файл из интернета

#### Категория 18: AI & Intelligence
- `ai_translate` — перевести текст
- `ai_summarize` — пересказать страницу/текст
- `ai_explain` — объяснить код/термин
- `ai_write` — написать письмо/сообщение по описанию
- `ai_solve` — решить задачу/пример

Эти инструменты вызывают LLM с отдельным промптом (не через VoiceAgent).
**Внимание:** Не создать рекурсию (агент вызывает AI инструмент, который снова вызывает агента).

#### Категория 19: Shell (ADB/root команды)
**Только для отладочных/rooted устройств или через ADB.**

- `shell_am` — `am start/force-stop/broadcast`
- `shell_pm` — `pm list packages/install/uninstall`
- `shell_dumpsys` — `dumpsys` информация
- `shell_input` — `input tap/swipe/keyevent` (эмуляция касаний)
- `shell_settings` — `settings put/get` (системные настройки)

**Kotlin:** `Runtime.getRuntime().exec(command)`

#### Категория 20: Multi-platform абстракция

**Суть:** Подготовить архитектуру для Windows Agent.

**Принцип:** LLM не знает про Android/Windows API. Он знает только абстрактные действия:
- `OpenApp("Telegram")`
- `SendMessage("+380...", "текст")`
- `FindFile("договор.pdf")`
- `TakePhoto()`

Платформенный слой (Android Tools / Windows Tools) сам решает КАК выполнить.

**Файл:** `lib/agent/tools/platform/abstract_tool.dart`

```dart
abstract class PlatformTool {
  String get name;
  String get description;
  List<ToolParam> get parameters;
  ToolSecurityLevel get securityLevel => ToolSecurityLevel.safe;
  
  Future<ToolResult> execute(Map<String, dynamic> params);
}
```

Для Windows:
- `OpenApp("Telegram")` → `Process.start("Telegram.exe")` или Shell: `start telegram:`
- `FindFile("договор.pdf")` → `Get-ChildItem -Recurse -Filter "*договор*.pdf"`
- `SendMessage("текст")` → эмуляция нажатий или Telegram API

---

### ШАГ 21: Telegram Agent (удалённый агент)

**Суть:** Сценарий: "Пишу себе в Telegram → Windows Agent слушает → находит файл → присылает обратно".

**Архитектура:**

```
Телефон (Android)
  │
  ├── Wake word → распознавание → VoiceAgent
  │
  └── "Арчи, пришли договор ООО Ромашка с компьютера"
        │
        ▼
  Котлин: Telegram Bot API → отправляет команду
        │
        ▼
  Windows Agent (отдельное приложение на ПК)
        │
        ├── Получает команду через Telegram Bot
        ├── Ищет файл (File Tool)
        ├── Отправляет файл обратно в Telegram
        │
        ▼
  Телефон: "Готово. Файл отправлен в Telegram."
```

**Компоненты:**
1. `TelegramBotService` — Dart/Kotlin служба, слушает Telegram Bot API
2. `WindowsAgent` — .NET или Python приложение на ПК с теми же абстрактными инструментами
3. `CrossPlatformMessaging` — протокол обмена командами через Telegram / WebSocket

---

### ШАГ 22: Multi-Agent архитектура (дальняя перспектива)

```
Пользователь
  │
  ▼
Planner Agent (строит план)
  │
  ├── File Agent (файлы)
  ├── Communication Agent (звонки, SMS, Telegram)
  ├── Vision Agent (камера, OCR)
  ├── Voice Agent (синтез, распознавание)
  └── Browser Agent (интернет)
```

Каждый агент специализируется на своей области. Planner разбивает задачу на подзадачи и распределяет между агентами.

---

## Приоритеты выполнения

| Приоритет | Шаг | Что | Зачем |
|-----------|-----|-----|-------|
| 🔴 High | 7 | Intent Detector | Скорость, экономия LLM |
| 🔴 High | 8 | Permission Manager | Безопасность, доверие |
| 🟡 Medium | 9 | Execution Log | Отладка, прозрачность |
| 🟡 Medium | 10 | Memory System | Контекст, персонализация |
| 🟡 Medium | 11 | Реорганизация инструментов | Масштабирование |
| 🟢 Low | 12-20 | Новые инструменты | Функциональность |
| 🔵 Future | 21-22 | Telegram Agent, Multi-Agent | Растяжение на платформы |

---

## Технические заметки

### Kotlin — новый инструмент (шаблон)
```kotlin
// helpers/NewToolHelper.kt
class NewToolHelper(private val context: Context) {
    fun doSomething(param: String): Map<String, Any> {
        return try {
            // Android API вызов
            mapOf("success" to true, "message" to "Готово")
        } catch (e: Exception) {
            mapOf("success" to false, "message" to e.message)
        }
    }
}
```

### MainActivity.kt — регистрация MethodChannel
```kotlin
private val NEW_CHANNEL = "com.aiagent.ai_voice_agent/new_tool"

// В configureFlutterEngine:
MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NEW_CHANNEL)
    .setMethodCallHandler { call, result ->
        when (call.method) {
            "doSomething" -> {
                val param = call.argument<String>("param") ?: ""
                result.success(newToolHelper.doSomething(param))
            }
            else -> result.notImplemented()
        }
    }
```

### Dart — новый инструмент (шаблон)
```dart
class NewTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/new_tool');
  
  @override
  String get name => 'new_tool';
  
  @override
  String get description => 'Описание нового инструмента';
  
  @override
  List<ToolParam> get parameters => [
    ToolParam(name: 'param', type: 'string', description: 'Параметр', required: true),
  ];
  
  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    try {
      final result = await _channel.invokeMethod('doSomething', params);
      return ToolResult.success('Готово');
    } catch (e) {
      return ToolResult.failure('Ошибка: $e');
    }
  }
}
```

### Добавление разрешения в AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.NEW_PERMISSION" />
```

### Security уровень в инструменте (для ШАГа 8)
```dart
@override
ToolSecurityLevel get securityLevel => ToolSecurityLevel.normal;
```

### Memory в system prompt (для ШАГа 10)
В system prompt добавляется блок:
```
=== КОНТЕКСТ О ПОЛЬЗОВАТЕЛЕ ===
Имя: Сергей
Дом: Левый берег
Любимые приложения: Telegram, YouTube
Последние команды: ...
==============================
```

---

## Соглашения по коду

1. **Новые файлы** — английские названия, snake_case
2. **Комментарии и UI строки** — русский
3. **Авто-регистрация** — если создаёшь новый tool, добавь его в `all_tools.dart` своей категории, а категорию в `tool_registry.registerAll()`
4. **Kotlin helpers** — каждый класс в отдельном файле в `helpers/`
5. **MethodChannel name** — `com.aiagent.ai_voice_agent/название`
6. **Возврат ошибок** — `ToolResult.failure('понятное описание')`
7. **Проверка разрешений** — всегда проверять `Permission.xxx.isGranted` перед вызовом Kotlin
8. **Асинхронность** — все Kotlin методы должны быть thread-safe (использовать runOnUiThread где нужно)
