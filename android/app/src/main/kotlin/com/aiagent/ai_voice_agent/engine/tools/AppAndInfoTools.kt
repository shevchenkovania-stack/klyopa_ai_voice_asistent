package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.aiagent.ai_voice_agent.engine.EngineManager
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Open/launch app by name — uses LLM to intelligently resolve app from installed list.
 * No hardcoded aliases — works on any phone with any language.
 */
class OpenAppTool(private val context: Context) : AgentTool {
    override val name = "open_app"
    override val description = "Запустить приложение на телефоне. Понимает любые названия: русские, английские, разговорные (\"заметки\", \"телега\", \"ютуб\")"
    override val parameters = listOf(
        ToolParam("name", "string", "Какое приложение открыть (например \"заметки\", \"YouTube\", \"телега\")", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val name = params["name"] as? String ?: return ToolResult.failure("Не указано название")
        val pm = context.packageManager

        // Get all launchable apps
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .mapNotNull { appInfo ->
                val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    "${label} (${appInfo.packageName})"
                } else null
            }

        if (apps.isEmpty()) {
            return ToolResult.failure("Нет установленных приложений с launch intent")
        }

        // Try LLM-based resolution
        val resolvedPackage = resolveWithLLM(name, apps)
        if (resolvedPackage != null) {
            return try {
                val intent = pm.getLaunchIntentForPackage(resolvedPackage)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    val label = try {
                        val appInfo = pm.getApplicationInfo(resolvedPackage, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) { resolvedPackage }
                    ToolResult.success("✓ Приложение \"$label\" запущено")
                } else {
                    ToolResult.failure("У приложения нет launch intent")
                }
            } catch (e: Exception) {
                ToolResult.failure("Ошибка запуска: ${e.message}")
            }
        }

        // Fallback: simple contains match
        val nameLower = name.lowercase()
        val similar = apps.filter { it.lowercase().contains(nameLower) }.take(5)

        return if (similar.isNotEmpty()) {
            ToolResult.failure("Не удалось определить приложение. Похожие:\n${similar.joinToString("\n") { "- $it" }}")
        } else {
            ToolResult.failure("Приложение \"$name\" не найдено на телефоне")
        }
    }

    /**
     * Ask LLM to pick the best matching app from the installed list.
     * Returns package name or null if LLM can't resolve.
     */
    private fun resolveWithLLM(query: String, appList: List<String>): String? {
        return try {
            val config = EngineManager.config
            val apiKey = if (config.activeProvider == "groq") config.groqApiKey else config.openaiApiKey
            if (apiKey.isEmpty()) return null

            val baseUrl = if (config.activeProvider == "groq")
                "https://api.groq.com/openai/v1/chat/completions"
            else
                "https://api.openai.com/v1/chat/completions"

            // Build compact app list (limit to avoid token overflow)
            val compactList = appList.take(150).joinToString("\n")

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Ты помогаешь найти приложение на телефоне. Пользователь говорит название (возможно на русском, разговорное, или неточное). Ты должен выбрать ОДИН пакет из списка установленных приложений. Ответь ТОЛЬКО package name (например com.google.android.keep) без объяснений. Если подходящего приложения нет, ответь NONE.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Пользователь хочет открыть: \"$query\"\n\nУстановленные приложения:\n$compactList\n\nКакой пакет запустить?")
                })
            }

            val body = JSONObject().apply {
                put("model", if (config.activeProvider == "groq") "llama-3.3-70b-versatile" else "gpt-4o-mini")
                put("messages", messages)
                put("temperature", 0)
                put("max_tokens", 50)
            }

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url(baseUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            val json = JSONObject(responseBody)
            val content = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            android.util.Log.d("OpenApp", "LLM resolved '$query' → '$content'")

            if (content.equals("NONE", ignoreCase = true) || content.isEmpty()) {
                return null
            }

            // Verify the package exists in our list
            val packageName = content.replace(Regex("[^a-zA-Z0-9._]"), "")
            if (appList.any { it.contains(packageName) }) {
                packageName
            } else {
                android.util.Log.w("OpenApp", "LLM returned unknown package: $packageName")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("OpenApp", "LLM resolution failed: ${e.message}")
            null
        }
    }
}

/**
 * Self-awareness — control agent visibility
 */
class SelfAwarenessTool(private val context: Context) : AgentTool {
    override val name = "self_awareness"
    override val description = "Управление видимостью агента. \"show\" — появиться, \"hide\" — скрыться."
    override val parameters = listOf(
        ToolParam("action", "string", "Действие: show или hide", required = true, enumValues = listOf("show", "hide"))
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"] as? String ?: return ToolResult.failure("Не указано действие")

        return when (action) {
            "show" -> {
                // Bring app to foreground via Intent
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(intent)
                }
                ToolResult.success("✓ Агент появился на экране")
            }
            "hide" -> {
                // Move task to back
                ToolResult.success("✓ Агент скрылся в фон")
            }
            else -> ToolResult.failure("Неизвестное действие: $action")
        }
    }
}

