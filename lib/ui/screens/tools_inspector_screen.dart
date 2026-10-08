import 'package:flutter/material.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/agent/tools/tool_registry.dart';
import 'package:ai_voice_agent/agent/tools/base_tool.dart';

/// Tool category for grouping
enum ToolCategory {
  apps('Приложения', Icons.apps, Colors.blue),
  communication('Связь', Icons.message, Colors.green),
  system('Система', Icons.settings, Colors.orange),
  files('Файлы', Icons.folder, Colors.brown),
  media('Медиа', Icons.play_circle, Colors.purple),
  info('Информация', Icons.info, Colors.teal),
  accessibility('Доступность', Icons.accessibility, Colors.cyan),
  notifications('Уведомления', Icons.notifications, Colors.amber),
  device('Устройство', Icons.phone_android, Colors.indigo),
  control('Управление', Icons.tune, Colors.deepOrange),
  network('Сеть', Icons.wifi, Colors.lightBlue),
  drawing('Рисование', Icons.brush, Colors.pink),
  download('Скачивание', Icons.download, Colors.deepPurple),
  music('Музыка', Icons.music_note, Colors.purple);

  final String label;
  final IconData icon;
  final Color color;
  const ToolCategory(this.label, this.icon, this.color);
}

/// Tool metadata with decision flow
class ToolInfo {
  final String toolKey;
  final String name;
  final String description;
  final ToolCategory category;
  final List<String> parameters;
  final List<String> relatedTools;
  final String decisionFlow;
  final List<String> fallbackChain;
  final String? defaultApp;

  ToolInfo({
    required this.toolKey,
    required this.name,
    required this.description,
    required this.category,
    required this.parameters,
    required this.relatedTools,
    required this.decisionFlow,
    required this.fallbackChain,
    this.defaultApp,
  });
}

/// Tools inspector screen - shows all tools with their logic
class ToolsInspectorScreen extends StatefulWidget {
  const ToolsInspectorScreen({super.key});

  @override
  State<ToolsInspectorScreen> createState() => _ToolsInspectorScreenState();
}

class _ToolsInspectorScreenState extends State<ToolsInspectorScreen> {
  ToolCategory? _selectedCategory;
  AgentTool? _expandedTool;

