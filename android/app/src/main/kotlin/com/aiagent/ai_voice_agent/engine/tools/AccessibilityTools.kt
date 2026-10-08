package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import com.aiagent.ai_voice_agent.services.AgentAccessibilityService

/**
 * Read what's on the screen — agent's "eyes".
 */
class ReadScreenTool : AgentTool {
    override val name = "read_screen"
    override val description = "Прочитать содержимое экрана. Возвращает список элементов с их типами и текстом."
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён. Скажи пользователю включить его в настройках специальных возможностей.")
        }
        val content = AgentAccessibilityService.readScreen()
        return ToolResult.success(content)
    }
}

/**
 * Click on a UI element by its text — agent's "finger".
 */
class ClickElementTool : AgentTool {
    override val name = "click_element"
    override val description = "Нажать на элемент на экране по его тексту. Например: нажать на \"Отправить\", нажать на \"Настройки\"."
    override val parameters = listOf(
        ToolParam("text", "string", "Текст элемента, на который нужно нажать", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён.")
        }
        val text = params["text"] as? String ?: return ToolResult.failure("Не указан текст элемента")
        val clicked = AgentAccessibilityService.clickByText(text)
        return if (clicked) {
            ToolResult.success("✓ Нажал на \"$text\"")
        } else {
            ToolResult.failure("Не удалось найти или нажать на \"$text\"")
        }
    }
}

/**
 * Type text into a field — agent's "keyboard".
 */
class TypeTextTool : AgentTool {
    override val name = "type_text"
    override val description = "Ввести текст в активное поле ввода. Можно указать метку поля для точного позиционирования."
    override val parameters = listOf(
        ToolParam("text", "string", "Текст для ввода", required = true),
        ToolParam("field_label", "string", "Метка/подсказка поля (необязательно)")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён.")
        }
        val text = params["text"] as? String ?: return ToolResult.failure("Не указан текст")
        val fieldLabel = params["field_label"] as? String
        val typed = AgentAccessibilityService.typeText(text, fieldLabel)
        return if (typed) {
            ToolResult.success("✓ Ввёл текст: \"$text\"")
        } else {
            ToolResult.failure("Не удалось ввести текст. Нет активного поля ввода.")
        }
    }
}

/**
 * Navigate: back, home, recents, notifications, quick_settings.
 */
class NavigateTool : AgentTool {
    override val name = "navigate"
    override val description = "Системная навигация: назад, домой, недавние, уведомления, быстрые настройки."
    override val parameters = listOf(
        ToolParam("action", "string", "Действие навигации", required = true,
            enumValues = listOf("back", "home", "recents", "notifications", "quick_settings", "power_dialog"))
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён.")
        }
        val action = params["action"] as? String ?: return ToolResult.failure("Не указано действие")
        val ok = AgentAccessibilityService.globalAction(action)
        val actionNames = mapOf(
            "back" to "Назад",
            "home" to "Домой",
            "recents" to "Недавние приложения",
            "notifications" to "Панель уведомлений",
            "quick_settings" to "Быстрые настройки",
            "power_dialog" to "Меню питания"
        )
        val name = actionNames[action] ?: action
        return if (ok) {
            ToolResult.success("✓ $name")
        } else {
            ToolResult.failure("Не удалось выполнить: $name")
        }
    }
}

/**
 * Scroll the current screen — up or down.
 */
class ScrollTool : AgentTool {
    override val name = "scroll"
    override val description = "Прокрутить экран вверх или вниз."
    override val parameters = listOf(
        ToolParam("direction", "string", "Направление прокрутки", required = true,
            enumValues = listOf("down", "up"))
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён.")
        }
        val direction = params["direction"] as? String ?: "down"
        val ok = AgentAccessibilityService.scroll(direction)
        val dirName = if (direction == "up") "вверх" else "вниз"
        return if (ok) {
            ToolResult.success("✓ Прокрутил $dirName")
        } else {
            ToolResult.failure("Не удалось прокрутить — нет прокручиваемого контейнера")
        }
    }
}

/**
 * List clickable elements on screen.
 */
class ListClickableTool : AgentTool {
    override val name = "list_clickable"
    override val description = "Показать все кликабельные элементы на экране с их координатами."
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён.")
        }
        val list = AgentAccessibilityService.listClickableElements()
        return ToolResult.success(list)
    }
}
