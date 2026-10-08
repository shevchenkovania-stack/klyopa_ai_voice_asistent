# Полный анализ архитектуры AI Voice Agent

## 📊 Общая структура

**Гибридная архитектура:** Flutter UI + Kotlin AI Engine
- **Flutter** (58 файлов) — только UI, настройки, навигация
- **Kotlin** (30+ файлов) — вся AI логика: запись, STT, агент, TTS, инструменты

---

## 🔄 Поток данных (Voice Pipeline)

```
[Пользователь нажимает микрофон]
         ↓
[Flutter: HomeScreen._toggleListening()]
         ↓
[MethodChannel: "processVoiceCommand"]
         ↓
[Kotlin: EngineManager.processVoiceCommand()]
         ↓
    ┌─────────────────────────────────────┐
    │ 1. Запись аудио с VAD (10 сек макс) │
    │    - AudioRecord → PCM 16kHz        │
    │    - Energy-based VAD               │
    │    - Сохранение в WAV               │
    └─────────────────────────────────────┘
         ↓
    ┌─────────────────────────────────────┐
    │ 2. STT (Whisper API)                │
    │    - OpenAI Whisper endpoint        │
    │    - WAV → текст                    │
    └─────────────────────────────────────┘
         ↓
    ┌─────────────────────────────────────┐
    │ 3. Agent (VoiceAgent)               │
    │    - System prompt + история        │
    │    - LLM вызов (OpenAI/Groq)        │
    │    - Function calling loop (≤5 ит.) │
    │    - Выполнение инструментов        │
    └─────────────────────────────────────┘
         ↓
    ┌─────────────────────────────────────┐
    │ 4. TTS (Edge TTS)                   │
    │    - WebSocket → Microsoft Edge     │
    │    - Голос: ru-RU-DmitryNeural      │
    │    - MP3 → MediaPlayer              │
    └─────────────────────────────────────┘
         ↓
[Возврат в Flutter: text + response]
         ↓
[UI обновление + сохранение в историю]
```

---

## 🏗️ Компоненты

### **Flutter сторона**

#### 1. **main.dart** (10 строк)
```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initDependencies();  // DI контейнер
  runApp(const VoiceAgentApp());
}
```
- Инициализация GetIt (DI)
- Запуск приложения

#### 2. **HomeScreen** (430 строк) — `lib/ui/screens/home_screen.dart`
**Главный экран взаимодействия:**
- Кнопка микрофона → `_toggleListening()`
- Непрерывный диалог (`_continuousDialogue = true`)
- Детекция эха (агент не слышит свой голос)
- Детекция прощания ("пока", "до свидания")
- Счетчик тишины (3 раза → стоп)
- Debug log panel (опционально)

**Логика диалога:**
```dart
while (continuousDialogue) {
  1. Запись → STT → Agent → TTS
  2. Проверка на эхо
  3. Проверка на прощание
  4. Авто-слушание (если вопрос)
  5. Пауза 1.5 сек → повтор
}
```

#### 3. **KotlinEngineService** (125 строк) — `lib/core/engine/kotlin_engine_service.dart`
**Мост к Kotlin через MethodChannel:**
```dart
Future<Map<String, String>> processVoiceCommand() async {
  final result = await _channel.invokeMethod('processVoiceCommand');
  return {'text': text, 'response': response};
}
```
- `processVoiceCommand()` — полная голосовая команда
- `processText()` — текст → Agent → TTS
- `transcribe()` — только STT
- `syncConfig()` — синхронизация API ключей

#### 4. **DI контейнер** — `lib/core/di/injection.dart` (138 строк)
**GetIt регистрация:**
- Config, Storage, API clients
- Voice компоненты (TtsEngine, AudioRecorder, VadDetector)
- ToolRegistry (20+ Flutter tools)
- VoiceAgent (Flutter side)
- KotlinEngineService

**Примечание:** Flutter имеет свой ToolRegistry, но он **НЕ используется** для голосовых команд. Все инструменты теперь в Kotlin.

---

### **Kotlin сторона**

#### 1. **MainActivity** (646 строк) — `android/.../MainActivity.kt`
**Центральный хаб MethodChannel:**
- `engine` — AI движок (processVoiceCommand, processText, transcribe)
- `system` — управление приложением (startForeground, minimize, bringToFront)
- `alarm` — будильники
- `tts_audio` — воспроизведение
- `contacts` — поиск контактов
- `apps` — запуск приложений
- `sms` — SMS
- `phone` — звонки
- `wifi` — WiFi

