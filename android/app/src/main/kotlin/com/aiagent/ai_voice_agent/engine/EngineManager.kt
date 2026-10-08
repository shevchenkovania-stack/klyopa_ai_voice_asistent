package com.aiagent.ai_voice_agent.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aiagent.ai_voice_agent.engine.core.AgentMemory
import com.aiagent.ai_voice_agent.engine.core.EngineConfig
import com.aiagent.ai_voice_agent.engine.core.ToolRegistry
import com.aiagent.ai_voice_agent.engine.core.VoiceAgent
import com.aiagent.ai_voice_agent.engine.core.agent.MultiAgentOrchestrator
import com.aiagent.ai_voice_agent.engine.pipeline.PipelineOrchestrator
import com.aiagent.ai_voice_agent.engine.pipeline.PipelineState
import com.aiagent.ai_voice_agent.engine.stt.SttEngine
import com.aiagent.ai_voice_agent.engine.tts.TtsEngine
import com.aiagent.ai_voice_agent.engine.tools.*
import com.aiagent.ai_voice_agent.engine.session.AssistantSessionManager
import com.aiagent.ai_voice_agent.engine.session.WakeWordAudioConsumer
import com.aiagent.ai_voice_agent.engine.session.SttAudioConsumer
import com.aiagent.ai_voice_agent.services.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import io.flutter.plugin.common.MethodChannel

/**
 * Central hub for the Kotlin AI Engine.
 * Singleton — holds all engine components.
 * Flutter calls this via MethodChannel.
 *
 * Responsibilities:
 * - Init all components (config, memory, tools, TTS, STT, agent)
 * - Wake word management
 * - Config reload
 * - Flutter bridge (callbacks, logs)
 * - Test methods
 *
 * Pipeline execution delegated to [PipelineOrchestrator].
 */

data class IntentMatch(val name: String, val params: Map<String, Any?>? = null)
interface IntentDetector {
    fun detect(text: String): IntentMatch?
}
interface PermissionManager {
    fun check(toolName: String, params: Map<String, Any?>): Boolean
    fun getLevel(toolName: String): PermissionLevel
}

object EngineManager {
    private const val TAG = "EngineManager"

    lateinit var context: Context
        private set

    lateinit var config: EngineConfig
        private set

    lateinit var memory: AgentMemory
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var agent: VoiceAgent
        private set

    lateinit var orchestrator: MultiAgentOrchestrator
        private set

    lateinit var tts: TtsEngine
        private set

    lateinit var stt: SttEngine
        private set

    lateinit var continuousDialogue: ContinuousDialogueManager
        private set

    lateinit var pipeline: PipelineOrchestrator
        private set

    // ===== Новый слой: Brain + Heart =====
    lateinit var sessionManager: AssistantSessionManager
        private set

    lateinit var sttConsumer: SttAudioConsumer
        private set

    // Тестовые методы — вынесены в отдельный класс
    private lateinit var testMethods: com.aiagent.ai_voice_agent.engine.test.EngineTestMethods

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    var isInitialized = false
        private set

    // Музыка — делегируем в PipelineOrchestrator
    var musicPlayer: android.media.MediaPlayer?
        get() = if (::pipeline.isInitialized) pipeline.musicPlayer else null
        set(value) { if (::pipeline.isInitialized) pipeline.musicPlayer = value }

    // Wake word (offline Vosk) — делегировано WakeWordService
    var isWakeWordListening = false
        private set

    // Job текущего pipeline (wake word → voice command).
    @Volatile
    private var pipelineJob: Job? = null
    @Volatile
    private var pipelineGeneration = 0L

    private var _intentDetector: IntentDetector? = null
    private var _permissionManager: PermissionManager? = null

    // ==================== Callbacks (Flutter bridge) ====================

    var onContinuousResult: ((text: String, response: String) -> Unit)? = null
    var onStatusUpdate: ((status: String) -> Unit)? = null
    var onAgentFarewell: (() -> Unit)? = null
    var onPipelineStateChanged: ((PipelineState) -> Unit)? = null
    var onToolConfirmed: ((String) -> Unit)? = null
    var onCancelled: (() -> Unit)? = null
    var onBargeIn: (() -> Unit)? = null

    // Мост для логов в Flutter UI
    private var engineChannel: MethodChannel? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun setEngineChannel(channel: MethodChannel) {
        engineChannel = channel
        Log.d(TAG, "Engine channel set for logging")
    }

