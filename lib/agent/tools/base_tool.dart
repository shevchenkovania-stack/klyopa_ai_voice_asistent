import 'dart:convert';

/// Tool parameter definition
class ToolParam {
  final String name;
  final String type; // string, number, boolean, object, array
  final String description;
  final bool required;
  final List<String>? enumValues;

  const ToolParam({
    required this.name,
    required this.type,
    required this.description,
    this.required = false,
    this.enumValues,
  });

  Map<String, dynamic> toJson() {
    final schema = <String, dynamic>{
      'type': type,
      'description': description,
    };
    if (enumValues != null) {
      schema['enum'] = enumValues;
    }
    return schema;
  }
}

/// Base tool interface
abstract class AgentTool {
  String get name;
  String get description;
  List<ToolParam> get parameters;

  /// Execute the tool with given parameters
  Future<ToolResult> execute(Map<String, dynamic> params);

  /// Convert to OpenAI function calling format
  Map<String, dynamic> toFunctionSchema() {
    final properties = <String, dynamic>{};
    final required = <String>[];

    for (final param in parameters) {
      properties[param.name] = param.toJson();
      if (param.required) {
        required.add(param.name);
      }
    }

    return {
      'type': 'function',
      'function': {
        'name': name,
        'description': description,
        'parameters': {
          'type': 'object',
          'properties': properties,
          'required': required,
        },
      },
    };
  }
}

/// Tool execution result
class ToolResult {
  final bool success;
  final String message;
  final Map<String, dynamic>? data;

  const ToolResult({
    required this.success,
    required this.message,
    this.data,
  });

  factory ToolResult.success(String message, [Map<String, dynamic>? data]) {
    return ToolResult(success: true, message: message, data: data);
  }

  factory ToolResult.failure(String message) {
    return ToolResult(success: false, message: message);
  }

  Map<String, dynamic> toJson() => {
        'success': success,
        'message': message,
        if (data != null) 'data': data,
      };

  String toJsonString() => jsonEncode(toJson());
}
