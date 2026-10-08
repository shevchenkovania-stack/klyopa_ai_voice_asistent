package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import com.aiagent.ai_voice_agent.services.AgentNotificationListener

/**
 * Read active notifications — agent's "ears".
 */
class ReadNotificationsTool(private val context: Context) : AgentTool {
    override val name = "read_notifications"
    override val description = "Прочитать все активные уведомления на телефоне. Показывает приложение, заголовок и текст каждого уведомления."
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val result = AgentNotificationListener.getActiveNotifications(context)
        return ToolResult.success(result)
    }
}

/**
 * Dismiss a notification by app name.
 */
class DismissNotificationTool : AgentTool {
    override val name = "dismiss_notification"
    override val description = "Убрать/закрыть уведомление по названию приложения."
    override val parameters = listOf(
        ToolParam("app_name", "string", "Название приложения (например \"Telegram\", \"WhatsApp\")", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val svc = AgentNotificationListener.instance
            ?: return ToolResult.failure("NotificationListener не запущен.")

        val appName = params["app_name"] as? String ?: return ToolResult.failure("Не указано приложение")
        val appNameLower = appName.lowercase()

        val sbns = svc.activeNotifications ?: return ToolResult.failure("Нет уведомлений")
        val match = sbns.find {
            it.packageName.lowercase().contains(appNameLower)
        } ?: return ToolResult.failure("Нет уведомлений от \"$appName\"")

        return try {
            svc.cancelNotification(match.packageName, match.tag, match.id)
            ToolResult.success("✓ Уведомление от \"$appName\" убрано")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка: ${e.message}")
        }
    }
}
