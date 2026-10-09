package com.aiagent.ai_voice_agent.engine

import android.util.Log
import com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager
import com.aiagent.ai_voice_agent.engine.pipeline.PipelineState
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Continuous Dialogue Manager — ядро системы непрерывного слушания.
 * 
 * Архитектура:
 * - Микрофон всегда активен во время диалоговой сессии
 * - VAD детектирует начало/конец речи
 * - Barge-in: пользователь может прервать TTS начав говорить
 * - Контекст сохраняется между репликами
 * 
 * State machine:
 * IDLE → LISTENING → SPEECH_DETECTED → RECORDING → SPEECH_ENDED → PROCESSING → (TTS) → LISTENING
 *                                    ↑                                          |
 *                                    └──────── BARGE_IN (user interrupts TTS) ──┘
 *                                    └── CANCELLING (user stops via UI)
 *                                    └── WAITING_FOR_CONFIRMATION (PermissionManager)
 */
class ContinuousDialogueManager {
    companion object {
        private const val TAG = "ContinuousDialogue"
        private const val SAMPLE_RATE = 16000
        
        // Processing timeout: макс время на STT + Agent + TTS
        private const val PROCESSING_TIMEOUT_MS = 30_000L
    }

    data class VadConfig(
        val silenceThreshold: Int = 100,        // Было 150 — тихая речь не определялась
        val speechStartFrames: Int = 5,
        val silenceEndFrames: Int = 40,
        val maxRecordingFrames: Int = 150,
        val bargeInThreshold: Int = 1500,
        val bargeInConfirmFrames: Int = 6,
        val bargeInGuardMs: Long = 800L,
        val cooldownAfterTtsMs: Long = 500L     // Было 1000 — пользователь говорит сразу, но ждёт 1 сек
    )
    
    // Callbacks for Flutter/UI
    var onStateChanged: ((PipelineState) -> Unit)? = null
    var onSpeechDetected: (() -> Unit)? = null
    var onSpeechResult: ((File, Boolean) -> Unit)? = null
    var onBargeIn: (() -> Unit)? = null
    var onTranscription: ((String) -> Unit)? = null
    var onAgentResponse: ((String) -> Unit)? = null
    var onProcessingTimeout: (() -> Unit)? = null
    var onVadLevelChanged: ((Float) -> Unit)? = null
    var onPipelineCancelled: (() -> Unit)? = null
    var onPermissionRequired: ((String, Map<String, Any?>, (Boolean) -> Unit) -> Unit)? = null
    
    @Volatile
    private var currentState = PipelineState.IDLE

    @Volatile
    var isSessionActive = false
        private set

    @Volatile
    var isCancelled = false
        private set
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Audio processing
    private var processingJob: Job? = null
    
    // VAD state
    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var isSpeaking = false
    
    // Adaptive VAD: noise floor estimation
    private var noiseFloor = 0.0
    private var noiseSamples = 0
    private val noiseFloorAlpha = 0.05 // EMA coefficient
    private val speechMultiplier = 2.5
    private val minThreshold = 80.0
    private val maxThreshold = 8000.0
    private var lastEnergy = 0.0 // for UI wave
    
    // Barge-in detection during TTS
    private var bargeInFrameCount = 0
    private var ttsStartTimeMs = 0L  // Когда начался TTS (для guard period)
    
    // Cooldown после TTS — чтобы эхо из динамика не триггерило повтор
    private var lastTtsFinishTimeMs = 0L

    // Processing timeout: время старта обработки (STT → Agent → TTS)
    private var processingStartTimeMs = 0L
    
    // Current recording buffer
    private val audioBuffer = ConcurrentLinkedQueue<Short>()

    private var vadConfig = VadConfig()

    /**
     * Override VAD thresholds from config.
     * Safe to call at any time, takes effect on next frame.
     */
    fun setVadThresholds(config: VadConfig) {
        vadConfig = config
        Log.d(TAG, "VAD thresholds updated: $config")
    }

    /**
     * External barge-in trigger (e.g. from UI stop button).
     */
    fun onBargeIn() {
        if (currentState == PipelineState.TTS_SPEAKING) {
            onBargeInDetected()
        }
    }

    /**
     * Returns current audio energy level (0.0..1.0) for UI voice wave.
     */
    fun getAudioLevel(): Float {
        return if (lastEnergy > 0) (lastEnergy / 32768.0).toFloat().coerceIn(0f, 1f) else 0f
    }
    
    /**
     * Start continuous dialogue session.
     * Mic stays active until stopSession() is called.
     */
    fun startSession(config: VadConfig? = null) {
        if (isSessionActive) {
            Log.w(TAG, "Session already active")
            return
        }

        if (config != null) vadConfig = config
        
        Log.d(TAG, "Starting continuous dialogue session with config: $vadConfig")
        isCancelled = false
        isSessionActive = true
        setState(PipelineState.LISTENING)
        
        startAudioRecording()
    }
    
