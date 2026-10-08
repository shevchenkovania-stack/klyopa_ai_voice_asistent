package com.aiagent.ai_voice_agent.engine.session

import android.util.Log
import kotlinx.coroutines.*

/**
 * AssistantSessionManager — "Мозг" (State Machine).
 *
 * Единственная точка принятия решений:
 * - В каком состоянии находится ассистент
 * - Кто сейчас владеет микрофоном (через AudioOwner)
 * - Какие события допустимы в каждом состоянии
 *
 * НЕ открывает микрофон, НЕ распознаёт речь, НЕ генерирует TTS.
 * Только управляет переходами между состояниями.
 */
object AssistantSessionManager {
    private const val TAG = "SessionManager"

    // ==================== Состояния ====================

    enum class State {
        IDLE,           // Wake word слушает (микрофон → Vosk)
        LISTENING,      // STT распознаёт речь (микрофон → STT)
        THINKING,       // LLM думает (микрофон заблокирован)
        SPEAKING,       // TTS говорит (микрофон → STT для barge-in)
        CONVERSING,     // Пауза — ждём ответ пользователя (микрофон → STT)
        FAREWELL,       // Прощаемся → через 2с → IDLE
    }

    enum class Event {
        WAKE_WORD_DETECTED,
        SPEECH_START,
        SPEECH_END,
        STT_RESULT,
        AGENT_RESPONSE_READY,
        TTS_STARTED,
        TTS_FINISHED,
        USER_BARGE_IN,
        SILENCE_TIMEOUT,
        FAREWELL_DETECTED,
        TIMEOUT,
        ERROR,
        CANCEL,
    }

    enum class AudioOwner {
        WAKE_WORD,  // Vosk слушает wake word
        STT,        // Распознавание речи (+ VAD для barge-in)
        NONE,       // Микрофон свободен (сливается в никуда)
    }

    // ==================== Состояние ====================

    @Volatile
    var currentState: State = State.IDLE
        private set

    val currentAudioOwner: AudioOwner
        get() = when (currentState) {
            State.IDLE -> AudioOwner.WAKE_WORD
            State.LISTENING, State.CONVERSING -> AudioOwner.STT
            State.SPEAKING -> AudioOwner.STT  // VAD слушает barge-in
            State.THINKING, State.FAREWELL -> AudioOwner.NONE
        }

    // ==================== Callbacks ====================

    var onStateChanged: ((old: State, new: State) -> Unit)? = null
    var onAudioOwnerChanged: ((owner: AudioOwner) -> Unit)? = null

    // ==================== Таймеры ====================

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var silenceTimer: Job? = null
    private var farewellTimer: Job? = null

    // ==================== Управление событиями ====================

    /**
     * ГЛАВНЫЙ МЕТОД — обработка события.
     * Вызывается из любого места: WakeWordService, Pipeline, VAD, UI.
     */
    fun handleEvent(event: Event) {
        val newState = resolveTransition(currentState, event)
        if (newState == null) {
            Log.w(TAG, "Event $event ignored in state $currentState")
            return
        }
        if (newState == currentState) {
            return
        }

        val oldState = currentState
        currentState = newState
        val newOwner = currentAudioOwner

        Log.i(TAG, "State: $oldState → $newState (owner: $newOwner) event: $event")

        // Уведомляем подписчиков
        onStateChanged?.invoke(oldState, newState)
        onAudioOwnerChanged?.invoke(newOwner)

        // Управление таймерами
        cancelSilenceTimer()
        cancelFarewellTimer()

        when (newState) {
            State.FAREWELL -> startFarewellTimer()
            State.CONVERSING -> startSilenceTimer()
            else -> {} // no timers needed
        }
    }

    /**
     * Старт в режиме IDLE (wake word слушает).
     * Вызывается из EngineManager.initialize().
     */
    fun startInIdle() {
        currentState = State.IDLE
        Log.i(TAG, "Started in IDLE (wake word listening)")
        onAudioOwnerChanged?.invoke(AudioOwner.WAKE_WORD)
    }

    // ==================== Таблица переходов ====================

    private fun resolveTransition(from: State, event: Event): State? {
        return when (from to event) {
            // IDLE — ждём wake word (или late TTS после fallback LLM)
            (State.IDLE to Event.WAKE_WORD_DETECTED) -> State.LISTENING
            (State.IDLE to Event.TTS_STARTED) -> State.SPEAKING

            // LISTENING — пользователь говорит
            (State.LISTENING to Event.SPEECH_END) -> State.THINKING
            (State.LISTENING to Event.CANCEL) -> State.IDLE
            (State.LISTENING to Event.ERROR) -> State.IDLE

            // THINKING — LLM думает
            (State.THINKING to Event.AGENT_RESPONSE_READY) -> State.SPEAKING
            (State.THINKING to Event.TTS_STARTED) -> State.SPEAKING
            (State.THINKING to Event.TTS_FINISHED) -> State.IDLE   // TTS failed before SPEAKING
            (State.THINKING to Event.USER_BARGE_IN) -> State.LISTENING
            (State.THINKING to Event.TIMEOUT) -> State.IDLE
            (State.THINKING to Event.ERROR) -> State.IDLE
            (State.THINKING to Event.CANCEL) -> State.IDLE

            // SPEAKING — TTS говорит (VAD слушает barge-in)
            (State.SPEAKING to Event.TTS_FINISHED) -> State.CONVERSING
            (State.SPEAKING to Event.USER_BARGE_IN) -> State.LISTENING
            (State.SPEAKING to Event.CANCEL) -> State.IDLE
            (State.SPEAKING to Event.ERROR) -> State.IDLE

            // CONVERSING — ждём ответ / продолжение
            (State.CONVERSING to Event.SPEECH_START) -> State.LISTENING
            (State.CONVERSING to Event.TTS_STARTED) -> State.SPEAKING  // Response TTS starts after filler
            (State.CONVERSING to Event.SILENCE_TIMEOUT) -> State.IDLE
            (State.CONVERSING to Event.FAREWELL_DETECTED) -> State.FAREWELL
            (State.CONVERSING to Event.CANCEL) -> State.IDLE

            // FAREWELL — прощание
            (State.FAREWELL to Event.SILENCE_TIMEOUT) -> State.IDLE
            (State.FAREWELL to Event.CANCEL) -> State.IDLE

            // Запрещённые переходы
            else -> null
        }
    }

    // ==================== Таймеры ====================

    private fun startSilenceTimer() {
        silenceTimer = scope.launch {
            delay(15_000) // 15 секунд тишины
            handleEvent(Event.SILENCE_TIMEOUT)
        }
    }

    private fun cancelSilenceTimer() {
        silenceTimer?.cancel()
        silenceTimer = null
    }

    private fun startFarewellTimer() {
        farewellTimer = scope.launch {
            delay(2_000) // 2 секунды на прощание
            handleEvent(Event.SILENCE_TIMEOUT) // → IDLE
        }
    }

    private fun cancelFarewellTimer() {
        farewellTimer?.cancel()
        farewellTimer = null
    }

    // ==================== Cleanup ====================

    fun shutdown() {
        cancelSilenceTimer()
        cancelFarewellTimer()
        scope.cancel()
        currentState = State.IDLE
        Log.i(TAG, "SessionManager shut down")
    }
}
