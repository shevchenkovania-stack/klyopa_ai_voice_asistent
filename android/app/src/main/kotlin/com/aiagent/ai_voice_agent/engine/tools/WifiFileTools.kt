package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import java.io.File

/** Подключение к WiFi сети */
class ConnectWifiTool(private val context: Context) : AgentTool {
    override val name = "connect_wifi"
    override val description = "Подключиться к WiFi сети. Если сеть открытая — подключится автоматически. Если сеть закрытая — попросит пароль. Может искать сеть по имени."
    override val parameters = listOf(
        ToolParam("ssid", "string", "Имя WiFi сети (например \"Nessa\", \"HomeNetwork\", \"CoffeeShop\")", required = true),
        ToolParam("password", "string", "Пароль WiFi сети (если сеть закрытая и пользователь предоставил пароль)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val ssid = params["ssid"] as? String
        if (ssid.isNullOrBlank()) return ToolResult.failure("Не указано имя WiFi сети")

        val password = params["password"] as? String

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — нельзя подключаться напрямую, открываем WiFi настройки
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.success("Открыты настройки WiFi. Выберите сеть \"$ssid\" для подключения.")
            } else {
                // Android 9 и ниже — можно попробовать через WifiManager
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

                // Включаем WiFi если выключен
                if (!wifiManager.isWifiEnabled) {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                    Thread.sleep(1000) // Ждём включения
                }

                // Сканируем сети
                @Suppress("DEPRECATION")
                val scanResults = wifiManager.scanResults
                val targetNetwork = scanResults.firstOrNull { it.SSID.contains(ssid, ignoreCase = true) }

                if (targetNetwork == null) {
                    return ToolResult.failure("Сеть \"$ssid\" не найдена. Проверьте имя сети и убедитесь что сеть доступна.")
                }

                // Проверяем нужна ли авторизация
                val capabilities = targetNetwork.capabilities
                val isOpen = !capabilities.contains("WEP") && !capabilities.contains("PSK") && !capabilities.contains("EAP")

                if (isOpen && password.isNullOrBlank()) {
                    // Открытая сеть — подключаемся через настройки (на Android 10+ нельзя напрямую)
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult.success("Найдена открытая сеть \"$ssid\". Открываю настройки WiFi для подключения.")
                } else {
                    // Закрытая сеть — нужен пароль
                    if (password.isNullOrBlank()) {
                        ToolResult.success("Сеть \"$ssid\" защищена паролем. Скажите пароль для подключения.")
                    } else {
                        // С паролем — открываем настройки (на новых Android нельзя подключиться напрямую)
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        ToolResult.success("Открыты настройки WiFi. Выберите сеть \"$ssid\" и введите пароль.")
                    }
                }
            }
        } catch (e: Exception) {
            ToolResult.failure("Ошибка подключения к WiFi: ${e.message}")
        }
    }
}

/** Создать текстовый файл */
class CreateFileTool(private val context: Context) : AgentTool {
    override val name = "create_file"
    override val description = "Создать текстовый файл. По умолчанию в /Documents/. Можно указать папку: 'Downloads', 'Documents/MyFolder', или полный путь."
    override val parameters = listOf(
        ToolParam("filename", "string", "Имя файла (например \"заметки.txt\")", required = true),
        ToolParam("content", "string", "Содержимое файла", required = true),
        ToolParam("folder", "string", "Папка: 'Documents' (по умолчанию), 'Downloads', 'Pictures', или путь", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val filename = params["filename"] as? String
        val content = params["content"] as? String
        val folder = params["folder"] as? String

        if (filename.isNullOrBlank()) return ToolResult.failure("Не указано имя файла")
        if (content == null) return ToolResult.failure("Не указано содержимое")

        return try {
            val dir = resolveFolder(folder)
            dir.mkdirs()
            val file = java.io.File(dir, filename)

            file.writeText(content, charset = Charsets.UTF_8)

            if (!file.exists()) {
                return ToolResult.failure("Файл не был создан: $filename")
            }
            val fileSize = file.length()
            if (fileSize == 0L && content.isNotEmpty()) {
                return ToolResult.failure("Файл создан, но пустой: $filename")
            }

            val relativePath = file.absolutePath.replace("/sdcard/", "/")
            ToolResult.success("✓ Файл создан: $relativePath (${fileSize} байт)")
        } catch (e: Exception) {
            ToolResult.failure("Не удалось создать файл: ${e.message}")
        }
    }

    private fun resolveFolder(folder: String?): java.io.File {
        if (folder == null) return java.io.File("/sdcard/Documents")
        return when {
            folder.startsWith("/") -> java.io.File(folder) // Полный путь
            folder.contains("download", true) || folder.contains("загрузк", true) -> java.io.File("/sdcard/Download")
            folder.contains("picture", true) || folder.contains("фото", true) -> java.io.File("/sdcard/Pictures")
            folder.contains("document", true) || folder.contains("документ", true) -> java.io.File("/sdcard/Documents")
            folder.contains("music", true) || folder.contains("музык", true) -> java.io.File("/sdcard/Music")
            folder.contains("movie", true) || folder.contains("видео", true) -> java.io.File("/sdcard/Movies")
            else -> java.io.File("/sdcard/Documents/$folder") // Подпапка в Documents
        }
    }
}

/** Прочитать текстовый файл */
class ReadFileTool(private val context: Context) : AgentTool {
    override val name = "read_file"
    override val description = "Прочитать содержимое текстового файла из папки Документы (/sdcard/Documents/)"
    override val parameters = listOf(
        ToolParam("filename", "string", "Имя файла для чтения (например \"заметки.txt\")", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val filename = params["filename"] as? String
        if (filename.isNullOrBlank()) return ToolResult.failure("Не указано имя файла")

        return try {
            // Читаем из публичной папки Documents
            val dir = java.io.File("/sdcard/Documents")
            val file = java.io.File(dir, filename)

            if (!file.exists()) {
                // Список доступных файлов
                val availableFiles = dir.listFiles()?.map { it.name }?.joinToString(", ") ?: "нет файлов"
                return ToolResult.failure("Файл \"$filename\" не найден в /Documents/. Доступные: $availableFiles")
            }

            val content = file.readText(charset = Charsets.UTF_8)
            ToolResult.success("Содержимое \"$filename\":\n$content", data = mapOf("content" to content, "path" to file.absolutePath))
        } catch (e: Exception) {
            ToolResult.failure("Не удалось прочитать файл: ${e.message}")
        }
    }
}