    /**
     * Stop continuous dialogue session.
     */
    fun stopSession(cancelled: Boolean = false) {
        if (!isSessionActive) return

        isCancelled = cancelled

        if (cancelled && currentState != PipelineState.IDLE) {
            setState(PipelineState.CANCELLING)
        }
        
        Log.d(TAG, "Stopping continuous dialogue session (cancelled=$cancelled)")
        isSessionActive = false
        
        processingJob?.cancel()
        
        stopAudioRecording()
        setState(PipelineState.IDLE)
    }
    
    /**
     * Called when TTS starts playing.
     * Enables barge-in detection after guard period.
     */
    fun onTtsStarted() {
        if (!isSessionActive) return
        setState(PipelineState.TTS_SPEAKING)
        bargeInFrameCount = 0
        ttsStartTimeMs = System.currentTimeMillis()
        Log.d(TAG, "TTS started, guard period ${vadConfig.bargeInGuardMs}ms")
    }
    
    /**
     * Called when TTS finishes playing.
     */
    fun onTtsFinished() {
        if (!isSessionActive) return
        lastTtsFinishTimeMs = System.currentTimeMillis()
        processingStartTimeMs = 0
        Log.d(TAG, "TTS finished, cooldown ${vadConfig.cooldownAfterTtsMs}ms before listening")
        setState(PipelineState.LISTENING)
    }

    /**
     * Обработка завершилась без TTS (STT пусто, эхо, галлюцинация, ошибка) —
     * сразу возвращаемся в слушание, не ждём 30-секундный вотчдог.
     */
    fun onProcessingDone() {
        if (!isSessionActive) return
        if (currentState == PipelineState.PROCESSING) {
            processingStartTimeMs = 0
            Log.d(TAG, "Processing done without TTS — back to LISTENING")
            setState(PipelineState.LISTENING)
        }
    }
    
    /**
     * Process audio frame for VAD and barge-in detection.
     * Called from audio recording thread.
     */
    private fun processAudioFrame(buffer: ShortArray, read: Int) {
        if (!isSessionActive || isCancelled) return
        
        // Calculate energy
        val energy = buffer.take(read).map { kotlin.math.abs(it.toInt()) }.average()
        lastEnergy = energy
        
        // Adaptive noise floor estimation
        if (noiseSamples < 10) {
            // First ~0.5s: measure ambient noise
            noiseFloor = if (noiseSamples == 0) energy else (noiseFloor + energy) / 2
            noiseSamples++
        } else {
            // EMA update — only update if frame is likely noise (below threshold)
            val currentThreshold = noiseFloor.coerceIn(minThreshold, maxThreshold) * speechMultiplier
            if (energy < currentThreshold) {
                noiseFloor = noiseFloor * (1 - noiseFloorAlpha) + energy * noiseFloorAlpha
            }
        }
        
        val effectiveThreshold = noiseFloor.coerceIn(minThreshold, maxThreshold) * speechMultiplier
        
        // Emit audio level for UI wave
        onVadLevelChanged?.invoke((energy / 32768.0).toFloat().coerceIn(0f, 1f))
        
        when (currentState) {
            PipelineState.LISTENING -> {
                val timeSinceTtsFinish = System.currentTimeMillis() - lastTtsFinishTimeMs
                if (timeSinceTtsFinish < vadConfig.cooldownAfterTtsMs) {
                    speechFrameCount = 0
                    return
                }
                
                if (energy > effectiveThreshold) {
                    speechFrameCount++
                    if (speechFrameCount >= vadConfig.speechStartFrames) {
                        onSpeechStart()
                    }
                } else {
                    speechFrameCount = 0
                }
            }
            
            PipelineState.SPEECH_RECORDING -> {
                audioBuffer.addAll(buffer.take(read))
                
                if (energy < vadConfig.silenceThreshold) {
                    silenceFrameCount++
                    if (silenceFrameCount >= vadConfig.silenceEndFrames) {
                        onSpeechEnd()
                    }
                } else {
                    silenceFrameCount = 0
                }
                
                if (audioBuffer.size >= vadConfig.maxRecordingFrames * (SAMPLE_RATE / 100)) {
                    onSpeechEnd()
                }
            }
            
            PipelineState.TTS_SPEAKING -> {
                val timeSinceTtsStart = System.currentTimeMillis() - ttsStartTimeMs
                if (timeSinceTtsStart < vadConfig.bargeInGuardMs) {
                    bargeInFrameCount = 0
                    return
                }
                
                if (energy > vadConfig.bargeInThreshold) {
                    bargeInFrameCount++
                    if (bargeInFrameCount >= vadConfig.bargeInConfirmFrames) {
                        onBargeInDetected()
                    }
                } else {
                    bargeInFrameCount = 0
                }
            }
            
            PipelineState.PROCESSING -> {
                if (processingStartTimeMs > 0) {
                    val elapsed = System.currentTimeMillis() - processingStartTimeMs
                    if (elapsed >= PROCESSING_TIMEOUT_MS) {
                        Log.w(TAG, "Processing timeout after ${elapsed}ms")
                        processingStartTimeMs = 0
                        onProcessingTimeout?.invoke()
                        setState(PipelineState.LISTENING)
                    }
                }
            }

            PipelineState.WAITING_FOR_CONFIRMATION -> {
                // Mic still active but pipeline paused for user confirmation
            }

            else -> { /* IDLE, BARGE_IN, CANCELLING - ignore */ }
        }
    }
    
