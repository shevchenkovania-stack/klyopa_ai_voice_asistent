import 'package:flutter/material.dart';
import 'tool_tester_shared.dart';

/// Вкладка "Ядро" — время, агент, поиск, браузер, рисование
class CoreTab extends StatefulWidget {
  const CoreTab({super.key});
  @override
  State<CoreTab> createState() => _CoreTabState();
}

class _CoreTabState extends State<CoreTab> {
  String? _status;
  bool _busy = false;
  bool _agentVisible = true;
  final _searchController = TextEditingController(text: 'рецепт борща');
  final _browseUrlController = TextEditingController(text: '999.md');
  final _browseSearchController = TextEditingController(text: 'новости');
  String _selectedShape = 'heart';
  String _selectedColor = 'red';
  String _selectedBgColor = 'white';
  String _selectedPosition = 'center';
  bool _sceneMode = false;
  final List<Map<String, String>> _sceneElements = [];
  bool _dotTestBusy = false;
  List<Map<String, dynamic>> _dotTestResults = [];

  static const _shapes = [
    'smiley',
    'sun',
    'heart',
    'star',
    'person',
    'house',
    'tree',
    'flower',
    'snowman',
    'circle',
    'rectangle',
  ];
  static const _colors = [
    'red',
    'blue',
    'green',
    'yellow',
    'white',
    'black',
    'orange',
    'purple',
    'pink',
    'cyan',
  ];
  static const _positions = [
    'top_left',
    'top_center',
    'top_right',
    'middle_left',
    'center',
    'middle_right',
    'bottom_left',
    'bottom_center',
    'bottom_right',
  ];
  static const _positionLabels = {
    'top_left': '↖',
    'top_center': '↑',
    'top_right': '↗',
    'middle_left': '←',
    'center': '●',
    'middle_right': '→',
    'bottom_left': '↙',
    'bottom_center': '↓',
    'bottom_right': '↘',
  };

