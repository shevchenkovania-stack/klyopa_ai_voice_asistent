import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/domain/repositories/i_history_repository.dart';
import 'package:ai_voice_agent/domain/entities/voice_command.dart';

/// Журнал длинного контента, который Клёпа рассказал/приготовил словами:
/// истории, сказки, анекдоты, рецепты, факты, советы. Берём из той же Hive-истории,
/// что и «История команд», но показываем ПОЛНЫЙ текст и только содержательные ответы,
/// чтобы можно было перечитать рассказ или рецепт спокойно.
class JournalScreen extends StatefulWidget {
  const JournalScreen({super.key});

  @override
  State<JournalScreen> createState() => _JournalScreenState();
}

class _JournalScreenState extends State<JournalScreen> {
  List<VoiceCommand> _all = [];
  List<VoiceCommand> _shown = [];
  bool _loading = true;
  String _filter = 'all'; // all | story | fairy | joke | recipe | fact | advice

  static const _categories = <String, (String, IconData, Color)>{
    'story':   ('История',  Icons.auto_stories,     Colors.indigo),
    'fairy':   ('Сказка',   Icons.nightlight,       Colors.deepPurple),
    'joke':    ('Анекдот',  Icons.emoji_emotions,   Colors.orange),
    'recipe':  ('Рецепт',   Icons.restaurant,       Colors.brown),
    'fact':    ('Факт',     Icons.lightbulb,        Colors.teal),
    'advice':  ('Совет',    Icons.psychology,       Colors.blueGrey),
  };

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final history = sl<IHistoryRepository>();
    final commands = await history.getHistory(limit: 500);
    _all = commands.where((c) => _classify(c).isNotEmpty).toList();
    _applyFilter();
    if (mounted) setState(() => _loading = false);
  }

  void _applyFilter() {
    setState(() {
      _shown = _filter == 'all'
          ? _all
          : _all.where((c) => _classify(c) == _filter).toList();
    });
  }

  /// Категория по запросу + длине ответа. Пустая строка = не контент (болтовня).
  String _classify(VoiceCommand c) {
    final q = c.rawText.toLowerCase();
    final a = c.response;
    bool has(List<String> keys) => keys.any(q.contains);

    if (has(['рецеп', 'как приготовить', 'приготовить', 'блюдо', 'приготов',
             'маринад', 'замес', 'выпек', 'суп из', 'что поесть', 'что готовить'])) {
      return 'recipe';
    }
    if (has(['сказк', 'колыбельн', 'на ночь', 'сон', 'жили были'])) return 'fairy';
    if (has(['анекдот', 'шутк', 'прикол', 'смешн', 'развесели'])) return 'joke';
    if (has(['факт', 'а ты знал', 'знал ли ты', 'интересн', 'удивит', 'расскажи что нибудь интересн'])) {
      return 'fact';
    }
    if (has(['истори', 'рассказ', 'расса', 'предан', 'былин', 'случа', 'байк', 'легенд'])) {
      return 'story';
    }
    if (has(['совет', 'как мне', 'что делать', 'подскажи как', 'что посовету'])) return 'advice';

    // Не попали под ключ, но ответ явно развёрнутый — считаем рассказом.
    if (a.length >= 220) return 'story';
    return '';
  }

  String _fmtDate(DateTime d) {
    String two(int v) => v.toString().padLeft(2, '0');
    return '${two(d.day)}.${two(d.month)} ${two(d.hour)}:${two(d.minute)}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Журнал'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Обновить',
            onPressed: _load,
          ),
        ],
        bottom: _loading
            ? null
            : PreferredSize(
                preferredSize: const Size.fromHeight(52),
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  children: [
                    _chip('all', 'Всё', Icons.apps),
                    ..._categories.entries.map((e) =>
                        _chip(e.key, e.value.$1, e.value.$2)),
                  ],
                ),
              ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _shown.isEmpty
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.auto_stories,
                            size: 64,
                            color: Theme.of(context).colorScheme.outline),
                        const SizedBox(height: 16),
                        Text(
                          _filter == 'all'
                              ? 'Пока пусто. Попроси Клёпу: «расскажи историю», «поставь рецепт», «расскажи сказку» — и тут появится текст.'
                              : 'В этой категории пока ничего нет.',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ],
                    ),
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: _shown.length,
                  itemBuilder: (context, i) => _card(_shown[i]),
                ),
    );
  }

  Widget _chip(String key, String label, IconData icon) {
    final selected = _filter == key;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        selected: selected,
        onSelected: (_) {
          setState(() => _filter = key);
          _applyFilter();
        },
        avatar: Icon(icon, size: 18),
        label: Text(label),
      ),
    );
  }

  Widget _card(VoiceCommand c) {
    final cat = _classify(c);
    final meta = _categories[cat];
    final color = meta?.$3 ?? Theme.of(context).colorScheme.primary;

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6),
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
          leading: CircleAvatar(
            backgroundColor: color.withValues(alpha: 0.15),
            child: Icon(meta?.$2 ?? Icons.article, color: color),
          ),
          title: Text(
            c.rawText,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          subtitle: Text('${meta?.$1 ?? cat} • ${_fmtDate(c.timestamp)}'),
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton.icon(
                      onPressed: () => _copy(c.response),
                      icon: const Icon(Icons.copy, size: 18),
                      label: const Text('Копировать'),
                    ),
                  ),
                  const SizedBox(height: 4),
                  SelectableText(
                    c.response,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.4),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _copy(String text) async {
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Скопировано в буфер обмена')),
    );
  }
}
