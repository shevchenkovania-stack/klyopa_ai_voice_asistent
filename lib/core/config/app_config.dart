import 'package:ai_voice_agent/core/security/secure_storage.dart';

class AppConfig {
  String agentName = 'Клёпа';
  String groqApiKey = '';
  String openaiApiKey = '';
  String geminiApiKey = '';
  String activeProvider = 'gemini';
  String language = 'ru';
  List<String> supportedLanguages = ['ru', 'ro', 'en'];
  List<String> supportedProviders = ['gemini', 'openai', 'groq'];
  double ttsSpeed = 1.0;
  double ttsPitch = 1.0;
  String ttsVoice = '';
  String ttsProvider = 'edge';
  int aiTimeoutSeconds = 5;
  double wakeWordSensitivity = 0.5;

  bool get hasApiKeys => geminiApiKey.isNotEmpty || groqApiKey.isNotEmpty || openaiApiKey.isNotEmpty;

  /// Storage value is usable only if it exists and is not blank.
  /// Blank values were written by older builds and used to lock the app on an
  /// empty onboarding screen, so they fall back to the code defaults instead.
  static String? _nn(String? value) {
    if (value == null) return null;
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : trimmed;
  }

  Future<void> load(SecureStorageService storage) async {
    agentName = _nn(await storage.read('agent_name')) ?? 'Клёпа';
    geminiApiKey = _nn(await storage.read('gemini_api_key')) ?? geminiApiKey;
    groqApiKey = _nn(await storage.read('groq_api_key')) ?? groqApiKey;
    openaiApiKey = _nn(await storage.read('openai_api_key')) ?? openaiApiKey;
    activeProvider = _nn(await storage.read('active_provider')) ?? 'gemini';
    // Force Gemini as primary for testing
    if (activeProvider == 'openai') activeProvider = 'gemini';
    language = _nn(await storage.read('language')) ?? 'ru';
    final speed = await storage.read('tts_speed');
    if (speed != null) ttsSpeed = double.tryParse(speed) ?? 1.0;
    final pitch = await storage.read('tts_pitch');
    if (pitch != null) ttsPitch = double.tryParse(pitch) ?? 1.0;
    ttsVoice = _nn(await storage.read('tts_voice')) ?? '';
    ttsProvider = _nn(await storage.read('tts_provider')) ?? 'edge';
    final wws = await storage.read('wake_word_sensitivity');
    if (wws != null) wakeWordSensitivity = double.tryParse(wws) ?? 0.5;
  }

  Future<void> save(SecureStorageService storage) async {
    await storage.write('agent_name', agentName);
    await storage.write('gemini_api_key', geminiApiKey);
    await storage.write('groq_api_key', groqApiKey);
    await storage.write('openai_api_key', openaiApiKey);
    await storage.write('active_provider', activeProvider);
    await storage.write('language', language);
    await storage.write('tts_speed', ttsSpeed.toString());
    await storage.write('tts_pitch', ttsPitch.toString());
    await storage.write('tts_voice', ttsVoice);
    await storage.write('tts_provider', ttsProvider);
    await storage.write('wake_word_sensitivity', wakeWordSensitivity.toString());
  }
}
