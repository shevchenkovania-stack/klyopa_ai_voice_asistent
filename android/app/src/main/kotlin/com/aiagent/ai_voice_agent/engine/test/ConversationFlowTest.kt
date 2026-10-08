package com.aiagent.ai_voice_agent.engine.test

import android.content.Context
import android.util.Log
import com.aiagent.ai_voice_agent.engine.EngineManager
import com.aiagent.ai_voice_agent.engine.core.agent.MultiAgentOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Conversation Flow Test — сценарный тестовый полигон.
 *
 * Загружает JSON-сценарий из assets и проходит по уровням:
 * LEVEL 1: GREETING → LEVEL 2: IDENTITY → LEVEL 3: MEMORY →
 * LEVEL 4: TOOL → LEVEL 5: CORRECTION → LEVEL 6: FAREWELL
 *
 * Каждый уровень:
 * - Отправляет текст пользователя в агент
 * - Проверяет ответ (response_contains)
 * - Проверяет память (memory_checks)
 * - Записывает PASS/FAIL + тайминги
 */
class ConversationFlowTest(
    private val context: Context,
    private val agent: MultiAgentOrchestrator
) {
    companion object {
        private const val TAG = "ConversationFlowTest"
    }

    data class StepResult(
        val id: String,
        val level: Int,
        val name: String,
        val userText: String,
        val agentResponse: String,
        val passed: Boolean,
        val checksPassed: List<String>,
        val checksFailed: List<String>,
        val latencyMs: Long
    )

    /**
     * Запустить полный сценарный тест.
     * @return отчёт в виде Map
     */
    suspend fun runScenario(scenarioFile: String = "conversation_flow.json"): Map<String, Any> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StepResult>()
        var allPassed = true

        try {
            // 1. Загружаем сценарий из assets
            val scenarioJson = context.assets.open(scenarioFile).bufferedReader().use { it.readText() }
            val scenario = JSONObject(scenarioJson)
            val steps = scenario.getJSONArray("steps")

            Log.i(TAG, "═══════════════════════════════════════")
            Log.i(TAG, "SCENARIO: ${scenario.getString("name")}")
            Log.i(TAG, "Steps: ${steps.length()}")
            Log.i(TAG, "═══════════════════════════════════════")

            // 2. Проходим по каждому уровню
            for (i in 0 until steps.length()) {
                // Задержка между запросами — Gemini free tier = 5 RPM (1 запрос / 12 сек)
                if (i > 0) {
                    Log.d(TAG, "⏳ Задержка 13 сек (rate limit 5 RPM)...")
                    delay(13_000)
                }
                val step = steps.getJSONObject(i)
                val stepResult = runStep(step)
                results.add(stepResult)

                if (!stepResult.passed) {
                    allPassed = false
                    Log.w(TAG, "✗ LEVEL ${stepResult.level} [${stepResult.name}] FAILED")
                } else {
                    Log.i(TAG, "✓ LEVEL ${stepResult.level} [${stepResult.name}] PASSED (${stepResult.latencyMs}ms)")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Scenario error: ${e.message}", e)
            return@withContext mapOf(
                "status" to "error",
                "error" to (e.message ?: "Unknown"),
                "results" to "[]"
            )
        }

        // 3. Формируем итоговый отчёт
        buildReport(scenarioFile, results, allPassed)
    }

    private suspend fun runStep(step: JSONObject): StepResult {
        val id = step.getString("id")
        val level = step.getInt("level")
        val name = step.getString("name")
        val userText = step.getString("user")
        val checks = step.getJSONObject("checks")
        val description = step.optString("description", "")

        Log.i(TAG, "───────────────────────────────────────")
        Log.i(TAG, "LEVEL $level: $name")
        Log.i(TAG, "User: \"$userText\"")

        val startTime = System.currentTimeMillis()

        // Отправляем в агент
        val agentResponse = try {
            agent.process(userText)
        } catch (e: Exception) {
            Log.e(TAG, "Agent error: ${e.message}")
            "ERROR: ${e.message}"
        }

        // Даём 2 сек на async memory extraction (extractFact в фоне)
        delay(2000)

        val latency = System.currentTimeMillis() - startTime
        Log.i(TAG, "Agent: \"$agentResponse\" (${latency}ms)")

        // Проверяем результат
        val checksPassed = mutableListOf<String>()
        val checksFailed = mutableListOf<String>()

        // Проверяем response_not_empty
        val responseNotEmpty = checks.optBoolean("response_not_empty", false)
        if (responseNotEmpty) {
            if (agentResponse.isNotBlank()) {
                checksPassed.add("response_not_empty")
            } else {
                checksFailed.add("response_not_empty — EMPTY RESPONSE")
            }
        }

        // Проверяем response_contains

        val responseContains = checks.optJSONArray("response_contains")
        if (responseContains != null && responseContains.length() > 0) {
            val keywords = (0 until responseContains.length()).map { responseContains.getString(it) }
            val matched = keywords.any { keyword ->
                agentResponse.contains(keyword, ignoreCase = true)
            }
            if (matched) {
                checksPassed.add("response_contains: [${keywords.joinToString(", ")}]")
            } else {
                checksFailed.add("response_contains: [${keywords.joinToString(", ")}] — NOT FOUND")
            }
        }

        // Проверяем memory_checks
        val memoryChecks = checks.optJSONArray("memory_checks")
        if (memoryChecks != null) {
            for (j in 0 until memoryChecks.length()) {
                val mc = memoryChecks.getJSONObject(j)
                val key = mc.getString("key")
                val action = mc.getString("action")

                when (action) {
                    "recall" -> {
                        val facts = EngineManager.memory.recall()
                        val found = facts.any { it.contains(key, ignoreCase = true) }
                        if (found) {
                            checksPassed.add("memory.recall($key)")
                        } else {
                            checksFailed.add("memory.recall($key) — NOT FOUND")
                        }
                    }
                    "save" -> {
                        // После agent.process() память уже должна быть обновлена
                        val facts = EngineManager.memory.recall()
                        val value = mc.optString("value", "")
                        val found = facts.any { it.contains(value, ignoreCase = true) }
                        if (found) {
                            checksPassed.add("memory.save($key=$value)")
                        } else {
                            checksFailed.add("memory.save($key=$value) — NOT SAVED")
                        }
                    }
                }
            }
        }

        val passed = checksFailed.isEmpty() && agentResponse.isNotBlank() && !agentResponse.startsWith("ERROR")

        return StepResult(
            id = id,
            level = level,
            name = name,
            userText = userText,
            agentResponse = agentResponse,
            passed = passed,
            checksPassed = checksPassed,
            checksFailed = checksFailed,
            latencyMs = latency
        )
    }

    private fun buildReport(scenarioFile: String, results: List<StepResult>, allPassed: Boolean): Map<String, Any> {
        val totalLatency = results.sumOf { it.latencyMs }
        val passedCount = results.count { it.passed }
        val failedCount = results.count { !it.passed }

        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "RESULT: ${if (allPassed) "✓ ALL PASSED" else "✗ SOME FAILED"}")
        Log.i(TAG, "Passed: $passedCount / ${results.size}")
        Log.i(TAG, "Total latency: ${totalLatency}ms")
        Log.i(TAG, "═══════════════════════════════════════")

        // Формируем отчёт для Flutter
        val stepsReport = results.map { r ->
            mapOf(
                "level" to r.level,
                "name" to r.name,
                "user" to r.userText,
                "agent" to (if (r.agentResponse.length > 100) r.agentResponse.take(100) + "..." else r.agentResponse),
                "passed" to r.passed,
                "checks_passed" to r.checksPassed.joinToString("; "),
                "checks_failed" to r.checksFailed.joinToString("; "),
                "latency_ms" to r.latencyMs.toString()
            )
        }

        // Карта прохождения — визуальный лог разговора
        val flowMap = results.joinToString("\n") { r ->
            val icon = if (r.passed) "✓" else "✗"
            val resp = if (r.agentResponse.length > 60) r.agentResponse.take(60) + "..." else r.agentResponse
            "$icon L${r.level}: ${r.name} (${r.latencyMs}ms)\n  User: ${r.userText}\n  Agent: $resp"
        }

        return mapOf(
            "status" to if (allPassed) "PASS" else "FAIL",
            "scenario" to scenarioFile,
            "passed" to passedCount.toString(),
            "failed" to failedCount.toString(),
            "total" to results.size.toString(),
            "total_latency_ms" to totalLatency.toString(),
            "flow_map" to flowMap,
            "steps" to stepsReport
        )
    }
}
