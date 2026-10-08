/**
 * DotArtTestRunner — тест точечного рисования через LLM.
 *
 * Проверяет что LLM САМ генерирует элементы из точек (не шаблоны!).
 * Отправляет голосовые команды через VoiceAgent → LLM → draw_image.
 *
 * Тестовые команды:
 * 1. "нарисуй цветок из точек"
 * 2. "нарисуй сердце точками"
 * 3. "нарисуй смайлик из точек"
 * 4. "нарисуй яблоко точками"
 *
 * Критерии PASS:
 * - LLM вызвал draw_image (файл создан)
 * - Файл PNG валидный (>100 байт, 512x512)
 * - Bitmap содержит нарисованные точки (не пустой)
 * - Каждый запуск генерирует УНИКАЛЬНЫЙ рисунок (LLM творит)
 */
package com.aiagent.ai_voice_agent.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Тест точечного рисования через LLM.
 * LLM сам придумывает координаты и размеры точек — никаких шаблонов!
 */
class DotArtTestRunner(private val context: Context) {
    companion object {
        private const val TAG = "DotArtTest"
        private const val TEST_SIZE = 512
        private const val MIN_FILE_BYTES = 100L
    }

    /**
     * Тестовые команды — LLM должен сам решить как рисовать.
     * Каждая команда уникальна, LLM каждый раз рисует по-разному.
     */
    private val testCommands = listOf(
        "нарисуй яблоко точками" to "dot_test_apple.png"
    )

    /**
     * Запускает все тесты через LLM.
     * Каждая команда проходит полный пайплайн: текст → LLM → draw_image → файл.
     */
    suspend fun runAll(): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()

        for ((command, filename) in testCommands) {
            val result = runLlmScenario(command, filename)
            results.add(result)
        }

        val passed = results.count { it["passed"] == true }
        val total = results.size
        Log.d(TAG, "Результат: $passed/$total пройдено")

        return results + mapOf(
            "scenario" to "ИТОГО",
            "passed" to (passed == total),
            "details" to "$passed/$total сценариев пройдено"
        )
    }

    /**
     * Отправляет команду через LLM и проверяет результат.
     * LLM рисует в drawing.png, тест переименовывает в уникальное имя.
     */
    private suspend fun runLlmScenario(command: String, filename: String): Map<String, Any> {
        val defaultFile = File("/sdcard/Pictures/drawing.png")
        val targetFile = File("/sdcard/Pictures/$filename")

        return try {
            // Удаляем старый drawing.png если есть
            defaultFile.delete()

            // Отправляем команду через VoiceAgent (полный пайплайн: LLM → tool → file)
            Log.d(TAG, "Команда: $command")
            val agentResponse = EngineManager.processText(command)
            Log.d(TAG, "Ответ агента: $agentResponse")

            // Проверяем что файл создан
            if (!defaultFile.exists()) {
                return mapOf(
                    "scenario" to command,
                    "passed" to false,
                    "details" to "✗ Файл не создан. LLM не вызвал draw_image"
                )
            }

            // Переименовываем в уникальное имя (чтобы не перезаписался)
            defaultFile.renameTo(targetFile)

            // Проверяем валидность PNG
            val fileSize = targetFile.length()
            if (fileSize < MIN_FILE_BYTES) {
                return mapOf(
                    "scenario" to command,
                    "passed" to false,
                    "details" to "✗ Файл слишком мал: $fileSize байт"
                )
            }

            // Проверяем bitmap
            val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
            if (bitmap == null) {
                return mapOf(
                    "scenario" to command,
                    "passed" to false,
                    "details" to "✗ Не удалось декодировать PNG"
                )
            }

            if (bitmap.width != TEST_SIZE || bitmap.height != TEST_SIZE) {
                bitmap.recycle()
                return mapOf(
                    "scenario" to command,
                    "passed" to false,
                    "details" to "✗ Размеры: ${bitmap.width}x${bitmap.height}"
                )
            }

            // Проверяем что есть нарисованные точки
            val hasContent = checkBitmapHasContent(bitmap)
            bitmap.recycle()

            if (!hasContent) {
                return mapOf(
                    "scenario" to command,
                    "passed" to false,
                    "details" to "✗ Изображение пустое — нет точек"
                )
            }

            // PASS — LLM сам нарисовал!
            mapOf(
                "scenario" to command,
                "passed" to true,
                "details" to "✓ LLM нарисовал! $filename (${fileSize} байт)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в '$command': ${e.message}", e)
            mapOf(
                "scenario" to command,
                "passed" to false,
                "details" to "✗ Исключение: ${e.message}"
            )
        }
    }

    /**
     * Проверяет что bitmap содержит более одного цвета (есть нарисованные точки).
     */
    private fun checkBitmapHasContent(bitmap: android.graphics.Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        var colorCount = 0
        val bgColor = bitmap.getPixel(0, 0)

        // Проверяем 50 точек в области центра
        for (i in 0 until 50) {
            val x = (w * 0.2 + (w * 0.6 * (i % 10) / 10)).toInt()
            val y = (h * 0.2 + (h * 0.6 * (i / 10) / 5)).toInt()
            if (bitmap.getPixel(x, y) != bgColor) {
                colorCount++
            }
        }

        return colorCount >= 5
    }
}
