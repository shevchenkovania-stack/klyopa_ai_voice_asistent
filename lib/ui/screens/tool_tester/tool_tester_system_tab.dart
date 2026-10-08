import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'tool_tester_shared.dart';

/// Вкладка "Система" — контроль, будильники, доступность, файлы
class SystemTab extends StatefulWidget {
  const SystemTab({super.key});
  @override
  State<SystemTab> createState() => _SystemTabState();
}

class _SystemTabState extends State<SystemTab> {
  // System Control state
  bool _wifiOn = false;
  bool _bluetoothOn = false;
  bool _flashlightOn = false;
  double _volumeLevel = 50;
  double _brightnessLevel = 128;
  String? _sysControlStatus;
  bool _sysControlBusy = false;
  int _lastVolumeApplied = 50;
  int _lastBrightnessApplied = 128;
  bool _hasWriteSettingsPermission = false;

  // System tools state
  String? _sysStatus;
  bool _sysBusy = false;
  final _alarmHourController = TextEditingController(text: '08');
  final _alarmMinuteController = TextEditingController(text: '00');
  final _alarmLabelController = TextEditingController(text: 'Тест будильника');
  final _alarmIdController = TextEditingController(text: '1');
  final _timerSecondsController = TextEditingController(text: '60');
  final _timerLabelController = TextEditingController(text: 'Тест таймера');
  final _accessTextController = TextEditingController(text: 'Привет мир');
  final _accessClickTextController = TextEditingController(text: 'Слушать');
  final _accessClickIndexController = TextEditingController(text: '0');
  final _filePathController = TextEditingController(text: '/sdcard/test.txt');
  final _fileContentController = TextEditingController(text: 'Привет мир! Это тестовый файл.');
  final _fileListPathController = TextEditingController(text: '/sdcard/');
  final _fileDownloadUrlController = TextEditingController(text: 'https://example.com/file.txt');
  final _fileDownloadNameController = TextEditingController(text: 'downloaded.txt');

  @override
  void initState() {
    super.initState();
    _checkWriteSettingsPermission();
  }

  @override
  void dispose() {
    _alarmHourController.dispose();
    _alarmMinuteController.dispose();
    _alarmLabelController.dispose();
    _alarmIdController.dispose();
    _timerSecondsController.dispose();
    _timerLabelController.dispose();
    _accessTextController.dispose();
    _accessClickTextController.dispose();
    _accessClickIndexController.dispose();
    _filePathController.dispose();
    _fileContentController.dispose();
    _fileListPathController.dispose();
    _fileDownloadUrlController.dispose();
    _fileDownloadNameController.dispose();
    super.dispose();
  }

  Future<void> _checkWriteSettingsPermission() async {
    final status = await Permission.systemAlertWindow.status;
    setState(() => _hasWriteSettingsPermission = status.isGranted);
  }

  Future<void> _requestWriteSettingsPermission() async {
    await openAppSettings();
    await Future.delayed(const Duration(seconds: 1));
    _checkWriteSettingsPermission();
  }

  Future<void> _execSystemControl(String action, {int? value}) async {
    if (_sysControlBusy) return;
    setState(() {
      _sysControlBusy = true;
      _sysControlStatus = '⏳ Выполняется...';
    });

    final params = <String, dynamic>{'action': action};
    if (value != null) params['value'] = value;

    debugPrint('[SystemControl] Вызов: action=$action, value=$value');

    try {
      final result = await invokeTool('system_control', params);
      debugPrint('[SystemControl] Результат: $result');
      final success = result?['success'] == true;
      final message = result?['message'] as String? ?? 'Нет ответа';

      setState(() {
        _sysControlStatus = success ? '✓ $message' : '✗ $message';
        switch (action) {
          case 'toggle_wifi':
            _wifiOn = !_wifiOn;
            break;
          case 'toggle_bluetooth':
            _bluetoothOn = !_bluetoothOn;
            break;
          case 'toggle_flashlight':
            _flashlightOn = !_flashlightOn;
            break;
        }
      });
    } catch (e) {
      debugPrint('[SystemControl] Ошибка: $e');
      setState(() => _sysControlStatus = '✗ Ошибка: $e');
    }

    setState(() => _sysControlBusy = false);
  }

