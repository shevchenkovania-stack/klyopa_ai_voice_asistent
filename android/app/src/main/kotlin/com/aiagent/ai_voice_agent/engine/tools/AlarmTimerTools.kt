package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

/** Установить будильник */
class SetAlarmTool(private val context: Context) : AgentTool {
    override val name = "set_alarm"
    override val description = "Установить будильник на указанное время"
    override val parameters = listOf(
        ToolParam("hour", "integer", "Часы (0-23)", required = true),
        ToolParam("minute", "integer", "Минуты (0-59)", required = true),
        ToolParam("label", "string", "Название будильника", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val hour = (params["hour"] as? Number)?.toInt() ?: run {
            return ToolResult.failure("Не указано время будильника")
        }
        val minute = (params["minute"] as? Number)?.toInt() ?: run {
            return ToolResult.failure("Не указано время будильника")
        }
        val label = params["label"] as? String ?: "Будильник"

        if (hour !in 0..23 || minute !in 0..59) {
            return ToolResult.failure("Неверное время. Часы: 0-23, минуты: 0-59")
        }

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            context.startActivity(intent)
            val timeStr = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
            ToolResult.success("Будильник установлен на $timeStr")
        } catch (e: Exception) {
            ToolResult.failure("Не удалось установить будильник: ${e.message}")
        }
    }
}

/** Отменить будильник */
class CancelAlarmTool(private val context: Context) : AgentTool {
    override val name = "cancel_alarm"
    override val description = "Отменить установленный будильник"
    override val parameters = listOf(
        ToolParam("alarm_id", "integer", "ID будильника для отмены (если известен)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Будильник отменён")
        } catch (e: Exception) {
            ToolResult.failure("Не удалось отменить будильник: ${e.message}")
        }
    }
}

/** Установить таймер */
class SetTimerTool(private val context: Context) : AgentTool {
    override val name = "set_timer"
    override val description = "Установить таймер обратного отсчёта (например \"на 5 минут\", \"на 30 секунд\"). Таймер сработает в системном приложении Часы."
    override val parameters = listOf(
        ToolParam("seconds", "integer", "Длительность таймера в секундах", required = true),
        ToolParam("label", "string", "Название таймера (необязательно)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val seconds = (params["seconds"] as? Number)?.toInt() ?: run {
            return ToolResult.failure("Не указана длительность таймера")
        }
        if (seconds <= 0) return ToolResult.failure("Длительность должна быть положительной")

        val label = params["label"] as? String ?: ""

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            context.startActivity(intent)
            val minutes = seconds / 60
            val secs = seconds % 60
            val timeStr = if (minutes > 0) "$minutes мин $secs сек" else "$secs сек"
            ToolResult.success("Таймер установлен на $timeStr")
        } catch (e: Exception) {
            ToolResult.failure("Не удалось установить таймер: ${e.message}")
        }
    }
}

/** Отменить таймер */
class CancelTimerTool(private val context: Context) : AgentTool {
    override val name = "cancel_timer"
    override val description = "Отменить установленный таймер"
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_DISMISS_TIMER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Таймер отменён")
        } catch (e: Exception) {
            ToolResult.failure("Не удалось отменить таймер: ${e.message}")
        }
    }
}
