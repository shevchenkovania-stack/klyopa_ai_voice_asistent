import 'package:flutter/material.dart';
import 'tool_tester_shared.dart';

/// Вкладка "Медиа" — медиа-плеер, Play Store, WiFi, локальная музыка
class MediaTab extends StatefulWidget {
  const MediaTab({super.key});
  @override
  State<MediaTab> createState() => _MediaTabState();
}

class _MediaTabState extends State<MediaTab> {
  String? _status;
  bool _busy = false;
  bool _isPlaying = false;
  final _playStoreSearch = TextEditingController(text: 'WhatsApp');
  final _playStorePackage = TextEditingController(text: 'com.whatsapp');
  final _wifiSsid = TextEditingController(text: 'Home_WiFi');
  final _localMusic = TextEditingController(text: 'любимая песня');

  @override
  void dispose() {
    _playStoreSearch.dispose();
    _playStorePackage.dispose();
    _wifiSsid.dispose();
    _localMusic.dispose();
    super.dispose();
  }

  Future<void> _exec(String toolName, Map<String, dynamic> params) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _status = '⏳ Выполняется...';
    });
    try {
      final result = await invokeTool(toolName, params);
      final success = result?['success'] == true;
      final message = result?['message'] as String? ?? 'Нет ответа';
      setState(() {
        _status = success ? '✓ $message' : '✗ $message';
        if (toolName == 'media_control') {
          final action = params['action'] ?? '';
          if (action == 'play' || action == 'resume') _isPlaying = true;
          if (action == 'pause' || action == 'stop') _isPlaying = false;
        }
      });
    } catch (e) {
      setState(() => _status = '✗ Ошибка: $e');
    }
    setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // КНОПКА: Запустить все тесты
          ElevatedButton.icon(
            onPressed: _busy
                ? null
                : () async {
                    debugPrint('[MediaTab] Нажата кнопка запуска тестов');
                    setState(() {
                      _busy = true;
                      _status = 'Запуск 70 автоматических тестов...';
                    });
                    try {
                      debugPrint('[MediaTab] Вызов runAllToolTests...');
                      final result = await kEngineChannel
                          .invokeMethod('runAllToolTests');
                      debugPrint('[MediaTab] Получен результат: ${result?.runtimeType}');
                      
                      if (result != null && result is List) {
                        final passed = result.where((t) => t['passed'] == true).length;
                        final total = result.length;
                        debugPrint('[MediaTab] Результаты: $passed/$total');
                        setState(() {
                          _status = 'Результат: $passed/$total тестов пройдено';
                        });
                        // Показываем детальный отчёт
                        showDialog(
                          context: context,
                          builder: (context) => AlertDialog(
                            title: Text('Результаты тестов'),
                            content: SingleChildScrollView(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text('Всего: $total', style: TextStyle(fontWeight: FontWeight.bold)),
                                  Text('Прошло: $passed', style: TextStyle(color: Colors.green)),
                                  Text('Не прошло: ${total - passed}', style: TextStyle(color: Colors.red)),
                                  const SizedBox(height: 16),
                                  ...result.map((t) => Padding(
                                    padding: const EdgeInsets.only(bottom: 8),
                                    child: Row(
                                      children: [
                                        Icon(
                                          t['passed'] == true ? Icons.check_circle : Icons.cancel,
                                          color: t['passed'] == true ? Colors.green : Colors.red,
                                          size: 20,
                                        ),
                                        const SizedBox(width: 8),
                                        Expanded(
                                          child: Column(
                                            crossAxisAlignment: CrossAxisAlignment.start,
                                            children: [
                                              Text(t['toolName'] ?? '', style: TextStyle(fontWeight: FontWeight.bold)),
                                              Text(t['details'] ?? '', style: TextStyle(fontSize: 12)),
                                            ],
                                          ),
                                        ),
                                      ],
                                    ),
                                  )),
                                ],
                              ),
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(context),
                                child: const Text('Закрыть'),
                              ),
                            ],
                          ),
                        );
                      }
                    } catch (e, stackTrace) {
                      debugPrint('[MediaTab] ОШИБКА: $e');
                      debugPrint('[MediaTab] StackTrace: $stackTrace');
                      setState(() {
                        _status = 'Ошибка: $e';
                      });
                    }
                    setState(() {
                      _busy = false;
                    });
                  },
            icon: const Icon(Icons.play_arrow),
            label: const Text('🧪 Запустить все 70 тестов'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
          ),
          const SizedBox(height: 16),
          if (_status != null) buildStatusBanner(_status!),
          if (_status != null) const SizedBox(height: 12),
          _buildPlayer(),
          const SizedBox(height: 12),
          _buildPlayStore(),
          const SizedBox(height: 12),
          _buildWifi(),
          const SizedBox(height: 12),
          _buildLocalMusic(),
        ],
      ),
    );
  }

  Widget _buildPlayer() {
    return Material(
      color: Colors.grey.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.play_circle_outline,
                  color: _isPlaying ? Colors.green : Colors.grey,
                  size: 28,
                ),
                const SizedBox(width: 12),
                const Text(
                  'Медиа плеер',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
                const Spacer(),
                if (_isPlaying)
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 2,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.green.withOpacity(0.2),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Text(
                      '▶ Играет',
                      style: TextStyle(fontSize: 11, color: Colors.green),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                _btn(
                  Icons.skip_previous,
                  'Назад',
                  () => _exec('media_control', {'action': 'previous'}),
                ),
                _btn(
                  Icons.play_arrow,
                  'Играть',
                  () => _exec('media_control', {'action': 'play'}),
                ),
                _btn(
                  Icons.pause,
                  'Пауза',
                  () => _exec('media_control', {'action': 'pause'}),
                ),
                _btn(
                  Icons.stop,
                  'Стоп',
                  () => _exec('media_control', {'action': 'stop'}),
                ),
                _btn(
                  Icons.skip_next,
                  'Далее',
                  () => _exec('media_control', {'action': 'next'}),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPlayStore() {
    return Material(
      color: Colors.grey.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.store, color: Colors.green, size: 24),
                SizedBox(width: 12),
                Text(
                  'Play Store',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
              ],
            ),
            const SizedBox(height: 12),
            buildTextField(
              _playStoreSearch,
              'Поиск приложения...',
              Icons.search,
              onSubmitted: (v) {
                if (v.isNotEmpty) _exec('play_store_search', {'query': v});
              },
            ),
            const SizedBox(height: 8),
            buildTextField(
              _playStorePackage,
              'Package name (com.app.name)',
              Icons.inventory_2,
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _busy
                        ? null
                        : () {
                            final q = _playStoreSearch.text.trim();
                            if (q.isNotEmpty)
                              _exec('play_store_search', {'query': q});
                          },
                    icon: const Icon(Icons.search),
                    label: const Text('Найти'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _busy
                        ? null
                        : () {
                            final p = _playStorePackage.text.trim();
                            if (p.isNotEmpty)
                              _exec('open_play_store', {'package_name': p});
                          },
                    icon: const Icon(Icons.open_in_new),
                    label: const Text(
                      'Открыть',
                      style: TextStyle(fontSize: 12),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green.withOpacity(0.6),
                      foregroundColor: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildWifi() {
    return Material(
      color: Colors.grey.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.wifi, color: Colors.cyan, size: 24),
                SizedBox(width: 12),
                Text(
                  'Подключиться к WiFi',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
              ],
            ),
            const SizedBox(height: 12),
            buildTextField(_wifiSsid, 'Название сети (SSID)...', Icons.wifi),
            const SizedBox(height: 12),
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () {
                      final s = _wifiSsid.text.trim();
                      if (s.isNotEmpty) _exec('connect_wifi', {'ssid': s});
                    },
              icon: const Icon(Icons.wifi),
              label: const Text('Подключиться'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.cyan,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLocalMusic() {
    return Material(
      color: Colors.grey.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(Icons.music_note, color: Colors.pink, size: 24),
                SizedBox(width: 12),
                Text(
                  'Локальная музыка',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
              ],
            ),
            const SizedBox(height: 12),
            buildTextField(
              _localMusic,
              'Поиск трека (или пусто = вся музыка)...',
              Icons.search,
              onSubmitted: (v) =>
                  _exec('search_local_music', {'query': v, 'limit': 20}),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _busy
                        ? null
                        : () {
                            final q = _localMusic.text.trim();
                            _exec('search_local_music', {
                              'query': q,
                              'limit': 20,
                            });
                          },
                    icon: const Icon(Icons.search),
                    label: Text(
                      _localMusic.text.trim().isEmpty ? 'Вся музыка' : 'Найти',
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.pink,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton.icon(
                  onPressed: _busy
                      ? null
                      : () {
                          _localMusic.clear();
                          _exec('search_local_music', {'limit': 50});
                        },
                  icon: const Icon(Icons.library_music),
                  label: const Text('Все MP3', style: TextStyle(fontSize: 12)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.pink.withOpacity(0.6),
                    foregroundColor: Colors.white,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            // Тест: Найти и играть Lose Yourself
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Ищу Lose Yourself...';
                      });
                      try {
                        final result = await invokeTool('search_local_music', {
                          'query': 'Lose Yourself',
                          'limit': 5,
                        });
                        final success = result?['success'] == true;
                        final message = result?['message'] as String? ?? '';
                        if (!success || message.contains('No songs found')) {
                          setState(
                            () => _status =
                                '✗ Не нашёл Lose Yourself на устройстве',
                          );
                          setState(() => _busy = false);
                          return;
                        }
                        final pathMatch = RegExp(
                          r'Path: (.+)',
                        ).firstMatch(message);
                        if (pathMatch == null) {
                          setState(
                            () => _status = '✗ Не удалось извлечь путь к файлу',
                          );
                          setState(() => _busy = false);
                          return;
                        }
                        final path = pathMatch.group(1)!.trim();
                        setState(() => _status = '⏳ Нашёл! Запускаю...');
                        final playResult = await invokeTool('media_control', {
                          'action': 'play_file',
                          'path': path,
                        });
                        final playSuccess = playResult?['success'] == true;
                        final playMessage =
                            playResult?['message'] as String? ?? '';
                        setState(
                          () => _status = playSuccess
                              ? '✓ Играет: $path'
                              : '✗ Ошибка воспроизведения: $playMessage',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.play_circle_fill),
              label: const Text('🧪 ТЕСТ: Найти и играть Lose Yourself'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.deepPurple,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // Тест: Полный пайплайн
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Тест пайплайна...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runMusicTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Тест пройден! Ответ: $response'
                              : '✗ Тест провален: $response',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.science),
              label: const Text('🧪 ТЕСТ: Полный пайплайн (текст → агент)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.orange,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // Тест: Напоминания (AlarmManager + push)
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Ставлю тестовое напоминание на 1 минуту...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runReminderTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final triggerAt =
                            result?['trigger_at'] as String? ?? '?';
                        final msg =
                            result?['message'] as String? ?? '';
                        setState(
                          () => _status = status == 'scheduled'
                              ? '✓ Напоминание поставлено на $triggerAt. $msg'
                              : '✗ Не удалось: $msg',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.notifications_active),
              label: const Text('🔔 ТЕСТ: Напоминание через 1 минуту'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // Тест: Голосовой — агент должен вызвать set_reminder
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Голосовой тест: "напомни через минуту выключить чайник"...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runReminderVoiceTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Агент ответил: $response\n$hint'
                              : '⚠ Статус: $status. Ответ: $response\n$hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.record_voice_over),
              label: const Text('🎤 ТЕСТ: Голосовой (напомни через минуту)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // АВТОТЕСТ: полная проверка AlarmManager (10 сек → срабатывание)
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Автотест: ставлю напоминание на 10 сек...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runReminderAutoTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final id =
                            result?['reminder_id'] as String? ?? '?';
                        final time =
                            result?['scheduled_at'] as String? ?? '?';
                        final wait =
                            result?['wait_seconds'] as String? ?? '?';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Автотест завершён (напоминание #$id на $time, ждал ${wait}с)\n$hint'
                              : '✗ Автотест провален: $hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка автотеста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.auto_awesome),
              label: const Text('🤖 АВТОТЕСТ: Напоминание (10 сек → сработает)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // ТЕСТ: Найти заправку (навигация)
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Тест: "найди заправку поблизости"...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runNavigationVoiceTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Агент ответил: $response\n$hint'
                              : '⚠ Статус: $status. Ответ: $response\n$hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.local_gas_station),
              label: const Text('⛽ ТЕСТ: Найти заправку (навигация)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.orange,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // ТЕСТ: Построить маршрут до Кишинёв центр
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Тест: "построй маршрут до Кишинёв центр"...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runRouteVoiceTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Агент ответил: $response\n$hint'
                              : '⚠ Статус: $status. Ответ: $response\n$hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.directions),
              label: const Text('🗺️ ТЕСТ: Маршрут до Кишинёв центр'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // ТЕСТ: Ответить на SMS
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Тест: "ответь на смс привет"...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runReplySmsVoiceTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Агент ответил: $response\n$hint'
                              : '⚠ Статус: $status. Ответ: $response\n$hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.reply),
              label: const Text('💬 ТЕСТ: Ответить на SMS'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // ТЕСТ: Скриншот экрана
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Тест: "сделай скриншот экрана"...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runScreenshotVoiceTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Агент ответил: $response\n$hint'
                              : '⚠ Статус: $status. Ответ: $response\n$hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.screenshot),
              label: const Text('📸 ТЕСТ: Скриншот экрана'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.purple,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // ТЕСТ: Объяснить экран
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Тест: "объясни что на экране"...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runExplainScreenVoiceTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Агент ответил: $response\n$hint'
                              : '⚠ Статус: $status. Ответ: $response\n$hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.question_answer),
              label: const Text('🔍 ТЕСТ: Объяснить экран'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.indigo,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            // ТЕСТ: Отправить координаты
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () async {
                      setState(() {
                        _busy = true;
                        _status = '⏳ Тест: "отправь мои координаты"...';
                      });
                      try {
                        final result = await kEngineChannel
                            .invokeMethod<Map<dynamic, dynamic>>(
                              'runShareLocationVoiceTest',
                            );
                        final status =
                            result?['status'] as String? ?? 'unknown';
                        final response =
                            result?['agent_response'] as String? ?? '';
                        final hint =
                            result?['hint'] as String? ?? '';
                        setState(
                          () => _status = status == 'done'
                              ? '✓ Агент ответил: $response\n$hint'
                              : '⚠ Статус: $status. Ответ: $response\n$hint',
                        );
                      } catch (e) {
                        setState(() => _status = '✗ Ошибка теста: $e');
                      }
                      setState(() => _busy = false);
                    },
              icon: const Icon(Icons.location_on),
              label: const Text('📍 ТЕСТ: Отправить координаты'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.teal,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _btn(IconData icon, String label, VoidCallback onTap) {
    return Column(
      children: [
        IconButton(
          onPressed: _busy ? null : onTap,
          icon: Icon(icon, size: 28),
          color: Colors.green,
        ),
        Text(label, style: const TextStyle(fontSize: 10)),
      ],
    );
  }
}
