package com.aiagent.ai_voice_agent.engine.core

import android.util.Log

/**
 * Динамический выбор инструментов по ключевым словам.
 * Вместо отправки всех 61 инструментов — отправляем только релевантные (2-10).
 * Это ускоряет LLM в 3-5 раз.
 */
class ToolSelector(private val toolRegistry: ToolRegistry) {
    companion object {
        private const val TAG = "ToolSelector"
        
        // Базовые инструменты — всегда включены
        private val ALWAYS_INCLUDE = setOf(
            "self_awareness",      // Появляться/скрываться
            "remember_fact",       // Запомнить факт
            "forget_fact",         // Забыть факт
        )
        
        // Категории инструментов по ключевым словам
        private val CATEGORIES = mapOf(
            // Время и дата
            "time" to setOf(
                "get_current_time", "set_alarm", "cancel_alarm", "set_timer", "cancel_timer"
            ),
            // Коммуникация
            "communication" to setOf(
                "send_sms", "make_call", "search_contacts", "read_sms", "search_sms", "send_email", "reply_sms"
            ),
            // Веб и поиск
            "web" to setOf(
                "web_search", "browse_website", "find_links", "search_on_site"
            ),
            // Медиа и музыка
            "media" to setOf(
                "media_control", "play_store_search", "open_play_store", "search_local_music", "play_youtube"
            ),
            // Рисование
            "drawing" to setOf(
                "draw_image"
            ),
            // Файлы
            "files" to setOf(
                "create_file", "read_file", "write_file", "delete_file", "list_files",
                "create_folder", "open_file", "file_info", "download_file"
            ),
            // Система и управление
            "system" to setOf(
                "system_control", "volume_control", "brightness", "flashlight",
                "connect_wifi", "open_app", "list_apps", "app_info"
            ),
            // Информация
            "info" to setOf(
                "get_weather", "get_currency_rate", "get_location", "share_location",
                "battery_info", "device_info", "storage_info", "network_info"
            ),
            // Камера
            "camera" to setOf(
                "take_photo", "take_selfie"
            ),
            // Доступность (экран)
            "accessibility" to setOf(
                "read_screen", "click_element", "type_text", "navigate", "scroll", "list_clickable", "take_screenshot", "explain_screen"
            ),
            // Уведомления
            "notifications" to setOf(
                "read_notifications", "dismiss_notification"
            ),
            // Буфер обмена
            "clipboard" to setOf(
                "clipboard_read", "clipboard_write"
            ),
            // Ссылки и шаринг
            "sharing" to setOf(
                "launch_url", "share_text"
            ),
            // Календарь и напоминания
            "calendar" to setOf(
                "create_event", "list_events", "delete_event", "set_reminder"
            ),
            // Навигация и карты
            "navigation" to setOf(
                "open_maps", "build_route", "find_nearby", "route_home", "route_work"
            ),
        )
        
        // Ключевые слова для каждой категории
        private val KEYWORDS = mapOf(
            "time" to listOf(
                "час", "времени", "время", "дата", "день", "будильник", "таймер",
                "разбуди", "напомни", "time", "clock", "alarm", "timer"
            ),
            "communication" to listOf(
                "позвони", "звонок", "набери", "смс", "сообщение", "напиши", "отправь", "ответь",
                "контакт", "телефон", "email", "почта", "call", "sms", "message", "reply"
            ),
            "web" to listOf(
                "найди", "поиск", "гугл", "интернет", "сайт", "ссылку", "browse",
                "search", "google", "find", "look up"
            ),
            "media" to listOf(
                "музыка", "песня", "плей", "пауза", "следующ", "предыдущ", "громче", "тише",
                "ютуб", "youtube", "клип", "видео", "play", "pause", "music", "song", "volume",
                "поставь", "включи", "заведи", "крутан", "найди музыку", "что за трек",
                "mp3", "альбом", "исполнител", "слуша",
                "плей маркет", "play market", "play store", "приложение", "app"
            ),
            "drawing" to listOf(
                "нарисуй", "картинку", "изображение", "фото", "рисунок", "draw", "picture", "image"
            ),
            "files" to listOf(
                "файл", "папку", "документ", "создай файл", "прочитай", "удали",
                "file", "folder", "document", "create", "read", "delete"
            ),
            "system" to listOf(
                "яркость", "звук", "wifi", "Bluetooth", "фонарик", "открой", "запусти",
                "приложение", "brightness", "sound", "wifi", "bluetooth", "flashlight", "open app"
            ),
            "info" to listOf(
                "погода", "курс", "валют", "батарея", "заряд", "память", "устройств", "координат", "локац", "где я", "отправь местополож",
                "weather", "currency", "battery", "storage", "device", "location", "share location"
            ),
            "camera" to listOf(
                "фото", "селфи", "сними", "камер", "photo", "selfie", "camera", "snap"
            ),
            "accessibility" to listOf(
                "экран", "нажми", "кликни", "прокрути", "введи", "скриншот", "объясни", "что открыто", "что на экране",
                "screen", "click", "scroll", "type", "screenshot", "explain"
            ),
            "notifications" to listOf(
                "уведомлен", "оповещен", "notification", "alert"
            ),
            "clipboard" to listOf(
                "буфер", "скопирован", "вставь", "clipboard", "copy", "paste"
            ),
            "sharing" to listOf(
                "открой ссылку", "поделись", "отправь ссылку", "url", "link", "share"
            ),
            "calendar" to listOf(
                "событие", "события", "встреч", "напомни", "напоминан", "календар",
                "расписание", "план", "удали событ", "создай событ", "покажи событ",
                "сегодня", "завтра", "послезавтра", "на недел", "дедлайн",
                "event", "reminder", "calendar", "schedule"
            ),
            "navigation" to listOf(
                "карт", "маршрут", "навигатор", "навиг", "поехали", "доехать", "маршрут до",
                "заправк", "парковк", "кафе", "аптек", "ресторан", "магазин",
                "домой", "на работу", "открой карты", "google maps", "maps",
                "route", "navigate", "directions", "maps"
            ),
        )
    }
    
