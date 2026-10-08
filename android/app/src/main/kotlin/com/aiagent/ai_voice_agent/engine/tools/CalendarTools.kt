/**
 * CalendarTools — управление календарём и напоминаниями.
 *
 * 4 инструмента:
 *  - `create_event` — создать событие в календаре
 *  - `list_events`  — показать события за период
 *  - `delete_event` — удалить событие по названию или ID
 *  - `set_reminder` — создать событие с push-напоминанием
 *
 * Время можно передать:
 *  - текстом на естественном русском ("завтра в 15:00", "в пятницу в 3 часа дня"),
 *  - или Unix-миллисекундами (параметр start_time_ms).
 *
 * Использует Android CalendarContract. Требует разрешений READ_CALENDAR / WRITE_CALENDAR.
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

// ============================================================================
// Парсер естественного времени (русский)
// ============================================================================
object NaturalTimeParser {
    private val WEEKDAYS = mapOf(
        "понедельник" to Calendar.MONDAY, "понед" to Calendar.MONDAY,
        "вторник" to Calendar.TUESDAY, "втор" to Calendar.TUESDAY,
        "среда" to Calendar.WEDNESDAY, "сред" to Calendar.WEDNESDAY,
        "четверг" to Calendar.THURSDAY, "четвер" to Calendar.THURSDAY,
        "пятница" to Calendar.FRIDAY, "пятниц" to Calendar.FRIDAY,
        "суббота" to Calendar.SATURDAY, "субот" to Calendar.SATURDAY,
        "воскресенье" to Calendar.SUNDAY, "воскрес" to Calendar.SUNDAY
    )

    private val MONTHS = mapOf(
        "январ" to Calendar.JANUARY, "феврал" to Calendar.FEBRUARY,
        "март" to Calendar.MARCH, "марта" to Calendar.MARCH, "марте" to Calendar.MARCH,
        "апрел" to Calendar.APRIL, "ма" to Calendar.MAY, "мая" to Calendar.MAY, "мае" to Calendar.MAY,
        "июн" to Calendar.JUNE, "июл" to Calendar.JULY,
        "август" to Calendar.AUGUST, "август" to Calendar.AUGUST,
        "сентябр" to Calendar.SEPTEMBER, "октябр" to Calendar.OCTOBER,
        "ноябр" to Calendar.NOVEMBER, "декабр" to Calendar.DECEMBER
    )

    /**
     * Распознать время из текста.
     * @return миллисекунды с эпохи или null, если не удалось.
     */
    fun parse(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val lower = text.lowercase(Locale.getDefault()).trim()

        val now = Calendar.getInstance()
        val base = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 1) "через N минут/часов"
        parseRelative(lower, now)?.let { return it }

        // 2) Определяем день
        applyDay(lower, base)

        // 3) Применяем время (часы:минуты)
        applyTimeOfDay(lower, base)

        // Если день не сдвинулся и время не задано — возвращаем null (нужно конкретнее)
        val dayShifted = base.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) ||
                base.get(Calendar.YEAR) != now.get(Calendar.YEAR)
        val timeSet = lower.contains(Regex("\\d{1,2}[:.\\s]?\\d{0,2}")) ||
                listOf("утра", "дня", "вечера", "полдень", "полдни", "ночи").any { lower.contains(it) }
        if (!dayShifted && !timeSet) return null

        return base.timeInMillis
    }

    private fun parseRelative(lower: String, now: Calendar): Long? {
        val mMinutes = Pattern.compile("через\\s+(\\d{1,3})\\s*(минут[а-я]?|мин)").matcher(lower)
        if (mMinutes.find()) {
            val mins = mMinutes.group(1).toInt()
            return now.timeInMillis + mins * 60_000L
        }
        val mHours = Pattern.compile("через\\s+(\\d{1,2})\\s*(час[а-я]?|ч)").matcher(lower)
        if (mHours.find()) {
            val hours = mHours.group(1).toInt()
            return now.timeInMillis + hours * 3_600_000L
        }
        if (lower.contains("через час")) return now.timeInMillis + 3_600_000L
        if (lower.contains("через полчаса")) return now.timeInMillis + 1_800_000L
        return null
    }

    private fun applyDay(lower: String, base: Calendar) {
        when {
            lower.contains("послезавтра") -> base.add(Calendar.DAY_OF_YEAR, 2)
            lower.contains("завтра") -> base.add(Calendar.DAY_OF_YEAR, 1)
            lower.contains("сегодня") -> { /* без сдвига */ }
            else -> {
                for ((key, calDay) in WEEKDAYS) {
                    if (lower.contains(key)) {
                        val today = base.get(Calendar.DAY_OF_WEEK)
                        var delta = (calDay - today + 7) % 7
                        if (delta == 0) delta = 7 // ближайший, но не сегодня
                        base.add(Calendar.DAY_OF_YEAR, delta)
                        return
                    }
                }
                // Конкретная дата: "15 июля", "1 сентября"
                val mDate = Pattern.compile("(\\d{1,2})\\s+([а-я]+)").matcher(lower)
                if (mDate.find()) {
                    val dayNum = mDate.group(1).toInt()
                    val monthKey = mDate.group(2).take(5)
                    val month = MONTHS.entries.firstOrNull { monthKey.startsWith(it.key) }?.value
                    if (month != null && dayNum in 1..31) {
                        base.set(Calendar.DAY_OF_MONTH, dayNum)
                        base.set(Calendar.MONTH, month)
                        // Если дата уже прошла в этом году — переносим на следующий
                        val now = Calendar.getInstance()
                        if (base.before(now)) base.add(Calendar.YEAR, 1)
                    }
                }
            }
        }
    }

    private fun applyTimeOfDay(lower: String, base: Calendar) {
        // "в 15:00", "в 3 часа", "в 3:30", "в 15.00"
        val mTime = Pattern.compile("(?:в\\s*)?(\\d{1,2})[:.\\s]?\\s*(\\d{0,2})").matcher(lower)
        var hour = -1
        var minute = 0
        if (mTime.find()) {
            hour = mTime.group(1).toIntOrNull() ?: -1
            val minStr = mTime.group(2)
            if (!minStr.isNullOrBlank()) minute = minStr.toIntOrNull() ?: 0
        }

        // Поправки: "3 часа дня" → 15:00
        if (hour in 1..11) {
            when {
                lower.contains("дня") || lower.contains("вечера") || lower.contains("дня") -> hour += 12
                lower.contains("утра") -> { /* AM */ }
                lower.contains("ночи") -> hour += 12
            }
        }
        if (lower.contains("полдень") && hour == -1) hour = 12
        if (lower.contains("полночь") && hour == -1) hour = 0

        if (hour in 0..23) {
            base.set(Calendar.HOUR_OF_DAY, hour)
            base.set(Calendar.MINUTE, minute)
        }
    }
}

