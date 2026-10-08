# Документация инструментов Клёпы

Все инструменты реализованы на **Kotlin** и зарегистрированы в `EngineManager.kt`.

## Категории инструментов

| Категория | Файл | Кол-во |
|-----------|------|--------|
| [Доступность](accessibility.md) | AccessibilityTools.kt | 6 |
| [Будильники и таймеры](alarms.md) | AlarmTimerTools.kt | 4 |
| [Приложения](apps.md) | AppAndInfoTools.kt | 3 |
| [Камера](camera.md) | CameraTools.kt | 2 |
| [Коммуникации](communication.md) | CommunicationTools.kt | 7 |
| [Управление](control.md) | ControlTools.kt | 5 |
| [Устройство](device.md) | DeviceTools.kt | 3 |
| [Загрузка файлов](download.md) | DownloadFileTool.kt | 1 |
| [Рисование](drawing.md) | DrawImageTool.kt | 1 |
| [Файлы](files.md) | FileTools.kt + WifiFileTools.kt | 8 |
| [Информация](info.md) | InfoTools.kt + GetCurrentTimeTool.kt | 4 |
| [Медиа](media.md) | MediaTools.kt | 3 |
| [Память](memory.md) | MemoryTools.kt | 2 |
| [Уведомления](notifications.md) | NotificationTools.kt | 2 |
| [Музыка](music.md) | SearchLocalMusicTool.kt | 1 |
| [Система](system.md) | SystemControlTool.kt + SystemTools.kt | 8 |
| [WiFi](wifi.md) | WifiFileTools.kt | 1 |

---

## Всего: 63 инструмента

### Как работает каждый инструмент

Каждый документ содержит:
- **Название** и **описание** на русском
- **Параметры** (что принимает)
- **Что делает** (реальное поведение)
- **Ограничения** (если есть)
- **Пример вызова** от Клёпы
