package com.aiagent.ai_voice_agent.engine.core.agent

import android.content.Context
import android.util.Log
import com.aiagent.ai_voice_agent.engine.IntentDetector
import com.aiagent.ai_voice_agent.engine.PermissionManager
import com.aiagent.ai_voice_agent.engine.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  MULTI-AGENT ORCHESTRATOR                                    ║
 * ║                                                              ║
 * ║  Маршрутизирует запрос пользователя к специализированному     ║
 *  ║  агенту в зависимости от темы.                               ║
 * ║                                                              ║
 * ║  Архитектура:                                                ║
 * ║  1. Router (ToolSelector) — определяет домен по ключевым     ║
 * ║     словам (быстро, бесплатно, без LLM)                      ║
 * ║  2. SpecialistAgent (VoiceAgent) — обрабатывает запрос       ║
 * ║     имея доступ ТОЛЬКО к своим инструментам                  ║
 * ║  3. General агент — fallback для всего остального            ║
 * ║                                                              ║
 * ║  Каждый агент хранит свою историю диалога — контекст не      ║
 * ║  смешивается между доменами.                                 ║
 * ║                                                              ║
 * ║  Чтобы добавить новый домен:                                 ║
 * ║  1. Добавь entry в AgentDomain enum                          ║
 * ║  2. Укажи toolNames — какие инструменты туда входят          ║
 * ║  3. Готово — оркестратор сам создаст агента                  ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * Cross-platform: этот класс использует VoiceAgent и ToolRegistry
 * (которые зависят от Android контекста). Для переноса на другую
 * платформу нужно заменить VoiceAgent на реализацию под целевую
 * платформу — интерфейс остаётся тем же.
 */