    private fun onSpeechStart() {
        Log.d(TAG, "Speech started")
        setState(PipelineState.SPEECH_RECORDING)
        audioBuffer.clear()
        silenceFrameCount = 0
        onSpeechDetected?.invoke()
    }
    
    private fun onSpeechEnd() {
        Log.d(TAG, "Speech ended, buffer size: ${audioBuffer.size}")
        EngineManager.logToFlutter("VAD", "info", "Речь завершена", "буфер: ${audioBuffer.size} сэмплов")
        
        if (audioBuffer.isEmpty() || isCancelled) {
            if (isCancelled) onPipelineCancelled?.invoke()
            setState(PipelineState.LISTENING)
            return
        }
        
        val audioFile = saveAudioToFile()
        if (audioFile != null) {
            setState(PipelineState.PROCESSING)
            resetProcessingTimer()
            onSpeechResult?.invoke(audioFile, false)
            
            processingJob = scope.launch {
                processSpeech(audioFile)
            }
        } else {
            setState(PipelineState.LISTENING)
        }
    }
    
    private fun onBargeInDetected() {
        Log.d(TAG, "Barge-in detected! User interrupting TTS")
        EngineManager.logToFlutter("BargeIn", "warning", "Пользователь прервал TTS (barge-in)")
        setState(PipelineState.BARGE_IN)
        bargeInFrameCount = 0
        processingStartTimeMs = 0
        
        onBargeIn?.invoke()
        
        if (isCancelled) {
            onPipelineCancelled?.invoke()
            return
        }
        
        audioBuffer.clear()
        setState(PipelineState.SPEECH_RECORDING)
        onSpeechDetected?.invoke()
    }
    
    private suspend fun processSpeech(audioFile: File) {
        try {
            // This will be called back from EngineManager with results
            Log.d(TAG, "Processing speech: ${audioFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Process speech error: ${e.message}", e)
            setState(PipelineState.LISTENING)
        }
    }
    
    fun resetSession() {
        speechFrameCount = 0
        silenceFrameCount = 0
        bargeInFrameCount = 0
        noiseFloor = 0.0
        noiseSamples = 0
        lastEnergy = 0.0
        isSpeaking = false
        isCancelled = false
        processingStartTimeMs = 0
        audioBuffer.clear()
    }

    private fun resetProcessingTimer() {
        processingStartTimeMs = System.currentTimeMillis()
    }

    private fun saveAudioToFile(): File? {
        return try {
            val file = File(EngineManager.context.cacheDir, "continuous_speech.wav")
            val samples = audioBuffer.toArray().map { it as Short }.toShortArray()
            
            FileOutputStream(file).use { fos ->
                // WAV header
                val dataSize = samples.size * 2
                fos.write("RIFF".toByteArray())
                fos.write(intToBytes(36 + dataSize))
                fos.write("WAVE".toByteArray())
                fos.write("fmt ".toByteArray())
                fos.write(intToBytes(16))
                fos.write(shortToBytes(1))  // PCM
                fos.write(shortToBytes(1))  // Mono
                fos.write(intToBytes(SAMPLE_RATE))
                fos.write(intToBytes(SAMPLE_RATE * 2))  // Byte rate
                fos.write(shortToBytes(2))  // Block align
                fos.write(shortToBytes(16)) // Bits per sample
                fos.write("data".toByteArray())
                fos.write(intToBytes(dataSize))
                
                // Audio data
                for (s in samples) {
                    fos.write(shortToBytes(s))
                }
            }
            
            Log.d(TAG, "Saved audio: ${file.absolutePath} (${file.length()} bytes)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Save audio error: ${e.message}", e)
            null
        }
    }
    
    private fun startAudioRecording() {
        val started = AudioSessionManager.start(AudioSessionManager.Owner.CONTINUOUS) { buffer, read ->
            processAudioFrame(buffer, read)
        }
        if (!started) {
            Log.e(TAG, "AudioSessionManager.start failed for CONTINUOUS")
            isSessionActive = false
            setState(PipelineState.IDLE)
        } else {
            Log.d(TAG, "Audio recording started via AudioSessionManager")
        }
    }
    
    private fun stopAudioRecording() {
        AudioSessionManager.stop()
        Log.d(TAG, "Audio recording stopped via AudioSessionManager")
    }
    
    private fun setState(newState: PipelineState) {
        if (currentState != newState) {
            Log.d(TAG, "State: $currentState → $newState")
            currentState = newState
            onStateChanged?.invoke(newState)
        }
    }
    
    private fun intToBytes(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        (v shr 8 and 0xFF).toByte(),
        (v shr 16 and 0xFF).toByte(),
        (v shr 24 and 0xFF).toByte()
    )
    
    private fun shortToBytes(v: Short) = byteArrayOf(
        (v.toInt() and 0xFF).toByte(),
        (v.toInt() shr 8 and 0xFF).toByte()
    )
}
