import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Общий MethodChannel для всех вкладок тестера
const kEngineChannel = MethodChannel('com.aiagent.ai_voice_agent/engine');

/// Баннер статуса (зелёный ✓ или красный ✗)
Widget buildStatusBanner(String status) {
  return Container(
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: status.startsWith('✓')
          ? Colors.green.withOpacity(0.15)
          : Colors.red.withOpacity(0.15),
      borderRadius: BorderRadius.circular(8),
      border: Border.all(
        color: status.startsWith('✓')
            ? Colors.green.withOpacity(0.4)
            : Colors.red.withOpacity(0.4),
      ),
    ),
    child: Text(
      status,
      style: TextStyle(
        color: status.startsWith('✓') ? Colors.green : Colors.red,
        fontSize: 13,
      ),
    ),
  );
}

/// Текстовое поле для ввода параметров
Widget buildTextField(
  TextEditingController controller,
  String hint,
  IconData icon, {
  ValueChanged<String>? onSubmitted,
}) {
  return TextField(
    controller: controller,
    style: const TextStyle(color: Colors.white),
    decoration: InputDecoration(
      hintText: hint,
      hintStyle: TextStyle(color: Colors.grey[600]),
      filled: true,
      fillColor: Colors.grey.withOpacity(0.2),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: BorderSide.none,
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      prefixIcon: Icon(icon, color: Colors.grey, size: 20),
    ),
    onSubmitted: onSubmitted,
  );
}

/// Вызвать инструмент через MethodChannel
Future<Map<dynamic, dynamic>?> invokeTool(
  String toolName,
  Map<String, dynamic> params,
) async {
  return await kEngineChannel.invokeMethod<Map<dynamic, dynamic>>(
    'executeTool',
    {'toolName': toolName, 'params': params},
  );
}
