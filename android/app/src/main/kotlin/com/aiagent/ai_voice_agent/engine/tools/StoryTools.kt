package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StoryItem(
    val file: File,
    val title: String,
    val body: String,
    val rawText: String,
    val createdAt: Long
)

class StoryLibrary(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "kleopa_story_library"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val MAX_SPEECH_CHARS = 1800
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val storiesDir: File
        get() = File(context.filesDir, "kleopa_stories").apply {
            if (!exists()) mkdirs()
        }

    fun save(text: String, title: String?, topic: String?): StoryItem {
        val cleanText = text.trim()
        val cleanTitle = makeTitle(title ?: topic ?: cleanText)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val safeTitle = cleanTitle
            .replace(Regex("[^a-zA-Zа-яА-Я0-9\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(40)
        val file = File(storiesDir, "story_${timestamp}_${safeTitle.ifBlank { "history" }}.txt")
        val displayDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val content = buildString {
            appendLine("=== История от Клёпы ===")
            appendLine("Дата: $displayDate")
            appendLine("Название: $cleanTitle")
            if (!topic.isNullOrBlank()) appendLine("Тема: ${topic.trim()}")
            appendLine("========================")
            appendLine()
            append(cleanText)
        }
        file.writeText(content)
        return read(file)
    }

    fun list(): List<StoryItem> {
        return storiesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("txt", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file -> runCatching { read(file) }.getOrNull() }
            ?.filter { it.looksLikeStory() }
            ?: emptyList()
    }

    fun get(index: Int?, query: String?): Pair<Int, StoryItem>? {
        val stories = list()
        if (stories.isEmpty()) return null

        val foundIndex = when {
            !query.isNullOrBlank() -> stories.indexOfFirst { story ->
                val needle = query.lowercase().trim()
                story.title.lowercase().contains(needle) || story.body.lowercase().contains(needle)
            }
            index != null -> (index - 1).coerceIn(0, stories.lastIndex)
            else -> currentIndex(stories.size)
        }

        if (foundIndex !in stories.indices) return null
        setCurrentIndex(foundIndex, stories.size)
        return foundIndex to stories[foundIndex]
    }

    fun move(delta: Int): Pair<Int, StoryItem>? {
        val stories = list()
        if (stories.isEmpty()) return null
        val nextIndex = (currentIndex(stories.size) + delta).floorMod(stories.size)
        setCurrentIndex(nextIndex, stories.size)
        return nextIndex to stories[nextIndex]
    }

    fun share(story: StoryItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, story.title)
            putExtra(Intent.EXTRA_TEXT, story.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться историей").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun storyForSpeech(index: Int, total: Int, story: StoryItem): String {
        val body = if (story.body.length <= MAX_SPEECH_CHARS) {
            story.body
        } else {
            story.body.take(MAX_SPEECH_CHARS).trimEnd() + "..."
        }
        return "История ${index + 1} из $total. ${story.title}.\n$body"
    }

    private fun read(file: File): StoryItem {
        val raw = file.readText()
        val lines = raw.lines()
        val titleLine = lines.firstOrNull { it.startsWith("Название:") || it.startsWith("Тема:") }
        val title = makeTitle(titleLine?.substringAfter(":") ?: file.nameWithoutExtension)
        val dividerIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed.length >= 8 && trimmed.all { it == '=' }
        }
        val body = if (dividerIndex >= 0) {
            lines.drop(dividerIndex + 1).joinToString("\n").trim()
        } else {
            raw.trim()
        }
        return StoryItem(file, title, body, raw, file.lastModified())
    }

    private fun currentIndex(count: Int): Int {
        return prefs.getInt(KEY_CURRENT_INDEX, 0).coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    private fun setCurrentIndex(index: Int, count: Int) {
        prefs.edit().putInt(KEY_CURRENT_INDEX, index.coerceIn(0, count - 1)).apply()
    }

    private fun makeTitle(value: String): String {
        val compact = value
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', '!', '?', ':', ';', '-', '—')
        return compact.take(60).ifBlank { "Без названия" }
    }

    private fun StoryItem.looksLikeStory(): Boolean {
        val haystack = "${title.lowercase()} ${body.lowercase().take(700)}"
        val storyWords = listOf(
            "истори",
            "сказк",
            "рассказ",
            "байк",
            "легенд",
            "приключ",
            "однажды",
            "жил ",
            "жила ",
            "герой",
            "героин",
            "в одном"
        )
        if (storyWords.any { haystack.contains(it) }) return true

        val serviceWords = listOf(
            "accessibility",
            "спецвозмож",
            "youtube",
            "не могу",
            "не получилось",
            "нужно включ",
            "ошибка",
            "готово:",
            "инструмент",
            "разрешен"
        )
        return body.length >= 420 && serviceWords.none { haystack.contains(it) }
    }

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
}

private fun numberParam(params: Map<String, Any?>, name: String): Int? {
    return when (val value = params[name]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

private fun stringParam(params: Map<String, Any?>, name: String): String? {
    return (params[name] as? String)?.takeIf { it.isNotBlank() }
}

class SaveStoryTool(private val context: Context) : AgentTool {
    override val name = "save_story"
    override val description = "Сохранить интересную историю Клёпы в отдельную библиотеку историй."
    override val parameters = listOf(
        ToolParam("text", "string", "Полный текст истории для сохранения", required = true),
        ToolParam("title", "string", "Короткое название истории", required = false),
        ToolParam("topic", "string", "Тема истории", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val text = stringParam(params, "text") ?: return ToolResult.failure("Не вижу текст истории для сохранения.")
        val library = StoryLibrary(context)
        val story = library.save(text, stringParam(params, "title"), stringParam(params, "topic"))
        val count = library.list().size
        return ToolResult.success(
            "Сохранила историю «${story.title}». Всего историй: $count.",
            mapOf("title" to story.title, "count" to count, "path" to story.file.absolutePath)
        )
    }
}

class ListStoriesTool(private val context: Context) : AgentTool {
    override val name = "list_stories"
    override val description = "Показать список сохранённых историй Клёпы."
    override val parameters = listOf(
        ToolParam("limit", "number", "Сколько историй показать", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val library = StoryLibrary(context)
        val stories = library.list()
        if (stories.isEmpty()) {
            return ToolResult.success("Пока нет сохранённых историй. Попроси меня рассказать историю, и я её сохраню.")
        }
        val limit = numberParam(params, "limit")?.coerceIn(1, 20) ?: 10
        val listText = stories.take(limit).mapIndexed { index, story -> "${index + 1}. ${story.title}" }
            .joinToString("\n")
        val tail = if (stories.size > limit) "\nИ ещё ${stories.size - limit}." else ""
        return ToolResult.success(
            "У меня ${stories.size} историй:\n$listText$tail\nМожно сказать: следующая история, предыдущая история, перескажи историю номер 2, поделись историей.",
            mapOf("count" to stories.size)
        )
    }
}

class ReadStoryTool(private val context: Context) : AgentTool {
    override val name = "read_story"
    override val description = "Достать и пересказать сохранённую историю."
    override val parameters = listOf(
        ToolParam("index", "number", "Номер истории в списке", required = false),
        ToolParam("query", "string", "Поиск по названию или тексту", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val library = StoryLibrary(context)
        val stories = library.list()
        val found = library.get(numberParam(params, "index"), stringParam(params, "query"))
            ?: return ToolResult.failure("Не нашла такую историю.")
        return ToolResult.success(
            library.storyForSpeech(found.first, stories.size, found.second),
            mapOf("index" to found.first + 1, "count" to stories.size, "title" to found.second.title)
        )
    }
}

class NextStoryTool(private val context: Context) : AgentTool {
    override val name = "next_story"
    override val description = "Перейти к следующей сохранённой истории и пересказать её."
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val library = StoryLibrary(context)
        val stories = library.list()
        val found = library.move(1) ?: return ToolResult.failure("Историй пока нет.")
        return ToolResult.success(library.storyForSpeech(found.first, stories.size, found.second))
    }
}

class PreviousStoryTool(private val context: Context) : AgentTool {
    override val name = "previous_story"
    override val description = "Вернуться к предыдущей сохранённой истории и пересказать её."
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val library = StoryLibrary(context)
        val stories = library.list()
        val found = library.move(-1) ?: return ToolResult.failure("Историй пока нет.")
        return ToolResult.success(library.storyForSpeech(found.first, stories.size, found.second))
    }
}

class ShareStoryTool(private val context: Context) : AgentTool {
    override val name = "share_story"
    override val description = "Открыть системное меню, чтобы поделиться сохранённой историей."
    override val parameters = listOf(
        ToolParam("index", "number", "Номер истории в списке", required = false),
        ToolParam("query", "string", "Поиск по названию или тексту", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val library = StoryLibrary(context)
        val found = library.get(numberParam(params, "index"), stringParam(params, "query"))
            ?: return ToolResult.failure("Не нашла историю, которой можно поделиться.")
        library.share(found.second)
        return ToolResult.success(
            "Открыла меню, чтобы поделиться историей «${found.second.title}».",
            mapOf("index" to found.first + 1, "title" to found.second.title)
        )
    }
}
