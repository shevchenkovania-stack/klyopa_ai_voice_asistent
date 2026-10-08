# AI Voice Agent - Архитектура

## Полный Workflow агентной системы

### От голоса до исполнения

```
┌─────────────────────────────────────────────────────────────────────┐
│  ЭТАП 1: ЗАХВАТ ГОЛОСА                                            │
│                                                                     │
│  Foreground (кнопка)          Background (wake word)               │
│  ┌─────────────────┐          ┌─────────────────────────────┐     │
│  │ Нажал кнопку    │          │ Микрофон слушает 5 сек      │     │
│  │ ↓               │          │ ↓                           │     │
│  │ Запись аудио    │          │ VAD детектирует речь        │     │
│  │ ↓               │          │ ↓                           │     │
│  │ Отпустил        │          │ Whisper → транскрипция      │     │
│  └─────────────────┘          │ ↓                           │     │
│                               │ Проверка: "Клёпа" есть?     │     │
│                               │ ↓                           │     │
│                               │ ДА → извлечь команду        │     │
│                               │ НЕТ → игнорировать          │     │
│                               └─────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ЭТАП 2: ТРАНСКРИБАЦИЯ (Whisper API)                               │
│                                                                     │
│  Audio → OpenAI Whisper API → Текст                                 │
│  "Клёпа подключи меня к WiFi сети HomeNetwork"                     │
│                                                                     │
│  Поддерживаемые языки: ru, ro, en                                   │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ЭТАП 3: AI АНАЛИЗ (VoiceAgent)                                    │
│                                                                     │
│  Формирует messages:                                                │
│  [                                                                  │
│    {system: "Ты ассистент Клёпа... используй инструменты..."},     │
│    {user: "подключи меня к WiFi сети HomeNetwork"}                 │
│  ]                                                                  │
│  + JSON schemas всех 18 инструментов                                │
│                                                                     │
│  Отправляет в AI (OpenAI или Groq)                                  │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ЭТАП 4: AI ПРИНИМАЕТ РЕШЕНИЕ                                      │
│                                                                     │
│  AI видит:                                                          │
│  - Запрос: "подключи к WiFi сети HomeNetwork"                      │
│  - Доступные инструменты: system_control (toggle_wifi)             │
│  - Ограничение: toggle_wifi только вкл/выкл, не подключает         │
│                                                                     │
│  AI решает:                                                         │
│  1. Честно сказать что не может                                    │
│  2. Предложить альтернативу (включить WiFi + открыть настройки)    │
│                                                                     │
│  Ответ AI:                                                          │
│  "Я могу включить WiFi, но подключение к конкретной сети           │
│   делается через настройки телефона. Включить WiFi?"               │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ЭТАП 5: ВЫПОЛНЕНИЕ ИНСТРУМЕНТОВ                                   │
│                                                                     │
│  Если пользователь согласен:                                        │
│                                                                     │
│  Итерация 1:                                                        │
│    AI → tool_call: system_control(action="toggle_wifi")            │
│    Tool → "WiFi включен"                                           │
│                                                                     │
│  Итерация 2:                                                        │
│    AI → tool_call: open_app(name="Настройки")                      │
│    Tool → "Настройки открыты"                                      │
│                                                                     │
│  Итерация 3:                                                        │
│    AI → "WiFi включен. Открой настройки для выбора сети."          │
│    (нет tool_calls → цикл завершён)                                │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ЭТАП 6: ОТВЕТ ПОЛЬЗОВАТЕЛЮ                                        │
│                                                                     │
│  TTS (Text-to-Speech) → озвучивает ответ                           │
│  Edge TTS (ru-RU-DmitryNeural) → MediaPlayer → динамики            │
│                                                                     │
│  Сохранение в историю команд                                        │
│                                                                     │
│  Авто-прослушивание: если ответ содержит '?' →                      │
│  автоматически начинает слушать через 500мс                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Компоненты системы

### 1. Распознавание речи (Whisper)
- Записывает аудио через микрофон
- Отправляет в OpenAI Whisper API
- Получает текст на русском/румынском/английском
- VAD (Voice Activity Detection) определяет конец речи

### 2. AI агент (OpenAI / Groq)
- Получает текст от пользователя
- Видит список всех доступных инструментов (JSON схемы)
- **Сам решает** какой инструмент использовать и с какими параметрами
- Может вызывать несколько инструментов подряд (до 5 итераций)
- **Primary/Fallback routing**: если Groq падает → OpenAI, и наоборот
- **История диалога**: сохраняет `_conversationHistory` для multi-turn
  (агент помнит контекст предыдущих сообщений в рамках 60 сек)

### 3. Инструменты (Tools)
Каждый инструмент описывает себя через JSON схему:
- `name` — имя (например `open_app`)
- `description` — что делает (для AI)
- `parameters` — какие параметры нужны

### 4. Нативный Android код (Kotlin)
Выполняет реальные действия через MethodChannel:
- `openAppByName()` — ищет и запускает приложения
- `searchContacts()` — querying ContactsContract
- `setAlarm()` — AlarmClock.ACTION_SET_ALARM
- `sendSms()` — SmsManager.sendTextMessage
- `makeCall()` — Intent.ACTION_CALL (прямой звонок без подтверждения)
- `systemControl()` — WiFi, Bluetooth, громкость, яркость
- `connectWifi()` — подключение к WiFi сети (открытая/защищённая)
- `playAudio()` — MediaPlayer для TTS

---

## Список инструментов (18 штук)

| Инструмент | Описание | Параметры |
|------------|----------|-----------|
| `get_current_time` | Текущая дата и время | — |
| `get_weather` | Погода в городе | `city` |
| `get_currency_rate` | Курс валют | `from`, `to`, `amount` |
| `web_search` | Поиск в DuckDuckGo | `query` |
| `play_store_search` | Поиск приложений | `query` |
| `open_play_store` | Открыть в Google Play | `package_name` |
| `open_app` | Запустить приложение | `name` |
| `send_sms` | Отправить SMS | `phone`, `message` |
| `make_call` | Позвонить | `phone` |
| `search_contacts` | Найти контакт | `query` |
| `set_alarm` | Установить будильник | `hour`, `minute`, `label` |
| `set_timer` | Установить таймер | `seconds`, `label` |
| `create_file` | Создать файл | `filename`, `content` |
| `read_file` | Прочитать файл | `filename` |
| `system_control` | WiFi, Bluetooth, фонарик | `action`, `value` |
| `media_control` | Управление музыкой | `action` |
| `connect_wifi` | Подключиться к WiFi сети | `ssid`, `password` (опц.) |
| `self_awareness` | Появиться/скрыться | `action` (show/hide) |

---

## AI Provider Selection

### OpenAI (GPT-4o-mini)
- Основной провайдер по умолчанию
- Function calling с tools
- Быстрый и дешёвый

### Groq (Llama 3.3 70B Versatile)
- Альтернативный провайдер
- Открытый исходный код
- Fallback если OpenAI недоступен

### Переключение
В настройках можно выбрать активный провайдер:
- **OpenAI** → основной OpenAI, fallback Groq
- **Groq** → основной Groq, fallback OpenAI

---

## Что AI может и не может

### ✅ Может
| Действие | Инструмент | Как работает |
|----------|------------|--------------|
| Включить/выключить WiFi | `system_control` | `toggle_wifi` через WifiManager |
| Подключиться к WiFi сети | `connect_wifi` | Scan → open/secured → WifiNetworkSpecifier |
| Запустить приложение | `open_app` | Поиск по label/package name |
| Отправить SMS | `send_sms` | SmsManager.sendTextMessage |
| Позвонить | `make_call` | Intent.ACTION_CALL (прямой звонок) |
| Установить будильник | `set_alarm` | AlarmClock.ACTION_SET_ALARM |
| Узнать погоду | `get_weather` | wttr.in API |
| Поиск в интернете | `web_search` | DuckDuckGo API |
| Появиться на экране | `self_awareness` | MethodChannel bringToFront |

### ❌ Не может (ограничения Android)
| Действие | Почему |
|----------|--------|
| Закрыть приложение | Нет kill_process инструмента |
| Автоматически ввести пароль WiFi без разрешения | Безопасность Android (но connect_wifi работает) |
| Прочитать SMS | Требует READ_SMS разрешение |
| Изменить системные настройки | Требует WRITE_SETTINGS разрешение |

---

## Агентный цикл (voice_agent.dart)

```dart
// История диалога сохраняется между вызовами (60 сек таймаут)
final messages = [
  {system: _systemPrompt},
  ..._conversationHistory,  // ← предыдущие сообщения
  {user: userMessage},
];

