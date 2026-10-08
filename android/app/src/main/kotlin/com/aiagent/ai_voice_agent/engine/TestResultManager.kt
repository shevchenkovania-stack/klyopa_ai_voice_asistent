package com.aiagent.ai_voice_agent.engine

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Менеджер результатов тестов — сохраняет, загружает, экспортирует.
 * 
 * Архитектура:
 * - Каждый запуск теста создаёт TestSession с уникальным ID
 * - Результаты сохраняются в JSON файл после КАЖДОГО теста (не в конце!)
 * - Если краш — промежуточные результаты не теряются
 * - История хранится в /sdcard/ai_voice_agent/test_results/
 */
class TestResultManager(private val context: Context) {
    companion object {
        private const val TAG = "TestResultManager"
        private const val FILE_PREFIX = "test_session_"
    }
    
    // Используем внутренний каталог приложения — не требует разрешений!
    private val TEST_DIR: String by lazy {
        File(context.filesDir, "test_results").absolutePath
    }
    
    data class TestSession(
        val id: String,
        val timestamp: Long,
        val apkVersion: String,
        val buildNumber: Int,
        val totalTests: Int,
        val passed: Int,
        val failed: Int,
        val skipped: Int,
        val results: List<TestResult>,
        val status: String // "running", "completed", "crashed"
    )
    
    data class TestResult(
        val toolName: String,
        val category: String,
        val passed: Boolean,
        val details: String,
        val timestamp: Long
    )
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    init {
        // Создаём директорию для результатов
        File(TEST_DIR).mkdirs()
    }
    
    /**
     * Создать новую сессию тестирования
     */
    fun createSession(totalTests: Int, apkVersion: String, buildNumber: Int): String {
        Log.d(TAG, "createSession: Создание сессии...")
        
        // Создаём директорию если не существует
        val dir = File(TEST_DIR)
        Log.d(TAG, "createSession: Директория: $TEST_DIR")
        Log.d(TAG, "createSession: Существует: ${dir.exists()}")
        
        if (!dir.exists()) {
            Log.d(TAG, "createSession: Создаём директорию...")
            val created = dir.mkdirs()
            Log.d(TAG, "createSession: Создано: $created")
            Log.d(TAG, "createSession: После создания существует: ${dir.exists()}")
            Log.d(TAG, "createSession: Можно записать: ${dir.canWrite()}")
        }
        
        val sessionId = dateFormat.format(Date())
        Log.d(TAG, "createSession: Session ID: $sessionId")
        val session = TestSession(
            id = sessionId,
            timestamp = System.currentTimeMillis(),
            apkVersion = apkVersion,
            buildNumber = buildNumber,
            totalTests = totalTests,
            passed = 0,
            failed = 0,
            skipped = 0,
            results = emptyList(),
            status = "running"
        )
        saveSession(session)
        Log.d(TAG, "Создана сессия: $sessionId")
        return sessionId
    }
    
    /**
     * Добавить результат теста (сохраняется сразу!)
     */
    fun addTestResult(sessionId: String, result: TestResult) {
        Log.d(TAG, "addTestResult: Загрузка сессии $sessionId...")
        val session = loadSession(sessionId) ?: run {
            Log.e(TAG, "addTestResult: Сессия $sessionId НЕ НАЙДЕНА!")
            return
        }
        Log.d(TAG, "addTestResult: Сессия загружена, текущие результаты: ${session.results.size}")
        
        val updatedResults = session.results + result
        val passed = updatedResults.count { it.passed }
        val failed = updatedResults.count { !it.passed }
        
        Log.d(TAG, "addTestResult: Добавляем ${result.toolName} (passed=${result.passed}), всего: $passed passed, $failed failed")
        
        val updatedSession = session.copy(
            results = updatedResults,
            passed = passed,
            failed = failed
        )
        
        saveSession(updatedSession)
        Log.d(TAG, "addTestResult: ✅ Результат сохранён: ${result.toolName} → ${if (result.passed) "PASS" else "FAIL"}")
    }
    
    /**
     * Завершить сессию
     */
    fun completeSession(sessionId: String) {
        val session = loadSession(sessionId) ?: return
        val updatedSession = session.copy(status = "completed")
        saveSession(updatedSession)
        Log.d(TAG, "Сессия завершена: $sessionId")
    }
    
    /**
     * Пометить сессию как крашнутую (если что-то пошло не так)
     */
    fun markSessionCrashed(sessionId: String, error: String) {
        val session = loadSession(sessionId) ?: return
        val crashResult = TestResult(
            toolName = "CRASH",
            category = "system",
            passed = false,
            details = error,
            timestamp = System.currentTimeMillis()
        )
        val updatedSession = session.copy(
            status = "crashed",
            results = session.results + crashResult
        )
        saveSession(updatedSession)
        Log.e(TAG, "Сессия крашнута: $sessionId - $error")
    }
    
