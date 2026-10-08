import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/security/secure_storage.dart';
import 'package:ai_voice_agent/domain/entities/agent_config.dart';
import 'package:ai_voice_agent/domain/repositories/i_config_repository.dart';

class ConfigRepository implements IConfigRepository {
  final AppConfig _appConfig;
  final SecureStorageService _storage;

  ConfigRepository(this._appConfig, this._storage);

  @override
  Future<AgentConfig> loadConfig() async {
    await _appConfig.load(_storage);
    return AgentConfig(
      agentName: _appConfig.agentName,
      groqApiKey: _appConfig.groqApiKey,
      openaiApiKey: _appConfig.openaiApiKey,
      language: _appConfig.language,
      supportedLanguages: _appConfig.supportedLanguages,
      wakeWordSensitivity: _appConfig.wakeWordSensitivity,
      ttsSpeed: _appConfig.ttsSpeed,
      ttsPitch: _appConfig.ttsPitch,
      ttsVoice: _appConfig.ttsVoice,
      aiTimeoutSeconds: _appConfig.aiTimeoutSeconds,
    );
  }

  @override
  Future<void> saveConfig(AgentConfig config) async {
    _appConfig.agentName = config.agentName;
    _appConfig.groqApiKey = config.groqApiKey;
    _appConfig.openaiApiKey = config.openaiApiKey;
    _appConfig.language = config.language;
    _appConfig.wakeWordSensitivity = config.wakeWordSensitivity;
    _appConfig.ttsSpeed = config.ttsSpeed;
    _appConfig.ttsPitch = config.ttsPitch;
    _appConfig.ttsVoice = config.ttsVoice;
    await _appConfig.save(_storage);
  }
}
