/**
 * YouTubeTools — инструменты для работы с YouTube.
 *
 * Файл содержит 1 инструмент:
 * - `play_youtube` — найти и открыть видео на YouTube
 *
 * Использует DuckDuckGo для поиска YouTube ссылок.
 * Открывает видео через Intent (YouTube приложение или браузер).
 *
 * Зависимости: OkHttp, Jsoup
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Инструмент `play_youtube` — найти и открыть видео на YouTube.
 * Ищет видео по названию и открывает в YouTube приложении или браузере.
 */
class PlayYouTubeTool(private val context: Context) : AgentTool {
    companion object {
        private const val TAG = "PlayYouTube"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override val name = "play_youtube"
    override val description = """
        Найти и открыть видео на YouTube.
        ЧТО ДЕЛАЕТ: Ищет видео по названию/исполнителю и открывает в YouTube приложении или браузере.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "включи [песню] на ютубе", "найди клип", "поставь видео",
        "открой на ютубе", "youtube [название]".
        ПАРАМЕТРЫ: query (обязательно) — название видео или исполнитель (например "Lose Yourself Eminem", "Imagine Dragons Believer").
        ВОЗВРАЩАЕТ: "Открываю YouTube: [название видео]"
        ПРИМЕР: play_youtube(query="Lose Yourself Eminem") → откроет видео в YouTube
        
        ВАЖНО: Этот инструмент ЛУЧШЕ чем web_search + launch_url для YouTube!
        Используй его когда пользователь хочет посмотреть видео/клип.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(
            name = "query",
            type = "string",
            description = "Что искать на YouTube (название песни, исполнитель, или и то и другое). Пример: 'Lose Yourself Eminem', 'Believer Imagine Dragons'",
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val query = params["query"] as? String
        if (query.isNullOrBlank()) {
            return ToolResult.failure("Не указан запрос для поиска на YouTube")
        }

        android.util.Log.d(TAG, "Ищу на YouTube: $query")

        return try {
            // Step 1: Search for YouTube video via DuckDuckGo
            val searchQuery = "site:youtube.com $query"
            val url = "https://html.duckduckgo.com/html/?q=${URLEncoder.encode(searchQuery, "UTF-8")}"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return ToolResult.failure("Пустой ответ от поиска")

            // Step 2: Extract YouTube URL from search results
            val youtubeUrl = extractYouTubeUrl(html)
            
            if (youtubeUrl == null) {
                // Fallback: construct direct YouTube search URL
                val fallbackUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"
                openYouTube(fallbackUrl)
                return ToolResult.success("Открываю поиск YouTube: $query")
            }

            android.util.Log.d(TAG, "Нашёл YouTube URL: $youtubeUrl")

            // Step 3: Open in YouTube app or browser
            openYouTube(youtubeUrl)
            
            ToolResult.success("Открываю YouTube: $query")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Ошибка: ${e.message}", e)
            // Fallback: open YouTube search
            val fallbackUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}"
            try {
                openYouTube(fallbackUrl)
                ToolResult.success("Открываю поиск YouTube: $query")
            } catch (e2: Exception) {
                ToolResult.failure("Не удалось открыть YouTube: ${e2.message}")
            }
        }
    }

    /**
     * Извлекает первую YouTube ссылку из HTML результатов DuckDuckGo.
     */
    private fun extractYouTubeUrl(html: String): String? {
        // DuckDuckGo wraps URLs in href attributes
        val youtubePattern = Regex("""href="(https?://(?:www\.)?(?:youtube\.com/watch\?v=[^"&]+|youtu\.be/[^"&]+)[^"]*)"""")
        val match = youtubePattern.find(html)
        return match?.groupValues?.get(1)?.split("&")?.firstOrNull()
    }

    /**
     * Открывает YouTube URL в приложении YouTube или браузере.
     */
    private fun openYouTube(url: String) {
        // Try to open in YouTube app first
        val videoId = extractVideoId(url)
        
        if (videoId != null) {
            // Try YouTube app intent
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(appIntent)
                android.util.Log.d(TAG, "Открыто в YouTube приложении")
                return
            } catch (e: Exception) {
                // YouTube app not installed, fall through to browser
                android.util.Log.d(TAG, "YouTube приложение не найдено, открываю в браузере")
            }
        }
        
        // Open in browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(browserIntent)
        android.util.Log.d(TAG, "Открыто в браузере: $url")
    }

    /**
     * Извлекает ID видео из YouTube URL.
     */
    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""youtube\.com/watch\?v=([^&?]+)"""),
            Regex("""youtu\.be/([^&?]+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