while (iterations < 5) {
  response = await callAI(messages);  // AI думает
  
  if (response.toolCalls.isEmpty) {
    _conversationHistory.add(assistant: response.message);
    return response.message;  // Готово, отвечаем пользователю
  }
  
  for (toolCall in response.toolCalls) {
    result = await tools.execute(toolCall);  // Выполняем инструмент
    messages.add(result);  // Добавляем результат в контекст
    _conversationHistory.add(result);
  }
  // AI видит результат и решает что дальше
}
```

**Авто-прослушивание:** если ответ содержит `?`, HomeScreen автоматически
начинает слушать через 500мс (не нужно нажимать кнопку).

---

## Background Service (Wake Word)

```
┌─────────────────────────────────────────────────────────────────┐
│  AgentBackgroundService                                         │
│                                                                 │
│  1. Foreground notification "Слушаю..."                        │
│  2. Цикл каждые 1 секунду:                                      │
│     - Записать 5 сек аудио                                      │
│     - VAD: есть речь?                                           │
│     - Whisper → транскрипция                                    │
│     - Проверка wake word "Клёпа"                                │
│     - Если есть → извлечь команду → VoiceAgent.process()        │
│     - Если нет → игнорировать                                   │
│  3. TTS → озвучить ответ                                        │
│  4. Сохранить в историю                                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Self-Awareness (Управление видимостью)

