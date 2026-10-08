import '../base_tool.dart';
import 'package:ai_voice_agent/core/network/api_client.dart';

/// Web search tool using DuckDuckGo
class WebSearchTool extends AgentTool {
  final ApiClient _apiClient;

  WebSearchTool(this._apiClient);

  @override
  String get name => 'web_search';

  @override
  String get description => 'Найти информацию в интернете. Возвращает краткие ответы и ссылки. Используй для вопросов о погоде, новостях, фактах, определениях.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'query',
          type: 'string',
          description: 'Поисковый запрос (например "курс доллара", "кто такой Эйнштейн", "новости сегодня")',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final query = params['query'] as String?;
    if (query == null || query.isEmpty) {
      return ToolResult.failure('Не указан поисковый запрос');
    }

    try {
      // Using DuckDuckGo Instant Answer API (free, no key needed)
      final response = await _apiClient.get(
        'https://api.duckduckgo.com/?q=${Uri.encodeComponent(query)}&format=json&no_html=1&skip_disambig=1',
        timeout: const Duration(seconds: 10),
      );

      final results = <String>[];

      // Abstract (main answer)
      final abstractText = response['AbstractText'] as String?;
      if (abstractText != null && abstractText.isNotEmpty) {
        results.add('Ответ: $abstractText');
        final source = response['AbstractSource'] as String?;
        if (source != null) results.add('Источник: $source');
      }

      // Answer (direct answer)
      final answer = response['Answer'] as String?;
      if (answer != null && answer.isNotEmpty) {
        results.add('Ответ: $answer');
      }

      // Definition
      final definition = response['Definition'] as String?;
      if (definition != null && definition.isNotEmpty) {
        results.add('Определение: $definition');
      }

      // Related topics
      final relatedTopics = response['RelatedTopics'] as List<dynamic>?;
      if (relatedTopics != null && relatedTopics.isNotEmpty) {
        results.add('\nПохожие результаты:');
        for (var topic in relatedTopics.take(3)) {
          if (topic is Map<String, dynamic>) {
            final text = topic['Text'] as String?;
            if (text != null) results.add('• $text');
          }
        }
      }

      if (results.isEmpty) {
        return ToolResult.success('По запросу "$query" не найдено краткого ответа. Попробуй переформулировать.');
      }

      return ToolResult.success(results.join('\n'));
    } catch (e) {
      return ToolResult.failure('Ошибка поиска: $e');
    }
  }
}
