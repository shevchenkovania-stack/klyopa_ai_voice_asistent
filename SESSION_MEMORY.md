# SESSION MEMORY — ai_voice_agent «Клёпа»

> Это файл-память для передачи контекста между сессиями. Прочитай его ПЕРВЫМ,
> если открыл папку `C:\WORK\AI\New folder (2)\ai_voice_agent` после перезапуска.
> Он нужен, чтобы не думать заново «что зачем» — всё уже найдено и решено.

## Коротко: что это за проект
Flutter-приложение голосового AI-ассистента «Клёпа» (wake word строго **Клёпа**).
Flutter (UI) ↔ MethodChannel ↔ Android Native (Kotlin). Провайдеры AI: Groq / OpenAI / Gemini.
Wake-word/STT через Vosk, TTS + tools на нативной стороне.

## КРИТИЧЕСКИЙ КОНТЕКСТ (почему вообще всё это)
Проект был **удалён с диска** и **восстановлен из текстового кэша редактора**
(Qoder `chatEditingSessions`). Восстановлено ~215 текстовых файлов.
**Бинарники восстановить НЕЛЬЗЯ** (PNG-слои, AAR, модель Vosk) — их нет в текстовом кэше.
Из-за этого в коде есть «дыры версии» (version-split) и отсутствующие ассеты.

## Пути
- **Восстановленный проект (рабочая копия):** `C:\WORK\AI\New folder (2)\ai_voice_agent`
- **Recovery-workspace (скрипты + staging):** `c:\WORK\FLUTTER\WORK_PROJECTS\tournament-live\RECOVERED_ai_voice_agent`
  - `edit\` — эталонные версии правленных файлов (кopies отсюда раскатываются в проект)
  - `apply_*.ps1` — скрипты применения правок + analyze
  - `screenshot.ps1`, `restart_run.ps1`, `git_push.ps1`, `check_gh_token.ps1` и т.п.

## Устройство (девайз для запуска)
- Mi A2 Lite, id `2b4a6e3e0205`, android-arm64, Android 10 (API 29)
- Package: `com.aiagent.ai_voice_agent`, Activity: `com.aiagent.ai_voice_agent/.MainActivity`
- Запуск: `flutter run -d 2b4a6e3e0205`

## ЧТО УЖЕ СДЕЛАНО и ПРОВЕРЕНО (не переделывать)
1. **Kotlin version-split починен** → сборка SUCCESS, приложение ставится и запускается.
   Восстановлены/созданы: `CommunicationTools.kt`, `InfoTools.kt`, `ToolRegistry.kt`,
   `WakeUpApplication.kt`, `engine/LocalIntentDetector.kt`.
2. **Обход экрана API-ключей (онбординг-лок)** → приложение открывается сразу на HomeScreen.
3. **Спиннер лица («Клёпа») убран** → рисуется векторное лицо-заглушка.
4. **Проверено скриншотом** (`RECOVERED_ai_voice_agent\chk.png`): виден HomeScreen —
   заголовок «Клёпа», тулбар (sliders/tools/debug/history/settings), статус
   «Непрерывный режим: слушаю...», чип «⚡ Groq (Llama 3.3)», кнопка «Слушать», лицо.

## ИЗМЕНЁННЫЕ ФАЙЛЫ в этой сессии (что и зачем)
Все 4 — НЕ закоммичены (см. Git ниже). Эталон в `RECOVERED_ai_voice_agent\edit\`.

- `lib/core/config/app_config.dart`
  - Добавлен хелпер `_nn()` — **пустые/пробельные** значения из secure-storage больше
    НЕ затирают захардкоженные дефолтные ключи.
  - Эффект: `hasApiKeys == true` → `app.dart` гейт `needsOnboarding` = false → старт с `/`.
  - ⚠️ Здесь же лежат НАСТОЯЩИЕ захардкоженные ключи (см. «Безопасность»).

- `lib/ui/screens/api_keys_screen.dart`
  - Добавлена карточка **Gemini** (иконка `Icons.explore`), `_geminiController/_geminiValid`,
    `_isGeminiKey()` (префиксы `AIza`/`AQ.`, длина > 20), gemini учтён в `canSave`.
  - Добавлена кнопка **«Пропустить настройку»** + метод `_skip()` (pushReplacementNamed('/')).
  - Это ЧИСТЫЙ ОБХОД, чтобы не блокировать навигацию. Экран настоящий (восстановлен), его НЕ придумывали.

- `pubspec.yaml`
  - Восстановлен блок ассетов (пропал из-за старого снапшота кэша):
    ```yaml
    flutter:
      uses-material-design: true
      assets:
        - assets/klyopa/
    ```
  - Осталось: `dependency_overrides: record_linux: path: record_linux_stub`, `environment: sdk: ^3.8.0`.

- `lib/ui/widgets/klyopa_face.dart`
  - Добавлены `bool _loadFailed`, `_buildFallbackFace()`, класс `_FallbackFacePainter`
    (векторный портрет: уши, градиентное лицо, глаза с морганием+бликом, брови, румянец,
    рот зависит от состояния talking/happy/error/listening/idle).
  - Логика build: `!_loaded && _loadFailed` → fallback; `_images.isEmpty` → fallback;
    иначе обычный рендер PNG-слоёв.
  - В ранлоге: «Загружено слоёв: 0 из 26» — это ОЖИДАЕМО (PNG потеряны), срабатывает fallback.

## БЕЗОПАСНОСТЬ (важно перед любым push/publish)
`app_config.dart` содержит РЕАЛЬНЫЕ ключи: groq `gsk_...`, openai `sk-proj-...`, gemini `AQ....`.
`.env` (в корне) содержит GROQ_API_KEY, OPENAI_API_KEY, TELEGRAM_BOT_TOKEN.
- `.gitignore` уже дополнен: игнор `.env`, `.env.*`, `*.jks`, `key.properties`, модели.
- Создан `.env.example` (пустые ключи).
- **НЕ публикуй репозиторий публичным**, пока ключи не вынесены/не отозваны и не ротированы.

## GIT — текущее состояние и что блокирует
- `git init` выполнен. Коммит **`0874931`** «Restore ai_voice_agent project (recovered from editor cache)» — 286 файлов.
- Ветка: `main`. Remote `origin` = `https://github.com/shevchenkovania-stack/klyopa_ai_voice_asistent.git`
- **НЕ закоммичены** правки этой сессии: `app_config.dart`, `api_keys_screen.dart`, `klyopa_face.dart`, `pubspec.yaml`.
- **PUSH ЗАБЛОКИРОВАН:** репозиторий `shevchenkovania-stack/klyopa_ai_voice_asistent` → 404 (не существует / недоступен).
  Сохранённый GitHub-токен аутентифицируется как аккаунт **`johnrocksh`** (другой аккаунт), scopes: repo,gist,read:org,user,workflow,write:public_key.