// ============================================================================
// Общие хелперы
// ============================================================================
private fun checkCalendarPermission(ctx: Context): String? {
    val read = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR)
    val write = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR)
    if (read != PackageManager.PERMISSION_GRANTED) return "READ_CALENDAR"
    if (write != PackageManager.PERMISSION_GRANTED) return "WRITE_CALENDAR"
    return null
}

/**
 * Найти первый доступный календарь для записи.
 * Приоритет: основной (isPrimary) → видимый → любой.
 */
private fun findWritableCalendarId(ctx: Context): Long? {
    val projection = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.IS_PRIMARY,
        CalendarContract.Calendars.VISIBLE
    )
    val cursor = ctx.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
    ) ?: return null
    var fallback: Long? = null
    cursor.use {
        while (it.moveToNext()) {
            val id = it.getLong(0)
            val isPrimary = it.getInt(1) == 1
            val visible = it.getInt(2) == 1
            if (isPrimary) return id
            if (visible && fallback == null) fallback = id
        }
    }
    return fallback
}

private fun formatDateTime(ms: Long): String {
    val sdf = SimpleDateFormat("d MMMM, HH:mm", Locale.getDefault())
    return sdf.format(ms)
}

private fun formatTime(ms: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(ms)
}

// ============================================================================
// create_event
// ============================================================================
class CreateEventTool(private val context: Context) : AgentTool {
    override val name = "create_event"
    override val description = """
        Создать событие в календаре.
        ЧТО ДЕЛАЕТ: Добавляет событие в системный календарь Android. Если включена синхронизация — улетит в Google Calendar.
        КОГДА ИСПОЛЬЗОВАТЬ: "созвони с врачом завтра в 15:00", "встреча с Анной в пятницу в 18:00", "дедлайн 15 июля".
        ПАРАМЕТРЫ:
          title (обязательно) — название события.
          start_time (обязательно) — время в естественном формате ("завтра в 15:00", "в пятницу в 3 часа дня") или Unix-мс.
          duration_minutes (необязательно) — длительность в минутах (по умолчанию 30).
          all_day (необязательно) — "true" для события на весь день.
        ВОЗВРАЩАЕТ: "Создано: Встреча с врачом — 5 июля, 15:00"
        ОГРАНИЧЕНИЯ: Требует разрешений READ_CALENDAR и WRITE_CALENDAR.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("title", "string", "Название события", required = true),
        ToolParam("start_time", "string", "Время начала (естественный текст или Unix-мс)", required = true),
        ToolParam("duration_minutes", "number", "Длительность в минутах (по умолчанию 30)", required = false),
        ToolParam("all_day", "string", "true = событие на весь день", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        checkCalendarPermission(context)?.let { return ToolResult.failure("Нет разрешения $it. Откройте Настройки → Приложения → AI Voice Agent → Разрешения → Календарь.") }
        val calId = findWritableCalendarId(context) ?: return ToolResult.failure("На устройстве нет календаря. Откройте приложение «Календарь» и добавьте аккаунт.")

        val title = (params["title"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.failure("Не указано название события")
        val startRaw = params["start_time"]?.toString()
            ?: return ToolResult.failure("Не указано время начала")

        val startMs = startRaw.toLongOrNull() ?: NaturalTimeParser.parse(startRaw)
            ?: return ToolResult.failure("Не удалось распознать время: '$startRaw'. Укажи конкретнее, например: 'завтра в 15:00' или 'в пятницу в 3 часа дня'.")

        val allDay = (params["all_day"] as? String)?.equals("true", ignoreCase = true) == true
        val duration = (params["duration_minutes"] as? Number)?.toInt() ?: if (allDay) 1440 else 30
        val endMs = startMs + duration * 60_000L

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return ToolResult.failure("Не удалось создать событие")
            val eventId = ContentUris.parseId(uri)
            ToolResult.success(
                "Создано: $title — ${formatDateTime(startMs)}",
                mapOf("event_id" to eventId, "title" to title, "start_ms" to startMs)
            )
        } catch (e: Exception) {
            android.util.Log.e("CreateEvent", "Error: ${e.message}")
            ToolResult.failure("Ошибка создания события: ${e.message}")
        }
    }
}

// ============================================================================
// list_events
// ============================================================================
class ListEventsTool(private val context: Context) : AgentTool {
    override val name = "list_events"
    override val description = """
        Показать события в календаре за период.
        ЧТО ДЕЛАЕТ: Возвращает список событий за день/неделю/конкретную дату.
        КОГДА ИСПОЛЬЗОВАТЬ: "что у меня сегодня?", "какие планы на завтра?", "что на этой неделе?", "что 15 июля?".
        ПАРАМЕТРЫ:
          period (необязательно) — "сегодня" (по умолчанию), "завтра", "неделя", "месяц", или конкретная дата ("15 июля", "в пятницу").
        ВОЗВРАЩАЕТ: Список событий в читаемом формате или "Ничего не запланировано".
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("period", "string", "Период: 'сегодня', 'завтра', 'неделя', 'месяц' или дата ('15 июля')", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        checkCalendarPermission(context)?.let { return ToolResult.failure("Нет разрешения $it") }

        val period = (params["period"] as? String)?.lowercase()?.trim() ?: "сегодня"
        val now = Calendar.getInstance()
        val from = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val to = Calendar.getInstance()

        when {
            period.contains("завтра") -> {
                from.add(Calendar.DAY_OF_YEAR, 1)
                to.timeInMillis = from.timeInMillis; to.add(Calendar.DAY_OF_YEAR, 1)
            }
            period.contains("недел") -> {
                to.timeInMillis = from.timeInMillis; to.add(Calendar.DAY_OF_YEAR, 7)
            }
            period.contains("месяц") -> {
                to.timeInMillis = from.timeInMillis; to.add(Calendar.MONTH, 1)
            }
            else -> {
                // "сегодня" или конкретная дата
                val parsed = NaturalTimeParser.parse(period)
                if (parsed != null) {
                    from.timeInMillis = parsed
                    from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0)
                }
                to.timeInMillis = from.timeInMillis; to.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_ID
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.Calendars.VISIBLE, "1")
            .build()

        return try {
            val cursor = context.contentResolver.query(
                uri, projection,
                "${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.BEGIN} < ?",
                arrayOf(from.timeInMillis.toString(), to.timeInMillis.toString()),
                "${CalendarContract.Instances.BEGIN} ASC"
            ) ?: return ToolResult.success("Не удалось прочитать календарь")

            val events = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "(без названия)"
                    val begin = it.getLong(1)
                    val allDay = it.getInt(3) == 1
                    val timeStr = if (allDay) "весь день" else formatTime(begin)
                    events.add("$timeStr — $title")
                }
            }

