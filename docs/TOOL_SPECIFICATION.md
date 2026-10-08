# СПЕЦИФИКАЦИЯ: Система инструментов Клёпы

> **Файл восстановления.** Если всё потеряется — по этому документу можно восстановить всю систему инструментов с нуля.

---

## 1. Архитектура (обзор)

```
┌─────────────────────────────────────────────────────────┐
│                    FLUTTER (UI)                          │
│                                                         │
│  HomeScreen → кнопка "Говори" → MethodChannel           │
│  ToolTesterScreen → тест инструментов → MethodChannel   │
│  SettingsDialog → настройки → SharedPreferences         │
└──────────────────────┬──────────────────────────────────┘
                       │ MethodChannel
                       │ "com.aiagent.ai_voice_agent/engine"
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                 KOTLIN (AI Engine)                       │
│                                                         │
│  MainActivity → EngineManager (singleton)               │
│                      ├── EngineConfig (SharedPreferences)│
│                      ├── ToolRegistry (63 инструмента)  │
│                      ├── VoiceAgent (агент + LLM)       │
│                      ├── AgentMemory (долгосрочная)     │
│                      ├── TtsEngine (Edge TTS)           │
│                      └── SttEngine (Whisper API)        │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Ключевые файлы

| Файл | Роль |
|------|------|
| `engine/core/AgentTool.kt` | Интерфейс `AgentTool` + `ToolParam` + `ToolResult` |
| `engine/core/ToolRegistry.kt` | Реестр инструментов (register, execute, executeWithRetry) |
| `engine/core/VoiceAgent.kt` | Агент: цикл вызовов LLM + выполнение tools |
| `engine/core/EngineConfig.kt` | Конфиг (API ключи, голос, язык) из SharedPreferences |
| `engine/core/AgentMemory.kt` | Долгосрочная память (remember_fact, forget_fact) |
| `engine/EngineManager.kt` | Singleton: инициализация + регистрация всех tools |
| `engine/tools/*.kt` | Реализации инструментов (19 файлов) |
| `MainActivity.kt` | MethodChannel handler (Flutter → Kotlin) |

---

## 3. Как создать новый инструмент

### Шаг 1: Создать файл инструмента

Путь: `android/app/src/main/kotlin/com/aiagent/ai_voice_agent/engine/tools/`

```kotlin
package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

class MyNewTool(private val context: Context) : AgentTool {
    
    // Обязательные поля:
    override val name = "my_tool"  // Имя для LLM (snake_case)
    override val description = "Описание что делает инструмент. " +
        "Используй когда пользователь просит ..."
    override val parameters = listOf(
        ToolParam("param1", "string", "Описание параметра", required = true),
        ToolParam("param2", "number", "Необязательный параметр", required = false)
    )
    
    // Главный метод:
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val param1 = params["param1"] as? String 
            ?: return ToolResult.failure("Не указан param1")
        val param2 = (params["param2"] as? Number)?.toInt() ?: 0
        
        return try {
            // ... логика инструмента ...
            ToolResult.success("✓ Успешно: результат")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка: ${e.message}")
        }
    }
}
```

### Шаг 2: Зарегистрировать в EngineManager

Файл: `engine/EngineManager.kt` → метод `registerTools()`

```kotlin
// Добавить в нужную секцию:
toolRegistry.register(MyNewTool(ctx))
```

### Шаг 3: Добавить в Tool Tester (для тестирования)

Файл: `lib/ui/screens/tool_tester_screen.dart` → `_testParams`

```dart
'my_tool': {'param1': 'тест', 'param2': 42},
```

### Шаг 4: Создать документацию

Путь: `docs/tools/` — создать или обновить .md файл

---

## 4. Интерфейс AgentTool

```kotlin
interface AgentTool {
    val name: String           // Имя для LLM (snake_case, англ.)
    val description: String    // Описание на русском (LLM читает его)
    val parameters: List<ToolParam>  // Параметры
    
    suspend fun execute(params: Map<String, Any?>): ToolResult  // Выполнение
    
    fun toFunctionSchema(): JSONObject  // Авто: конвертация в OpenAI schema
}
```

### ToolParam

```kotlin
data class ToolParam(
    val name: String,          // Имя параметра
    val type: String,          // "string", "number", "boolean", "object", "array"
    val description: String,   // Описание (на русском)
    val required: Boolean = false,  // Обязательный?
    val enumValues: List<String>? = null  // Допустимые значения
)
```

### ToolResult

```kotlin
data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?>? = null
) {
    companion object {
        fun success(message: String, data: Map<String, Any?>? = null)
        fun failure(message: String)
    }
}
```

---

## 5. Как агент использует инструменты

### Цикл агента (VoiceAgent.process):

```
Пользователь: "Включи фонарик"
    │
    ▼
[1] Построение messages:
    system_prompt + история + "Включи фонарик"
    │
    ▼
[2] Вызов LLM (OpenAI gpt-4o-mini):
    Отправляем messages + tools (схемы всех 63 инструментов)
    │
    ▼
[3] LLM возвращает:
    {
      "tool_calls": [{
        "name": "system_control",
        "arguments": {"action": "toggle_flashlight"}
      }]
    }
    │
    ▼
[4] ToolRegistry.executeWithRetry("system_control", {...}):
    → Ищет инструмент в реестре
    → Вызывает execute()
    → При ошибке — повторяет до 2 раз
    │
    ▼
[5] Результат → обратно в messages:
    {"role": "tool", "content": "✓ Фонарик включён"}
    │
    ▼
[6] Повторный вызов LLM (итерация 2):
    LLM видит результат → формирует ответ пользователю
    │
    ▼
[7] Ответ: "Фонарик включил!"
    │
    ▼
[8] TTS: озвучивает ответ
```

### Параметры цикла:
- **MAX_ITERATIONS = 5** — максимум итераций tool calls
- **CONVERSATION_TIMEOUT_MS = 60_000** — контекст стирается через 60 сек
- **MAX_HISTORY_MESSAGES = 20** — макс. сообщений в истории
- **MAX_TOOL_RESULT_LENGTH = 500** — обрезание длинных результатов
- **temperature = 0.7** — креативность LLM
- **max_tokens = 1024** — макс. длина ответа
- **model = "gpt-4o-mini"** — модель OpenAI

---

## 6. MethodChannel протокол

### Flutter → Kotlin

**Канал:** `com.aiagent.ai_voice_agent/engine`

| Метод | Параметры | Что делает |
|-------|-----------|------------|
| `initialize` | — | Инициализация движка |
| `processVoiceCommand` | — | Запись → STT → Агент → TTS |
| `processText` | `{"text": "..."}` | Текст → Агент → TTS |
| `executeTool` | `{"toolName": "...", "params": {...}}` | Прямой вызов инструмента |
| `reloadConfig` | — | Перезагрузка конфига |
| `clearContext` | — | Очистка истории |
| `getDebugLogs` | — | Получить логи |

### ⚠️ КРИТИЧНО: Имя параметра для executeTool

```kotlin
// MainActivity.kt — строка ~131
val toolName = call.argument<String>("toolName")  // НЕ "tool", НЕ "name"
```

Если изменить на `"tool"` — все тесты сломаются!

---

## 7. Регистрация инструментов (полный список)

Файл: `EngineManager.kt` → `registerTools()`

```
=== Core (5) ===
GetCurrentTimeTool, SystemControlTool, OpenAppTool, SelfAwarenessTool, WebSearchTool

=== Alarm & Timer (4) ===
SetAlarmTool, CancelAlarmTool, SetTimerTool, CancelTimerTool

=== Info (3) ===
GetLocationTool, GetWeatherTool, GetCurrencyRateTool

=== Media & Play Store (3) ===
MediaControlTool, PlayStoreSearchTool, OpenPlayStoreTool

=== WiFi & Files (3) ===
ConnectWifiTool, CreateFileTool, ReadFileTool

=== Accessibility (6) ===
ReadScreenTool, ClickElementTool, TypeTextTool, NavigateTool, ScrollTool, ListClickableTool

=== Notifications (2) ===
ReadNotificationsTool, DismissNotificationTool

=== Camera (2) ===
TakeSelfieTool, TakePhotoTool

=== File System (6) ===
CreateFolderTool, ListFilesTool, DeleteFileTool, WriteFileTool, OpenFileTool, FileInfoTool

=== Device Info (3) ===
BatteryInfoTool, DeviceInfoTool, StorageInfoTool

=== Control (5) ===
ClipboardReadTool, ClipboardWriteTool, VolumeControlTool, BrightnessControlTool, FlashlightTool

=== Communication (8) ===
SendSmsTool, MakeCallTool, SearchContactsTool, ReadSmsTool, SearchSmsTool, LaunchUrlTool, ShareTextTool, SendEmailTool

=== System (3) ===
ListAppsTool, AppInfoTool, NetworkInfoTool

=== Drawing (1) ===
DrawImageTool

=== Download (1) ===
DownloadFileTool

=== Music (1) ===
SearchLocalMusicTool

=== Memory (2) ===
RememberFactTool(memory), ForgetFactTool(memory)

ИТОГО: 63 инструмента
```

---

## 8. Как LLM выбирает инструменты

LLM получает схемы всех инструментов в формате OpenAI function calling:

```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "system_control",
        "description": "Управлять настройками телефона...",
        "parameters": {
          "type": "object",
          "properties": {
            "action": {
              "type": "string",
              "description": "Действие: toggle_wifi, toggle_bluetooth..."
            },
            "value": {
              "type": "number",
              "description": "Значение для set_volume (0-100)..."
            }
          },
          "required": ["action"]
        }
      }
    }
    // ... ещё 62 инструмента
  ],
  "tool_choice": "auto"
}
```

**Важно:**
- `description` на русском — LLM понимает русский напрямую
- `enumValues` помогают LLM выбрать правильные значения
- `tool_choice: "auto"` — LLM сам решает когда вызывать инструмент

---

## 9. Восстановление системы (если всё потерялось)

### Порядок действий:

1. **Восстановить core файлы:**
   - `AgentTool.kt` — интерфейс (скопировать из этого документа)
   - `ToolRegistry.kt` — реестр (execute, executeWithRetry)
   - `VoiceAgent.kt` — агент (цикл + LLM вызовы)
   - `EngineConfig.kt` — конфиг
   - `EngineManager.kt` — singleton + регистрация

2. **Восстановить инструменты:**
   - Каждый файл в `engine/tools/*.kt` — реализует `AgentTool`
   - Документация в `docs/tools/*.md` — описывает поведение

3. **Настроить MethodChannel:**
   - `MainActivity.kt` — обработчик `executeTool`
   - Параметр `"toolName"` (НЕ `"tool"`)

4. **Настроить Flutter сторону:**
   - `tool_tester_screen.dart` — тесты
   - MethodChannel вызовы с `{"toolName": "...", "params": {...}}`

5. **Проверить:**
   - Запустить приложение
   - Открыть Tool Tester
   - Вызвать `get_current_time` — должен вернуть время

---

## 10. Ограничения платформы

| Ограничение | Android | Решение |
|-------------|---------|---------|
| WiFi toggle | 10+ | Открывает настройки WiFi |
| Bluetooth toggle | 12+ | Открывает настройки Bluetooth |
| Яркость | Все | Нужно WRITE_SETTINGS |
| SMS чтение | Все | Нужно READ_SMS |
| Контакты | Все | Нужно READ_CONTACTS |
| Камера | Все | Нужно CAMERA |
| Геолокация | Все | Нужно ACCESS_FINE_LOCATION |
| Прямой звонок | Все | Нужно CALL_PHONE |

---

## 11. Тестирование инструментов

### Через Tool Tester (Flutter UI):
1. Открыть Tool Tester
2. Найти инструмент в списке
3. Нажать ▶ — вызовется Kotlin инструмент через MethodChannel
4. Результат отобразится в UI

### Через голос (Клёпа):
1. Нажать кнопку микрофона
2. Сказать команду (например "включи фонарик")
3. Клёпа → LLM → tool call → execute → ответ → TTS

### Через логи:
```
adb logcat -s "OpenAppTool" "SystemControlTool" "VoiceAgent" "EngineManager"
```