  /// Tool metadata mapping
  static final Map<String, ToolInfo> _toolInfo = {
    'open_app': ToolInfo(
      toolKey: 'open_app',
      name: 'Запуск приложения',
      description: 'Запускает приложение по названию. Если не найдено — ищет похожие.',
      category: ToolCategory.apps,
      parameters: ['name — название приложения'],
      relatedTools: ['play_store_search', 'open_play_store'],
      decisionFlow: '1. Ищем приложение по названию\n2. Если не найдено → показываем похожие\n3. Если нет похожих → предлагаем установить',
      fallbackChain: ['open_app → play_store_search → open_play_store'],
      defaultApp: 'Зависит от запроса',
    ),
    'play_store_search': ToolInfo(
      toolKey: 'play_store_search',
      name: 'Поиск в Play Store',
      description: 'Ищет приложения в Google Play через DuckDuckGo.',
      category: ToolCategory.apps,
      parameters: ['query — поисковый запрос'],
      relatedTools: ['open_play_store', 'open_app'],
      decisionFlow: '1. Ищем через DuckDuckGo с фильтром play.google.com\n2. Показываем найденные варианты\n3. Предкладываем установить',
      fallbackChain: ['play_store_search → open_play_store'],
    ),
    'open_play_store': ToolInfo(
      toolKey: 'open_play_store',
      name: 'Открыть в Play Store',
      description: 'Открывает страницу приложения в Google Play для установки.',
      category: ToolCategory.apps,
      parameters: ['package_name — имя пакета (com.example.app)'],
      relatedTools: ['play_store_search', 'open_app'],
      decisionFlow: '1. Открываем market:// ссылку\n2. Если Play Store недоступен → веб-версия',
      fallbackChain: ['open_play_store → веб-браузер'],
    ),
    'send_sms': ToolInfo(
      toolKey: 'send_sms',
      name: 'Отправить SMS',
      description: 'Отправляет SMS-сообщение на указанный номер.',
      category: ToolCategory.communication,
      parameters: ['phone — номер телефона', 'message — текст сообщения'],
      relatedTools: ['search_contacts', 'make_call'],
      decisionFlow: '1. Проверяем разрешение на SMS\n2. Отправляем сообщение\n3. Подтверждаем отправку',
      fallbackChain: ['send_sms → показать ошибку если нет разрешения'],
    ),
    'make_call': ToolInfo(
      toolKey: 'make_call',
      name: 'Позвонить',
      description: 'Звонит по указанному номеру телефона.',
      category: ToolCategory.communication,
      parameters: ['phone — номер телефона'],
      relatedTools: ['search_contacts', 'send_sms'],
      decisionFlow: '1. Проверяем разрешение на звонки\n2. Открываем приложение телефона с номером',
      fallbackChain: ['make_call → показать ошибку если нет разрешения'],
    ),
    'search_contacts': ToolInfo(
      toolKey: 'search_contacts',
      name: 'Найти контакт',
      description: 'Ищет контакт в телефонной книге по имени.',
      category: ToolCategory.communication,
      parameters: ['query — имя для поиска'],
      relatedTools: ['send_sms', 'make_call'],
      decisionFlow: '1. Запрашиваем список контактов\n2. Фильтруем по имени\n3. Возвращаем совпадения с номерами',
      fallbackChain: ['search_contacts → показать ошибку если нет разрешения'],
    ),
    'set_alarm': ToolInfo(
      toolKey: 'set_alarm',
      name: 'Установить будильник',
      description: 'Создаёт системный будильник на указанное время.',
      category: ToolCategory.system,
      parameters: ['hour — часы (0-23)', 'minute — минуты (0-59)', 'label — название (опц.)'],
      relatedTools: ['set_timer', 'get_current_time'],
      decisionFlow: '1. Проверяем корректность времени\n2. Создаём будильник через системный Intent\n3. Подтверждаем установку',
      fallbackChain: ['set_alarm → показать ошибку при неверном времени'],
    ),
    'set_timer': ToolInfo(
      toolKey: 'set_timer',
      name: 'Установить таймер',
      description: 'Запускает таймер обратного отсчёта.',
      category: ToolCategory.system,
      parameters: ['seconds — длительность в секундах', 'label — название (опц.)'],
      relatedTools: ['set_alarm', 'get_current_time'],
      decisionFlow: '1. Создаём таймер через системный Intent\n2. Пользователь видит системный таймер',
      fallbackChain: ['set_timer → системное приложение таймера'],
    ),
    'get_current_time': ToolInfo(
      toolKey: 'get_current_time',
      name: 'Текущее время',
      description: 'Возвращает текущее время и дату.',
      category: ToolCategory.info,
      parameters: [],
      relatedTools: ['set_alarm', 'set_timer'],
      decisionFlow: '1. Получаем системное время\n2. Форматируем в читаемый вид',
      fallbackChain: ['get_current_time — всегда работает'],
    ),
    'create_file': ToolInfo(
      toolKey: 'create_file',
      name: 'Создать файл',
      description: 'Создаёт текстовый файл в документах приложения.',
      category: ToolCategory.files,
      parameters: ['filename — имя файла', 'content — содержимое'],
      relatedTools: ['read_file'],
      decisionFlow: '1. Создаём файл в /documents/\n2. Записываем содержимое\n3. Проверяем создание и размер',
      fallbackChain: ['create_file → ошибка при проблеме с записью'],
    ),
    'read_file': ToolInfo(
      toolKey: 'read_file',
      name: 'Прочитать файл',
      description: 'Читает содержимое файла из документов.',
      category: ToolCategory.files,
      parameters: ['filename — имя файла'],
      relatedTools: ['create_file'],
      decisionFlow: '1. Ищем файл в /documents/\n2. Если не найден → показываем доступные\n3. Читаем и возвращаем содержимое',
      fallbackChain: ['read_file → показать список файлов если не найден'],
    ),
    'web_search': ToolInfo(
      toolKey: 'web_search',
      name: 'Веб-поиск',
      description: 'Ищет информацию в интернете через DuckDuckGo.',
      category: ToolCategory.info,
      parameters: ['query — поисковый запрос'],
      relatedTools: ['play_store_search'],
      decisionFlow: '1. Отправляем запрос в DuckDuckGo\n2. Парсим результаты\n3. Форматируем ответ',
      fallbackChain: ['web_search → показать ошибку при отсутствии сети'],
    ),
    'get_weather': ToolInfo(
      toolKey: 'get_weather',
      name: 'Погода',
      description: 'Получает текущую погоду для указанного города.',
      category: ToolCategory.info,
      parameters: ['city — название города'],
      relatedTools: ['web_search'],
      decisionFlow: '1. Запрашиваем wttr.in API\n2. Парсим ответ\n3. Форматируем погоду',
      fallbackChain: ['get_weather → web_search если API недоступен'],
    ),
    'get_currency_rate': ToolInfo(
      toolKey: 'get_currency_rate',
      name: 'Курс валют',
      description: 'Получает текущий курс валют.',
      category: ToolCategory.info,
      parameters: ['from — исходная валюта', 'to — целевая валюта'],
      relatedTools: ['web_search'],
      decisionFlow: '1. Запрашиваем exchangerate-api\n2. Получаем курс\n3. Форматируем ответ',
      fallbackChain: ['get_currency_rate → web_search если API недоступен'],
    ),
    'media_control': ToolInfo(
      toolKey: 'media_control',
      name: 'Управление медиа',
      description: 'Управляет воспроизведением медиа (play/pause/next).',
      category: ToolCategory.media,
      parameters: ['action — действие (play, pause, next, previous)'],
      relatedTools: ['system_control'],
      decisionFlow: '1. Отправляем медиа-команду через систему\n2. Подтверждаем выполнение',
      fallbackChain: ['media_control → системное управление медиа'],
    ),
    'system_control': ToolInfo(
      toolKey: 'system_control',
      name: 'Системное управление',
      description: 'Управляет системными настройками (WiFi, Bluetooth и т.д.).',
      category: ToolCategory.system,
      parameters: ['action — действие (wifi_toggle, bluetooth_toggle и др.)'],
      relatedTools: ['media_control'],
      decisionFlow: '1. Определяем тип действия\n2. Выполняем через системные API\n3. Подтверждаем изменение',
      fallbackChain: ['system_control → показать ошибку если действие недоступно'],
    ),
    'self_awareness': ToolInfo(
      toolKey: 'self_awareness',
      name: 'Самосознание',
      description: 'Управление видимостью агента — появиться или скрыться.',
      category: ToolCategory.system,
      parameters: ['action — действие (show — появиться, hide — скрыться)'],
      relatedTools: [],
      decisionFlow: '1. Получаем команду от пользователя\n2. Если "покажись/где ты" → show\n3. Если "скройся/уйди" → hide',
      fallbackChain: ['self_awareness → системное управление окнами'],
    ),
    'cancel_alarm': ToolInfo(
      toolKey: 'cancel_alarm',
      name: 'Отменить будильник',
      description: 'Отменяет системный будильник по ID или все будильники.',
      category: ToolCategory.system,
      parameters: ['alarm_id — ID будильника (опц.)'],
      relatedTools: ['set_alarm', 'get_current_time'],
      decisionFlow: '1. Если указан ID → отменяем конкретный\n2. Если нет → отменяем все\n3. Подтверждаем отмену',
      fallbackChain: ['cancel_alarm → показать ошибку если будильник не найден'],
    ),
    'cancel_timer': ToolInfo(
      toolKey: 'cancel_timer',
      name: 'Отменить таймер',
      description: 'Отменяет активный таймер обратного отсчёта.',
      category: ToolCategory.system,
      parameters: ['timer_id — ID таймера (опц.)'],
      relatedTools: ['set_timer', 'cancel_alarm'],
      decisionFlow: '1. Отменяем таймер через системный Intent\n2. Подтверждаем отмену',
      fallbackChain: ['cancel_timer → показать ошибку если таймер не найден'],
    ),
    'connect_wifi': ToolInfo(
      toolKey: 'connect_wifi',
      name: 'Подключить WiFi',
      description: 'Подключается к WiFi сети по SSID. Требует разрешение на местоположение.',
      category: ToolCategory.system,
      parameters: ['ssid — название сети'],
      relatedTools: ['system_control'],
      decisionFlow: '1. Проверяем разрешение ACCESS_FINE_LOCATION\n2. Сканируем сети\n3. Подключаемся к указанной SSID',
      fallbackChain: ['connect_wifi → показать ошибку если сеть не найдена или нет разрешения'],
    ),
    // === Accessibility ===
    'read_screen': ToolInfo(
      toolKey: 'read_screen',
      name: 'Читать экран',
      description: 'Читает содержимое текущего экрана через AccessibilityService. Возвращает список элементов с типами и текстом.',
      category: ToolCategory.accessibility,
      parameters: [],
      relatedTools: ['click_element', 'list_clickable', 'scroll'],
      decisionFlow: '1. Получаем rootInActiveWindow\n2. Обходим дерево элементов\n3. Извлекаем текст, типы, координаты\n4. Возвращаем структурированный список',
      fallbackChain: ['read_screen → ошибка если AccessibilityService не включён'],
    ),
    'click_element': ToolInfo(
      toolKey: 'click_element',
      name: 'Нажать на элемент',
      description: 'Находит элемент на экране по тексту и нажимает на него. Если элемент не кликабелен — ищет кликабельного родителя.',
      category: ToolCategory.accessibility,
      parameters: ['text — текст элемента для поиска'],
      relatedTools: ['read_screen', 'type_text', 'list_clickable'],
      decisionFlow: '1. Ищем элемент по тексту (case-insensitive)\n2. Если кликабелен → кликаем\n3. Если нет → ищем кликабельного родителя\n4. Подтверждаем нажатие',
      fallbackChain: ['click_element → read_screen если элемент не найден'],
    ),
    'type_text': ToolInfo(
      toolKey: 'type_text',
      name: 'Ввести текст',
      description: 'Вводит текст в активное поле ввода. Можно указать метку поля для точного позиционирования.',
      category: ToolCategory.accessibility,
      parameters: ['text — текст для ввода', 'field_label — метка поля (опц.)'],
      relatedTools: ['click_element', 'read_screen'],
      decisionFlow: '1. Если указана метка → ищем поле по метке\n2. Иначе → берём текущий фокус\n3. Вставляем текст через буфер обмена',
      fallbackChain: ['type_text → ошибка если нет активного поля ввода'],
    ),
    'navigate': ToolInfo(
      toolKey: 'navigate',
      name: 'Системная навигация',
      description: 'Выполняет системные действия: назад, домой, недавние, уведомления, быстрые настройки.',
      category: ToolCategory.accessibility,
      parameters: ['action — back, home, recents, notifications, quick_settings, power_dialog'],
      relatedTools: ['read_screen', 'scroll'],
      decisionFlow: '1. Определяем тип навигации\n2. Выполняем globalAction через AccessibilityService\n3. Подтверждаем выполнение',
      fallbackChain: ['navigate → ошибка если AccessibilityService не включён'],
    ),
    'scroll': ToolInfo(
      toolKey: 'scroll',
      name: 'Прокрутка',
      description: 'Прокручивает экран вверх или вниз. Находит прокручиваемый контейнер и выполняет scroll.',
      category: ToolCategory.accessibility,
      parameters: ['direction — down или up'],
      relatedTools: ['read_screen', 'navigate'],
      decisionFlow: '1. Ищем scrollable контейнер в дереве\n2. Выполняем ACTION_SCROLL_FORWARD или ACTION_SCROLL_BACKWARD\n3. Подтверждаем прокрутку',
      fallbackChain: ['scroll → ошибка если нет прокручиваемого контейнера'],
    ),
    'list_clickable': ToolInfo(
      toolKey: 'list_clickable',
      name: 'Список кликабельных',
      description: 'Показывает все кликабельные элементы на экране с их текстом и координатами.',
      category: ToolCategory.accessibility,
      parameters: [],
      relatedTools: ['read_screen', 'click_element'],
      decisionFlow: '1. Обходим дерево элементов\n2. Фильтруем кликабельные с текстом\n3. Возвращаем список с координатами',
      fallbackChain: ['list_clickable → ошибка если AccessibilityService не включён'],
    ),
    // === Notifications ===
    'read_notifications': ToolInfo(
      toolKey: 'read_notifications',
      name: 'Читать уведомления',
      description: 'Читает все активные уведомления через NotificationListenerService. Показывает приложение, заголовок и текст.',
      category: ToolCategory.notifications,
      parameters: [],
      relatedTools: ['dismiss_notification'],
      decisionFlow: '1. Получаем activeNotifications из NotificationListener\n2. Извлекаем title, text, packageName\n3. Форматируем список',
      fallbackChain: ['read_notifications → ошибка если NotificationListener не включён'],
    ),
    'dismiss_notification': ToolInfo(
      toolKey: 'dismiss_notification',
      name: 'Убрать уведомление',
      description: 'Закрывает уведомление по названию приложения.',
      category: ToolCategory.notifications,
      parameters: ['app_name — название приложения'],
      relatedTools: ['read_notifications'],
      decisionFlow: '1. Ищем уведомление по packageName\n2. Вызываем cancelNotification\n3. Подтверждаем удаление',
      fallbackChain: ['dismiss_notification → ошибка если уведомление не найдено'],
    ),
    // === NEW: File System (6 tools) ===
    'create_folder': ToolInfo(
      toolKey: 'create_folder',
      name: 'Создать папку',
      description: 'Создаёт папку в Documents или указанном месте. Понимает "Загрузки", "Документы" и т.д.',
      category: ToolCategory.files,
      parameters: ['name — имя папки', 'parent — родительская папка (опц.)'],
      relatedTools: ['create_file', 'list_files'],
      decisionFlow: '1. Определяем родительскую папку\n2. Создаём папку (mkdirs)\n3. Подтверждаем создание',
      fallbackChain: ['create_folder → ошибка если нет прав'],
    ),
    'list_files': ToolInfo(
      toolKey: 'list_files',
      name: 'Список файлов',
      description: 'Показывает файлы и папки в указанной директории с размерами и типами.',
      category: ToolCategory.files,
      parameters: ['path — путь к папке (например, /sdcard/Download)'],
      relatedTools: ['read_file', 'create_file', 'file_info', 'delete_file'],
      decisionFlow: '1. Открываем директорию\n2. Сортируем по имени\n3. Возвращаем список с типами и размерами',
      fallbackChain: ['list_files → ошибка если папка не найдена'],
    ),
    'delete_file': ToolInfo(
      toolKey: 'delete_file',
      name: 'Удалить файл',
      description: 'Удаляет файл или пустую папку. Только пустые папки — безопасность!',
      category: ToolCategory.files,
      parameters: ['path — путь к файлу'],
      relatedTools: ['list_files', 'file_info'],
      decisionFlow: '1. Проверяем существование\n2. Если папка — проверяем что пустая\n3. Удаляем и подтверждаем',
      fallbackChain: ['delete_file → ошибка если файл не найден или папка не пуста'],
    ),
    'write_file': ToolInfo(
      toolKey: 'write_file',
      name: 'Записать в файл',
      description: 'Записывает текст в файл. Создаёт файл если не существует. Может дописывать.',
      category: ToolCategory.files,
      parameters: ['path — путь к файлу', 'content — текст', 'append — дописать (true/false, опц.)'],
      relatedTools: ['create_file', 'read_file', 'list_files'],
      decisionFlow: '1. Создаём папки если нужно\n2. Записываем/дописываем текст\n3. Подтверждаем размер',
      fallbackChain: ['write_file → ошибка при проблеме с записью'],
    ),
    'open_file': ToolInfo(
      toolKey: 'open_file',
      name: 'Открыть файл',
      description: 'Открывает файл в приложении по умолчанию (PDF, фото, видео и т.д.).',
      category: ToolCategory.files,
      parameters: ['path — путь к файлу'],
      relatedTools: ['list_files', 'file_info'],
      decisionFlow: '1. Определяем MIME-тип по расширению\n2. Открываем через системный Intent\n3. Система выбирает приложение',
      fallbackChain: ['open_file → ошибка если нет приложения для типа'],
    ),
    'file_info': ToolInfo(
      toolKey: 'file_info',
      name: 'Инфо о файле',
      description: 'Показывает информацию: размер, дата изменения, тип, путь.',
      category: ToolCategory.files,
      parameters: ['path — путь к файлу'],
      relatedTools: ['list_files', 'read_file'],
      decisionFlow: '1. Проверяем существование\n2. Читаем метаданные\n3. Форматируем ответ',
      fallbackChain: ['file_info → ошибка если файл не найден'],
    ),
    // === NEW: Device Info (3 tools) ===
    'battery_info': ToolInfo(
      toolKey: 'battery_info',
      name: 'Состояние батареи',
      description: 'Заряд батареи, статус зарядки, тип зарядного устройства.',
      category: ToolCategory.device,
      parameters: [],
      relatedTools: ['device_info'],
      decisionFlow: '1. Читаем BatteryManager\n2. Определяем статус (CRITICAL/LOW/NORMAL/GOOD/FULL)\n3. Если заряжается — показываем тип (AC/USB/Wireless)',
      fallbackChain: ['battery_info — всегда работает'],
    ),
    'device_info': ToolInfo(
      toolKey: 'device_info',
      name: 'Инфо об устройстве',
      description: 'Модель телефона, версия Android, разрешение экрана, хранилище.',
      category: ToolCategory.device,
      parameters: [],
      relatedTools: ['battery_info', 'storage_info'],
      decisionFlow: '1. Читаем Build.MANUFACTURER/MODEL\n2. Получаем версию Android\n3. Считаем хранилище и экран',
      fallbackChain: ['device_info — всегда работает'],
    ),
    'storage_info': ToolInfo(
      toolKey: 'storage_info',
      name: 'Свободное место',
      description: 'Подробная информация о хранилище: занято, свободно, процент.',
      category: ToolCategory.device,
      parameters: [],
      relatedTools: ['device_info'],
      decisionFlow: '1. Читаем StatFs\n2. Считаем used/total/free\n3. Предупреждаем если >80% занято',
      fallbackChain: ['storage_info — всегда работает'],
    ),
    // === NEW: Control Tools (5 tools) ===
    'clipboard_read': ToolInfo(
      toolKey: 'clipboard_read',
      name: 'Читать буфер',
      description: 'Читает текст из буфера обмена.',
      category: ToolCategory.control,
      parameters: [],
      relatedTools: ['clipboard_write'],
      decisionFlow: '1. Получаем ClipboardManager\n2. Читаем primaryClip\n3. Возвращаем текст',
      fallbackChain: ['clipboard_read → "Буфер пуст" если ничего нет'],
    ),
    'clipboard_write': ToolInfo(
      toolKey: 'clipboard_write',
      name: 'Записать в буфер',
      description: 'Копирует текст в буфер обмена.',
      category: ToolCategory.control,
      parameters: ['text — текст для копирования'],
      relatedTools: ['clipboard_read'],
      decisionFlow: '1. Создаём ClipData\n2. Устанавливаем как primaryClip\n3. Подтверждаем',
      fallbackChain: ['clipboard_write → ошибка если текст пустой'],
    ),
    'volume_control': ToolInfo(
      toolKey: 'volume_control',
      name: 'Громкость',
      description: 'Управляет громкостью: медиа, звонок, уведомления, система. 0-100 или mute/max.',
      category: ToolCategory.control,
      parameters: ['stream — media/ringtone/notification/system', 'level — 0-100 или mute/max'],
      relatedTools: ['brightness'],
      decisionFlow: '1. Определяем аудиопоток\n2. Получаем макс. громкость\n3. Устанавливаем нужный уровень',
      fallbackChain: ['volume_control → ошибка при неверном потоке'],
    ),
    'brightness': ToolInfo(
      toolKey: 'brightness',
      name: 'Яркость экрана',
      description: 'Устанавливает яркость экрана 0-100% или включает авто-яркость.',
      category: ToolCategory.control,
      parameters: ['level — 0-100 или auto'],
      relatedTools: ['volume_control'],
      decisionFlow: '1. Если "auto" → включаем авто-яркость\n2. Иначе → переключаем на ручную\n3. Устанавливаем уровень 10-255',
      fallbackChain: ['brightness → ошибка если нет разрешения WRITE_SETTINGS'],
    ),
    'flashlight': ToolInfo(
      toolKey: 'flashlight',
      name: 'Фонарик',
      description: 'Включает или выключает вспышку камеры как фонарик.',
      category: ToolCategory.control,
      parameters: ['state — on или off'],
      relatedTools: [],
      decisionFlow: '1. Получаем CameraManager\n2. Находим камеру\n3. setTorchMode(on/off)',
      fallbackChain: ['flashlight → ошибка если нет камеры'],
    ),
    // === NEW: Communication (3 tools) ===
    'launch_url': ToolInfo(
      toolKey: 'launch_url',
      name: 'Открыть URL',
      description: 'Открывает ссылку в браузере. Автоматически добавляет https:// если нужно.',
      category: ToolCategory.communication,
      parameters: ['url — ссылка (google.com или https://google.com)'],
      relatedTools: ['web_search'],
      decisionFlow: '1. Добавляем https:// если нет протокола\n2. Открываем через ACTION_VIEW\n3. Система выбирает браузер',
      fallbackChain: ['launch_url → ошибка если нет браузера'],
    ),
    'share_text': ToolInfo(
      toolKey: 'share_text',
      name: 'Поделиться',
      description: 'Отправляет текст через любое приложение: мессенджер, email, соцсети.',
      category: ToolCategory.communication,
      parameters: ['text — текст для отправки', 'subject — тема (опц.)'],
      relatedTools: ['send_email', 'launch_url'],
      decisionFlow: '1. Создаём ACTION_SEND Intent\n2. Показываем диалог выбора приложения\n3. Пользователь выбирает куда отправить',
      fallbackChain: ['share_text → ошибка если нет приложений'],
    ),
    'send_email': ToolInfo(
      toolKey: 'send_email',
      name: 'Отправить email',
      description: 'Открывает почтовый клиент с заполненными полями (кому, тема, текст).',
      category: ToolCategory.communication,
      parameters: ['to — email получателя (опц.)', 'subject — тема (опц.)', 'body — текст письма'],
      relatedTools: ['share_text', 'make_call'],
      decisionFlow: '1. Создаём mailto: Intent\n2. Заполняем поля (to/subject/body)\n3. Открываем почтовый клиент',
      fallbackChain: ['send_email → ошибка если нет email-приложения'],
    ),
    // === NEW: System (3 tools) ===
    'list_apps': ToolInfo(
      toolKey: 'list_apps',
      name: 'Список приложений',
      description: 'Показывает установленные приложения с фильтром по имени.',
      category: ToolCategory.system,
      parameters: ['filter — фильтр по имени (опц.)', 'limit — макс. количество (опц., по умолч. 20)'],
      relatedTools: ['app_info', 'open_app'],
      decisionFlow: '1. Получаем список установленных\n2. Фильтруем только запускаемые\n3. Применяем фильтр по имени\n4. Сортируем и ограничиваем',
      fallbackChain: ['list_apps — всегда работает'],
    ),
    'app_info': ToolInfo(
      toolKey: 'app_info',
      name: 'Инфо о приложении',
      description: 'Показывает информацию: версия, пакет, system app, target SDK.',
      category: ToolCategory.system,
      parameters: ['app_name — имя или пакет приложения'],
      relatedTools: ['list_apps', 'open_app'],
      decisionFlow: '1. Ищем по пакету или имени\n2. Если не точное — ищем похожее\n3. Читаем PackageInfo\n4. Форматируем ответ',
      fallbackChain: ['app_info → ошибка если приложение не найдено'],
    ),
    'network_info': ToolInfo(
      toolKey: 'network_info',
      name: 'Сетевое подключение',
      description: 'Статус сети: WiFi/мобильная, есть ли интернет, metered или безлимит.',
      category: ToolCategory.network,
      parameters: [],
      relatedTools: ['connect_wifi'],
      decisionFlow: '1. Получаем ConnectivityManager\n2. Проверяем activeNetwork\n3. Определяем тип (WiFi/Mobile/Ethernet/VPN)\n4. Проверяем наличие интернета',
      fallbackChain: ['network_info → "Нет подключения" если сеть отсутствует'],
    ),
    // === Camera ===
    'take_selfie': ToolInfo(
      toolKey: 'take_selfie',
      name: 'Сделать селфи',
      description: 'Фотографирует через фронтальную камеру и сохраняет файл.',
      category: ToolCategory.media,
      parameters: [],
      relatedTools: ['take_photo'],
      decisionFlow: '1. Открываем фронтальную камеру (Camera2)\n2. Делаем снимок без превью\n3. Сохраняем JPEG в документы\n4. Возвращаем путь',
      fallbackChain: ['take_selfie → ошибка если нет камеры или разрешения'],
    ),
    'take_photo': ToolInfo(
      toolKey: 'take_photo',
      name: 'Сделать фото',
      description: 'Фотографирует через основную камеру и сохраняет файл.',
      category: ToolCategory.media,
      parameters: [],
      relatedTools: ['take_selfie'],
      decisionFlow: '1. Открываем основную камеру (Camera2)\n2. Делаем снимок без превью\n3. Сохраняем JPEG в документы\n4. Возвращаем путь',
      fallbackChain: ['take_photo → ошибка если нет камеры или разрешения'],
    ),
    // === SMS ===
    'read_sms': ToolInfo(
      toolKey: 'read_sms',
      name: 'Читать SMS',
      description: 'Читает последние SMS-сообщения с устройства. Возвращает номер отправителя, текст и дату.',
      category: ToolCategory.communication,
      parameters: ['count — количество последних SMS (по умолчанию 10)'],
      relatedTools: ['send_sms', 'search_sms', 'make_call'],
      decisionFlow: '1. Запрашиваем разрешение READ_SMS\n2. Читаем из ContentResolver (inbox)\n3. Сортируем по дате (новые первые)\n4. Возвращаем список с номерами и текстом',
      fallbackChain: ['read_sms → ошибка если нет разрешения READ_SMS'],
    ),
    'search_sms': ToolInfo(
      toolKey: 'search_sms',
      name: 'Поиск SMS',
      description: 'Ищет SMS-сообщения по тексту, номеру отправителя или дате. Находит все совпадения.',
      category: ToolCategory.communication,
      parameters: ['query — текст поиска (номер, имя, слово из сообщения)', 'limit — макс. результатов (по умолчанию 20)'],
      relatedTools: ['read_sms', 'send_sms'],
      decisionFlow: '1. Запрашиваем разрешение READ_SMS\n2. Ищем в ContentResolver по body LIKE и address LIKE\n3. Сортируем по дате\n4. Возвращаем найденные SMS',
      fallbackChain: ['search_sms → "не найдено" → предложить прочитать все SMS'],
    ),
    // === Drawing ===
    'draw_image': ToolInfo(
      toolKey: 'draw_image',
      name: 'Нарисовать изображение',
      description: 'Рисует фигуры на холсте (круг, прямоугольник, линия, текст) и сохраняет как PNG. Можно отправить как MMS.',
      category: ToolCategory.drawing,
      parameters: ['shape — circle/rectangle/line/text', 'color — red/blue/green/yellow/white/orange/purple', 'text — текст для shape=text', 'filename — имя файла (опц.)', 'size — размер в пикселях (опц., 512)'],
      relatedTools: ['share_text', 'open_file'],
      decisionFlow: '1. Создаём Bitmap + Canvas (чёрный фон)\n2. Рисуем фигуру указанным цветом\n3. Сохраняем PNG в /Documents/\n4. Можно отправить через share или MMS',
      fallbackChain: ['draw_image → ошибка если неверная фигура'],
    ),
    // === Download ===
    'download_file': ToolInfo(
      toolKey: 'download_file',
      name: 'Скачать файл',
      description: 'Скачивает файл по URL. MP3, картинки, PDF, документы. Сохраняет в Downloads/Music/Documents.',
      category: ToolCategory.download,
      parameters: ['url — прямая ссылка на файл', 'filename — имя файла (опц., авто из URL)', 'folder — Downloads/Music/Documents (опц.)'],
      relatedTools: ['web_search', 'open_file', 'media_control'],
      decisionFlow: '1. web_search находит прямую ссылку\n2. download_file скачивает по URL\n3. MediaScanner добавляет в галерею/музыку\n4. open_file или media_control для воспроизведения',
      fallbackChain: ['download_file → ошибка если URL недоступен'],
    ),
    // === Music ===
    'search_local_music': ToolInfo(
      toolKey: 'search_local_music',
      name: 'Найти музыку на устройстве',
      description: 'Ищет песни на телефоне по названию, исполнителю или альбому. Возвращает список с путями для воспроизведения.',
      category: ToolCategory.music,
      parameters: ['query — название песни, исполнитель или альбом', 'limit — макс. результатов (опц., 10)'],
      relatedTools: ['open_file', 'media_control'],
      decisionFlow: '1. search_local_music ищет по MediaStore\n2. Возвращает список совпадений с путями\n3. open_file или media_control для воспроизведения',
      fallbackChain: ['search_local_music → "не найдено" → web_search + download_file'],
    ),
  };

