package com.aiagent.ai_voice_agent.engine.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.aiagent.ai_voice_agent.engine.session.AssistantSessionManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Единый владелец микрофона — "Сердце".
 *
 * Полная инкапсуляция AudioRecord:
 * - Один AudioRecord на всё приложение
 * - Один read-loop
 * - AEC + NS управляются автоматически
 * - Маршрутизация PCM через AudioConsumer
 *
 * ВАЖНО: AudioRecord НЕ release() при смене владельца.
 * Только stop()/startRecording() для переключения.
 * release() — только в shutdown().
 *
 * Thread safety:
 * - consumers — ConcurrentHashMap
 * - currentConsumer — @Volatile
 * - recordingJob — AtomicReference
 */
object AudioSessionManager {
    private const val TAG = "AudioSession"
    private const val SAMPLE_RATE = 16000

    // ==================== AudioConsumer Interface ====================

    /**
     * Каждый потребитель реализует этот интерфейс.
     * Получает PCM-данные когда он — активный владелец.
     */
    interface AudioConsumer {
        val id: String
        fun onActivated()
        fun onDeactivated()
        fun onAudioData(buffer: ShortArray, read: Int)
    }

    // ==================== Legacy Owner (backward compat) ====================

    enum class Owner { WAKE_WORD, CONTINUOUS, SINGLE_LISTEN }

    // ==================== State ====================

    @Volatile
    private var activeOwner: Owner? = null

    private var audioRecord: AudioRecord? = null
    private var acousticEchoCanceler: android.media.audiofx.AcousticEchoCanceler? = null
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
    private var recordingJob: Job? = null

    @Volatile
    private var isStreaming = false

    // ==================== Consumer-based routing ====================

    private val consumers = ConcurrentHashMap<AssistantSessionManager.AudioOwner, AudioConsumer>()

    @Volatile
    private var currentConsumer: AudioConsumer? = null

    /**
     * Регистрация потребителя для конкретного AudioOwner.
     * Вызывается при инициализации (один раз).
     */
    fun registerConsumer(owner: AssistantSessionManager.AudioOwner, consumer: AudioConsumer) {
        consumers[owner] = consumer
        Log.d(TAG, "Registered consumer: ${consumer.id} for owner: $owner")
    }

    /**
     * ГЛАВНЫЙ МЕТОД — переключение владельца микрофона.
     * Вызывается из AssistantSessionManager.onAudioOwnerChanged.
     *
     * НЕ release() AudioRecord — только stop/startRecording.
     */
    fun setOwner(newOwner: AssistantSessionManager.AudioOwner) {
        val oldConsumer = currentConsumer
        val newConsumer = consumers[newOwner]

        if (oldConsumer == newConsumer) return

        Log.i(TAG, "Owner change: ${oldConsumer?.id ?: "null"} → ${newConsumer?.id ?: "null"}")

        // 1. Деактивируем старого потребителя
        try { oldConsumer?.onDeactivated() } catch (e: Exception) {
            Log.e(TAG, "onDeactivated error: ${e.message}")
        }

        currentConsumer = newConsumer

        // 2. Если NONE — останавливаем запись (но НЕ release)
        if (newOwner == AssistantSessionManager.AudioOwner.NONE) {
            pauseRecording()
            return
        }

        // 3. Если микрофон ещё не открыт — открываем
        if (!isStreaming) {
            startStreaming()
        } else {
            // Микрофон уже открыт — просто resume
            resumeRecording()
        }

        // 4. Активируем нового потребителя
        try { newConsumer?.onActivated() } catch (e: Exception) {
            Log.e(TAG, "onActivated error: ${e.message}")
        }

        Log.i(TAG, "Owner active: ${newConsumer?.id}")
    }

    // ==================== AudioRecord lifecycle ====================

    /**
     * Открывает AudioRecord И запускает read-loop.
     * Вызывается ОДИН раз, далее только pause/resume.
     * Если нет permission — логирует и выходит (retry позже).
     */
    private fun startStreaming() {
        if (isStreaming) return

        // Проверка permission — если нет, выходим (retry через startWhenReady)
        val ctx = com.aiagent.ai_voice_agent.engine.EngineManager.context
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted — deferring AudioRecord creation")
            return
        }

        isStreaming = true

