import 'package:flutter/material.dart';
import 'package:ai_voice_agent/core/di/injection.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/security/secure_storage.dart';
import 'package:ai_voice_agent/voice/wake_word_detector.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late TextEditingController _nameController;
  late double _speed;
  late double _pitch;
  late double _sensitivity;
  late String _language;
  late String _activeProvider;

  @override
  void initState() {
    super.initState();
    final config = sl<AppConfig>();
    _nameController = TextEditingController(text: config.agentName);
    _speed = config.ttsSpeed;
    _pitch = config.ttsPitch;
    _sensitivity = config.wakeWordSensitivity;
    _language = config.language;
    _activeProvider = config.activeProvider;
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final config = sl<AppConfig>();
    final storage = sl<SecureStorageService>();
    config.agentName = _nameController.text;
    config.ttsSpeed = _speed;
    config.ttsPitch = _pitch;
    config.wakeWordSensitivity = _sensitivity;
    config.language = _language;
    config.activeProvider = _activeProvider;
    await config.save(storage);

    // Update wake word detector with new name
    sl<WakeWordDetector>().updateWakeWord(config.agentName);

    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Настройки сохранены')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Настройки'),
        actions: [IconButton(icon: const Icon(Icons.save), onPressed: _save)],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Agent Name
          TextField(
            controller: _nameController,
            decoration: const InputDecoration(
              labelText: 'Имя агента (wake word)',
              hintText: 'Как обращаться к агенту',
              prefixIcon: Icon(Icons.person),
            ),
          ),
          const SizedBox(height: 24),

          // Language
          DropdownButtonFormField<String>(
            value: _language,
            decoration: const InputDecoration(
              labelText: 'Язык',
              prefixIcon: Icon(Icons.language),
            ),
            items: const [
              DropdownMenuItem(value: 'ru', child: Text('Русский')),
              DropdownMenuItem(value: 'ro', child: Text('Română')),
              DropdownMenuItem(value: 'en', child: Text('English')),
            ],
            onChanged: (v) => setState(() => _language = v ?? 'ru'),
          ),
          const SizedBox(height: 24),

          // AI Provider
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              'AI Модель',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
            ),
          ),
          Row(
            children: [
              Expanded(
                child: _ProviderCard(
                  title: 'OpenAI',
                  subtitle: 'GPT-4o-mini',
                  icon: Icons.auto_awesome,
                  isSelected: _activeProvider == 'openai',
                  onTap: () => setState(() => _activeProvider = 'openai'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _ProviderCard(
                  title: 'Groq',
                  subtitle: 'Llama 3.3 70B',
                  icon: Icons.bolt,
                  isSelected: _activeProvider == 'groq',
                  onTap: () => setState(() => _activeProvider = 'groq'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),

          // TTS Speed
          Text('Скорость речи: ${_speed.toStringAsFixed(1)}'),
          Slider(
            value: _speed,
            min: 0.5,
            max: 2.0,
            divisions: 15,
            onChanged: (v) => setState(() => _speed = v),
          ),

          // TTS Pitch
          Text('Высота голоса: ${_pitch.toStringAsFixed(1)}'),
          Slider(
            value: _pitch,
            min: 0.5,
            max: 2.0,
            divisions: 15,
            onChanged: (v) => setState(() => _pitch = v),
          ),

          // Wake word sensitivity
          Text('Чувствительность: ${(_sensitivity * 100).toInt()}%'),
          Slider(
            value: _sensitivity,
            min: 0.3,
            max: 1.0,
            divisions: 7,
            onChanged: (v) => setState(() => _sensitivity = v),
          ),
          const SizedBox(height: 24),

          // API Keys
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              'API Ключи',
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: Theme.of(context).colorScheme.primary,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Card(
            child: ListTile(
              leading: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10),
                  color: Theme.of(context).colorScheme.primary.withAlpha(25),
                ),
                child: Icon(
                  Icons.key_rounded,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
              title: const Text('Управление ключами'),
              subtitle: Text(
                sl<AppConfig>().hasApiKeys ? 'Настроены' : 'Не настроены',
                style: TextStyle(
                  color: sl<AppConfig>().hasApiKeys ? Colors.green : Colors.red,
                ),
              ),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => Navigator.pushNamed(context, '/api-keys'),
            ),
          ),
          const SizedBox(height: 24),

          // Permissions
          OutlinedButton.icon(
            onPressed: () => Navigator.pushNamed(context, '/permissions'),
            icon: const Icon(Icons.security),
            label: const Text('Управление разрешениями'),
          ),
          const SizedBox(height: 16),

          // Save
          FilledButton.icon(
            onPressed: _save,
            icon: const Icon(Icons.save),
            label: const Text('Сохранить'),
          ),
        ],
      ),
    );
  }
}

/// Provider selection card
class _ProviderCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _ProviderCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isSelected
              ? colorScheme.primary.withAlpha(25)
              : colorScheme.surfaceContainerHighest.withAlpha(100),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? colorScheme.primary : Colors.transparent,
            width: 2,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  icon,
                  color: isSelected ? colorScheme.primary : colorScheme.onSurface,
                  size: 24,
                ),
                const Spacer(),
                if (isSelected)
                  Icon(
                    Icons.check_circle,
                    color: colorScheme.primary,
                    size: 20,
                  ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              title,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: isSelected ? colorScheme.primary : colorScheme.onSurface,
              ),
            ),
            Text(
              subtitle,
              style: TextStyle(
                fontSize: 12,
                color: colorScheme.onSurface.withAlpha(180),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
