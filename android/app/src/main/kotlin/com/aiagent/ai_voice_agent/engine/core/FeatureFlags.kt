package com.aiagent.ai_voice_agent.engine.core

/**
 * FeatureFlags — внутренние флаги архитектуры (debug-переключатели).
 *
 * НЕ сохраняются в SharedPreferences — только compile-time toggles.
 * Пользовательские настройки (wake word, voice, provider) — в EngineConfig.
 */
object FeatureFlags {

    /** Новая архитектура: SessionManager + SttAudioConsumer + AudioSessionManager routing */
    var useNewSessionArchitecture: Boolean = true

    /** Foreground service (WakeWordService) — слушает wake word в фоне */
    var enableForegroundService: Boolean = false

    /** Continuous dialogue — авто-слушание после ответа */
    var enableContinuousDialogue: Boolean = true
}