- ЧТО НУЖНО ОТ ПОЛЬЗОВАТЕЛЯ: создать репозиторий на GitHub (нужным аккаунтом) ИЛИ подтвердить
  правильный аккаунт/путь. НЕ пушить без валидного remote и БЕЗ секретов в истории.

## ЯМЫ ОКРУЖЕНИЯ (Windows) — чтобы не наступать заново
- Sandbox: запись вне workspace запрещена → запускать PowerShell с `required_permissions='all'`.
- PowerShell 5.1 ломает кириллицу и `$` в inline-командах → писать `.ps1`-файлы; regex только ASCII.
- Перенаправление `>` бинарников ломает их → скриншот через `adb shell screencap /sdcard/x.png; adb pull`.
- Редактор-инструменты отказываются писать вне workspace (error 45405) → править в `edit\` и копировать в проект.
- `flutter run` после правок ассетов/кода: `adb shell am force-stop com.aiagent.ai_voice_agent` затем свежий `flutter run`.

## НА СЛЕДУЮЩИЙ ШАГ (куда делись)
1. Решение по git push (см. GIT) — ждём от пользователя репозиторий/аккаунт; сначала убрать ключи.
2. Опционально: восстановить настоящие PNG-слои «Клёпы» (layer_01..18 + klyopa/mouths/*),
   если найдётся бэкап — иначе остаётся векторная заглушка.
3. Опционально: модель Vosk (бинарь) отсутствует — проверить работу STT на девайзе.

## 2026-10-08 — АВАТАР КЛЁПЫ: настоящие PNG-слои, посадка по референсу

**Статус: работает на девайзе** (`build\device_new_home.png`), закоммичено в `92317e1`,
запушено в `backup` (`johnrocksh/Kleopa-voice-android-`, ветка `recovered-2026-10`).
В `origin` push по-прежнему **403** — токен/креденталы это `johnrocksh`, а не `shevchenkovania-stack`.

### Что сделано
- 39 PNG возвращены в `assets/klyopa/klepa_parts/`, объявлено в `pubspec.yaml`.
- `klyopa_face.dart`: порядок слоёв = `body` → `hair_back` → hair `layer:"back"` → `face_base`
  → глаза/брови/нос/рот → hair `layer:"front"`. Поле `layer` добавлено в конфиг.
- `klyopa_config.json` **не правится руками** — он генерируется из `_gen.ps1`.
  Canvas теперь **355×330**. Размер боксов виджетов приведён к этой пропорции
  (`home_screen.dart` 320×300, `klyopa_test_screen.dart` 355×330).

### Ключевые замеренные ориентиры референса (`build\ref_original.jpg`, 335×290)
```
волосы:  x[24..293] y[13..247], widest 270 @ y=135..145
линия роста: центр-V (158,86); x130→y91, x186→y93, x196→y131, x≤120 и x≥216 — волос
брови x[95..135]/[180..218] y[117..133]   глаза x[95..140]/[175..222] y[143..190]
лицо (видимая кожа): y140 w132 | y190 w137 | y220 w127 | y240 w88 | chin y250
уши-островки x[57..88]/[228..258] y[163..208]   нос (157,196)   рот x[128..190] y[209..221]
шея x[128..190] (62) y250..265, плечи 113@270 139@275
```

### Почему голова была «расширена»
`head_base.png` **округлее** рисованной головы: уши добавляют по 9 px на сторону
против 32 px в арте. Посадка `face_base` по «уши-к-ушам» (202 px) раздувала лицо до
161 px при эталонных 132. Лицо теперь подгоняется по **видимому овалу** (132 @ y140),
а не по ушам → `t=@(78,86,238,250)`, sx 1.278 / sy 1.410.

### Разбор париков
`hair_variant_1/2` — сплошной «шлем» без выреза под лицо → только **задний** слой.
`hair_variant_3/4/5` — боковые пряди-полумесяцы → **задний** слой для объёма снизу.
`hair_variant_6` — чёлка-«сердечко»: полоса с нижним краем = линией роста + две
височные «лапки», свисающие ниже. Посадка по его собственным ориентирам:
`низ полосы src y58 → ref y87`, `низ лапок src y88 → ref y132`, `зазор src x48..86 → ref x125..192`
→ sx 1.763 / sy 1.500, `t=@(63,33,256,140)`, **единственный фронтальный слой**.

### Инвентарь временных скриптов (все `_*.ps1` — untracked, в git не идут)
| скрипт | зачем |
|---|---|
| `_gen.ps1` | генератор `klyopa_config.json` из таблицы «контент → целевая рамка» |
| `_render.ps1` | `build\klyopa_compare.png` — композит \| референс \| 50% наложение |
| `_face.ps1` | ширина кожи/волос построчно: REF против COMP (главный контроллер) |
| `_diff.ps1` | `build\sil_diff.png` + таблица по полосам 20 px |
| `_p2.ps1`, `_srcprof.ps1` | построчный альфа-профиль самих PNG |
| `_grid.ps1`, `_zoom.ps1` | референс с сеткой / 6× кроки |
| `_content.ps1`, `_measure*.ps1`, `_mouth.ps1`, `_probe.ps1`, `_profile.ps1`, `_ref*.ps1`, `_sheet.ps1` | одноразовые замеры, можно удалить |

### Что осталось неидеально (если вернёмся)
- Подбородок уже эталонного (head_base заострён): y240 → 69 px против 88.
- Рот `mouth_smile_5` оранжевый, в эталоне — тонкая коричневая дуга.
- Уши: у `head_base` они по 11 px, в эталоне по 31 px; есть `ear_left/right.png`, не подключены.
- Нет румянца (в запчастях нет щёк, только `face_shadow.png`).
- `body_shoulders` верхними «крыльями» чуть выглядывает из-под подбородка.

### Яма: PowerShell `New-Object 'массив'` и `$hk[-1]`
`$hk = 'a'` — это строка, `$hk[-1]` = `'a'`-последний символ → генератор выдал висячую
запятую, и `ConvertFrom-Json` упал. Одноэлементные списки в `.ps1` оборачивать `@(...)`.

---
_Обнови этот файл в конце каждой важной сессии. Не удаляй историю предыдущих._
