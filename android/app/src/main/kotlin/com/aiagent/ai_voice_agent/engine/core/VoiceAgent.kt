package com.aiagent.ai_voice_agent.engine.core

import android.content.Context
import android.util.Log
import com.aiagent.ai_voice_agent.engine.IntentDetector
import com.aiagent.ai_voice_agent.engine.IntentMatch
import com.aiagent.ai_voice_agent.engine.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import okio.Buffer
import java.util.concurrent.TimeUnit

/**
 * Tool call from AI
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)

/**
 * Agent response
 */
data class AgentResponse(
    val message: String,
    val toolCalls: List<ToolCall> = emptyList()
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}

/**
 * Core AI agent with function calling (Groq/OpenAI)
 */
/**
 * VoiceAgent — LLM-агент с function calling.
 *
 * Поддерживает два режима:
 * 1. Обычный (domainTools = null): все инструменты, динамический выбор через ToolSelector
 * 2. Доменный (domainTools = setOf(...)): только указанные инструменты, специализация
 *
 * Для multi-agent системы создаётся несколько экземпляров с разными domainTools.
 * Каждый экземпляр хранит свою историю диалога.
 */
class VoiceAgent(
    private val context: Context,
    private val toolRegistry: ToolRegistry,
    private val memory: AgentMemory? = null,
    /** Если задано — агент видит ТОЛЬКО эти инструменты (доменная специализация) */
    private val domainTools: Set<String>? = null,
    /** Суффикс к system prompt для доменной специализации */
    private val domainPromptSuffix: String = ""
) {
    companion object {
        private const val TAG = "VoiceAgent"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        private const val MAX_ITERATIONS = 5
        private const val CONVERSATION_TIMEOUT_MS = 60_000L
        private const val MAX_HISTORY_MESSAGES = 20  // Больше = лучше помнит контекст (было 12)
        private const val MAX_TOOL_RESULT_LENGTH = 200  // Короткие ответы для скорости
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Динамический выбор инструментов
    private val toolSelector = ToolSelector(toolRegistry)

    // Conversation history
    private val conversationHistory = mutableListOf<JSONObject>()
    private var lastInteractionTime = System.currentTimeMillis()

    // Config (will be loaded from SharedPreferences)
    var groqApiKey = ""
    var geminiApiKey = ""
    var openaiApiKey = ""
    var activeProvider = "openai"
    var agentName = "Клёпа"

    var intentDetector: IntentDetector? = null
    var permissionManager: PermissionManager? = null
    var cancellationToken: kotlinx.coroutines.Job? = null

    /**
     * Fast path: try IntentDetector before LLM.
     * Returns response or null if intent not matched.
     */
    suspend fun processWithIntentDetect(userMessage: String): String? {
        val detector = intentDetector ?: return null
        val match = detector.detect(userMessage) ?: return null
        Log.d(TAG, "IntentDetector matched: ${match.name}")
        val result = toolRegistry.executeWithRetry(match.name, match.params ?: emptyMap())
        return if (result.success) "Готово: ${result.message}" else "Ошибка: ${result.message}"
    }

    /**
     * Check PermissionManager before executing a tool.
     * Returns true if allowed, false if denied.
     */
    fun processWithPermissionCheck(toolCall: ToolCall): Boolean {
        val pm = permissionManager ?: return true
        return pm.check(toolCall.name, toolCall.arguments)
    }

    /**
     * Process user message and execute tools if needed
     */
    suspend fun process(userMessage: String, onToken: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Команда: '$userMessage' (история: ${conversationHistory.size} сообщений) ===")

        // Шаг 0: IntentDetector fast path (без LLM)
        processWithIntentDetect(userMessage)?.let { fastResponse ->
            Log.d(TAG, "IntentDetector fast path: $fastResponse")
            return@withContext fastResponse
        }

        // Clear conversation if timed out
        if (conversationHistory.isNotEmpty() &&
            System.currentTimeMillis() - lastInteractionTime > CONVERSATION_TIMEOUT_MS
        ) {
            Log.d(TAG, "Контекст устарел (${System.currentTimeMillis() - lastInteractionTime}мс), очищаю")
            conversationHistory.clear()
        }
        lastInteractionTime = System.currentTimeMillis()

        // Build messages: system + history + new user message
        val messages = JSONArray().apply {
            put(systemPrompt())
            conversationHistory.forEach { put(it) }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        // Add user message to history
        conversationHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        // Выбор инструментов: доменный фильтр или динамический выбор
        val selectedTools = if (domainTools != null) {
            domainTools.mapNotNull { toolRegistry.get(it) }
        } else {
            toolSelector.selectTools(userMessage)
        }
        val toolSchemas = toolsToSchemas(selectedTools)
        Log.d(TAG, "Инструменты: ${selectedTools.size} (домен=${domainTools?.size ?: "все"})")

        // Agent loop
        var iterations = 0
        while (iterations < MAX_ITERATIONS) {
            iterations++
            Log.d(TAG, "Итерация $iterations")

            val response = callAI(messages, toolSchemas, onToken)

            if (!response.hasToolCalls) {
                // No tool calls — return final message
                Log.d(TAG, "Ответ: ${response.message}")
                conversationHistory.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", response.message)
                })
                return@withContext response.message
            }

            // Add assistant message with tool calls
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                if (response.message.isNotEmpty()) put("content", response.message)
                put("tool_calls", JSONArray().apply {
                    response.toolCalls.forEach { tc ->
                        put(JSONObject().apply {
                            put("id", tc.id)
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", tc.name)
                                put("arguments", JSONObject(tc.arguments).toString())
                            })
                        })
                    }
                })
            }
            messages.put(assistantMsg)
            conversationHistory.add(assistantMsg)

            // Execute each tool
            for (toolCall in response.toolCalls) {
                Log.d(TAG, "Вызов: ${toolCall.name}")

                // PermissionManager check before tool execution
                if (!processWithPermissionCheck(toolCall)) {
                    val denyMsg = "Действие '${toolCall.name}' не разрешено"
                    Log.w(TAG, denyMsg)
                    val deniedResult = """{"success":false,"message":"$denyMsg"}"""
                    val toolMsg = JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", toolCall.id)
                        put("content", deniedResult)
                    }
                    messages.put(toolMsg)
                    conversationHistory.add(toolMsg)
                    continue
                }

                val result = toolRegistry.executeWithRetry(toolCall.name, toolCall.arguments)
                val resultStr = result.toJsonString()
                Log.d(TAG, "Результат: ${toolCall.name} = $resultStr")

                // Truncate long tool results to save context window
                val truncatedResult = if (resultStr.length > MAX_TOOL_RESULT_LENGTH) {
                    resultStr.take(MAX_TOOL_RESULT_LENGTH) + "...[обрезано]"
                } else {
                    resultStr
                }

                val toolMsg = JSONObject().apply {
                    put("role", "tool")
                    put("tool_call_id", toolCall.id)
                    put("content", truncatedResult)
                }
                messages.put(toolMsg)
                conversationHistory.add(toolMsg)
            }

            // Trim history if too long
            trimHistory()
        }

        val errorMsg = "Слишком много шагов. Попробуй проще сформулировать."
        conversationHistory.add(JSONObject().apply {
            put("role", "assistant")
            put("content", errorMsg)
        })
        return@withContext errorMsg
    }

    /**
     * Получить историю разговора (для ToolTestRunner).
     */
    fun getConversationHistory(): List<JSONObject> = conversationHistory.toList()

    /**
     * Whether the last response expects user input (is a question)
     */
    val expectsResponse: Boolean
        get() {
            if (conversationHistory.isEmpty()) return false
            val last = conversationHistory.lastOrNull() ?: return false
            if (last.optString("role") != "assistant") return false
            return (last.optString("content") ?: "").contains("?")
        }

    /**
     * Clear conversation history
     */
    fun clearContext() {
        conversationHistory.clear()
    }

    /**
     * Trim conversation history to MAX_HISTORY_MESSAGES (keep most recent).
     * Удаляем парами: assistant с tool_calls + все его tool responses.
     * Иначе LLM получит assistant с tool_calls без tool responses — и вернёт ошибку.
     */
    private fun trimHistory() {
        if (conversationHistory.size <= MAX_HISTORY_MESSAGES) return
        
        // Удаляем парами: assistant + все его tool responses
        while (conversationHistory.size > MAX_HISTORY_MESSAGES) {
            val first = conversationHistory.firstOrNull() ?: break
            
            if (first.optString("role") == "assistant" && first.has("tool_calls")) {
                // Это assistant с tool_calls — удаляем его И все следующие tool responses
                conversationHistory.removeAt(0) // assistant
                // Удаляем все tool responses, пока не встретим не-tool сообщение
                while (conversationHistory.isNotEmpty() && 
                       conversationHistory.first().optString("role") == "tool") {
                    conversationHistory.removeAt(0)
                }
            } else {
                // Обычное сообщение (user/assistant без tools) — удаляем одно
                conversationHistory.removeAt(0)
            }
        }
        Log.d(TAG, "История обрезана до ${conversationHistory.size}")
    }

    // ==================== PRIVATE ====================

    private fun callAI(messages: JSONArray, toolSchemas: JSONArray, onToken: ((String) -> Unit)? = null): AgentResponse {
        // Используем активный провайдер из настроек
        val primaryProvider = when (activeProvider) {
            "gemini" -> "Gemini"
            "groq" -> "Groq"
            else -> "OpenAI"
        }

        // Диагностика: проверяем что API ключи загружены
        val activeKey = when (primaryProvider) {
            "Gemini" -> geminiApiKey
            "Groq" -> groqApiKey
            else -> openaiApiKey
        }
        Log.d(TAG, "callAI: провайдер=$primaryProvider, ключ=${if (activeKey.isEmpty()) "❌ ПУСТОЙ!" else "${activeKey.take(10)}..."}")

        // 3 попытки: первая + retry с задержкой из 429 + fallback
        val maxAttempts = 3
        var lastError: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return callProvider(primaryProvider, messages, toolSchemas, onToken)
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "$primaryProvider ошибка (попытка $attempt/$maxAttempts): [${e.javaClass.simpleName}] ${e.message}")
                
                // Если 429 (rate limit) — сразу fallback, не ждём
                if (e.message?.contains("429") == true) {
                    val fallbackResult = tryFallbackProviders(messages, toolSchemas, skipProvider = primaryProvider, onToken = onToken)
                    if (fallbackResult != null) return fallbackResult
                }
                
                if (attempt < maxAttempts) {
                    try { Thread.sleep(600) } catch (_: InterruptedException) {}
                }
            }
        }
        Log.e(TAG, "$primaryProvider — все попытки провалены: [${lastError?.javaClass?.simpleName}] ${lastError?.message}")
        return AgentResponse(message = "Ошибка соединения. Попробуй позже.")
    }

    /**
     * Пробуем fallback провайдеров по цепочке: Gemini → Groq → OpenAI
     */
    private fun tryFallbackProviders(messages: JSONArray, toolSchemas: JSONArray, skipProvider: String, onToken: ((String) -> Unit)? = null): AgentResponse? {
        val fallbackChain = listOf("Gemini", "Groq", "OpenAI").filter { it != skipProvider }
        for (fb in fallbackChain) {
            val fbKey = when (fb) {
                "Gemini" -> geminiApiKey
                "Groq" -> groqApiKey
                else -> openaiApiKey
            }
            if (fbKey.isEmpty()) continue
            Log.d(TAG, "$skipProvider quota exceeded → fallback на $fb")
            try {
                // Для Groq/Gemini убираем инструменты (могут быть несовместимы)
                val fbTools = if (fb != "OpenAI") JSONArray() else toolSchemas
                return callProvider(fb, messages, fbTools, onToken)
            } catch (fbError: Exception) {
                Log.e(TAG, "$fb fallback ошибка: ${fbError.message}")
            }
        }
        return null
    }

    /**
     * Парсит retryDelay из 429 ответа (Gemini формат: "retryDelay": "25s")
     */
    private fun parseRetryDelay(errorMessage: String?): Int {
        if (errorMessage == null) return 0
        val match = Regex("""retryDelay["\s:]+(\d+)""").find(errorMessage)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun callProvider(provider: String, messages: JSONArray, toolSchemas: JSONArray, onToken: ((String) -> Unit)? = null): AgentResponse {
        val body = JSONObject().apply {
            put("temperature", 0.5)  // Баланс: не слишком случайно, но не робот
            put("max_tokens", 100)   // Голос = уши. 100 токенов ≈ 2-3 предложения максимум
            put("messages", messages)

            if (toolSchemas.length() > 0) {
                put("tools", toolSchemas)
                put("tool_choice", "auto")
            }

            // Streaming: включаем SSE только если есть callback для токенов
            if (onToken != null) {
                put("stream", true)
            }
        }

        val url: String
        val apiKey: String
        val model: String

        when (provider) {
            "Gemini" -> {
                url = GEMINI_URL
                apiKey = geminiApiKey
                model = "gemini-2.0-flash"
                body.put("model", model)
            }
            "Groq" -> {
                url = GROQ_URL
                apiKey = groqApiKey
                model = "llama-3.1-8b-instant"  // Быстрее, выше лимиты (30k TPM)
                body.put("model", model)
            }
            else -> {
                url = OPENAI_URL
                apiKey = openaiApiKey
                model = "gpt-4o-mini"
                body.put("model", model)
            }
        }

        Log.d(TAG, "$provider вызов (stream=${onToken != null}, ${apiKey.take(10)}...)")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw Exception("$provider error ${response.code}: $errorBody")
        }

        // ===== SSE Streaming path =====
        if (onToken != null) {
            return readSseStream(response, provider, onToken)
        }

        // ===== Non-streaming path (backward compatible) =====
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        Log.d(TAG, "$provider ответ получен")
        return parseResponse(responseBody)
    }

    /**
     * Read SSE stream from LLM response.
     * Invokes onToken for each text delta (real-time streaming).
     * If tool_calls detected → switches to accumulation mode (no TTS).
     * Returns assembled AgentResponse.
     */
    private fun readSseStream(response: okhttp3.Response, provider: String, onToken: (String) -> Unit): AgentResponse {
        val source = response.body?.source() ?: throw Exception("Empty streaming response")
        val buffer = Buffer()

        var firstTokenTime = -1L
        val requestStartTime = System.currentTimeMillis()
        var textMode = true
        val textBuffer = StringBuilder()

        // Tool call accumulator (used when tool_calls detected in stream)
        val toolCallAccum = mutableMapOf<Int, MutableMap<String, String>>()

        streamLoop@ while (true) {
            val bytesRead = source.read(buffer, 8192)
            if (bytesRead == -1L) break

            while (true) {
                val line = buffer.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.substring(6).trim()
                if (data == "[DONE]") break@streamLoop

                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue

                    // === TEXT STREAMING MODE ===
                    if (textMode) {
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) {
                            if (firstTokenTime == -1L) {
                                firstTokenTime = System.currentTimeMillis()
                                Log.i(TAG, "[TTFA] first_token: +${firstTokenTime - requestStartTime}ms (provider=$provider)")
                            }
                            textBuffer.append(content)
                            onToken(content)
                        }

                        // Detect tool_calls in stream → switch to accumulation mode
                        val toolCallsDelta = delta.optJSONArray("tool_calls")
                        if (toolCallsDelta != null && toolCallsDelta.length() > 0) {
                            textMode = false
                            Log.d(TAG, "Stream: tool_calls detected → switching to accumulation mode")
                            assembleToolCallDelta(toolCallsDelta, toolCallAccum)
                        }
                    }
                    // === TOOL CALL ACCUMULATION MODE ===
                    else {
                        val toolCallsDelta = delta.optJSONArray("tool_calls")
                        if (toolCallsDelta != null) {
                            assembleToolCallDelta(toolCallsDelta, toolCallAccum)
                        }
                        // Also accumulate any content (some models send both)
                        val content = delta.optString("content", "")
                        if (content.isNotEmpty()) textBuffer.append(content)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SSE parse error: ${e.message}, line: ${line.take(80)}")
                }
            }
        }

        source.close()

        // === Assemble response ===
        if (!textMode && toolCallAccum.isNotEmpty()) {
            // Tool call response
            val toolCalls = toolCallAccum.keys.sorted().map { idx ->
                val parts = toolCallAccum[idx]!!
                ToolCall(
                    id = parts["id"] ?: "call_$idx",
                    name = parts["name"] ?: "unknown",
                    arguments = try {
                        jsonToMap(JSONObject(parts["arguments"] ?: "{}"))
                    } catch (e: Exception) {
                        emptyMap()
                    }
                )
            }
            Log.d(TAG, "$provider streaming: ${toolCalls.size} tool_calls assembled")
            return AgentResponse(message = textBuffer.toString(), toolCalls = toolCalls)
        }

        // Text response
        val fullText = textBuffer.toString()
        val totalMs = System.currentTimeMillis() - requestStartTime
        Log.d(TAG, "$provider streaming done: ${fullText.length} chars, ${totalMs}ms, first_token=${if (firstTokenTime > 0) "${firstTokenTime - requestStartTime}ms" else "N/A"}")
        return AgentResponse(message = fullText)
    }

    /**
     * Accumulate tool_call delta chunks into the accumulator map.
     * Format: index → {id, name, arguments}
     */
    private fun assembleToolCallDelta(
        toolCallsDelta: org.json.JSONArray,
        accum: MutableMap<Int, MutableMap<String, String>>
    ) {
        for (i in 0 until toolCallsDelta.length()) {
            val tc = toolCallsDelta.getJSONObject(i)
            val index = tc.optInt("index", 0)
            val entry = accum.getOrPut(index) { mutableMapOf() }

            tc.optString("id", "").takeIf { it.isNotEmpty() }?.let { entry["id"] = it }

            val function = tc.optJSONObject("function")
            if (function != null) {
                function.optString("name", "").takeIf { it.isNotEmpty() }?.let { entry["name"] = it }
                val args = function.optString("arguments", "")
                if (args.isNotEmpty()) {
                    entry["arguments"] = (entry["arguments"] ?: "") + args
                }
            }
        }
    }

    private fun parseResponse(responseBody: String): AgentResponse {
        val json = JSONObject(responseBody)
        val choice = json.getJSONArray("choices").getJSONObject(0)
        val message = choice.getJSONObject("message")
        var content = message.optString("content", "")
        val toolCallsRaw = message.optJSONArray("tool_calls")

        // Sanitize any XML/JSON-style function calls leaked into content by LLM
        content = content.replace(Regex("""<function[^>]*>.*?</function>""", RegexOption.DOT_MATCHES_ALL), "")
        content = content.replace(Regex("""<function_call[^>]*>.*?</function_call>""", RegexOption.DOT_MATCHES_ALL), "")
        content = content.replace(Regex("""\{"name"\s*:\s*"[^"]+"[^}]*\}"""), "")
        content = content.replace(Regex("""<tool_call[^>]*>.*?""", RegexOption.DOT_MATCHES_ALL), "")
        content = content.trim()

        if (toolCallsRaw == null || toolCallsRaw.length() == 0) {
            return AgentResponse(message = content)
        }

        val toolCalls = mutableListOf<ToolCall>()
        for (i in 0 until toolCallsRaw.length()) {
            val tc = toolCallsRaw.getJSONObject(i)
            val func = tc.getJSONObject("function")
            val argsStr = func.optString("arguments", "{}")
            val args = try {
                jsonToMap(JSONObject(argsStr))
            } catch (e: Exception) {
                emptyMap()
            }

            toolCalls.add(ToolCall(
                id = tc.getString("id"),
                name = func.getString("name"),
                arguments = args
            ))
        }

        return AgentResponse(message = content, toolCalls = toolCalls)
    }

    private fun systemPrompt(): JSONObject {
        val memoryFacts = memory?.recall() ?: emptyList()
        val recentContext = memory?.recallContext() ?: emptyList()
        val prompt = AgentCharacter.systemPrompt(agentName, memoryFacts, recentContext, domainPromptSuffix)

        return JSONObject().apply {
            put("role", "system")
            put("content", prompt)
        }
    }

    /**
     * Конвертировать список инструментов в JSON schemas
     */
    private fun toolsToSchemas(tools: List<AgentTool>): JSONArray {
        return JSONArray().apply {
            tools.forEach { put(it.toFunctionSchema()) }
        }
    }

    /**
     * Convert JSONObject to Map<String, Any?>
     */
    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> {
                    val list = mutableListOf<Any?>()
                    for (i in 0 until value.length()) {
                        val item = value.get(i)
                        list.add(when (item) {
                            is JSONObject -> jsonToMap(item)
                            is JSONArray -> {
                                val nested = mutableListOf<Any?>()
                                for (j in 0 until item.length()) {
                                    val nestedItem = item.get(j)
                                    nested.add(when (nestedItem) {
                                        is JSONObject -> jsonToMap(nestedItem)
                                        else -> nestedItem
                                    })
                                }
                                nested
                            }
                            else -> item
                        })
                    }
                    list
                }
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }
}
