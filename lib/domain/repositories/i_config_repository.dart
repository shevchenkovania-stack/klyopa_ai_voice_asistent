import 'package:ai_voice_agent/domain/entities/agent_config.dart';

abstract class IConfigRepository {
  Future<AgentConfig> loadConfig();
  Future<void> saveConfig(AgentConfig config);
}
