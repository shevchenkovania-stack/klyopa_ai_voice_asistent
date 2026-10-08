package com.aiagent.ai_voice_agent.engine.tools

import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Get current date and time
 */
class GetCurrentTimeTool : AgentTool {
    override val name = "get_current_time"
    override val description = "Получить текущую дату и время на устройстве"
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val now = LocalDateTime.now()

        val weekdays = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
        val months = listOf("января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря")

        val weekday = weekdays[now.dayOfWeek.value - 1]
        val month = months[now.monthValue - 1]
        val formatted = "$weekday, ${now.dayOfMonth} $month ${now.year}, ${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"

        return ToolResult.success(
            "Текущее время: $formatted",
            mapOf(
                "hour" to now.hour,
                "minute" to now.minute,
                "day" to now.dayOfMonth,
                "month" to now.monthValue,
                "year" to now.year,
                "weekday" to now.dayOfWeek.value
            )
        )
    }
}
