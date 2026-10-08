package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import com.aiagent.ai_voice_agent.services.CameraHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Take a selfie — front camera with timer, actually captures the photo.
 */
class TakeSelfieTool(private val context: Context) : AgentTool {
    override val name = "take_selfie"
    override val description = """
        Сделать селфи-фото через фронтальную камеру.
        ЧТО ДЕЛАЕТ: Открывает фронтальную камеру, делает снимок без превью, сохраняет JPEG в галерею.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "сделай селфи", "selfie", "фото себя".
        ПАРАМЕТРЫ: 
          - timer (number, опц.) — таймер в секундах перед съёмкой (по умолчанию 3)
          - save_path (string, опц.) — куда сохранить (по умолчанию /sdcard/Pictures/ — галерея)
        ВОЗВРАЩАЕТ: "✓ Селфи сделано! Сохранено: /Pictures/IMG_20240627_123456.jpg (245KB)"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("timer", "number", "Таймер в секундах перед съёмкой (по умолчанию 3)", required = false),
        ToolParam("save_path", "string", "Папка для сохранения (по умолчанию /sdcard/Pictures/ — обычная галерея)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на камеру. Разрешите в настройках телефона.")
        }

        val timer = (params["timer"] as? Number)?.toInt() ?: 3
        val savePath = params["save_path"] as? String

        return withContext(Dispatchers.IO) {
            val camera = CameraHelper(context)
            val file = camera.takePhoto(
                useFrontCamera = true,
                timerSeconds = timer.coerceIn(0, 10),
                savePath = savePath,
                onCountdown = { tick ->
                    android.util.Log.d("TakeSelfie", "Countdown: $tick")
                }
            )

            if (file != null && file.exists() && file.length() > 0) {
                val sizeKb = file.length() / 1024
                val relativePath = file.absolutePath.replace("/sdcard/", "/")
                ToolResult.success(
                    "✓ Селфи сделано! Сохранено: $relativePath (${sizeKb}KB)",
                    data = mapOf("path" to file.absolutePath, "size_kb" to sizeKb)
                )
            } else {
                ToolResult.failure("Не удалось сделать фото. Камера занята или недоступна.")
            }
        }
    }
}

/**
 * Take a photo with back camera.
 */
class TakePhotoTool(private val context: Context) : AgentTool {
    override val name = "take_photo"
    override val description = """
        Сделать фото через основную (заднюю) камеру.
        ЧТО ДЕЛАЕТ: Открывает заднюю камеру, делает снимок без превью, сохраняет JPEG в галерею.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "сделай фото", "фото через камеру", "сними на камеру".
        ПАРАМЕТРЫ: 
          - timer (number, опц.) — таймер в секундах перед съёмкой (по умолчанию 0)
          - save_path (string, опц.) — куда сохранить (по умолчанию /sdcard/Pictures/ — галерея)
        ВОЗВРАЩАЕТ: "✓ Фото сделано! Сохранено: /Pictures/IMG_20240627_123456.jpg (320KB)"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("timer", "number", "Таймер в секундах перед съёмкой (по умолчанию 0)", required = false),
        ToolParam("save_path", "string", "Папка для сохранения (по умолчанию /sdcard/Pictures/ — обычная галерея)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на камеру. Разрешите в настройках телефона.")
        }

        val timer = (params["timer"] as? Number)?.toInt() ?: 0
        val savePath = params["save_path"] as? String

        return withContext(Dispatchers.IO) {
            val camera = CameraHelper(context)
            val file = camera.takePhoto(
                useFrontCamera = false,
                timerSeconds = timer.coerceIn(0, 10),
                savePath = savePath
            )

            if (file != null && file.exists() && file.length() > 0) {
                val sizeKb = file.length() / 1024
                val relativePath = file.absolutePath.replace("/sdcard/", "/")
                ToolResult.success(
                    "✓ Фото сделано! Сохранено: $relativePath (${sizeKb}KB)",
                    data = mapOf("path" to file.absolutePath, "size_kb" to sizeKb)
                )
            } else {
                ToolResult.failure("Не удалось сделать фото. Камера занята или недоступна.")
            }
        }
    }
}
