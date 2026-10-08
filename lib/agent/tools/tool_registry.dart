import 'base_tool.dart';

/// Registry for all available agent tools
class ToolRegistry {
  final Map<String, AgentTool> _tools = {};

  /// Register a tool
  void register(AgentTool tool) {
    _tools[tool.name] = tool;
  }

  /// Get tool by name
  AgentTool? get(String name) => _tools[name];

  /// Check if tool exists
  bool has(String name) => _tools.containsKey(name);

  /// Get all tool names
  List<String> get names => _tools.keys.toList();

  /// Get all tools as OpenAI function schemas
  List<Map<String, dynamic>> toFunctionSchemas() {
    return _tools.values.map((t) => t.toFunctionSchema()).toList();
  }

  /// Execute a tool by name
  Future<ToolResult> execute(String name, Map<String, dynamic> params) async {
    final tool = _tools[name];
    if (tool == null) {
      return ToolResult.failure('Инструмент "$name" не найден');
    }
    return await tool.execute(params);
  }
}