AI сам решает когда появиться или скрыться:

### Появиться (action="show")
- Пользователь просит показать себя
- Спрашивает "где ты?"
- Хочет чтобы что-то показал на экране
- Просит открыть приложение

### Остаться в фоне (НЕ вызывать self_awareness)
- Приветствие, беседа
- Краткие ответы (время, погода)
- Простые действия без экрана

### Скрыться (action="hide")
- Пользователь просит скрыться
- Говорит "пока", "до свидания"

---

## Файловая структура

```
lib/
├── agent/
│   ├── tools/
│   │   ├── base_tool.dart              # Базовый класс инструмента
│   │   ├── tool_registry.dart          # Реестр инструментов
│   │   └── impl/                       # Реализации инструментов
│   │       ├── get_current_time_tool.dart
│   │       ├── get_weather_tool.dart
│   │       ├── get_currency_rate_tool.dart
│   │       ├── web_search_tool.dart
│   │       ├── play_store_search_tool.dart
│   │       ├── open_play_store_tool.dart
│   │       ├── open_app_tool.dart
│   │       ├── send_sms_tool.dart
│   │       ├── make_call_tool.dart
│   │       ├── search_contacts_tool.dart
│   │       ├── set_alarm_tool.dart
│   │       ├── set_timer_tool.dart
│   │       ├── create_file_tool.dart
│   │       ├── read_file_tool.dart
│   │       ├── system_control_tool.dart
│   │       ├── media_control_tool.dart
│   │       ├── self_awareness_tool.dart
│   │       └── connect_wifi_tool.dart
│   └── voice_agent.dart                # Главный агент (OpenAI/Groq + цикл + история)
├── voice/
│   ├── tts_engine.dart                 # Edge TTS (ru-RU-DmitryNeural)
│   ├── vad_detector.dart               # Voice Activity Detection
│   ├── wake_word_detector.dart         # Wake word detection
│   └── audio_recorder.dart             # Запись аудио
├── background/
│   └── agent_background_service.dart   # Background listening service
├── core/
│   ├── di/injection.dart               # Dependency Injection (get_it)
│   ├── config/app_config.dart          # Конфигурация (API keys, имя)
│   ├── network/api_client.dart         # HTTP клиент (Dio)
│   └── debug/debug_log_service.dart    # Логирование
├── domain/
│   ├── entities/voice_command.dart     # Сущность команды
│   ├── repositories/i_history_repository.dart
│   └── usecases/process_voice_input.dart
└── ui/
    ├── screens/
    │   ├── home_screen.dart            # Главный экран
    │   ├── settings_screen.dart        # Настройки (AI provider, имя)
    │   ├── history_screen.dart         # История команд
    │   ├── tool_tester_screen.dart     # Тестирование инструментов
    │   ├── tools_inspector_screen.dart # Просмотр инструментов
    │   ├── api_keys_screen.dart        # Управление API ключами
    │   └── permissions_screen.dart     # Разрешения
    └── widgets/
        └── debug_log_panel.dart        # Панель логов

android/app/src/main/kotlin/
└── MainActivity.kt                     # Нативный Android код
    ├── AlarmChannel                    # setAlarm, cancelAlarm
    ├── TTSChannel                      # playAudio, stopAudio
    ├── ContactsChannel                 # searchContacts
    ├── AppsChannel                     # openApp
    ├── SystemChannel                   # systemControl, mediaControl, setTimer
    ├── SMSChannel                      # sendSms
    ├── PhoneChannel                    # makeCall (ACTION_CALL)
    └── WiFiChannel                     # connectWifi
```

