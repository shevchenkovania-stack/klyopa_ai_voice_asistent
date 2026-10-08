import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:convert';

/// Экран истории результатов тестов
/// Показывает все прошлые тестирования с деталями
class TestHistoryScreen extends StatefulWidget {
  const TestHistoryScreen({super.key});

  @override
  State<TestHistoryScreen> createState() => _TestHistoryScreenState();
}

class _TestHistoryScreenState extends State<TestHistoryScreen> {
  List<TestSession> _sessions = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    setState(() => _loading = true);
    try {
      debugPrint('[TestHistory] Загрузка истории...');
      final result = await const MethodChannel(
        'com.aiagent.ai_voice_agent/engine',
      ).invokeMethod('loadTestHistory');

      debugPrint(
        '[TestHistory] Получен результат: ${result?.toString().substring(0, 100)}...',
      );

      if (result != null) {
        final List<dynamic> data = jsonDecode(result);
        debugPrint('[TestHistory] Распарсено ${data.length} сессий');
        setState(() {
          _sessions = data.map((json) => TestSession.fromJson(json)).toList();
          _loading = false;
        });
      } else {
        debugPrint('[TestHistory] Результат null');
        setState(() => _loading = false);
      }
    } catch (e, stackTrace) {
      debugPrint('[TestHistory] ОШИБКА ЗАГРУЗКИ: $e');
      debugPrint('[TestHistory] StackTrace: $stackTrace');
      if (mounted) {
        setState(() => _loading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Ошибка загрузки: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 5),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('📊 История тестов'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _loadHistory),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _sessions.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.history, size: 64, color: Colors.grey[400]),
                  const SizedBox(height: 16),
                  Text(
                    'История пуста',
                    style: TextStyle(fontSize: 18, color: Colors.grey[600]),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Запустите тесты чтобы увидеть результаты',
                    style: TextStyle(color: Colors.grey[500]),
                  ),
                ],
              ),
            )
          : RefreshIndicator(
              onRefresh: _loadHistory,
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: _sessions.length,
                itemBuilder: (context, index) {
                  final session = _sessions[index];
                  return _buildSessionCard(session);
                },
              ),
            ),
    );
  }

  Widget _buildSessionCard(TestSession session) {
    final date = DateTime.fromMillisecondsSinceEpoch(session.timestamp);
    final passRate = session.totalTests > 0
        ? (session.passed * 100 / session.totalTests).round()
        : 0;

    Color statusColor;
    String statusIcon;
    switch (session.status) {
      case 'completed':
        statusColor = Colors.green;
        statusIcon = '✅';
        break;
      case 'crashed':
        statusColor = Colors.red;
        statusIcon = '💥';
        break;
      default:
        statusColor = Colors.orange;
        statusIcon = '⏳';
    }

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () => _showSessionDetails(session),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Заголовок
              Row(
                children: [
                  Text(statusIcon, style: const TextStyle(fontSize: 24)),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${date.day}.${date.month.toString().padLeft(2, '0')}.${date.year} ${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          'v${session.apkVersion} (build ${session.buildNumber})',
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey[600],
                          ),
                        ),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: statusColor.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '$passRate%',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: statusColor,
                      ),
                    ),
                  ),
                ],
              ),
              const Divider(height: 24),
              // Статистика
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _buildStat(
                    'Всего',
                    session.totalTests.toString(),
                    Colors.blue,
                  ),
                  _buildStat('✅', session.passed.toString(), Colors.green),
                  _buildStat('❌', session.failed.toString(), Colors.red),
                  _buildStat('⏭️', session.skipped.toString(), Colors.grey),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStat(String label, String value, Color color) {
    return Column(
      children: [
        Text(
          value,
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        const SizedBox(height: 4),
        Text(label, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
      ],
    );
  }

  void _showSessionDetails(TestSession session) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return DraggableScrollableSheet(
          initialChildSize: 0.9,
          minChildSize: 0.5,
          maxChildSize: 0.95,
          expand: false,
          builder: (context, scrollController) {
            return Container(
              padding: const EdgeInsets.all(16),
              child: SingleChildScrollView(
                controller: scrollController,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Заголовок
                    Row(
                      children: [
                        const Text(
                          'Детали теста',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const Spacer(),
                        IconButton(
                          icon: const Icon(Icons.share),
                          onPressed: () => _exportSession(session),
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete, color: Colors.red),
                          onPressed: () => _deleteSession(session),
                        ),
                      ],
                    ),
                    const Divider(),
                    // Информация
                    Text('Дата: ${_formatDate(session.timestamp)}'),
                    Text('Версия: ${session.apkVersion}'),
                    Text('Сборка: ${session.buildNumber}'),
                    Text('Статус: ${session.status}'),
                    const SizedBox(height: 16),
                    // Результаты
                    ...session.results.map(
                      (result) => _buildResultTile(result),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildResultTile(TestResult result) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      color: result.passed
          ? Colors.green.withOpacity(0.1)
          : Colors.red.withOpacity(0.1),
      child: ListTile(
        leading: Icon(
          result.passed ? Icons.check_circle : Icons.cancel,
          color: result.passed ? Colors.green : Colors.red,
        ),
        title: Text(
          result.toolName,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: Text(result.details),
        trailing: Text(
          result.category,
          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
        ),
      ),
    );
  }

  Future<void> _exportSession(TestSession session) async {
    debugPrint('[TestHistory] Экспорт сессии ${session.id}...');
    String report;
    try {
      debugPrint('[TestHistory] Вызов exportTestSession...');
      final result = await const MethodChannel(
        'com.aiagent.ai_voice_agent/engine',
      ).invokeMethod('exportTestSession', {'sessionId': session.id});

      debugPrint(
        '[TestHistory] Результат: ${result?.toString().length ?? "null"} символов',
      );
      report = result?.toString() ?? '';
    } catch (e, stackTrace) {
      debugPrint('[TestHistory] ❌ ОШИБКА: $e');
      debugPrint('[TestHistory] StackTrace: $stackTrace');
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Ошибка: $e')));
      }
      return;
    }

    if (report.isEmpty) {
      debugPrint('[TestHistory] ⚠️ Отчёт пустой');
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('⚠️ Отчёт пустой')));
      }
      return;
    }

    if (!mounted) return;

    // Показываем отчёт в видимом диалоге ПОВЕРХ всего (видно даже над bottom sheet).
    // Кнопка копирования сразу меняется на "✅ Скопировано!" — подтверждение всегда видно.
    await showDialog<void>(
      context: context,
      builder: (dialogContext) {
        bool copied = false;
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Text('📋 Отчёт о тестировании'),
              content: SizedBox(
                width: double.maxFinite,
                child: SingleChildScrollView(
                  child: SelectableText(
                    report,
                    style: const TextStyle(
                      fontSize: 12,
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  child: const Text('Закрыть'),
                ),
                FilledButton.icon(
                  icon: Icon(copied ? Icons.check : Icons.copy),
                  label: Text(copied ? '✅ Скопировано!' : 'Копировать'),
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: report));
                    debugPrint('[TestHistory] ✅ Скопировано в буфер');
                    setDialogState(() => copied = true);
                  },
                ),
              ],
            );
          },
        );
      },
    );
  }

  Future<void> _deleteSession(TestSession session) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Удалить сессию?'),
        content: Text(
          'Удалить результаты от ${_formatDate(session.timestamp)}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Отмена'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Удалить'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try {
        await const MethodChannel(
          'com.aiagent.ai_voice_agent/engine',
        ).invokeMethod('deleteTestSession', {'sessionId': session.id});
        _loadHistory();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Ошибка: $e')));
        }
      }
    }
  }

  String _formatDate(int timestamp) {
    final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
    return '${date.day}.${date.month.toString().padLeft(2, '0')}.${date.year} ${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
  }
}

