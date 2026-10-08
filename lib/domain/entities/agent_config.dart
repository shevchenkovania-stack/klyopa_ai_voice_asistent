import 'package:equatable/equatable.dart';

class AgentConfig extends Equatable {
  final String agentName;
  final String groqApiKey;
  final String openaiApiKey;
  final String language;
  final List<String> supportedLanguages;
  final double wakeWordSensitivity;
  final double ttsSpeed;
  final double ttsPitch;
  final String ttsVoice;
  final int aiTimeoutSeconds;

  const AgentConfig({
    required this.agentName,
    required this.groqApiKey,
    required this.openaiApiKey,
    this.language = 'ru',
    this.supportedLanguages = const ['ru', 'ro', 'en'],
    this.wakeWordSensitivity = 0.7,
    this.ttsSpeed = 1.0,
    this.ttsPitch = 1.0,
    this.ttsVoice = '',
    this.aiTimeoutSeconds = 5,
  });

  AgentConfig copyWith({
    String? agentName,
    String? groqApiKey,
    String? openaiApiKey,
    String? language,
    List<String>? supportedLanguages,
    double? wakeWordSensitivity,
    double? ttsSpeed,
    double? ttsPitch,
    String? ttsVoice,
    int? aiTimeoutSeconds,
  }) {
    return AgentConfig(
      agentName: agentName ?? this.agentName,
      groqApiKey: groqApiKey ?? this.groqApiKey,
      openaiApiKey: openaiApiKey ?? this.openaiApiKey,
      language: language ?? this.language,
      supportedLanguages: supportedLanguages ?? this.supportedLanguages,
      wakeWordSensitivity: wakeWordSensitivity ?? this.wakeWordSensitivity,
      ttsSpeed: ttsSpeed ?? this.ttsSpeed,
      ttsPitch: ttsPitch ?? this.ttsPitch,
      ttsVoice: ttsVoice ?? this.ttsVoice,
      aiTimeoutSeconds: aiTimeoutSeconds ?? this.aiTimeoutSeconds,
    );
  }

  @override
  List<Object?> get props => [
        agentName, groqApiKey, openaiApiKey, language,
        supportedLanguages, wakeWordSensitivity, ttsSpeed,
        ttsPitch, ttsVoice, aiTimeoutSeconds,
      ];
}
