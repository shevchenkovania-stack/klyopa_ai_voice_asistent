/**
 * MemoryTools — набор инструментов для долгосрочной памяти агента.
 *
 * Файл содержит 3 инструмента:
 * - `remember_fact` — запомнить факт о пользователе
 * - `forget_fact` — удалить факт из памяти
 * - `list_memory` — показать все сохранённые факты
 *
 * Использует AgentMemory для хранения. Максимум 30 фактов.
 * Память структурирована: имя/возраст/город/работа обновляются (upsert),
 * а не плодятся. Агент сам решает что запоминать: имена, номера, предпочтения.
 *
 * Зависимости: AgentMemory
 */
package com.aiagent.ai_voice_agent.engine.tools

import com.aiagent.ai_voice_agent.engine.core.AgentMemory
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

/**
 * Инструмент `remember_fact` — запомнить факт.
 * Сохраняет факт в долгосрочную память через AgentMemory.
 */
class RememberFactTool(
    private val memory: AgentMemory
) : AgentTool {
    companion object { private const val TAG = "RememberFact" }

    override val name = "remember_fact"
    override val description = """
        Запомнить факт о пользователе на будущее.
        ЧТО ДЕЛАЕТ: Сохраняет факт в долгосрочную память (до 30 фактов).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "запомни", или когда узнал что-то важное (имя, номер, предпочтение).
        ПАРАМЕТРЫ: fact (обязательно) — факт для запоминания (кратко: "мама — Елена, +380501234567").
        ВОЗВРАЩАЕТ: "Запомнил: мама — Елена"
        ОГРАНИЧЕНИЯ: До 30 фактов. Имя/возраст/город/работа обновляются автоматически (новое заменяет старое). При переполнении вытесняется самый старый неважный факт.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(
            name = "fact",
            type = "string",
            description = "Факт для запоминания. Кратко и понятно. " +
                    "Примеры: 'мама — Елена, +380501234567', 'пользователь на ты', 'любимая группа — Imagine Dragons'",
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val fact = params["fact"] as? String
        if (fact.isNullOrBlank()) {
            return ToolResult.failure("Не передал факт для запоминания")
        }
        memory.remember(fact)
        return ToolResult.success("Запомнил: $fact")
    }
}

/**
 * Инструмент `forget_fact` — удалить факт из памяти.
 * Удаляет факт из долгосрочной памяти по частичному совпадению.
 */
class ForgetFactTool(
    private val memory: AgentMemory
) : AgentTool {
    companion object { private const val TAG = "ForgetFact" }

    override val name = "forget_fact"
    override val description = """
        Удалить факт из памяти.
        ЧТО ДЕЛАЕТ: Удаляет факт из долгосрочной памяти по частичному совпадению.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "забудь", "удали из памяти", "не помни".
        ПАРАМЕТРЫ: fact (обязательно) — факт или его часть для удаления (например "мама", "Елена").
        ВОЗВРАЩАЕТ: "Забыл: мама" или "Не нашёл в памяти: мама"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(
            name = "fact",
            type = "string",
            description = "Факт или его часть для удаления. Пример: 'мама', 'Елена'",
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val fact = params["fact"] as? String
        if (fact.isNullOrBlank()) {
            return ToolResult.failure("Не передал факт для удаления")
        }
        val removed = memory.forget(fact)
        return if (removed) {
            ToolResult.success("Забыл: $fact")
        } else {
            ToolResult.failure("Не нашёл в памяти: $fact")
        }
    }
}

/**
 * Инструмент `list_memory` — показать все сохранённые факты.
 * Используется, когда пользователь спрашивает «что ты помнишь», «что в памяти».
 */
class ListMemoryTool(
    private val memory: AgentMemory
) : AgentTool {
    companion object { private const val TAG = "ListMemory" }

    override val name = "list_memory"
    override val description = """
        Показать все сохранённые факты из долгосрочной памяти.
        ЧТО ДЕЛАЕТ: Возвращает список всех фактов, которые агент помнит о пользователе.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает «что ты помнишь», «что в памяти», «покажи память», «что ты знаешь обо мне».
        ПАРАМЕТРЫ: нет.
        ВОЗВРАЩАЕТ: Список фактов построчно, либо «Память пуста».
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val facts = memory.recall()
        if (facts.isEmpty()) {
            return ToolResult.success("Память пуста — пока ничего не запомнил.")
        }
        val list = facts.joinToString("\n") { "- $it" }
        return ToolResult.success("Вот что я помню (${facts.size}):\n$list")
    }
}