/// Модель данных для сессии теста
class TestSession {
  final String id;
  final int timestamp;
  final String apkVersion;
  final int buildNumber;
  final int totalTests;
  final int passed;
  final int failed;
  final int skipped;
  final List<TestResult> results;
  final String status;

  TestSession({
    required this.id,
    required this.timestamp,
    required this.apkVersion,
    required this.buildNumber,
    required this.totalTests,
    required this.passed,
    required this.failed,
    required this.skipped,
    required this.results,
    required this.status,
  });

  factory TestSession.fromJson(Map<String, dynamic> json) {
    return TestSession(
      id: json['id'],
      timestamp: json['timestamp'],
      apkVersion: json['apkVersion'],
      buildNumber: json['buildNumber'],
      totalTests: json['totalTests'],
      passed: json['passed'],
      failed: json['failed'],
      skipped: json['skipped'] ?? 0,
      results: (json['results'] as List)
          .map((r) => TestResult.fromJson(r))
          .toList(),
      status: json['status'],
    );
  }
}

/// Модель данных для результата одного теста
class TestResult {
  final String toolName;
  final String category;
  final bool passed;
  final String details;
  final int timestamp;

  TestResult({
    required this.toolName,
    required this.category,
    required this.passed,
    required this.details,
    required this.timestamp,
  });

  factory TestResult.fromJson(Map<String, dynamic> json) {
    return TestResult(
      toolName: json['toolName'],
      category: json['category'],
      passed: json['passed'],
      details: json['details'],
      timestamp: json['timestamp'],
    );
  }
}
