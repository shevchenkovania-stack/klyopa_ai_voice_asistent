import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:share_plus/share_plus.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/agent/tools/tool_registry.dart';
import 'package:ai_voice_agent/agent/tools/base_tool.dart';

/// Test result for a tool
class _ToolTestResult {
  final String toolName;
  final bool success;
  final String message;
  final Duration duration;

  _ToolTestResult({
    required this.toolName,
    required this.success,
    required this.message,
    required this.duration,
  });
}

/// Tool tester screen - tests all registered tools
class ToolTesterScreen extends StatefulWidget {
  const ToolTesterScreen({super.key});

  @override
  State<ToolTesterScreen> createState() => _ToolTesterScreenState();
}

class _ToolTesterScreenState extends State<ToolTesterScreen> {
  final List<_ToolTestResult> _results = [];
  bool _isRunning = false;
  String? _currentTool;

  /// Test parameters for each tool (must match actual tool parameter names)
  static const Map<String, Map<String, dynamic>> _testParams = {
    'get_current_time': {},
    'create_file': {'filename': 'test.txt', 'content': 'Hello from tool tester!'},
    'read_file': {'filename': 'test.txt'},
    'web_search': {'query': 'Flutter documentation'},
    'get_weather': {'city': 'Moscow'},
    'get_currency_rate': {'from': 'USD', 'to': 'RUB'},
    'play_store_search': {'query': 'notes app'},
    'open_play_store': {'package_name': 'com.google.android.apps.maps'},
    'open_app': {'name': 'Chrome'},
    'send_sms': {'phone': '+1234567890', 'message': 'Test message'},
    'make_call': {'phone': '+1234567890'},
    'set_alarm': {'hour': 8, 'minute': 30, 'label': 'Test alarm'},
    'cancel_alarm': {},
    'set_timer': {'seconds': 10, 'label': 'Test timer'},
    'cancel_timer': {},
    'search_contacts': {'query': 'John'},
    'system_control': {'action': 'toggle_wifi'},
    'media_control': {'action': 'play'},
    'self_awareness': {'action': 'show'},
    'connect_wifi': {'ssid': 'TestNetwork'},
    // === Accessibility ===
    'read_screen': {},
    'click_element': {'text': 'Настройки'},
    'type_text': {'text': 'Hello from AI Agent'},
    'navigate': {'action': 'home'},
    'scroll': {'direction': 'down'},
    'list_clickable': {},
    // === Notifications ===
    'read_notifications': {},
    'dismiss_notification': {'app_name': 'Test'},
    // === NEW: File System ===
    'create_folder': {'name': 'test_folder'},
    'list_files': {'path': '/sdcard/Download'},
    'delete_file': {'path': '/sdcard/Documents/test_tool.txt'},
    'write_file': {'path': '/sdcard/Documents/test_tool.txt', 'content': 'Hello from AI OS!'},
    'open_file': {'path': '/sdcard/Download/test.txt'},
    'file_info': {'path': '/sdcard/Download'},
    // === NEW: Device Info ===
    'battery_info': {},
    'device_info': {},
    'storage_info': {},
    // === NEW: Control ===
    'clipboard_read': {},
    'clipboard_write': {'text': 'Hello from AI Agent test'},
    'volume_control': {'stream': 'media', 'level': '50'},
    'brightness': {'level': '50'},
    'flashlight': {'state': 'off'},
    // === NEW: Communication ===
    'launch_url': {'url': 'google.com'},
    'share_text': {'text': 'Test share from AI Agent'},
    'send_email': {'to': 'test@example.com', 'subject': 'Test', 'body': 'Hello from AI Agent'},
    // === NEW: System ===
    'list_apps': {'filter': '', 'limit': 10},
    'app_info': {'app_name': 'Chrome'},
    'network_info': {},
    // === Camera ===
    'take_selfie': {},
    'take_photo': {},
    // === SMS ===
    'read_sms': {'count': 5},
    'search_sms': {'query': 'привет', 'limit': 10},
    // === Drawing ===
    'draw_image': {'shape': 'heart', 'color': 'pink', 'filename': 'test_heart.png'},
    // === Download ===
    'download_file': {'url': 'https://www.soundhelix.com/sites/default/files/HearAndNow-SoundHelix-Song-1.mp3', 'folder': 'Music'},
    // === Local Music ===
    'search_local_music': {'query': 'test', 'limit': 5},
  };

