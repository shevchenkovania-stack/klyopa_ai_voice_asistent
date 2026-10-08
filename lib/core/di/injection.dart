import 'package:get_it/get_it.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:ai_voice_agent/core/config/app_config.dart';
import 'package:ai_voice_agent/core/network/api_client.dart';
import 'package:ai_voice_agent/core/security/secure_storage.dart';
import 'package:ai_voice_agent/data/datasources/whisper_api.dart';
import 'package:ai_voice_agent/data/datasources/local_db.dart';
import 'package:ai_voice_agent/data/repositories/speech_repository.dart';
import 'package:ai_voice_agent/data/repositories/history_repository.dart';
import 'package:ai_voice_agent/domain/repositories/i_speech_repository.dart';
import 'package:ai_voice_agent/domain/repositories/i_history_repository.dart';
import 'package:ai_voice_agent/voice/tts_engine.dart';
import 'package:ai_voice_agent/voice/audio_recorder.dart';
import 'package:ai_voice_agent/voice/vad_detector.dart';
import 'package:ai_voice_agent/voice/wake_word_detector.dart';
import 'package:ai_voice_agent/agent/tools/tool_registry.dart';
import 'package:ai_voice_agent/agent/tools/impl/send_sms_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/read_sms_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/search_sms_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/make_call_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/set_alarm_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/file_tools.dart';
import 'package:ai_voice_agent/agent/tools/impl/get_current_time_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/search_contacts_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/open_app_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/set_timer_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/system_control_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/get_weather_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/get_currency_rate_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/media_control_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/web_search_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/play_store_search_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/open_play_store_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/self_awareness_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/connect_wifi_tool.dart';
import 'package:ai_voice_agent/agent/tools/impl/kotlin_tool_proxy.dart';
import 'package:ai_voice_agent/agent/voice_agent.dart';
import 'package:ai_voice_agent/core/engine/kotlin_engine_service.dart';

final sl = GetIt.instance;