        try {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufSize <= 0) {
                Log.e(TAG, "Invalid buffer size: $minBufSize")
                isStreaming = false
                return
            }
            val bufferSize = maxOf(minBufSize, 4096)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                record.release()
                isStreaming = false
                return
            }

            // AEC/NS
            try {
                acousticEchoCanceler = android.media.audiofx.AcousticEchoCanceler
                    .create(record.audioSessionId)
                noiseSuppressor = android.media.audiofx.NoiseSuppressor
                    .create(record.audioSessionId)
                acousticEchoCanceler?.setEnabled(true)
                noiseSuppressor?.setEnabled(true)
            } catch (e: Exception) {
                Log.w(TAG, "AEC/NS init failed (non-fatal): ${e.message}")
            }

            audioRecord = record

            // Read loop
            recordingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val buffer = ShortArray(bufferSize / 2)
                record.startRecording()
                Log.d(TAG, "Recording started (buf=$bufferSize)")

                try {
                    while (isActive) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            routeAudio(buffer, read)
                        } else if (read < 0) {
                            Log.e(TAG, "read() error: $read")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Read loop error: ${e.message}")
                }
            }

            Log.d(TAG, "Streaming started (AEC=${acousticEchoCanceler != null}, NS=${noiseSuppressor != null})")
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied", e)
            isStreaming = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming: ${e.message}", e)
            cleanup()
            isStreaming = false
        }
    }

    /**
     * Пауза записи — AudioRecord.stop() БЕЗ release().
     * Микрофон свободен, но объект жив.
     */
    private fun pauseRecording() {
        try {
            audioRecord?.stop()
            Log.d(TAG, "Recording paused (AudioRecord stopped, not released)")
        } catch (e: Exception) {
            Log.w(TAG, "pauseRecording error: ${e.message}")
        }
    }

    /**
     * Возобновление записи — AudioRecord.startRecording() БЕЗ пересоздания.
     */
    private fun resumeRecording() {
        try {
            audioRecord?.startRecording()
            Log.d(TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.w(TAG, "resumeRecording error: ${e.message}")
        }
    }

    /**
     * Вызывается ПОСЛЕ предоставления permission.
     * Пытается открыть микрофон если есть pending consumer.
     */
    fun startWhenReady() {
        if (isStreaming) {
            Log.d(TAG, "Already streaming — startWhenReady no-op")
            return
        }
        val owner = currentConsumer
        if (owner != null) {
            Log.i(TAG, "startWhenReady: retrying startStreaming for ${owner.id}")
            startStreaming()
            if (isStreaming) {
                try { owner.onActivated() } catch (_: Exception) {}
            }
        } else {
            Log.d(TAG, "startWhenReady: no consumer registered yet")
        }
    }

    /**
     * МАРШРУТИЗАЦИЯ — PCM → текущему потребителю.
     */
    private fun routeAudio(buffer: ShortArray, read: Int) {
        currentConsumer?.onAudioData(buffer, read)
    }

    // ==================== Legacy API (backward compat) ====================

    /**
     * Legacy: start() для обратной совместимости.
     * Используется ContinuousDialogueManager и PipelineOrchestrator.
     */
    fun start(owner: Owner, onFrame: (ShortArray, Int) -> Unit): Boolean {
        val current = activeOwner
        if (current == owner) {
            Log.w(TAG, "Already started for $owner")
            return true
        }
        if (current != null) {
            Log.w(TAG, "Force-releasing $current for $owner")
            stopLegacy()
        }

        return try {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufSize <= 0) {
                Log.e(TAG, "Invalid min buffer size: $minBufSize")
                return false
            }
            val bufferSize = maxOf(minBufSize, 4096)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized for $owner")
                record.release()
                return false
            }

            try {
                acousticEchoCanceler = android.media.audiofx.AcousticEchoCanceler
                    .create(record.audioSessionId)
                noiseSuppressor = android.media.audiofx.NoiseSuppressor
                    .create(record.audioSessionId)
                acousticEchoCanceler?.setEnabled(true)
                noiseSuppressor?.setEnabled(true)
            } catch (e: Exception) {
                Log.w(TAG, "AEC/NS init failed (non-fatal): ${e.message}")
            }

            audioRecord = record
            activeOwner = owner
            isStreaming = true

            recordingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val buffer = ShortArray(bufferSize / 2)
                record.startRecording()
                Log.d(TAG, "[$owner] Recording started (buf=$bufferSize)")

                try {
                    while (isActive) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            onFrame(buffer, read)
                        } else if (read < 0) {
                            Log.e(TAG, "[$owner] read() error: $read")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[$owner] Read loop error: ${e.message}")
                }
            }

            Log.d(TAG, "Legacy session started: $owner")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session for $owner: ${e.message}", e)
            cleanup()
            false
        }
    }

    /**
     * Legacy: stop() для обратной совместимости.
     */
    fun stop() {
        stopLegacy()
    }

    private fun stopLegacy() {
        val owner = activeOwner ?: return
        Log.d(TAG, "Stopping legacy session: $owner")
        recordingJob?.cancel()
        cleanup()
        activeOwner = null
        Log.d(TAG, "Legacy session stopped: $owner")
    }

    // ==================== Shutdown (полная очистка) ====================

    /**
     * Полная очистка — release() AudioRecord.
     * Вызывается ТОЛЬКО при shutdown() EngineManager.
     */
    fun shutdown() {
        Log.d(TAG, "Full shutdown — releasing AudioRecord")
        recordingJob?.cancel()
        cleanup()
        consumers.clear()
        currentConsumer = null
        activeOwner = null
        isStreaming = false
    }

    private fun cleanup() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        try { acousticEchoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}

        audioRecord = null
        acousticEchoCanceler = null
        noiseSuppressor = null
        recordingJob = null
        isStreaming = false
    }

    // ==================== Status ====================

    fun currentOwner(): Owner? = activeOwner
    fun isRecording(): Boolean = activeOwner != null || isStreaming
}