**Инициализация:**
```kotlin
override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    EngineManager.initialize(this)  // AI движок
    // ... регистрация всех MethodChannel handlers
}
```

#### 2. **EngineManager** (367 строк) — `android/.../engine/EngineManager.kt`
**Singleton — центр управления AI:**
```kotlin
object EngineManager {
    lateinit var config: EngineConfig
    lateinit var toolRegistry: ToolRegistry
    lateinit var agent: VoiceAgent
    lateinit var tts: TtsEngine
    lateinit var stt: SttEngine
    
    fun initialize(context: Context) {
        // Создание всех компонентов
        // Регистрация 40+ инструментов
    }
}
```

**Главный pipeline:**
```kotlin
suspend fun processVoiceCommand(): Map<String, String> {
    // 1. Запись с VAD
    val audioFile = recordAudioWithVAD()
    
    // 2. STT (Whisper)
    val text = stt.transcribe(audioFile, apiKey, language, "openai")
    
    // 3. Agent (function calling)
    val response = agent.process(text)
    
    // 4. TTS (Edge)
    tts.speak(response)
    
    return mapOf("text" to text, "response" to response)
}
```

**VAD (Voice Activity Detection):**
```kotlin
private fun recordAudioWithVAD(): File? {
    // AudioRecord → PCM 16kHz
    // Энергия > 200 → речь обнаружена
    // Тишина 25 фреймов после речи → стоп
    // Макс 10 секунд
    // Сохранение в WAV
}
```

**Регистрация инструментов (40+):**
- **Оригинальные (5):** GetCurrentTime, SystemControl, OpenApp, SelfAwareness, WebSearch
- **Alarm/Timer (4):** SetAlarm, CancelAlarm, SetTimer, CancelTimer
- **Info (3):** GetLocation, GetWeather, GetCurrencyRate
- **Media/Play Store (3):** MediaControl, PlayStoreSearch, OpenPlayStore
- **WiFi/Files (3):** ConnectWifi, CreateFile, ReadFile
- **Accessibility (6):** ReadScreen, ClickElement, TypeText, Navigate, Scroll, ListClickable
- **Notifications (2):** ReadNotifications, DismissNotification
- **Camera (2):** TakeSelfie, TakePhoto
- **File System (6):** CreateFolder, ListFiles, DeleteFile, WriteFile, OpenFile, FileInfo
- **Device Info (3):** BatteryInfo, DeviceInfo, StorageInfo
- **Control (5):** ClipboardRead/Write, VolumeControl, Brightness, Flashlight
- **Communication (3):** LaunchUrl, ShareText, SendEmail
- **System (3):** ListApps, AppInfo, NetworkInfo
- **Drawing (1):** DrawImage
- **Download (1):** DownloadFile
- **Music (1):** SearchLocalMusic

#### 3. **VoiceAgent** (417 строк) — `android/.../engine/core/VoiceAgent.kt`
**AI агент с function calling:**

**Конфигурация:**
```kotlin
companion object {
    const val MAX_ITERATIONS = 5
    const val CONVERSATION_TIMEOUT_MS = 60_000L  // 1 минута
    const val MAX_HISTORY_MESSAGES = 20
    const val MAX_TOOL_RESULT_LENGTH = 500
}
```

**Agent loop:**
```kotlin
suspend fun process(userMessage: String): String {
    // 1. Проверка timeout (60 сек)
    if (timeout) conversationHistory.clear()
    
    // 2. Build messages: system + history + user
    val messages = JSONArray()
    messages.put(systemPrompt())
    conversationHistory.forEach { put(it) }
    messages.put(userMessage)
    
    // 3. Agent loop (≤5 итераций)
    var iterations = 0
    while (iterations < MAX_ITERATIONS) {
        val response = callAI(messages)
        
        if (!response.hasToolCalls) {
            // Финальный ответ
            conversationHistory.add(assistantMessage)
            return response.message
        }
        
        // Выполнение инструментов
        for (toolCall in response.toolCalls) {
            val result = toolRegistry.execute(toolCall.name, toolCall.arguments)
            // Добавление tool result в messages
        }
        
        iterations++
    }
    
    return "Слишком много шагов"
}
```

**LLM вызовы:**
```kotlin
private fun callAI(messages: JSONArray): AgentResponse {
    // Primary: OpenAI (gpt-4o-mini)
    // Fallback: Groq (llama-3.3-70b-versatile)
    
    val body = JSONObject()
    body.put("model", model)
    body.put("temperature", 0.7)
    body.put("max_tokens", 1024)
    body.put("messages", messages)
    body.put("tools", toolRegistry.toFunctionSchemas())
    
    // HTTP POST → OpenAI/Groq API
    // Parse response → AgentResponse
}
```

