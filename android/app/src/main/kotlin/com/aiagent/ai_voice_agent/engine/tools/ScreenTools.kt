/**
 * ScreenTools — инструменты для взаимодействия с экраном.
 *
 * Файл содержит 2 инструмента:
 * - `take_screenshot` — прочитать содержимое экрана (текстовый "скриншот")
 * - `explain_screen` — объяснить что происходит на экране (через LLM)
 *
 * Все инструменты требуют включённый AgentAccessibilityService.
 *
 * Зависимости: AgentAccessibilityService
 */
package com.aiagent.ai_voice_agent.engine.tools

import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import com.aiagent.ai_voice_agent.services.AgentAccessibilityService

/**
 * Инструмент `take_screenshot` — сделать "скриншот" экрана (текстовый).
 * Возвращает содержимое экрана в текстовом виде.
 */
class TakeScreenshotTool : AgentTool {
    companion object { private const val TAG = "TakeScreenshot" }

    override val name = "take_screenshot"
    override val description = """
        Сделать скриншот экрана (текстовый).
        ЧТО ДЕЛАЕТ: Читает содержимое текущего экрана и возвращает в текстовом виде.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "сделай скриншот", "что на экране", "покажи экран".
        ПАРАМЕТРЫ: Нет параметров.
        ВОЗВРАЩАЕТ: Текстовое содержимое экрана: "Приложение: WhatsApp\nTextView: Привет\nButton: Отправить"
        ОГРАНИЧЕНИЯ: Требует включённый AccessibilityService.
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён. Скажи пользователю включить его в настройках специальных возможностей.")
        }

        return try {
            val content = AgentAccessibilityService.readScreen()
            android.util.Log.d(TAG, "Screenshot taken: ${content.take(100)}...")
            ToolResult.success(content)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to take screenshot: ${e.message}")
            ToolResult.failure("Не удалось сделать скриншот: ${e.message}")
        }
    }
}

/**
 * Инструмент `explain_screen` — объяснить что происходит на экране.
 * Использует LLM для анализа содержимого экрана.
 */
class ExplainScreenTool : AgentTool {
    companion object { private const val TAG = "ExplainScreen" }

    override val name = "explain_screen"
    override val description = """
        Объяснить что происходит на экране.
        ЧТО ДЕЛАЕТ: Читает содержимое экрана и объясняет пользователю что открыто, что видно, что можно сделать.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "что сейчас открыто", "объясни экран", "что на экране", "что происходит".
        ПАРАМЕТРЫ: Нет параметров.
        ВОЗВРАЩАЕТ: Объяснение: "Открыт WhatsApp, последний чат с Андреем, можно ответить на сообщение."
        ОГРАНИЧЕНИЯ: Требует включённый AccessibilityService.
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (!AgentAccessibilityService.isServiceRunning()) {
            return ToolResult.failure("AccessibilityService не включён. Скажи пользователю включить его в настройках специальных возможностей.")
        }

        return try {
            val content = AgentAccessibilityService.readScreen()
            android.util.Log.d(TAG, "Screen content: ${content.take(100)}...")
            // LLM сам обработает этот результат и даст объяснение
            ToolResult.success(content)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to explain screen: ${e.message}")
            ToolResult.failure("Не удалось прочитать экран: ${e.message}")
        }
    }
}