  Future<void> _execSystemTool(
    String toolName,
    Map<String, dynamic> params,
  ) async {
    if (_sysBusy) return;
    setState(() {
      _sysBusy = true;
      _sysStatus = '⏳ Выполняется...';
    });
    try {
      final result = await invokeTool(toolName, params);
      final success = result?['success'] == true;
      final message = result?['message'] as String? ?? 'Нет ответа';
      setState(() => _sysStatus = success ? '✓ $message' : '✗ $message');
    } catch (e) {
      setState(() => _sysStatus = '✗ Ошибка: $e');
    }
    setState(() => _sysBusy = false);
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Permission Banner
          if (!_hasWriteSettingsPermission)
            Container(
              margin: const EdgeInsets.only(bottom: 16),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.orange.withOpacity(0.15),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.orange.withOpacity(0.4)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.warning, color: Colors.orange, size: 20),
                      const SizedBox(width: 8),
                      const Expanded(
                        child: Text(
                          'Для яркости нужно разрешение',
                          style: TextStyle(
                            color: Colors.orange,
                            fontWeight: FontWeight.bold,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  ElevatedButton.icon(
                    onPressed: _requestWriteSettingsPermission,
                    icon: const Icon(Icons.settings, size: 16),
                    label: const Text('Открыть настройки'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.orange,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 6,
                      ),
                      textStyle: const TextStyle(fontSize: 12),
                    ),
                  ),
                ],
              ),
            ),

