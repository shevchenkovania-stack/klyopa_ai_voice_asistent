/**
 * CommunicationTools — набор инструментов для коммуникации: SMS, звонки, контакты, email, шаринг.
 *
 * Файл содержит 8 инструментов:
 * - `send_sms` — отправка SMS-сообщения
 * - `make_call` — телефонный звонок
 * - `search_contacts` — поиск контактов по имени
 * - `read_sms` — чтение последних SMS
 * - `search_sms` — поиск SMS по тексту/номеру
 * - `launch_url` — открытие URL в браузере
 * - `share_text` — шаринг текста через любое приложение
 * - `send_email` — отправка email
 *
 * Большинство инструментов требуют runtime permissions (SEND_SMS, CALL_PHONE, READ_CONTACTS, READ_SMS).
 *
 * Зависимости: SmsManager, ContactsHelper, Intent API
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

/**
 * Инструмент `send_sms` — отправка SMS.
 * Проверяет разрешение SEND_SMS, валидирует номер и текст, отправляет через SmsManager.
 */
class SendSmsTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "SendSms" }

    override val name = "send_sms"
    override val description = """
        Отправить SMS-сообщение на номер телефона.
        ЧТО ДЕЛАЕТ: Отправляет SMS через SmsManager. Проверяет разрешение SEND_SMS.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "отправь смс", "напиши маме", "сообщи по смс".
        ПАРАМЕТРЫ: phone (обязательно, номер), message (обязательно, текст).
        ВОЗВРАЩАЕТ: "SMS отправлено на +380501234567"
        ОГРАНИЧЕНИЯ: Требует разрешение SEND_SMS.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "phone", description = "Номер телефона (например +380501234567)", type = "string", required = true),
        ToolParam(name = "message", description = "Текст сообщения", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на отправку SMS")
        }

        val phone = params["phone"] as? String ?: return ToolResult.failure("Не указан номер телефона")
        val message = params["message"] as? String ?: return ToolResult.failure("Не указан текст сообщения")

        if (phone.isEmpty()) return ToolResult.failure("Не указан номер телефона")
        if (message.isEmpty()) return ToolResult.failure("Не указан текст сообщения")

        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, message, null, null)
            android.util.Log.d("SMS", "SMS sent to $phone")
            ToolResult.success("SMS отправлено на $phone")
        } catch (e: Exception) {
            android.util.Log.e("SMS", "Failed to send SMS: ${e.message}")
            ToolResult.failure("Не удалось отправить SMS: ${e.message}")
        }
    }
}

/**
 * Инструмент `make_call` — телефонный звонок.
 * Проверяет разрешение CALL_PHONE, использует ACTION_CALL с fallback на ACTION_DIAL.
 */
class MakeCallTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "MakeCall" }

    override val name = "make_call"
    override val description = """
        Позвонить по номеру телефона.
        ЧТО ДЕЛАЕТ: Открывает звонок через ACTION_CALL. Если нет разрешения — fallback на ACTION_DIAL (набор номера).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "позвони маме", "набери номер", "сделай звонок".
        ПАРАМЕТРЫ: phone (обязательно, номер телефона).
        ВОЗВРАЩАЕТ: "Звоню на +380501234567" или "Открываю набор номера: ..."
        ОГРАНИЧЕНИЯ: Требует разрешение CALL_PHONE для прямого звонка.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "phone", description = "Номер телефона для звонка", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на звонки")
        }

        val phone = params["phone"] as? String ?: return ToolResult.failure("Не указан номер телефона")
        if (phone.isEmpty()) return ToolResult.failure("Не указан номер телефона")

        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$phone")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Звоню на $phone")
        } catch (e: Exception) {
            android.util.Log.e("Phone", "Failed to make call: ${e.message}")
            // Fallback to dial
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                    data = android.net.Uri.parse("tel:$phone")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.success("Открываю набор номера: $phone")
            } catch (e2: Exception) {
                ToolResult.failure("Не удалось позвонить: ${e2.message}")
            }
        }
    }
}

/**
 * Инструмент `search_contacts` — поиск контактов.
 * Ищет в телефонной книге по имени через ContactsHelper.
 * Требует разрешение READ_CONTACTS.
 */
class SearchContactsTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "SearchContacts" }

    override val name = "search_contacts"
    override val description = """
        Найти контакт в телефонной книге по имени.
        ЧТО ДЕЛАЕТ: Ищет контакт по имени через ContactsHelper, возвращает имя и номер телефона.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "найди контакт Мама", "какой номер у Ивана", "позвони Еве".
        ПАРАМЕТРЫ: query (обязательно) — имя для поиска (например "Мама", "Иван").
        ВОЗВРАЩАЕТ: "Найдено контактов: 1\nМама: +380501234567"
        ОГРАНИЧЕНИЯ: Требует разрешение READ_CONTACTS.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "query", description = "Имя для поиска (например \"Мама\", \"Иван\", \"Ева Директор\")", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на доступ к контактам")
        }

        val query = params["query"] as? String ?: return ToolResult.failure("Не указано имя для поиска")
        if (query.isEmpty()) return ToolResult.failure("Не указано имя для поиска")

        return try {
            val helper = com.aiagent.ai_voice_agent.helpers.ContactsHelper(context)
            val contacts = helper.searchContacts(query)

            if (contacts.isEmpty()) {
                return ToolResult.failure("Контакт \"$query\" не найден")
            }

            // Format results
            val formatted = contacts.joinToString("\n") { c ->
                "${c["name"]}: ${c["phone"]}"
            }

            ToolResult.success(
                "Найдено контактов: ${contacts.size}\n$formatted",
                mapOf("count" to contacts.size, "contacts" to contacts)
            )
        } catch (e: Exception) {
            android.util.Log.e("Contacts", "Failed to search: ${e.message}")
            ToolResult.failure("Ошибка поиска контактов: ${e.message}")
        }
    }
}

/**
 * Инструмент `read_sms` — чтение последних SMS.
 * Читает входящие SMS из ContentResolver, возвращает номер, текст и дату.
 * Требует разрешение READ_SMS.
 */
class ReadSmsTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "ReadSms" }

    override val name = "read_sms"
    override val description = "Прочитать последние SMS-сообщения с устройства. " +
        "ЧТО ДЕЛАЕТ: Возвращает список последних входящих SMS с номером отправителя, текстом и датой. " +
        "КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает \"прочитай смс\", \"какие сообщения\", \"есть ли новые смс\", \"кто писал\"."
    override val parameters = listOf(
        ToolParam("count", "number", "Количество последних SMS для чтения (по умолчанию 10)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на чтение SMS. Откройте Настройки телефона → Приложения → AI Voice Agent → Разрешения → SMS → Разрешить.")
        }

        val count = (params["count"] as? Number)?.toInt() ?: 10

        return try {
            val uri = android.net.Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("address", "body", "date"),
                null, null, "date DESC LIMIT $count"
            )

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                return ToolResult.success("SMS-сообщений не найдено. Если сообщения есть, но не читаются — возможно, Android ограничивает доступ. Попробуйте сделать AI Voice Agent SMS-приложением по умолчанию в настройках.")
            }

            val messages = mutableListOf<String>()
            var index = 1
            do {
                val address = cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: "Неизвестный"
                val body = cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(date))

                val truncatedBody = if (body.length > 100) "${body.substring(0, 100)}..." else body
                messages.add("$index. $address\n   Текст: $truncatedBody\n   Время: $dateStr")
                index++
            } while (cursor.moveToNext())
            cursor.close()

            ToolResult.success("Найдено ${messages.size} SMS:\n${messages.joinToString("\n")}")
        } catch (e: Exception) {
            android.util.Log.e("SMS", "Failed to read SMS: ${e.message}")
            ToolResult.failure("Ошибка чтения SMS: ${e.message}")
        }
    }
}

/**
 * Инструмент `search_sms` — поиск SMS по тексту или номеру.
 * Ищет в теле SMS и в номере отправителя через ContentResolver.
 * Требует разрешение READ_SMS.
 */
class SearchSmsTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "SearchSms" }

    override val name = "search_sms"
    override val description = "Поиск SMS-сообщений по тексту, номеру или дате. " +
        "ЧТО ДЕЛАЕТ: Ищет SMS содержащие указанную строку в тексте или от определённого номера. " +
        "КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит \"найди смс от Оли\", \"поищи сообщения с текстом привет\", \"найди смс за сегодня\"."
    override val parameters = listOf(
        ToolParam("query", "string", "Текст для поиска (номер телефона, имя, или слово из сообщения)", required = true),
        ToolParam("limit", "number", "Максимальное количество результатов (по умолчанию 20)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на чтение SMS. Разрешите в настройках.")
        }

        val query = params["query"] as? String
        if (query.isNullOrBlank()) return ToolResult.failure("Не указан текст для поиска")

        val limit = (params["limit"] as? Number)?.toInt() ?: 20

        return try {
            val uri = android.net.Uri.parse("content://sms/inbox")
            val selection = "body LIKE ? OR address LIKE ?"
            val selectionArgs = arrayOf("%$query%", "%$query%")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("address", "body", "date"),
                selection, selectionArgs, "date DESC LIMIT $limit"
            )

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                return ToolResult.success("SMS по запросу \"$query\" не найдено")
            }

            val messages = mutableListOf<String>()
            var index = 1
            do {
                val address = cursor.getString(cursor.getColumnIndexOrThrow("address")) ?: "Неизвестный"
                val body = cursor.getString(cursor.getColumnIndexOrThrow("body")) ?: ""
                val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(date))

                val truncatedBody = if (body.length > 100) "${body.substring(0, 100)}..." else body
                messages.add("$index. $address\n   Текст: $truncatedBody\n   Время: $dateStr")
                index++
            } while (cursor.moveToNext())
            cursor.close()

            ToolResult.success("Найдено ${messages.size} SMS по запросу \"$query\":\n${messages.joinToString("\n")}")
        } catch (e: Exception) {
            android.util.Log.e("SMS", "Failed to search SMS: ${e.message}")
            ToolResult.failure("Ошибка поиска SMS: ${e.message}")
        }
    }
}

/**
 * Инструмент `launch_url` — открытие URL в браузере.
 * Автоматически добавляет https:// если протокол не указан.
 */
class LaunchUrlTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "LaunchUrl" }

    override val name = "launch_url"
    override val description = """
        Открыть URL в браузере.
        ЧТО ДЕЛАЕТ: Открывает указанный URL в системном браузере через ACTION_VIEW.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "открой гугл", "зайди на сайт", "открой ссылку".
        ПАРАМЕТРЫ: url (обязательно) — URL (например "https://google.com").
        ВОЗВРАЩАЕТ: "Opening https://google.com"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "url", description = "URL to open (e.g., 'https://google.com')", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        var url = params["url"] as? String ?: return ToolResult.failure("URL is required")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Opening $url")
        } catch (e: Exception) {
            ToolResult.failure("Cannot open URL: ${e.message}")
        }
    }
}

