package com.aiagent.ai_voice_agent.engine.core

import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Registry for all available agent tools
 */
class ToolRegistry {
    companion object {
        private const val TAG = "ToolRegistry"
        private const val RETRY_DELAY_MS = 800L
    }

    private val tools = mutableMapOf<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun get(name: String): AgentTool? = tools[name]

    fun has(name: String): Boolean = tools.containsKey(name)

    val names: List<String> get() = tools.keys.toList()

    val all: List<AgentTool> get() = tools.values.toList()

    /**
     * Get all tools as OpenAI function schemas array
     */
    fun toFunctionSchemas(): JSONArray {
        return JSONArray().apply {
            tools.values.forEach { put(it.toFunctionSchema()) }
        }
    }

    /**
     * Execute a tool by name
     */
    suspend fun execute(name: String, params: Map<String, Any?>): ToolResult {
        val tool = tools[name] ?: return ToolResult.failure("Инструмент \"$name\" не найден")
        return try {
            tool.execute(params)
        } catch (e: Exception) {
            ToolResult.failure("Ошибка выполнения \"$name\": ${e.message}")
        }
    }

    /**
     * Execute with retry — tries up to maxRetries times before giving up.
     * Waits RETRY_DELAY_MS between attempts (as if "thinking").
     * The result (success or final failure) goes back to the LLM,
     * which decides how to present it to the user.
     */
    suspend fun executeWithRetry(
        name: String,
        params: Map<String, Any?>,
        maxRetries: Int = 2
    ): ToolResult {
        var lastError: ToolResult? = null
        repeat(maxRetries) { attempt ->
            val result = execute(name, params)
            if (result.success) return result
            lastError = result
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "$name: попытка ${attempt + 1} не удалась, пробую ещё раз...")
                delay(RETRY_DELAY_MS)
            }
        }
        Log.d(TAG, "$name: все $maxRetries попыток не удались")
        return lastError!!
    }
}