/**
 * Web search — DuckDuckGo HTML search with real results
 */
class WebSearchTool(private val context: Context) : AgentTool {
    override val name = "web_search"
    override val description = "Поиск в интернете. Ищет информацию, статьи, ответы на вопросы. Понимает русские и английские запросы."
    override val parameters = listOf(
        ToolParam("query", "string", "Поисковый запрос (например \"какие окна лучше ставить\", \"best pizza recipe\")", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val query = params["query"] as? String ?: return ToolResult.failure("Не указан запрос")
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .build()

            // Step 1: Get DuckDuckGo HTML search page
            val url = "https://html.duckduckgo.com/html/?q=${Uri.encode(query)}"
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return ToolResult.failure("Пустой ответ от поиска")

            // Step 2: Parse search results from HTML
            val results = parseDuckDuckGoHtml(html)

            if (results.isEmpty()) {
                // Fallback: try instant answer API
                val instantResult = tryInstantAnswer(client, query)
                if (instantResult != null) {
                    return ToolResult.success(instantResult)
                }
                return ToolResult.failure("По запросу \"$query\" ничего не найдено")
            }

            // Step 3: Format top results
            val top = results.take(5)
            val formatted = top.joinToString("\n\n") { r ->
                buildString {
                    appendLine("[${r.title}]")
                    appendLine(r.snippet)
                    append("(${r.url})")
                }
            }

            ToolResult.success("Результаты поиска по \"$query\":\n\n$formatted")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка поиска: ${e.message}")
        }
    }

    data class SearchResult(val title: String, val snippet: String, val url: String)

    private fun parseDuckDuckGoHtml(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // DuckDuckGo HTML uses result-link class for titles and result__snippet for descriptions
        val resultBlocks = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>.*?<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(html)

        for (block in resultBlocks) {
            val rawUrl = block.groupValues[1]
            val rawTitle = block.groupValues[2]
            val rawSnippet = block.groupValues[3]

            val title = cleanHtml(rawTitle).trim()
            val snippet = cleanHtml(rawSnippet).trim()
            val url = extractRealUrl(rawUrl)

            if (title.isNotEmpty() && snippet.isNotEmpty()) {
                results.add(SearchResult(title, snippet, url))
            }
        }

        // Fallback parser: try simpler pattern
        if (results.isEmpty()) {
            val simplePattern = Regex(
                """<a[^>]*rel="nofollow"[^>]*class="result__a"[^>]*>(.*?)</a>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val snippetPattern = Regex(
                """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val titles = simplePattern.findAll(html).map { cleanHtml(it.groupValues[1]).trim() }.toList()
            val snippets = snippetPattern.findAll(html).map { cleanHtml(it.groupValues[1]).trim() }.toList()

            for (i in titles.indices) {
                if (i < snippets.size && titles[i].isNotEmpty() && snippets[i].isNotEmpty()) {
                    results.add(SearchResult(titles[i], snippets[i], ""))
                }
            }
        }

        return results
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")  // Remove tags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
    }

    private fun extractRealUrl(ddgUrl: String): String {
        // DuckDuckGo redirects through their own URL: /l/?khg=...&uddg=REAL_URL
        val uddgMatch = Regex("uddg=([^&]+)").find(ddgUrl)
        if (uddgMatch != null) {
            return try {
                java.net.URLDecoder.decode(uddgMatch.groupValues[1], "UTF-8")
            } catch (e: Exception) {
                ddgUrl
            }
        }
        return ddgUrl
    }

    private fun tryInstantAnswer(client: okhttp3.OkHttpClient, query: String): String? {
        return try {
            val url = "https://api.duckduckgo.com/?q=${Uri.encode(query)}&format=json&no_html=1&skip_disambig=1"
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = org.json.JSONObject(body)

            val abstractText = json.optString("AbstractText", "")
            val heading = json.optString("Heading", "")

            if (abstractText.isNotEmpty()) {
                "$heading: $abstractText"
            } else {
                val topics = json.optJSONArray("RelatedTopics")
                if (topics != null && topics.length() > 0) {
                    val texts = mutableListOf<String>()
                    for (i in 0 until minOf(topics.length(), 3)) {
                        val text = topics.getJSONObject(i).optString("Text", "")
                        if (text.isNotEmpty()) texts.add(text)
                    }
                    if (texts.isNotEmpty()) texts.joinToString("\n") else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