    fun sendStatus(status: String) {
        mainHandler.post {
            try {
                engineChannel?.invokeMethod("onStatusUpdate", status)
            } catch (_: Exception) {}
            onStatusUpdate?.invoke(status)
        }
    }

    fun logToFlutter(tag: String, level: String, message: String, details: String? = null) {
        mainHandler.post {
            try {
                engineChannel?.invokeMethod("onDebugLog", mapOf(
                    "tag" to tag, "level" to level, "message" to message, "details" to details
                ))
            } catch (e: Exception) {
                Log.w(TAG, "logToFlutter failed: ${e.message}")
            }
        }
    }

    // ==================== Initialize ====================

    fun initialize(context: Context) {
        if (isInitialized) return
        this.context = context.applicationContext

        Log.d(TAG, "Initializing Kotlin AI Engine...")

        config = EngineConfig(this.context)
        memory = AgentMemory(this.context)
        toolRegistry = ToolRegistry()

        com.aiagent.ai_voice_agent.engine.ReminderScheduler.ensureChannel(this.context)
        com.aiagent.ai_voice_agent.engine.ReminderScheduler.rescheduleAll(this.context)

        tts = TtsEngine(this.context).apply {
            voice = when {
                config.ttsVoice == "android" -> "android"
                config.ttsVoice.startsWith("ru-RU-") -> config.ttsVoice
                else -> "android"
            }
            openaiApiKey = config.openaiApiKey
            initAndroidTts()
        }
        stt = SttEngine()
        continuousDialogue = ContinuousDialogueManager()

        // Register all tools
        registerTools()

        _intentDetector = LocalIntentDetector()
        _permissionManager = LocalPermissionManager()

        agent = VoiceAgent(this.context, toolRegistry, memory).apply {
            groqApiKey = config.groqApiKey
            geminiApiKey = config.geminiApiKey
            openaiApiKey = config.openaiApiKey
            activeProvider = config.activeProvider
            agentName = config.agentName
            intentDetector = _intentDetector
            permissionManager = _permissionManager
        }

        val uncovered = com.aiagent.ai_voice_agent.engine.core.agent.AgentDomain.validate(toolRegistry.names.toSet())
        if (uncovered.isNotEmpty()) {
            Log.w(TAG, "Инструменты не привязаны к доменам: $uncovered")
        }

        orchestrator = MultiAgentOrchestrator(this.context, toolRegistry, memory).apply {
            reloadConfig(
                groqApiKey = config.groqApiKey,
                geminiApiKey = config.geminiApiKey,
                openaiApiKey = config.openaiApiKey,
                activeProvider = config.activeProvider,
                agentName = config.agentName
            )
            intentDetector = _intentDetector
        }

        // Тестовые методы — вынесены в отдельный класс
        testMethods = com.aiagent.ai_voice_agent.engine.test.EngineTestMethods(this.context, orchestrator)

        // Create PipelineOrchestrator — делегируем pipeline execution
        pipeline = PipelineOrchestrator(
            stt = stt, tts = tts, orchestrator = orchestrator,
            toolRegistry = toolRegistry, config = config,
            context = this.context, continuousDialogue = continuousDialogue,
            scope = scope
        ).apply {
            intentDetector = _intentDetector
            permissionManager = _permissionManager

            // Wire callbacks → Flutter bridge
            onPipelineStateChanged = { state ->
                mainHandler.post {
                    try { engineChannel?.invokeMethod("onPipelineStateChanged", state.name) } catch (_: Exception) {}
                    this@EngineManager.onPipelineStateChanged?.invoke(state)
                }
            }
            onStatusUpdate = { status -> sendStatus(status) }
            onContinuousResult = { text, response ->
                mainHandler.post {
                    try { engineChannel?.invokeMethod("onContinuousResult", mapOf("text" to text, "response" to response)) } catch (_: Exception) {}
                    this@EngineManager.onContinuousResult?.invoke(text, response)
                }
            }
            onAgentFarewell = {
                mainHandler.post {
                    try { engineChannel?.invokeMethod("onAgentFarewell", null) } catch (_: Exception) {}
                    this@EngineManager.onAgentFarewell?.invoke()
                }
            }
            onCancelled = { this@EngineManager.onCancelled?.invoke() }
            onToolConfirmed = { tool -> this@EngineManager.onToolConfirmed?.invoke(tool) }
            onBargeIn = {
                mainHandler.post {
                    try { engineChannel?.invokeMethod("onBargeIn", null) } catch (_: Exception) {}
                    this@EngineManager.onBargeIn?.invoke()
                }
            }
            logBridge = { tag, level, msg, details -> logToFlutter(tag, level, msg, details) }

            // Setup continuous dialogue + TTS callbacks (only for new architecture)
            if (config.useNewSessionArchitecture) {
                setupContinuousDialogueCallbacks(onResumeWakeWord = {
                    sessionManager.handleEvent(AssistantSessionManager.Event.TTS_FINISHED)
                })
            }
        }

        // ===== Feature flags: новая архитектура (SessionManager + WakeWord + Continuous) =====
        if (config.useNewSessionArchitecture) {
            // Wire WakeWordService → SessionManager (через событие)
            WakeWordService.onWakeWordDetected = { text ->
                Log.i(TAG, "[WAKE] Wake word detected: '$text' — передаём в SessionManager")
                logToFlutter("WakeWord", "success", "Пробуждение! Сказано: $text")
                sessionManager.handleEvent(AssistantSessionManager.Event.WAKE_WORD_DETECTED)
            }

            // ОТКЛЮЧАЕМ СТАРОЕ
            continuousDialogue.stopSession(cancelled = true)
            Log.d(TAG, "[Init] Old ContinuousDialogueManager disabled")

            // НОВЫЙ СЛОЙ: Brain + Heart
            sessionManager = AssistantSessionManager

            com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager.registerConsumer(
                AssistantSessionManager.AudioOwner.WAKE_WORD,
                WakeWordAudioConsumer()
            )
            sttConsumer = SttAudioConsumer(sessionManager, cacheDir = this.context.cacheDir)
            com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager.registerConsumer(
                AssistantSessionManager.AudioOwner.STT,
                sttConsumer
            )

            sttConsumer.onSpeechComplete = { wavFile ->
                Log.i(TAG, "[STT] Speech complete — launching pipeline with ${wavFile.length()} bytes")
                pipelineJob?.cancel()
                val gen = ++pipelineGeneration
                pipelineJob = scope.launch {
                    try {
                        pipeline.processContinuousSpeech(wavFile, onResumeWakeWord = {
                            sessionManager.handleEvent(AssistantSessionManager.Event.TTS_FINISHED)
                        })
                    } catch (e: CancellationException) {
                        Log.d(TAG, "[STT] Pipeline cancelled")
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "[STT] Pipeline error: ${e.message}", e)
                    } finally {
                        if (pipelineGeneration == gen) pipelineJob = null
                    }
                }
            }

            sessionManager.onAudioOwnerChanged = { newOwner ->
                com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager.setOwner(newOwner)
            }

            sessionManager.onStateChanged = { oldState, newState ->
                val pipelineState = when (newState) {
                    AssistantSessionManager.State.IDLE -> PipelineState.IDLE
                    AssistantSessionManager.State.LISTENING -> PipelineState.LISTENING
                    AssistantSessionManager.State.THINKING -> PipelineState.PROCESSING
                    AssistantSessionManager.State.SPEAKING -> PipelineState.TTS_SPEAKING
                    AssistantSessionManager.State.CONVERSING -> PipelineState.LISTENING
                    AssistantSessionManager.State.FAREWELL -> PipelineState.IDLE
                }
                onPipelineStateChanged?.invoke(pipelineState)
            }

            pipeline.onTtsStarted = {
                sttConsumer.ttsPlaying = true
                sessionManager.handleEvent(AssistantSessionManager.Event.TTS_STARTED)
            }
            pipeline.onTtsFinished = {
                sttConsumer.ttsPlaying = false
                sessionManager.handleEvent(AssistantSessionManager.Event.TTS_FINISHED)
            }

            // СТАРТ: WakeWordService + IDLE mode
            if (config.enableForegroundService) {
                config.wakeWordEnabled = true
                enableWakeWord()
            }
            sessionManager.startInIdle()
        } else {
            // ===== СТАРЫЙ РЕЖИМ: только кнопка, без SessionManager/WakeWord/Continuous =====
            Log.d(TAG, "[Init] ⚡ SIMPLE MODE: button only, no SessionManager/WakeWord/Continuous")
            continuousDialogue.stopSession(cancelled = true)

            // TTS callbacks — простые, без session manager
            pipeline.onTtsStarted = {
                Log.d(TAG, "[TTS] Started (simple mode)")
            }
            pipeline.onTtsFinished = {
                Log.d(TAG, "[TTS] Finished (simple mode)")
            }
        }

