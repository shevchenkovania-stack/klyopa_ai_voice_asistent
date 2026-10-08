package com.aiagent.ai_voice_agent.engine.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

// ==================== CLIPBOARD READ ====================
class ClipboardReadTool(private val context: Context) : AgentTool {
    override val name = "clipboard_read"
    override val description = """
        Прочитать текст из буфера обмена.
        ЧТО ДЕЛАЕТ: Возвращает содержимое буфера обмена (последний скопированный текст).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "что в буфере", "что я скопировал", "вставь из буфера".
        ПАРАМЕТРЫ: Нет параметров.
        ВОЗВРАЩАЕТ: "Clipboard: [текст]" или "Clipboard is empty"
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) return ToolResult.success("Clipboard is empty")
        val text = clip.getItemAt(0).text?.toString() ?: ""
        return if (text.isNotEmpty()) ToolResult.success("Clipboard: $text")
        else ToolResult.success("Clipboard contains non-text content")
    }
}

// ==================== CLIPBOARD WRITE ====================
class ClipboardWriteTool(private val context: Context) : AgentTool {
    override val name = "clipboard_write"
    override val description = """
        Скопировать текст в буфер обмена.
        ЧТО ДЕЛАЕТ: Копирует указанный текст в буфер обмена (как Ctrl+C).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "скопируй это", "запомни текст", "копировать в буфер".
        ПАРАМЕТРЫ: text (string, обязательно) — текст для копирования.
        ВОЗВРАЩАЕТ: "Copied: [первые 50 символов]..."
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "text", description = "Текст для копирования в буфер обмена", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val text = params["text"] as? String ?: return ToolResult.failure("Text is required")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AI Agent", text))
        return ToolResult.success("Copied: ${text.take(50)}${if (text.length > 50) "..." else ""}")
    }
}

// ==================== VOLUME CONTROL ====================
class VolumeControlTool(private val context: Context) : AgentTool {
    override val name = "volume_control"
    override val description = """
        Управление громкостью устройства.
        ЧТО ДЕЛАЕТ: Устанавливает громкость для медиа, звонка, уведомлений или системных звуков.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "сделай громче", "убавь звук", "выключи звук", "тише".
        ПАРАМЕТРЫ: 
          - stream (string, обязательно) — тип звука: 'media' (музыка/видео), 'ringtone' (звонок), 'notification' (уведомления), 'system' (система)
          - level (string, обязательно) — уровень громкости: число 0-100, или 'mute' (без звука), 'max' (максимум)
        ВОЗВРАЩАЕТ: "media volume set to 75%"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "stream", description = "Тип звука: 'media' (музыка), 'ringtone' (звонок), 'notification' (уведомления), 'system' (система)", type = "string", required = true),
        ToolParam(name = "level", description = "Уровень громкости: 0-100, или 'mute' (без звука), 'max' (максимум)", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val streamName = params["stream"] as? String ?: "media"
        val levelStr = params["level"] as? String ?: return ToolResult.failure("Level is required")
        
        val streamType = when (streamName.lowercase()) {
            "media", "music" -> AudioManager.STREAM_MUSIC
            "ringtone", "ring" -> AudioManager.STREAM_RING
            "notification", "alarm" -> AudioManager.STREAM_NOTIFICATION
            "system" -> AudioManager.STREAM_SYSTEM
            else -> AudioManager.STREAM_MUSIC
        }
        
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(streamType)
        
        val target = when (levelStr.lowercase()) {
            "mute", "0" -> 0
            "max", "100" -> maxVol
            else -> {
                val pct = levelStr.toIntOrNull() ?: return ToolResult.failure("Invalid level: $levelStr")
                (pct * maxVol / 100).coerceIn(0, maxVol)
            }
        }
        
        am.setStreamVolume(streamType, target, 0)
        return ToolResult.success("$streamName volume set to ${(target * 100 / maxVol)}%")
    }
}

// ==================== BRIGHTNESS CONTROL ====================
class BrightnessControlTool(private val context: Context) : AgentTool {
    override val name = "brightness"
    override val description = """
        Управление яркостью экрана.
        ЧТО ДЕЛАЕТ: Устанавливает яркость экрана вручную или включает авторежим.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "сделай ярче", "убавь яркость", "включи автояркость".
        ПАРАМЕТРЫ: level (string, обязательно) — число 0-100 или 'auto' (авторежим).
        ВОЗВРАЩАЕТ: "Brightness set to 75%" или "Brightness set to AUTO"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "level", description = "Яркость: число 0-100 или 'auto' (автоматическая регулировка)", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val levelStr = params["level"] as? String ?: return ToolResult.failure("Level is required")
        
        return try {
            if (levelStr.lowercase() == "auto") {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                ToolResult.success("Brightness set to AUTO")
            } else {
                val pct = levelStr.toIntOrNull() ?: return ToolResult.failure("Invalid level: $levelStr")
                val brightness = (pct * 255 / 100).coerceIn(10, 255)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
                ToolResult.success("Brightness set to $pct%")
            }
        } catch (e: Exception) {
            ToolResult.failure("Cannot change brightness: ${e.message}")
        }
    }
}

// ==================== FLASHLIGHT ====================
class FlashlightTool(private val context: Context) : AgentTool {
    override val name = "flashlight"
    override val description = """
        Включить или выключить вспышку (фонарик).
        ЧТО ДЕЛАЕТ: Включает или выключает светодиодную вспышку на задней панели устройства.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "включи фонарик", "выключи вспышку", "посвети".
        ПАРАМЕТРЫ: state (string, обязательно) — 'on' (включить) или 'off' (выключить).
        ВОЗВРАЩАЕТ: "Flashlight: ON" или "Flashlight: OFF"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "state", description = "Состояние вспышки: 'on' (включить) или 'off' (выключить)", type = "string", required = true)
    )

    private var cameraId: String? = null
    private var isOn = false

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val state = params["state"] as? String ?: "on"
        val turnOn = state.lowercase() != "off"
        
        if (turnOn == isOn) return ToolResult.success("Flashlight already ${if (isOn) "ON" else "OFF"}")
        
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (cameraId == null) cameraId = cm.cameraIdList.firstOrNull()
            if (cameraId == null) return ToolResult.failure("No camera found")
            
            cm.setTorchMode(cameraId!!, turnOn)
            isOn = turnOn
            ToolResult.success("Flashlight ${if (turnOn) "ON" else "OFF"}")
        } catch (e: Exception) {
            ToolResult.failure("Flashlight error: ${e.message}")
        }
    }
}
