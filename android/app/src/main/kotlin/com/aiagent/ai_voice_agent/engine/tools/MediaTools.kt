package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Управление медиа: play/pause/next/prev/volume */
class MediaControlTool(private val context: Context) : AgentTool {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override val name = "media_control"
    override val description = "Управлять воспроизведением музыки: play, pause, next, previous, volume_up, volume_down."
    override val parameters = listOf(
        ToolParam("action", "string", "Действие: play, pause, next, previous, volume_up, volume_down", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"] as? String
            ?: return ToolResult.failure("Не указано действие")

        return try {
            when (action) {
                "play" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                "pause" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                "next" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                "previous" -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                "volume_up" -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                )
                "volume_down" -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                )
                else -> return ToolResult.failure("Неизвестное действие: $action")
            }

            val actionNames = mapOf(
                "play" to "Воспроизведение",
                "pause" to "Пауза",
                "next" to "Следующий трек",
                "previous" to "Предыдущий трек",
                "volume_up" to "Громкость увеличена",
                "volume_down" to "Громкость уменьшена"
            )
            ToolResult.success(actionNames[action] ?: "Действие \"$action\" выполнено")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка управления медиа: ${e.message}")
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }
}

/** Поиск приложений в Google Play (DuckDuckGo) */
class PlayStoreSearchTool : AgentTool {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override val name = "play_store_search"
    override val description = "Поиск приложений в Google Play Store. Используй когда нужно найти и установить приложение."
    override val parameters = listOf(
        ToolParam("query", "string", "Поисковый запрос (например, \"заметки\", \"калькулятор\")", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val query = params["query"] as? String
        if (query.isNullOrBlank()) return ToolResult.failure("Не указан поисковый запрос")

        return try {
            val searchQuery = "site:play.google.com $query приложение"
            val url = "https://api.duckduckgo.com/?q=${Uri.encode(searchQuery)}&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return ToolResult.failure("Пустой ответ")

            if (!response.isSuccessful) return ToolResult.failure("Ошибка поиска")

            val json = JSONObject(body)
            val results = mutableListOf<String>()

            json.optString("AbstractText").takeIf { it.isNotBlank() }?.let {
                results.add("Ответ: $it")
            }

            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null) {
                for (i in 0 until minOf(relatedTopics.length(), 5)) {
                    val topic = relatedTopics.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    val firstUrl = topic.optString("FirstURL", "")
                    if (text.isNotBlank() && firstUrl.contains("play.google.com")) {
                        results.add("• $text\n  $firstUrl")
                    }
                }
            }

            if (results.isEmpty()) {
                ToolResult.success("Не найдено приложений по запросу \"$query\". Попробуй другой запрос или открой Google Play вручную.")
            } else {
                ToolResult.success("Найдено в Google Play:\n${results.joinToString("\n\n")}")
            }
        } catch (e: Exception) {
            ToolResult.failure("Ошибка поиска: ${e.message}")
        }
    }
}

/** Открыть страницу приложения в Google Play */
class OpenPlayStoreTool(private val context: Context) : AgentTool {
    override val name = "open_play_store"
    override val description = "Открыть страницу приложения в Google Play Store для установки. Используй после поиска приложения."
    override val parameters = listOf(
        ToolParam("package_name", "string", "Package name приложения (например, com.google.android.keep)", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val packageName = params["package_name"] as? String
        if (packageName.isNullOrBlank()) return ToolResult.failure("Не указан package name приложения")

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Открыта страница в Google Play для установки: $packageName")
        } catch (e: Exception) {
            // Fallback to browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.success("Открыта страница в Google Play (через браузер): $packageName")
            } catch (e2: Exception) {
                ToolResult.failure("Не удалось открыть Play Store: ${e2.message}")
            }
        }
    }
}
