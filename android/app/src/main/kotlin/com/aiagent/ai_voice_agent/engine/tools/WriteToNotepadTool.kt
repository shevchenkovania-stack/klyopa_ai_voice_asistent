/**
 * WriteToNotepadTool — создание новой заметки в системном приложении «Заметки».
 *
 * Использует Intent с ACTION_SEND и типом text/plain для передачи текста
 * в приложение заметок (Google Keep, Samsung Notes, системные заметки и т.д.).
 * Если найдено несколько приложений — показывает диалог выбора.
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

/**
 * Инструмент `write_to_notepad` — создать заметку.
 * Открывает приложение заметок и передаёт туда текст через Intent.
 */
class WriteToNotepadTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "WriteToNotepad" }

    override val name = "write_to_notepad"
    override val description = """
        Создать новую заметку в приложении «Заметки».
        ЧТО ДЕЛАЕТ: Открывает системное приложение заметок (Google Keep, Samsung Notes и т.д.) и создаёт новую заметку с текстом.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит «запиши в блокнот», «создай заметку», «напиши в заметки».
        ПАРАМЕТРЫ: text (обязательно) — текст заметки. title (необязательно) — заголовок.
        ВОЗВРАЩАЕТ: "Открываю заметки..." или "Не найдено приложение для заметок".
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "text", description = "Текст заметки", type = "string", required = true),
        ToolParam(name = "title", description = "Заголовок заметки (необязательно)", type = "string", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val text = params["text"] as? String ?: return ToolResult.failure("Не указан текст заметки")
        val title = params["title"] as? String ?: ""

        if (text.isBlank()) return ToolResult.failure("Текст заметки пустой")

        return try {
            // Пробуем найти приложение для заметок
            val notesAppPackage = findNotesApp()
            
            // Если нашли конкретное приложение — пробуем открыть его напрямую через launch intent
            if (notesAppPackage != null) {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(notesAppPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(launchIntent)
                        Log.d(TAG, "Открываю заметки напрямую: $notesAppPackage")
                        // После открытия приложения пытаемся отправить текст через ACTION_SEND
                        // (некоторые приложения могут принять текст сразу)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            if (title.isNotEmpty()) {
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TITLE, title)
                            }
                            setPackage(notesAppPackage)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        // Даём приложению время открыться
                        kotlinx.coroutines.delay(500)
                        try {
                            context.startActivity(sendIntent)
                            Log.d(TAG, "Отправляю текст в $notesAppPackage")
                        } catch (e: Exception) {
                            Log.w(TAG, "Не удалось отправить текст: ${e.message}")
                        }
                        return ToolResult.success("Открываю заметки и создаю новую запись")
                    } catch (e: Exception) {
                        Log.w(TAG, "Не удалось открыть $notesAppPackage напрямую: ${e.message}")
                    }
                }
            }

            // Fallback — используем ACTION_SEND с chooser
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (title.isNotEmpty()) {
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TITLE, title)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(Intent.createChooser(intent, "Создать заметку в").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Log.d(TAG, "Показываю диалог выбора приложения для заметок")
            ToolResult.success("Выбери приложение для создания заметки")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка создания заметки: ${e.message}")
            ToolResult.failure("Не удалось создать заметку: ${e.message}")
        }
    }

    /**
     * Ищет установленное приложение для заметок.
     * Приоритет: Google Keep → Samsung Notes → другие заметки.
     */
    private fun findNotesApp(): String? {
        val pm = context.packageManager
        val candidates = listOf(
            "com.google.android.keep", // Google Keep
            "com.samsung.android.app.notes", // Samsung Notes
            "com.miui.notes", // Mi Notes
            "com.oneplus.note", // OnePlus Notes
            "com.sec.android.app.memo", // Samsung Memo (старые устройства)
            "com.htc.notes" // HTC Notes
        )

        for (pkg in candidates) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    Log.d(TAG, "Найдено приложение заметок: $pkg")
                    return pkg
                }
            } catch (e: Exception) {
                // Игнорируем — пробуем следующее
            }
        }

        // Если не нашли известные — ищем по ключевым словам
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val notesKeywords = listOf("note", "notes", "notepad", "keep", "memo")
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val pkg = app.packageName.lowercase()
            if (notesKeywords.any { label.contains(it) || pkg.contains(it) }) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    Log.d(TAG, "Найдено приложение заметок по ключевому слову: ${app.packageName}")
                    return app.packageName
                }
            }
        }

        Log.d(TAG, "Не найдено приложение заметок")
        return null
    }
}
