package com.aiagent.ai_voice_agent.engine.pipeline

import android.content.Context
import android.util.Log
import com.aiagent.ai_voice_agent.engine.ContinuousDialogueManager
import com.aiagent.ai_voice_agent.engine.IntentDetector
import com.aiagent.ai_voice_agent.engine.PermissionManager
import com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager
import com.aiagent.ai_voice_agent.engine.core.EngineConfig
import com.aiagent.ai_voice_agent.engine.core.ToolRegistry
import com.aiagent.ai_voice_agent.engine.core.agent.MultiAgentOrchestrator
import com.aiagent.ai_voice_agent.engine.stt.SttEngine
import com.aiagent.ai_voice_agent.engine.tts.TtsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple step timer for pipeline performance debugging.
 */
class PipelineTimer(private val tag: String) {
    private val start = System.currentTimeMillis()
    private var last = start

    fun step(name: String) {
        val now = System.currentTimeMillis()
        val fromStart = now - start
        val fromLast = now - last
        last = now
        Log.d(tag, "[TIMER] $name: +${fromLast}ms (total=${fromStart}ms)")
    }

    fun total(): Long = System.currentTimeMillis() - start
}

/**
 * PipelineOrchestrator — управляет голосовым pipeline:
 * запись → STT → Agent → TTS.
 *
 * Извлечён из EngineManager для разделения ответственностей:
 * - EngineManager: инициализация, wiring, wake word, Flutter bridge
 * - PipelineOrchestrator: pipeline execution, VAD, echo detection, music ducking
 */