  Future<void> _runAllTests() async {
    setState(() {
      _isRunning = true;
      _results.clear();
    });

    final registry = sl<ToolRegistry>();
    final toolNames = registry.names;

    for (final name in toolNames) {
      setState(() => _currentTool = name);
      
      final stopwatch = Stopwatch()..start();
      try {
        final params = _testParams[name] ?? {};
        final result = await registry.execute(name, params);
        stopwatch.stop();
        
        setState(() {
          _results.add(_ToolTestResult(
            toolName: name,
            success: result.success,
            message: result.message,
            duration: stopwatch.elapsed,
          ));
        });
      } catch (e) {
        stopwatch.stop();
        setState(() {
          _results.add(_ToolTestResult(
            toolName: name,
            success: false,
            message: 'Exception: $e',
            duration: stopwatch.elapsed,
          ));
        });
      }
      
      // Small delay between tests
      await Future.delayed(const Duration(milliseconds: 300));
    }

    setState(() {
      _isRunning = false;
      _currentTool = null;
    });
  }

  Future<void> _runSingleTest(String toolName) async {
    setState(() => _currentTool = toolName);
    
    final registry = sl<ToolRegistry>();
    final stopwatch = Stopwatch()..start();
    
    try {
      final params = _testParams[toolName] ?? {};
      final result = await registry.execute(toolName, params);
      stopwatch.stop();
      
      setState(() {
        _results.removeWhere((r) => r.toolName == toolName);
        _results.insert(0, _ToolTestResult(
          toolName: toolName,
          success: result.success,
          message: result.message,
          duration: stopwatch.elapsed,
        ));
      });
    } catch (e) {
      stopwatch.stop();
      setState(() {
        _results.removeWhere((r) => r.toolName == toolName);
        _results.insert(0, _ToolTestResult(
          toolName: toolName,
          success: false,
          message: 'Exception: $e',
          duration: stopwatch.elapsed,
        ));
      });
    }
    
    setState(() => _currentTool = null);
  }

  /// Format test results as text
  String _formatResultsAsText() {
    final buffer = StringBuffer();
    buffer.writeln('=== Tool Test Results ===');
    buffer.writeln('Time: ${DateTime.now().toIso8601String()}');
    buffer.writeln('');
    
    final passed = _results.where((r) => r.success).toList();
    final failed = _results.where((r) => !r.success).toList();
    
    buffer.writeln('SUMMARY: ${passed.length} passed, ${failed.length} failed');
    buffer.writeln('');
    
    if (failed.isNotEmpty) {
      buffer.writeln('=== FAILED TESTS ===');
      for (final r in failed) {
        buffer.writeln('❌ ${r.toolName} (${r.duration.inMilliseconds}ms)');
        buffer.writeln('   Error: ${r.message}');
        buffer.writeln('');
      }
    }
    
    if (passed.isNotEmpty) {
      buffer.writeln('=== PASSED TESTS ===');
      for (final r in passed) {
        buffer.writeln('✓ ${r.toolName} (${r.duration.inMilliseconds}ms)');
        buffer.writeln('  ${r.message}');
        buffer.writeln('');
      }
    }
    
    return buffer.toString();
  }

  /// Share test results
  void _shareResults() {
    final text = _formatResultsAsText();
    Share.share(text, subject: 'Tool Test Results');
  }

