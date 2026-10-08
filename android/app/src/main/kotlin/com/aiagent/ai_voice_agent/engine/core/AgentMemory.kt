package com.aiagent.ai_voice_agent.engine.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  ДОЛГОСРОЧНАЯ ПАМЯТЬ АГЕНТА                                    ║
 * ║                                                                ║
 * ║  Структурированная память на 30 фактов о пользователе.        ║
 * ║  Каждый факт: { key=категория, value=текст, ts=время }.        ║
 * ║                                                                ║
 * ║  • Категории-одиночки (имя, возраст, город, работа) —          ║
 * ║    upsert: новое значение ЗАМЕНЯЕТ старое (нет противоречий).  ║
 * ║  • Остальные (like/dislike/person/note) — дедуп по значению.   ║
 * ║  • Вытеснение по давности (LRU), важные категории защищены.    ║
 * ║  • Старый формат (плоские строки) мигрируется автоматически.   ║
 * ║                                                                ║
 * ║  Хранится в SharedPreferences, переживает перезапуск.          ║
 * ║  Факты подмешиваются в system prompt при каждом разговоре.     ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
class AgentMemory(context: Context) {
    companion object {
        private const val TAG = "AgentMemory"
        private const val PREFS_NAME = "agent_memory"
        private const val KEY_FACTS = "facts"
        private const val MAX_FACTS = 30

        // Категории-одиночки: у пользователя одно имя/возраст/город/работа.
        // Новое значение перезаписывает старое (upsert), не плодит дубли.
        private val SINGLETON_KEYS = setOf("name", "age", "city", "work")

        // Все известные категории (для forget по имени категории).
        private val ALL_KEYS = setOf("name", "age", "city", "work", "like", "dislike", "person", "note")

        // Слова, по которым распознаём факты о близких людях.
        private val PERSON_WORDS = setOf(
            "мама", "мать", "папа", "отец", "родители",
            "брат", "сестра", "жена", "муж", "супруг", "супруга",
            "сын", "дочь", "дочка", "ребёнок", "ребенок", "дети",
            "бабушка", "дедушка", "друг", "подруга", "коллега",
            "начальник", "босс", "сосед", "девушка", "парень"
        )

        // Лёгкая память «о чём недавно говорили» (переживает перезапуск)
        private const val KEY_CONTEXT = "recent_context"
        private const val MAX_CONTEXT = 8       // сколько последних реплик держим
        private const val MAX_CONTEXT_LINE = 80 // максимум символов на реплику
    }

    /** Один структурированный факт памяти. */
    private data class Fact(val key: String, val value: String, val ts: Long)

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== ПУБЛИЧНОЕ API ====================

    /**
     * Все сохранённые факты как список строк (для system prompt).
     * Порядок — по возрастанию времени обновления (старые первыми).
     */
    fun recall(): List<String> = readFacts().map { it.value }

    /**
     * Запомнить новый факт. Категория определяется автоматически.
     * - Одиночные категории (имя/возраст/город/работа) — перезаписываются.
     * - Прочие — дедуп по точному значению (повтор просто освежает время).
     * При переполнении вытесняется самый старый НЕзащищённый факт.
     */
    fun remember(fact: String) = rememberWithKey(fact, null)

    /**
     * То же, что remember(), но с явным указанием категории.
     * @param explicitKey одна из ALL_KEYS, либо null для авто-определения.
     */
    fun rememberWithKey(rawFact: String, explicitKey: String?) {
        val value = normalize(rawFact)
        if (value.isBlank()) return

        val facts = readFacts()
        val key = (explicitKey?.takeIf { it in ALL_KEYS }) ?: deriveKey(value)
        val now = System.currentTimeMillis()

        if (key in SINGLETON_KEYS) {
            // Upsert: заменяем существующее значение этой категории
            val idx = facts.indexOfFirst { it.key == key }
            if (idx >= 0) {
                if (facts[idx].value.equals(value, ignoreCase = true)) {
                    Log.d(TAG, "Факт уже актуален ($key): $value")
                    facts[idx] = facts[idx].copy(ts = now) // освежаем время
                } else {
                    Log.d(TAG, "Обновил $key: '${facts[idx].value}' → '$value'")
                    facts[idx] = Fact(key, value, now)
                }
            } else {
                facts.add(Fact(key, value, now))
                Log.d(TAG, "Запомнил ($key): $value")
            }
        } else {
            // Прочие категории: дедуп по точному значению
            val idx = facts.indexOfFirst { it.value.equals(value, ignoreCase = true) }
            if (idx >= 0) {
                Log.d(TAG, "Факт уже есть: $value")
                facts[idx] = facts[idx].copy(ts = now) // освежаем время
            } else {
                facts.add(Fact(key, value, now))
                Log.d(TAG, "Запомнил ($key): $value")
            }
        }

        evict(facts)
        writeFacts(facts)
    }

    /**
     * Удалить факт. Стратегия — от точного к широкому, чтобы не снести лишнее:
     *  1) точное совпадение значения;
     *  2) совпадение по имени категории (например forget("name"));
     *  3) целое слово внутри значения (запрос ≥ 3 символов);
     *  4) подстрока — только для длинных запросов (≥ 5 символов).
     */
    fun forget(query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return false
        val ql = q.lowercase()
        val facts = readFacts()

        // 1) точное совпадение значения
        var removed = facts.removeAll { it.value.equals(q, ignoreCase = true) }

        // 2) имя категории целиком (forget "name" / "город")
        if (!removed) {
            val keyByAlias = when (ql) {
                "имя", "name" -> "name"
                "возраст", "age" -> "age"
                "город", "city" -> "city"
                "работа", "work" -> "work"
                "нравится", "любит", "like" -> "like"
                "не любит", "dislike" -> "dislike"
                "person" -> "person"
                "note" -> "note"
                else -> null
            }
            if (keyByAlias != null) removed = facts.removeAll { it.key == keyByAlias }
        }

        // 3) целое слово внутри значения (защита от «а» → снести всё)
        if (!removed && q.length >= 3) {
            removed = facts.removeAll { fact -> tokens(fact.value).any { it == ql } }
        }

        // 4) подстрока — только для достаточно длинных запросов
        if (!removed && q.length >= 5) {
            removed = facts.removeAll { it.value.contains(q, ignoreCase = true) }
        }

        if (removed) {
            writeFacts(facts)
            Log.d(TAG, "Забыл по запросу '$q' (осталось: ${facts.size})")
        } else {
            Log.d(TAG, "Не нашёл в памяти: $q")
        }
        return removed
    }

