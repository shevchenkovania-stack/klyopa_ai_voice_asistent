import 'package:flutter/services.dart';
import 'package:ai_voice_agent/agent/tools/base_tool.dart';

/// A Flutter-side tool stub that delegates execution to the Kotlin engine via MethodChannel.
/// Used for UI display (tool tester, inspector) while actual logic runs in Kotlin.
class KotlinToolProxy extends AgentTool {
  @override
  final String name;
  @override
  final String description;
  final List<ToolParam> _parameters;

  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/engine');

  KotlinToolProxy({
    required this.name,
    required this.description,
    List<ToolParam>? parameters,
  }) : _parameters = parameters ?? [];

  @override
  List<ToolParam> get parameters => _parameters;

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    try {
      final result = await _channel.invokeMethod('executeTool', {
        'tool': name,
        'params': params,
      });
      
      if (result is Map) {
        final success = result['success'] as bool? ?? false;
        final message = result['message'] as String? ?? '';
        return success
            ? ToolResult.success(message)
            : ToolResult.failure(message);
      }
      
      return ToolResult.success(result?.toString() ?? 'OK');
    } on PlatformException catch (e) {
      return ToolResult.failure('Kotlin error: ${e.message ?? e.code}');
    } catch (e) {
      return ToolResult.failure('Proxy error: $e');
    }
  }
}
