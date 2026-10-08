import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:ai_voice_agent/core/debug/debug_log_service.dart';

/// Debug log panel widget - shows real-time logs in app
class DebugLogPanel extends StatefulWidget {
  const DebugLogPanel({super.key});

  @override
  State<DebugLogPanel> createState() => _DebugLogPanelState();
}

class _DebugLogPanelState extends State<DebugLogPanel> {
  final _scrollController = ScrollController();
  LogType? _filter;

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  Color _getTypeColor(LogType type) {
    switch (type) {
      case LogType.info:
        return Colors.blue;
      case LogType.success:
        return Colors.green;
      case LogType.warning:
        return Colors.orange;
      case LogType.error:
        return Colors.red;
      case LogType.api:
        return Colors.purple;
      case LogType.voice:
        return Colors.teal;
      case LogType.tool:
        return Colors.amber;
    }
  }

  List<LogEntry> _getFilteredLogs(List<LogEntry> logs) {
    if (_filter == null) return logs;
    return logs.where((l) => l.type == _filter).toList();
  }

  /// Format logs as text for sharing
  String _formatLogsAsText() {
    final logs = _getFilteredLogs(DebugLogService().logs);
    final buffer = StringBuffer();
    buffer.writeln('=== AI Voice Agent Logs ===');
    buffer.writeln('Time: ${DateTime.now().toIso8601String()}');
    buffer.writeln('Filter: ${_filter?.name ?? 'All'}');
    buffer.writeln('Total: ${logs.length} entries');
    buffer.writeln('');
    
    for (final entry in logs.reversed) {
      buffer.writeln('[${entry.timeStr}] ${entry.typeIcon} [${entry.tag}] ${entry.message}');
      if (entry.details != null) {
        buffer.writeln('  └─ ${entry.details}');
      }
    }
    
    return buffer.toString();
  }

  /// Share logs via system share sheet
  void _shareLogs() {
    final text = _formatLogsAsText();
    Share.share(text, subject: 'AI Voice Agent Logs');
  }

  /// Copy logs to clipboard
  void _copyLogs() {
    final text = _formatLogsAsText();
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Логи скопированы в буфер обмена'),
        duration: Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black.withOpacity(0.95),
      child: Column(
        children: [
          // Header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: Colors.grey[900],
              border: Border(
                bottom: BorderSide(color: Colors.grey[800]!),
              ),
            ),
            child: Row(
              children: [
                const Text(
                  '🔍 Debug Logs',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                // Filter chips
                _buildFilterChip(null, 'All', Colors.grey),
                _buildFilterChip(LogType.error, '❌', Colors.red),
                _buildFilterChip(LogType.api, '🌐', Colors.purple),
                _buildFilterChip(LogType.voice, '🎤', Colors.teal),
                _buildFilterChip(LogType.tool, '🔧', Colors.amber),
                const SizedBox(width: 8),
                // Share button
                IconButton(
                  icon: const Icon(Icons.share, color: Colors.cyan, size: 18),
                  onPressed: _shareLogs,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  tooltip: 'Поделиться логами',
                ),
                const SizedBox(width: 8),
                // Copy button
                IconButton(
                  icon: const Icon(Icons.copy, color: Colors.cyan, size: 18),
                  onPressed: _copyLogs,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                  tooltip: 'Копировать логи',
                ),
                const SizedBox(width: 8),
                // Clear button
                IconButton(
                  icon: const Icon(Icons.delete_outline, color: Colors.grey, size: 18),
                  onPressed: () => DebugLogService().clear(),
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            ),
          ),
          // Log list
          Expanded(
            child: ListenableBuilder(
              listenable: DebugLogService(),
              builder: (context, _) {
                final logs = _getFilteredLogs(DebugLogService().logs);
                if (logs.isEmpty) {
                  return const Center(
                    child: Text(
                      'No logs yet',
                      style: TextStyle(color: Colors.grey, fontSize: 12),
                    ),
                  );
                }
                return ListView.builder(
                  controller: _scrollController,
                  itemCount: logs.length,
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  itemBuilder: (context, index) {
                    final entry = logs[index];
                    return _buildLogEntry(entry);
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(LogType? type, String label, Color color) {
    final isSelected = _filter == type;
    return Padding(
      padding: const EdgeInsets.only(left: 4),
      child: InkWell(
        onTap: () => setState(() => _filter = type),
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: isSelected ? color.withOpacity(0.3) : Colors.transparent,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isSelected ? color : Colors.grey[700]!,
              width: 1,
            ),
          ),
          child: Text(
            label,
            style: TextStyle(
              color: isSelected ? color : Colors.grey[500],
              fontSize: 10,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLogEntry(LogEntry entry) {
    final typeColor = _getTypeColor(entry.type);
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: typeColor.withOpacity(0.08),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: typeColor.withOpacity(0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(entry.typeIcon, style: const TextStyle(fontSize: 12)),
              const SizedBox(width: 6),
              Text(
                entry.timeStr,
                style: TextStyle(
                  color: Colors.grey[500],
                  fontSize: 10,
                  fontFamily: 'monospace',
                ),
              ),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: typeColor.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  entry.tag,
                  style: TextStyle(
                    color: typeColor,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            entry.message,
            style: TextStyle(
              color: entry.type == LogType.error ? Colors.red[300] : Colors.white,
              fontSize: 12,
              fontFamily: 'monospace',
            ),
          ),
          if (entry.details != null) ...[
            const SizedBox(height: 4),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.3),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                entry.details!,
                style: TextStyle(
                  color: Colors.grey[400],
                  fontSize: 10,
                  fontFamily: 'monospace',
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