**System prompt (ключевые секции):**
- Роль: голосовой AI-ассистент "{agentName}"
- Звонки/SMS: строгое подтверждение (поиск → презентация → ожидание "да")
- Гибкий поиск контактов (анализ уточнений)
- Управление видимостью (self_awareness: show/hide)
- Проверка действий (✓ = подтверждено)
- Временные задачи (сначала get_current_time → рассчитай → set_alarm)

#### 4. **SttEngine** (109 строк) — `android/.../engine/stt/SttEngine.kt`
**Whisper API (Speech-to-Text):**
```kotlin
suspend fun transcribe(audioFile: File, apiKey: String, language: String, provider: String): String {
    // OpenAI Whisper endpoint
    // Multipart request: file + model + language
    // Response: plain text
}
```
- **Endpoint:** `https://api.openai.com/v1/audio/transcriptions`
- **Model:** `whisper-1`
- **Формат:** WAV (16kHz, mono, 16-bit)

#### 5. **TtsEngine** (318 строк) — `android/.../engine/tts/TtsEngine.kt`
**Edge TTS (WebSocket):**
```kotlin
suspend fun speak(text: String) {
    // 1. Генерация токенов (Sec-MS-GEC)
    // 2. WebSocket → Microsoft Edge
    // 3. Отправка SSML
    // 4. Получение MP3 chunks
    // 5. Воспроизведение через MediaPlayer
}
```
- **Голос:** `ru-RU-DmitryNeural` (мужской, естественный)
- **Протокол:** WebSocket → `wss://speech.platform.bing.com/...`
- **Формат:** `audio-24khz-48kbitrate-mono-mp3`
- **Таймаут:** 30 секунд

#### 6. **ToolRegistry** (45 строк) — `android/.../engine/core/ToolRegistry.kt`
**Реестр инструментов:**
```kotlin
class ToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()
    
    fun register(tool: AgentTool)
    fun execute(name: String, params: Map<String, Any?>): ToolResult
    fun toFunctionSchemas(): JSONArray  // Для OpenAI function calling
}
```

#### 7. **EngineConfig** (45 строк) — `android/.../engine/core/EngineConfig.kt`
**Конфигурация (SharedPreferences):**
```kotlin
class EngineConfig(context: Context) {
    var agentName: String = "Клёпа"
    var groqApiKey: String
    var openaiApiKey: String
    var activeProvider: String = "openai"  // по умолчанию OpenAI
    var language: String = "ru"
    var ttsVoice: String = "ru-RU-DmitryNeural"
    var ttsSpeed: Float = 1.0f
    var ttsPitch: Float = 1.0f
}
```

---

## 📦 Удаленные компоненты

**Полностью удалено:**
- ❌ `AgentForegroundService.kt` — foreground service
- ❌ `agent_background_service.dart` — background service
- ❌ `WakeWordListener.kt` — wake word detection (Kotlin)
- ❌ `wake_word_detector.dart` — wake word detection (Flutter)
- ❌ Background mode toggle в UI
- ❌ Wake word sensitivity в настройках

**Причина удаления:** Wake word pipeline терял команду (пользователь говорил команду во время wake word detection, но начиналась новая запись → тишина → "Не расслышал").

---

## 🔧 Технические детали

### **Audio запись**
- **Sample rate:** 16000 Hz
- **Channels:** Mono
- **Format:** PCM 16-bit
- **VAD threshold:** 200 (энергия)
- **Silence frames:** 25 (~1.25 сек)
- **Max duration:** 10 секунд
- **Format:** WAV (RIFF header)

### **STT (Whisper)**
- **Provider:** OpenAI (всегда, даже если activeProvider="groq")
- **Model:** `whisper-1`
- **Language:** из конфига (ru/en/ro)
- **Response format:** text (plain)
- **Timeout:** 60 сек

### **Agent (LLM)**
- **Primary:** OpenAI `gpt-4o-mini`
- **Fallback:** Groq `llama-3.3-70b-versatile`
- **Temperature:** 0.7
- **Max tokens:** 1024
- **Timeout:** 60 сек
- **History:** ≤20 сообщений
- **Tool results:** ≤500 символов