          // Status
          if (_sysControlStatus != null)
            Container(
              margin: const EdgeInsets.only(bottom: 16),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: _sysControlStatus!.startsWith('✓')
                    ? Colors.green.withOpacity(0.15)
                    : _sysControlStatus!.contains('WRITE_SETTINGS')
                    ? Colors.orange.withOpacity(0.15)
                    : Colors.red.withOpacity(0.15),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: _sysControlStatus!.startsWith('✓')
                      ? Colors.green.withOpacity(0.4)
                      : _sysControlStatus!.contains('WRITE_SETTINGS')
                      ? Colors.orange.withOpacity(0.4)
                      : Colors.red.withOpacity(0.4),
                ),
              ),
              child: Text(
                _sysControlStatus!,
                style: TextStyle(
                  color: _sysControlStatus!.startsWith('✓')
                      ? Colors.green
                      : _sysControlStatus!.contains('WRITE_SETTINGS')
                      ? Colors.orange
                      : Colors.red,
                  fontSize: 13,
                ),
              ),
            ),

          // WiFi Toggle
          _buildSystemTile(
            icon: Icons.wifi,
            title: 'WiFi',
            subtitle: _wifiOn ? 'Включён' : 'Выключен',
            active: _wifiOn,
            onTap: () => _execSystemControl('toggle_wifi'),
          ),
          const SizedBox(height: 8),

          // Bluetooth Toggle
          _buildSystemTile(
            icon: Icons.bluetooth,
            title: 'Bluetooth',
            subtitle: _bluetoothOn ? 'Включён' : 'Выключен',
            active: _bluetoothOn,
            onTap: () => _execSystemControl('toggle_bluetooth'),
          ),
          const SizedBox(height: 8),

          // Flashlight Toggle
          _buildSystemTile(
            icon: Icons.flashlight_on,
            title: 'Фонарик',
            subtitle: _flashlightOn ? 'Включён' : 'Выключен',
            active: _flashlightOn,
            onTap: () => _execSystemControl('toggle_flashlight'),
          ),
          const SizedBox(height: 16),

          // Volume Slider
          _buildSliderTile(
            icon: Icons.volume_up,
            title: 'Громкость',
            value: _volumeLevel,
            label: '${_volumeLevel.round()}%',
            onChanged: (v) {
              setState(() => _volumeLevel = v);
              final rounded = v.round();
              if (rounded != _lastVolumeApplied && !_sysControlBusy) {
                _lastVolumeApplied = rounded;
                _execSystemControl('set_volume', value: rounded);
              }
            },
            onApply: () =>
                _execSystemControl('set_volume', value: _volumeLevel.round()),
          ),
          const SizedBox(height: 12),

          // Brightness Slider
          _buildSliderTile(
            icon: Icons.brightness_6,
            title: 'Яркость',
            value: _brightnessLevel,
            max: 255,
            label: '${_brightnessLevel.round()}',
            onChanged: (v) {
              setState(() => _brightnessLevel = v);
              final rounded = v.round();
              if (rounded != _lastBrightnessApplied && !_sysControlBusy) {
                _lastBrightnessApplied = rounded;
                _execSystemControl('set_brightness', value: rounded);
              }
            },
            onApply: () => _execSystemControl(
              'set_brightness',
              value: _brightnessLevel.round(),
            ),
          ),
          const SizedBox(height: 24),

          // Status Banner
          if (_sysStatus != null) buildStatusBanner(_sysStatus!),
          if (_sysStatus != null) const SizedBox(height: 12),

          // Будильники и таймеры
          const Divider(),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              'Будильники и таймеры',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: Colors.grey[400],
              ),
            ),
          ),
          const SizedBox(height: 8),

          // Set Alarm
          _buildSystemCard(
            icon: Icons.alarm,
            color: Colors.orange,
            title: 'Установить будильник',
            children: [
              Row(
                children: [
                  Expanded(
                    child: buildTextField(
                      _alarmHourController,
                      'Часы (0-23)',
                      Icons.schedule,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: buildTextField(
                      _alarmMinuteController,
                      'Минуты (0-59)',
                      Icons.timer,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              buildTextField(
                _alarmLabelController,
                'Название (необязательно)',
                Icons.label,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () {
                        final h = int.tryParse(_alarmHourController.text) ?? 8;
                        final m =
                            int.tryParse(_alarmMinuteController.text) ?? 0;
                        _execSystemTool('set_alarm', {
                          'hour': h,
                          'minute': m,
                          'label': _alarmLabelController.text.isEmpty
                              ? null
                              : _alarmLabelController.text,
                        });
                      },
                icon: const Icon(Icons.alarm_add),
                label: const Text('Установить'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.orange,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Cancel Alarm
          _buildSystemCard(
            icon: Icons.alarm_off,
            color: Colors.red,
            title: 'Отменить будильник',
            children: [
              buildTextField(
                _alarmIdController,
                'Название или ID будильника',
                Icons.tag,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () {
                        final id = _alarmIdController.text;
                        _execSystemTool('cancel_alarm', {
                          if (id.isNotEmpty) 'label': id,
                        });
                      },
                icon: const Icon(Icons.delete),
                label: const Text('Отменить'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Set Timer
          _buildSystemCard(
            icon: Icons.timer,
            color: Colors.blue,
            title: 'Установить таймер',
            children: [
              buildTextField(_timerSecondsController, 'Секунды', Icons.timer),
              const SizedBox(height: 8),
              buildTextField(
                _timerLabelController,
                'Название (необязательно)',
                Icons.label,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () {
                        final sec =
                            int.tryParse(_timerSecondsController.text) ?? 60;
                        _execSystemTool('set_timer', {
                          'seconds': sec,
                          'label': _timerLabelController.text.isEmpty
                              ? null
                              : _timerLabelController.text,
                        });
                      },
                icon: const Icon(Icons.play_arrow),
                label: const Text('Запустить'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Cancel Timer
          _buildSystemCard(
            icon: Icons.timer_off,
            color: Colors.grey,
            title: 'Отменить таймер',
            children: [
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('cancel_timer', {}),
                icon: const Icon(Icons.stop),
                label: const Text('Отменить все таймеры'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.grey[700],
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // Доступность
          const Divider(),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              'Доступность',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: Colors.grey[400],
              ),
            ),
          ),
          const SizedBox(height: 8),

          // Read Screen
          _buildSystemCard(
            icon: Icons.visibility,
            color: Colors.purple,
            title: 'Прочитать экран',
            children: [
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('read_screen', {}),
                icon: const Icon(Icons.read_more),
                label: const Text('Прочитать содержимое экрана'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.purple,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Click Element
          _buildSystemCard(
            icon: Icons.touch_app,
            color: Colors.indigo,
            title: 'Нажать на элемент',
            children: [
              buildTextField(
                _accessClickTextController,
                'Текст элемента',
                Icons.text_fields,
              ),
              const SizedBox(height: 8),
              buildTextField(
                _accessClickIndexController,
                'Индекс (если несколько)',
                Icons.format_list_numbered,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () {
                        final idx =
                            int.tryParse(_accessClickIndexController.text) ?? 0;
                        _execSystemTool('click_element', {
                          'text': _accessClickTextController.text,
                          'index': idx,
                        });
                      },
                icon: const Icon(Icons.ads_click),
                label: const Text('Нажать'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.indigo,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Type Text
          _buildSystemCard(
            icon: Icons.keyboard,
            color: Colors.teal,
            title: 'Ввести текст',
            children: [
              buildTextField(
                _accessTextController,
                'Текст для ввода',
                Icons.edit,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('type_text', {
                        'text': _accessTextController.text,
                      }),
                icon: const Icon(Icons.send),
                label: const Text('Ввести'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.teal,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Navigate
          _buildSystemCard(
            icon: Icons.navigation,
            color: Colors.cyan,
            title: 'Навигация',
            children: [
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _sysBusy
                          ? null
                          : () =>
                                _execSystemTool('navigate', {'action': 'back'}),
                      icon: const Icon(Icons.arrow_back),
                      label: const Text('Назад'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.cyan,
                        foregroundColor: Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _sysBusy
                          ? null
                          : () =>
                                _execSystemTool('navigate', {'action': 'home'}),
                      icon: const Icon(Icons.home),
                      label: const Text('Домой'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.cyan,
                        foregroundColor: Colors.white,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('navigate', {'action': 'recent'}),
                icon: const Icon(Icons.recent_actors),
                label: const Text('Недавние приложения'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.cyan,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Scroll
          _buildSystemCard(
            icon: Icons.swipe,
            color: Colors.amber,
            title: 'Прокрутка',
            children: [
              Row(
                children: [
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _sysBusy
                          ? null
                          : () =>
                                _execSystemTool('scroll', {'direction': 'up'}),
                      icon: const Icon(Icons.arrow_upward),
                      label: const Text('Вверх'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.amber,
                        foregroundColor: Colors.black,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: _sysBusy
                          ? null
                          : () => _execSystemTool('scroll', {
                              'direction': 'down',
                            }),
                      icon: const Icon(Icons.arrow_downward),
                      label: const Text('Вниз'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.amber,
                        foregroundColor: Colors.black,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),

          // List Clickable
          _buildSystemCard(
            icon: Icons.list,
            color: Colors.deepPurple,
            title: 'Список кликабельных элементов',
            children: [
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('list_clickable', {}),
                icon: const Icon(Icons.view_list),
                label: const Text('Показать все элементы'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.deepPurple,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // Файлы
          const Divider(),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              'Файлы',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: Colors.grey[400],
              ),
            ),
          ),
          const SizedBox(height: 8),

          // Create File
          _buildSystemCard(
            icon: Icons.note_add,
            color: Colors.green,
            title: 'Создать файл',
            children: [
              buildTextField(
                _filePathController,
                'Путь (например /sdcard/test.txt)',
                Icons.folder,
              ),
              const SizedBox(height: 8),
              buildTextField(
                _fileContentController,
                'Содержимое файла',
                Icons.edit_note,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('create_file', {
                        'path': _filePathController.text,
                        'content': _fileContentController.text,
                      }),
                icon: const Icon(Icons.add),
                label: const Text('Создать'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.green,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Read File
          _buildSystemCard(
            icon: Icons.article,
            color: Colors.blue,
            title: 'Прочитать файл',
            children: [
              buildTextField(_filePathController, 'Путь к файлу', Icons.folder),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('read_file', {
                        'path': _filePathController.text,
                      }),
                icon: const Icon(Icons.visibility),
                label: const Text('Прочитать'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Write File
          _buildSystemCard(
            icon: Icons.edit,
            color: Colors.orange,
            title: 'Записать в файл',
            children: [
              buildTextField(_filePathController, 'Путь к файлу', Icons.folder),
              const SizedBox(height: 8),
              buildTextField(
                _fileContentController,
                'Содержимое',
                Icons.edit_note,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('write_file', {
                        'path': _filePathController.text,
                        'content': _fileContentController.text,
                      }),
                icon: const Icon(Icons.save),
                label: const Text('Записать'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.orange,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // List Files
          _buildSystemCard(
            icon: Icons.folder_open,
            color: Colors.cyan,
            title: 'Список файлов',
            children: [
              buildTextField(
                _fileListPathController,
                'Путь (пусто = /sdcard)',
                Icons.folder,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('list_files', {
                        'path': _fileListPathController.text.isEmpty
                            ? '/sdcard'
                            : _fileListPathController.text,
                      }),
                icon: const Icon(Icons.list),
                label: const Text('Показать'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.cyan,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Delete File
          _buildSystemCard(
            icon: Icons.delete_forever,
            color: Colors.red,
            title: 'Удалить файл',
            children: [
              buildTextField(_filePathController, 'Путь к файлу', Icons.folder),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('delete_file', {
                        'path': _filePathController.text,
                      }),
                icon: const Icon(Icons.delete),
                label: const Text('Удалить'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Create Folder
          _buildSystemCard(
            icon: Icons.create_new_folder,
            color: Colors.teal,
            title: 'Создать папку',
            children: [
              buildTextField(_filePathController, 'Путь к папке', Icons.folder),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('create_folder', {
                        'path': _filePathController.text,
                      }),
                icon: const Icon(Icons.add_circle),
                label: const Text('Создать'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.teal,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Open File
          _buildSystemCard(
            icon: Icons.open_in_new,
            color: Colors.indigo,
            title: 'Открыть файл',
            children: [
              buildTextField(_filePathController, 'Путь к файлу', Icons.folder),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('open_file', {
                        'path': _filePathController.text,
                      }),
                icon: const Icon(Icons.open_with),
                label: const Text('Открыть'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.indigo,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // File Info
          _buildSystemCard(
            icon: Icons.info,
            color: Colors.purple,
            title: 'Информация о файле',
            children: [
              buildTextField(_filePathController, 'Путь к файлу', Icons.folder),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('file_info', {
                        'path': _filePathController.text,
                      }),
                icon: const Icon(Icons.info_outline),
                label: const Text('Инфо'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.purple,
                  foregroundColor: Colors.white,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Download File
          _buildSystemCard(
            icon: Icons.download,
            color: Colors.amber,
            title: 'Скачать файл',
            children: [
              buildTextField(
                _fileDownloadUrlController,
                'URL файла',
                Icons.link,
              ),
              const SizedBox(height: 8),
              buildTextField(
                _fileDownloadNameController,
                'Имя файла (необязательно)',
                Icons.save_as,
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _sysBusy
                    ? null
                    : () => _execSystemTool('download_file', {
                        'url': _fileDownloadUrlController.text,
                        'filename': _fileDownloadNameController.text.isEmpty
                            ? null
                            : _fileDownloadNameController.text,
                      }),
                icon: const Icon(Icons.cloud_download),
                label: const Text('Скачать'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.amber,
                  foregroundColor: Colors.black,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSystemCard({
    required IconData icon,
    required Color color,
    required String title,
    required List<Widget> children,
  }) {
    return Card(
      color: Colors.grey[900],
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(icon, color: color, size: 22),
                const SizedBox(width: 8),
                Text(
                  title,
                  style: TextStyle(
                    color: color,
                    fontSize: 15,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            ...children,
          ],
        ),
      ),
    );
  }

  Widget _buildSystemTile({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool active,
    required VoidCallback onTap,
  }) {
    return Material(
      color: active
          ? Colors.blue.withOpacity(0.15)
          : Colors.grey.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: _sysControlBusy ? null : onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(
            children: [
              Icon(icon, color: active ? Colors.blue : Colors.grey, size: 28),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                        color: active ? Colors.blue : Colors.white,
                      ),
                    ),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: active ? Colors.blue[300] : Colors.grey,
                      ),
                    ),
                  ],
                ),
              ),
              if (_sysControlBusy)
                const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              else
                Switch(
                  value: active,
                  onChanged: (_) => onTap(),
                  activeColor: Colors.blue,
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSliderTile({
    required IconData icon,
    required String title,
    required double value,
    required String label,
    required ValueChanged<double> onChanged,
    required VoidCallback onApply,
    double max = 100,
  }) {
    return Material(
      color: Colors.grey.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Column(
          children: [
            Row(
              children: [
                Icon(icon, color: Colors.orange, size: 24),
                const SizedBox(width: 12),
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
                  ),
                ),
                const Spacer(),
                Text(
                  label,
                  style: TextStyle(
                    color: Colors.orange,
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: Slider(
                    value: value,
                    min: 0,
                    max: max,
                    activeColor: Colors.orange,
                    onChanged: onChanged,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.send, color: Colors.orange, size: 20),
                  onPressed: _sysControlBusy ? null : onApply,
                  tooltip: 'Применить',
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
