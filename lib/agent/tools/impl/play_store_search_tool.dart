import '../base_tool.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/core/network/api_client.dart';

/// Tool to search for apps on Google Play Store
class PlayStoreSearchTool extends AgentTool {
  @override
  String get name => 'play_store_search';

  @override
  String get description => 'Поиск приложений в Google Play Store. Используй когда нужно найти и установить приложение.';

  @override
  List<ToolParam> get parameters => [
    ToolParam(
      name: 'query',
      type: 'string',
      description: 'Поисковый запрос (например, "заметки", "калькулятор")',
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
      // Use DuckDuckGo to search for Play Store apps
      final apiClient = sl<ApiClient>();
      final searchQuery = 'site:play.google.com $query приложение';
      
      final response = await apiClient.get(
        'https://api.duckduckgo.com/?q=${Uri.encodeComponent(searchQuery)}&format=json&no_html=1&skip_disambig=1',
        timeout: const Duration(seconds: 10),
      );

      final results = <String>[];

      // Abstract
      final abstractText = response['AbstractText'] as String?;
      if (abstractText != null && abstractText.isNotEmpty) {
        results.add('Ответ: $abstractText');
      }

      // Related topics
      final relatedTopics = response['RelatedTopics'] as List?;
      if (relatedTopics != null) {
        for (final topic in relatedTopics.take(5)) {
          final text = topic['Text'] as String?;
          final firstUrl = topic['FirstURL'] as String?;
          if (text != null && firstUrl != null && firstUrl.contains('play.google.com')) {
            results.add('• $text\n  $firstUrl');
          }
        }
      }

      if (results.isEmpty) {
        return ToolResult.success('Не найдено приложений по запросу "$query". Попробуй другой запрос или открой Google Play вручную.');
      }

      return ToolResult.success('Найдено в Google Play:\n${results.join('\n\n')}');
    } catch (e) {
      return ToolResult.failure('Ошибка поиска: $e');
    }
  }
}