class PipelineOrchestrator(
    val stt: SttEngine,
    val tts: TtsEngine,
    val orchestrator: MultiAgentOrchestrator,
    val toolRegistry: ToolRegistry,
    val config: EngineConfig,
    val context: Context,
    val continuousDialogue: ContinuousDialogueManager,
    val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PipelineOrchestrator"
    }

    // ==================== Dependencies (set by EngineManager) ====================

    var intentDetector: IntentDetector? = null
    var permissionManager: PermissionManager? = null

    @Volatile
    var lastAgentResponse: String = ""

    // Музыка — ссылка на MediaPlayer из EngineManager
    @Volatile
    var musicPlayer: android.media.MediaPlayer? = null
    @Volatile
    private var wasMusicPlaying = false

    // ==================== Callbacks ====================

    var onPipelineStateChanged: ((PipelineState) -> Unit)? = null
    var onStatusUpdate: ((String) -> Unit)? = null
    var onContinuousResult: ((text: String, response: String) -> Unit)? = null
    var onAgentFarewell: (() -> Unit)? = null
    var onCancelled: (() -> Unit)? = null
    var onToolConfirmed: ((String) -> Unit)? = null
    var onBargeIn: (() -> Unit)? = null
    var onTtsFinished: (() -> Unit)? = null
    var onTtsStarted: (() -> Unit)? = null

    // Мост для логов в Flutter
    var logBridge: ((tag: String, level: String, message: String, details: String?) -> Unit)? = null

    private fun log(tag: String, level: String, message: String, details: String? = null) {
        logBridge?.invoke(tag, level, message, details)
    }

    private fun sendStatus(status: String) {
        onStatusUpdate?.invoke(status)
    }

    /**
     * Единый метод: смена состояния + обновление статус-текста.
     * Единственный источник истины для UI.
     */
    private fun transitionTo(state: PipelineState, status: String = "") {
        onPipelineStateChanged?.invoke(state)
        sendStatus(status)
    }

    // ==================== Setup ====================

    /**
     * Настроить callbacks ContinuousDialogueManager и TTS.
     * Вызывается один раз при инициализации EngineManager.
     */
    fun setupContinuousDialogueCallbacks(
        onResumeWakeWord: () -> Unit
    ) {
        continuousDialogue.onSpeechDetected = {
            duckMusicForRecording()
        }

        continuousDialogue.onSpeechResult = { audioFile, cancelled ->
            if (cancelled) {
                Log.d(TAG, "Speech result cancelled, skipping processing")
                onCancelled?.invoke()
            } else {
                scope.launch {
                    processContinuousSpeech(audioFile, onResumeWakeWord)
                }
            }
        }

        continuousDialogue.onBargeIn = {
            duckMusicForRecording()
            tts.stop()
            onBargeIn?.invoke()
        }

        continuousDialogue.onProcessingTimeout = {
            Log.w(TAG, "Processing timeout in continuous dialogue")
            sendStatus("")
        }

        tts.onTtsStarted = {
            continuousDialogue.onTtsStarted()
        }

        tts.onTtsFinished = {
            continuousDialogue.onTtsFinished()
            onTtsFinished?.invoke()
        }

        tts.onTtsInterrupted = {
            Log.d(TAG, "TTS interrupted, continuous dialogue handles barge-in")
            onTtsFinished?.invoke()  // Notify EngineManager to clear ttsPlaying flag
        }
    }

    // ==================== Pipeline Execution ====================

    /**
     * Полный голосовой pipeline: запись → STT → Agent → TTS.
     * Вызывается при нажатии кнопки микрофона или при wake word.
     */
    suspend fun executeVoiceCommand(onResumeWakeWord: () -> Unit): Map<String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== executeVoiceCommand START ===")
        transitionTo(PipelineState.LISTENING, "Настраиваюсь...")

        val wasContinuousActive = continuousDialogue.isSessionActive
        if (wasContinuousActive) {
            Log.d(TAG, "Continuous session active — приостанавливаем для executeVoiceCommand")
            continuousDialogue.stopSession(cancelled = false)
        }

        var isFarewellInThisCommand = false

        try {
            duckMusicForRecording()

            // Step 1: Record audio with VAD
            sendStatus("🎤 Слушаю...")
            val audioFile = recordAudioWithVAD()
            restoreMusicVolume()

            if (audioFile == null) {
                log("VAD", "info", "Тишина — речь не обнаружена")
                transitionTo(PipelineState.IDLE)
                return@withContext mapOf("text" to "", "response" to "")
            }
            Log.d(TAG, "Recorded: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
            log("VAD", "success", "Речь записана", "${audioFile.length()} байт")

            // Step 2: STT — prefer Groq (free, no quota) if available
            transitionTo(PipelineState.STT_RUNNING, "🧠 Распознаю...")
            val sttProvider = if (config.groqApiKey.isNotEmpty()) "groq" else "openai"
            val sttApiKey = if (sttProvider == "groq") config.groqApiKey else config.openaiApiKey
            Log.d(TAG, "STT: provider=$sttProvider")
            val text = stt.transcribe(
                audioFile = audioFile,
                apiKey = sttApiKey,
                language = config.language,
                provider = sttProvider
            )
            Log.d(TAG, "STT: '$text'")

            if (text.isEmpty()) {
                log("STT", "warning", "STT: пусто")
                transitionTo(PipelineState.IDLE)
                return@withContext mapOf("text" to "", "response" to "Не расслышал. Повтори.")
            }

            // Step 2.5: IntentDetector fast path
            intentDetector?.let { detector ->
                val match = detector.detect(text)
                if (match != null) {
                    Log.d(TAG, "IntentDetector matched: ${match.name}")
                    permissionManager?.let { pm ->
                        if (!pm.check(match.name, match.params ?: emptyMap())) {
                            val denyResponse = "Действие '${match.name}' отклонено"
                            lastAgentResponse = denyResponse
                            onContinuousResult?.invoke(text, denyResponse)
                            transitionTo(PipelineState.IDLE)
                            return@withContext mapOf("text" to text, "response" to denyResponse)
                        }
                    }
                    transitionTo(PipelineState.TOOL_EXECUTING)
                    val toolResult = toolRegistry.executeWithRetry(match.name, match.params ?: emptyMap())
                    val response = if (toolResult.success) "Готово: ${toolResult.message}" else "Ошибка: ${toolResult.message}"
                    lastAgentResponse = response
                    if (response.isNotEmpty()) {
                        transitionTo(PipelineState.TTS_SPEAKING, "🗣️ Отвечаю...")
                        tts.speak(response)
                    }
                    transitionTo(PipelineState.IDLE)
                    return@withContext mapOf("text" to text, "response" to response)
                }
            }

            // Step 3: Agent
            transitionTo(PipelineState.AGENT_RUNNING, "💭 Думаю...")
            // Филлер «Секунду...» убран из 0.1: он глушил коллбэки и мог оставить Клёпу немой.
            val response = orchestrator.process(text)

            Log.d(TAG, "Agent: $response")
            log("Agent", "success", "Ответ агента", response.take(80))
            lastAgentResponse = response

            // Step 4: TTS
            if (response.isNotEmpty()) {
                transitionTo(PipelineState.TTS_SPEAKING, "🗣️ Отвечаю...")
                tts.speak(response)
            }

            if (isFarewellResponse(response)) {
                Log.d(TAG, "Farewell detected")
                isFarewellInThisCommand = true
                onAgentFarewell?.invoke()
            }

            transitionTo(PipelineState.IDLE)
            return@withContext mapOf("text" to text, "response" to response)
        } catch (e: CancellationException) {
            Log.d(TAG, "executeVoiceCommand cancelled")
            throw e // Не проглатываем cancellation
        } catch (e: Exception) {
            Log.e(TAG, "executeVoiceCommand error: ${e.message}", e)
            transitionTo(PipelineState.ERROR)
            return@withContext mapOf("text" to "", "response" to "Ошибка: ${e.message}")
        } finally {
            if (wasContinuousActive && !isFarewellInThisCommand) {
                Log.d(TAG, "Resuming continuous session")
                continuousDialogue.startSession()
            }
            if (config.wakeWordEnabled && !wasContinuousActive) {
                onResumeWakeWord()
            }
            Log.d(TAG, "=== executeVoiceCommand END ===")
        }
    }

    /**
     * Обработка уже записанной речи из continuous dialogue.
     */
    suspend fun processContinuousSpeech(audioFile: File, onResumeWakeWord: () -> Unit) {
        val timer = PipelineTimer(TAG)
        val pipeStart = System.currentTimeMillis()
        Log.i(TAG, "┌──────────────────────────────────────────────────")
        Log.i(TAG, "│ PIPELINE START  file=${audioFile.length()}B")
        Log.i(TAG, "├──────────────────────────────────────────────────")
        try {
            // ── STEP 1: STT ──
            Log.i(TAG, "│ [1/6] STT  ▶ transcribing (${config.groqApiKey.isNotEmpty().let { if (it) "groq" else "openai" }})...")
            timer.step("VAD → STT start")
            transitionTo(PipelineState.STT_RUNNING, "🧠 Распознаю...")
            val sttProvider = if (config.groqApiKey.isNotEmpty()) "groq" else "openai"
            val sttApiKey = if (sttProvider == "groq") config.groqApiKey else config.openaiApiKey
            val text = stt.transcribe(
                audioFile = audioFile,
                apiKey = sttApiKey,
                language = config.language,
                provider = sttProvider
            )
            val sttMs = System.currentTimeMillis() - pipeStart
            timer.step("STT done: '${text.take(30)}'")
            Log.i(TAG, "│ [1/6] STT  ◀ '${text.take(40)}' (+${sttMs}ms)")

            if (text.isEmpty()) {
                Log.i(TAG, "│ [1/6] STT  ✗ empty result — skipping")
                Log.i(TAG, "└──────────────────────────────────────────────────")
                if (continuousDialogue.isCancelled) { onCancelled?.invoke(); return }
                restoreMusicVolume()
                onContinuousResult?.invoke("", "")
                return
            }

            if (continuousDialogue.isCancelled) { onCancelled?.invoke(); return }

            log("STT", "success", "Continuous STT", text.take(80))

            // ── STEP 2: Echo check ──
            if (isEcho(text, lastAgentResponse)) {
                Log.i(TAG, "│ [2/6] ECHO ✗ filtered (similarity too high)")
                Log.i(TAG, "└──────────────────────────────────────────────────")
                log("Echo", "warning", "Эхо отфильтровано", text.take(40))
                restoreMusicVolume()
                return
            }
            Log.i(TAG, "│ [2/6] ECHO ✓ not echo")

            // ── STEP 2.5: Whisper hallucination filter ──
            if (isWhisperHallucination(text)) {
                Log.i(TAG, "│ [2.5/6] HALLUCINATION ✗ filtered: '${text.take(40)}'")
                Log.i(TAG, "└──────────────────────────────────────────────────")
                log("Hallucination", "warning", "Whisper галлюцинация отфильтрована", text.take(40))
                restoreMusicVolume()
                return
            }
            Log.i(TAG, "│ [2.5/6] HALLUCINATION ✓ not hallucination")

            // ── STEP 2.7: Farewell detection in user speech ──
            if (isFarewellText(text)) {
                Log.i(TAG, "│ [2.7/6] FAREWELL ✓ user said goodbye: '${text.take(40)}'")
                Log.i(TAG, "└──────────────────────────────────────────────────")
                // Respond with farewell
                val farewellResponse = "пока!"
                lastAgentResponse = farewellResponse
                if (farewellResponse.isNotEmpty()) {
                    transitionTo(PipelineState.TTS_SPEAKING, "🗣️ Отвечаю...")
                    onTtsStarted?.invoke()
                    tts.speak(farewellResponse)
                }
                continuousDialogue.stopSession(cancelled = false)
                onAgentFarewell?.invoke()
                onContinuousResult?.invoke(text, farewellResponse)
                return
            }

            // ── STEP 3: Intent detection ──
            var intentMatch = false
            intentDetector?.let { detector ->
                val match = detector.detect(text)
                if (match != null) {
                    intentMatch = true
                    Log.i(TAG, "│ [3/6] INTENT ✓ '${match.name}'")
                    permissionManager?.let { pm ->
                        if (!pm.check(match.name, match.params ?: emptyMap())) {
                            val denyResponse = "Действие '$match.name' отклонено"
                            lastAgentResponse = denyResponse
                            onContinuousResult?.invoke(text, denyResponse)
                            Log.i(TAG, "└──────────────────────────────────────────────────")
                            return
                        }
                    }
                    transitionTo(PipelineState.TOOL_EXECUTING)
                    onToolConfirmed?.invoke(match.name)
                    timer.step("IntentDetector → tool: ${match.name}")
                    val toolResult = toolRegistry.executeWithRetry(match.name, match.params ?: emptyMap())
                    val response = if (toolResult.success) "Готово: ${toolResult.message}" else "Ошибка: ${toolResult.message}"
                    if (response.isNotEmpty()) {
                        transitionTo(PipelineState.TTS_SPEAKING, "🗣️ Отвечаю...")
                        tts.speak(response)
                    }
                    lastAgentResponse = response
                    onContinuousResult?.invoke(text, response)
                    timer.step("Tool TTS done")
                    Log.i(TAG, "└──────────────────────────────────────────────────")
                    return
                }
            }
            if (!intentMatch) Log.i(TAG, "│ [3/6] INTENT — no match → LLM")

            // ── STEP 4: LLM ──
            transitionTo(PipelineState.AGENT_RUNNING, "💭 Думаю...")
            val llmRequestStart = System.currentTimeMillis()
            val tokenBuffer = StringBuilder()
            var firstTokenLogged = false

            val onTokenCallback: (String) -> Unit = { token ->
                if (!firstTokenLogged) {
                    firstTokenLogged = true
                    val firstTokenMs = System.currentTimeMillis() - llmRequestStart
                    Log.i(TAG, "│ [4/6] LLM  ▶ first_token +${firstTokenMs}ms")
                }
                tokenBuffer.append(token)
            }

            Log.i(TAG, "│ [4/6] LLM  ▶ processing...")
            // Филлер «Секунду...» убран из 0.1: он обнулял onTtsFinished и мог заглушить весь цикл.
            val response = orchestrator.process(text, onToken = onTokenCallback)

            val llmTotalMs = System.currentTimeMillis() - llmRequestStart
            timer.step("Agent done: '${response.take(40)}...' (streaming: ${llmTotalMs}ms)")
            Log.i(TAG, "│ [4/6] LLM  ◀ '${response.take(50)}' (+${llmTotalMs}ms, ${tokenBuffer.length}ch)")

            lastAgentResponse = response

            if (continuousDialogue.isCancelled) { onCancelled?.invoke(); return }

            // ── STEP 5: TTS ──
            if (response.isNotEmpty()) {
                Log.i(TAG, "│ [5/6] TTS  ▶ '${response.take(40)}'")
                transitionTo(PipelineState.TTS_SPEAKING, "🗣️ Отвечаю...")
                onTtsStarted?.invoke()
                tts.speak(response)
                timer.step("TTS done")
                Log.i(TAG, "│ [5/6] TTS  ◀ playback complete")
            }

            // ── STEP 6: Farewell check ──
            if (isFarewellResponse(response)) {
                Log.i(TAG, "│ [6/6] FAREWELL ✓ detected — ending session")
                continuousDialogue.stopSession(cancelled = false)
                onAgentFarewell?.invoke()
            } else {
                Log.i(TAG, "│ [6/6] FAREWELL — no → listening continues")
            }

            val totalMs = timer.total()
            Log.i(TAG, "│ TOTAL: ${totalMs}ms  (STT=${sttMs}ms, LLM=${llmTotalMs}ms)")
            Log.i(TAG, "└──────────────────────────────────────────────────")
            log("Timer", "info", "Pipeline: ${totalMs}ms", "STT→Agent→TTS")
            sendStatus("✓ ${totalMs}ms")

            onContinuousResult?.invoke(text, response)
        } catch (e: CancellationException) {
            Log.d(TAG, "Continuous speech processing cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Continuous speech processing error: ${e.message}", e)
            transitionTo(PipelineState.ERROR)
        } finally {
            restoreMusicVolume()
            // Если ответ не озвучивался (STT пуст, эхо, ошибка) — не держать 30с до watchdog
            continuousDialogue.onProcessingDone()
            if (config.wakeWordEnabled && !continuousDialogue.isSessionActive) {
                onResumeWakeWord()
            }
        }
    }

    /**
     * Process a voice command (simplified — for external callers).
     */
    suspend fun executeCommand(audioFile: File): String {
        val text = stt.transcribe(
            audioFile = audioFile,
            apiKey = config.openaiApiKey,
            language = config.language,
            provider = "openai"
        )
        if (text.isEmpty()) return "Не расслышал. Повтори."

        Log.d(TAG, "STT: $text")
        val response = orchestrator.process(text)
        Log.d(TAG, "Agent: $response")

        if (response.isNotEmpty()) tts.speak(response)

        if (isFarewellResponse(response)) {
            onAgentFarewell?.invoke()
        }
        return response
    }

    /**
     * Process text directly (skip STT).
     */
    suspend fun executeText(text: String): String {
        Log.d(TAG, "Processing text: $text")
        val response = orchestrator.process(text)
        Log.d(TAG, "Agent response: $response")
        if (response.isNotEmpty()) tts.speak(response)
        return response
    }

    /**
     * Process text for tests (no TTS).
     */
    suspend fun executeTextForTest(text: String): String {
        Log.d(TAG, "[TEST] Processing text: $text")
        val response = orchestrator.process(text)
        Log.d(TAG, "[TEST] Agent response: $response")
        return response
    }

    // ==================== Pipeline Control ====================

    fun cancelPipeline() {
        Log.d(TAG, "cancelPipeline called")
        continuousDialogue.stopSession(cancelled = true)
        AudioSessionManager.stop() // Прервать любую активную запись
        tts.stop()
        transitionTo(PipelineState.CANCELLED)
        onCancelled?.invoke()
        log("Pipeline", "warning", "Pipeline отменён пользователем")
    }

    fun getCurrentState(continuousActive: Boolean): PipelineState {
        return if (continuousActive) PipelineState.LISTENING else PipelineState.IDLE
    }

    // ==================== Audio Recording ====================

    private fun recordAudioWithVAD(): File? {
        return try {
            val sampleRate = 16000
            val audioFile = File(context.cacheDir, "voice_command.wav")
            val maxDurationSec = 15
            val maxSamples = sampleRate * maxDurationSec
            val allSamples = ShortArray(maxSamples)
            var totalSamplesWritten = 0

            var silenceCount = 0
            var speechDetected = false
            val maxSilenceFrames = 45
            val isRecordingActive = AtomicBoolean(true)

            // Adaptive VAD: exponential moving average of noise floor
            var noiseFloor = 0.0
            var noiseSamples = 0
            val noiseFloorAlpha = 0.05 // EMA coefficient (lower = smoother)
            val speechMultiplier = 2.5 // threshold = noiseFloor * multiplier
            val minThreshold = 80.0
            val maxThreshold = 8000.0

            val started = AudioSessionManager.start(AudioSessionManager.Owner.SINGLE_LISTEN) { buffer, read ->
                if (!isRecordingActive.get()) return@start

                val spaceLeft = maxSamples - totalSamplesWritten
                val toCopy = minOf(read, spaceLeft)
                System.arraycopy(buffer, 0, allSamples, totalSamplesWritten, toCopy)
                totalSamplesWritten += toCopy

                // Calculate frame energy
                var sum = 0.0
                for (i in 0 until read) {
                    sum += kotlin.math.abs(buffer[i].toInt())
                }
                val frameEnergy = sum / read

                // Adaptive noise floor estimation
                if (noiseSamples < 10) {
                    // First ~0.5s: measure ambient noise
                    noiseFloor = if (noiseSamples == 0) frameEnergy else (noiseFloor + frameEnergy) / 2
                    noiseSamples++
                } else {
                    // EMA update — only update if frame is likely noise (below threshold)
                    val threshold = noiseFloor.coerceIn(minThreshold, maxThreshold) * speechMultiplier
                    if (frameEnergy < threshold) {
                        noiseFloor = noiseFloor * (1 - noiseFloorAlpha) + frameEnergy * noiseFloorAlpha
                    }
                }

                val effectiveThreshold = noiseFloor.coerceIn(minThreshold, maxThreshold) * speechMultiplier

                if (frameEnergy > effectiveThreshold) {
                    speechDetected = true
                    silenceCount = 0
                } else if (speechDetected) {
                    silenceCount++
                    if (silenceCount > maxSilenceFrames) isRecordingActive.set(false)
                }
            }

            if (!started) {
                Log.e(TAG, "[REC] AudioSessionManager.start failed")
                return null
            }

            Log.d(TAG, "[REC] Recording via AudioSessionManager, maxSamples=$maxSamples")

            while (isRecordingActive.get() && totalSamplesWritten < maxSamples) {
                Thread.sleep(50)
            }
            AudioSessionManager.stop()

            Log.d(TAG, "[REC] Done: samples=$totalSamplesWritten, speech=$speechDetected, noiseFloor=${noiseFloor.toInt()}")

            if (!speechDetected) {
                Log.d(TAG, "[REC] Речь не обнаружена")
                log("VAD", "info", "Речь не обнаружена (VAD)", "шум: ${noiseFloor.toInt()}")
                return null
            }

            if (totalSamplesWritten == 0) { Log.e(TAG, "[REC] No samples recorded"); return null }

            writeWav(audioFile, allSamples.copyOf(totalSamplesWritten), sampleRate)
            Log.d(TAG, "[REC] WAV written: ${audioFile.length()} bytes")
            log("VAD", "success", "Речь записана", "${audioFile.length()} байт")
            audioFile
        } catch (e: Exception) {
            Log.e(TAG, "[REC] Error: ${e.message}", e)
            log("VAD", "error", "Ошибка записи", e.message)
            AudioSessionManager.stop()
            null
        }
    }

    // ==================== Music Ducking ====================

    fun duckMusicForRecording() {
        wasMusicPlaying = false
        try {
            musicPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    wasMusicPlaying = true
                    Log.d(TAG, "Музыка на паузе (локальный плеер)")
                }
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val pauseDown = android.view.KeyEvent(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(),
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0
            )
            val pauseUp = android.view.KeyEvent(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis() + 50,
                android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE, 0
            )
            am.dispatchMediaKeyEvent(pauseDown)
            am.dispatchMediaKeyEvent(pauseUp)
        } catch (e: Exception) {
            Log.w(TAG, "duckMusic failed: ${e.message}")
        }
    }

    fun restoreMusicVolume() {
        if (!wasMusicPlaying) return
        try {
            musicPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    Log.d(TAG, "Музыка возобновлена (локальный плеер)")
                }
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val playDown = android.view.KeyEvent(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(),
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0
            )
            val playUp = android.view.KeyEvent(
                android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis() + 50,
                android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0
            )
            am.dispatchMediaKeyEvent(playDown)
            am.dispatchMediaKeyEvent(playUp)
        } catch (e: Exception) {
            Log.w(TAG, "restoreMusic failed: ${e.message}")
        }
    }

    // ==================== Echo & Farewell Detection ====================

    private fun isFarewellResponse(response: String): Boolean {
        val lower = response.lowercase().trim()
        // Farewell must be explicit — not "пока не особо" (adverb = "not yet")
        // Check for standalone farewell phrases, not substrings
        val farewellPatterns = listOf(
            Regex("""(^|[\s,.!])пока([\s,.!?!]|$)"""),    // "пока" as standalone word
            Regex("""до свидания"""),
            Regex("""до связи"""),
            Regex("""до встречи"""),
            Regex("""всего доброго"""),
            Regex("""прощай""")
        )
        return farewellPatterns.any { it.containsMatchIn(lower) }
    }
    
    /** Check if user's speech is a farewell phrase — end session gracefully */
    private fun isFarewellText(text: String): Boolean {
        val lower = text.lowercase().trim().replace(Regex("[^а-яёa-z\\s]"), "")
        if (lower.length < 3) return false
        val farewellPatterns = listOf(
            Regex("""(^|\s)пока(\s|$)"""),
            Regex("""до свидания"""),
            Regex("""до связи"""),
            Regex("""до встречи"""),
            Regex("""прощай"""),
            Regex("""всего доброго"""),
            Regex("""покеда"""),
            Regex("""удачи""")
        )
        return farewellPatterns.any { it.containsMatchIn(lower) }
    }
    
    /**
     * Detect Whisper hallucinations — known patterns that Whisper generates from silence/echo.
     * These are NOT real user speech and must be filtered before sending to LLM.
     */
    private fun isWhisperHallucination(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isEmpty()) return true
    
        // Known hallucination patterns (from logs and community reports)
        val hallucinationPatterns = listOf(
            "субтитры сделал",
            "субтитры от",
            "dimatorzok",
            "torzok",
            "спасибо за просмотр",
            "подписывайтесь на канал",
            "ставьте лайк",
            "всем привет" // when detected right after TTS (not as first user message)
        )
        if (hallucinationPatterns.any { lower.contains(it) }) return true
    
        // Too short + no vowels = likely hallucination from noise
        val vowels = "аеёиоуыэюя".toSet()
        val cleanLower = lower.replace(Regex("[^а-яёa-z]"), "")
        if (cleanLower.length < 4 && cleanLower.count { it in vowels } < 2) return true
    
        // Single word that's not a real greeting/response (Whisper often outputs random single words)
        val words = cleanLower.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size == 1 && words[0].length < 4) return true
    
        return false
    }

    private fun isEcho(recognized: String, agentResponse: String): Boolean {
        if (agentResponse.isEmpty()) return false
        val cleanRecognized = recognized.lowercase().trim().replace(Regex("[^а-яёa-z0-9\\s]"), "")
        val cleanResponse = agentResponse.lowercase().trim().replace(Regex("[^а-яёa-z0-9\\s]"), "")
        if (cleanRecognized.isEmpty() || cleanResponse.isEmpty()) return false

        // Too short to be echo — user said something real, don't filter
        if (cleanRecognized.length < 10) return false

        // Substring check: if recognized is a significant part of the response, it's echo
        // (user repeating back what agent just said, possibly heard from speaker)
        if (cleanRecognized.length >= 15 && cleanResponse.contains(cleanRecognized)) {
            return true
        }

        // Levenshtein similarity: texts must be ~85% identical (not just substring)
        val distance = levenshtein(cleanRecognized, cleanResponse)
        val maxLen = maxOf(cleanRecognized.length, cleanResponse.length)
        return 1.0 - (distance.toDouble() / maxLen) > 0.85
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i-1] == b[j-1]) 0 else 1
                dp[i][j] = minOf(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    // ==================== WAV Utilities ====================

    private fun writeWav(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val os = file.outputStream()
        os.write("RIFF".toByteArray()); os.write(intToBytes(36 + dataSize))
        os.write("WAVE".toByteArray()); os.write("fmt ".toByteArray())
        os.write(intToBytes(16)); os.write(shortToBytes(1)); os.write(shortToBytes(1))
        os.write(intToBytes(sampleRate)); os.write(intToBytes(sampleRate * 2))
        os.write(shortToBytes(2)); os.write(shortToBytes(16))
        os.write("data".toByteArray()); os.write(intToBytes(dataSize))
        for (s in samples) os.write(shortToBytes(s))
        os.close()
    }

    private fun intToBytes(v: Int) = byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(), (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte())
    private fun shortToBytes(v: Short) = byteArrayOf((v.toInt() and 0xFF).toByte(), (v.toInt() shr 8 and 0xFF).toByte())
}