    /**
     * Загрузить все сессии (история)
     */
    fun loadAllSessions(): List<TestSession> {
        Log.d(TAG, "Загрузка истории из: $TEST_DIR")
        val dir = File(TEST_DIR)
        
        // Создаём директорию если не существует (включая родительскую)
        if (!dir.exists()) {
            Log.d(TAG, "Директория не существует, создаём...")
            val created = dir.mkdirs()
            Log.d(TAG, "Директория создана: $created")
            return emptyList()
        }
        
        Log.d(TAG, "Директория существует, читаем файлы...")
        val files = dir.listFiles { file -> file.name.startsWith(FILE_PREFIX) }
        Log.d(TAG, "Найдено файлов: ${files?.size ?: 0}")
        
        return files
            ?.mapNotNull { file ->
                try {
                    Log.d(TAG, "Читаем файл: ${file.name}")
                    val json = file.readText()
                    parseSession(json)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка загрузки ${file.name}: ${e.message}", e)
                    null
                }
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }
    
    /**
     * Загрузить одну сессию по ID
     */
    fun loadSession(sessionId: String): TestSession? {
        val file = File(TEST_DIR, "$FILE_PREFIX$sessionId.json")
        if (!file.exists()) {
            Log.w(TAG, "loadSession: Файл $file НЕ СУЩЕСТВУЕТ")
            return null
        }
        
        return try {
            val json = file.readText()
            Log.d(TAG, "loadSession: Читаем файл ${file.name} (${json.length} символов)")
            val session = parseSession(json)
            Log.d(TAG, "loadSession: Загружена сессия ${session.id}, результатов: ${session.results.size}")
            session
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки сессии $sessionId: ${e.message}")
            null
        }
    }
    
    /**
     * Экспортировать сессию в текстовый формат
     */
    fun exportSession(sessionId: String): String {
        Log.d(TAG, "exportSession: Экспорт сессии $sessionId")
        val session = loadSession(sessionId)
        if (session == null) {
            Log.w(TAG, "exportSession: Сессия $sessionId НЕ НАЙДЕНА")
            return "Сессия не найдена"
        }
        
        Log.d(TAG, "exportSession: Сессия найдена, результатов: ${session.results.size}")
        
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════════")
        sb.appendLine("ОТЧЁТ О ТЕСТИРОВАНИИ КЛЁПЫ")
        sb.appendLine("════════════════════════════════════════")
        sb.appendLine()
        sb.appendLine("Дата: ${displayDateFormat.format(Date(session.timestamp))}")
        sb.appendLine("Версия APK: ${session.apkVersion}")
        sb.appendLine("Номер сборки: ${session.buildNumber}")
        sb.appendLine("Статус: ${session.status}")
        sb.appendLine()
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("РЕЗУЛЬТАТЫ:")
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("Всего тестов: ${session.totalTests}")
        sb.appendLine("✅ Прошло: ${session.passed}")
        sb.appendLine("❌ Провалилось: ${session.failed}")
        sb.appendLine("⏭️ Пропущено: ${session.skipped}")
        sb.appendLine("Процент успеха: ${if (session.totalTests > 0) (session.passed * 100 / session.totalTests) else 0}%")
        sb.appendLine()
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("ДЕТАЛИ:")
        sb.appendLine("────────────────────────────────────────")
        
        session.results.forEachIndexed { index, result ->
            val icon = if (result.passed) "✅" else "❌"
            sb.appendLine()
            sb.appendLine("${index + 1}. $icon ${result.toolName} (${result.category})")
            sb.appendLine("   ${result.details}")
        }
        
        sb.appendLine()
        sb.appendLine("════════════════════════════════════════")
        sb.appendLine("Конец отчёта")
        sb.appendLine("════════════════════════════════════════")
        
        val report = sb.toString()
        Log.d(TAG, "exportSession: ✅ Отчёт сгенерирован (${report.length} символов)")
        return report
    }
    
    /**
     * Удалить старую сессию
     */
    fun deleteSession(sessionId: String): Boolean {
        val file = File(TEST_DIR, "$FILE_PREFIX$sessionId.json")
        return file.delete()
    }
    
    // ─────────────────── Private helpers ───────────────────
    
    private fun saveSession(session: TestSession) {
        val dir = File(TEST_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        val file = File(TEST_DIR, "$FILE_PREFIX${session.id}.json")
        val json = sessionToJson(session)
        file.writeText(json.toString(2))
    }
    
    private fun parseSession(json: String): TestSession {
        val obj = JSONObject(json)
        val resultsArray = obj.getJSONArray("results")
        val results = (0 until resultsArray.length()).map { i ->
            val r = resultsArray.getJSONObject(i)
            TestResult(
                toolName = r.getString("toolName"),
                category = r.getString("category"),
                passed = r.getBoolean("passed"),
                details = r.getString("details"),
                timestamp = r.getLong("timestamp")
            )
        }
        
        return TestSession(
            id = obj.getString("id"),
            timestamp = obj.getLong("timestamp"),
            apkVersion = obj.getString("apkVersion"),
            buildNumber = obj.getInt("buildNumber"),
            totalTests = obj.getInt("totalTests"),
            passed = obj.getInt("passed"),
            failed = obj.getInt("failed"),
            skipped = obj.optInt("skipped", 0),
            results = results,
            status = obj.getString("status")
        )
    }
    
    private fun sessionToJson(session: TestSession): JSONObject {
        val obj = JSONObject()
        obj.put("id", session.id)
        obj.put("timestamp", session.timestamp)
        obj.put("apkVersion", session.apkVersion)
        obj.put("buildNumber", session.buildNumber)
        obj.put("totalTests", session.totalTests)
        obj.put("passed", session.passed)
        obj.put("failed", session.failed)
        obj.put("skipped", session.skipped)
        obj.put("status", session.status)
        
        val resultsArray = JSONArray()
        session.results.forEach { result ->
            val r = JSONObject()
            r.put("toolName", result.toolName)
            r.put("category", result.category)
            r.put("passed", result.passed)
            r.put("details", result.details)
            r.put("timestamp", result.timestamp)
            resultsArray.put(r)
        }
        obj.put("results", resultsArray)
        
        return obj
    }
}
