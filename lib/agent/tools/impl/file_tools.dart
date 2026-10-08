import 'dart:io';
import 'package:path_provider/path_provider.dart';
import '../base_tool.dart';

/// Create file tool
class CreateFileTool extends AgentTool {
  @override
  String get name => 'create_file';

  @override
  String get description => 'Создать текстовый файл. Путь должен начинаться с /data/ или использовать документы пользователя.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'filename',
          type: 'string',
          description: 'Имя файла (например "заметки.txt", "test.txt")',
          required: true,
        ),
        ToolParam(
          name: 'content',
          type: 'string',
          description: 'Содержимое файла',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final filename = params['filename'] as String?;
    final content = params['content'] as String?;

    if (filename == null || filename.isEmpty) {
      return ToolResult.failure('Не указано имя файла');
    }
    if (content == null) {
      return ToolResult.failure('Не указано содержимое');
    }

    try {
      // Use documents directory
      final dir = await getApplicationDocumentsDirectory();
      final filePath = '${dir.path}/$filename';
      final file = File(filePath);
      
      // Create parent directories if needed
      final parentDir = file.parent;
      if (!await parentDir.exists()) {
        await parentDir.create(recursive: true);
      }
      
      await file.writeAsString(content, flush: true);
      
      // VERIFICATION: Check if file was actually created
      if (!await file.exists()) {
        return ToolResult.failure('Файл не был создан (проверка не удалась): $filePath');
      }
      
      // VERIFICATION: Check file size
      final fileSize = await file.length();
      if (fileSize == 0 && content.isNotEmpty) {
        return ToolResult.failure('Файл создан, но пустой (проверка не удалась): $filePath');
      }
      
      return ToolResult.success('Файл создан и проверен: $filePath (размер: $fileSize байт)', {'path': filePath});
    } catch (e) {
      return ToolResult.failure('Не удалось создать файл: $e');
    }
  }
}

/// Read file tool
class ReadFileTool extends AgentTool {
  @override
  String get name => 'read_file';

  @override
  String get description => 'Прочитать содержимое текстового файла из документов пользователя';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'filename',
          type: 'string',
          description: 'Имя файла для чтения (например "заметки.txt")',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final filename = params['filename'] as String?;

    if (filename == null || filename.isEmpty) {
      return ToolResult.failure('Не указано имя файла');
    }

    try {
      final dir = await getApplicationDocumentsDirectory();
      final filePath = '${dir.path}/$filename';
      final file = File(filePath);
      
      if (!await file.exists()) {
        // List available files
        final files = await dir.list().toList();
        final fileNames = files.whereType<File>().map((f) => f.path.split('/').last).toList();
        return ToolResult.failure('Файл "$filename" не найден. Доступные файлы: ${fileNames.join(", ")}');
      }
      
      final content = await file.readAsString();
      return ToolResult.success('Содержимое файла "$filename":', {'content': content, 'path': filePath});
    } catch (e) {
      return ToolResult.failure('Не удалось прочитать файл: $e');
    }
  }
}
