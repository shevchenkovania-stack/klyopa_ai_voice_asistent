/**
 * SearchOnSiteTool — инструмент для поиска информации на конкретном сайте.
 *
 * Файл содержит 1 инструмент:
 * - `search_on_site` — поиск через DuckDuckGo с оператором site:
 *
 * Использует DuckDuckGo HTML-версию для парсинга результатов.
 * Поддерживает нормализацию кириллических TLD.
 *
 * Зависимости: OkHttp
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.net.Uri
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Search within a specific website using DuckDuckGo site: operator.
 * Example: search "кроссовки 38 размер" on "999.md"
 */
class SearchOnSiteTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "SearchOnSite" }

    override val name = "search_on_site"
    override val description = """
Найти информацию на конкретном сайте.
ЧТО ДЕЛАЕТ: Ищет запрос на указанном сайте через поисковик DuckDuckGo (site: оператор).
КОГДА ИСПОЛЬЗОВАТЬ: Когда нужно найти что-то на конкретном сайте, а browse_website не нашёл (например "найди кроссовки на 999.md", "поиск iPhone на сайте магазина").

ВАЖНО — НОРМАЛИЗАЦИЯ ДОМЕНА:
Если пользователь говорит кириллические домены — ПРЕОБРАЗУЙ в латиницу:
- "999.мд" → "999.md", "сайт.ру" → "сайт.ru", "google.ком" → "google.com"

ПАРАМЕТРЫ:
- site (string, обязательно) — домен сайта (например "999.md", "example.com")
- query (string, обязательно) — что искать (например "кроссовки 38 размер", "iPhone")
ПРИМЕРЫ:
- "найди кроссовки 38 размер на 999.md" → site="999.md", query="кроссовки 38 размер"
- "поиск iPhone на сайте магазина" → site="magazin.md", query="iPhone"
ВОЗВРАЩАЕТ: Топ-5 результатов поиска с указанного сайта
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "site", description = "Домен сайта (например 999.md, example.com)", type = "string", required = true),
        ToolParam(name = "query", description = "Что искать на сайте", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val site = params["site"] as? String ?: return ToolResult.failure("Не указан сайт")
        val query = params["query"] as? String ?: return ToolResult.failure("Не указан запрос")

        return try {
            // Normalize site (remove protocol if present)
            val normalizedSite = site
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .trim()

            // Cyrillic TLD fallback
            val fixedSite = fixCyrillicTld(normalizedSite)

            // Use DuckDuckGo with site: operator
            val searchQuery = "site:$fixedSite $query"
            val url = "https://html.duckduckgo.com/html/?q=${Uri.encode(searchQuery)}"

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "ru-RU,ru;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return ToolResult.failure("Пустой ответ от поиска")

            // Parse DuckDuckGo results
            val results = parseDuckDuckGoResults(html)

            if (results.isEmpty()) {
                return ToolResult.failure("На сайте $fixedSite ничего не найдено по запросу '$query'")
            }

            // Format results
            val top = results.take(5)
            val formatted = top.joinToString("\n\n") { r ->
                buildString {
                    appendLine("[${r.title}]")
                    appendLine(r.snippet)
                    append("(${r.url})")
                }
            }

            ToolResult.success("Результаты поиска '$query' на $fixedSite:\n\n$formatted")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка поиска: ${e.message}")
        }
    }

    data class SearchResult(val title: String, val snippet: String, val url: String)

    private fun parseDuckDuckGoResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

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

        return results
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&#x2F;", "/")
            .replace(Regex("\\s+"), " ")
    }

    private fun extractRealUrl(ddgUrl: String): String {
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

    private fun fixCyrillicTld(domain: String): String {
        val cyrillicTlds = mapOf(
            "мд" to "md", "ру" to "ru", "ком" to "com", "орг" to "org",
            "нет" to "net", "инфо" to "info", "кз" to "kz", "укр" to "ua"
        )
        val parts = domain.split(".")
        if (parts.size >= 2) {
            val tld = parts.last().lowercase()
            val latinTld = cyrillicTlds[tld]
            if (latinTld != null) {
                return parts.dropLast(1).joinToString(".") + ".$latinTld"
            }
        }
        return domain
    }
}