  List<ToolInfo> get _filteredTools {
    final registry = sl<ToolRegistry>();
    final toolNames = registry.names.toSet();
    
    var tools = _toolInfo.entries
        .where((e) => toolNames.contains(e.key))
        .map((e) => e.value)
        .toList();
    
    if (_selectedCategory != null) {
      tools = tools.where((t) => t.category == _selectedCategory).toList();
    }
    
    return tools;
  }

  @override
  Widget build(BuildContext context) {
    final registry = sl<ToolRegistry>();
    final registeredNames = registry.names.toSet();
    final documentedCount = _toolInfo.length;
    final registeredInDocs = registeredNames.where((n) => _toolInfo.containsKey(n)).length;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('🔧 Инструменты'),
            Text(
              'Зарегистрировано: ${registeredNames.length} · Описано: $documentedCount · Показано: ${_filteredTools.length}',
              style: TextStyle(fontSize: 11, color: Colors.grey[400]),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          // Category filter
          Container(
            height: 60,
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              children: [
                _buildCategoryChip(null, 'Все', Colors.grey),
                ...ToolCategory.values.map((c) => _buildCategoryChip(c, c.label, c.color)),
              ],
            ),
          ),
          // Tools list
          Expanded(
            child: ListView.builder(
              itemCount: _filteredTools.length,
              itemBuilder: (context, index) {
                final toolInfo = _filteredTools[index];
                final isRegistered = registeredNames.contains(toolInfo.toolKey);
                return _buildToolCard(toolInfo, isRegistered, registry.get(toolInfo.toolKey), index: index + 1, total: _filteredTools.length);
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCategoryChip(ToolCategory? category, String label, Color color) {
    final isSelected = _selectedCategory == category;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        selected: isSelected,
        label: Text(label),
        selectedColor: color.withOpacity(0.3),
        onSelected: (_) => setState(() => _selectedCategory = category),
        avatar: category != null ? Icon(category.icon, size: 18, color: color) : null,
      ),
    );
  }

  Widget _buildToolCard(ToolInfo info, bool isRegistered, AgentTool? tool, {required int index, required int total}) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      color: theme.cardColor,
      child: ExpansionTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: info.category.color.withOpacity(0.2),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(info.category.icon, color: info.category.color),
        ),
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              margin: const EdgeInsets.only(right: 8),
              decoration: BoxDecoration(
                color: info.category.color.withOpacity(0.2),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                '$index/$total',
                style: TextStyle(fontSize: 10, color: info.category.color, fontWeight: FontWeight.bold, fontFamily: 'monospace'),
              ),
            ),
            Expanded(
              child: Text(
                info.name,
                style: TextStyle(fontWeight: FontWeight.bold, color: theme.colorScheme.onSurface),
              ),
            ),
            if (!isRegistered)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: Colors.red.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text(
                  'Не зарегистрирован',
                  style: TextStyle(fontSize: 10, color: Colors.red),
                ),
              ),
          ],
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              info.description,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: theme.colorScheme.onSurface.withOpacity(0.6), fontSize: 12),
            ),
            const SizedBox(height: 2),
            Text(
              'key: ${info.toolKey}',
              style: TextStyle(color: theme.colorScheme.onSurface.withOpacity(0.35), fontSize: 10, fontFamily: 'monospace'),
            ),
          ],
        ),
        onExpansionChanged: (expanded) {
          setState(() {
            _expandedTool = expanded ? tool : null;
          });
        },
        children: [
          Container(
            padding: const EdgeInsets.all(16),
            color: isDark ? Colors.grey[900] : Colors.grey[50],
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Parameters
                if (info.parameters.isNotEmpty) ...[
                  _buildSectionTitle('Параметры', Icons.tune, Colors.blue),
                  const SizedBox(height: 8),
                  ...info.parameters.map((p) => Padding(
                    padding: const EdgeInsets.only(left: 8, bottom: 4),
                    child: Text('• $p', style: TextStyle(fontSize: 13, color: theme.colorScheme.onSurface)),
                  )),
                  const SizedBox(height: 12),
                ],
                
                // Default app
                if (info.defaultApp != null) ...[
                  _buildSectionTitle('По умолчанию', Icons.star, Colors.amber),
                  const SizedBox(height: 4),
                  Text(info.defaultApp!, style: TextStyle(fontSize: 13, color: theme.colorScheme.onSurface)),
                  const SizedBox(height: 12),
                ],
                
                // Decision flow
                _buildSectionTitle('Логика работы', Icons.route, Colors.green),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: isDark ? Colors.grey[850] : Colors.white,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.green.withOpacity(0.3)),
                  ),
                  child: Text(
                    info.decisionFlow,
                    style: TextStyle(fontSize: 12, fontFamily: 'monospace', color: theme.colorScheme.onSurface),
                  ),
                ),
                const SizedBox(height: 12),
                
                // Fallback chain
                _buildSectionTitle('Цепочка fallback', Icons.alt_route, Colors.orange),
                const SizedBox(height: 8),
                ...info.fallbackChain.map((f) => Container(
                  margin: const EdgeInsets.only(bottom: 4),
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.orange.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(f, style: TextStyle(fontSize: 11, fontFamily: 'monospace', color: theme.colorScheme.onSurface)),
                )),
                const SizedBox(height: 12),
                
                // Related tools
                if (info.relatedTools.isNotEmpty) ...[
                  _buildSectionTitle('Связанные инструменты', Icons.link, Colors.purple),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: info.relatedTools.map((name) {
                      final relatedInfo = _toolInfo[name];
                      return Chip(
                        label: Text(relatedInfo?.name ?? name, style: TextStyle(fontSize: 11, color: theme.colorScheme.onSurface)),
                        backgroundColor: Colors.purple.withOpacity(0.1),
                        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      );
                    }).toList(),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title, IconData icon, Color color) {
    return Row(
      children: [
        Icon(icon, size: 16, color: color),
        const SizedBox(width: 6),
        Text(
          title,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 13,
            color: color,
          ),
        ),
      ],
    );
  }
}
