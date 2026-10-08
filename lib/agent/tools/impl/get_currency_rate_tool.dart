import '../base_tool.dart';
import 'package:ai_voice_agent/core/network/api_client.dart';

/// Get currency exchange rate tool
class GetCurrencyRateTool extends AgentTool {
  final ApiClient _apiClient;

  GetCurrencyRateTool(this._apiClient);

  @override
  String get name => 'get_currency_rate';

  @override
  String get description => 'Узнать курс валюты. Например: "сколько долларов в 100 евро", "курс доллара к молдавскому лею".';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'from',
          type: 'string',
          description: 'Код исходной валюты (например "USD", "EUR", "MDL")',
          required: true,
        ),
        ToolParam(
          name: 'to',
          type: 'string',
          description: 'Код целевой валюты (например "USD", "EUR", "MDL")',
          required: true,
        ),
        ToolParam(
          name: 'amount',
          type: 'number',
          description: 'Сумма для конвертации (по умолчанию 1)',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final from = (params['from'] as String?)?.toUpperCase();
    final to = (params['to'] as String?)?.toUpperCase();
    if (from == null || to == null) {
      return ToolResult.failure('Не указаны валюты');
    }

    final amount = (params['amount'] as num?)?.toDouble() ?? 1.0;

    try {
      // Using exchangerate-api.com free endpoint
      final response = await _apiClient.get(
        'https://api.exchangerate-api.com/v4/latest/$from',
        timeout: const Duration(seconds: 10),
      );

      final rates = response['rates'] as Map<String, dynamic>?;
      if (rates == null) {
        return ToolResult.failure('Курсы валют недоступны');
      }

      final rate = rates[to];
      if (rate == null) {
        return ToolResult.failure('Курс $from → $to не найден');
      }

      final result = amount * (rate as num).toDouble();
      final rateStr = '1 $from = ${rate.toStringAsFixed(4)} $to';
      final amountStr = amount != 1 ? '\n$amount $from = ${result.toStringAsFixed(2)} $to' : '';

      return ToolResult.success('$rateStr$amountStr', {
        'rate': rate,
        'result': result,
      });
    } catch (e) {
      return ToolResult.failure('Ошибка получения курса валют: $e');
    }
  }
}