/**
 * Инструмент `share_text` — шаринг текста через любое приложение.
 * Открывает системный chooser для выбора приложения (мессенджер, email, соцсети).
 */
class ShareTextTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "ShareText" }

    override val name = "share_text"
    override val description = """
        Поделиться текстом через любое приложение.
        ЧТО ДЕЛАЕТ: Открывает системный диалог шаринга (мессенджер, email, соцсети).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "поделись", "отправь кому-то", "шарь этот текст".
        ПАРАМЕТРЫ: text (обязательно) — текст для шаринга. subject (необязательно) — тема/заголовок.
        ВОЗВРАЩАЕТ: "Opening share dialog"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "text", description = "Text to share", type = "string", required = true),
        ToolParam(name = "subject", description = "Optional subject/title", type = "string", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val text = params["text"] as? String ?: return ToolResult.failure("Text is required")
        val subject = params["subject"] as? String ?: ""
        
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                if (subject.isNotEmpty()) putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share via").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            ToolResult.success("Opening share dialog")
        } catch (e: Exception) {
            ToolResult.failure("Cannot share: ${e.message}")
        }
    }
}

/**
 * Инструмент `send_email` — отправка email.
 * Открывает email-компомер через ACTION_SENDTO с mailto: URI.
 */
class SendEmailTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "SendEmail" }

    override val name = "send_email"
    override val description = """
        Отправить email (открывает email-компомер).
        ЧТО ДЕЛАЕТ: Открывает системное email-приложение с предзаполненными полями.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "отправь email", "напиши письмо", "отправь на почту".
        ПАРАМЕТРЫ: to (необязательно, email получателя), subject (необязательно, тема), body (обязательно, текст письма).
        ВОЗВРАЩАЕТ: "Opening email composer to user@example.com" или "No email app found"
        ОГРАНИЧЕНИЯ: Требует установленное email-приложение.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "to", description = "Recipient email address", type = "string", required = false),
        ToolParam(name = "subject", description = "Email subject", type = "string", required = false),
        ToolParam(name = "body", description = "Email body text", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val to = params["to"] as? String ?: ""
        val subject = params["subject"] as? String ?: ""
        val body = params["body"] as? String ?: return ToolResult.failure("Body is required")
        
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                if (to.isNotEmpty()) putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(to))
                if (subject.isNotEmpty()) putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                putExtra(android.content.Intent.EXTRA_TEXT, body)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ToolResult.success("Opening email composer${if (to.isNotEmpty()) " to $to" else ""}")
            } else {
                ToolResult.failure("No email app found")
            }
        } catch (e: Exception) {
            ToolResult.failure("Cannot send email: ${e.message}")
        }
    }
}

/**
 * Инструмент `reply_sms` — ответ на последнее SMS.
 * Читает последнее входящее SMS и отправляет ответ на тот же номер.
 */
class ReplySmsTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "ReplySms" }

    override val name = "reply_sms"
    override val description = """
        Ответить на последнее SMS-сообщение.
        ЧТО ДЕЛАЕТ: Читает последнее входящее SMS и отправляет ответ на тот же номер.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "ответь на смс", "напиши в ответ", "ответь маме".
        ПАРАМЕТРЫ: message (обязательно, текст ответа).
        ВОЗВРАЩАЕТ: "✓ Ответ отправлено на +380501234567: Привет!"
        ОГРАНИЧЕНИЯ: Требует разрешение SEND_SMS и READ_SMS.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("message", "string", "Текст ответа", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        // Check permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.failure("Нет разрешения на чтение/отправку SMS")
        }

        val message = params["message"] as? String ?: return ToolResult.failure("Не указан текст ответа")
        if (message.isEmpty()) return ToolResult.failure("Не указан текст ответа")

        return try {
            // Get last SMS
            val uri = android.net.Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("address", "body", "date"),
                null, null, "date DESC LIMIT 1"
            )

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                return ToolResult.failure("Не найдено входящих SMS для ответа")
            }

            val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
            cursor.close()

            // Send reply
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(address, null, message, null, null)
            android.util.Log.d(TAG, "Reply sent to $address: $message")
            ToolResult.success("✓ Ответ отправлено на $address: $message")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to reply: ${e.message}")
            ToolResult.failure("Не удалось ответить: ${e.message}")
        }
    }
}
