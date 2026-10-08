import 'package:flutter/material.dart';
import 'tool_tester_shared.dart';

/// Вкладка "Связь" — SMS, звонки, контакты, email, URL, шаринг
class CommTab extends StatefulWidget {
  const CommTab({super.key});
  @override
  State<CommTab> createState() => _CommTabState();
}

class _CommTabState extends State<CommTab> {
  String? _status;
  bool _busy = false;
  final _smsPhone = TextEditingController(text: '076083715');
  final _smsMessage = TextEditingController(text: 'Привет! Это тестовое сообщение');
  final _callPhone = TextEditingController(text: '076083715');
  final _contactSearch = TextEditingController(text: 'Мама');
  final _smsReadCount = TextEditingController(text: '5');
  final _smsSearchQuery = TextEditingController(text: 'привет');
  final _launchUrl = TextEditingController(text: 'https://999.md');
  final _shareText = TextEditingController(text: 'Привет! Попробуй Клёпу - лучший голосовой ассистент!');
  final _emailTo = TextEditingController(text: 'test@example.com');
  final _emailSubject = TextEditingController(text: 'Тестовое письмо от Клёпы');
  final _emailBody = TextEditingController(text: 'Привет! Это тестовое письмо отправленное через Клёпу.');

  @override
  void dispose() {
    _smsPhone.dispose();
    _smsMessage.dispose();
    _callPhone.dispose();
    _contactSearch.dispose();
    _smsReadCount.dispose();
    _smsSearchQuery.dispose();
    _launchUrl.dispose();
    _shareText.dispose();
    _emailTo.dispose();
    _emailSubject.dispose();
    _emailBody.dispose();
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
      setState(() => _status = success ? '✓ $message' : '✗ $message');
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
          if (_status != null) buildStatusBanner(_status!),
          if (_status != null) const SizedBox(height: 12),
          _card(Icons.sms, Colors.blue, 'Отправить SMS', [
            buildTextField(_smsPhone, 'Номер телефона...', Icons.phone),
            const SizedBox(height: 8),
            buildTextField(_smsMessage, 'Текст сообщения...', Icons.message),
            const SizedBox(height: 12),
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () => _exec('send_sms', {
                      'phone': _smsPhone.text,
                      'message': _smsMessage.text,
                    }),
              icon: const Icon(Icons.send),
              label: const Text('Отправить SMS'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
              ),
            ),
          ]),
          const SizedBox(height: 12),
          _card(Icons.call, Colors.green, 'Позвонить', [
            buildTextField(_callPhone, 'Номер телефона...', Icons.phone),
            const SizedBox(height: 12),
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () => _exec('make_call', {'phone': _callPhone.text}),
              icon: const Icon(Icons.call),
              label: const Text('Позвонить'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green,
                foregroundColor: Colors.white,
              ),
            ),
          ]),
          const SizedBox(height: 12),
          _card(Icons.contacts, Colors.orange, 'Поиск контактов', [
            buildTextField(
              _contactSearch,
              'Имя контакта...',
              Icons.person,
              onSubmitted: (v) {
                if (v.isNotEmpty) _exec('search_contacts', {'query': v});
              },
            ),
            const SizedBox(height: 12),
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () {
                      final q = _contactSearch.text.trim();
                      if (q.isNotEmpty) _exec('search_contacts', {'query': q});
                    },
              icon: const Icon(Icons.search),
              label: const Text('Найти'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.orange,
                foregroundColor: Colors.white,
              ),
            ),
          ]),
          const SizedBox(height: 12),
          _card(Icons.inbox, Colors.teal, 'Прочитать SMS', [
            Row(
              children: [
                Expanded(
                  child: buildTextField(_smsReadCount, 'Кол-во', Icons.numbers),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _busy
                      ? null
                      : () => _exec('read_sms', {
                          'count': int.tryParse(_smsReadCount.text) ?? 5,
                        }),
                  child: const Text('Читать'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.teal,
                    foregroundColor: Colors.white,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            buildTextField(
              _smsSearchQuery,
              'Поиск в SMS...',
              Icons.search,
              onSubmitted: (v) {
                if (v.isNotEmpty)
                  _exec('search_sms', {'query': v, 'limit': 10});
              },
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _busy
                  ? null
                  : () {
                      final q = _smsSearchQuery.text.trim();
                      if (q.isNotEmpty)
                        _exec('search_sms', {'query': q, 'limit': 10});
                    },
              child: const Text('Искать в SMS'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.teal.withOpacity(0.7),
                foregroundColor: Colors.white,
              ),
            ),
          ]),
          const SizedBox(height: 12),
          _card(Icons.link, Colors.indigo, 'Открыть URL / Поделиться', [
            buildTextField(
              _launchUrl,
              'URL (google.com)...',
              Icons.link,
              onSubmitted: (v) {
                if (v.isNotEmpty) _exec('launch_url', {'url': v});
              },
            ),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _busy
                  ? null
                  : () {
                      final u = _launchUrl.text.trim();
                      if (u.isNotEmpty) _exec('launch_url', {'url': u});
                    },
              child: const Text('Открыть'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.indigo,
                foregroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            buildTextField(_shareText, 'Текст для шаринга...', Icons.share),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _busy
                  ? null
                  : () {
                      final t = _shareText.text.trim();
                      if (t.isNotEmpty) _exec('share_text', {'text': t});
                    },
              child: const Text('Поделиться'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.indigo.withOpacity(0.7),
                foregroundColor: Colors.white,
              ),
            ),
          ]),
          const SizedBox(height: 12),
          _card(Icons.email, Colors.red, 'Email', [
            buildTextField(_emailTo, 'Кому (email)...', Icons.alternate_email),
            const SizedBox(height: 8),
            buildTextField(_emailSubject, 'Тема...', Icons.subject),
            const SizedBox(height: 8),
            buildTextField(_emailBody, 'Текст письма...', Icons.article),
            const SizedBox(height: 12),
            ElevatedButton.icon(
              onPressed: _busy
                  ? null
                  : () => _exec('send_email', {
                      'to': _emailTo.text,
                      'subject': _emailSubject.text,
                      'body': _emailBody.text,
                    }),
              icon: const Icon(Icons.send),
              label: const Text('Отправить Email'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red,
                foregroundColor: Colors.white,
              ),
            ),
          ]),
        ],
      ),
    );
  }

  Widget _card(
    IconData icon,
    Color color,
    String title,
    List<Widget> children,
  ) {
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
                Icon(icon, color: color, size: 24),
                const SizedBox(width: 12),
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 15,
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
}