        isInitialized = true
        Log.d(TAG, "Kotlin AI Engine initialized. Tools: ${toolRegistry.names}, simpleMode=${!config.useNewSessionArchitecture}")
    }

    // ==================== Wake Word ====================

    private fun onWakeWordDetected() {
        // Теперь управление через SessionManager
        // Этот метод оставлен для обратной совместимости
        pipelineJob?.cancel()
        val gen = ++pipelineGeneration
        pipelineJob = scope.launch {
            try {
                val result = pipeline.executeVoiceCommand(onResumeWakeWord = {
                    sessionManager.handleEvent(AssistantSessionManager.Event.TTS_FINISHED)
                })
                Log.d(TAG, "[WAKE] Pipeline result: text='${result["text"]}', response='${result["response"]}'")
            } catch (e: CancellationException) {
                Log.d(TAG, "[WAKE] Pipeline cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[WAKE] Pipeline error: ${e.message}", e)
            } finally {
                if (pipelineGeneration == gen) {
                    pipelineJob = null
                }
            }
        }
    }

    fun enableWakeWord() {
        if (!isInitialized) return
        config.wakeWordEnabled = true
        isWakeWordListening = true
        logToFlutter("WakeWord", "info", "Wake word ВКЛЮЧЁН: \"${config.wakeWordName}\"")

        // Запускаем WakeWordService (тупой процессор — держит Vosk в памяти)
        val intent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun disableWakeWord() {
        config.wakeWordEnabled = false
        isWakeWordListening = false
        val intent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_STOP
        }
        context.startService(intent)
        logToFlutter("WakeWord", "info", "Wake word ВЫКЛЮЧЕН")
    }

    fun setWakeWordName(name: String) {
        val normalized = name.lowercase().trim()
        if (config.wakeWordName == normalized) return
        config.wakeWordName = normalized
        if (WakeWordService.isRunning) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = WakeWordService.ACTION_SET_WAKE_WORD
                putExtra(WakeWordService.EXTRA_WAKE_WORD, normalized)
            }
            context.startService(intent)
        }
        Log.d(TAG, "[WAKE] Wake word name updated: '$normalized'")
    }

    // ==================== Shutdown ====================

    /**
     * Полная очистка всех ресурсов.
     * Вызывать из Activity.onDestroy().
     */
    fun shutdown() {
        if (!isInitialized) return
        Log.d(TAG, "Shutting down engine...")

        // 1. Session Manager (only if new architecture)
        if (config.useNewSessionArchitecture) {
            try { sessionManager.shutdown() } catch (_: Exception) {}
        }

        // 2. Audio Session Manager
        try { com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager.shutdown() } catch (_: Exception) {}

        // 3. Wake word
        disableWakeWord()

        // 4. TTS
        try { tts.shutdown() } catch (_: Exception) {}

        // 5. Continuous dialogue
        try { continuousDialogue.stopSession(cancelled = true) } catch (_: Exception) {}

        // 6. Coroutine scope
        scope.cancel()

        isInitialized = false
        Log.d(TAG, "Engine shut down")
    }

    // ==================== Config ====================

    fun reloadConfig() {
        if (!isInitialized) return
        agent.apply {
            groqApiKey = config.groqApiKey
            geminiApiKey = config.geminiApiKey
            openaiApiKey = config.openaiApiKey
            activeProvider = config.activeProvider
            agentName = config.agentName
        }
        orchestrator.reloadConfig(
            groqApiKey = config.groqApiKey, geminiApiKey = config.geminiApiKey, openaiApiKey = config.openaiApiKey,
            activeProvider = config.activeProvider, agentName = config.agentName
        )
        tts.voice = when {
            config.ttsVoice == "android" -> "android"
            config.ttsVoice.startsWith("ru-RU-") -> config.ttsVoice
            else -> "android"
        }
        tts.openaiApiKey = config.openaiApiKey
        setWakeWordName(config.wakeWordName)
        if (config.useNewSessionArchitecture && config.enableForegroundService) {
            if (config.wakeWordEnabled && !WakeWordService.isRunning) enableWakeWord()
            else if (!config.wakeWordEnabled && WakeWordService.isRunning) disableWakeWord()
        }
        Log.d(TAG, "Config reloaded: provider=${config.activeProvider}, name=${config.agentName}, voice=${tts.voice}")
    }

    // ==================== Pipeline Control (delegate to PipelineOrchestrator) ====================

    fun setIntentDetector(detector: IntentDetector) {
        _intentDetector = detector
        pipeline.intentDetector = detector
        Log.d(TAG, "IntentDetector set")
    }

    fun setPermissionManager(pm: PermissionManager) {
        _permissionManager = pm
        pipeline.permissionManager = pm
        Log.d(TAG, "PermissionManager set")
    }

    fun cancelPipeline() {
        Log.d(TAG, "cancelPipeline called")
        pipelineJob?.cancel()
        pipelineJob = null
        pipeline.cancelPipeline()
    }

    fun getCurrentPipelineState(): PipelineState = pipeline.getCurrentState(continuousDialogue.isSessionActive)

    // ==================== Continuous Dialogue ====================

    fun startContinuousSession(config: ContinuousDialogueManager.VadConfig? = null) {
        if (!isInitialized) { Log.w(TAG, "Engine not initialized"); return }
        if (!this.config.useNewSessionArchitecture) {
            Log.d(TAG, "startContinuousSession — simple mode: no-op (button only)")
            return
        }
        Log.d(TAG, "startContinuousSession — new arch: SessionManager owns mic, legacy CDM skipped")
        if (!WakeWordService.isRunning && this.config.wakeWordEnabled) {
            enableWakeWord()
        }
    }

    fun stopContinuousSession(cancelled: Boolean = false) {
        Log.d(TAG, "stopContinuousSession — new arch: no-op (SessionManager owns mic)")
        // New architecture: SessionManager handles lifecycle. Nothing to stop.
    }

    // ==================== Public Pipeline Methods (delegate) ====================

    suspend fun processVoiceCommand(): Map<String, String> =
        pipeline.executeVoiceCommand(onResumeWakeWord = {
            if (config.wakeWordEnabled) {
                WakeWordService.resetTrigger()
            }
        })

    suspend fun processCommand(audioFile: java.io.File): String =
        pipeline.executeCommand(audioFile)

    suspend fun processText(text: String): String =
        pipeline.executeText(text)

    suspend fun processTextForTest(text: String): String =
        pipeline.executeTextForTest(text)

    fun duckMusicForRecording() = pipeline.duckMusicForRecording()
    fun restoreMusicVolume() = pipeline.restoreMusicVolume()

    // ==================== Tool Registration (delegate to ToolRegistrar) ====================

    private fun registerTools() {
        com.aiagent.ai_voice_agent.engine.tools.ToolRegistrar.registerAll(toolRegistry, context.applicationContext, memory)
    }

    // ==================== Test Methods (delegate to EngineTestMethods) ====================

    fun runReminderTest(): Map<String, String> = testMethods.runReminderTest()
    suspend fun runReminderVoiceTest(): Map<String, String> = testMethods.runReminderVoiceTest()
    suspend fun runReminderAutoTest(): Map<String, String> = testMethods.runReminderAutoTest()
    suspend fun runNavigationVoiceTest(): Map<String, String> = testMethods.runNavigationVoiceTest()
    suspend fun runRouteVoiceTest(): Map<String, String> = testMethods.runRouteVoiceTest()
    suspend fun runReplySmsVoiceTest(): Map<String, String> = testMethods.runReplySmsVoiceTest()
    suspend fun runScreenshotVoiceTest(): Map<String, String> = testMethods.runScreenshotVoiceTest()
    suspend fun runExplainScreenVoiceTest(): Map<String, String> = testMethods.runExplainScreenVoiceTest()
    suspend fun runShareLocationVoiceTest(): Map<String, String> = testMethods.runShareLocationVoiceTest()
    suspend fun runMusicTest(): Map<String, String> = testMethods.runMusicTest()
    suspend fun runAllToolTests(): List<Map<String, Any>> = testMethods.runAllToolTests()
    fun loadTestHistory(): String = testMethods.loadTestHistory()
    fun exportTestSession(sessionId: String): String = testMethods.exportTestSession(sessionId)
    fun deleteTestSession(sessionId: String): Boolean = testMethods.deleteTestSession(sessionId)
    suspend fun runToolTestsByCategory(category: String): List<Map<String, Any>> = testMethods.runToolTestsByCategory(category)
    suspend fun runPipelineTest(): Map<String, Any> = testMethods.runPipelineTest()
    suspend fun runConversationTest(): Map<String, Any> = testMethods.runConversationTest()
}
