package com.aiagent.ai_voice_agent.engine.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Engine configuration — reads from SharedPreferences (shared with Flutter via EncryptedSharedPreferences)
 */
class EngineConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_voice_agent_config", Context.MODE_PRIVATE)

    var agentName: String
        get() = prefs.getString("agent_name", "Клёпа") ?: "Клёпа"
        set(value) = prefs.edit().putString("agent_name", value).apply()

    var groqApiKey: String
        get() = prefs.getString("groq_api_key", "") ?: ""
        set(value) = prefs.edit().putString("groq_api_key", value).apply()

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    var openaiApiKey: String
        get() = prefs.getString("openai_api_key", "") ?: ""
        set(value) = prefs.edit().putString("openai_api_key", value).apply()

    var activeProvider: String
        get() = prefs.getString("active_provider", "groq") ?: "groq"
        set(value) = prefs.edit().putString("active_provider", value).apply()

    var language: String
        get() = prefs.getString("language", "ru") ?: "ru"
        set(value) = prefs.edit().putString("language", value).apply()

    var ttsVoice: String
        get() = prefs.getString("tts_voice", "ru-RU-DmitryNeural") ?: "ru-RU-DmitryNeural"
        set(value) = prefs.edit().putString("tts_voice", value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat("tts_speed", 1.0f)
        set(value) = prefs.edit().putFloat("tts_speed", value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("tts_pitch", value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake_word_enabled", false)
        set(value) = prefs.edit().putBoolean("wake_word_enabled", value).apply()

    var wakeWordName: String
        get() = prefs.getString("wake_word_name", "клёпа") ?: "клёпа"
        set(value) = prefs.edit().putString("wake_word_name", value).apply()

    // ===== Feature flags (внутренний конфиг для отладки) =====

    /** Новая архитектура: SessionManager + SttAudioConsumer + AudioSessionManager routing */
    var useNewSessionArchitecture: Boolean = false

    /** Foreground service (WakeWordService) — слушает wake word в фоне */
    var enableForegroundService: Boolean = false

    /** Continuous dialogue — авто-слушание после ответа */
    var enableContinuousDialogue: Boolean = false
}