Future<void> initDependencies() async {
  // Core
  sl.registerLazySingleton<FlutterSecureStorage>(
    () => const FlutterSecureStorage(),
  );
  sl.registerLazySingleton<SecureStorageService>(
    () => SecureStorageService(sl()),
  );
  sl.registerLazySingleton<Dio>(() => Dio());
  sl.registerLazySingleton<ApiClient>(() => ApiClient(sl()));

  // Config & Storage
  sl.registerLazySingleton<AppConfig>(() => AppConfig());
  sl.registerLazySingleton<LocalDb>(() => LocalDb());

  // Transcription (Whisper)
  sl.registerLazySingleton<WhisperApi>(() => WhisperApi(sl()));
  sl.registerLazySingleton<ISpeechRepository>(() => SpeechRepository(sl()));

  // History
  sl.registerLazySingleton<IHistoryRepository>(() => HistoryRepository(sl()));

  // Voice
  sl.registerLazySingleton<TtsEngine>(() => TtsEngine());
  sl.registerLazySingleton<AgentAudioRecorder>(() => AgentAudioRecorder());
  sl.registerLazySingleton<VadDetector>(() => VadDetector());
  sl.registerLazySingleton<KotlinEngineService>(() => KotlinEngineService());
  sl.registerLazySingleton<WakeWordDetector>(() {
    final detector = WakeWordDetector();
    detector.initialize();
    return detector;
  });

  // Agent System
  final toolRegistry = ToolRegistry();
  toolRegistry.register(SendSmsTool());
  toolRegistry.register(ReadSmsTool());
  toolRegistry.register(SearchSmsTool());
  toolRegistry.register(MakeCallTool());
  toolRegistry.register(SetAlarmTool());
  toolRegistry.register(CreateFileTool());
  toolRegistry.register(ReadFileTool());
  toolRegistry.register(GetCurrentTimeTool());
  toolRegistry.register(SearchContactsTool());
  toolRegistry.register(OpenAppTool());
  toolRegistry.register(SetTimerTool());
  toolRegistry.register(SystemControlTool());
  toolRegistry.register(GetWeatherTool(sl()));
  toolRegistry.register(GetCurrencyRateTool(sl()));
  toolRegistry.register(MediaControlTool());
  toolRegistry.register(WebSearchTool(sl()));
  toolRegistry.register(PlayStoreSearchTool());
  toolRegistry.register(OpenPlayStoreTool());
  toolRegistry.register(SelfAwarenessTool());
  toolRegistry.register(ConnectWifiTool());
  
  // === NEW: Kotlin-proxied tools (UI display + test) ===
  // File System
  toolRegistry.register(KotlinToolProxy(name: 'create_folder', description: 'Создать папку'));
  toolRegistry.register(KotlinToolProxy(name: 'list_files', description: 'Показывает файлы и папки в директории'));
  toolRegistry.register(KotlinToolProxy(name: 'delete_file', description: 'Удаляет файл или пустую папку'));
  toolRegistry.register(KotlinToolProxy(name: 'write_file', description: 'Записывает текст в файл'));
  toolRegistry.register(KotlinToolProxy(name: 'open_file', description: 'Открывает файл в приложении по умолчанию'));
  toolRegistry.register(KotlinToolProxy(name: 'file_info', description: 'Информация о файле'));
  // Device
  toolRegistry.register(KotlinToolProxy(name: 'battery_info', description: 'Состояние батареи'));
  toolRegistry.register(KotlinToolProxy(name: 'device_info', description: 'Информация об устройстве'));
  toolRegistry.register(KotlinToolProxy(name: 'storage_info', description: 'Свободное место'));
  // Control
  toolRegistry.register(KotlinToolProxy(name: 'clipboard_read', description: 'Читать буфер обмена'));
  toolRegistry.register(KotlinToolProxy(name: 'clipboard_write', description: 'Копировать в буфер обмена'));
  toolRegistry.register(KotlinToolProxy(name: 'volume_control', description: 'Управление громкостью'));
  toolRegistry.register(KotlinToolProxy(name: 'brightness', description: 'Яркость экрана'));
  toolRegistry.register(KotlinToolProxy(name: 'flashlight', description: 'Фонарик вкл/выкл'));
  // Communication
  toolRegistry.register(KotlinToolProxy(name: 'launch_url', description: 'Открыть URL в браузере'));
  toolRegistry.register(KotlinToolProxy(name: 'share_text', description: 'Поделиться текстом'));
  toolRegistry.register(KotlinToolProxy(name: 'send_email', description: 'Отправить email'));
  // System
  toolRegistry.register(KotlinToolProxy(name: 'list_apps', description: 'Список установленных приложений'));
  toolRegistry.register(KotlinToolProxy(name: 'app_info', description: 'Информация о приложении'));
  toolRegistry.register(KotlinToolProxy(name: 'network_info', description: 'Статус сетевого подключения'));
  // Camera
  toolRegistry.register(KotlinToolProxy(name: 'take_selfie', description: 'Сделать селфи'));
  toolRegistry.register(KotlinToolProxy(name: 'take_photo', description: 'Сделать фото'));
  // Drawing
  toolRegistry.register(KotlinToolProxy(name: 'draw_image', description: 'Нарисовать фигуру и сохранить как PNG'));
  // Download
  toolRegistry.register(KotlinToolProxy(name: 'download_file', description: 'Скачать файл по URL (MP3, картинки, PDF)'));
  // Local Music
  toolRegistry.register(KotlinToolProxy(name: 'search_local_music', description: 'Искать музыку на устройстве по названию/исполнителю'));
  
  sl.registerLazySingleton<ToolRegistry>(() => toolRegistry);
  sl.registerLazySingleton<VoiceAgent>(() => VoiceAgent(sl()));

  // Init async dependencies
  await sl<LocalDb>().init();
  await sl<AppConfig>().load(sl());
  
  // Sync config from Flutter (SecureStorage) to Kotlin (SharedPreferences)
  await sl<KotlinEngineService>().syncConfig(sl<AppConfig>());
}