  @override
  void dispose() {
    _searchController.dispose();
    _browseUrlController.dispose();
    _browseSearchController.dispose();
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
        if (toolName == 'self_awareness') {
          _agentVisible = params['action'] == 'show';
        }
      });
    } catch (e) {
      setState(() => _status = '✗ Ошибка: $e');
    }
    setState(() => _busy = false);
  }

  Future<void> _runDotArtTest() async {
    if (_dotTestBusy) return;
    setState(() {
      _dotTestBusy = true;
      _dotTestResults = [];
    });
    try {
      final results = await kEngineChannel.invokeMethod<List<dynamic>>(
        'runDotArtTest',
      );
      if (results != null) {
        setState(() {
          _dotTestResults = results
              .whereType<Map>()
              .map((m) => Map<String, dynamic>.from(m))
              .toList();
        });
      }
    } catch (e) {
      setState(() {
        _dotTestResults = [
          {'scenario': 'Ошибка', 'passed': false, 'details': '$e'},
        ];
      });
    }
    setState(() => _dotTestBusy = false);
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Status
          if (_status != null) buildStatusBanner(_status!),
          if (_status != null) const SizedBox(height: 12),

          // 1. Current Time
          Material(
            color: Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: ListTile(
              leading: const Icon(
                Icons.access_time,
                color: Colors.blue,
                size: 28,
              ),
              title: const Text('Текущее время'),
              subtitle: const Text('Показать дату и время устройства'),
              trailing: _busy
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : IconButton(
                      icon: const Icon(Icons.play_arrow),
                      onPressed: () => _exec('get_current_time', {}),
                    ),
            ),
          ),
          const SizedBox(height: 12),

          // 2. Self Awareness
          Material(
            color: _agentVisible
                ? Colors.blue.withOpacity(0.15)
                : Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: _busy
                  ? null
                  : () => _exec('self_awareness', {
                      'action': _agentVisible ? 'hide' : 'show',
                    }),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 14,
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.visibility,
                      color: _agentVisible ? Colors.blue : Colors.grey,
                      size: 28,
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Агент на экране',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                              color: _agentVisible ? Colors.blue : Colors.white,
                            ),
                          ),
                          Text(
                            _agentVisible ? 'Видим' : 'Скрыт',
                            style: TextStyle(
                              fontSize: 12,
                              color: _agentVisible
                                  ? Colors.blue[300]
                                  : Colors.grey,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (_busy)
                      const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    else
                      Switch(
                        value: _agentVisible,
                        onChanged: (_) => _exec('self_awareness', {
                          'action': _agentVisible ? 'hide' : 'show',
                        }),
                        activeColor: Colors.blue,
                      ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 3. Web Search
          Material(
            color: Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.search, color: Colors.orange, size: 24),
                      SizedBox(width: 12),
                      Text(
                        'Поиск в интернете',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _searchController,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      hintText: 'Поисковый запрос...',
                      hintStyle: TextStyle(color: Colors.grey[600]),
                      filled: true,
                      fillColor: Colors.grey.withOpacity(0.2),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                      suffixIcon: IconButton(
                        icon: const Icon(Icons.send, color: Colors.orange),
                        onPressed: _busy
                            ? null
                            : () {
                                final q = _searchController.text.trim();
                                if (q.isNotEmpty)
                                  _exec('web_search', {'query': q});
                              },
                      ),
                    ),
                    onSubmitted: (v) {
                      if (v.trim().isNotEmpty)
                        _exec('web_search', {'query': v.trim()});
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 4. Browse Website
          Material(
            color: Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.language, color: Colors.teal, size: 24),
                      SizedBox(width: 12),
                      Text(
                        'Зайти на сайт',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _browseUrlController,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      hintText: 'URL сайта (например 999.md, example.com)...',
                      hintStyle: TextStyle(color: Colors.grey[600]),
                      filled: true,
                      fillColor: Colors.grey.withOpacity(0.2),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                      prefixIcon: const Icon(
                        Icons.link,
                        color: Colors.teal,
                        size: 20,
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: _browseSearchController,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      hintText: 'Что искать на странице (опционально)...',
                      hintStyle: TextStyle(color: Colors.grey[600]),
                      filled: true,
                      fillColor: Colors.grey.withOpacity(0.2),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                      prefixIcon: const Icon(
                        Icons.search,
                        color: Colors.teal,
                        size: 20,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _busy
                              ? null
                              : () {
                                  final url = _browseUrlController.text.trim();
                                  if (url.isNotEmpty)
                                    _exec('browse_website', {
                                      'url': url,
                                      'search_query':
                                          _browseSearchController.text
                                              .trim()
                                              .isEmpty
                                          ? null
                                          : _browseSearchController.text.trim(),
                                    });
                                },
                          icon: const Icon(Icons.open_in_browser),
                          label: const Text('Открыть'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.teal,
                            foregroundColor: Colors.white,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      ElevatedButton.icon(
                        onPressed: _busy
                            ? null
                            : () {
                                final url = _browseUrlController.text.trim();
                                if (url.isNotEmpty)
                                  _exec('find_links', {
                                    'url': url,
                                    'filter':
                                        _browseSearchController.text
                                            .trim()
                                            .isEmpty
                                        ? null
                                        : _browseSearchController.text.trim(),
                                  });
                              },
                        icon: const Icon(Icons.link),
                        label: const Text(
                          'Ссылки',
                          style: TextStyle(fontSize: 12),
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.teal.withOpacity(0.6),
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          // 5. Draw Image
          Material(
            color: Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.brush, color: Colors.purple, size: 24),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Text(
                          'Нарисовать',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 15,
                          ),
                        ),
                      ),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            'Сцена',
                            style: TextStyle(
                              fontSize: 11,
                              color: _sceneMode ? Colors.purple : Colors.grey,
                            ),
                          ),
                          Switch(
                            value: _sceneMode,
                            onChanged: (_) =>
                                setState(() => _sceneMode = !_sceneMode),
                            activeColor: Colors.purple,
                          ),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Фигура:',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: _shapes
                        .map(
                          (s) => ChoiceChip(
                            label: Text(
                              s,
                              style: const TextStyle(fontSize: 11),
                            ),
                            selected: _selectedShape == s,
                            onSelected: (_) =>
                                setState(() => _selectedShape = s),
                            selectedColor: Colors.purple.withOpacity(0.3),
                            backgroundColor: Colors.grey.withOpacity(0.2),
                          ),
                        )
                        .toList(),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Цвет:',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: _colors
                        .map(
                          (c) => ChoiceChip(
                            label: Text(
                              c,
                              style: const TextStyle(fontSize: 11),
                            ),
                            selected: _selectedColor == c,
                            onSelected: (_) =>
                                setState(() => _selectedColor = c),
                            selectedColor: Colors.purple.withOpacity(0.3),
                            backgroundColor: Colors.grey.withOpacity(0.2),
                          ),
                        )
                        .toList(),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Фон:',
                    style: TextStyle(fontSize: 12, color: Colors.grey),
                  ),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: _colors
                        .map(
                          (c) => ChoiceChip(
                            label: Text(
                              c,
                              style: const TextStyle(fontSize: 11),
                            ),
                            selected: _selectedBgColor == c,
                            onSelected: (_) =>
                                setState(() => _selectedBgColor = c),
                            selectedColor: Colors.purple.withOpacity(0.3),
                            backgroundColor: Colors.grey.withOpacity(0.2),
                          ),
                        )
                        .toList(),
                  ),
                  const SizedBox(height: 8),
                  if (!_sceneMode) ...[
                    const Text(
                      'Позиция:',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    const SizedBox(height: 4),
                    Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      children: _positions
                          .map(
                            (p) => ChoiceChip(
                              label: Text(
                                _positionLabels[p] ?? p,
                                style: const TextStyle(fontSize: 14),
                              ),
                              selected: _selectedPosition == p,
                              onSelected: (_) =>
                                  setState(() => _selectedPosition = p),
                              selectedColor: Colors.purple.withOpacity(0.3),
                              backgroundColor: Colors.grey.withOpacity(0.2),
                              tooltip: p,
                            ),
                          )
                          .toList(),
                    ),
                    const SizedBox(height: 12),
                    Center(
                      child: ElevatedButton.icon(
                        onPressed: _busy
                            ? null
                            : () => _exec('draw_image', {
                                'shape': _selectedShape,
                                'color': _selectedColor,
                                'bg_color': _selectedBgColor,
                                'position': _selectedPosition,
                              }),
                        icon: const Icon(Icons.draw),
                        label: const Text('Нарисовать'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.purple,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ),
                  ],
                  if (_sceneMode) ...[
                    const SizedBox(height: 8),
                    const Text(
                      'Элементы сцены:',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    const SizedBox(height: 4),
                    if (_sceneElements.isEmpty)
                      const Text(
                        'Пусто — добавь объекты',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                    ..._sceneElements.asMap().entries.map((entry) {
                      final i = entry.key;
                      final el = entry.value;
                      return Container(
                        margin: const EdgeInsets.only(bottom: 4),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.purple.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Row(
                          children: [
                            Text(
                              '${el['shape']} @ ${el['position']}',
                              style: const TextStyle(fontSize: 12),
                            ),
                            const Spacer(),
                            IconButton(
                              icon: const Icon(
                                Icons.close,
                                size: 16,
                                color: Colors.red,
                              ),
                              onPressed: () =>
                                  setState(() => _sceneElements.removeAt(i)),
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(),
                            ),
                          ],
                        ),
                      );
                    }),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton.icon(
                            onPressed: () => setState(
                              () => _sceneElements.add({
                                'shape': _selectedShape,
                                'color': _selectedColor,
                                'position': _selectedPosition,
                              }),
                            ),
                            icon: const Icon(Icons.add, size: 18),
                            label: const Text(
                              'Добавить',
                              style: TextStyle(fontSize: 12),
                            ),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.purple.withOpacity(0.5),
                              foregroundColor: Colors.white,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        ElevatedButton.icon(
                          onPressed: _busy || _sceneElements.isEmpty
                              ? null
                              : () {
                                  final elements = _sceneElements
                                      .map(
                                        (e) => {
                                          'shape': e['shape'],
                                          'color': e['color'],
                                          'position': e['position'],
                                        },
                                      )
                                      .toList();
                                  _exec('draw_image', {
                                    'elements': elements,
                                    'bg_color': _selectedBgColor,
                                  });
                                },
                          icon: const Icon(Icons.draw),
                          label: const Text(
                            'Рисуй!',
                            style: TextStyle(fontSize: 12),
                          ),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.purple,
                            foregroundColor: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 6. Dot-Art Test
          Material(
            color: Colors.orange.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(
                        Icons.bug_report,
                        color: Colors.orange,
                        size: 24,
                      ),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Text(
                          'Тест: Рисование точками',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 15,
                          ),
                        ),
                      ),
                      if (_dotTestBusy)
                        const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      else
                        IconButton(
                          icon: const Icon(
                            Icons.play_circle,
                            color: Colors.orange,
                          ),
                          onPressed: _runDotArtTest,
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    '4 сценария: цветок, сердце, смайлик, абстракция. Проверяет: файл создан, PNG валидный, точки нарисованы.',
                    style: TextStyle(fontSize: 11, color: Colors.grey),
                  ),
                  if (_dotTestResults.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    const Divider(),
                    ..._dotTestResults.map((r) {
                      final passed = r['passed'] == true;
                      final scenario = r['scenario'] ?? '?';
                      final details = r['details'] ?? '';
                      return Container(
                        margin: const EdgeInsets.only(bottom: 6),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 6,
                        ),
                        decoration: BoxDecoration(
                          color: passed
                              ? Colors.green.withOpacity(0.1)
                              : Colors.red.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(6),
                          border: Border.all(
                            color: passed
                                ? Colors.green.withOpacity(0.3)
                                : Colors.red.withOpacity(0.3),
                          ),
                        ),
                        child: Row(
                          children: [
                            Icon(
                              passed ? Icons.check_circle : Icons.cancel,
                              color: passed ? Colors.green : Colors.red,
                              size: 18,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                '$scenario: $details',
                                style: TextStyle(
                                  fontSize: 11,
                                  color: passed
                                      ? Colors.green.shade800
                                      : Colors.red.shade800,
                                ),
                              ),
                            ),
                          ],
                        ),
                      );
                    }),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