---

## Поиск приложений (openAppByName)

1. Точное совпадение по package name
2. Список известных пакетов (с русскими и английскими ключевыми словами)
3. Поиск по всем установленным приложениям:
   - Точное совпадение по label
   - Contains match
   - Fuzzy match (без пробелов и спецсимволов)
   - Поиск по package name

---

## Что НЕ хардкод

- **Какой инструмент вызвать** — решает AI
- **В каком порядке** — решает AI
- **Какие параметры** — решает AI
- **Цепочки действий** — AI строит динамически
- **Когда появиться/скрыться** — решает AI (self_awareness)

## Что хардкод

- **Список известных пакетов** (Roblox, YouTube и т.д.) — маппинг имён
- **Нативный код инструментов** — реализация Android API
- **JSON схемы инструментов** — описание для AI

---

## Примеры работы агента

### Простая команда
**Пользователь:** "Запусти Роблокс"

**AI думает:**
1. → `open_app(name="Роблокс")` 
2. → "Приложение запущено"

### Цепочка действий
**Пользователь:** "Найди номер мамы и отправь ей SMS что я опоздаю"

**AI думает:**
1. → `search_contacts(query="мама")` → получает номер +380...
2. → `send_sms(phone="+380...", message="Я опоздаю")` → отправляет
3. → "SMS отправлено маме на номер +380..."

### Временная логика
**Пользователь:** "Поставь будильник через 5 минут"

**AI думает:**
1. → `get_current_time()` → узнаёт что сейчас 14:30
2. → Рассчитывает: 14:30 + 5 мин = 14:35
3. → `set_alarm(hour=14, minute=35, label="Таймер")`
4. → "Будильник установлен на 14:35"

### Подключение к WiFi (новая возможность)
**Пользователь:** "Подключи меня к WiFi сети Nessa"

**AI думает:**
1. → `connect_wifi(ssid="Nessa")` 
2. → Tool сканирует сети, находит Nessa
3. → Если сеть открытая → подключается сразу
4. → Если сеть закрытая → "Сеть защищена паролем. Скажи пароль."
5. → Пользователь говорит пароль → `connect_wifi(ssid="Nessa", password="...")` 
6. → "Подключено к сети Nessa"

### Multi-turn диалог (подтверждение звонка)
**Пользователь:** "Позвони Оле"

**AI думает:**
1. → `search_contacts(query="Оля")` → находит контакт
2. → "Нашёл Олю, номер +380...12...34. Звонить?"
3. → TTS озвучивает → **авто-прослушивание** (ответ содержит '?')
4. → Пользователь: "Да" (агент помнит контекст!)
5. → `make_call(phone="+380...")` → прямой звонок

---

## История команд

Все команды сохраняются в `HistoryRepository`:
- `id` — UUID
- `rawText` — что сказал пользователь
- `response` — что ответил AI
- `timestamp` — когда

Сохранение происходит:
- В foreground (после нажатия кнопки)
- В background (после wake word)

---

## Debug Logs

Панель логов показывает:
- 🎤 Voice — распознавание речи
- 🧠 Agent — решения AI
- 🔧 Tool — вызовы инструментов
- 🌐 API — вызовы OpenAI/Groq
- ✅ Success — успешные операции
- ⚠️ Warning — предупреждения
- ❌ Error — ошибки

Кнопки:
- **Share** — поделиться логами
- **Copy** — скопировать
- **Clear** — очистить

---

## Tool Tester

Экран для тестирования всех 18 инструментов:
- **Run All** — запустить все тесты
- **Individual** — тестировать один инструмент
- **Share Results** — поделиться результатами

Показывает:
- ✅ PASSED — тест пройден
- ❌ FAILED — тест не пройден (с ошибкой)
- Время выполнения
- Результат или ошибка

---

## Tools Inspector

Экран для просмотра всех инструментов:
- Категории (apps, communication, system, files, media, info)
- Параметры каждого инструмента
- Decision flow (как AI выбирает)
- Fallback chains (что если основной не работает)
- Related tools (связанные инструменты)
