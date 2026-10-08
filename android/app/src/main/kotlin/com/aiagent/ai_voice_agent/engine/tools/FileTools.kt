package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ==================== CREATE FOLDER ====================
class CreateFolderTool(private val context: Context) : AgentTool {
    override val name = "create_folder"
    override val description = """
        Создать новую папку на устройстве.
        ЧТО ДЕЛАЕТ: Создаёт папку в указанном месте. По умолчанию в /Documents/. Понимает "Загрузки", "Документы", "Фото".
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь говорит "создай папку", "сделай директорию", "организуй файлы".
        ПАРАМЕТРЫ: 
          - name (string, обязательно) — имя папки (например 'Работа', 'Проект Х')
          - parent (string, опц.) — родительская папка: 'Documents' (по умолчанию), 'Downloads', 'Pictures', или полный путь
        ВОЗВРАЩАЕТ: "✓ Папка создана: /Documents/Работа" или "Папка уже существует"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "name", description = "Имя папки (например 'Работа', 'Проект Х')", type = "string", required = true),
        ToolParam(name = "parent", description = "Родительская папка: 'Documents' (по умолчанию), 'Downloads', 'Pictures', или полный путь", type = "string", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val name = params["name"] as? String ?: return ToolResult.failure("Имя папки обязательно")
        val parent = params["parent"] as? String
        
        val parentDir = resolveParent(parent)
        val folder = File(parentDir, name)
        
        return try {
            if (folder.exists()) {
                ToolResult.success("Папка уже существует: ${folder.absolutePath.replace("/sdcard/", "/")}")
            } else {
                folder.mkdirs()
                if (folder.exists()) {
                    ToolResult.success("✓ Папка создана: ${folder.absolutePath.replace("/sdcard/", "/")}")
                } else {
                    ToolResult.failure("Не удалось создать папку")
                }
            }
        } catch (e: Exception) {
            ToolResult.failure("Ошибка создания папки: ${e.message}")
        }
    }

    private fun resolveParent(parent: String?): File {
        if (parent == null) return File("/sdcard/Documents")
        return when {
            parent.startsWith("/") -> File(parent)
            parent.contains("download", true) || parent.contains("загрузк", true) -> File("/sdcard/Download")
            parent.contains("picture", true) || parent.contains("фото", true) -> File("/sdcard/Pictures")
            parent.contains("document", true) || parent.contains("документ", true) -> File("/sdcard/Documents")
            else -> File("/sdcard/Documents/$parent")
        }
    }
}

// ==================== LIST FILES ====================
class ListFilesTool(private val context: Context) : AgentTool {
    override val name = "list_files"
    override val description = """
        Показать содержимое папки.
        ЧТО ДЕЛАЕТ: Выводит список файлов и подпапок в указанной директории с размерами и типами.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "что в папке", "покажи файлы", "какие документы есть".
        ПАРАМЕТРЫ: path (string, обязательно) — путь к папке (например '/sdcard/Download').
        ВОЗВРАЩАЕТ: Список файлов с размерами: "📁 Работа/\n📄 заметки.txt (1.2 KB)\n📄 отчёт.pdf (245 KB)"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "path", description = "Путь к папке (например '/sdcard/Download')", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val path = params["path"] as? String ?: "/sdcard"
        val dir = resolvePath(path)
        
        if (!dir.exists()) return ToolResult.failure("Directory not found: $path")
        if (!dir.isDirectory) return ToolResult.failure("Not a directory: $path")
        
        val files = dir.listFiles() ?: return ToolResult.failure("Cannot read directory")
        val items = files.sortedBy { it.name.lowercase() }.map { file ->
            mapOf(
                "name" to file.name,
                "type" to if (file.isDirectory) "folder" else getFileType(file),
                "size" to formatSize(file.length()),
                "modified" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            )
        }
        
        return ToolResult.success("Files in ${dir.name} (${files.size} items):\n${items.joinToString("\n") { "${it["name"]} (${it["type"]}, ${it["size"]})" }}")
    }

    private fun resolvePath(path: String): File {
        if (path.startsWith("/")) return File(path)
        return when {
            path.contains("download", true) -> File("/sdcard/Download")
            path.contains("document", true) -> File("/sdcard/Documents")
            path.contains("picture", true) || path.contains("photo", true) -> File("/sdcard/Pictures")
            else -> File("/sdcard/$path")
        }
    }

    private fun getFileType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp" -> "image"
            "mp4", "avi", "mkv", "mov" -> "video"
            "mp3", "wav", "ogg", "flac" -> "audio"
            "pdf" -> "pdf"
            "doc", "docx" -> "document"
            "txt", "md", "json" -> "text"
            "zip", "rar", "7z" -> "archive"
            "apk" -> "installer"
            else -> "file"
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

