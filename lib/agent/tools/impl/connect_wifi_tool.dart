import 'package:flutter/services.dart';
import '../base_tool.dart';

/// Connect to WiFi network tool
class ConnectWifiTool extends AgentTool {
  static const _channel = MethodChannel('com.aiagent.ai_voice_agent/wifi');

  @override
  String get name => 'connect_wifi';

  @override
  String get description => 'Подключиться к WiFi сети. Если сеть открытая — подключится автоматически. Если сеть закрытая — попросит пароль. Может искать сеть по имени.';

  @override
  List<ToolParam> get parameters => [
        ToolParam(
          name: 'ssid',
          type: 'string',
          description: 'Имя WiFi сети (например "Nessa", "HomeNetwork", "CoffeeShop")',
          required: true,
        ),
        ToolParam(
          name: 'password',
          type: 'string',
          description: 'Пароль WiFi сети (если сеть закрытая и пользователь предоставил пароль)',
          required: false,
        ),
      ];

  @override
  Future<ToolResult> execute(Map<String, dynamic> params) async {
    final ssid = params['ssid'] as String?;
    if (ssid == null || ssid.isEmpty) {
      return ToolResult.failure('Не указано имя WiFi сети');
    }

    final password = params['password'] as String?;

    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'connectWifi',
        {'ssid': ssid, 'password': password ?? ''},
      );

      if (result == null) {
        return ToolResult.failure('Нет ответа от системы');
      }

      final success = result['success'] as bool? ?? false;
      final message = result['message'] as String? ?? '';
      final needsPassword = result['needs_password'] as bool? ?? false;
      final networkFound = result['network_found'] as bool? ?? true;

      if (!networkFound) {
        return ToolResult.failure('Сеть "$ssid" не найдена. Проверьте имя сети и убедитесь что сеть доступна.');
      }

      if (needsPassword) {
        return ToolResult.success(
          'Сеть "$ssid" защищена паролем. Скажите пароль для подключения.',
          {'needs_password': true, 'ssid': ssid},
        );
      }

      if (success) {
        return ToolResult.success(message, {'connected': true, 'ssid': ssid});
      } else {
        return ToolResult.failure(message);
      }
    } catch (e) {
      return ToolResult.failure('Ошибка подключения к WiFi: $e');
    }
  }
}
