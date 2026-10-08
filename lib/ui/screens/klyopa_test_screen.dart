import 'package:flutter/material.dart';
import 'package:ai_voice_agent/ui/widgets/klyopa_face.dart';

class KlyopaTestScreen extends StatefulWidget {
  const KlyopaTestScreen({super.key});

  @override
  State<KlyopaTestScreen> createState() => _KlyopaTestScreenState();
}

class _KlyopaTestScreenState extends State<KlyopaTestScreen> {
  KlyopaState _currentState = KlyopaState.idle;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Тест лица Клёпы'),
        centerTitle: true,
      ),
      body: Column(
        children: [
          const SizedBox(height: 20),
          Container(
            // 1:1 with the 375x330 canvas of klyopa_config.json
            width: 375,
            height: 330,
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Center(
              child: KlyopaFace(state: _currentState),
            ),
          ),
          const SizedBox(height: 20),
          Text(
            'Состояние: ${_currentState.name}',
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 20),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            alignment: WrapAlignment.center,
            children: [
              _buildButton('Idle', KlyopaState.idle, Colors.blue),
              _buildButton('Listening', KlyopaState.listening, Colors.green),
              _buildButton('Talking', KlyopaState.talking, Colors.orange),
              _buildButton('Thinking', KlyopaState.thinking, Colors.purple),
              _buildButton('Happy', KlyopaState.happy, Colors.pink),
              _buildButton('Error', KlyopaState.error, Colors.red),
              _buildButton('Blink', KlyopaState.blink, Colors.teal),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildButton(String label, KlyopaState state, Color color) {
    return ElevatedButton(
      onPressed: () => setState(() => _currentState = state),
      style: ElevatedButton.styleFrom(
        backgroundColor: color,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      ),
      child: Text(label),
    );
  }
}
