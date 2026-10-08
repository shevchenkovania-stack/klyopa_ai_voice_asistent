import 'package:flutter/material.dart';
import 'package:ai_voice_agent/ui/widgets/klyopa_face.dart';

/// Панель управления эмоциями Клёпы для тестирования
class EmotionPanel extends StatelessWidget {
  final KlyopaState? currentState;
  final Function(KlyopaState) onEmotionSelected;

  const EmotionPanel({
    super.key,
    required this.currentState,
    required this.onEmotionSelected,
  });

  @override
  Widget build(BuildContext context) {
    final emotions = [
      _EmotionButton(
        state: KlyopaState.idle,
        icon: Icons.face,
        label: 'Спокойно',
        color: Colors.grey,
      ),
      _EmotionButton(
        state: KlyopaState.happy,
        icon: Icons.sentiment_very_satisfied,
        label: 'Радость',
        color: Colors.amber,
      ),
      _EmotionButton(
        state: KlyopaState.listening,
        icon: Icons.hearing,
        label: 'Слушает',
        color: Colors.blue,
      ),
      _EmotionButton(
        state: KlyopaState.talking,
        icon: Icons.record_voice_over,
        label: 'Говорит',
        color: Colors.green,
      ),
      _EmotionButton(
        state: KlyopaState.thinking,
        icon: Icons.psychology,
        label: 'Думает',
        color: Colors.purple,
      ),
      _EmotionButton(
        state: KlyopaState.error,
        icon: Icons.sentiment_very_dissatisfied,
        label: 'Грусть',
        color: Colors.red,
      ),
      _EmotionButton(
        state: KlyopaState.blink,
        icon: Icons.visibility,
        label: 'Моргает',
        color: Colors.cyan,
      ),
    ];

    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.7),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white24),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '🎭 Эмоции Клёпы',
            style: TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 6),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: emotions.map((emotion) {
              final isSelected = currentState == emotion.state;
              return InkWell(
                onTap: () => onEmotionSelected(emotion.state),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: isSelected
                        ? emotion.color.withOpacity(0.3)
                        : Colors.white10,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: isSelected ? emotion.color : Colors.white24,
                      width: isSelected ? 2 : 1,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        emotion.icon,
                        size: 16,
                        color: isSelected ? emotion.color : Colors.white70,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        emotion.label,
                        style: TextStyle(
                          color: isSelected ? emotion.color : Colors.white70,
                          fontSize: 11,
                          fontWeight: isSelected
                              ? FontWeight.bold
                              : FontWeight.normal,
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}

class _EmotionButton {
  final KlyopaState state;
  final IconData icon;
  final String label;
  final Color color;

  _EmotionButton({
    required this.state,
    required this.icon,
    required this.label,
    required this.color,
  });
}