  @override
  Widget build(BuildContext context) {
    final registry = sl<ToolRegistry>();
    final toolNames = registry.names;
    final passed = _results.where((r) => r.success).length;
    final failed = _results.where((r) => !r.success).length;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('🔧 Tool Tester'),
            Text(
              '${toolNames.length} инструментов',
              style: TextStyle(fontSize: 12, color: Colors.grey[400]),
            ),
          ],
        ),
        actions: [
          if (_results.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.share),
              tooltip: 'Share results',
              onPressed: _shareResults,
            ),
          TextButton(
            onPressed: _isRunning ? null : _runAllTests,
            child: Text(
              _isRunning ? 'Testing...' : 'Run All',
              style: const TextStyle(color: Colors.white),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // Summary
          if (_results.isNotEmpty)
            Container(
              padding: const EdgeInsets.all(16),
              color: Colors.grey[900],
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _buildStat('Total', toolNames.length.toString(), Colors.blue),
                  _buildStat('Passed', passed.toString(), Colors.green),
                  _buildStat('Failed', failed.toString(), Colors.red),
                  _buildStat('Not tested', (toolNames.length - _results.length).toString(), Colors.grey),
                ],
              ),
            ),
          // Current tool indicator
          if (_isRunning && _currentTool != null)
            Container(
              padding: const EdgeInsets.all(8),
              color: Colors.orange.withOpacity(0.2),
              child: Row(
                children: [
                  const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  const SizedBox(width: 12),
                  Text('Testing: $_currentTool'),
                ],
              ),
            ),
          // Results list
          Expanded(
            child: ListView.builder(
              itemCount: toolNames.length,
              itemBuilder: (context, index) {
                final name = toolNames[index];
                final result = _results.where((r) => r.toolName == name).firstOrNull;
                final tool = registry.get(name);
                return _buildToolTile(name, tool, result, index: index + 1, total: toolNames.length);
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStat(String label, String value, Color color) {
    return Column(
      children: [
        Text(
          value,
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
        Text(label, style: TextStyle(color: Colors.grey[400], fontSize: 12)),
      ],
    );
  }

  Widget _buildToolTile(String name, AgentTool? tool, _ToolTestResult? result, {required int index, required int total}) {
    final isRunning = _currentTool == name;
    final params = _testParams[name] ?? {};
    
    return ExpansionTile(
      leading: isRunning
          ? const SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : result == null
              ? const Icon(Icons.help_outline, color: Colors.grey)
              : Icon(
                  result.success ? Icons.check_circle : Icons.error,
                  color: result.success ? Colors.green : Colors.red,
                ),
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
            margin: const EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: Colors.blue.withOpacity(0.2),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(
              '$index/$total',
              style: TextStyle(fontSize: 10, color: Colors.blue, fontWeight: FontWeight.bold, fontFamily: 'monospace'),
            ),
          ),
          Expanded(
            child: Text(
              name,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                color: result?.success == true
                    ? Colors.green
                    : result != null
                        ? Colors.red
                        : null,
              ),
            ),
          ),
        ],
      ),
      subtitle: Text(
        result != null
            ? '${result.message.substring(0, result.message.length.clamp(0, 50))}... (${result.duration.inMilliseconds}ms)'
            : tool?.description ?? '',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: IconButton(
        icon: const Icon(Icons.play_arrow),
        onPressed: _isRunning ? null : () => _runSingleTest(name),
      ),
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          color: Colors.grey[900],
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Description:', style: TextStyle(color: Colors.grey[400], fontSize: 12)),
              Text(tool?.description ?? 'N/A', style: const TextStyle(fontSize: 12)),
              const SizedBox(height: 8),
              Text('Test params:', style: TextStyle(color: Colors.grey[400], fontSize: 12)),
              Text(params.toString(), style: const TextStyle(fontSize: 11, fontFamily: 'monospace')),
              if (result != null) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(
                      result.success ? Icons.check_circle : Icons.error,
                      size: 16,
                      color: result.success ? Colors.green : Colors.red,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      result.success ? 'PASSED' : 'FAILED',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: result.success ? Colors.green : Colors.red,
                        fontSize: 12,
                      ),
                    ),
                    const Spacer(),
                    Text('${result.duration.inMilliseconds}ms', style: TextStyle(color: Colors.grey[500], fontSize: 11)),
                  ],
                ),
                const SizedBox(height: 8),
                Text('Result:', style: TextStyle(color: Colors.grey[400], fontSize: 12)),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: result.success ? Colors.green.withOpacity(0.1) : Colors.red.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(
                      color: result.success ? Colors.green.withOpacity(0.3) : Colors.red.withOpacity(0.3),
                    ),
                  ),
                  child: SelectableText(
                    result.message,
                    style: TextStyle(
                      fontSize: 12,
                      color: result.success ? Colors.green[200] : Colors.red[200],
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}
