package com.aiagent.ai_voice_agent.engine.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Пресет настроек — набор конфигурации для быстрого переключения.
 */
data class SettingsPreset(
    val id: String,
    val name: String,
    val description: String,
    val provider: String,
    val ttsVoice: String,
    val ttsSpeed: Float,
    val ttsPitch: Float
)

/**
 * Менеджер пресетов — управление наборами настроек.
 */
class PresetManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_voice_agent_presets", Context.MODE_PRIVATE)

    companion object {
        // Готовые пресеты
        val PRESET_CURRENT = SettingsPreset(
            id = "current",
            name = "Текущий",
            description = "OpenAI + DmitryNeural (как сейчас)",
            provider = "openai",
            ttsVoice = "ru-RU-DmitryNeural",
            ttsSpeed = 1.0f,
            ttsPitch = 1.0f
        )

        val PRESET_FAST = SettingsPreset(
            id = "fast",
            name = "Быстрый",
            description = "Groq (быстрее в 5 раз) + SvetlanaNeural",
            provider = "groq",
            ttsVoice = "ru-RU-SvetlanaNeural",
            ttsSpeed = 1.1f,
            ttsPitch = 1.0f
        )

        val ALL_PRESETS = listOf(PRESET_CURRENT, PRESET_FAST)
    }

    var activePresetId: String
        get() = prefs.getString("active_preset", "current") ?: "current"
        set(value) = prefs.edit().putString("active_preset", value).apply()

    fun getActivePreset(): SettingsPreset {
        return ALL_PRESETS.find { it.id == activePresetId } ?: PRESET_CURRENT
    }

    fun applyPreset(preset: SettingsPreset, config: EngineConfig) {
        config.activeProvider = preset.provider
        config.ttsVoice = preset.ttsVoice
        config.ttsSpeed = preset.ttsSpeed
        config.ttsPitch = preset.ttsPitch
        activePresetId = preset.id
    }
}