    /** Очистить всю долгосрочную память (факты). */
    fun clear() {
        prefs.edit().remove(KEY_FACTS).apply()
        Log.d(TAG, "Память очищена")
    }

    // ==================== ХРАНИЛИЩЕ + МИГРАЦИЯ ====================

    /**
     * Читает факты из prefs. Поддерживает старый формат (массив строк) —
     * при обнаружении мигрирует его в новый и сразу сохраняет.
     */
    private fun readFacts(): MutableList<Fact> {
        val out = ArrayList<Fact>()
        try {
            val json = prefs.getString(KEY_FACTS, "[]") ?: "[]"
            val arr = JSONArray(json)
            var migrated = false
            val base = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                when (val el = arr.opt(i)) {
                    is JSONObject -> {
                        val v = el.optString("v").trim()
                        if (v.isBlank()) continue
                        val k = el.optString("k").ifBlank { deriveKey(v) }
                        val t = el.optLong("t", base + i)
                        out.add(Fact(k, v, t))
                    }
                    is String -> {
                        // Старый формат: плоская строка → мигрируем
                        val v = el.trim()
                        if (v.isBlank()) continue
                        out.add(Fact(deriveKey(v), v, base + i))
                        migrated = true
                    }
                }
            }
            if (migrated) {
                writeFacts(out)
                Log.d(TAG, "Миграция памяти в новый формат: ${out.size} фактов")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения памяти: ${e.message}")
        }
        // По времени обновления: старые первыми (стабильный порядок для промпта)
        out.sortBy { it.ts }
        return out
    }

    private fun writeFacts(facts: List<Fact>) {
        val arr = JSONArray()
        facts.forEach { f ->
            arr.put(JSONObject().apply {
                put("k", f.key)
                put("v", f.value)
                put("t", f.ts)
            })
        }
        prefs.edit().putString(KEY_FACTS, arr.toString()).apply()
    }

    /**
     * Вытеснение при переполнении: удаляем самые старые НЕзащищённые факты.
     * Если незащищённых нет — жертвуем самым старым вообще.
     */
    private fun evict(facts: MutableList<Fact>) {
        while (facts.size > MAX_FACTS) {
            val victim = facts.filter { !isProtected(it.key) }.minByOrNull { it.ts }
                ?: facts.minByOrNull { it.ts }
                ?: break
            facts.remove(victim)
            Log.d(TAG, "Вытеснил старый факт (${victim.key}): ${victim.value}")
        }
    }

    /** Важные категории не вытесняются мелочью. */
    private fun isProtected(key: String): Boolean = key in SINGLETON_KEYS || key == "person"

    // ==================== КЛАССИФИКАЦИЯ ====================

    /** Нормализует текст факта: trim + схлопывание пробелов. Регистр сохраняем. */
    private fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    /** Разбивает строку на слова-токены в нижнем регистре. */
    private fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    /**
     * Определяет категорию факта по содержимому.
     * Порядок важен: отрицание (dislike) проверяем раньше like.
     */
    private fun deriveKey(value: String): String {
        val l = value.lowercase()
        return when {
            l.contains("зовут") || l.startsWith("имя") -> "name"
            l.contains("возраст") || Regex("\\bмне\\s+\\d+\\s+(год|года|лет)").containsMatchIn(l) -> "age"
            l.contains("живу в") || l.contains("живу во") || l.startsWith("город") -> "city"
            l.contains("работаю") || l.startsWith("работа") || l.contains("профессия") -> "work"
            l.contains("не люблю") || l.contains("ненавиж") || l.startsWith("не любит") || l.contains("не нравит") -> "dislike"
            l.startsWith("любит") || l.startsWith("люблю") || l.startsWith("обожа") || l.contains("нравит") -> "like"
            tokens(l).any { it in PERSON_WORDS } -> "person"
            else -> "note"
        }
    }

    // ==================== НЕДАВНИЙ КОНТЕКСТ ====================

    /**
     * Обрывки последних реплик пользователя (старые первыми).
     * Это НЕ точные факты, а примерный контекст прошлых бесед.
     */
    fun recallContext(): List<String> {
        return try {
            val json = prefs.getString(KEY_CONTEXT, "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения контекста: ${e.message}")
            emptyList()
        }
    }

    /**
     * Добавить реплику в недавний контекст. Держим только последние MAX_CONTEXT.
     */
    fun rememberContext(line: String) {
        val trimmed = line.trim().take(MAX_CONTEXT_LINE)
        if (trimmed.isBlank()) return
        val ctx = recallContext().toMutableList()
        // Не дублируем подряд одну и ту же реплику
        if (ctx.lastOrNull()?.equals(trimmed, ignoreCase = true) == true) return
        ctx.add(trimmed)
        while (ctx.size > MAX_CONTEXT) ctx.removeAt(0)
        val arr = JSONArray()
        ctx.forEach { arr.put(it) }
        prefs.edit().putString(KEY_CONTEXT, arr.toString()).apply()
    }
}
