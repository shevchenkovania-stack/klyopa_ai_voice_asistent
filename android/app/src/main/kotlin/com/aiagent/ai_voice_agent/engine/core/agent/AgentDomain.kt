package com.aiagent.ai_voice_agent.engine.core.agent

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  DOMAIN — Группировка инструментов по тематикам              ║
 * ║                                                              ║
 * ║  Каждый домен — это специализированный агент, который        ║
 * ║  знает только свои инструменты. Это делает LLM быстрее       ║
 * ║  и точнее (меньше токенов, меньше путаницы).                 ║
 * ║                                                              ║
 * ║  Хочешь добавить новый домен? Просто допиши enum.            ║
 * ║  Инструменты распредели вручную — LLM скажет спасибо.        ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * Architecture note: This file is pure Kotlin — NO Android deps.
 * Can be reused on any platform (Windows, Mac, Linux) with any LLM.
 */
enum class AgentDomain(
    /** Human-readable name (Russian, for logs/UI) */
    val displayName: String,
    /** Short hint for the router — what kind of messages belong here */
    val routeHint: String,
    /** Tool names in this domain — passed to the specialist agent */
    val toolNames: Set<String>
) {
    // ======================== DOMAINS ========================

    GENERAL(
        displayName = "Общее",
        routeHint = "приветствия, общие вопросы, болтовня, помощь, что умеешь",
        toolNames = setOf(
            "web_search"
        )
    ),

    MUSIC(
        displayName = "Музыка",
        routeHint = "музыка, песни, треки, плеер, пауза, стоп, громкость, ютуб, видео",
        toolNames = setOf(
            "search_local_music",
            "media_control",
            "play_youtube",
            "volume_control",
            "brightness"
        )
    ),

    SYSTEM(
        displayName = "Система",
        routeHint = "яркость, громкость, фонарик, wifi, Bluetooth, приложения, открыть, установить",
        toolNames = setOf(
            "system_control",
            "volume_control",
            "brightness",
            "flashlight",
            "connect_wifi",
            "open_app",
            "list_apps",
            "app_info"
        )
    ),

    INFO(
        displayName = "Информация",
        routeHint = "погода, время, дата, курс валют, батарея, устройство, память, интернет, местоположение, координаты, где я, отправь локацию",
        toolNames = setOf(
            "get_weather",
            "get_current_time",
            "get_currency_rate",
            "get_location",
            "share_location",
            "battery_info",
            "device_info",
            "storage_info",
            "network_info"
        )
    ),

    WEB(
        displayName = "Веб",
        routeHint = "найди в интернете, поиск в интернете, гугл, сайт, ссылка, открой сайт, приложение в плей маркете",
        toolNames = setOf(
            "web_search",
            "browse_website",
            "find_links",
            "search_on_site",
            "launch_url",
            "share_text",
            "play_store_search",
            "open_play_store"
        )
    ),

    FILES(
        displayName = "Файлы",
        routeHint = "файл, папка, документ, создать, прочитать, удалить, сохранить, скачать",
        toolNames = setOf(
            "create_file",
            "read_file",
            "write_file",
            "delete_file",
            "list_files",
            "create_folder",
            "open_file",
            "file_info",
            "download_file"
        )
    ),

    COMMUNICATION(
        displayName = "Коммуникация",
        routeHint = "позвони, смс, сообщение, email, контакт, найти контакт, отправить, ответь",
        toolNames = setOf(
            "search_contacts",
            "send_sms",
            "make_call",
            "read_sms",
            "search_sms",
            "send_email",
            "reply_sms"
        )
    ),

    ALARMS(
        displayName = "Будильники",
        routeHint = "будильник, таймер, разбуди, напомни, поставь на",
        toolNames = setOf(
            "set_alarm",
            "cancel_alarm",
            "set_timer",
            "cancel_timer"
        )
    ),

    ACCESSIBILITY(
        displayName = "Экран",
        routeHint = "экран, нажми, кликни, прокрути, введи текст, прочитай экран, уведомления, скриншот, объясни, что открыто",
        toolNames = setOf(
            "read_screen",
            "click_element",
            "type_text",
            "navigate",
            "scroll",
            "list_clickable",
            "take_screenshot",
            "explain_screen",
            "read_notifications",
            "dismiss_notification"
        )
    ),

    CAMERA(
        displayName = "Камера",
        routeHint = "фото, селфи, сфотографируй, камера, снимок",
        toolNames = setOf(
            "take_photo",
            "take_selfie"
        )
    ),

    CLIPBOARD(
        displayName = "Буфер обмена",
        routeHint = "буфер обмена, скопируй, вставь, скопировано",
        toolNames = setOf(
            "clipboard_read",
            "clipboard_write"
        )
    ),

    DRAWING(
        displayName = "Рисование",
        routeHint = "нарисуй, изображение, картинку, пуантилизм, далл-е",
        toolNames = setOf(
            "draw_image"
        )
    ),

    MEMORY(
        displayName = "Память",
        routeHint = "запомни, забудь, факты, напомни что я, что ты помнишь, покажи память",
        toolNames = setOf(
            "remember_fact",
            "forget_fact",
            "list_memory"
        )
    ),

    CORE(
        displayName = "Ядро",
        routeHint = "покажись, скройся, появись на экране",
        toolNames = setOf(
            "self_awareness"
        )
    ),

    NAVIGATION(
        displayName = "Навигация",
        routeHint = "найди рядом, найди по маршруту, карты, маршрут, навигатор, поехали, доехать, заправка, парковка, кафе рядом, аптека рядом, домой, на работу, ближайш",
        toolNames = setOf(
            "open_maps",
            "build_route",
            "find_nearby",
            "route_home",
            "route_work"
        )
    );

    companion object {
        /**
         * Все инструменты, известные системе (flat set).
         * Используется для проверки что ни один инструмент не пропущен.
         */
        val allToolNames: Set<String> by lazy {
            entries.flatMap { it.toolNames }.toSet()
        }

        /**
         * Найти домен по имени инструмента.
         * Если инструмент не найден ни в одном домене — вернёт null.
         * Это нормально для старта: инструмент просто не попадёт ни в один специалист.
         */
        fun forTool(toolName: String): AgentDomain? {
            return entries.firstOrNull { toolName in it.toolNames }
        }

        /**
         * Проверить что все инструменты из ToolRegistry распределены по доменам.
         * Вызывается при инициализации для отлова забытых инструментов.
         */
        fun validate(allRegistered: Set<String>): List<String> {
            return allRegistered.filter { it !in allToolNames }
        }
    }
}