class MultiAgentOrchestrator(
    private val context: Context,
    private val toolRegistry: ToolRegistry,
    private val memory: AgentMemory? = null
) {
    companion object {
        private const val TAG = "MultiAgent"
    }

    // Общий агент — для приветствий, болтовни, всего что не попало в домены
    // Использует ТОЛЬКО инструменты из GENERAL домена (web_search)
    // Не засоряем LLM 77 инструментами — он и так думает долго
    private val generalAgent: VoiceAgent by lazy {
        createSpecialist("GENERAL", AgentDomain.GENERAL.toolNames)
    }

    // Специализированные агенты — создаются лениво при первом запросе к домену
    private val specialistCache = ConcurrentHashMap<AgentDomain, VoiceAgent>()

    // Шаренный ToolSelector для быстрой маршрутизации
    private val toolSelector = ToolSelector(toolRegistry)

    // Фоновый scope + клиент для лёгкого LLM-извлечения фактов (не блокирует ответ)
    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val extractorClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // IntentDetector для быстрого пути (пробрасывается в агентов)
    @Volatile
    var intentDetector: IntentDetector? = null

    // PermissionManager для проверки безопасности (пробрасывается в агентов)
    @Volatile
    var permissionManager: PermissionManager? = null

    // Конфиг для новых агентов (синхронизируется через reloadConfig)
    @Volatile
    private var currentGroqKey: String = ""
    @Volatile
    private var currentGeminiKey: String = ""
    @Volatile
    private var currentOpenAiKey: String = ""
    @Volatile
    private var currentProvider: String = "openai"
    @Volatile
    private var currentAgentName: String = "Клёпа"

    // Последняя реплика пользователя — нужна для команды "запомни", сказанной отдельной фразой
    @Volatile
    private var lastUserUtterance: String = ""

    /**
     * Обработать сообщение пользователя через multi-agent pipeline.
     *
     * 1. Маршрутизация: определяем домен по ключевым словам
     * 2. Выбор/создание агента для этого домена
     * 3. Обработка сообщения агентом (LLM + инструменты домена)
     * 4. Возврат ответа
     */
    suspend fun process(userMessage: String, onToken: ((String) -> Unit)? = null): String {
        Log.d(TAG, "=== MultiAgent process: '$userMessage' ===")

        // Детерминированный захват памяти ДО маршрутизации:
        // гарантирует запись фактов независимо от того, вызовет ли модель remember_fact.
        val captured = captureMemory(userMessage)
        // Лёгкий кросс-сессионный контекст: что пользователь недавно говорил.
        memory?.rememberContext(userMessage)
        lastUserUtterance = userMessage
        // Если шаблоны факт не поймали — пробуем в фоне лёгким LLM-проходом.
        if (!captured) maybeExtractFactAsync(userMessage)

        val domain = route(userMessage)
        val agent = getOrCreateAgent(domain)
        
        // Сохраняем последнего вызванного агента (для тестов)
        lastCalledAgent = agent

        return agent.process(userMessage, onToken)
    }

    /**
     * Очистить историю всех агентов.
     */
    fun clearAllContexts() {
        generalAgent.clearContext()
        specialistCache.values.forEach { it.clearContext() }
        specialistCache.clear()
        Log.d(TAG, "Все контексты очищены")
    }

    /**
     * Очистить историю конкретного домена.
     */
    fun clearDomainContext(domain: AgentDomain) {
        specialistCache[domain]?.clearContext()
        Log.d(TAG, "Контекст домена ${domain.name} очищен")
    }

    /**
     * Обновить конфиг во всех агентах.
     * Вызывается после изменения настроек (API ключи, провайдер, имя).
     */
    fun reloadConfig(
        groqApiKey: String,
        geminiApiKey: String,
        openaiApiKey: String,
        activeProvider: String,
        agentName: String
    ) {
        currentGroqKey = groqApiKey
        currentGeminiKey = geminiApiKey
        currentOpenAiKey = openaiApiKey
        currentProvider = activeProvider
        currentAgentName = agentName

        // Обновить существующих агентов
        fun applyToAgent(agent: VoiceAgent) {
            agent.groqApiKey = groqApiKey
            agent.geminiApiKey = geminiApiKey
            agent.openaiApiKey = openaiApiKey
            agent.activeProvider = activeProvider
            agent.agentName = agentName
        }

        applyToAgent(generalAgent)
        specialistCache.values.forEach { applyToAgent(it) }

        Log.d(TAG, "Конфиг обновлён для ${specialistCache.size + 1} агентов")
    }

    // ==================== PRIVATE ====================
    
    /**
     * Получить последнего вызванного агента (для тестов).
     */
    fun getLastCalledAgent(): VoiceAgent? {
        return lastCalledAgent
    }
    
    private var lastCalledAgent: VoiceAgent? = null

    /**
     * Маршрутизация сообщения к домену.
     *
     * Использует стем-матчинг (корень 4 символа) — понимает падежи русского языка.
     * Если не удалось определить — возвращает GENERAL.
     */
    private fun route(userMessage: String): AgentDomain {
        val lower = userMessage.lowercase()

        // Извлекаем стемы из сообщения (4+ символа)
        val messageStems = lower.split(Regex("[\\s,.!?:;]+"))
            .filter { it.length >= 4 }
            .map { it.take(4) }
            .toSet()

        // Проверяем каждый домен через стем-матчинг
        for (domain in AgentDomain.entries) {
            val hints = domain.routeHint.lowercase().split(", ")
            for (hint in hints) {
                // Точное вхождение (для коротких слов типа "play", "sms")
                if (lower.contains(hint)) {
                    Log.d(TAG, "Роутинг → ${domain.name} (точное: '$hint')")
                    return domain
                }
                // Стем-матчинг (для русских слов с падежами)
                if (hint.length >= 4) {
                    val hintStem = hint.take(4)
                    if (messageStems.any { it == hintStem }) {
                        Log.d(TAG, "Роутинг → ${domain.name} (стем: '$hintStem' из '$hint')")
                        return domain
                    }
                }
            }
        }

        // Fallback: используем ToolSelector для точного определения
        val tools = toolSelector.selectTools(userMessage)
        if (tools.isNotEmpty()) {
            // Фильтруем ALWAYS_INCLUDE инструменты (они всегда есть, но не определяют домен)
            val alwaysInclude = setOf("self_awareness", "remember_fact", "forget_fact", "list_memory")
            val filtered = tools.filter { it.name !in alwaysInclude }
            if (filtered.isNotEmpty()) {
                // Берём домен первого найденного инструмента
                val firstTool = filtered.first().name
                val domain = AgentDomain.forTool(firstTool)
                if (domain != null && domain != AgentDomain.GENERAL) {
                    Log.d(TAG, "Роутинг → ${domain.name} (инструмент: $firstTool)")
                    return domain
                }
            }
        }

        Log.d(TAG, "Роутинг → GENERAL (не определено)")
        return AgentDomain.GENERAL
    }

    /**
     * Получить или создать агента для домена.
     * Агенты создаются лениво — при первом запросе к домену.
     */
    private fun getOrCreateAgent(domain: AgentDomain): VoiceAgent {
        if (domain == AgentDomain.GENERAL) return generalAgent
        return specialistCache.getOrPut(domain) {
            createSpecialist(domain.name, domain.toolNames)
        }
    }

    /**
     * Создать специализированного агента.
     *
     * @param domainName имя домена (используется в логах)
     * @param tools набор инструментов для этого агента
     */
    private fun createSpecialist(domainName: String, tools: Set<String>): VoiceAgent {
        Log.d(TAG, "Создание агента '$domainName' с ${tools.size} инструментами")

        // Собираем описания инструментов для промпта
        val toolDescriptions = tools.mapNotNull { name ->
            toolRegistry.get(name)?.let { tool ->
                val shortDesc = tool.description.substringBefore("\n").trim()
                "- ${tool.name}: ${shortDesc.take(120)}"
            }
        }.joinToString("\n")

        val domainSuffix = AgentCharacter.domainPrompt(domainName, toolDescriptions)

        return VoiceAgent(
            context = context,
            toolRegistry = toolRegistry,
            memory = memory,
            domainTools = tools,
            domainPromptSuffix = domainSuffix
        ).apply {
            groqApiKey = currentGroqKey
            geminiApiKey = currentGeminiKey
            openaiApiKey = currentOpenAiKey
            activeProvider = currentProvider
            agentName = currentAgentName
            intentDetector = this@MultiAgentOrchestrator.intentDetector
            permissionManager = this@MultiAgentOrchestrator.permissionManager
        }
    }

    // ==================== ПАМЯТЬ (детерминированный захват) ====================

    /**
     * Пишет факты в долгосрочную память напрямую, не полагаясь на то,
     * вызовет ли LLM remember_fact. Два пути:
     *  1) явная команда "запомни/запиши/заметь ..." — сохраняем указанное (или прошлую реплику);
     *  2) частые личные фразы (имя, предпочтения, возраст) — сохраняем сами, без команды.
     */
    private fun captureMemory(message: String): Boolean {
        val mem = memory ?: return false
        try {
            val text = message.trim()
            if (text.isEmpty()) return false
            val lower = text.lowercase()

            // 1) Явная команда "запомни/запиши/заметь" (indexOf надёжнее \b для кириллицы)
            val hit = listOf("запомни", "запиши", "заметь")
                .mapNotNull { w -> lower.indexOf(w).let { if (it >= 0) it to w else null } }
                .minByOrNull { it.first }
            if (hit != null) {
                val (idx, word) = hit
                var fact = text.substring(idx + word.length).trim().trimStart(',', ':', '-', ' ')
                // Убираем «что ...» в начале — это лишнее вводное слово команды
                if (fact.lowercase().startsWith("что ")) fact = fact.substring(6).trim()
                if (fact.isBlank()) {
                    val before = text.substring(0, idx).trim().trimEnd(',', '-', ' ')
                    fact = if (before.isNotBlank()) before else lastUserUtterance.trim()
                }
                if (fact.isNotBlank()) {
                    // Пишем через rememberWithKey — факт пройдёт нормализацию и попадёт
                    // в правильную категорию (name/age/like/...), а не «что ...».
                    mem.rememberWithKey(fact.take(120), null)
                    Log.d(TAG, "Память (команда): '$fact'")
                    return true
                }
                return false
            }

            // 2) Проактивные шаблоны (без слова "запомни")
            return captureByPatterns(lower, mem)
        } catch (e: Exception) {
            Log.e(TAG, "captureMemory ошибка: ${e.message}")
            return false
        }
    }

    /**
     * Ловит частые личные факты по шаблонам. Работает по lower-тексту.
     * AgentMemory сам отсеивает дубли, поэтому повторы безопасны.
     */
    private fun captureByPatterns(lower: String, mem: AgentMemory): Boolean {
        var matched = false
        // Имя: "меня зовут Иван"
        Regex("меня зовут\\s+([\\p{L}\\-]{2,30})").find(lower)?.let {
            val name = it.groupValues[1].replaceFirstChar { c -> c.uppercaseChar() }
            mem.remember("меня зовут $name")
            Log.d(TAG, "Память (имя): $name")
            matched = true
        }

        // Возраст: "мне 30 лет"
        Regex("мне\\s+(\\d{1,3})\\s+(?:год|года|лет)").find(lower)?.let {
            mem.remember("возраст: ${it.groupValues[1]}")
            matched = true
        }

        // Предпочтения. Сначала отрицание, чтобы "не люблю" не попало в "люблю".
        val negative = lower.contains("не люблю") || lower.contains("ненавижу")
        if (negative) {
            Regex("(?:не\\s+люблю|ненавижу)\\s+([\\p{L}\\s\\-]{2,40})").find(lower)?.let {
                val what = it.groupValues[1].trim().trimEnd('.', ',', '!', '?')
                if (what.isNotBlank()) { mem.remember("не любит $what"); matched = true }
            }
        } else {
            Regex("(?:люблю|обожаю)\\s+([\\p{L}\\s\\-]{2,40})").find(lower)?.let {
                val what = it.groupValues[1].trim().trimEnd('.', ',', '!', '?')
                if (what.isNotBlank()) { mem.remember("любит $what"); matched = true }
            }
            Regex("мне\\s+нравится\\s+([\\p{L}\\s\\-]{2,40})").find(lower)?.let {
                val what = it.groupValues[1].trim().trimEnd('.', ',', '!', '?')
                if (what.isNotBlank()) { mem.remember("нравится $what"); matched = true }
            }
        }
        return matched
    }

    // ==================== ПАМЯТЬ (лёгкий LLM-проход в фоне) ====================

    /**
     * Запускает извлечение факта в фоне — НЕ блокирует ответ пользователю.
     * Дешёвый предфильтр: только реплики от первого лица, иначе не тратим API.
     */
    private fun maybeExtractFactAsync(message: String) {
        val mem = memory ?: return
        val text = message.trim()
        if (text.length < 6) return
        val lower = text.lowercase()
        val firstPerson = listOf("я ", "меня", "мне", "мой", "моя", "мои", "моё", "мою", "у меня")
            .any { lower.contains(it) }
        if (!firstPerson) return

        bgScope.launch {
            try {
                val fact = extractFact(text) ?: return@launch
                mem.remember(fact.take(120))
                Log.d(TAG, "Память (LLM): '$fact'")
            } catch (e: Exception) {
                Log.e(TAG, "extractFact ошибка: ${e.message}")
            }
        }
    }

    /**
     * Один запрос к LLM: выделить ОДИН стабильный факт или вернуть NONE.
     * Использует тот же провайдер/модель/ключи, что и основной агент.
     */
    private fun extractFact(message: String): String? {
        val url: String
        val key: String
        val model: String
        when (currentProvider) {
            "gemini" -> {
                url = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
                key = currentGeminiKey
                model = "gemini-2.0-flash"
            }
            "groq" -> {
                url = "https://api.groq.com/openai/v1/chat/completions"
                key = currentGroqKey
                model = "llama-3.3-70b-versatile"
            }
            else -> {
                url = "https://api.openai.com/v1/chat/completions"
                key = currentOpenAiKey
                model = "gpt-4o-mini"
            }
        }
        if (key.isBlank()) return null

        val sys = "Ты извлекаешь ОДИН стабильный факт о пользователе для долгой памяти: " +
            "имя, как обращаться, близкие люди, устойчивые предпочтения, работа, город, важные даты. " +
            "Игнорируй сиюминутное (погода, разовые вопросы, команды). " +
            "Если стабильного факта нет — ответь строго: NONE. " +
            "Иначе — ОДНА короткая фраза на русском, без пояснений и кавычек."

        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("max_tokens", 40)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(JSONObject().put("role", "user").put("content", message))
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        extractorClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string() ?: return null)
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim() ?: return null
            val cleaned = content.trim().trim('"', '.', ' ', '«', '»')
            if (cleaned.isBlank() || cleaned.length > 100) return null
            if (cleaned.uppercase().contains("NONE")) return null
            return cleaned
        }
    }
}