    /**
     * Выбрать релевантные инструменты по сообщению пользователя.
     * Всегда включает базовые инструменты + релевантные категории.
     * Использует стемминг (4+ символа корня) для_MATCH всех падежей русского языка.
     */
    fun selectTools(userMessage: String): List<AgentTool> {
        val lower = userMessage.lowercase()
        val selectedToolNames = mutableSetOf<String>()
        
        // Всегда включаем базовые
        selectedToolNames.addAll(ALWAYS_INCLUDE)
        
        // Извлекаем стемы (корни слов) из сообщения — первые 4 символа каждого слова
        val messageStems = lower.split(Regex("[\\s,.!?:;]+"))
            .filter { it.length >= 4 }
            .map { it.take(4) }
            .toSet()
        
        // Определяем релевантные категории по стемам
        val matchedCategories = mutableSetOf<String>()
        for ((category, keywords) in KEYWORDS) {
            for (keyword in keywords) {
                val kwLower = keyword.lowercase()
                // Точное вхождение (для коротких слов типа "play", "sms")
                if (lower.contains(kwLower)) {
                    matchedCategories.add(category)
                    break
                }
                // Стем-матчинг (для русских слов с падежами)
                if (kwLower.length >= 4) {
                    val kwStem = kwLower.take(4)
                    if (messageStems.any { it == kwStem }) {
                        matchedCategories.add(category)
                        break
                    }
                }
            }
        }
        
        // Добавляем инструменты из matched категорий
        for (category in matchedCategories) {
            selectedToolNames.addAll(CATEGORIES[category] ?: emptySet())
        }
        
        // Если ничего не нашли — отправляем минимальный набор для общего чата
        if (matchedCategories.isEmpty()) {
            selectedToolNames.add("web_search")  // Для общих вопросов
        }
        
        // Фильтруем по реально зарегистрированным инструментам
        val tools = selectedToolNames.mapNotNull { name ->
            toolRegistry.get(name)
        }
        
        Log.d(TAG, "Выбрано ${tools.size} инструментов для: '$userMessage' (категории: $matchedCategories, стемы: $messageStems)")
        return tools
    }
}
