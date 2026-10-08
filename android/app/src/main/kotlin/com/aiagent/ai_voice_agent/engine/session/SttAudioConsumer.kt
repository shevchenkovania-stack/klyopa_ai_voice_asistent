package com.aiagent.ai_voice_agent.engine.session

import android.util.Log
import com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SttAudioConsumer — адаптер между AudioSessionManager и STT pipeline.
 *
 * Задачи:
 * 1. Маршрутизация PCM в STT engine
 * 2. VAD для определения начала/конца речи
 * 3. Barge-in detection (перебивание TTS)
 * 4. Silence timeout (возврат к wake word)
 * 5. Буферизация PCM → WAV файл для pipeline
 *
 * VAD: простая энергия (RMS) с адаптивным noise floor.
 */
class SttAudioConsumer(
    private val sessionManager: AssistantSessionManager,
    private val cacheDir: File? = null
) : AudioSessionManager.AudioConsumer {

    companion object {
        private const val TAG = "SttConsumer"

        // VAD thresholds
        private const val SPEECH_THRESHOLD = 500.0    // RMS amplitude
        private const val SILENCE_TIMEOUT_MS = 15_000L // 15с тишины → возврат к wake word
        private const val SPEECH_END_SILENCE_MS = 400L // 400мс тишины = конец фразы
        private const val BARGE_IN_MIN_SPEECH_MS = 300L // Минимум 300мс речи для barge-in
        private const val NOISE_FLOOR_ALPHA = 0.05     // EMA coefficient
        private const val SPEECH_MULTIPLIER = 2.5
        private const val MIN_THRESHOLD = 80.0
        private const val MAX_THRESHOLD = 8000.0
        private const val SAMPLE_RATE = 16000

        // Echo suppression: когда TTS играет, микрофон слышит динамик.
        // Поднимаем порог в 30x — на Xiaomi Mi A2 Lite TTS эхо достигает RMS=2700+,
        // при noise floor ~80 обычный множитель 4x даёт порог 800 — недостаточно.
        private const val TTS_ECHO_MULTIPLIER = 30.0
        // Cooldown после TTS: динамик ещё звенит 1-2с после остановки.
        private const val TTS_COOLDOWN_MS = 3000L
        // Grace period после cooldown: порог постепенно снижается.
        private const val TTS_GRACE_PERIOD_MS = 2000L
        private const val TTS_GRACE_MULTIPLIER = 10.0
    }

    override val id = "stt"

    // VAD state
    private var noiseFloor = 0.0
    private var noiseSamples = 0
    private var isSpeaking = false
    private var lastSpeechTimeMs = 0L
    private var speechStartTimeMs = 0L

    // Echo suppression: raised by EngineManager when TTS is playing
    @Volatile
    var ttsPlaying = false
        set(value) {
            field = value
            if (!value) {
                // TTS stopped — set cooldown to ignore lingering echo
                ttsCooldownUntilMs = System.currentTimeMillis() + TTS_COOLDOWN_MS
                // Grace period starts after cooldown
                ttsGraceUntilMs = ttsCooldownUntilMs + TTS_GRACE_PERIOD_MS
            }
        }
    // Cooldown after TTS: echo from speaker lingers
    @Volatile
    private var ttsCooldownUntilMs = 0L
    // Grace period after cooldown: threshold gradually elevated
    @Volatile
    private var ttsGraceUntilMs = 0L

    // Audio buffer — accumulates PCM during speech for STT pipeline
    private var speechBuffer: ByteArrayOutputStream? = null
    private var speechSampleCount = 0

    // Callback: fired when speech ends (VAD silence). Provides WAV file.
    var onSpeechComplete: ((File) -> Unit)? = null

    override fun onActivated() {
        // Сбрасываем VAD state
        noiseFloor = 0.0
        noiseSamples = 0
        isSpeaking = false
        lastSpeechTimeMs = System.currentTimeMillis()
        speechStartTimeMs = 0L
        speechBuffer = null
        speechSampleCount = 0
        Log.d(TAG, "Activated — STT listening")
    }

    override fun onDeactivated() {
        isSpeaking = false
        speechBuffer = null
        speechSampleCount = 0
        Log.d(TAG, "Deactivated")
    }

    override fun onAudioData(buffer: ShortArray, read: Int) {
        val now = System.currentTimeMillis()
        val rms = calculateRms(buffer, read)

        // Adaptive noise floor estimation
        if (noiseSamples < 10) {
            noiseFloor = if (noiseSamples == 0) rms else (noiseFloor + rms) / 2
            noiseSamples++
        } else {
            val currentThreshold = noiseFloor.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD) * SPEECH_MULTIPLIER
            if (rms < currentThreshold) {
                noiseFloor = noiseFloor * (1 - NOISE_FLOOR_ALPHA) + rms * NOISE_FLOOR_ALPHA
            }
        }

        // Echo suppression: пока TTS играет или сразу после — игнрируем звук из динамика.
        // Порог поднят в 30x, noise floor не адаптируется.
        val inCooldown = now < ttsCooldownUntilMs
        val inGracePeriod = !inCooldown && now < ttsGraceUntilMs
        if (ttsPlaying || inCooldown) {
            val echoThreshold = noiseFloor.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD) * SPEECH_MULTIPLIER * TTS_ECHO_MULTIPLIER
            if (rms > echoThreshold) {
                // Громкая речь пользователя поверх TTS — barge-in
                if (!isSpeaking) {
                    isSpeaking = true
                    speechStartTimeMs = now
                    speechBuffer = ByteArrayOutputStream()
                    speechSampleCount = 0
                    Log.d(TAG, "Barge-in speech detected (RMS=${rms.toInt()}, echoThreshold=${echoThreshold.toInt()})")
                    if (sessionManager.currentState == AssistantSessionManager.State.SPEAKING ||
                        sessionManager.currentState == AssistantSessionManager.State.CONVERSING) {
                        Log.i(TAG, "Barge-in detected during TTS!")
                        sessionManager.handleEvent(AssistantSessionManager.Event.USER_BARGE_IN)
                    }
                }
                lastSpeechTimeMs = now
                speechBuffer?.write(shortsToBytes(buffer, read))
                speechSampleCount += read
            }
            return  // Skip normal VAD processing during TTS
        }
        if (inGracePeriod) {
            // Grace period: threshold elevated but not as high as full echo suppression
            val graceThreshold = noiseFloor.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD) * SPEECH_MULTIPLIER * TTS_GRACE_MULTIPLIER
            if (rms > graceThreshold) {
                if (!isSpeaking) {
                    isSpeaking = true
                    speechStartTimeMs = now
                    speechBuffer = ByteArrayOutputStream()
                    speechSampleCount = 0
                    Log.d(TAG, "Speech in grace period (RMS=${rms.toInt()}, graceThreshold=${graceThreshold.toInt()})")
                }
                lastSpeechTimeMs = now
                speechBuffer?.write(shortsToBytes(buffer, read))
                speechSampleCount += read
            }
            return  // Skip normal VAD during grace period
        }

        val effectiveThreshold = noiseFloor.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD) * SPEECH_MULTIPLIER

        if (rms > effectiveThreshold) {
            // ===== Обнаружили речь =====
            if (!isSpeaking) {
                isSpeaking = true
                speechStartTimeMs = now
                speechBuffer = ByteArrayOutputStream()
                speechSampleCount = 0
                Log.d(TAG, "Speech started (RMS=${rms.toInt()}, threshold=${effectiveThreshold.toInt()})")

                // Barge-in: если в SPEAKING и речь > 300мс
                if (sessionManager.currentState == AssistantSessionManager.State.SPEAKING) {
                    val speechDuration = now - speechStartTimeMs
                    if (speechDuration > BARGE_IN_MIN_SPEECH_MS) {
                        Log.i(TAG, "Barge-in detected!")
                        sessionManager.handleEvent(AssistantSessionManager.Event.USER_BARGE_IN)
                    }
                }

                // Speech start в CONVERSING
                if (sessionManager.currentState == AssistantSessionManager.State.CONVERSING) {
                    sessionManager.handleEvent(AssistantSessionManager.Event.SPEECH_START)
                }
            }
            lastSpeechTimeMs = now

            // Buffer PCM for STT
            speechBuffer?.write(shortsToBytes(buffer, read))
            speechSampleCount += read

        } else {
            // ===== Тишина =====
            if (isSpeaking) {
                // Still buffer silence (until speech end)
                speechBuffer?.write(shortsToBytes(buffer, read))
                speechSampleCount += read

                val silenceMs = now - lastSpeechTimeMs
                if (silenceMs > SPEECH_END_SILENCE_MS) {
                    isSpeaking = false
                    Log.d(TAG, "Speech ended (silence=${silenceMs}ms, samples=$speechSampleCount)")

                    // Write WAV and notify pipeline
                    val wavFile = writeWav()
                    if (wavFile != null) {
                        Log.i(TAG, "WAV ready: ${wavFile.length()} bytes")
                        onSpeechComplete?.invoke(wavFile)
                    }

                    sessionManager.handleEvent(AssistantSessionManager.Event.SPEECH_END)
                }
            }

            // Silence timeout в CONVERSING
            if (sessionManager.currentState == AssistantSessionManager.State.CONVERSING) {
                val totalSilence = now - lastSpeechTimeMs
                if (totalSilence > SILENCE_TIMEOUT_MS) {
                    Log.i(TAG, "Silence timeout → returning to wake word")
                    sessionManager.handleEvent(AssistantSessionManager.Event.SILENCE_TIMEOUT)
                }
            }
        }
    }

    /** Convert shorts to byte array (little-endian) and write to buffer */
    private fun shortsToBytes(buffer: ShortArray, read: Int): ByteArray {
        val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until read) {
            byteBuffer.putShort(buffer[i])
        }
        return byteBuffer.array()
    }

    /** Write buffered PCM to WAV file */
    private fun writeWav(): File? {
        val buf = speechBuffer ?: return null
        val pcmData = buf.toByteArray()
        if (pcmData.isEmpty()) return null

        try {
            val wavFile = File(cacheDir ?: return null, "stt_speech.wav")
            FileOutputStream(wavFile).use { fos ->
                val header = wavHeader(pcmData.size, SAMPLE_RATE, 1, 16)
                fos.write(header)
                fos.write(pcmData)
            }
            return wavFile
        } catch (e: Exception) {
            Log.e(TAG, "WAV write error: ${e.message}")
            return null
        }
    }

    /** Generate WAV header (44 bytes) */
    private fun wavHeader(dataSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalSize = dataSize + 36

        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // chunk size
            putShort(1) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(dataSize)
        }.array()
    }

    /** RMS amplitude — энергия сигнала */
    private fun calculateRms(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) {
            sum += buffer[i] * buffer[i]
        }
        return Math.sqrt(sum / read)
    }
}
