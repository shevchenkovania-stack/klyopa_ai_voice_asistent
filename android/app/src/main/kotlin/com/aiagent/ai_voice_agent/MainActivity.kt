package com.aiagent.ai_voice_agent

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.provider.AlarmClock
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream
import com.aiagent.ai_voice_agent.helpers.AppLauncher
import com.aiagent.ai_voice_agent.helpers.SystemController
import com.aiagent.ai_voice_agent.helpers.ContactsHelper
import com.aiagent.ai_voice_agent.engine.EngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : FlutterActivity() {
    // Static reference to FlutterEngine for access from services
    companion object {
        var engineInstance: FlutterEngine? = null
        const val EXTRA_WAKE_TRIGGERED = "wake_triggered"
    }

    override fun provideFlutterEngine(context: android.content.Context): FlutterEngine? {
        return FlutterEngineCache.getInstance().get(WakeUpApplication.ENGINE_ID)
    }
    
    private val ALARM_CHANNEL = "com.aiagent.ai_voice_agent/alarm"
    private val TTS_CHANNEL = "com.aiagent.ai_voice_agent/tts_audio"
    private val CONTACTS_CHANNEL = "com.aiagent.ai_voice_agent/contacts"
    private val APPS_CHANNEL = "com.aiagent.ai_voice_agent/apps"
    private val SYSTEM_CHANNEL = "com.aiagent.ai_voice_agent/system"
    private val SMS_CHANNEL = "com.aiagent.ai_voice_agent/sms"
    private val PHONE_CHANNEL = "com.aiagent.ai_voice_agent/phone"
    private val WIFI_CHANNEL = "com.aiagent.ai_voice_agent/wifi"
    
    private var mediaPlayer: MediaPlayer? = null
    
    // Один scope на все вызовы — корректная отмена + нет утечек
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Защита от двойного нажатия — только один processVoiceCommand за раз
    private val voiceCommandMutex = Mutex()
    
    // Helpers
    private lateinit var appLauncher: AppLauncher
    private lateinit var systemController: SystemController
    private lateinit var contactsHelper: ContactsHelper

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        appLauncher = AppLauncher(this)
        systemController = SystemController(this)
        contactsHelper = ContactsHelper(this)
    }

    // ==== Автопилот: отладочный вход для автотестов голосового пайплайна ====
    // Широковещательное сообщение com.aiagent.ai_voice_agent.TEST_UTTERANCE с extra "wav"=<путь>
    // принимает ТОЛЬКО debug-сборка; в релизе приёмник не регистрируется.
    private var autoPilotReceiver: android.content.BroadcastReceiver? = null

    private fun registerAutoPilotReceiver() {
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebug || autoPilotReceiver != null) return
        autoPilotReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent == null) return
                // Второй режим того же отладочного входа: залить ключи отдельными extras,
                // чтобы автопилот работал на свежем устройстве без ручного ввода через UI.
                val kGroq = intent.getStringExtra("k_groq")
                val kOpenai = intent.getStringExtra("k_openai")
                val kGemini = intent.getStringExtra("k_gemini")
                if (!kGroq.isNullOrEmpty() || !kOpenai.isNullOrEmpty() || !kGemini.isNullOrEmpty()) {
                    EngineManager.setDebugKeys(groq = kGroq, openai = kOpenai, gemini = kGemini)
                    return
                }
                val wav = intent.getStringExtra("wav")
                if (wav.isNullOrEmpty()) {
                    android.util.Log.i("AutoPilot", "BROADCAST без extra 'wav'/'k_*' — игнор")
                    return
                }
                // Аппу недоступен монтированный /sdcard (mount namespace shell) —
                // переводим «/sdcard/Android/data/<pkg>/files/x» в приватный путь.
                val legacy = "/storage/emulated/0/Android/data/com.aiagent.ai_voice_agent/files"
                val priv = context!!.getExternalFilesDir(null)?.absolutePath ?: wav
                val real = wav
                    .replace("/sdcard/Android/data/com.aiagent.ai_voice_agent/files", legacy)
                    .replace(legacy, priv)
                android.util.Log.i("AutoPilot", "INJECT path=$wav real=$real")
                EngineManager.injectTestUtterance(real)
            }
        }
        val filter = android.content.IntentFilter("com.aiagent.ai_voice_agent.TEST_UTTERANCE")
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(autoPilotReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(autoPilotReceiver, filter)
        }
        android.util.Log.i("AutoPilot", "Тестовый приёмник зарегистрирован (debug)")
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        engineInstance = flutterEngine

        // Initialize Kotlin AI Engine
        EngineManager.initialize(this)
        registerAutoPilotReceiver()

        // Engine channel — Flutter calls Kotlin AI Engine directly
        val engineChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.aiagent.ai_voice_agent/engine")
        
        // Подключить канал для логов (Kotlin → Flutter debug panel)
        EngineManager.setEngineChannel(engineChannel)
        
        // Setup continuous dialogue result callback
        EngineManager.onContinuousResult = { text, response ->
            runOnUiThread {
                try {
                    engineChannel.invokeMethod("onContinuousResult", mapOf(
                        "text" to text,
                        "response" to response
                    ))
                } catch (_: Exception) {}
            }
        }
        
        // Setup farewell callback — агент попрощался
        EngineManager.onAgentFarewell = {
            runOnUiThread {
                try {
                    engineChannel.invokeMethod("onAgentFarewell", null)
                } catch (_: Exception) {}
            }
        }
        
        // Setup barge-in callback — пользователь прервал TTS голосом
        EngineManager.onBargeIn = {
            runOnUiThread {
                try {
                    engineChannel.invokeMethod("onBargeIn", null)
                } catch (_: Exception) {}
            }
        }
        
        // Setup pipeline state callback — синхронизация состояния с Flutter
        EngineManager.onPipelineStateChanged = { state ->
            runOnUiThread {
                try {
                    engineChannel.invokeMethod("onPipelineStateChanged", state.name)
                } catch (_: Exception) {}
            }
        }
        
        engineChannel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "processText" -> {
                        val text = call.argument<String>("text") ?: ""
                        engineScope.launch {
                            val response = EngineManager.processText(text)
                            result.success(response)
                        }
                    }
                    "runMusicTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runMusicTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runPipelineTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runPipelineTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runConversationTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runConversationTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runReminderTest" -> {
                        try {
                            val testResult = EngineManager.runReminderTest()
                            result.success(testResult)
                        } catch (e: Exception) {
                            result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                        }
                    }
                    "runAllToolTests" -> {
                        engineScope.launch {
                            try {
                                val testResults = EngineManager.runAllToolTests()
                                result.success(testResults)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "loadTestHistory" -> {
                        try {
                            val history = EngineManager.loadTestHistory()
                            result.success(history)
                        } catch (e: Exception) {
                            result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                        }
                    }
                    "exportTestSession" -> {
                        try {
                            val sessionId = call.argument<String>("sessionId") ?: ""
                            val report = EngineManager.exportTestSession(sessionId)
                            result.success(report)
                        } catch (e: Exception) {
                            result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                        }
                    }
                    "deleteTestSession" -> {
                        try {
                            val sessionId = call.argument<String>("sessionId") ?: ""
                            val success = EngineManager.deleteTestSession(sessionId)
                            result.success(success)
                        } catch (e: Exception) {
                            result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                        }
                    }
                    "runReminderVoiceTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runReminderVoiceTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runReminderAutoTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runReminderAutoTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runNavigationVoiceTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runNavigationVoiceTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runRouteVoiceTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runRouteVoiceTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runReplySmsVoiceTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runReplySmsVoiceTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runScreenshotVoiceTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runScreenshotVoiceTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runExplainScreenVoiceTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runExplainScreenVoiceTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "runShareLocationVoiceTest" -> {
                        engineScope.launch {
                            try {
                                val testResult = EngineManager.runShareLocationVoiceTest()
                                result.success(testResult)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "processVoiceCommand" -> {
                        engineScope.launch {
                            // Если уже выполняется — второй вызов ждёт и сразу получает результат
                            if (voiceCommandMutex.isLocked) {
                                android.util.Log.d("MainActivity", "processVoiceCommand уже выполняется, игнорирую повторный вызов")
                                result.error("BUSY", "Already processing", null)
                                return@launch
                            }
                            voiceCommandMutex.withLock {
                                try {
                                    val resultMap = EngineManager.processVoiceCommand()
                                    result.success(resultMap)
                                } catch (e: Exception) {
                                    result.error("VOICE_CMD_ERROR", e.message ?: "Unknown error", null)
                                }
                            }
                        }
                    }
                    "processCommand" -> {
                        val audioPath = call.argument<String>("audioPath") ?: ""
                        engineScope.launch {
                            try {
                                val file = java.io.File(audioPath)
                                val response = EngineManager.processCommand(file)
                                result.success(response)
                            } catch (e: Exception) {
                                result.error("ENGINE_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "reloadConfig" -> {
                        EngineManager.reloadConfig()
                        result.success(null)
                    }
                    "syncConfig" -> {
                        // Flutter passes API keys to Kotlin (different storage systems)
                        val prefs = applicationContext.getSharedPreferences("ai_voice_agent_config", android.content.Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        call.argument<String>("openaiApiKey")?.let { editor.putString("openai_api_key", it) }
                        call.argument<String>("groqApiKey")?.let { editor.putString("groq_api_key", it) }
                        call.argument<String>("geminiApiKey")?.let { editor.putString("gemini_api_key", it) }
                        call.argument<String>("activeProvider")?.let { editor.putString("active_provider", it) }
                        call.argument<String>("language")?.let { editor.putString("language", it) }
                        call.argument<String>("ttsVoice")?.let { editor.putString("tts_voice", it) }
                        call.argument<String>("agentName")?.let { editor.putString("agent_name", it) }
                        call.argument<Boolean>("wakeWordEnabled")?.let { editor.putBoolean("wake_word_enabled", it) }
                        call.argument<String>("wakeWordName")?.let { editor.putString("wake_word_name", it) }
                        editor.apply()
                        EngineManager.reloadConfig()
                        android.util.Log.d("MainActivity", "Config synced from Flutter")
                        result.success(null)
                    }
                    "applyPreset" -> {
                        val presetId = call.argument<String>("presetId") ?: "current"
                        val preset = com.aiagent.ai_voice_agent.engine.core.PresetManager.ALL_PRESETS.find { it.id == presetId }
                        if (preset != null) {
                            val presetManager = com.aiagent.ai_voice_agent.engine.core.PresetManager(applicationContext)
                            val config = EngineManager.config
                            presetManager.applyPreset(preset, config)
                            EngineManager.reloadConfig()
                            android.util.Log.d("MainActivity", "Preset applied: ${preset.name}")
                            result.success(mapOf(
                                "success" to true,
                                "preset" to preset.name,
                                "provider" to preset.provider,
                                "ttsVoice" to preset.ttsVoice
                            ))
                        } else {
                            result.error("PRESET_NOT_FOUND", "Preset $presetId not found", null)
                        }
                    }
                    "getPresets" -> {
                        val presetManager = com.aiagent.ai_voice_agent.engine.core.PresetManager(applicationContext)
                        val presets = com.aiagent.ai_voice_agent.engine.core.PresetManager.ALL_PRESETS.map { preset ->
                            mapOf<String, Any>(
                                "id" to preset.id,
                                "name" to preset.name,
                                "description" to preset.description,
                                "provider" to preset.provider,
                                "ttsVoice" to preset.ttsVoice,
                                "isActive" to (preset.id == presetManager.activePresetId)
                            )
                        }
                        result.success(presets)
                    }
                    "getTools" -> {
                        result.success(EngineManager.toolRegistry.names)
                    }
                    "getToolDetails" -> {
                        val details = EngineManager.toolRegistry.all.map { tool ->
                            mapOf(
                                "name" to tool.name,
                                "description" to tool.description,
                                "parameters" to tool.parameters.map { param ->
                                    mapOf(
                                        "name" to param.name,
                                        "type" to param.type,
                                        "description" to param.description,
                                        "required" to param.required
                                    )
                                }
                            )
                        }
                        result.success(details)
                    }
                    "runDotArtTest" -> {
                        engineScope.launch {
                            try {
                                val testRunner = com.aiagent.ai_voice_agent.engine.DotArtTestRunner(
                                    this@MainActivity
                                )
                                val results = testRunner.runAll()
                                result.success(results)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "Test failed", null)
                            }
                        }
                    }
                    "runToolTests" -> {
                        engineScope.launch {
                            try {
                                val category = call.argument<String>("category")
                                val results = if (category != null) {
                                    EngineManager.runToolTestsByCategory(category)
                                } else {
                                    EngineManager.runAllToolTests()
                                }
                                result.success(results)
                            } catch (e: Exception) {
                                result.error("TEST_ERROR", e.message ?: "", null)
                            }
                        }
                    }
                    "startContinuousSession" -> {
                        EngineManager.startContinuousSession()
                        result.success(null)
                    }
                    "stopContinuousSession" -> {
                        EngineManager.stopContinuousSession()
                        result.success(null)
                    }
                    "notifyMicPermissionGranted" -> {
                        com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager.startWhenReady()
                        result.success(null)
                    }
                    "startManualListening" -> {
                        // Manual "Слушать" button — same as wake word detected
                        EngineManager.sessionManager.handleEvent(
                            com.aiagent.ai_voice_agent.engine.session.AssistantSessionManager.Event.WAKE_WORD_DETECTED
                        )
                        result.success(null)
                    }
                    "cancelPipeline" -> {
                        EngineManager.cancelPipeline()
                        result.success(null)
                    }
                    "executeTool" -> {
                        val toolName = call.argument<String>("toolName") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val toolParams = call.argument<Map<String, Any?>>("params") ?: emptyMap()
                        engineScope.launch {
                            try {
                                val toolResult = withContext(Dispatchers.IO) {
                                    EngineManager.toolRegistry.execute(toolName, toolParams)
                                }
                                val resultMap = mapOf(
                                    "success" to toolResult.success,
                                    "message" to toolResult.message
                                )
                                result.success(resultMap)
                            } catch (e: Exception) {
                                result.error("TOOL_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "transcribe" -> {
                        val audioPath = call.argument<String>("audioPath") ?: ""
                        engineScope.launch {
                            try {
                                val file = java.io.File(audioPath)
                                val provider = EngineManager.config.activeProvider
                                // Use OpenAI key for Whisper (both providers use same STT)
                                val apiKey = EngineManager.config.openaiApiKey
                                val text = EngineManager.stt.transcribe(
                                    audioFile = file,
                                    apiKey = apiKey,
                                    language = EngineManager.config.language,
                                    provider = provider
                                )
                                result.success(text)
                            } catch (e: Exception) {
                                result.error("STT_ERROR", e.message ?: "Unknown error", null)
                            }
                        }
                    }
                    "stopTts" -> {
                        // Stop TTS playback (barge-in / manual stop)
                        EngineManager.tts.stop()
                        android.util.Log.d("MainActivity", "TTS stopped by user")
                        result.success(true)
                    }
                    "openAccessibilitySettings" -> {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            result.success(true)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to open accessibility settings: ${e.message}")
                            result.error("SETTINGS_ERROR", e.message, null)
                        }
                    }
                    "startForegroundService" -> {
                        try {
                            val intent = Intent(this, com.aiagent.ai_voice_agent.services.AgentForegroundService::class.java).apply {
                                action = com.aiagent.ai_voice_agent.services.AgentForegroundService.ACTION_START
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                            result.success(true)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "startForegroundService error: ${e.message}")
                            result.error("FG_SERVICE_ERROR", e.message, null)
                        }
                    }
                    "stopForegroundService" -> {
                        try {
                            val intent = Intent(this, com.aiagent.ai_voice_agent.services.AgentForegroundService::class.java).apply {
                                action = com.aiagent.ai_voice_agent.services.AgentForegroundService.ACTION_STOP
                            }
                            startService(intent)
                            result.success(true)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "stopForegroundService error: ${e.message}")
                            result.error("FG_SERVICE_ERROR", e.message, null)
                        }
                    }
                    "enableWakeWord" -> {
                        EngineManager.enableWakeWord()
                        result.success(null)
                    }
                    "disableWakeWord" -> {
                        EngineManager.disableWakeWord()
                        result.success(null)
                    }
                    "setWakeWordName" -> {
                        val name = call.argument<String>("name") ?: "клёпа"
                        EngineManager.setWakeWordName(name)
                        result.success(null)
                    }
                    "getWakeWordState" -> {
                        result.success(mapOf(
                            "enabled" to EngineManager.config.wakeWordEnabled,
                            "name" to EngineManager.config.wakeWordName,
                            "isListening" to EngineManager.isWakeWordListening,
                            "isReady" to com.aiagent.ai_voice_agent.services.WakeWordService.isReady
                        ))
                    }
                    else -> result.notImplemented()
                }
            }

        // Alarm channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, ALARM_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "setAlarm" -> {
                        val hour = call.argument<Int>("hour") ?: 0
                        val minute = call.argument<Int>("minute") ?: 0
                        val label = call.argument<String>("label") ?: ""
                        setAlarm(hour, minute, label)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        // TTS channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, TTS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "playAudio" -> playAudio(call.argument<String>("path") ?: "")
                    "stopAudio" -> stopAudio()
                }
                result.success(true)
            }

        // Contacts channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CONTACTS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "searchContacts" -> result.success(contactsHelper.searchContacts(call.argument<String>("query") ?: ""))
                    else -> result.notImplemented()
                }
            }

        // Apps channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, APPS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "openApp" -> result.success(appLauncher.openAppByName(call.argument<String>("name") ?: ""))
                    else -> result.notImplemented()
                }
            }

        // System channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SYSTEM_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "setTimer" -> {
                        systemController.setTimer(
                            call.argument<Int>("seconds") ?: 0,
                            call.argument<String>("label") ?: ""
                        )
                        result.success(null)
                    }
                    "systemControl" -> result.success(systemController.handleSystemControl(
                        call.argument<String>("action") ?: "",
                        call.argument<Int>("value")
                    ))
                    "mediaControl" -> {
                        systemController.handleMediaControl(call.argument<String>("action") ?: "")
                        result.success(null)
                    }
                    "bringToFront" -> {
                        bringActivityToFront()
                        result.success(null)
                    }
                    "minimizeApp" -> {
                        minimizeApp()
                        result.success(null)
                    }
                    "openPlayStore" -> {
                        val packageName = call.argument<String>("packageName") ?: ""
                        openPlayStore(packageName)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        // SMS channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SMS_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "sendSms" -> {
                        val phone = call.argument<String>("phone") ?: ""
                        val message = call.argument<String>("message") ?: ""
                        sendSms(phone, message)
                        result.success(null)
                    }
                    "readSms" -> {
                        val count = call.argument<Int>("count") ?: 10
                        val messages = readSms(count)
                        result.success(messages)
                    }
                    "searchSms" -> {
                        val query = call.argument<String>("query") ?: ""
                        val limit = call.argument<Int>("limit") ?: 20
                        val messages = searchSms(query, limit)
                        result.success(messages)
                    }
                    else -> result.notImplemented()
                }
            }

        // Phone channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PHONE_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "makeCall" -> {
                        val phone = call.argument<String>("phone") ?: ""
                        makeCall(phone)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }

        // WiFi channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, WIFI_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "connectWifi" -> {
                        val ssid = call.argument<String>("ssid") ?: ""
                        val password = call.argument<String>("password") ?: ""
                        val response = connectWifi(ssid, password)
                        result.success(response)
                    }
                    else -> result.notImplemented()
                }
            }

        // Обработка wake intent (Activity поднята из фона через WakeWordService)
        handleWakeIntent(intent)
    }

    private fun setAlarm(hour: Int, minute: Int, label: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("Alarm", "Failed to set alarm: ${e.message}")
        }
    }

    private fun sendSms(phone: String, message: String) {
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            android.util.Log.d("SMS", "SMS sent to $phone (${parts.size} parts)")
        } catch (e: Exception) {
            android.util.Log.e("SMS", "Failed to send SMS: ${e.message}")
        }
    }

    private fun readSms(count: Int): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()
        try {
            // Check if we have SMS read permission
            val hasPermission = checkCallingOrSelfPermission(android.Manifest.permission.READ_SMS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                android.util.Log.w("SMS", "READ_SMS permission not granted")
                return messages
            }

            val uri = android.net.Uri.parse("content://sms/inbox")
            val cursor = contentResolver.query(
                uri,
                arrayOf("address", "body", "date"),
                null,
                null,
                "date DESC LIMIT $count"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val address = it.getString(it.getColumnIndexOrThrow("address")) ?: ""
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow("date"))
                    val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(date))
                    messages.add(mapOf("address" to address, "body" to body, "date" to dateStr))
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("SMS", "Security exception: ${e.message}")
            // Return empty list with error indicator
        } catch (e: Exception) {
            android.util.Log.e("SMS", "Failed to read SMS: ${e.message}")
        }
        return messages
    }

    private fun searchSms(query: String, limit: Int): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()
        try {
            val uri = android.net.Uri.parse("content://sms/inbox")
            val selection = "body LIKE ? OR address LIKE ?"
            val selectionArgs = arrayOf("%$query%", "%$query%")
            val cursor = contentResolver.query(
                uri,
                arrayOf("address", "body", "date"),
                selection,
                selectionArgs,
                "date DESC LIMIT $limit"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val address = it.getString(it.getColumnIndexOrThrow("address")) ?: ""
                    val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                    val date = it.getLong(it.getColumnIndexOrThrow("date"))
                    val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(date))
                    messages.add(mapOf("address" to address, "body" to body, "date" to dateStr))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SMS", "Failed to search SMS: ${e.message}")
        }
        return messages
    }

    private fun makeCall(phone: String) {
        try {
            // Check for CALL_PHONE permission
            val hasPermission = checkCallingOrSelfPermission(android.Manifest.permission.CALL_PHONE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                // Use ACTION_CALL to directly place the call
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = android.net.Uri.parse("tel:$phone")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } else {
                // Fallback to ACTION_DIAL if permission not granted
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:$phone")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("Phone", "Failed to make call: ${e.message}")
            // Fallback to ACTION_DIAL on error
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:$phone")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e2: Exception) {
                android.util.Log.e("Phone", "Fallback also failed: ${e2.message}")
            }
        }
    }

    private fun connectWifi(ssid: String, password: String): Map<String, Any> {
        try {
            // Check location permission (required for WiFi scanning on Android 6+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return mapOf(
                        "success" to false,
                        "message" to "Нет разрешения на определение местоположения. Включите его в настройках для поиска WiFi.",
                        "network_found" to false
                    )
                }
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            // Check if WiFi is enabled
            if (!wifiManager.isWifiEnabled) {
                wifiManager.isWifiEnabled = true
                Thread.sleep(1000) // Wait for WiFi to enable
            }

            // Scan for available networks
            wifiManager.startScan()
            val scanResults = wifiManager.scanResults
            
            // Find the target network
            val targetNetwork = scanResults.find { it.SSID == ssid || it.SSID == "\"$ssid\"" }
            
            if (targetNetwork == null) {
                return mapOf(
                    "success" to false,
                    "message" to "Сеть \"$ssid\" не найдена",
                    "network_found" to false
                )
            }

            // Check if network is open (no security)
            val isOpen = targetNetwork.capabilities.contains("WEP").not() && 
                        targetNetwork.capabilities.contains("WPA").not() && 
                        targetNetwork.capabilities.contains("WPS").not()

            if (isOpen && password.isEmpty()) {
                // Open network - connect directly
                return connectToOpenNetwork(wifiManager, ssid)
            } else if (password.isEmpty()) {
                // Secured network but no password provided
                return mapOf(
                    "success" to false,
                    "message" to "Сеть \"$ssid\" защищена паролем",
                    "needs_password" to true,
                    "network_found" to true
                )
            } else {
                // Secured network with password
                return connectToSecuredNetwork(wifiManager, ssid, password)
            }
        } catch (e: Exception) {
            android.util.Log.e("WiFi", "Failed to connect: ${e.message}")
            return mapOf(
                "success" to false,
                "message" to "Ошибка подключения: ${e.message}",
                "network_found" to true
            )
        }
    }

    private fun connectToOpenNetwork(wifiManager: android.net.wifi.WifiManager, ssid: String): Map<String, Any> {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ use WifiNetworkSpecifier
            val specifier = android.net.wifi.WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .build()

            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            connectivityManager.requestNetwork(request, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    android.util.Log.d("WiFi", "Connected to open network: $ssid")
                }
            })

            return mapOf(
                "success" to true,
                "message" to "Подключение к открытой сети \"$ssid\"...",
                "network_found" to true
            )
        } else {
            // Android 9 and below
            @Suppress("DEPRECATION")
            val config = android.net.wifi.WifiConfiguration().apply {
                SSID = "\"$ssid\""
                allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
            }

            @Suppress("DEPRECATION")
            val networkId = wifiManager.addNetwork(config)
            @Suppress("DEPRECATION")
            val connected = wifiManager.enableNetwork(networkId, true)

            return mapOf(
                "success" to connected,
                "message" to if (connected) "Подключено к открытой сети \"$ssid\"" else "Не удалось подключиться",
                "network_found" to true
            )
        }
    }

    private fun connectToSecuredNetwork(wifiManager: android.net.wifi.WifiManager, ssid: String, password: String): Map<String, Any> {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ use WifiNetworkSpecifier
            val specifier = android.net.wifi.WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            connectivityManager.requestNetwork(request, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    android.util.Log.d("WiFi", "Connected to secured network: $ssid")
                }
            })

            return mapOf(
                "success" to true,
                "message" to "Подключение к сети \"$ssid\"...",
                "network_found" to true
            )
        } else {
            // Android 9 and below
            @Suppress("DEPRECATION")
            val config = android.net.wifi.WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
            }

            @Suppress("DEPRECATION")
            val networkId = wifiManager.addNetwork(config)
            @Suppress("DEPRECATION")
            val connected = wifiManager.enableNetwork(networkId, true)

            return mapOf(
                "success" to connected,
                "message" to if (connected) "Подключено к сети \"$ssid\"" else "Не удалось подключиться. Проверьте пароль.",
                "network_found" to true
            )
        }
    }

    private fun playAudio(path: String) {
        stopAudio()
        try {
            val file = File(path)
            if (!file.exists()) return
            
            val fis = FileInputStream(file)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fis.fd)
                prepare()
                start()
                setOnCompletionListener { 
                    it.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TTS", "Error playing audio: ${e.message}")
        }
    }

    private fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    private fun bringActivityToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun minimizeApp() {
        moveTaskToBack(true)
    }

    private fun openPlayStore(packageName: String) {
        try {
            // Try to open in Play Store app first
            val marketIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$packageName"))
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(marketIntent)
        } catch (e: Exception) {
            // Fallback to web browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(webIntent)
            } catch (e2: Exception) {
                android.util.Log.e("PlayStore", "Failed to open Play Store: ${e2.message}")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWakeIntent(intent)
    }

    private fun handleWakeIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_WAKE_TRIGGERED, false)) {
            android.util.Log.d("MainActivity", "[WAKE] Launched via wake word — pipeline already running")
        }
    }

    override fun onDestroy() {
        stopAudio()
        engineScope.cancel()
        EngineManager.shutdown()
        engineInstance = null
        super.onDestroy()
    }
}