// ==================== DELETE FILE ====================
class DeleteFileTool(private val context: Context) : AgentTool {
    override val name = "delete_file"
    override val description = "Delete a file or empty folder. Use with caution!"
    override val parameters = listOf(
        ToolParam(name = "path", description = "File or folder path to delete", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val path = params["path"] as? String ?: return ToolResult.failure("Path is required")
        val file = resolvePath(path)
        if (!file.exists()) return ToolResult.failure("File not found: $path")
        
        val name = file.name
        val deleted = if (file.isDirectory) {
            if (file.listFiles()?.isEmpty() == true) file.delete()
            else return ToolResult.failure("Folder not empty: $name")
        } else {
            file.delete()
        }
        
        return if (deleted) ToolResult.success("Deleted: $name")
        else ToolResult.failure("Failed to delete: $name")
    }

    private fun resolvePath(path: String): File {
        if (path.startsWith("/")) return File(path)
        return File("/sdcard/$path")
    }
}

// ==================== WRITE FILE ====================
class WriteFileTool(private val context: Context) : AgentTool {
    override val name = "write_file"
    override val description = "Write text content to a file. Creates the file if it doesn't exist."
    override val parameters = listOf(
        ToolParam(name = "path", description = "File path to write to", type = "string", required = true),
        ToolParam(name = "content", description = "Text content to write", type = "string", required = true),
        ToolParam(name = "append", description = "If true, append to existing file", type = "boolean", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val path = params["path"] as? String ?: return ToolResult.failure("Path is required")
        val content = params["content"] as? String ?: return ToolResult.failure("Content is required")
        val append = params["append"] as? Boolean ?: false
        
        val file = resolvePath(path)
        file.parentFile?.mkdirs()
        
        return try {
            if (append) {
                file.appendText(content)
                ToolResult.success("Appended ${content.length} chars to ${file.name}")
            } else {
                file.writeText(content)
                ToolResult.success("Wrote ${content.length} chars to ${file.name}")
            }
        } catch (e: Exception) {
            ToolResult.failure("Write failed: ${e.message}")
        }
    }

    private fun resolvePath(path: String): File {
        if (path.startsWith("/")) return File(path)
        return File("/sdcard/Documents/$path")
    }
}

// ==================== OPEN FILE ====================
class OpenFileTool(private val context: Context) : AgentTool {
    override val name = "open_file"
    override val description = "Open a file with the default app (PDF, image, video, etc.)"
    override val parameters = listOf(
        ToolParam(name = "path", description = "File path to open", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val path = params["path"] as? String ?: return ToolResult.failure("Path is required")
        val file = resolvePath(path)
        if (!file.exists()) return ToolResult.failure("File not found: $path")
        
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Opening ${file.name}")
        } catch (e: Exception) {
            ToolResult.failure("Cannot open file: ${e.message}")
        }
    }

    private fun resolvePath(path: String): File {
        if (path.startsWith("/")) return File(path)
        return File("/sdcard/$path")
    }

    private fun getMimeType(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        "doc", "docx" -> "application/msword"
        else -> "*/*"
    }
}

// ==================== FILE INFO ====================
class FileInfoTool(private val context: Context) : AgentTool {
    override val name = "file_info"
    override val description = "Get information about a file (size, type, modified date)"
    override val parameters = listOf(
        ToolParam(name = "path", description = "File path", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val path = params["path"] as? String ?: return ToolResult.failure("Path is required")
        val file = resolvePath(path)
        if (!file.exists()) return ToolResult.failure("File not found: $path")
        
        val info = buildString {
            appendLine("Name: ${file.name}")
            appendLine("Type: ${if (file.isDirectory) "Directory" else "File"}")
            appendLine("Size: ${formatSize(file.length())}")
            appendLine("Modified: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))}")
            appendLine("Path: ${file.absolutePath}")
            if (file.isFile) appendLine("Extension: .${file.extension}")
            if (file.isDirectory) appendLine("Items: ${file.listFiles()?.size ?: 0}")
        }
        
        return ToolResult.success(info.trim())
    }

    private fun resolvePath(path: String): File {
        if (path.startsWith("/")) return File(path)
        return File("/sdcard/$path")
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

