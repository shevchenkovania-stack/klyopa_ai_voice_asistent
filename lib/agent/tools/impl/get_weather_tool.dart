import '../base_tool.dart';
import 'package:ai_voice_agent/core/network/api_client.dart';

/// Get weather tool
class GetWeatherTool extends AgentTool {
  final ApiClient _apiClient;

  GetWeatherTool(this._apiClient);

  @override
  String get name => 'get_weather';

  @override
  String get description => 'Узнать текущую погоду в городе (температура, влажность, ветер, описание).';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'city',
          type: 'string',
          description: 'Название города (например "Кишинёв", "Москва", "London")',
          required: true,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final city = params['city'] as String?;
    if (city == null || city.isEmpty) {
      return ToolResult.failure('Не указан город');
    }

    try {
      // Using wttr.in - free weather API, no key needed
      final response = await _apiClient.get(
        'https://wttr.in/${Uri.encodeComponent(city)}?format=j1',
        timeout: const Duration(seconds: 10),
      );

      if (response == null) {
        return ToolResult.failure('Не удалось получить погоду');
      }

      final current = response['current_condition']?[0];
      if (current == null) {
        return ToolResult.failure('Данные о погоде недоступны');
      }

      final temp = current['temp_C'] ?? '?';
      final feelsLike = current['FeelsLikeC'] ?? '?';
      final humidity = current['humidity'] ?? '?';
      final windSpeed = current['windspeedKmph'] ?? '?';
      final description = current['weatherDesc']?[0]?['value'] ?? '?';

      final result = 'Погода в городе $city:\n'
          'Температура: $temp°C (ощущается как $feelsLike°C)\n'
          'Описание: $description\n'
          'Влажность: $humidity%\n'
          'Ветер: $windSpeed км/ч';

      return ToolResult.success(result, {
        'temp': temp,
        'feels_like': feelsLike,
        'humidity': humidity,
        'wind': windSpeed,
        'description': description,
      });
    } catch (e) {
      return ToolResult.failure('Ошибка получения погоды: $e');
    }
  }
}
