import 'package:flutter/material.dart';
import 'tool_tester_shared.dart';

/// Вкладка "Инфо" — местоположение, погода, валюта, устройство
class InfoTab extends StatefulWidget {
  const InfoTab({super.key});
  @override
  State<InfoTab> createState() => _InfoTabState();
}

class _InfoTabState extends State<InfoTab> {
  String? _status;
  bool _busy = false;
  final _weatherCity = TextEditingController(text: 'Кишинев');
  final _currencyFrom = TextEditingController(text: 'USD');
  final _currencyTo = TextEditingController(text: 'MDL');

  @override
  void dispose() {
    _weatherCity.dispose();
    _currencyFrom.dispose();
    _currencyTo.dispose();
    super.dispose();
  }

  Future<void> _exec(String toolName, Map<String, dynamic> params) async {
    if (_busy) return;
    setState(() { _busy = true; _status = '⏳ Выполняется...'; });
    try {
      final result = await invokeTool(toolName, params);
      final success = result?['success'] == true;
      final message = result?['message'] as String? ?? 'Нет ответа';
      setState(() => _status = success ? '✓ $message' : '✗ $message');
    } catch (e) {
      setState(() => _status = '✗ Ошибка: $e');
    }
    setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_status != null) buildStatusBanner(_status!),
          if (_status != null) const SizedBox(height: 12),

          // Location
          _buildInfoBtn(
            Icons.my_location,
            'Местоположение',
            'Определить GPS координаты',
            Colors.blue,
            () => _exec('get_location', {}),
          ),
          const SizedBox(height: 12),

          // Weather
          Material(
            color: Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.cloud, color: Colors.cyan, size: 24),
                      SizedBox(width: 12),
                      Text(
                        'Погода',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: buildTextField(
                          _weatherCity,
                          'Город...',
                          Icons.location_city,
                        ),
                      ),
                      const SizedBox(width: 8),
                      ElevatedButton(
                        onPressed: _busy
                            ? null
                            : () {
                                final c = _weatherCity.text.trim();
                                if (c.isNotEmpty)
                                  _exec('get_weather', {'city': c});
                              },
                        child: const Text('Узнать'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.cyan,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          // Currency
          Material(
            color: Colors.grey.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.attach_money, color: Colors.green, size: 24),
                      SizedBox(width: 12),
                      Text(
                        'Курс валют',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 15,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: buildTextField(
                          _currencyFrom,
                          'Из (USD)',
                          Icons.currency_exchange,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: buildTextField(
                          _currencyTo,
                          'В (MDL)',
                          Icons.currency_exchange,
                        ),
                      ),
                      const SizedBox(width: 8),
                      ElevatedButton(
                        onPressed: _busy
                            ? null
                            : () => _exec('get_currency_rate', {
                                'from': _currencyFrom.text,
                                'to': _currencyTo.text,
                              }),
                        child: const Text('Курс'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.green,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Device Info buttons
          const Divider(),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              'Устройство',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: Colors.grey[400],
              ),
            ),
          ),
          const SizedBox(height: 8),
          _buildInfoBtn(
            Icons.battery_std,
            'Батарея',
            'Уровень заряда, состояние',
            Colors.orange,
            () => _exec('battery_info', {}),
          ),
          const SizedBox(height: 8),
          _buildInfoBtn(
            Icons.phone_android,
            'Устройство',
            'Модель, версия Android',
            Colors.purple,
            () => _exec('device_info', {}),
          ),
          const SizedBox(height: 8),
          _buildInfoBtn(
            Icons.storage,
            'Память',
            'Свободное / занятое место',
            Colors.teal,
            () => _exec('storage_info', {}),
          ),
          const SizedBox(height: 8),
          _buildInfoBtn(
            Icons.signal_cellular_alt,
            'Сеть',
            'WiFi, IP, оператор',
            Colors.indigo,
            () => _exec('network_info', {}),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoBtn(
    IconData icon,
    String title,
    String subtitle,
    Color color,
    VoidCallback onTap,
  ) {
    return Material(
      color: Colors.grey.withOpacity(0.1),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: _busy ? null : onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(
            children: [
              Icon(icon, color: color, size: 28),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                    Text(
                      subtitle,
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ],
                ),
              ),
              if (_busy)
                const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              else
                Icon(Icons.play_arrow, color: color),
            ],
          ),
        ),
      ),
    );
  }
}
