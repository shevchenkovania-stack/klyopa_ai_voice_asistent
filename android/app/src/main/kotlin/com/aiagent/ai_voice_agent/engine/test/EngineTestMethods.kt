package com.aiagent.ai_voice_agent.engine.test

import android.content.Context
import com.aiagent.ai_voice_agent.engine.EngineManager
import com.aiagent.ai_voice_agent.engine.core.agent.MultiAgentOrchestrator
import com.aiagent.ai_voice_agent.engine.session.AssistantSessionManager
import com.aiagent.ai_voice_agent.engine.ToolTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Тестовые методы для проверки инструментов.
 * Вынесены из EngineManager для уменьшения размера.
 */
class EngineTestMethods(
    private val context: Context,
    private val agent: MultiAgentOrchestrator
) {
    companion object {
        private const val TAG = "EngineTestMethods"
    }

    fun runReminderTest(): Map<String, String> {
        val triggerAt = System.currentTimeMillis() + 60_000L
        val id = com.aiagent.ai_voice_agent.engine.ReminderScheduler.schedule(context, "Тестовое напоминание — всё работает!", triggerAt)
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(triggerAt)
        android.util.Log.d(TAG, "Reminder test: #$id сработает в $timeStr")
        return mapOf("status" to "scheduled", "reminder_id" to id.toString(), "trigger_at" to timeStr, "message" to "Через 1 минуту должно прийти push-уведомление")
    }

    suspend fun runReminderVoiceTest(): Map<String, String> = withContext(Dispatchers.IO) {
        val testText = "напомни через минуту выключить чайник"
        try {
            val response = agent.process(testText)
            val success = response.contains("напоминан", ignoreCase = true) || response.contains("поставлен", ignoreCase = true)
            val reminderScheduled = response.contains("поставлено", ignoreCase = true) || response.contains("напомн", ignoreCase = true)
            mapOf("status" to if (success) "done" else "uncertain", "test_text" to testText, "agent_response" to response, "reminder_appears_set" to reminderScheduled.toString())
        } catch (e: Exception) {
            mapOf("status" to "error", "error" to (e.message ?: "Unknown"))
        }
    }

    suspend fun runReminderAutoTest(): Map<String, String> = withContext(Dispatchers.IO) {
        val triggerAt = System.currentTimeMillis() + 10_000L
        val id = com.aiagent.ai_voice_agent.engine.ReminderScheduler.schedule(context, "Автотест напоминания", triggerAt)
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(triggerAt)
        delay(15_000L)
        mapOf("status" to "done", "reminder_id" to id.toString(), "scheduled_at" to timeStr, "wait_seconds" to "15")
    }

    suspend fun runNavigationVoiceTest(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = agent.process("найди заправку поблизости")
            val success = response.contains("заправк", ignoreCase = true) || response.contains("maps", ignoreCase = true)
            mapOf("status" to if (success) "done" else "uncertain", "agent_response" to response)
        } catch (e: Exception) { mapOf("status" to "error", "error" to (e.message ?: "Unknown")) }
    }

    suspend fun runRouteVoiceTest(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = agent.process("построй маршрут до Кишинёв центр")
            val success = response.contains("маршрут", ignoreCase = true) || response.contains("навигатор", ignoreCase = true)
            mapOf("status" to if (success) "done" else "uncertain", "agent_response" to response)
        } catch (e: Exception) { mapOf("status" to "error", "error" to (e.message ?: "Unknown")) }
    }

    suspend fun runReplySmsVoiceTest(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = agent.process("ответь на смс привет")
            val success = response.contains("ответ", ignoreCase = true) || response.contains("смс", ignoreCase = true)
            mapOf("status" to if (success) "done" else "uncertain", "agent_response" to response)
        } catch (e: Exception) { mapOf("status" to "error", "error" to (e.message ?: "Unknown")) }
    }

    suspend fun runScreenshotVoiceTest(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = agent.process("сделай скриншот экрана")
            val success = response.contains("скриншот", ignoreCase = true) || response.contains("экран", ignoreCase = true)
            mapOf("status" to if (success) "done" else "uncertain", "agent_response" to response)
        } catch (e: Exception) { mapOf("status" to "error", "error" to (e.message ?: "Unknown")) }
    }

    suspend fun runExplainScreenVoiceTest(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = agent.process("объясни что сейчас открыто на экране")
            val success = response.contains("открыт", ignoreCase = true) || response.contains("экран", ignoreCase = true)
            mapOf("status" to if (success) "done" else "uncertain", "agent_response" to response)
        } catch (e: Exception) { mapOf("status" to "error", "error" to (e.message ?: "Unknown")) }
    }

    suspend fun runShareLocationVoiceTest(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = agent.process("отправь мои координаты жене")
            val success = response.contains("координат", ignoreCase = true) || response.contains("локац", ignoreCase = true)
            mapOf("status" to if (success) "done" else "uncertain", "agent_response" to response)
        } catch (e: Exception) { mapOf("status" to "error", "error" to (e.message ?: "Unknown")) }
    }

    suspend fun runMusicTest(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val response = agent.process("привет клёпа найди на телефоне Eminem Lose Yourself")
            mapOf("status" to "done", "agent_response" to response)
        } catch (e: Exception) { mapOf("status" to "error", "error" to (e.message ?: "Unknown")) }
    }

    suspend fun runAllToolTests(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val runner = ToolTestRunner(context)
        runner.runAll().map { r -> mapOf("toolName" to r.toolName, "userMessage" to r.userMessage, "passed" to r.passed, "llmCalledTool" to (r.llmCalledTool ?: ""), "llmResponse" to r.llmResponse, "paramCheck" to r.paramCheck, "executionResult" to r.executionResult, "details" to r.details) }
    }

    fun loadTestHistory(): String {
        return try { ToolTestRunner(context).loadTestHistory() } catch (e: Exception) { "[]" }
    }

    fun exportTestSession(sessionId: String): String = ToolTestRunner(context).exportSession(sessionId)
    fun deleteTestSession(sessionId: String): Boolean = ToolTestRunner(context).deleteSession(sessionId)

    suspend fun runToolTestsByCategory(category: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        ToolTestRunner(context).runByCategory(category).map { r -> mapOf("toolName" to r.toolName, "userMessage" to r.userMessage, "passed" to r.passed, "llmCalledTool" to (r.llmCalledTool ?: ""), "llmResponse" to r.llmResponse, "paramCheck" to r.paramCheck, "executionResult" to r.executionResult, "details" to r.details) }
    }

    suspend fun runPipelineTest(): Map<String, Any> = withContext(Dispatchers.IO) {
        val stateLog = mutableListOf<String>()
        var sttText = ""
        var agentResponse = ""
        var ttsTriggered = false
        var error: String? = null

        try {
            // 1. Копируем WAV из assets в cache
            val wavFile = File(context.cacheDir, "test_pipeline.wav")
            context.assets.open("test_hello.wav").use { input ->
                FileOutputStream(wavFile).use { output -> input.copyTo(output) }
            }

            // 2. Подписываемся на переходы state machine
            AssistantSessionManager.onStateChanged = { _, newState ->
                stateLog.add(newState.name)
                android.util.Log.i(TAG, "[PipelineTest] State → ${newState.name}")
            }

            // 3. Перехватываем TTS
            val origOnTtsStarted = EngineManager.pipeline.onTtsStarted
            EngineManager.pipeline.onTtsStarted = {
                ttsTriggered = true
                android.util.Log.i(TAG, "[PipelineTest] TTS started")
            }

            // 4. Перехватываем результат pipeline
            val origOnContinuousResult = EngineManager.pipeline.onContinuousResult
            EngineManager.pipeline.onContinuousResult = { text, response ->
                sttText = text
                agentResponse = response
                android.util.Log.i(TAG, "[PipelineTest] STT='$text' Agent='$response'")
            }

            // 5. Сбрасываем state machine → IDLE, затем → LISTENING
            AssistantSessionManager.startInIdle()
            AssistantSessionManager.handleEvent(AssistantSessionManager.Event.WAKE_WORD_DETECTED)

            // 6. Запускаем pipeline
            android.util.Log.i(TAG, "[PipelineTest] Starting pipeline with WAV (${wavFile.length()} bytes)")
            EngineManager.pipeline.processContinuousSpeech(wavFile, onResumeWakeWord = {})

            // Даём время на завершение
            delay(2000)

            // 7. Cleanup
            EngineManager.pipeline.onTtsStarted = origOnTtsStarted
            EngineManager.pipeline.onContinuousResult = origOnContinuousResult
            AssistantSessionManager.onStateChanged = null
            wavFile.delete()

        } catch (e: Exception) {
            error = "${e.javaClass.simpleName}: ${e.message}"
            android.util.Log.e(TAG, "[PipelineTest] Error: $error", e)
        }

        mapOf(
            "states" to stateLog.joinToString(" → "),
            "state_count" to stateLog.size.toString(),
            "stt_text" to sttText,
            "agent_response" to agentResponse,
            "tts_triggered" to ttsTriggered.toString(),
            "error" to (error ?: "none"),
            "final_state" to AssistantSessionManager.currentState.name
        )
    }

    /**
     * Сценарный тест: полный цикл разговора (GREETING → MEMORY → TOOL → FAREWELL).
     * Загружает conversation_flow.json из assets и проходит по уровням.
     */
    suspend fun runConversationTest(): Map<String, Any> {
        val flowTest = ConversationFlowTest(context, agent)
        return flowTest.runScenario()
    }
}
