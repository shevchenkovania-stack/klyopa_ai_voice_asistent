import 'package:flutter/material.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/domain/repositories/i_history_repository.dart';
import 'package:ai_voice_agent/domain/entities/voice_command.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  List<VoiceCommand> _commands = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    final history = sl<IHistoryRepository>();
    final commands = await history.getHistory();
    setState(() {
      _commands = commands;
      _loading = false;
    });
  }

  Future<void> _clearHistory() async {
    final history = sl<IHistoryRepository>();
    await history.clearHistory();
    setState(() => _commands = []);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('История команд'),
        actions: [
          if (_commands.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.delete_sweep),
              onPressed: () async {
                final confirm = await showDialog<bool>(
                  context: context,
                  builder: (ctx) => AlertDialog(
                    title: const Text('Очистить историю?'),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Нет')),
                      TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Да')),
                    ],
                  ),
                );
                if (confirm == true) await _clearHistory();
              },
            ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _commands.isEmpty
              ? const Center(child: Text('Пока нет команд'))
              : ListView.builder(
                  itemCount: _commands.length,
                  itemBuilder: (context, index) {
                    final cmd = _commands[index];
                    return ListTile(
                      leading: Icon(
                        cmd.actionId != null ? Icons.play_circle : Icons.chat,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                      title: Text(cmd.rawText, maxLines: 1, overflow: TextOverflow.ellipsis),
                      subtitle: Text(cmd.response, maxLines: 2, overflow: TextOverflow.ellipsis),
                      trailing: Text(
                        '${cmd.timestamp.hour}:${cmd.timestamp.minute.toString().padLeft(2, '0')}',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    );
                  },
                ),
    );
  }
}