### **TTS (Edge)**
- **Voice:** `ru-RU-DmitryNeural` (Дмитрий, мужской)
- **SSML Pitch:** `+50Hz` (подростковый, выше обычного)
- **SSML Rate:** `+5%` (чуть быстрее)
- **Provider:** Edge TTS (Microsoft Bing Speech WebSocket API)
- **Protocol:** WebSocket → `wss://speech.platform.bing.com/...`
- **Format:** `audio-24khz-48kbitrate-mono-mp3`
- **Timeout:** 30 сек
- **Fallback:** НЕТ — при ошибке exception
- **Cache:** `tts_response.mp3` (удаляется после воспроизведения)

---

## 🎯 Ключевые особенности

### **1. Непрерывный диалог**
```dart
bool _continuousDialogue = true;
```
- Агент слушает пока пользователь не скажет "пока"
- Авто-слушание после вопроса
- Детекция эха (не слышит свой голос)
- Пауза 1.5 сек между командами

### **2. Детекция эха**
```dart
bool _isEcho(String recognizedText, String agentResponse) {
    // Удаление эмодзи
    // Проверка на вхождение подстроки
    // Проверка на overlap слов (>60% → эхо)
}
```

### **3. Детекция прощания**
```dart
bool _isFarewell(String text) {
    final farewellPhrases = [
        'пока', 'до свидания', 'хватит', 'стоп', 'отбой',
        'goodbye', 'bye', 'выход', 'закрыть', 'выключить'
    ];
}
```

### **4. Function calling loop**
```kotlin
while (iterations < MAX_ITERATIONS) {
    val response = callAI(messages)
    if (!response.hasToolCalls) return response.message
    
    for (toolCall in response.toolCalls) {
        val result = toolRegistry.execute(toolCall.name, toolCall.arguments)
        // Добавление результата в messages
    }
    iterations++
}
```

### **5. Обрезка истории**
```kotlin
private fun trimHistory() {
    if (conversationHistory.size > MAX_HISTORY_MESSAGES) {
        // Удаление старых сообщений (оставить последние 20)
    }
}
```

### **6. Обрезка результатов инструментов**
```kotlin
val truncatedResult = if (resultStr.length > MAX_TOOL_RESULT_LENGTH) {
    resultStr.take(MAX_TOOL_LENGTH) + "...[обрезано]"
} else {
    resultStr
}
```

---

## 📊 Статистика

**Flutter:**
- 58 Dart файлов
- ~8000 строк кода
- UI экраны: Home, Settings, History, ToolsInspector, ToolTester, ApiKeys, Permissions
- Debug log panel (опционально)

**Kotlin:**
- 30+ файлов
- ~5000 строк кода
- 40+ инструментов
- 3 AI компонента (STT, Agent, TTS)
- 6 сервисов (Accessibility, NotificationListener, Camera, etc.)

**Инструменты (40+):**
- Коммуникация: SMS, звонки, контакты
- Органайзер: будильники, таймеры, файлы
- Приложения: запуск, поиск, установка
- Система: WiFi, Bluetooth, медиа, буфер обмена
- Информация: погода, валюты, поиск, устройство
- Accessibility: чтение экрана, клики, ввод текста
- Camera: селфи, фото
- И другие...

---

## 🚀 Текущий режим работы

**Foreground-only mode:**
- Пользователь нажимает кнопку микрофона
- Агент записывает → распознает → отвечает
- Непрерывный диалог (авто-слушание)
- Нет фонового режима
- Нет wake word detection

**Потенциал:**
- 40+ инструментов готовы к использованию
- Function calling работает стабильно
- История диалога сохраняется
- Debug логи доступны

---

## 🔮 Будущие улучшения

1. **Wake word detection** (требует переделки)
   - Проблема: команда теряется между wake word и новой записью
   - Решение: записывать 5 сек аудио → Whisper → извлечь wake word + команду из того же аудио

2. **Background mode** (foreground service)
   - Использовать для постоянного слушания
   - Показать notification когда активен

3. **On-device wake word**
   - Без облака
   - Персонализированный (любое имя)
   - Низкое энергопотребление

4. **Оптимизация истории**
   - Суммаризация старых сообщений
   - Умная обрезка (не по количеству, а по контексту)

5. **Offline mode**
   - Локальный STT (Android SpeechRecognizer)
   - Локальный TTS (Android TTS)
   - Кэширование ответов

---

## 📝 Заключение

**Архитектура:** Чистая, модульная, расширяемая
**Код:** Хорошо структурирован, много логов
**Инструменты:** 40+ готовы к использованию
**AI:** Function calling работает стабильно
**TTS:** Естественный голос (Edge TTS)
**STT:** Точное распознавание (Whisper)

**Главное:** Все работает, но нет wake word и background mode. Код готов для добавления этих функций в будущем.