            if (events.isEmpty()) {
                ToolResult.success("На ${formatDateTime(from.timeInMillis).substringBefore(",")} ничего не запланировано")
            } else {
                ToolResult.success("События (${events.size}):\n${events.joinToString("\n")}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ListEvents", "Error: ${e.message}")
            ToolResult.failure("Ошибка чтения календаря: ${e.message}")
        }
    }
}

// ============================================================================
// delete_event
// ============================================================================
class DeleteEventTool(private val context: Context) : AgentTool {
    override val name = "delete_event"
    override val description = """
        Удалить событие из календаря.
        ЧТО ДЕЛАЕТ: Ищет событие по названию и удаляет его. Если найдено несколько — удаляет все совпадения.
        КОГДА ИСПОЛЬЗОВАТЬ: "удали встречу с врачом", "отмени ужин завтра", "удали все события на завтра".
        ПАРАМЕТРЫ:
          title (обязательно) — название события (или его часть) для удаления.
          date (необязательно) — дата для уточнения ("завтра", "сегодня", "15 июля").
        ВОЗВРАЩАЕТ: "Удалено: Встреча с врачом" или "Событие не найдено".
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("title", "string", "Название события (или его часть)", required = true),
        ToolParam("date", "string", "Дата для уточнения ('сегодня', 'завтра', '15 июля')", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        checkCalendarPermission(context)?.let { return ToolResult.failure("Нет разрешения $it") }

        val title = (params["title"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.failure("Не указано название события")
        val dateRaw = params["date"] as? String

        // Определяем диапазон дат (если указана дата)
        val fromMs: Long?
        val toMs: Long?
        if (dateRaw != null) {
            val start = NaturalTimeParser.parse(dateRaw) ?: return ToolResult.failure("Не удалось распознать дату: '$dateRaw'")
            fromMs = start
            toMs = start + 24 * 3_600_000L
        } else {
            fromMs = null; toMs = null
        }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART
        )

        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, null, null, null
            ) ?: return ToolResult.failure("Не удалось прочитать календарь")

            val toDelete = mutableListOf<Pair<Long, String>>()
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val t = it.getString(1) ?: continue
                    val dt = it.getLong(2)
                    if (t.contains(title, ignoreCase = true)) {
                        if (fromMs == null || (dt >= fromMs && dt < toMs!!)) {
                            toDelete.add(id to t)
                        }
                    }
                }
            }

            if (toDelete.isEmpty()) return ToolResult.failure("Событие \"$title\" не найдено")

            var deleted = 0
            for ((id, _) in toDelete) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                val rows = context.contentResolver.delete(uri, null, null)
                deleted += rows
            }

            if (deleted == 0) ToolResult.failure("Не удалось удалить")
            else ToolResult.success("Удалено событий: $deleted\n${toDelete.joinToString("\n") { "• ${it.second}" }}")
        } catch (e: Exception) {
            android.util.Log.e("DeleteEvent", "Error: ${e.message}")
            ToolResult.failure("Ошибка удаления: ${e.message}")
        }
    }
}

// ============================================================================
// set_reminder
// ============================================================================
class SetReminderTool(private val context: Context) : AgentTool {
    override val name = "set_reminder"
    override val description = """
        Поставить напоминание (событие с push-уведомлением).
        ЧТО ДЕЛАЕТ: Создаёт событие в календаре и привязывает к нему системное уведомление, которое придёт в указанное время (или за N минут до события).
        КОГДА ИСПОЛЬЗОВАТЬ: "напомни через 20 минут выключить духовку", "напомни завтра в 9 утра позвонить маме", "напомни в пятницу в 18:00".
        ПАРАМЕТРЫ:
          title (обязательно) — текст напоминания.
          time (обязательно) — когда напомнить ("через 20 минут", "завтра в 9 утра", "в пятницу в 18:00").
          remind_before_minutes (необязательно) — за сколько минут до события прислать уведомление (по умолчанию 0 = ровно в момент).
        ВОЗВРАЩАЕТ: "Напоминание поставлено: Позвонить маме — завтра, 09:00".
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("title", "string", "Текст напоминания", required = true),
        ToolParam("time", "string", "Когда напомнить (естественный текст)", required = true),
        ToolParam("remind_before_minutes", "number", "За сколько минут до события (по умолчанию 0)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        checkCalendarPermission(context)?.let { return ToolResult.failure("Нет разрешения $it") }
        val calId = findWritableCalendarId(context) ?: return ToolResult.failure("На устройстве нет календаря")

        val title = (params["title"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.failure("Не указан текст напоминания")
        val timeRaw = params["time"]?.toString()
            ?: return ToolResult.failure("Не указано время")

        val startMs = NaturalTimeParser.parse(timeRaw)
            ?: return ToolResult.failure("Не удалось распознать время: '$timeRaw'. Укажи конкретнее: 'через 20 минут', 'завтра в 9 утра'.")

        val duration = 30
        val endMs = startMs + duration * 60_000L
        val beforeMin = (params["remind_before_minutes"] as? Number)?.toInt() ?: 0

        val eventValues = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        return try {
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
                ?: return ToolResult.failure("Не удалось создать событие")
            val eventId = ContentUris.parseId(eventUri)

            // Добавляем напоминание в календарь (для системного приложения «Календарь»)
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, beforeMin)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

            // Гарантированное push-уведомление через AlarmManager (работает даже если календарь не шлёт уведомлений)
            val triggerAtMs = startMs - beforeMin * 60_000L
            com.aiagent.ai_voice_agent.engine.ReminderScheduler.schedule(context, title, triggerAtMs)

            ToolResult.success(
                "Напоминание поставлено: $title — ${formatDateTime(startMs)}${if (beforeMin > 0) " (за $beforeMin мин)" else ""}"
            )
        } catch (e: Exception) {
            android.util.Log.e("SetReminder", "Error: ${e.message}")
            ToolResult.failure("Ошибка создания напоминания: ${e.message}")
        }
    }
}
