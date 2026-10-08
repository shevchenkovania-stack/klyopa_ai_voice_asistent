/**
 * BrowseWebsiteTool — набор инструментов для работы с веб-сайтами.
 *
 * Файл содержит 2 инструмента:
 * - `browse_website` — открытие URL и извлечение текста страницы
 * - `find_links` — извлечение всех ссылок со страницы
 *
 * Оба инструмента используют OkHttp + Jsoup для парсинга HTML.
 * Поддерживают нормализацию кириллических TLD (мд→md, ру→ru, ком→com).
 * Поддерживают IDN (internationalized domain names) через punycode.
 *
 * Зависимости: OkHttp, Jsoup
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

// Shared Cyrillic TLD → Latin mapping for URL normalization
private val cyrillicTldMap = mapOf(
    "мд" to "md", "ру" to "ru", "ком" to "com", "орг" to "org",
    "нет" to "net", "инфо" to "info", "кз" to "kz", "укр" to "ua",
    "бг" to "bg", "срб" to "rs", "мкд" to "mk", "бел" to "by"
)

/**
 * Browse a specific website and extract content.
 * Agent can open URLs, read page content, search for specific information.
 */
class BrowseWebsiteTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "BrowseWebsite" }

    override val name = "browse_website"
    override val description = """
Зайти на конкретный сайт и прочитать его содержимое.
ЧТО ДЕЛАЕТ: Открывает URL, извлекает текст страницы. Показывает начало текста если не найдено.
КОГДА ИСПОЛЬЗОВАТЬ: Когда нужно зайти на конкретный сайт и прочитать общую информацию.

ВАЖНО — ЕСЛИ НУЖНО НАЙТИ КОНКРЕТНЫЙ ТОВАР/ИНФОРМАЦИЮ:
Если browse_website не нашёл конкретный товар/информацию (например "кроссовки 38 размер"),
ИСПОЛЬЗУЙ search_on_site для поиска по сайту через поисковик!
Пример: browse_website вернул "На странице не найдено 'кроссовки'" → вызови search_on_site.

ВАЖНО — НОРМАЛИЗАЦИЯ URL:
Если пользователь говорит кириллические домены — ПРЕОБРАЗУЙ в латиницу:
- "999.мд" → "999.md" (Молдова)
- "сайт.ру" → "сайт.ru" (Россия)
- "google.ком" → "google.com"
- "сайт.орг" → "сайт.org"
- "сайт.нет" → "сайт.net"
- "сайт.инфо" → "сайт.info"
- "сайт.кз" → "сайт.kz"
- "сайт.укр" → "сайт.ua"

Если не уверен в домене — попробуй оба варианта (кириллицу и латиницу).

ПАРАМЕТРЫ:
- url (string, обязательно) — URL сайта в ЛАТИНИЦЕ (например "999.md", "google.com")
- search_query (string, опц.) — что искать на странице
ПРИМЕРЫ:
- "зайди на 999.мд" → url="999.md"
- "открой гугл.ком" → url="google.com"
- "сайт яндекс.ру" → url="yandex.ru"
ВОЗВРАЩАЕТ: Текст страницы или результаты поиска если сайт недоступен
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "url", description = "URL сайта (например example.com, https://shop.md)", type = "string", required = true),
        ToolParam(name = "search_query", description = "Что искать на странице (например 'кроссовки', 'цена')", type = "string", required = false),
        ToolParam(name = "max_length", description = "Максимальная длина текста (по умолчанию 2000)", type = "number", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val url = params["url"] as? String ?: return ToolResult.failure("Не указан URL")
        val searchQuery = params["search_query"] as? String
        val maxLength = (params["max_length"] as? Number)?.toInt() ?: 2000

        return try {
            val normalizedUrl = normalizeUrl(url)

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return ToolResult.failure("Сайт вернул ошибку ${response.code}. Возможно, сайт заблокирован или недоступен.")
            }

            val html = response.body?.string() ?: return ToolResult.failure("Пустой ответ от сайта")

            // Parse HTML with Jsoup
            val doc = Jsoup.parse(html, normalizedUrl)

            // Remove script and style elements
            doc.select("script, style, nav, header, footer").remove()

            // Extract text
            val fullText = doc.body()?.text() ?: doc.text()

            if (fullText.isBlank()) {
                return ToolResult.failure("Страница пуста или не содержит текста")
            }

            // If search query provided, find relevant parts
            val result = if (searchQuery != null && searchQuery.isNotBlank()) {
                findRelevantContent(fullText, searchQuery, maxLength)
            } else {
                val truncated = fullText.take(maxLength)
                if (truncated.length < fullText.length) "$truncated... [обрезано]" else truncated
            }

            val title = doc.title()
            val summary = buildString {
                appendLine("Сайт: $normalizedUrl")
                if (title.isNotBlank()) appendLine("Заголовок: $title")
                appendLine()
                append(result)
            }

            ToolResult.success(summary.trim())
        } catch (e: Exception) {
            ToolResult.failure("Не удалось открыть сайт: ${e.message}")
        }
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        // Replace Cyrillic TLD with Latin equivalent
        val cyrillicTldRegex = Regex("""\.([а-яё]+)$""", RegexOption.IGNORE_CASE)
        normalized = cyrillicTldRegex.replace(normalized) { match ->
            val cyrillicTld = match.groupValues[1].lowercase()
            val latinTld = cyrillicTldMap[cyrillicTld]
            if (latinTld != null) ".$latinTld" else match.value
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        // Convert IDN (internationalized domain names) to ASCII (punycode)
        try {
            val uri = java.net.URI(normalized)
            val host = uri.host
            if (host != null && host.contains(Regex("[^\\x00-\\x7F]"))) {
                val asciiHost = java.net.IDN.toASCII(host)
                normalized = normalized.replace(host, asciiHost)
            }
        } catch (e: Exception) {
            android.util.Log.w("BrowseWebsite", "IDN conversion failed: ${e.message}")
        }
        return normalized
    }

    private fun findRelevantContent(text: String, query: String, maxLength: Int): String {
        val queryLower = query.lowercase()
        val sentences = text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.length > 20 }

        // Find sentences containing the query
        val relevant = sentences.filter { sentence ->
            sentence.lowercase().contains(queryLower)
        }

        if (relevant.isEmpty()) {
            return "На странице не найдено '$query'. Вот начало текста:\n\n${text.take(maxLength)}"
        }

        val combined = relevant.joinToString(". ")

        return if (combined.length > maxLength) {
            "Найдено по запросу '$query':\n\n${combined.take(maxLength)}... [обрезано]"
        } else {
            "Найдено по запросу '$query':\n\n$combined"
        }
    }
}

/**
 * Find links on a webpage.
 */
class FindLinksTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "FindLinks" }

    override val name = "find_links"
    override val description = """
        Найти все ссылки на странице сайта.
        ЧТО ДЕЛАЕТ: Открывает URL, извлекает все гиперссылки.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда нужно найти ссылки или товары на странице.
        ПАРАМЕТРЫ:
          url (string, обязательно) — URL сайта
          filter (string, опц.) — фильтр по тексту ссылок (например "кроссовки", "купить")
        ВОЗВРАЩАЕТ: Список ссылок с текстом
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "url", description = "URL сайта", type = "string", required = true),
        ToolParam(name = "filter", description = "Фильтр по тексту ссылок (например 'кроссовки')", type = "string", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val url = params["url"] as? String ?: return ToolResult.failure("Не указан URL")
        val filter = params["filter"] as? String

        return try {
            val normalizedUrl = normalizeUrl(url)

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return ToolResult.failure("Пустой ответ")

            val doc = Jsoup.parse(html, normalizedUrl)
            val links = doc.select("a[href]")

            val filtered = if (filter != null && filter.isNotBlank()) {
                links.filter { it.text().lowercase().contains(filter.lowercase()) }
            } else {
                links
            }

            if (filtered.isEmpty()) {
                val filterMsg = if (filter != null) " по запросу '$filter'" else ""
                return ToolResult.failure("Ссылок не найдено$filterMsg")
            }

            val result = filtered.take(20).joinToString("\n") { link ->
                val text = link.text().trim()
                val href = link.attr("abs:href")
                if (text.isNotBlank()) "• $text → $href" else "• $href"
            }

            val filterMsg = if (filter != null) " (фильтр: '$filter')" else ""
            ToolResult.success("Ссылки на $normalizedUrl$filterMsg:\n\n$result")
        } catch (e: Exception) {
            ToolResult.failure("Не удалось открыть сайт: ${e.message}")
        }
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        // Replace Cyrillic TLD with Latin equivalent
        val cyrillicTldRegex = Regex("""\.([а-яё]+)$""", RegexOption.IGNORE_CASE)
        normalized = cyrillicTldRegex.replace(normalized) { match ->
            val cyrillicTld = match.groupValues[1].lowercase()
            val latinTld = cyrillicTldMap[cyrillicTld]
            if (latinTld != null) ".$latinTld" else match.value
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        // Convert IDN (internationalized domain names) to ASCII (punycode)
        try {
            val uri = java.net.URI(normalized)
            val host = uri.host
            if (host != null && host.contains(Regex("[^\\x00-\\x7F]"))) {
                val asciiHost = java.net.IDN.toASCII(host)
                normalized = normalized.replace(host, asciiHost)
            }
        } catch (e: Exception) {
            android.util.Log.w("BrowseWebsite", "IDN conversion failed: ${e.message}")
        }
        return normalized
    }
}
